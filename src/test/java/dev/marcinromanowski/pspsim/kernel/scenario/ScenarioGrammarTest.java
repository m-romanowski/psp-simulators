package dev.marcinromanowski.pspsim.kernel.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ScenarioGrammarTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final FaultDelayCeiling THIRTY_SECONDS =
      new FaultDelayCeiling(Duration.ofSeconds(30));

  private final ScenarioGrammar grammar = new ScenarioGrammar(THIRTY_SECONDS);

  @Test
  void a_rule_is_written_against_the_provider_and_test_run_it_was_declared_for() {
    NewScenarioRule rule = grammar.ruleOf(GROW, RUN, declining(100));

    assertThat(rule.provider()).isEqualTo(GROW);
    assertThat(rule.testRunId()).isEqualTo(RUN);
    assertThat(rule.priority()).isEqualTo(100);
    assertThat(rule.action()).isEqualTo(new ScenarioAction.TerminalFailure("813"));
    assertThat(rule.story()).isNull();
  }

  @Test
  void a_rule_that_declares_no_priority_is_written_at_the_lowest_one_and_not_refused() {
    assertThat(grammar.ruleOf(GROW, RUN, declining(null)).priority()).isZero();
  }

  @Test
  void a_decline_answers_with_the_code_the_provider_publishes() {
    assertThat(
            grammar.actionOf(
                new DeclaredAction("TERMINAL_FAILURE", null, "813", null, null, null, null, null)))
        .isEqualTo(new ScenarioAction.TerminalFailure("813"));
  }

  @ParameterizedTest(name = "a decline with the provider code {0} cannot be read")
  @ValueSource(strings = {"", " "})
  void a_decline_that_names_no_code_is_rejected_by_the_property_that_broke_it(String code) {
    assertThatThrownBy(
            () ->
                grammar.actionOf(
                    new DeclaredAction(
                        "TERMINAL_FAILURE", null, code, null, null, null, null, null)))
        .hasMessage("action.providerCode is required");
  }

  @Test
  void a_decline_with_no_code_at_all_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(
            () ->
                grammar.actionOf(
                    new DeclaredAction(
                        "TERMINAL_FAILURE", null, null, null, null, null, null, null)))
        .hasMessage("action.providerCode is required");
  }

  @Test
  void a_rule_that_declares_no_action_says_nothing_about_what_it_would_do() {
    assertThatThrownBy(() -> grammar.actionOf(null)).hasMessage("action is required");
  }

  @Test
  void an_action_that_stands_aside_declines_nothing_and_needs_nothing() {
    assertThat(grammar.actionOf(faulting("FIXED_SUCCESS", null, null, null)))
        .isEqualTo(new ScenarioAction.FixedSuccess());
  }

  @Test
  void a_fault_after_the_operation_carries_the_status_the_transport_puts_in_place() {
    assertThat(grammar.actionOf(faulting("TRANSPORT_FAULT", "SERVER_ERROR", 502, null)))
        .isEqualTo(new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(502)));
  }

  @Test
  void a_fault_answered_instead_of_the_operation_carries_the_same_status() {
    assertThat(
            grammar.actionOf(
                faulting("TRANSPORT_FAULT_INSTEAD_OF_OPERATION", "SERVER_ERROR", 502, null)))
        .isEqualTo(
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)));
  }

  @ParameterizedTest(name = "{0} answers no provider code of its own")
  @ValueSource(
      strings = {"FIXED_SUCCESS", "TRANSPORT_FAULT", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION"})
  void only_the_action_that_declines_may_name_a_provider_code(String type) {
    assertThatThrownBy(
            () ->
                grammar.actionOf(
                    new DeclaredAction(type, null, "813", "SERVER_ERROR", 502, null, null, null)))
        .hasMessageStartingWith("action.providerCode applies only to TERMINAL_FAILURE, because ");
  }

  @ParameterizedTest(name = "{0} names the type the caller wrote when no fault is declared")
  @ValueSource(strings = {"TRANSPORT_FAULT", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION"})
  void a_fault_action_that_names_no_fault_is_told_which_type_needs_one(String type) {
    assertThatThrownBy(() -> grammar.actionOf(faulting(type, null, null, null)))
        .hasMessage(
            "action.fault is required by "
                + type
                + ", and must be one of SERVER_ERROR, TRUNCATED_BODY, CORRUPTED_BODY,"
                + " DROPPED_CONNECTION, DELAYED_RESPONSE");
  }

  @Test
  void a_fault_this_simulator_cannot_perform_is_answered_with_the_ones_it_can() {
    assertThatThrownBy(
            () -> grammar.actionOf(faulting("TRANSPORT_FAULT", "CONNECTION_RESET", null, null)))
        .hasMessage(
            "action.fault must be one of SERVER_ERROR, TRUNCATED_BODY, CORRUPTED_BODY,"
                + " DROPPED_CONNECTION, DELAYED_RESPONSE");
  }

  @ParameterizedTest(name = "the fault {0} is read into the damage it names")
  @ValueSource(strings = {"TRUNCATED_BODY", "CORRUPTED_BODY", "DROPPED_CONNECTION"})
  void every_fault_that_damages_a_body_is_read_into_the_fault_it_names(String fault) {
    ScenarioAction action = grammar.actionOf(faulting("TRANSPORT_FAULT", fault, null, null));

    assertThat(action)
        .isInstanceOfSatisfying(
            ScenarioAction.TransportFaultAlways.class,
            always ->
                assertThat(always.fault().kind()).isEqualTo(TransportFaultKind.valueOf(fault)));
  }

  @Test
  void a_delay_is_read_into_the_hold_the_operator_still_allows() {
    ScenarioAction action =
        grammar.actionOf(faulting("TRANSPORT_FAULT", "DELAYED_RESPONSE", null, 250L));

    assertThat(action)
        .isEqualTo(
            new ScenarioAction.TransportFaultAlways(
                new TransportFault.DelayedResponse(Duration.ofMillis(250))));
  }

  @Test
  void a_status_is_required_by_the_one_fault_that_answers_one() {
    assertThatThrownBy(
            () -> grammar.actionOf(faulting("TRANSPORT_FAULT", "SERVER_ERROR", null, null)))
        .hasMessage("action.httpStatus is required by action.fault SERVER_ERROR");
  }

  @Test
  void a_length_is_required_by_the_one_fault_that_holds_a_connection_open() {
    assertThatThrownBy(
            () -> grammar.actionOf(faulting("TRANSPORT_FAULT", "DELAYED_RESPONSE", null, null)))
        .hasMessage("action.delayMs is required by action.fault DELAYED_RESPONSE");
  }

  @Test
  void a_delay_longer_than_the_operator_allows_is_refused_where_the_rule_is_written() {
    assertThatThrownBy(
            () -> grammar.actionOf(faulting("TRANSPORT_FAULT", "DELAYED_RESPONSE", null, 60_000L)))
        .hasMessageContaining("simulator.fault.longest-delay");
  }

  @Test
  void
      a_hand_built_action_holding_longer_than_the_ceiling_is_refused_by_the_property_although_no_preset_grow_publishes_can_build_one() {
    ScenarioAction held =
        new ScenarioAction.TransportFaultAlways(
            new TransportFault.DelayedResponse(Duration.ofSeconds(60)));

    assertThat(grammar.theDelaysThisSimulatorWillNotHold(held))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("action.delayMs");
              assertThat(refusal.reason()).contains("simulator.fault.longest-delay", "PT30S");
            });
  }

  @Test
  void a_hand_built_action_whose_delay_the_ceiling_admits_is_held_rather_than_refused() {
    ScenarioAction held =
        new ScenarioAction.TransportFaultAlways(
            new TransportFault.DelayedResponse(Duration.ofSeconds(30)));

    assertThat(grammar.theDelaysThisSimulatorWillNotHold(held)).isEmpty();
  }

  @Test
  void an_action_that_holds_no_connection_open_is_never_refused_for_the_length_of_a_delay() {
    assertThat(grammar.theDelaysThisSimulatorWillNotHold(new ScenarioAction.FixedSuccess()))
        .isEmpty();
    assertThat(grammar.theDelaysThisSimulatorWillNotHold(new ScenarioAction.TerminalFailure("813")))
        .isEmpty();
    assertThat(
            grammar.theDelaysThisSimulatorWillNotHold(
                new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(502))))
        .isEmpty();
    assertThat(
            grammar.theDelaysThisSimulatorWillNotHold(
                new ScenarioAction.TransportFaultInsteadOfOperation(
                    new TransportFault.ServerError(502))))
        .isEmpty();
  }

  @Test
  void a_rule_declaring_no_action_at_all_is_refused_by_no_field_of_one() {
    assertThat(ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(null)).isEmpty();
    assertThat(ScenarioGrammar.theActionTypeThisSimulatorCannotRead(null)).isEmpty();
  }

  @Test
  void every_field_a_decline_cannot_perform_is_named_in_one_answer_and_in_a_fixed_order() {
    List<ScenarioRefusal> refused =
        ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(
            new DeclaredAction(
                "TERMINAL_FAILURE", 1, "813", "SERVER_ERROR", 504, "SUCCEEDED", 250L, null));

    assertThat(refused)
        .extracting(ScenarioRefusal::property)
        .containsExactly(
            "action.failuresBeforeSuccess",
            "action.httpStatus",
            "action.fault",
            "action.terminalStatus",
            "action.delayMs");
  }

  @Test
  void a_count_of_failures_before_success_is_answered_with_the_story_that_replaced_it() {
    assertThat(
            ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(
                new DeclaredAction("TERMINAL_FAILURE", 1, "813", null, null, null, null, null)))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("action.failuresBeforeSuccess");
              assertThat(refusal.reason()).contains("story", "whenState", "thenState");
            });
  }

  @Test
  void a_status_and_a_delay_are_not_refused_on_the_fault_that_performs_each_of_them() {
    assertThat(
            ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(
                faulting("TRANSPORT_FAULT", "SERVER_ERROR", 502, null)))
        .isEmpty();
    assertThat(
            ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(
                faulting("TRANSPORT_FAULT", "DELAYED_RESPONSE", null, 250L)))
        .isEmpty();
    assertThat(
            ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(
                faulting("TRANSPORT_FAULT_INSTEAD_OF_OPERATION", "SERVER_ERROR", 502, null)))
        .isEmpty();
  }

  @ParameterizedTest(name = "action.{0} is refused on a fault that performs neither")
  @CsvSource({"httpStatus", "delayMs"})
  void a_field_the_named_fault_cannot_perform_is_refused_although_the_type_could(String property) {
    DeclaredAction action =
        "httpStatus".equals(property)
            ? faulting("TRANSPORT_FAULT", "CORRUPTED_BODY", 502, null)
            : faulting("TRANSPORT_FAULT", "CORRUPTED_BODY", null, 250L);

    assertThat(ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(action))
        .extracting(ScenarioRefusal::property)
        .containsExactly("action." + property);
  }

  @Test
  void a_fault_named_on_an_action_that_performs_none_is_refused_rather_than_dropped() {
    assertThat(
            ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(
                new DeclaredAction(
                    "TERMINAL_FAILURE", null, "813", "DROPPED_CONNECTION", null, null, null, null)))
        .extracting(ScenarioRefusal::property)
        .containsExactly("action.fault");
  }

  @Test
  void a_normalized_status_is_derived_and_never_written_by_a_rule() {
    assertThat(
            ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(
                new DeclaredAction(
                    "FIXED_SUCCESS", null, null, null, null, "SUCCEEDED", null, null)))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("action.terminalStatus");
              assertThat(refusal.reason()).contains("derived");
            });
  }

  @ParameterizedTest(name = "the action type {0} is read rather than refused")
  @ValueSource(
      strings = {
        "FIXED_SUCCESS",
        "TERMINAL_FAILURE",
        "TRANSPORT_FAULT",
        "TRANSPORT_FAULT_INSTEAD_OF_OPERATION",
        "DELIVERY_DELAYED",
        "DELIVERY_DUPLICATED",
        "DELIVERY_DROPPED"
      })
  void every_action_type_this_simulator_publishes_is_readable(String type) {
    assertThat(ScenarioGrammar.theActionTypeThisSimulatorCannotRead(named(type))).isEmpty();
  }

  @ParameterizedTest(
      name = "the removed action type {0} is answered with the story that replaced it")
  @ValueSource(strings = {"TRANSIENT_THEN_SUCCESS", "TRANSPORT_FAULT_THEN_SUCCESS"})
  void an_action_type_the_story_grammar_removed_names_what_replaced_it(String type) {
    assertThat(ScenarioGrammar.theActionTypeThisSimulatorCannotRead(named(type)))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("action.type");
              assertThat(refusal.reason())
                  .contains("no longer exists", "story name", "whenState", "thenState");
            });
  }

  @Test
  void every_action_answers_for_itself_which_provider_code_it_comes_to_rest_with() {
    assertThat(new ScenarioAction.TerminalFailure("813").theProviderCodeItComesToRestWith())
        .isEqualTo("813");
    assertThat(
            new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), "813")
                .theProviderCodeItComesToRestWith())
        .isEqualTo("813");
    assertThat(
            new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null)
                .theProviderCodeItComesToRestWith())
        .isNull();
    assertThat(theActionsThatComeToRestWithNoCode())
        .hasSize(6)
        .allSatisfy(action -> assertThat(action.theProviderCodeItComesToRestWith()).isNull());
  }

  private static List<ScenarioAction> theActionsThatComeToRestWithNoCode() {
    return List.of(
        new ScenarioAction.FixedSuccess(),
        new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()),
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(502)),
        new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5)),
        new ScenarioAction.DeliveryDuplicated(),
        new ScenarioAction.DeliveryDropped());
  }

  @Test
  void a_pending_payment_is_read_as_a_processing_state_with_the_success_it_is_owed() {
    assertThat(grammar.actionOf(settling(5000L, null)))
        .isEqualTo(new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null));
  }

  @Test
  void a_pending_payment_that_names_a_code_is_owed_the_decline_that_code_spells() {
    assertThat(grammar.actionOf(settling(5000L, "813")))
        .isEqualTo(new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), "813"));
  }

  @Test
  void a_processing_state_with_no_moment_to_settle_in_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(() -> grammar.actionOf(settling(null, null)))
        .hasMessageContaining("action.settlesAfterMs is required by")
        .hasMessageContaining(ScenarioGrammar.PROCESSING_THEN_TERMINAL);
  }

  @ParameterizedTest(name = "an outcome scheduled {0} ms away is refused")
  @ValueSource(longs = {0L, -1L, 3_600_001L})
  void an_outcome_no_test_run_could_wait_for_is_rejected_by_the_property_that_broke_it(
      long millis) {
    assertThatThrownBy(() -> grammar.actionOf(settling(millis, null)))
        .hasMessageContaining("action.settlesAfterMs");
  }

  @ParameterizedTest(name = "an outcome scheduled {0} ms away is admitted")
  @ValueSource(longs = {1L, 3_600_000L})
  void the_shortest_and_the_longest_wait_a_rule_may_ask_for_are_both_admitted(long millis) {
    assertThat(grammar.actionOf(settling(millis, null)))
        .isEqualTo(new ScenarioAction.ProcessingThenTerminal(Duration.ofMillis(millis), null));
  }

  @Test
  void the_longest_wait_is_the_one_the_ceiling_names_and_the_millisecond_past_it_is_refused() {
    long ceiling = ScenarioAction.ProcessingThenTerminal.LONGEST_WAIT.toMillis();

    assertThat(ceiling).isEqualTo(3_600_000L);
    assertThat(grammar.actionOf(settling(ceiling, null)))
        .isEqualTo(
            new ScenarioAction.ProcessingThenTerminal(
                ScenarioAction.ProcessingThenTerminal.LONGEST_WAIT, null));
    assertThatThrownBy(() -> grammar.actionOf(settling(ceiling + 1, null)))
        .hasMessageContaining("action.settlesAfterMs");
  }

  @Test
  void a_pending_payment_whose_decline_names_nothing_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(() -> grammar.actionOf(settling(5000L, " ")))
        .hasMessageContaining("action.providerCode");
  }

  @ParameterizedTest(name = "{0} cannot schedule an outcome")
  @ValueSource(
      strings = {
        "FIXED_SUCCESS",
        "TERMINAL_FAILURE",
        "TRANSPORT_FAULT",
        "TRANSPORT_FAULT_INSTEAD_OF_OPERATION",
        "DELIVERY_DELAYED",
        "DELIVERY_DUPLICATED",
        "DELIVERY_DROPPED"
      })
  void an_action_that_answers_now_and_schedules_nothing_refuses_a_moment_to_settle_in(String type) {
    DeclaredAction asking = new DeclaredAction(type, null, null, null, null, null, null, 5000L);

    assertThat(ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(asking))
        .extracting(ScenarioRefusal::property)
        .contains("action.settlesAfterMs");
  }

  @Test
  void a_processing_state_may_carry_the_moment_it_settles_in_without_being_refused_for_it() {
    assertThat(ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(settling(5000L, null)))
        .isEmpty();
  }

  @Test
  void a_processing_state_that_narrows_on_no_operation_could_never_be_performed() {
    NewScenarioRule everyCall =
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null),
            null);

    assertThat(ScenarioGrammar.theSubjectThisRuleCannotNarrowOn(everyCall))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("match.operation");
              assertThat(refusal.reason()).contains(ScenarioGrammar.PROCESSING_THEN_TERMINAL);
            });
  }

  @Test
  void a_processing_state_that_narrows_on_one_operation_is_the_subject_a_rule_may_have() {
    NewScenarioRule oneCall =
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming("settleSuspendedTransaction"),
            new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null),
            null);

    assertThat(ScenarioGrammar.theSubjectThisRuleCannotNarrowOn(oneCall)).isEmpty();
  }

  @Test
  void a_processing_state_is_named_by_the_type_it_is_stored_and_read_back_as() {
    assertThat(
            ScenarioGrammar.typeOf(
                new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null)))
        .isEqualTo(ScenarioGrammar.PROCESSING_THEN_TERMINAL);
    assertThat(
            ScenarioGrammar.faultOf(
                (ScenarioAction)
                    new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null)))
        .isNull();
  }

  @Test
  void an_action_type_this_simulator_never_published_is_answered_with_the_whole_vocabulary() {
    for (DeclaredAction unknown : List.of(named("SOMETHING_ELSE"), named(null))) {
      assertThat(ScenarioGrammar.theActionTypeThisSimulatorCannotRead(unknown))
          .singleElement()
          .satisfies(
              refusal -> {
                assertThat(refusal.property()).isEqualTo("action.type");
                assertThat(refusal.reason())
                    .isEqualTo(
                        "action.type must be one of FIXED_SUCCESS, TERMINAL_FAILURE,"
                            + " TRANSPORT_FAULT, TRANSPORT_FAULT_INSTEAD_OF_OPERATION,"
                            + " PROCESSING_THEN_TERMINAL, DELIVERY_DELAYED, DELIVERY_DUPLICATED,"
                            + " DELIVERY_DROPPED");
              });
    }
  }

  @ParameterizedTest(name = "{0} is read into the shaping it names")
  @CsvSource({
    "DELIVERY_DUPLICATED",
    "DELIVERY_DROPPED",
  })
  void a_delivery_action_that_needs_no_length_is_read_without_one(String type) {
    ScenarioAction action =
        grammar.actionOf(new DeclaredAction(type, null, null, null, null, null, null, null));

    assertThat(action).isInstanceOf(ScenarioAction.OnTheDelivery.class);
    assertThat(ScenarioGrammar.typeOf(action)).isEqualTo(type);
  }

  @Test
  void a_held_delivery_is_read_into_the_length_of_the_hold_the_rule_names() {
    ScenarioAction action =
        grammar.actionOf(
            new DeclaredAction("DELIVERY_DELAYED", null, null, null, null, null, 5000L, null));

    assertThat(action).isEqualTo(new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5)));
  }

  @ParameterizedTest(name = "a hold of {0} ms leaves the delivery where it was and is rejected")
  @ValueSource(longs = {0L, -1L})
  void a_hold_that_moves_no_delivery_is_rejected_rather_than_written(long delayMs) {
    assertThatThrownBy(
            () ->
                grammar.actionOf(
                    new DeclaredAction(
                        "DELIVERY_DELAYED", null, null, null, null, null, delayMs, null)))
        .hasMessageContaining("must hold a delivery back by a positive number of milliseconds");
  }

  @Test
  void a_held_delivery_that_names_no_length_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(
            () ->
                grammar.actionOf(
                    new DeclaredAction(
                        "DELIVERY_DELAYED", null, null, null, null, null, null, null)))
        .hasMessage("action.delayMs is required by DELIVERY_DELAYED");
  }

  @Test
  void a_hold_longer_than_a_test_run_would_wait_for_is_rejected_rather_than_written() {
    assertThatThrownBy(
            () ->
                grammar.actionOf(
                    new DeclaredAction(
                        "DELIVERY_DELAYED", null, null, null, null, null, 3_600_001L, null)))
        .hasMessageContaining("must not hold a delivery back longer than");
  }

  @Test
  void a_length_named_on_a_held_delivery_is_never_refused_as_a_field_it_cannot_perform() {
    assertThat(
            ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(
                new DeclaredAction("DELIVERY_DELAYED", null, null, null, null, null, 5000L, null)))
        .isEmpty();
  }

  @Test
  void a_rule_that_answers_a_request_may_not_narrow_on_the_event_type_only_a_delivery_carries() {
    NewScenarioRule answering =
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().reporting("payment_process.completed"),
            new ScenarioAction.FixedSuccess(),
            null);

    assertThat(ScenarioGrammar.theSubjectThisRuleCannotNarrowOn(answering))
        .extracting(ScenarioRefusal::property)
        .containsExactly("match.eventType");
  }

  @Test
  void a_rule_that_shapes_a_delivery_is_refused_every_request_fact_it_narrows_on() {
    NewScenarioRule shaping =
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            new ScenarioMatch(
                "merchant-1",
                "order-1",
                "9000001",
                "cus_1",
                "pm_1",
                Amount.of("10.00"),
                "PLN",
                "refundTransaction",
                "payment_process.completed",
                Map.of("X-Simulator-Scenario", "held")),
            new ScenarioAction.DeliveryDropped(),
            null);

    assertThat(ScenarioGrammar.theSubjectThisRuleCannotNarrowOn(shaping))
        .extracting(ScenarioRefusal::property)
        .containsExactly(
            "match.orderId",
            "match.customerId",
            "match.paymentMethodId",
            "match.amount",
            "match.currency",
            "match.operation",
            "match.header");
  }

  @Test
  void a_rule_that_shapes_a_delivery_keeps_the_two_facts_a_delivery_carries() {
    NewScenarioRule shaping =
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            new ScenarioMatch(
                "merchant-1",
                null,
                "9000001",
                null,
                null,
                null,
                null,
                null,
                "payment_process.completed",
                Map.of()),
            new ScenarioAction.DeliveryDropped(),
            null);

    assertThat(ScenarioGrammar.theSubjectThisRuleCannotNarrowOn(shaping)).isEmpty();
  }

  @Test
  void a_rule_that_shapes_a_delivery_is_refused_the_story_a_delivery_leaves_no_step_of() {
    NewScenarioRule shaping =
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.DeliveryDuplicated(),
            new ScenarioStory("late", StoryKeying.FLOW, null, "held"));

    assertThat(ScenarioGrammar.theSubjectThisRuleCannotNarrowOn(shaping))
        .extracting(ScenarioRefusal::property)
        .containsExactly("story");
  }

  @Test
  void a_rule_that_names_no_predicate_matches_any_request() {
    assertThat(ScenarioGrammar.matchOf(null)).isEqualTo(ScenarioMatch.anyRequest());
  }

  @Test
  void a_rule_carries_every_predicate_it_names() {
    ScenarioMatch match =
        ScenarioGrammar.matchOf(
            new DeclaredMatch(
                "merchant-1",
                "order-1",
                "9000001",
                "cus_1",
                "pm_1",
                "10.00",
                "PLN",
                "refundTransaction",
                null,
                Map.of("X-Simulator-Scenario", "capture-timeout")));

    assertThat(match.merchantId()).isEqualTo("merchant-1");
    assertThat(match.orderId()).isEqualTo("order-1");
    assertThat(match.providerObjectId()).isEqualTo("9000001");
    assertThat(match.customerId()).isEqualTo("cus_1");
    assertThat(match.paymentMethodId()).isEqualTo("pm_1");
    assertThat(match.amount()).isEqualTo(Amount.of("10.00"));
    assertThat(match.currency()).isEqualTo("PLN");
    assertThat(match.operation()).isEqualTo("refundTransaction");
    assertThat(match.header())
        .containsExactly(Map.entry("X-Simulator-Scenario", "capture-timeout"));
  }

  @Test
  void a_rule_that_shapes_a_delivery_carries_the_event_type_it_narrows_on() {
    assertThat(
            ScenarioGrammar.matchOf(
                    new DeclaredMatch(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "payment_process.completed",
                        null))
                .eventType())
        .isEqualTo("payment_process.completed");
  }

  @Test
  void a_rule_that_names_no_event_type_narrows_on_no_event_rather_than_on_an_empty_one() {
    assertThat(ScenarioGrammar.matchOf(matching(null, null, null)).eventType()).isNull();
  }

  @Test
  void an_event_type_predicate_that_names_nothing_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(
            () ->
                ScenarioGrammar.matchOf(
                    new DeclaredMatch(null, null, null, null, null, null, null, null, " ", null)))
        .hasMessage("match.eventType must name the event the delivery reports, or be absent");
  }

  @Test
  void a_hold_of_exactly_the_longest_this_simulator_admits_is_written_rather_than_refused() {
    assertThat(
            grammar.actionOf(
                new DeclaredAction(
                    "DELIVERY_DELAYED", null, null, null, null, null, 3_600_000L, null)))
        .isEqualTo(new ScenarioAction.DeliveryDelayed(Duration.ofHours(1)));
  }

  @Test
  void an_action_that_shapes_a_delivery_may_not_name_the_code_a_provider_would_decline_with() {
    assertThatThrownBy(
            () ->
                grammar.actionOf(
                    new DeclaredAction(
                        "DELIVERY_DROPPED", null, "813", null, null, null, null, null)))
        .hasMessageContaining(
            "action.providerCode applies only to TERMINAL_FAILURE, because an action that shapes"
                + " a delivery answers no provider envelope at all");
  }

  @Test
  void two_rules_of_one_expansion_narrowing_on_the_same_absent_fact_are_refused_once() {
    NewScenarioRule narrowed =
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            new ScenarioMatch(null, "order-1", null, null, null, null, null, null, null, Map.of()),
            new ScenarioAction.DeliveryDropped(),
            null);

    assertThat(ScenarioGrammar.theSubjectTheseRulesCannotNarrowOn(List.of(narrowed, narrowed)))
        .extracting(ScenarioRefusal::property)
        .containsExactly("match.orderId");
  }

  @Test
  void an_expansion_whose_rules_all_narrow_on_what_their_subject_carries_is_refused_nothing() {
    NewScenarioRule shaping =
        new NewScenarioRule(
            GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().reporting("payment_process.completed"),
            new ScenarioAction.DeliveryDropped(),
            null);

    assertThat(ScenarioGrammar.theSubjectTheseRulesCannotNarrowOn(List.of(shaping))).isEmpty();
  }

  @Test
  void a_rule_that_names_no_amount_carries_none_rather_than_one_that_matches_nothing() {
    assertThat(ScenarioGrammar.matchOf(matching(null, null, null)).amount()).isNull();
  }

  @Test
  void a_rule_that_names_no_operation_is_about_every_operation_the_provider_exposes() {
    assertThat(ScenarioGrammar.matchOf(matching(null, null, null)).operation()).isNull();
  }

  @Test
  void an_operation_predicate_that_names_nothing_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(() -> ScenarioGrammar.matchOf(matching(null, " ", null)))
        .hasMessage("match.operation must name the operation the rule is about, or be absent");
  }

  @Test
  void a_rule_that_names_no_header_carries_no_header_predicate() {
    assertThat(ScenarioGrammar.matchOf(matching(null, null, null)).header()).isEmpty();
  }

  @Test
  void
      a_rule_naming_many_headers_keeps_them_in_the_order_it_named_and_twelve_cannot_fall_that_way_by_luck() {
    Map<String, String> declared = new LinkedHashMap<>();
    for (int at = 1; at <= 12; at++) {
      declared.put("X-Header-" + at, "value-" + at);
    }

    Map<String, String> read = ScenarioGrammar.matchOf(matching(null, null, declared)).header();

    assertThat(read)
        .as(
            "the header predicates of a rule are read back in the order a tester named them, by"
                + " the document a run is read back as, by the words a preset is printed in and by"
                + " the form a screen draws, and twelve names fall into one declared order by"
                + " chance once in 479001600, so this sequence is the order and not a draw")
        .containsExactlyEntriesOf(declared);
    assertThat(read)
        .as(
            "a map with no iteration order of its own would answer the sequence above from the"
                + " keys' hashes and a per-JVM salt, so the type is asserted as well and fails in"
                + " every run rather than in most of them")
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void a_header_predicate_sent_with_no_value_is_rejected_by_the_header_that_broke_it() {
    Map<String, String> header = new HashMap<>();
    header.put("X-Simulator-Scenario", null);

    assertThatThrownBy(() -> ScenarioGrammar.matchOf(matching(null, null, header)))
        .hasMessage("match.header.X-Simulator-Scenario must name the value the request carries");
  }

  @Test
  void a_preset_is_read_by_the_name_it_is_published_under() {
    assertThat(ScenarioGrammar.presetOf("cancel-hold-success"))
        .isEqualTo(ScenarioPreset.CANCEL_HOLD_SUCCESS);
  }

  @ParameterizedTest(name = "a preset selection naming {0} says which names exist")
  @ValueSource(strings = {"", " "})
  void a_preset_selection_that_names_nothing_is_answered_with_the_published_names(String named) {
    assertThatThrownBy(() -> ScenarioGrammar.presetOf(named))
        .hasMessage("preset is required, and must be one of " + ScenarioPreset.thePublishedNames());
  }

  @Test
  void a_preset_selection_naming_no_preset_at_all_is_answered_with_the_published_names() {
    assertThatThrownBy(() -> ScenarioGrammar.presetOf(null))
        .hasMessage("preset is required, and must be one of " + ScenarioPreset.thePublishedNames());
  }

  @Test
  void a_preset_this_simulator_does_not_publish_is_answered_with_the_ones_it_does() {
    assertThatThrownBy(() -> ScenarioGrammar.presetOf("cancel-hold-succes"))
        .hasMessage("preset must be one of " + ScenarioPreset.thePublishedNames());
  }

  @Test
  void a_rule_that_is_no_step_of_a_story_stands_on_no_story_at_all() {
    assertThat(ScenarioGrammar.storyOf(declining(0))).isNull();
  }

  @ParameterizedTest(name = "{0} named without the story it belongs to is refused")
  @ValueSource(strings = {"whenState", "thenState", "keyedOn"})
  void a_state_or_a_key_named_without_the_story_it_belongs_to_is_refused(String property) {
    DeclaredScenario declared =
        new DeclaredScenario(
            null,
            null,
            null,
            "keyedOn".equals(property) ? "TEST_RUN" : null,
            "whenState".equals(property) ? "asked" : null,
            "thenState".equals(property) ? "asked" : null,
            null,
            null);

    assertThatThrownBy(() -> ScenarioGrammar.storyOf(declared))
        .hasMessageStartingWith("story is required by keyedOn, whenState and thenState");
  }

  @Test
  void a_step_that_declares_no_key_stands_on_the_flow_the_provider_resolves() {
    ScenarioStory story = ScenarioGrammar.storyOf(stepping("capture-retries", null, "asked"));

    assertThat(story).isNotNull();
    assertThat(story.name()).isEqualTo("capture-retries");
    assertThat(story.keyedOn()).isEqualTo(StoryKeying.FLOW);
    assertThat(story.thenState()).isEqualTo("asked");
  }

  @Test
  void a_step_that_declares_the_test_run_stands_on_the_whole_run() {
    ScenarioStory story = ScenarioGrammar.storyOf(stepping("hold-retries", "TEST_RUN", "asked"));

    assertThat(story).isNotNull();
    assertThat(story.keyedOn()).isEqualTo(StoryKeying.TEST_RUN);
  }

  @ParameterizedTest(name = "the key {0} names nothing a story can stand on")
  @ValueSource(strings = {"flow", "PROCESS", "", "OBJECT"})
  void a_key_no_story_can_stand_on_is_answered_with_the_keys_that_exist(String named) {
    assertThatThrownBy(() -> ScenarioGrammar.storyOf(stepping("capture-retries", named, "asked")))
        .hasMessage(
            "keyedOn must name one of FLOW, TEST_RUN, because a story is keyed either on the flow"
                + " the provider resolves for the request or on the whole test run");
  }

  private static DeclaredScenario declining(@Nullable Integer priority) {
    return new DeclaredScenario(
        null,
        priority,
        null,
        null,
        null,
        null,
        null,
        new DeclaredAction("TERMINAL_FAILURE", null, "813", null, null, null, null, null));
  }

  private static DeclaredScenario stepping(
      String story, @Nullable String keyedOn, String thenState) {
    return new DeclaredScenario(null, null, story, keyedOn, null, thenState, null, null);
  }

  private static DeclaredAction settling(
      @Nullable Long settlesAfterMs, @Nullable String providerCode) {
    return new DeclaredAction(
        ScenarioGrammar.PROCESSING_THEN_TERMINAL,
        null,
        providerCode,
        null,
        null,
        null,
        null,
        settlesAfterMs);
  }

  private static DeclaredAction named(@Nullable String type) {
    return new DeclaredAction(type, null, null, null, null, null, null, null);
  }

  private static DeclaredAction faulting(
      String type, @Nullable String fault, @Nullable Integer httpStatus, @Nullable Long delayMs) {
    return new DeclaredAction(type, null, null, fault, httpStatus, null, delayMs, null);
  }

  private static DeclaredMatch matching(
      @Nullable String amount, @Nullable String operation, @Nullable Map<String, String> header) {
    return new DeclaredMatch(
        "merchant-1", null, null, null, null, amount, null, operation, null, header);
  }
}
