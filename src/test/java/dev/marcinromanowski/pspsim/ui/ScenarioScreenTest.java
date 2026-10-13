package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ScenarioScreenTest {

  private static final Instant AT = Instant.parse("2026-09-07T12:00:00Z");
  private static final TestRunId RUN = TestRunId.of("ui-scenarios");
  private static final ProviderId GROW = ProviderId.of("grow");
  private static final String SETTLE = "settleSuspendedTransaction";
  private static final String LOOKUP = "getTransactionInfo";
  private static final String CARD_TOKEN = "pm_that_must_not_be_read_off_a_monitor";
  private static final int PAGE = 3;
  private static final ScenarioScreen.Decisions NO_CALLS = scenarioId -> List.of();

  private static final ProviderVocabulary VOCABULARY =
      new ProviderVocabulary(
          GROW,
          List.of(
              new ProviderOperation(
                  SETTLE,
                  Set.of(),
                  "the payment process the call belongs to",
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  LOOKUP,
                  Set.of(),
                  "the payment process the call belongs to",
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
          Map.of(
              MatchFact.CUSTOMER_ID,
              "provider grow publishes no customer id",
              MatchFact.CURRENCY,
              "provider grow publishes no currency parameter"),
          new FailureCodes("[0-9]+", "a numeric err.id"),
          ProcessingStates.noneOfThem("this provider names no processing state"));

  @Test
  void
      the_rule_a_request_would_be_answered_by_stands_above_a_more_specific_rule_of_lower_priority() {
    ScenarioScreen.View view =
        screen(
            List.of(
                rule("scn_specific", 0, 1L, narrowedTo("merchant-1", "10.00")),
                rule("scn_urgent", 100, 2L, ScenarioMatch.anyRequest())));

    assertThat(namesOn(view)).containsExactly("scn_urgent", "scn_specific");
  }

  @Test
  void among_rules_of_one_priority_the_more_specific_stands_first_and_the_newest_breaks_a_tie() {
    ScenarioScreen.View view =
        screen(
            List.of(
                rule("scn_first", 0, 1L, ScenarioMatch.anyRequest()),
                rule("scn_newest", 0, 2L, ScenarioMatch.anyRequest()),
                rule("scn_narrow", 0, 3L, narrowedTo("merchant-1", null))));

    assertThat(namesOn(view)).containsExactly("scn_narrow", "scn_newest", "scn_first");
  }

  @Test
  void
      the_values_a_rule_matches_on_stay_off_the_screen_until_a_reader_opens_it_and_asks_for_them() {
    List<ScenarioRule> written = List.of(rule("scn_1", 0, 1L, narrowedTo("merchant-1", "10.00")));

    ScenarioScreen.Row closed = onlyRowOf(screen(written, null, false));
    ScenarioScreen.Row opened = onlyRowOf(screen(written, "scn_1", false));
    ScenarioScreen.Row asked = onlyRowOf(screen(written, "scn_1", true));

    assertThat(closed.match()).isEmpty();
    assertThat(closed.narrows()).containsExactly("match.merchantId", "match.amount");
    assertThat(opened.match())
        .extracting(Disclosure.Field::visibility)
        .containsOnly(Disclosure.Visibility.WITHHELD);
    assertThat(valuesOn(asked)).containsExactly("merchant-1", "10.00");
  }

  @Test
  void a_rule_that_holds_a_delivery_back_shows_the_length_of_the_hold_beside_its_action() {
    ScenarioScreen.Row held =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        ScenarioMatch.anyRequest().reporting("payment_process.completed"),
                        new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5)))),
                "scn_1",
                true));

    assertThat(held.action()).isEqualTo("DELIVERY_DELAYED");
    assertThat(held.delayMs()).isEqualTo(5000L);
    assertThat(held.fault()).isNull();
    assertThat(held.narrows()).containsExactly("match.eventType");
    assertThat(valuesOn(held)).containsExactly("payment_process.completed");
  }

  @Test
  void a_rule_that_holds_a_payment_in_flight_shows_when_it_settles_and_what_it_settles_as() {
    ScenarioScreen.Row settling =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        ScenarioMatch.anyRequest().naming(SETTLE),
                        new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), "813"))),
                "scn_1",
                true));

    assertThat(settling.action()).isEqualTo("PROCESSING_THEN_TERMINAL");
    assertThat(settling.settlesAfterMs()).isEqualTo(5000L);
    assertThat(settling.providerCode()).isEqualTo("813");
    assertThat(settling.delayMs()).isNull();
  }

  @Test
  void a_rule_that_holds_a_payment_in_flight_to_succeed_shows_the_moment_and_no_decline() {
    ScenarioScreen.Row settling =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        ScenarioMatch.anyRequest().naming(SETTLE),
                        new ScenarioAction.ProcessingThenTerminal(Duration.ofMillis(1500), null))),
                "scn_1",
                true));

    assertThat(settling.settlesAfterMs()).isEqualTo(1500L);
    assertThat(settling.providerCode()).isNull();
  }

  @Test
  void a_rule_that_answers_at_once_shows_no_moment_to_settle_in() {
    ScenarioScreen.Row answering =
        onlyRowOf(screen(List.of(rule("scn_1", 0, 1L, ScenarioMatch.anyRequest())), "scn_1", true));

    assertThat(answering.settlesAfterMs()).isNull();
  }

  @Test
  void a_rule_that_drops_a_delivery_shows_no_length_because_it_holds_nothing_back() {
    ScenarioScreen.Row dropped =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.DeliveryDropped())),
                null,
                false));

    assertThat(dropped.action()).isEqualTo("DELIVERY_DROPPED");
    assertThat(dropped.delayMs()).isNull();
  }

  @Test
  void a_header_a_rule_narrows_on_is_named_beside_the_facts_it_narrows_on() {
    ScenarioScreen.Row opened =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        new ScenarioMatch(
                            null,
                            null,
                            null,
                            null,
                            CARD_TOKEN,
                            null,
                            null,
                            null,
                            null,
                            Map.of("X-Simulator-Scenario", "capture-decline")))),
                "scn_1",
                false));

    assertThat(opened.narrows())
        .containsExactly("match.paymentMethodId", "match.header.X-Simulator-Scenario");
    assertThat(valuesOn(opened)).isEmpty();
  }

  @Test
  void a_rule_that_narrows_on_every_fact_the_model_carries_names_each_of_them_on_its_row() {
    ScenarioScreen.Row opened =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        new ScenarioMatch(
                            "merchant-1",
                            "order-1",
                            "trx_1",
                            "cus_1",
                            CARD_TOKEN,
                            Amount.of("10.00"),
                            "PLN",
                            SETTLE,
                            null,
                            Map.of()))),
                "scn_1",
                false));

    assertThat(opened.narrows())
        .containsExactly(
            "match.merchantId",
            "match.orderId",
            "match.providerObjectId",
            "match.customerId",
            "match.paymentMethodId",
            "match.amount",
            "match.currency");
    assertThat(opened.operation()).isEqualTo(SETTLE);
  }

  @Test
  void a_call_whose_outcome_the_provider_never_reached_shows_none_rather_than_an_invented_one() {
    ScenarioScreen.Row opened =
        onlyRowOf(
            screen(
                List.of(rule("scn_1", 0, 1L, ScenarioMatch.anyRequest())),
                "scn_1",
                false,
                scenarioId -> List.of(aCall("aud_1"), aCallTheProviderNeverAnswered("aud_2"))));

    assertThat(opened.decided()).isNotNull();
    assertThat(opened.decided().calls())
        .extracting(ScenarioScreen.Call::outcome)
        .containsExactly(null, "SUCCESS");
  }

  @Test
  void only_the_rule_a_reader_opened_reads_the_trail_for_the_calls_it_decided() {
    List<String> asked = new ArrayList<>();
    ScenarioScreen.Decisions counting =
        scenarioId -> {
          asked.add(scenarioId);
          return List.of();
        };

    ScenarioScreen.of(
        "grow",
        RUN.value(),
        List.of(
            rule("scn_1", 0, 1L, ScenarioMatch.anyRequest()),
            rule("scn_2", 0, 2L, ScenarioMatch.anyRequest())),
        VOCABULARY,
        Map.of(),
        "scn_2",
        false,
        PAGE,
        counting);

    assertThat(asked).containsExactly("scn_2");
  }

  @Test
  void the_calls_a_rule_decided_are_shown_newest_first() {
    ScenarioScreen.Row opened =
        onlyRowOf(
            screen(
                List.of(rule("scn_1", 0, 1L, ScenarioMatch.anyRequest())),
                "scn_1",
                false,
                scenarioId -> List.of(aCall("aud_1"), aCall("aud_2"))));

    assertThat(opened.decided()).isNotNull();
    assertThat(opened.decided().calls())
        .extracting(ScenarioScreen.Call::auditId)
        .containsExactly("aud_2", "aud_1");
  }

  @Test
  void a_rule_that_decided_more_calls_than_the_page_holds_says_the_page_is_not_all_of_them() {
    ScenarioScreen.Row filled =
        onlyRowOf(
            screen(
                List.of(rule("scn_1", 0, 1L, ScenarioMatch.anyRequest())),
                "scn_1",
                false,
                scenarioId -> List.of(aCall("aud_1"), aCall("aud_2"), aCall("aud_3"))));
    ScenarioScreen.Row shortOfThePage =
        onlyRowOf(
            screen(
                List.of(rule("scn_1", 0, 1L, ScenarioMatch.anyRequest())),
                "scn_1",
                false,
                scenarioId -> List.of(aCall("aud_1"), aCall("aud_2"))));

    assertThat(filled.decided()).isNotNull();
    assertThat(filled.decided().everyCallItDecided()).isFalse();
    assertThat(shortOfThePage.decided()).isNotNull();
    assertThat(shortOfThePage.decided().everyCallItDecided()).isTrue();
  }

  @Test
  void a_rule_answered_in_place_of_the_operation_is_told_apart_from_one_that_damages_the_answer() {
    TransportFault serverError = new TransportFault.ServerError(502);

    ScenarioScreen.Row instead =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.TransportFaultInsteadOfOperation(serverError)))));
    ScenarioScreen.Row afterwards =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.TransportFaultAlways(serverError)))));

    assertThat(instead.answeredWithoutTheOperation()).isTrue();
    assertThat(afterwards.answeredWithoutTheOperation()).isFalse();
    assertThat(instead.httpStatus()).isEqualTo(502);
    assertThat(afterwards.httpStatus()).isEqualTo(502);
  }

  @Test
  void what_a_rule_performs_is_on_its_row_so_a_tester_reads_it_without_opening_the_rule() {
    ScenarioScreen.Row declining =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.TerminalFailure("809")))));
    ScenarioScreen.Row holding =
        onlyRowOf(
            screen(
                List.of(
                    rule(
                        "scn_1",
                        0,
                        1L,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.TransportFaultAlways(
                            new TransportFault.DelayedResponse(Duration.ofMillis(1500)))))));

    assertThat(declining.action()).isEqualTo("TERMINAL_FAILURE");
    assertThat(declining.providerCode()).isEqualTo("809");
    assertThat(declining.fault()).isNull();
    assertThat(holding.action()).isEqualTo("TRANSPORT_FAULT");
    assertThat(holding.fault()).isEqualTo("DELAYED_RESPONSE");
    assertThat(holding.delayMs()).isEqualTo(1500L);
    assertThat(holding.providerCode()).isNull();
  }

  @Test
  void a_step_of_a_story_shows_the_state_it_waits_for_and_the_state_it_leaves() {
    ScenarioScreen.Row step =
        onlyRowOf(
            screen(
                List.of(
                    step(
                        "scn_1",
                        new ScenarioStory(
                            "psp-5xx-then-success", StoryKeying.FLOW, "Started", "Answered")))));

    assertThat(step.story()).isEqualTo("psp-5xx-then-success");
    assertThat(step.keyedOn()).isEqualTo("FLOW");
    assertThat(step.whenState()).isEqualTo("Started");
    assertThat(step.thenState()).isEqualTo("Answered");
  }

  @Test
  void a_rule_that_is_no_step_of_a_story_reports_no_story_rather_than_an_invented_one() {
    ScenarioScreen.Row plain =
        onlyRowOf(screen(List.of(rule("scn_1", 0, 1L, ScenarioMatch.anyRequest()))));

    assertThat(plain.story()).isNull();
    assertThat(plain.keyedOn()).isNull();
    assertThat(plain.whenState()).isNull();
    assertThat(plain.thenState()).isNull();
  }

  @Test
  void a_fact_this_provider_never_carries_is_named_in_the_providers_own_words() {
    ScenarioScreen.View view = screen(List.of());

    assertThat(view.vocabulary().factsThisProviderNeverCarries())
        .extracting(ScenarioScreen.Fact::property)
        .containsExactly("match.customerId", "match.currency");
    assertThat(view.vocabulary().factsThisProviderNeverCarries())
        .extracting(ScenarioScreen.Fact::instead)
        .contains("provider grow publishes no currency parameter");
    assertThat(view.vocabulary().operations()).containsExactly(SETTLE, LOOKUP);
  }

  @Test
  void the_screen_offers_the_whole_vocabulary_a_rule_can_be_written_in() {
    ScenarioScreen.Vocabulary vocabulary = screen(List.of()).vocabulary();

    assertThat(vocabulary.actionTypes())
        .containsExactly(
            "FIXED_SUCCESS",
            "TERMINAL_FAILURE",
            "TRANSPORT_FAULT",
            "TRANSPORT_FAULT_INSTEAD_OF_OPERATION",
            "PROCESSING_THEN_TERMINAL",
            "DELIVERY_DELAYED",
            "DELIVERY_DUPLICATED",
            "DELIVERY_DROPPED");
    assertThat(vocabulary.faults())
        .containsExactly(
            "SERVER_ERROR",
            "TRUNCATED_BODY",
            "CORRUPTED_BODY",
            "DROPPED_CONNECTION",
            "DELAYED_RESPONSE");
    assertThat(vocabulary.keyings()).containsExactly("FLOW", "TEST_RUN");
  }

  @Test
  void a_fact_this_provider_never_carries_is_one_no_form_should_offer_a_tester() {
    ScenarioScreen.Vocabulary vocabulary = screen(List.of()).vocabulary();

    assertThat(vocabulary.carries("match.customerId")).isFalse();
    assertThat(vocabulary.carries("match.currency")).isFalse();
    assertThat(vocabulary.carries("match.merchantId")).isTrue();
    assertThat(vocabulary.carries("match.amount")).isTrue();
  }

  @Test
  void the_state_a_story_of_the_whole_test_run_stands_in_is_on_the_screen() {
    ScenarioScreen.View view =
        ScenarioScreen.of(
            "grow",
            RUN.value(),
            List.of(),
            VOCABULARY,
            Map.of("declines-every-third-call", "TwoSoFar"),
            null,
            false,
            PAGE,
            NO_CALLS);

    assertThat(view.storiesKeyedOnTheTestRun())
        .containsExactly(new ScenarioScreen.Instance("declines-every-third-call", "TwoSoFar"));
  }

  private static List<String> namesOn(ScenarioScreen.View view) {
    List<String> named = new ArrayList<>();
    view.rules().forEach(row -> named.add(row.scenarioId()));
    return named;
  }

  private static List<String> valuesOn(ScenarioScreen.Row row) {
    List<String> values = new ArrayList<>();
    row.match().forEach(field -> values.addAll(field.values()));
    return values;
  }

  private static ScenarioScreen.Row onlyRowOf(ScenarioScreen.View view) {
    assertThat(view.rules()).hasSize(1);
    return view.rules().get(0);
  }

  private static ScenarioScreen.View screen(List<ScenarioRule> rules) {
    return screen(rules, null, false);
  }

  private static ScenarioScreen.View screen(
      List<ScenarioRule> rules, @Nullable String open, boolean valuesAsked) {
    return screen(rules, open, valuesAsked, NO_CALLS);
  }

  private static ScenarioScreen.View screen(
      List<ScenarioRule> rules,
      @Nullable String open,
      boolean valuesAsked,
      ScenarioScreen.Decisions decisions) {
    return ScenarioScreen.of(
        "grow", RUN.value(), rules, VOCABULARY, Map.of(), open, valuesAsked, PAGE, decisions);
  }

  private static ScenarioMatch narrowedTo(@Nullable String merchantId, @Nullable String amount) {
    return new ScenarioMatch(
        merchantId,
        null,
        null,
        null,
        null,
        amount == null ? null : Amount.of(amount),
        null,
        null,
        null,
        Map.of());
  }

  private static ScenarioRule rule(
      String scenarioId, int priority, long arrival, ScenarioMatch match) {
    return rule(scenarioId, priority, arrival, match, new ScenarioAction.FixedSuccess());
  }

  private static ScenarioRule rule(
      String scenarioId, int priority, long arrival, ScenarioMatch match, ScenarioAction action) {
    return new ScenarioRule(scenarioId, GROW, RUN, priority, arrival, match, action, null, AT);
  }

  @Test
  void a_preview_shows_one_row_per_rule_in_the_order_the_preset_expanded_them() {
    ScenarioScreen.Preview preview =
        ScenarioScreen.previewOf(
            "cancel-hold-success",
            "grow",
            0,
            List.of(
                proposing(SETTLE, new ScenarioAction.FixedSuccess()),
                proposing("refundTransaction", new ScenarioAction.TerminalFailure("813"))),
            "{}");

    assertThat(preview.rules())
        .extracting(ScenarioScreen.Proposed::operation)
        .containsExactly(SETTLE, "refundTransaction");
    assertThat(preview.rules())
        .extracting(ScenarioScreen.Proposed::action)
        .containsExactly("FIXED_SUCCESS", "TERMINAL_FAILURE");
    assertThat(preview.rules().get(1).providerCode()).isEqualTo("813");
  }

  @Test
  void a_preview_names_the_preset_the_provider_the_priority_and_carries_the_document() {
    ScenarioScreen.Preview preview =
        ScenarioScreen.previewOf(
            "cancel-hold-success",
            "grow",
            7,
            List.of(proposing(SETTLE, new ScenarioAction.FixedSuccess())),
            "{\"provider\":\"grow\"}");

    assertThat(preview.preset()).isEqualTo("cancel-hold-success");
    assertThat(preview.provider()).isEqualTo("grow");
    assertThat(preview.priority()).isEqualTo(7);
    assertThat(preview.document()).isEqualTo("{\"provider\":\"grow\"}");
  }

  @Test
  void a_preview_shows_the_fault_the_status_and_the_delay_a_faulted_rule_would_answer_with() {
    ScenarioScreen.Preview preview =
        ScenarioScreen.previewOf(
            "psp-5xx-then-success",
            "grow",
            0,
            List.of(
                proposing(
                    SETTLE,
                    new ScenarioAction.TransportFaultAlways(
                        new TransportFault.DelayedResponse(Duration.ofSeconds(2)))),
                proposing(
                    SETTLE,
                    new ScenarioAction.TransportFaultInsteadOfOperation(
                        new TransportFault.ServerError(502))),
                proposing(
                    SETTLE,
                    new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null))),
            "{}");

    assertThat(preview.rules().get(0).fault()).isEqualTo("DELAYED_RESPONSE");
    assertThat(preview.rules().get(0).delayMs()).isEqualTo(2000L);
    assertThat(preview.rules().get(1).httpStatus()).isEqualTo(502);
    assertThat(preview.rules().get(2).settlesAfterMs()).isEqualTo(5000L);
  }

  @Test
  void a_preview_shows_the_step_of_the_story_a_rule_would_be_written_as() {
    ScenarioScreen.Preview preview =
        ScenarioScreen.previewOf(
            "psp-5xx-then-success",
            "grow",
            0,
            List.of(
                new NewScenarioRule(
                    GROW,
                    RUN,
                    0,
                    ScenarioMatch.anyRequest().naming(SETTLE),
                    new ScenarioAction.FixedSuccess(),
                    new ScenarioStory("a-story", StoryKeying.TEST_RUN, "Started", "Answered"))),
            "{}");

    assertThat(preview.rules())
        .singleElement()
        .satisfies(
            rule -> {
              assertThat(rule.story()).isEqualTo("a-story");
              assertThat(rule.keyedOn()).isEqualTo("TEST_RUN");
              assertThat(rule.whenState()).isEqualTo("Started");
              assertThat(rule.thenState()).isEqualTo("Answered");
            });
  }

  @Test
  void a_form_that_carried_no_document_field_at_all_names_the_document_property() {
    ScenarioScreen.Pasted pasted =
        ScenarioScreen.pastedInto(
            null,
            RUN.value(),
            read -> {
              throw new IllegalStateException("the reader should not have been asked");
            });

    assertThat(pasted)
        .isInstanceOfSatisfying(
            ScenarioScreen.Pasted.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message()).isEqualTo(ScenarioScreen.NOTHING_WAS_PASTED));
  }

  @Test
  void an_empty_box_names_the_document_property_and_no_reader_is_asked_to_read_it() {
    List<String> asked = new ArrayList<>();

    ScenarioScreen.Pasted pasted =
        ScenarioScreen.pastedInto(
            "   ",
            RUN.value(),
            read -> {
              asked.add(read);
              throw new IllegalStateException("the reader should not have been asked");
            });

    assertThat(pasted)
        .isInstanceOfSatisfying(
            ScenarioScreen.Pasted.Unreadable.class,
            unreadable ->
                assertThat(unreadable.message()).isEqualTo(ScenarioScreen.NOTHING_WAS_PASTED));
    assertThat(asked).isEmpty();
  }

  @Test
  void a_box_holding_text_no_reader_can_parse_names_the_document_property_and_not_the_text() {
    ScenarioScreen.Pasted pasted =
        ScenarioScreen.pastedInto(
            "not json at all",
            RUN.value(),
            read -> {
              throw new IllegalArgumentException("unexpected character");
            });

    assertThat(pasted)
        .isInstanceOfSatisfying(
            ScenarioScreen.Pasted.Unreadable.class,
            unreadable -> {
              assertThat(unreadable.message()).isEqualTo(ScenarioScreen.THE_DOCUMENT_IS_NOT_JSON);
              assertThat(unreadable.message()).doesNotContain("not json at all");
            });
  }

  @Test
  void a_pasted_document_is_taken_into_the_run_the_screen_is_on_and_not_the_one_it_names() {
    ScenarioDocument produced =
        new ScenarioDocument("another-run", "grow", "cancel-hold-success", List.of());

    ScenarioScreen.Pasted pasted = ScenarioScreen.pastedInto("{}", RUN.value(), read -> produced);

    assertThat(pasted)
        .isInstanceOfSatisfying(
            ScenarioScreen.Pasted.Document.class,
            document -> {
              assertThat(document.document().testRunId()).isEqualTo(RUN.value());
              assertThat(document.document().provider()).isEqualTo("grow");
              assertThat(document.document().preset()).isEqualTo("cancel-hold-success");
            });
  }

  @Test
  void rules_a_screen_did_not_write_are_refused_as_a_rejection_naming_the_rule_it_did_not_hold() {
    assertThat(ScenarioScreen.theRefusalOfRulesItDidNotWrite())
        .isEqualTo(
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(
                    ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD)));
  }

  private static NewScenarioRule proposing(String operation, ScenarioAction action) {
    return new NewScenarioRule(
        GROW, RUN, 0, ScenarioMatch.anyRequest().naming(operation), action, null);
  }

  private static ScenarioRule step(String scenarioId, ScenarioStory story) {
    return new ScenarioRule(
        scenarioId,
        GROW,
        RUN,
        0,
        1L,
        ScenarioMatch.anyRequest().naming(SETTLE),
        new ScenarioAction.FixedSuccess(),
        story,
        AT);
  }

  private static AuditRecord aCallTheProviderNeverAnswered(String auditId) {
    AuditRecord answered = aCall(auditId);
    return new AuditRecord(
        answered.auditId(),
        answered.testRunId(),
        answered.resolutionRule(),
        answered.provider(),
        null,
        answered.origin(),
        AuditDirection.INBOUND_REQUEST,
        null,
        answered.method(),
        answered.path(),
        null,
        null,
        null,
        answered.scenarioId(),
        answered.attempt(),
        answered.createdAt(),
        Map.of(),
        Map.of(),
        null);
  }

  private static AuditRecord aCall(String auditId) {
    return new AuditRecord(
        auditId,
        RUN,
        TestRunResolutionRule.TEST_HEADER,
        GROW,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.SUCCESS,
        "POST",
        "/api/light/server/1.0/" + SETTLE,
        200,
        "trx_1",
        null,
        "scn_1",
        1,
        AT,
        Map.of(),
        Map.of(),
        null);
  }
}
