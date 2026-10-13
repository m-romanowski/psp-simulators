package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class APresetInWordsTest {

  private static final ProviderId GROW = ProviderId.of("grow");
  private static final TestRunId RUN = TestRunId.of("ui-preset-words");
  private static final String SETTLE = "settleSuspendedTransaction";
  private static final String CREATE = "createPaymentProcess";
  private static final String LOOKUP = "getTransactionInfo";
  private static final Duration FIVE_SECONDS = Duration.ofSeconds(5);
  private static final String HELD = "answered-without-the-capture";
  private static final String IN_PART = "refunded-in-part";

  @Test
  void an_operation_that_is_let_through_succeeds_in_words() {
    assertThat(theRule(CREATE, null, new ScenarioAction.FixedSuccess()))
        .isEqualTo("createPaymentProcess succeeds");
  }

  @Test
  void an_operation_that_comes_to_rest_failed_names_the_code_it_fails_with() {
    assertThat(theRule(SETTLE, null, new ScenarioAction.TerminalFailure("809")))
        .isEqualTo("settleSuspendedTransaction fails with code 809");
  }

  @Test
  void a_fault_after_the_operation_says_the_operation_happened_and_what_became_of_its_answer() {
    assertThat(
            theRule(
                SETTLE,
                null,
                new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection())))
        .isEqualTo(
            "settleSuspendedTransaction happens, and the connection drops before its answer is"
                + " complete");
    assertThat(
            theRule(
                SETTLE,
                null,
                new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(503))))
        .isEqualTo("settleSuspendedTransaction happens, and its answer is replaced by HTTP 503");
    assertThat(
            theRule(
                SETTLE,
                null,
                new ScenarioAction.TransportFaultAlways(new TransportFault.TruncatedBody())))
        .isEqualTo("settleSuspendedTransaction happens, and its answer arrives cut short");
    assertThat(
            theRule(
                SETTLE,
                null,
                new ScenarioAction.TransportFaultAlways(new TransportFault.CorruptedBody())))
        .isEqualTo(
            "settleSuspendedTransaction happens, and its answer arrives with a damaged body");
    assertThat(
            theRule(
                SETTLE,
                null,
                new ScenarioAction.TransportFaultAlways(
                    new TransportFault.DelayedResponse(FIVE_SECONDS))))
        .isEqualTo("settleSuspendedTransaction happens, and its answer arrives 5 seconds late");
  }

  @Test
  void a_fault_in_place_of_the_operation_says_the_operation_did_not_happen() {
    assertThat(
            theRule(
                SETTLE,
                null,
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502))))
        .isEqualTo("settleSuspendedTransaction is answered HTTP 502 and does not happen at all");
  }

  @Test
  void an_outcome_held_in_flight_says_when_it_comes_to_rest_and_as_what() {
    assertThat(theRule(LOOKUP, null, new ScenarioAction.ProcessingThenTerminal(FIVE_SECONDS, null)))
        .isEqualTo(
            "getTransactionInfo answers processing and comes to rest 5 seconds later as a success");
    assertThat(
            theRule(LOOKUP, null, new ScenarioAction.ProcessingThenTerminal(FIVE_SECONDS, "809")))
        .isEqualTo(
            "getTransactionInfo answers processing and comes to rest 5 seconds later as a failure"
                + " with code 809");
  }

  @Test
  void a_delivery_rule_is_about_the_webhook_and_names_the_event_when_it_narrows_on_one() {
    assertThat(theRule(null, null, new ScenarioAction.DeliveryDelayed(FIVE_SECONDS)))
        .isEqualTo("its webhook is held back by 5 seconds");
    assertThat(theRule(null, "completed", new ScenarioAction.DeliveryDelayed(FIVE_SECONDS)))
        .isEqualTo("its webhook completed is held back by 5 seconds");
    assertThat(theRule(null, null, new ScenarioAction.DeliveryDuplicated()))
        .isEqualTo("its webhook is delivered twice");
    assertThat(theRule(null, null, new ScenarioAction.DeliveryDropped()))
        .isEqualTo("its webhook is never delivered");
  }

  @Test
  void a_span_no_whole_unit_counts_is_read_back_as_the_span_itself() {
    assertThat(theRule(null, null, new ScenarioAction.DeliveryDelayed(Duration.ofMillis(1500))))
        .isEqualTo("its webhook is held back by PT1.5S");
    assertThat(theRule(null, null, new ScenarioAction.DeliveryDelayed(Duration.ofMinutes(2))))
        .isEqualTo("its webhook is held back by 2 minutes");
  }

  @Test
  void a_rule_that_is_a_step_of_a_story_says_what_the_story_stands_on_and_the_states_it_moves() {
    assertThat(
            theStep(
                SETTLE,
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502)),
                StoryKeying.FLOW,
                ScenarioStory.INITIAL_STATE,
                HELD))
        .isEqualTo(
            "settleSuspendedTransaction is answered HTTP 502 and does not happen at all, one step"
                + " of the story psp-5xx-then-success kept per the flow of one call, taken while"
                + " that story stands at Started, and leaving that story at"
                + " answered-without-the-capture");
  }

  @Test
  void a_step_that_waits_for_no_state_and_leaves_the_story_where_it_was_says_both() {
    assertThat(theStep(SETTLE, new ScenarioAction.FixedSuccess(), StoryKeying.TEST_RUN, null, null))
        .isEqualTo(
            "settleSuspendedTransaction succeeds, one step of the story psp-5xx-then-success kept"
                + " per the whole test run, taken whatever state that story stands in, and leaving"
                + " that story where it was");
  }

  @Test
  void a_flow_of_more_than_two_rules_reads_as_one_line_per_rule_and_asserts_no_order() {
    List<String> read =
        APresetInWords.theRulesOf(
            List.of(
                rule(CREATE, null, new ScenarioAction.FixedSuccess()),
                rule("confirm", null, new ScenarioAction.FixedSuccess()),
                rule(SETTLE, null, new ScenarioAction.TerminalFailure("809"))));

    assertThat(read)
        .containsExactly(
            "createPaymentProcess succeeds",
            "confirm succeeds",
            "settleSuspendedTransaction fails with code 809");
    assertThat(read).allSatisfy(line -> assertThat(line).doesNotContain(", then "));
  }

  @Test
  void two_rules_that_each_answer_processing_keep_their_own_then_inside_their_own_line() {
    List<String> read =
        APresetInWords.theRulesOf(
            List.of(
                rule(CREATE, null, new ScenarioAction.ProcessingThenTerminal(FIVE_SECONDS, null)),
                rule(
                    LOOKUP, null, new ScenarioAction.ProcessingThenTerminal(FIVE_SECONDS, "809"))));

    assertThat(read).hasSize(2);
    assertThat(read)
        .allSatisfy(line -> assertThat(line).doesNotContain("then").doesNotContain(", then "));
  }

  @Test
  void two_rules_that_differ_only_in_the_story_state_they_carry_do_not_read_alike() {
    List<String> read =
        APresetInWords.theRulesOf(
            List.of(
                step(
                    "POST /v1/refunds",
                    new ScenarioAction.FixedSuccess(),
                    StoryKeying.FLOW,
                    ScenarioStory.INITIAL_STATE,
                    IN_PART),
                step(
                    "POST /v1/refunds",
                    new ScenarioAction.FixedSuccess(),
                    StoryKeying.FLOW,
                    IN_PART,
                    null)));

    assertThat(read).doesNotHaveDuplicates();
    assertThat(read.get(0)).contains("stands at Started", "leaving that story at " + IN_PART);
    assertThat(read.get(1)).contains("stands at " + IN_PART, "leaving that story where it was");
  }

  @Test
  void a_rule_narrowing_on_a_fact_these_words_do_not_read_is_refused_rather_than_left_out() {
    assertThatThrownBy(
            () ->
                APresetInWords.theRuleOf(
                    new NewScenarioRule(
                        GROW,
                        RUN,
                        0,
                        ScenarioMatch.anyRequest().naming(SETTLE).reporting("completed"),
                        new ScenarioAction.FixedSuccess(),
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("match.eventType");

    assertThatThrownBy(() -> APresetInWords.theRuleOf(narrowedOnAnAmount()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a preset of provider grow")
        .hasMessageContaining("match.amount")
        .hasMessageContaining("tell a tester the rule fires where it does not");

    assertThatCode(() -> APresetInWords.theRuleOf(aDeliveryNarrowedOnAnAmount()))
        .as(
            "a webhook rule is narrowed on its event type, and a narrowing these words do not read"
                + " is refused on that path as it is on the path of a call")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("match.amount");

    assertThatThrownBy(() -> APresetInWords.theRuleOf(narrowedOnAHeader()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("match.header.X-Trace");
  }

  @Test
  void a_rule_that_shapes_a_call_and_names_no_operation_is_refused_as_no_webhook_of_any_kind() {
    assertThatThrownBy(
            () ->
                APresetInWords.theRuleOf(
                    new NewScenarioRule(
                        GROW,
                        RUN,
                        0,
                        ScenarioMatch.anyRequest(),
                        new ScenarioAction.FixedSuccess(),
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("narrows on no operation")
        .hasMessageContaining("as though the rule were about a webhook");
  }

  @Test
  void a_rule_that_shapes_a_delivery_and_names_an_operation_is_refused_as_no_call_of_any_kind() {
    assertThatThrownBy(
            () ->
                APresetInWords.theRuleOf(
                    new NewScenarioRule(
                        GROW,
                        RUN,
                        0,
                        ScenarioMatch.anyRequest().naming(SETTLE),
                        new ScenarioAction.DeliveryDuplicated(),
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("shapes a webhook delivery and narrows on the operation")
        .hasMessageContaining("tell a tester the call itself is shaped");
  }

  @Test
  void a_flow_this_provider_expands_is_read_off_the_rules_it_expanded_into() {
    APresetInWords read =
        APresetInWords.of(
            ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
            new PresetExpansion.Rules(
                List.of(
                    rule(CREATE, null, new ScenarioAction.FixedSuccess()),
                    rule(SETTLE, null, new ScenarioAction.FixedSuccess()))));

    assertThat(read.preset()).isEqualTo("preauth-success-capture-success");
    assertThat(read.whatItDoes())
        .containsExactly("createPaymentProcess succeeds", "settleSuspendedTransaction succeeds");
    assertThat(read.whyThisProviderCannotExpressIt()).isNull();
    assertThat(read.inTheChooser()).isEqualTo("preauth-success-capture-success · 2 rules");
  }

  @Test
  void a_flow_of_one_rule_is_counted_in_the_singular_in_the_chooser() {
    APresetInWords read =
        APresetInWords.of(
            ScenarioPreset.REFUND_FULL_SUCCESS,
            new PresetExpansion.Rules(
                List.of(rule("refundTransaction", null, new ScenarioAction.FixedSuccess()))));

    assertThat(read.inTheChooser()).isEqualTo("refund-full-success · 1 rule");
  }

  @Test
  void a_flow_this_provider_cannot_express_carries_the_providers_own_words_and_writes_no_rule() {
    APresetInWords read =
        APresetInWords.of(
            ScenarioPreset.WEBHOOK_OUT_OF_ORDER,
            new PresetExpansion.NotExpressible("one grow process carries one callback"));

    assertThat(read.preset()).isEqualTo("webhook-out-of-order");
    assertThat(read.whatItDoes()).isEmpty();
    assertThat(read.whyThisProviderCannotExpressIt())
        .isEqualTo("one grow process carries one callback");
    assertThat(read.inTheChooser())
        .isEqualTo("webhook-out-of-order · this provider cannot express it");
  }

  @Test
  void every_ready_made_flow_is_offered_with_what_this_provider_does_with_it() {
    ProviderModuleDouble module =
        new ProviderModuleDouble("grow")
            .expanding(
                ScenarioPreset.CARD_ADD_SUCCESS,
                new PresetExpansion.Rules(
                    List.of(rule(CREATE, null, new ScenarioAction.FixedSuccess()))))
            .expanding(
                ScenarioPreset.PSP_5XX_THEN_SUCCESS,
                new PresetExpansion.Rules(
                    List.of(
                        step(
                            SETTLE,
                            new ScenarioAction.TransportFaultInsteadOfOperation(
                                new TransportFault.ServerError(502)),
                            StoryKeying.FLOW,
                            ScenarioStory.INITIAL_STATE,
                            HELD),
                        step(
                            SETTLE,
                            new ScenarioAction.FixedSuccess(),
                            StoryKeying.FLOW,
                            HELD,
                            null))));

    List<APresetInWords> offered = APresetInWords.theOnesOffered(module, RUN);

    assertThat(offered).hasSize(ScenarioPreset.values().length);
    assertThat(offered)
        .extracting(APresetInWords::preset)
        .doesNotHaveDuplicates()
        .containsExactly(
            "card-add-success",
            "card-add-failed",
            "card-add-callback-delayed",
            "preauth-success-capture-success",
            "preauth-success-capture-decline",
            "preauth-success-capture-timeout-then-success",
            "capture-accepted-response-lost",
            "cancel-hold-success",
            "refund-full-success",
            "refund-partial-then-full",
            "psp-5xx-then-success",
            "webhook-delayed",
            "webhook-duplicate",
            "webhook-out-of-order",
            "webhook-invalid-signature",
            "status-lookup-processing-then-success",
            "status-lookup-processing-then-failed");
    assertThat(theOne(offered, "card-add-success").whatItDoes())
        .containsExactly("createPaymentProcess succeeds");
    assertThat(theOne(offered, "psp-5xx-then-success").whatItDoes()).hasSize(2);
    assertThat(theOne(offered, "psp-5xx-then-success").inTheChooser())
        .isEqualTo("psp-5xx-then-success · 2 rules");
    assertThat(theOne(offered, "refund-full-success").whyThisProviderCannotExpressIt()).isNotNull();
  }

  private static APresetInWords theOne(List<APresetInWords> offered, String preset) {
    for (APresetInWords read : offered) {
      if (read.preset().equals(preset)) {
        return read;
      }
    }
    throw new AssertionError(preset + " is not offered at all");
  }

  private static NewScenarioRule aDeliveryNarrowedOnAnAmount() {
    return new NewScenarioRule(
        GROW,
        RUN,
        0,
        new ScenarioMatch(
            null, null, null, null, null, Amount.of("1000"), null, null, "completed", Map.of()),
        new ScenarioAction.DeliveryDuplicated(),
        null);
  }

  private static NewScenarioRule narrowedOnAnAmount() {
    return new NewScenarioRule(
        GROW,
        RUN,
        0,
        new ScenarioMatch(
            null, null, null, null, null, Amount.of("1000"), null, SETTLE, null, Map.of()),
        new ScenarioAction.FixedSuccess(),
        null);
  }

  private static NewScenarioRule narrowedOnAHeader() {
    return new NewScenarioRule(
        GROW,
        RUN,
        0,
        new ScenarioMatch(
            null, null, null, null, null, null, null, SETTLE, null, Map.of("X-Trace", "abc")),
        new ScenarioAction.FixedSuccess(),
        null);
  }

  private static String theRule(
      @Nullable String operation, @Nullable String eventType, ScenarioAction action) {
    return APresetInWords.theRuleOf(rule(operation, eventType, action));
  }

  private static String theStep(
      String operation,
      ScenarioAction action,
      StoryKeying keying,
      @Nullable String whenState,
      @Nullable String thenState) {
    return APresetInWords.theRuleOf(step(operation, action, keying, whenState, thenState));
  }

  private static NewScenarioRule rule(
      @Nullable String operation, @Nullable String eventType, ScenarioAction action) {
    ScenarioMatch match = ScenarioMatch.anyRequest();
    if (operation != null) {
      match = match.naming(operation);
    }
    if (eventType != null) {
      match = match.reporting(eventType);
    }
    return new NewScenarioRule(GROW, RUN, 0, match, action, null);
  }

  private static NewScenarioRule step(
      String operation,
      ScenarioAction action,
      StoryKeying keying,
      @Nullable String whenState,
      @Nullable String thenState) {
    return new NewScenarioRule(
        GROW,
        RUN,
        0,
        ScenarioMatch.anyRequest().naming(operation),
        action,
        new ScenarioStory("psp-5xx-then-success", keying, whenState, thenState));
  }
}
