package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScenarioFormTest {

  private static final String RUN = "ui-scenarios";
  private static final String TYPED_BY_MISTAKE = "as soon as possible";

  @Test
  void a_field_a_tester_left_empty_narrows_nothing_rather_than_narrowing_on_an_empty_value() {
    Written form =
        aForm()
            .withMerchantId("  ")
            .withOrderId("")
            .withOperation("settleSuspendedTransaction")
            .withActionType("FIXED_SUCCESS");

    DeclaredScenario declared = declaredBy(form);

    assertThat(declared.match().merchantId()).isNull();
    assertThat(declared.match().orderId()).isNull();
    assertThat(declared.match().operation()).isEqualTo("settleSuspendedTransaction");
  }

  @Test
  void an_event_type_a_tester_typed_reaches_the_rule_that_shapes_the_delivery_reporting_it() {
    Written form =
        aForm()
            .withEventType("  payment_process.completed  ")
            .withActionType("DELIVERY_DELAYED")
            .withDelayMs("5000");

    DeclaredScenario declared = declaredBy(form);

    assertThat(declared.match().eventType()).isEqualTo("payment_process.completed");
    assertThat(declared.action().type()).isEqualTo("DELIVERY_DELAYED");
    assertThat(declared.action().delayMs()).isEqualTo(5000L);
  }

  @Test
  void an_event_type_a_tester_left_empty_narrows_on_no_event_rather_than_on_an_empty_one() {
    assertThat(declaredBy(aForm().withEventType("   ")).match().eventType()).isNull();
  }

  @Test
  void the_form_a_tester_has_just_opened_narrows_on_nothing_and_names_no_action() {
    ScenarioForm.Read read = ScenarioForm.empty().read(RUN);

    assertThat(read).isInstanceOf(ScenarioForm.Read.Declared.class);
    DeclaredScenario declared = ((ScenarioForm.Read.Declared) read).scenario();
    assertThat(declared.match().merchantId()).isNull();
    assertThat(declared.match().operation()).isNull();
    assertThat(declared.match().header()).isEmpty();
    assertThat(declared.priority()).isNull();
    assertThat(declared.story()).isNull();
    assertThat(declared.action().type()).isNull();
  }

  @Test
  void a_value_a_tester_padded_with_spaces_is_read_as_the_value_they_meant() {
    DeclaredScenario declared = declaredBy(aForm().withMerchantId("  merchant-1  "));

    assertThat(declared.match().merchantId()).isEqualTo("merchant-1");
  }

  @Test
  void a_priority_that_is_not_a_whole_number_is_refused_by_name_and_never_repeated_back() {
    List<ScenarioRefusal> refusals = refusalsOf(aForm().withPriority(TYPED_BY_MISTAKE));

    assertThat(refusals).hasSize(1);
    assertThat(refusals.get(0).property()).isEqualTo("priority");
    assertThat(refusals.get(0).reason()).doesNotContain(TYPED_BY_MISTAKE);
  }

  @Test
  void every_number_the_form_cannot_read_is_reported_at_once_rather_than_one_per_submission() {
    List<ScenarioRefusal> refusals =
        refusalsOf(
            aForm()
                .withPriority("high")
                .withHttpStatus("five hundred")
                .withDelayMs("soon")
                .withSettlesAfterMs("later"));

    assertThat(refusals)
        .extracting(ScenarioRefusal::property)
        .containsExactly(
            "priority", "action.httpStatus", "action.delayMs", "action.settlesAfterMs");
  }

  @Test
  void a_moment_to_settle_in_that_is_not_a_number_is_refused_naming_the_field_it_was_typed_in() {
    assertThat(refusalsOf(aForm().withSettlesAfterMs("five seconds")))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("action.settlesAfterMs");
              assertThat(refusal.reason()).isEqualTo("must be a whole number of milliseconds");
            });
  }

  @Test
  void a_moment_to_settle_in_a_tester_typed_reaches_the_rule_that_holds_the_payment_in_flight() {
    DeclaredScenario declared =
        declaredBy(
            aForm()
                .withActionType("PROCESSING_THEN_TERMINAL")
                .withOperation("POST /v1/payment_intents")
                .withSettlesAfterMs("  5000  "));

    assertThat(declared.action().type()).isEqualTo("PROCESSING_THEN_TERMINAL");
    assertThat(declared.action().settlesAfterMs()).isEqualTo(5000L);
  }

  @Test
  void a_priority_no_whole_number_column_could_hold_is_refused_rather_than_breaking_the_write() {
    List<ScenarioRefusal> refusals = refusalsOf(aForm().withPriority("99999999999"));

    assertThat(refusals).extracting(ScenarioRefusal::property).containsExactly("priority");
  }

  @Test
  void a_delay_longer_than_a_priority_could_hold_is_still_a_number_of_milliseconds() {
    DeclaredScenario declared =
        declaredBy(
            aForm()
                .withActionType("TRANSPORT_FAULT")
                .withFault("DELAYED_RESPONSE")
                .withDelayMs("99999999999"));

    assertThat(declared.action().delayMs()).isEqualTo(99999999999L);
  }

  @Test
  void a_header_value_with_no_header_to_carry_it_is_refused_rather_than_quietly_dropped() {
    List<ScenarioRefusal> refusals = refusalsOf(aForm().withHeader(null, "capture-decline"));

    assertThat(refusals).hasSize(1);
    assertThat(refusals.get(0).property()).isEqualTo("match.header");
  }

  @Test
  void a_header_a_tester_named_becomes_the_narrowing_the_engine_matches_on() {
    DeclaredScenario declared =
        declaredBy(aForm().withHeader("X-Simulator-Scenario", "capture-decline"));

    assertThat(declared.match().header())
        .containsExactly(Map.entry("X-Simulator-Scenario", "capture-decline"));
  }

  @Test
  void a_form_that_names_no_header_narrows_on_no_header() {
    DeclaredScenario declared = declaredBy(aForm().withMerchantId("merchant-1"));

    assertThat(declared.match().header()).isEmpty();
  }

  @Test
  void the_action_a_tester_chose_reaches_the_grammar_as_they_wrote_it() {
    DeclaredScenario declared =
        declaredBy(
            aForm()
                .withActionType("TRANSPORT_FAULT")
                .withFault("SERVER_ERROR")
                .withHttpStatus("502")
                .withPriority("100"));

    assertThat(declared.priority()).isEqualTo(100);
    assertThat(declared.action().type()).isEqualTo("TRANSPORT_FAULT");
    assertThat(declared.action().fault()).isEqualTo("SERVER_ERROR");
    assertThat(declared.action().httpStatus()).isEqualTo(502);
  }

  @Test
  void a_delay_a_tester_typed_reaches_the_grammar_in_milliseconds() {
    DeclaredScenario declared =
        declaredBy(
            aForm()
                .withActionType("TRANSPORT_FAULT")
                .withFault("DELAYED_RESPONSE")
                .withDelayMs("1500"));

    assertThat(declared.action().delayMs()).isEqualTo(1500L);
  }

  @Test
  void a_story_a_tester_wrote_reaches_the_grammar_with_the_states_it_stands_between() {
    DeclaredScenario declared =
        declaredBy(
            aForm()
                .withStory("psp-5xx-then-success")
                .withKeyedOn("TEST_RUN")
                .withWhenState("Started")
                .withThenState("Answered"));

    assertThat(declared.story()).isEqualTo("psp-5xx-then-success");
    assertThat(declared.keyedOn()).isEqualTo("TEST_RUN");
    assertThat(declared.whenState()).isEqualTo("Started");
    assertThat(declared.thenState()).isEqualTo("Answered");
    assertThat(declared.testRunId()).isEqualTo(RUN);
  }

  @Test
  void a_preset_is_expanded_under_the_priority_a_tester_set_and_narrowed_by_nothing_else() {
    ScenarioForm.Chosen read = aForm().withPriority("50").readPreset(RUN, "psp-5xx-then-success");

    assertThat(read).isInstanceOf(ScenarioForm.Chosen.Declared.class);
    ScenarioForm.Chosen.Declared declared = (ScenarioForm.Chosen.Declared) read;
    assertThat(declared.preset().preset()).isEqualTo("psp-5xx-then-success");
    assertThat(declared.preset().priority()).isEqualTo(50);
    assertThat(declared.preset().testRunId()).isEqualTo(RUN);
    assertThat(declared.preset().match()).isNull();
  }

  @Test
  void a_preset_priority_that_is_not_a_whole_number_is_refused_before_a_rule_is_written() {
    ScenarioForm.Chosen read =
        aForm().withPriority(TYPED_BY_MISTAKE).readPreset(RUN, "psp-5xx-then-success");

    assertThat(read).isInstanceOf(ScenarioForm.Chosen.Unreadable.class);
    ScenarioForm.Chosen.Unreadable unreadable = (ScenarioForm.Chosen.Unreadable) read;
    assertThat(unreadable.refusals())
        .extracting(ScenarioRefusal::property)
        .containsExactly("priority");
  }

  private static DeclaredScenario declaredBy(Written form) {
    ScenarioForm.Read read = form.read(RUN);
    assertThat(read).isInstanceOf(ScenarioForm.Read.Declared.class);
    return ((ScenarioForm.Read.Declared) read).scenario();
  }

  private static List<ScenarioRefusal> refusalsOf(Written form) {
    ScenarioForm.Read read = form.read(RUN);
    assertThat(read).isInstanceOf(ScenarioForm.Read.Unreadable.class);
    return ((ScenarioForm.Read.Unreadable) read).refusals();
  }

  private static Written aForm() {
    return new Written();
  }

  private static final class Written {

    private String priority;
    private String operation;
    private String merchantId;
    private String orderId;
    private String eventType;
    private String headerName;
    private String headerValue;
    private String story;
    private String keyedOn;
    private String whenState;
    private String thenState;
    private String actionType;
    private String fault;
    private String httpStatus;
    private String delayMs;
    private String settlesAfterMs;

    Written withPriority(String value) {
      priority = value;
      return this;
    }

    Written withOperation(String value) {
      operation = value;
      return this;
    }

    Written withMerchantId(String value) {
      merchantId = value;
      return this;
    }

    Written withOrderId(String value) {
      orderId = value;
      return this;
    }

    Written withHeader(String name, String value) {
      headerName = name;
      headerValue = value;
      return this;
    }

    Written withStory(String value) {
      story = value;
      return this;
    }

    Written withKeyedOn(String value) {
      keyedOn = value;
      return this;
    }

    Written withWhenState(String value) {
      whenState = value;
      return this;
    }

    Written withThenState(String value) {
      thenState = value;
      return this;
    }

    Written withEventType(String value) {
      eventType = value;
      return this;
    }

    Written withActionType(String value) {
      actionType = value;
      return this;
    }

    Written withFault(String value) {
      fault = value;
      return this;
    }

    Written withHttpStatus(String value) {
      httpStatus = value;
      return this;
    }

    Written withDelayMs(String value) {
      delayMs = value;
      return this;
    }

    Written withSettlesAfterMs(String value) {
      settlesAfterMs = value;
      return this;
    }

    ScenarioForm.Read read(String testRunId) {
      return form().read(testRunId);
    }

    ScenarioForm.Chosen readPreset(String testRunId, String preset) {
      return ScenarioForm.readPreset(testRunId, preset, priority);
    }

    private ScenarioForm form() {
      return new ScenarioForm(
          priority,
          operation,
          merchantId,
          orderId,
          null,
          null,
          null,
          null,
          null,
          eventType,
          headerName,
          headerValue,
          story,
          keyedOn,
          whenState,
          thenState,
          actionType,
          null,
          fault,
          httpStatus,
          delayMs,
          settlesAfterMs);
    }
  }
}
