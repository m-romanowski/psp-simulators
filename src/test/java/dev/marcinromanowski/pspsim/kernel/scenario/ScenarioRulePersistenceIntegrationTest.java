package dev.marcinromanowski.pspsim.kernel.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.internal.JdbcScenarioRules;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class ScenarioRulePersistenceIntegrationTest extends PostgresBackedTest {

  private static final ProviderId GROW = ProviderId.of("grow");
  private static final ProviderId STRIPE = ProviderId.of("stripe");
  private static final TestRunId RUN = TestRunId.of("scenario-persistence-it");
  private static final TestRunId OTHER_RUN = TestRunId.of("scenario-persistence-it-other");
  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");
  private static final String STORY = "capture-retries";

  private static final int THE_STORIES_A_KEY_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_HOLDS = 12;

  private static final String
      THE_SCENARIO_SCREEN_LISTS_ONE_ROW_PER_STORY_IN_THIS_ORDER_AND_TWELVE_LEAVE_479001600_ORDERS =
          "the Scenarios screen draws one row per story of this test run in the order the store"
              + " hands them over, so a tester reading which story stands where finds them named"
              + " the same way on every restart and wherever a state was moved last. Twelve"
              + " stories leave 479001600 orders an unordered copy could draw from the stories'"
              + " hashes and the salt java.util.ImmutableCollections draws once per JVM, so this"
              + " assertion cannot pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
              + " states are in such a map there is no way left to tell it from an ordered one,"
              + " so the store hands the screen a map whose type carries an order on every run"
              + " rather than one that happened to print in the right order on this one";

  private static final String ONE_FLOW_VALUE = "process:900001";

  private static final StoryKey ONE_FLOW = flow(ONE_FLOW_VALUE);

  private static final StoryKey THE_WHOLE_RUN = StoryKey.ofTestRun(RUN);

  private static StoryKey flow(String value) {
    return new StoryKey(StoryKeying.FLOW, value);
  }

  @Autowired private ScenarioRules rules;
  @Autowired private FixedTimes fixedTimes;
  @Autowired private JdbcClient jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private IdGenerator ids;
  @Autowired private SimulatorClock clock;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void clearWhatThisTestWrote() {
    for (TestRunId run : List.of(RUN, OTHER_RUN)) {
      rules.clear(GROW, run);
      rules.clear(STRIPE, run);
      fixedTimes.release(run);
    }
    rules.clearAll(GROW);
    rules.clearAll(STRIPE);
  }

  @Test
  void a_stored_rule_comes_back_with_every_predicate_and_the_action_it_was_written_with() {
    fixedTimes.pin(RUN, PINNED);
    ScenarioMatch match =
        new ScenarioMatch(
            "merchant-1",
            "order-123",
            "pi_sim_123",
            "cus_sim_123",
            "pm_success",
            Amount.of("0010.00"),
            "PLN",
            "settleSuspendedTransaction",
            null,
            Map.of("X-Simulator-Scenario", "capture-decline"));

    ScenarioRule added =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                100,
                match,
                new ScenarioAction.TerminalFailure("803"),
                new ScenarioStory(
                    "capture-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked")));

    assertThat(added.scenarioId()).startsWith("scn_");
    assertThat(added.createdAt()).isEqualTo(PINNED);
    ScenarioRule read = rules.find(GROW, RUN, added.scenarioId()).orElseThrow();
    assertThat(read).isEqualTo(added);
    assertThat(read.match()).isEqualTo(match);
    assertThat(read.match().amount().value()).isEqualTo("0010.00");
    assertThat(read.match().providerObjectId()).isEqualTo("pi_sim_123");
    assertThat(read.match().customerId()).isEqualTo("cus_sim_123");
    assertThat(read.match().operation()).isEqualTo("settleSuspendedTransaction");
    assertThat(read.match().specificity()).isEqualTo(9);
    assertThat(read.action()).isEqualTo(new ScenarioAction.TerminalFailure("803"));
    assertThat(read.story())
        .isEqualTo(
            new ScenarioStory(
                "capture-retries", StoryKeying.FLOW, ScenarioStory.INITIAL_STATE, "asked"));
    assertThat(read.specificity()).isEqualTo(10);
    assertThat(read.priority()).isEqualTo(100);
  }

  @Test
  void a_stored_processing_state_comes_back_with_the_moment_and_the_outcome_it_was_owed() {
    ScenarioRule succeeding = rules.add(pending(Duration.ofSeconds(5), null));
    ScenarioRule failing = rules.add(pending(Duration.ofMillis(1500), "803"));

    assertThat(rules.find(GROW, RUN, succeeding.scenarioId()).orElseThrow().action())
        .isEqualTo(new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null));
    assertThat(rules.find(GROW, RUN, failing.scenarioId()).orElseThrow().action())
        .isEqualTo(new ScenarioAction.ProcessingThenTerminal(Duration.ofMillis(1500), "803"));
  }

  private static NewScenarioRule pending(Duration settlesAfter, @Nullable String providerCode) {
    return new NewScenarioRule(
        GROW,
        RUN,
        0,
        ScenarioMatch.anyRequest().naming("settleSuspendedTransaction"),
        new ScenarioAction.ProcessingThenTerminal(settlesAfter, providerCode),
        null);
  }

  @Test
  void a_rule_that_names_no_predicate_comes_back_naming_none() {
    ScenarioRule added =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.TerminalFailure("701"),
                null));

    ScenarioRule read = rules.find(GROW, RUN, added.scenarioId()).orElseThrow();
    assertThat(read.match()).isEqualTo(ScenarioMatch.anyRequest());
    assertThat(read.match().specificity()).isZero();
    assertThat(read.action()).isEqualTo(new ScenarioAction.TerminalFailure("701"));
  }

  @Test
  void arrival_order_is_a_column_of_its_own_and_two_rules_of_one_pinned_clock_still_differ() {
    fixedTimes.pin(RUN, PINNED);

    ScenarioRule first = decline();
    ScenarioRule second = decline();

    assertThat(first.createdAt()).isEqualTo(second.createdAt());
    assertThat(second.arrival()).isGreaterThan(first.arrival());
    assertThat(rules.of(GROW, RUN))
        .extracting(ScenarioRule::scenarioId)
        .containsExactly(second.scenarioId(), first.scenarioId());
  }

  @Test
  void a_rule_is_addressed_only_within_its_own_provider_and_test_run() {
    ScenarioRule added = decline();

    assertThat(rules.find(GROW, RUN, added.scenarioId())).isPresent();
    assertThat(rules.find(STRIPE, RUN, added.scenarioId())).isEmpty();
    assertThat(rules.find(GROW, OTHER_RUN, added.scenarioId())).isEmpty();
    assertThat(rules.remove(GROW, OTHER_RUN, added.scenarioId())).isFalse();
    assertThat(rules.remove(GROW, RUN, added.scenarioId())).isTrue();
    assertThat(rules.remove(GROW, RUN, added.scenarioId())).isFalse();
    assertThat(rules.of(GROW, RUN)).isEmpty();
  }

  @Test
  void clearing_a_test_run_leaves_every_other_test_run_and_provider_alone() {
    decline();
    decline();
    rules.add(
        new NewScenarioRule(
            GROW,
            OTHER_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure("1"),
            null));
    rules.add(
        new NewScenarioRule(
            STRIPE,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure("1"),
            null));

    assertThat(rules.clear(GROW, RUN)).isEqualTo(2);
    assertThat(rules.of(GROW, RUN)).isEmpty();
    assertThat(rules.of(GROW, OTHER_RUN)).hasSize(1);
    assertThat(rules.of(STRIPE, RUN)).hasSize(1);
    assertThat(rules.clearAll(GROW)).isEqualTo(1);
    assertThat(rules.of(STRIPE, RUN)).hasSize(1);
  }

  @Test
  void a_flow_and_a_test_run_that_spell_one_key_hold_two_states_of_one_story() {
    StoryKey spelledLikeTheRun = flow(THE_WHOLE_RUN.value());

    assertThat(moveOf(spelledLikeTheRun, ScenarioStory.INITIAL_STATE, "asked")).isTrue();
    assertThat(moveOf(THE_WHOLE_RUN, ScenarioStory.INITIAL_STATE, "settled")).isTrue();

    assertThat(rules.storyStatesOf(GROW, RUN, spelledLikeTheRun))
        .containsExactly(entry(STORY, "asked"));
    assertThat(rules.storyStatesOf(GROW, RUN, THE_WHOLE_RUN))
        .containsExactly(entry(STORY, "settled"));
  }

  @Test
  void a_story_no_request_has_moved_is_at_no_state_at_all() {
    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW)).isEmpty();
    assertThat(rules.storyStatesOf(GROW, RUN, flow("process:pi_sim_123"))).isEmpty();
  }

  @Test
  void a_story_moves_only_from_the_state_the_mover_saw_it_in() {
    assertThat(moveOf(ONE_FLOW, ScenarioStory.INITIAL_STATE, "asked")).isTrue();
    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW)).containsExactly(entry(STORY, "asked"));

    assertThat(moveOf(ONE_FLOW, ScenarioStory.INITIAL_STATE, "settled")).isFalse();
    assertThat(moveOf(ONE_FLOW, "declined", "settled")).isFalse();
    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW)).containsExactly(entry(STORY, "asked"));

    assertThat(moveOf(ONE_FLOW, "asked", "settled")).isTrue();
    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW)).containsExactly(entry(STORY, "settled"));
  }

  @Test
  void a_story_sent_back_to_its_start_moves_off_it_again_and_only_once() {
    moveOf(ONE_FLOW, ScenarioStory.INITIAL_STATE, "asked");

    assertThat(moveOf(ONE_FLOW, "asked", ScenarioStory.INITIAL_STATE)).isTrue();
    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW))
        .containsExactly(entry(STORY, ScenarioStory.INITIAL_STATE));
    assertThat(moveOf(ONE_FLOW, ScenarioStory.INITIAL_STATE, "asked")).isTrue();
    assertThat(moveOf(ONE_FLOW, ScenarioStory.INITIAL_STATE, "asked")).isFalse();
  }

  @Test
  void one_flow_holds_its_own_state_of_a_story_and_every_other_flow_holds_its_own() {
    assertThat(moveOf(flow("process:pi_sim_123"), ScenarioStory.INITIAL_STATE, "asked")).isTrue();

    assertThat(rules.storyStatesOf(GROW, RUN, flow("process:pi_sim_123")))
        .containsExactly(entry(STORY, "asked"));
    assertThat(rules.storyStatesOf(GROW, RUN, flow("process:pi_sim_456"))).isEmpty();
    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW)).isEmpty();
    assertThat(rules.storyStatesOf(GROW, OTHER_RUN, flow("process:pi_sim_123"))).isEmpty();
    assertThat(rules.storyStatesOf(STRIPE, RUN, flow("process:pi_sim_123"))).isEmpty();
    assertThat(moveOf(flow("process:pi_sim_456"), ScenarioStory.INITIAL_STATE, "asked")).isTrue();
    assertThat(moveOf(ONE_FLOW, ScenarioStory.INITIAL_STATE, "asked")).isTrue();
  }

  @Test
  void a_story_lives_in_the_database_and_not_in_the_store_that_moved_it() {
    assertThat(moveOf(ONE_FLOW, ScenarioStory.INITIAL_STATE, "asked")).isTrue();

    ScenarioRules restarted =
        new JdbcScenarioRules(jdbc, json, ids, clock, new TransactionTemplate(transactionManager));

    assertThat(restarted.storyStatesOf(GROW, RUN, ONE_FLOW)).containsExactly(entry(STORY, "asked"));
    assertThat(restarted.moveStory(GROW, RUN, ONE_FLOW, STORY, "asked", "settled")).isTrue();
    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW)).containsExactly(entry(STORY, "settled"));
  }

  @Test
  void only_one_of_many_concurrent_requests_takes_a_step_off_the_state_they_all_saw()
      throws InterruptedException {
    assertThat(theWinnersAmong(8, ScenarioStory.INITIAL_STATE, "asked")).isEqualTo(1);
    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW)).containsExactly(entry(STORY, "asked"));

    assertThat(theWinnersAmong(8, "asked", "settled")).isEqualTo(1);
    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW)).containsExactly(entry(STORY, "settled"));
  }

  private int theWinnersAmong(int callers, String from, String to) throws InterruptedException {
    CyclicBarrier together = new CyclicBarrier(callers);
    List<Callable<Boolean>> attempts = new ArrayList<>();
    for (int caller = 0; caller < callers; caller++) {
      attempts.add(
          () -> {
            together.await();
            return rules.moveStory(GROW, RUN, ONE_FLOW, STORY, from, to);
          });
    }
    List<Future<Boolean>> answers;
    try (ExecutorService callersPool = Executors.newFixedThreadPool(callers)) {
      answers = callersPool.invokeAll(attempts);
    }
    return (int)
        answers.stream()
            .map(ScenarioRulePersistenceIntegrationTest::valueOf)
            .filter(won -> won)
            .count();
  }

  private boolean moveOf(StoryKey key, String from, String to) {
    return rules.moveStory(GROW, RUN, key, STORY, from, to);
  }

  @Test
  void
      the_stories_of_one_key_are_read_back_named_in_one_order_whatever_order_they_were_moved_in_and_twelve_stories_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    List<String> theStoriesThisRunHolds = theStoriesOfThatWidth();
    for (String story : theStoriesThisRunHolds.reversed()) {
      assertThat(rules.moveStory(GROW, RUN, ONE_FLOW, story, ScenarioStory.INITIAL_STATE, "asked"))
          .isTrue();
    }
    assertThat(
            rules.moveStory(
                GROW, RUN, ONE_FLOW, theStoriesThisRunHolds.getLast(), "asked", "settled"))
        .isTrue();

    SequencedMap<String, String> held = rules.storyStatesOf(GROW, RUN, ONE_FLOW);

    assertThat(List.copyOf(held.keySet()))
        .as(
            THE_SCENARIO_SCREEN_LISTS_ONE_ROW_PER_STORY_IN_THIS_ORDER_AND_TWELVE_LEAVE_479001600_ORDERS)
        .isEqualTo(theStoriesThisRunHolds);
    assertThat(held)
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  private static List<String> theStoriesOfThatWidth() {
    List<String> named = new ArrayList<>();
    for (int at = 1; at <= THE_STORIES_A_KEY_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_HOLDS; at++) {
      named.add("story-" + (char) ('a' + at - 1));
    }
    return List.copyOf(named);
  }

  @Test
  void a_story_key_the_kernel_refuses_is_one_the_story_table_itself_could_never_have_held() {
    assertThatThrownBy(() -> storedDirectlyUnder("process:900001\u0000x"))
        .isInstanceOf(DataAccessException.class);
    String widerThanTheColumn = "process:" + ids.hex(8000);
    String namingNoSpace = ids.digits(6);

    assertThatThrownBy(() -> storedDirectlyUnder(widerThanTheColumn))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> storedDirectlyUnder(namingNoSpace))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> storedDirectlyUnder("process:"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void the_longest_story_key_the_kernel_accepts_is_one_the_story_table_takes() {
    StoryKey longest = flow("process:" + ids.hex(StoryKey.LONGEST_KEY - "process:".length()));

    assertThat(moveOf(longest, ScenarioStory.INITIAL_STATE, "asked")).isTrue();
    assertThat(rules.storyStatesOf(GROW, RUN, longest)).containsExactly(entry(STORY, "asked"));
  }

  private void storedDirectlyUnder(String storyKey) {
    jdbc.sql(
            """
            insert into simulator_scenario_story_state
                (provider, test_run_id, story, keyed_on, story_key, state, updated_at)
            values (?, ?, ?, 'FLOW', ?, 'asked', now())
            """)
        .param(GROW.value())
        .param(RUN.value())
        .param(STORY)
        .param(storyKey)
        .update();
  }

  @Test
  void clearing_a_test_run_sends_every_story_of_that_run_back_to_its_start() {
    moveOf(ONE_FLOW, ScenarioStory.INITIAL_STATE, "asked");
    moveOf(flow("process:pi_sim_123"), ScenarioStory.INITIAL_STATE, "asked");
    rules.moveStory(GROW, OTHER_RUN, ONE_FLOW, STORY, ScenarioStory.INITIAL_STATE, "asked");

    rules.clear(GROW, RUN);

    assertThat(rules.storyStatesOf(GROW, RUN, ONE_FLOW)).isEmpty();
    assertThat(rules.storyStatesOf(GROW, RUN, flow("process:pi_sim_123"))).isEmpty();
    assertThat(rules.storyStatesOf(GROW, OTHER_RUN, ONE_FLOW))
        .containsExactly(entry(STORY, "asked"));

    rules.clearAll(GROW);

    assertThat(rules.storyStatesOf(GROW, OTHER_RUN, ONE_FLOW)).isEmpty();
  }

  @Test
  void the_table_refuses_a_second_state_of_one_story_against_one_flow() {
    moveOf(ONE_FLOW, ScenarioStory.INITIAL_STATE, "asked");

    JdbcClient.StatementSpec aSecondState =
        jdbc.sql(
                """
                insert into simulator_scenario_story_state
                    (provider, test_run_id, story, keyed_on, story_key, state, updated_at)
                values (?, ?, ?, 'FLOW', ?, ?, now())
                """)
            .params(GROW.value(), RUN.value(), STORY, ONE_FLOW_VALUE, "settled");

    assertThatThrownBy(aSecondState::update).isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest(name = "the table refuses a label made only of {0}")
  @MethodSource(
      "dev.marcinromanowski.pspsim.kernel.scenario.ScenarioSelectionTest#theBlanksNoLabelMayBeMadeOf")
  void the_table_refuses_every_label_the_reader_would_refuse_to_read_back(
      String name, String blank) {
    ScenarioRule added = decline();

    assertThatThrownBy(() -> labelledWith(added, blank))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aStoryStateOf(blank))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_table_refuses_a_label_of_more_characters_than_the_reader_will_read_back() {
    ScenarioRule added = decline();
    String longest = "\uD83D\uDCB3".repeat(ScenarioStory.LONGEST_NAME);

    assertThat(labelledWith(added, longest)).isEqualTo(1);
    assertThat(rules.find(GROW, RUN, added.scenarioId()).orElseThrow().story())
        .isEqualTo(new ScenarioStory(longest, StoryKeying.FLOW, null, null));
    assertThatThrownBy(() -> labelledWith(added, longest + "\uD83D\uDCB3"))
        .isInstanceOf(DataIntegrityViolationException.class);
    String oneCharacterTooLong = "s".repeat(ScenarioStory.LONGEST_NAME + 1);

    assertThatThrownBy(() -> labelledWith(added, oneCharacterTooLong))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private int labelledWith(ScenarioRule rule, String label) {
    return jdbc.sql(
            "update simulator_scenario_rule set story = ?, keyed_on = 'FLOW' where scenario_id = ?")
        .params(label, rule.scenarioId())
        .update();
  }

  private int aStoryStateOf(String state) {
    return jdbc.sql(
            """
            insert into simulator_scenario_story_state
                (provider, test_run_id, story, keyed_on, story_key, state, updated_at)
            values (?, ?, ?, 'FLOW', ?, ?, now())
            """)
        .params(GROW.value(), RUN.value(), STORY, ONE_FLOW_VALUE, state)
        .update();
  }

  @Test
  void the_table_refuses_a_rule_that_names_a_state_of_no_story() {
    ScenarioRule added = decline();

    JdbcClient.StatementSpec namingAStateOfNoStory =
        jdbc.sql("update simulator_scenario_rule set when_state = ? where scenario_id = ?")
            .params("asked", added.scenarioId());

    assertThatThrownBy(namingAStateOfNoStory::update)
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void no_two_rules_can_share_the_arrival_that_breaks_every_remaining_tie() {
    ScenarioRule first = decline();
    ScenarioRule second = decline();

    JdbcClient.StatementSpec takingTheArrivalOfTheFirst =
        jdbc.sql("update simulator_scenario_rule set seq = ? where scenario_id = ?")
            .params(first.arrival(), second.scenarioId());

    assertThatThrownBy(takingTheArrivalOfTheFirst::update)
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void refuses_to_store_a_rule_under_the_namespace_reserved_for_a_failed_resolution() {
    NewScenarioRule unresolved =
        new NewScenarioRule(
            GROW,
            TestRunId.UNKNOWN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure("701"),
            null);

    assertThatThrownBy(() -> rules.add(unresolved))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
  }

  @Test
  void a_story_step_comes_back_carrying_the_two_states_it_was_written_with() {
    ScenarioRule added =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.FixedSuccess(),
                new ScenarioStory(STORY, StoryKeying.FLOW, "asked", null)));

    ScenarioRule read = rules.find(GROW, RUN, added.scenarioId()).orElseThrow();
    assertThat(read.story()).isEqualTo(new ScenarioStory(STORY, StoryKeying.FLOW, "asked", null));
    assertThat(read.specificity()).isEqualTo(1);
    assertThat(rules.find(GROW, RUN, decline().scenarioId()).orElseThrow().story()).isNull();
  }

  @ParameterizedTest(name = "a {0} rule comes back as the fault it was written with")
  @MethodSource("theTransportFaults")
  void a_stored_transport_fault_comes_back_as_the_fault_it_was_written_with(
      String name, TransportFault fault) {
    ScenarioRule added =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.TransportFaultAlways(fault),
                null));

    ScenarioRule read = rules.find(GROW, RUN, added.scenarioId()).orElseThrow();
    assertThat(read.action()).isEqualTo(new ScenarioAction.TransportFaultAlways(fault));
  }

  private static List<Arguments> theTransportFaults() {
    return List.of(
        Arguments.of("SERVER_ERROR", new TransportFault.ServerError(504)),
        Arguments.of("TRUNCATED_BODY", new TransportFault.TruncatedBody()),
        Arguments.of("CORRUPTED_BODY", new TransportFault.CorruptedBody()),
        Arguments.of("DROPPED_CONNECTION", new TransportFault.DroppedConnection()),
        Arguments.of(
            "DELAYED_RESPONSE", new TransportFault.DelayedResponse(Duration.ofMillis(250))));
  }

  @Test
  void a_rule_that_answers_instead_of_the_operation_comes_back_as_the_status_it_was_written_with() {
    ScenarioRule added =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest().naming("settleSuspendedTransaction"),
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502)),
                null));

    ScenarioRule read = rules.find(GROW, RUN, added.scenarioId()).orElseThrow();
    assertThat(read.action())
        .isEqualTo(
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)));
  }

  @ParameterizedTest(name = "the table refuses a row answering {0} instead of the operation")
  @CsvSource({"TRUNCATED_BODY,", "CORRUPTED_BODY,", "DROPPED_CONNECTION,", "DELAYED_RESPONSE,250"})
  void the_table_refuses_a_row_that_replaces_an_operation_with_a_fault_that_needs_its_bytes(
      String fault, @Nullable Long delayMs) {
    ScenarioRule added =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502)),
                null));

    JdbcClient.StatementSpec carryingAFaultTheRowCannotHold =
        jdbc.sql(
                "update simulator_scenario_rule set action_fault = ?, action_http_status ="
                    + " null, action_delay_ms = ? where scenario_id = ?")
            .param(fault)
            .param(delayMs)
            .param(added.scenarioId());

    assertThatThrownBy(carryingAFaultTheRowCannotHold::update)
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_fixed_success_rule_survives_the_database_carrying_no_code_and_no_budget() {
    ScenarioRule added =
        rules.add(
            new NewScenarioRule(
                GROW,
                RUN,
                0,
                ScenarioMatch.anyRequest().naming("refundTransaction"),
                new ScenarioAction.FixedSuccess(),
                null));

    ScenarioRule read = rules.find(GROW, RUN, added.scenarioId()).orElseThrow();
    assertThat(read.action()).isEqualTo(new ScenarioAction.FixedSuccess());
    assertThat(read.match().operation()).isEqualTo("refundTransaction");
  }

  @Test
  void the_table_refuses_a_fixed_success_row_carrying_a_code_no_fixed_success_can_answer_with() {
    ScenarioRule added =
        rules.add(
            new NewScenarioRule(
                GROW, RUN, 0, ScenarioMatch.anyRequest(), new ScenarioAction.FixedSuccess(), null));

    JdbcClient.StatementSpec carryingACodeNoFixedSuccessAnswersWith =
        jdbc.sql(
                "update simulator_scenario_rule set action_provider_code = ? where"
                    + " scenario_id = ?")
            .params("813", added.scenarioId());

    assertThatThrownBy(carryingACodeNoFixedSuccessAnswersWith::update)
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_batch_of_rules_that_fails_partway_through_leaves_no_rule_of_that_batch_behind() {
    ScenarioRules colliding =
        new JdbcScenarioRules(
            jdbc,
            json,
            alwaysHanding(ids.hex(24)),
            clock,
            new TransactionTemplate(transactionManager));

    List<NewScenarioRule> batch =
        List.of(batched("settleSuspendedTransaction"), batched("refundTransaction"));

    assertThatThrownBy(() -> colliding.addAll(batch))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(rules.of(GROW, RUN)).isEmpty();
  }

  @Test
  void a_batch_of_rules_that_writes_is_stored_whole_and_in_the_order_it_was_given() {
    List<ScenarioRule> added =
        rules.addAll(List.of(batched("settleSuspendedTransaction"), batched("refundTransaction")));

    assertThat(added)
        .extracting(rule -> rule.match().operation())
        .containsExactly("settleSuspendedTransaction", "refundTransaction");
    assertThat(rules.of(GROW, RUN))
        .extracting(rule -> rule.match().operation())
        .containsExactly("refundTransaction", "settleSuspendedTransaction");
  }

  private static NewScenarioRule batched(String operation) {
    return new NewScenarioRule(
        GROW,
        RUN,
        0,
        ScenarioMatch.anyRequest().naming(operation),
        new ScenarioAction.FixedSuccess(),
        null);
  }

  private static IdGenerator alwaysHanding(String hex) {
    return new IdGenerator() {
      @Override
      public String hex(int length) {
        return hex;
      }

      @Override
      public String digits(int length) {
        return "1".repeat(length);
      }
    };
  }

  private static boolean valueOf(Future<Boolean> answer) {
    try {
      return answer.get();
    } catch (InterruptedException | ExecutionException failed) {
      throw new IllegalStateException("a concurrent consumer did not answer", failed);
    }
  }

  private ScenarioRule decline() {
    return rules.add(
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure("701"),
            null));
  }
}
