package dev.marcinromanowski.pspsim.kernel.scenario.internal;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class JdbcScenarioRules implements ScenarioRules {

  private static final TypeReference<Map<String, String>> SINGLE_VALUED = new TypeReference<>() {};

  private static final int SCENARIO_ID_LENGTH = 24;

  private static final int STORY_LOCKS = 0x51_4d_53_54;

  private static final char SEPARATOR = '\u0000';

  private static final String FIXED_SUCCESS = "FIXED_SUCCESS";
  private static final String TERMINAL_FAILURE = "TERMINAL_FAILURE";
  private static final String TRANSPORT_FAULT = "TRANSPORT_FAULT";
  private static final String TRANSPORT_FAULT_INSTEAD_OF_OPERATION =
      "TRANSPORT_FAULT_INSTEAD_OF_OPERATION";
  private static final String PROCESSING_THEN_TERMINAL = "PROCESSING_THEN_TERMINAL";
  private static final String DELIVERY_DELAYED = "DELIVERY_DELAYED";
  private static final String DELIVERY_DUPLICATED = "DELIVERY_DUPLICATED";
  private static final String DELIVERY_DROPPED = "DELIVERY_DROPPED";

  private final JdbcClient jdbc;
  private final ObjectMapper json;
  private final IdGenerator ids;
  private final SimulatorClock clock;
  private final TransactionTemplate unitOfWork;

  public JdbcScenarioRules(
      JdbcClient jdbc,
      ObjectMapper json,
      IdGenerator ids,
      SimulatorClock clock,
      TransactionTemplate unitOfWork) {
    this.jdbc = jdbc;
    this.json = json;
    this.ids = ids;
    this.clock = clock;
    this.unitOfWork = unitOfWork;
  }

  @Override
  public List<ScenarioRule> addAll(List<NewScenarioRule> rules) {
    return Objects.requireNonNull(
        unitOfWork.execute(status -> addInOneTransaction(rules)),
        "the unit of work answered nothing where its callback answers the rules it added on"
            + " every path");
  }

  private List<ScenarioRule> addInOneTransaction(List<NewScenarioRule> rules) {
    List<ScenarioRule> added = new ArrayList<>();
    for (NewScenarioRule rule : rules) {
      added.add(add(rule));
    }
    return List.copyOf(added);
  }

  @Override
  public ScenarioRule add(NewScenarioRule rule) {
    String scenarioId = "scn_" + ids.hex(SCENARIO_ID_LENGTH);
    Instant createdAt = clock.now(rule.testRunId());
    ScenarioMatch match = rule.match();
    Amount amount = match.amount();
    ScenarioStory story = rule.story();
    long arrival =
        jdbc.sql(
                """
                insert into simulator_scenario_rule
                    (scenario_id, provider, test_run_id, priority, match_merchant_id,
                     match_order_id, match_provider_object_id, match_customer_id,
                     match_payment_method_id, match_amount, match_currency,
                     match_operation, match_event_type, match_header, action_type,
                     action_provider_code, action_fault, action_http_status, action_delay_ms,
                     action_settles_after_ms, story, keyed_on, when_state, then_state, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning seq
                """)
            .param(scenarioId)
            .param(rule.provider().value())
            .param(TestRunId.storableNamespace(rule.testRunId()).value())
            .param(rule.priority())
            .param(match.merchantId())
            .param(match.orderId())
            .param(match.providerObjectId())
            .param(match.customerId())
            .param(match.paymentMethodId())
            .param(amount == null ? null : amount.value())
            .param(match.currency())
            .param(match.operation())
            .param(match.eventType())
            .param(json.writeValueAsString(match.header()))
            .param(typeOf(rule.action()))
            .param(rule.action().theProviderCodeItComesToRestWith())
            .param(faultNameOf(rule.action()))
            .param(faultStatusOf(rule.action()))
            .param(delayMillisOf(rule.action()))
            .param(settlesAfterMillisOf(rule.action()))
            .param(story == null ? null : story.name())
            .param(story == null ? null : story.keyedOn().name())
            .param(story == null ? null : story.whenState())
            .param(story == null ? null : story.thenState())
            .param(createdAt.atOffset(ZoneOffset.UTC))
            .query(Long.class)
            .single();
    return new ScenarioRule(
        scenarioId,
        rule.provider(),
        rule.testRunId(),
        rule.priority(),
        arrival,
        match,
        rule.action(),
        story,
        createdAt);
  }

  @Override
  public Optional<ScenarioRule> find(ProviderId provider, TestRunId testRunId, String scenarioId) {
    return jdbc.sql(
            """
            select * from simulator_scenario_rule
            where provider = ? and test_run_id = ? and scenario_id = ?
            """)
        .params(provider.value(), testRunId.value(), scenarioId)
        .query(this::toRule)
        .optional();
  }

  @Override
  public List<ScenarioRule> of(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql(
            """
            select * from simulator_scenario_rule
            where provider = ? and test_run_id = ?
            order by seq desc
            """)
        .params(provider.value(), testRunId.value())
        .query(this::toRule)
        .list();
  }

  @Override
  public boolean remove(ProviderId provider, TestRunId testRunId, String scenarioId) {
    return jdbc.sql(
                """
                delete from simulator_scenario_rule
                where provider = ? and test_run_id = ? and scenario_id = ?
                """)
            .params(provider.value(), testRunId.value(), scenarioId)
            .update()
        == 1;
  }

  @Override
  public <T> T decidingOneRequestAtATimeAbout(
      ProviderId provider, TestRunId testRunId, List<StoryKey> keys, Supplier<T> decision) {
    return Objects.requireNonNull(
        unitOfWork.execute(
            status -> {
              for (int lock : theLocksOver(provider, testRunId, keys)) {
                jdbc.sql(
                        """
                        select count(*) from (select pg_advisory_xact_lock(?, ?)) as taken
                        """)
                    .params(STORY_LOCKS, lock)
                    .query(Long.class)
                    .single();
              }
              return decision.get();
            }));
  }

  private static List<Integer> theLocksOver(
      ProviderId provider, TestRunId testRunId, List<StoryKey> keys) {
    return keys.stream()
        .map(key -> theStoriesOf(provider, testRunId, key))
        .distinct()
        .sorted()
        .toList();
  }

  private static int theStoriesOf(ProviderId provider, TestRunId testRunId, StoryKey key) {
    return (provider.value()
            + SEPARATOR
            + testRunId.value()
            + SEPARATOR
            + key.keyedOn().name()
            + SEPARATOR
            + key.value())
        .hashCode();
  }

  @Override
  public SequencedMap<String, String> storyStatesOf(
      ProviderId provider, TestRunId testRunId, StoryKey key) {
    LinkedHashMap<String, String> held = new LinkedHashMap<>();
    jdbc.sql(
            """
            select story, state from simulator_scenario_story_state
            where provider = ? and test_run_id = ? and keyed_on = ? and story_key = ?
            order by story
            """)
        .param(provider.value())
        .param(testRunId.value())
        .param(key.keyedOn().name())
        .param(key.value())
        .query()
        .listOfRows()
        .forEach(row -> held.put((String) row.get("story"), (String) row.get("state")));
    return Collections.unmodifiableSequencedMap(held);
  }

  @Override
  public boolean moveStory(
      ProviderId provider,
      TestRunId testRunId,
      StoryKey key,
      String story,
      String from,
      String to) {
    Instant now = clock.now(testRunId);
    if (ScenarioStory.INITIAL_STATE.equals(from)) {
      return jdbc.sql(
                  """
                  insert into simulator_scenario_story_state
                      (provider, test_run_id, story, keyed_on, story_key, state, updated_at)
                  values (?, ?, ?, ?, ?, ?, ?)
                  on conflict (provider, test_run_id, story, keyed_on, story_key) do update
                      set state = excluded.state, updated_at = excluded.updated_at
                      where simulator_scenario_story_state.state = ?
                  """)
              .param(provider.value())
              .param(TestRunId.storableNamespace(testRunId).value())
              .param(story)
              .param(key.keyedOn().name())
              .param(key.value())
              .param(to)
              .param(now.atOffset(ZoneOffset.UTC))
              .param(ScenarioStory.INITIAL_STATE)
              .update()
          == 1;
    }
    return jdbc.sql(
                """
                update simulator_scenario_story_state
                set state = ?, updated_at = ?
                where provider = ? and test_run_id = ? and story = ?
                  and keyed_on = ? and story_key = ? and state = ?
                """)
            .param(to)
            .param(now.atOffset(ZoneOffset.UTC))
            .param(provider.value())
            .param(testRunId.value())
            .param(story)
            .param(key.keyedOn().name())
            .param(key.value())
            .param(from)
            .update()
        == 1;
  }

  @Override
  public int clear(ProviderId provider, TestRunId testRunId) {
    jdbc.sql("delete from simulator_scenario_story_state where provider = ? and test_run_id = ?")
        .params(provider.value(), testRunId.value())
        .update();
    return jdbc.sql("delete from simulator_scenario_rule where provider = ? and test_run_id = ?")
        .params(provider.value(), testRunId.value())
        .update();
  }

  @Override
  public int clearAll(ProviderId provider) {
    jdbc.sql("delete from simulator_scenario_story_state where provider = ?")
        .param(provider.value())
        .update();
    return jdbc.sql("delete from simulator_scenario_rule where provider = ?")
        .param(provider.value())
        .update();
  }

  private static String typeOf(ScenarioAction action) {
    return switch (action) {
      case ScenarioAction.FixedSuccess _ -> FIXED_SUCCESS;
      case ScenarioAction.TerminalFailure _ -> TERMINAL_FAILURE;
      case ScenarioAction.TransportFaultAlways _ -> TRANSPORT_FAULT;
      case ScenarioAction.TransportFaultInsteadOfOperation _ ->
          TRANSPORT_FAULT_INSTEAD_OF_OPERATION;
      case ScenarioAction.ProcessingThenTerminal _ -> PROCESSING_THEN_TERMINAL;
      case ScenarioAction.DeliveryDelayed _ -> DELIVERY_DELAYED;
      case ScenarioAction.DeliveryDuplicated _ -> DELIVERY_DUPLICATED;
      case ScenarioAction.DeliveryDropped _ -> DELIVERY_DROPPED;
    };
  }

  private static @Nullable Long settlesAfterMillisOf(ScenarioAction action) {
    return action instanceof ScenarioAction.ProcessingThenTerminal settling
        ? settling.settlesAfter().toMillis()
        : null;
  }

  private static @Nullable TransportFault faultOf(ScenarioAction action) {
    return switch (action) {
      case ScenarioAction.TransportFaultAlways always -> always.fault();
      case ScenarioAction.TransportFaultInsteadOfOperation instead -> instead.fault();
      case ScenarioAction.FixedSuccess _,
          ScenarioAction.TerminalFailure _,
          ScenarioAction.ProcessingThenTerminal _,
          ScenarioAction.OnTheDelivery _ ->
          null;
    };
  }

  private static @Nullable String faultNameOf(ScenarioAction action) {
    TransportFault fault = faultOf(action);
    return fault == null ? null : fault.kind().name();
  }

  private static @Nullable Integer faultStatusOf(ScenarioAction action) {
    return faultOf(action) instanceof TransportFault.ServerError error ? error.httpStatus() : null;
  }

  private static @Nullable Long delayMillisOf(ScenarioAction action) {
    if (action instanceof ScenarioAction.DeliveryDelayed held) {
      return held.held().toMillis();
    }
    return faultOf(action) instanceof TransportFault.DelayedResponse delayed
        ? delayed.delay().toMillis()
        : null;
  }

  private ScenarioRule toRule(ResultSet rs, int row) throws SQLException {
    String amount = rs.getString("match_amount");
    String story = rs.getString("story");
    return new ScenarioRule(
        rs.getString("scenario_id"),
        ProviderId.of(rs.getString("provider")),
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getInt("priority"),
        rs.getLong("seq"),
        new ScenarioMatch(
            rs.getString("match_merchant_id"),
            rs.getString("match_order_id"),
            rs.getString("match_provider_object_id"),
            rs.getString("match_customer_id"),
            rs.getString("match_payment_method_id"),
            amount == null ? null : Amount.of(amount),
            rs.getString("match_currency"),
            rs.getString("match_operation"),
            rs.getString("match_event_type"),
            json.readValue(rs.getString("match_header"), SINGLE_VALUED)),
        actionOf(rs),
        story == null
            ? null
            : new ScenarioStory(
                story,
                StoryKeying.valueOf(rs.getString("keyed_on")),
                rs.getString("when_state"),
                rs.getString("then_state")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }

  private static ScenarioAction actionOf(ResultSet rs) throws SQLException {
    String type = rs.getString("action_type");
    if (FIXED_SUCCESS.equals(type)) {
      return new ScenarioAction.FixedSuccess();
    }
    if (TRANSPORT_FAULT.equals(type)) {
      return new ScenarioAction.TransportFaultAlways(storedFaultOf(rs));
    }
    if (TRANSPORT_FAULT_INSTEAD_OF_OPERATION.equals(type)) {
      return new ScenarioAction.TransportFaultInsteadOfOperation(storedFaultOf(rs));
    }
    if (PROCESSING_THEN_TERMINAL.equals(type)) {
      return new ScenarioAction.ProcessingThenTerminal(
          Duration.ofMillis(rs.getLong("action_settles_after_ms")),
          rs.getString("action_provider_code"));
    }
    if (DELIVERY_DELAYED.equals(type)) {
      return new ScenarioAction.DeliveryDelayed(Duration.ofMillis(rs.getLong("action_delay_ms")));
    }
    if (DELIVERY_DUPLICATED.equals(type)) {
      return new ScenarioAction.DeliveryDuplicated();
    }
    if (DELIVERY_DROPPED.equals(type)) {
      return new ScenarioAction.DeliveryDropped();
    }
    return new ScenarioAction.TerminalFailure(rs.getString("action_provider_code"));
  }

  private static TransportFault storedFaultOf(ResultSet rs) throws SQLException {
    return switch (TransportFaultKind.valueOf(rs.getString("action_fault"))) {
      case SERVER_ERROR -> new TransportFault.ServerError(rs.getInt("action_http_status"));
      case TRUNCATED_BODY -> new TransportFault.TruncatedBody();
      case CORRUPTED_BODY -> new TransportFault.CorruptedBody();
      case DROPPED_CONNECTION -> new TransportFault.DroppedConnection();
      case DELAYED_RESPONSE ->
          new TransportFault.DelayedResponse(Duration.ofMillis(rs.getLong("action_delay_ms")));
    };
  }
}
