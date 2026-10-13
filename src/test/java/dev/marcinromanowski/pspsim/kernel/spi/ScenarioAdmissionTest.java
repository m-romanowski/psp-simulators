package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredAction;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.PortableMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.PortableScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScenarioAdmissionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId NAMED_RUN = TestRunId.of("run-a");

  private static final String A_SUPPLIED_SECRET = "sk-live-0123456789";

  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final RegisteredRuns testRuns = new RegisteredRuns();
  private final ProviderDouble provider = new ProviderDouble();
  private final ScenarioAdmission admission =
      new ScenarioAdmission(
          new ScenarioGrammar(new FaultDelayCeiling(Duration.ofSeconds(30))),
          rules,
          testRuns,
          DEFAULT_RUN);

  @Test
  void a_rule_this_provider_can_perform_is_written_and_reported_with_the_id_it_was_given() {
    ScenarioAdmission.Verdict verdict = admission.write(provider, declining("813", NAMED_RUN));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Written.class,
            written ->
                assertThat(written.rules())
                    .singleElement()
                    .satisfies(
                        rule -> {
                          assertThat(rule.scenarioId()).isEqualTo("scn_1");
                          assertThat(rule.testRunId()).isEqualTo(NAMED_RUN);
                          assertThat(rule.action())
                              .isEqualTo(new ScenarioAction.TerminalFailure("813"));
                        }));
    assertThat(rules.of(ProviderDouble.ID, NAMED_RUN)).hasSize(1);
  }

  @Test
  void writing_a_rule_registers_the_test_run_it_names_so_a_harness_can_find_it_afterwards() {
    admission.write(provider, declining("813", NAMED_RUN));

    assertThat(testRuns.exists(NAMED_RUN)).isTrue();
  }

  @Test
  void a_rule_that_names_no_test_run_is_written_in_the_default_namespace() {
    admission.write(provider, declining("813", null));

    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).hasSize(1);
  }

  @Test
  void a_rule_naming_a_test_run_no_namespace_can_hold_is_rejected_and_nothing_is_written() {
    ScenarioAdmission.Verdict verdict =
        admission.write(
            provider,
            new DeclaredScenario(
                "sk-live-0123456789 leaked", null, null, null, null, null, null, declining("813")));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Rejected.class,
            rejected -> {
              assertThat(rejected.message()).startsWith("testRunId");
              assertThat(rejected.message()).doesNotContain("sk-live-0123456789");
            });
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
    assertThat(testRuns.registered()).isEmpty();
  }

  @Test
  void a_rule_this_layer_cannot_read_into_an_action_is_rejected_and_nothing_is_written() {
    ScenarioAdmission.Verdict verdict =
        admission.write(
            provider,
            new DeclaredScenario(null, null, null, null, null, null, null, declining(null)));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message()).isEqualTo("action.providerCode is required"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
    assertThat(testRuns.registered()).isEmpty();
  }

  @Test
  void an_action_type_this_layer_cannot_read_is_refused_beside_every_field_it_can_still_judge() {
    ScenarioAdmission.Verdict verdict =
        admission.write(
            provider,
            acting(
                new DeclaredAction(
                    "TRANSIENT_THEN_SUCCESS", 1, null, null, 504, null, null, null)));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly(
                        "action.type", "action.failuresBeforeSuccess", "action.httpStatus"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void the_refusals_of_this_layer_are_answered_before_the_provider_is_asked_about_the_rule() {
    ScenarioAdmission.Verdict verdict =
        admission.write(
            provider,
            new DeclaredScenario(
                null,
                null,
                null,
                null,
                null,
                null,
                new DeclaredMatch(null, null, null, null, null, null, "PLN", null, null, null),
                new DeclaredAction(
                    "TERMINAL_FAILURE", null, "not-a-number", null, null, null, 250L, null)));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("action.delayMs", "action.providerCode", "match.currency"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
    assertThat(testRuns.registered()).isEmpty();
  }

  @Test
  void a_preset_this_provider_cannot_express_says_what_is_missing_and_writes_nothing() {
    ScenarioAdmission.Verdict verdict =
        admission.expand(
            provider.expanding(new PresetExpansion.NotExpressible("grow has no card vault")),
            new DeclaredPreset(null, "card-add-success", null, null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .containsExactly(new ScenarioRefusal("preset", "grow has no card vault")));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void a_preset_this_simulator_does_not_publish_is_rejected_before_the_provider_is_asked() {
    ScenarioAdmission.Verdict verdict =
        admission.expand(provider, new DeclaredPreset(null, "cancel-hold-succes", null, null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Rejected.class,
            rejected -> assertThat(rejected.message()).startsWith("preset must be one of"));
  }

  @Test
  void a_preset_narrowed_by_an_operation_is_rejected_because_a_preset_names_its_own_operations() {
    ScenarioAdmission.Verdict verdict =
        admission.expand(
            provider,
            new DeclaredPreset(
                null,
                "cancel-hold-success",
                null,
                new DeclaredMatch(
                    null, null, null, null, null, null, null, "refundTransaction", null, null)));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .startsWith("match.operation is not a narrowing a preset accepts"));
  }

  @Test
  void every_rule_of_an_expansion_is_written_in_one_go_and_the_test_run_is_registered() {
    ScenarioAdmission.Verdict verdict =
        admission.expand(
            provider.expanding(
                new PresetExpansion.Rules(
                    List.of(
                        expanded(new ScenarioAction.FixedSuccess(), NAMED_RUN),
                        expanded(new ScenarioAction.TerminalFailure("813"), NAMED_RUN)))),
            new DeclaredPreset("run-a", "cancel-hold-success", 100, null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Written.class,
            written -> assertThat(written.rules()).hasSize(2));
    assertThat(rules.of(ProviderDouble.ID, NAMED_RUN)).hasSize(2);
    assertThat(testRuns.exists(NAMED_RUN)).isTrue();
  }

  @Test
  void
      an_expansion_of_a_provider_double_holding_past_the_ceiling_is_refused_once_although_no_preset_grow_publishes_can_build_one() {
    ScenarioAction held =
        new ScenarioAction.TransportFaultAlways(
            new TransportFault.DelayedResponse(Duration.ofSeconds(60)));

    ScenarioAdmission.Verdict verdict =
        admission.expand(
            provider.expanding(
                new PresetExpansion.Rules(
                    List.of(expanded(held, DEFAULT_RUN), expanded(held, DEFAULT_RUN)))),
            new DeclaredPreset(null, "psp-5xx-then-success", null, null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("action.delayMs"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
    assertThat(testRuns.registered()).isEmpty();
  }

  @Test
  void an_expansion_the_provider_itself_refuses_writes_nothing_and_names_the_property() {
    ScenarioAdmission.Verdict verdict =
        admission.expand(
            provider.expanding(
                new PresetExpansion.Rules(
                    List.of(
                        expanded(new ScenarioAction.TerminalFailure("declined"), DEFAULT_RUN)))),
            new DeclaredPreset(null, "cancel-hold-success", null, null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("action.providerCode"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void
      a_rule_that_shapes_a_delivery_and_narrows_on_a_request_fact_is_refused_and_nothing_written() {
    ScenarioAdmission.Verdict verdict =
        admission.write(
            provider,
            new DeclaredScenario(
                null,
                null,
                null,
                null,
                null,
                null,
                new DeclaredMatch(null, "order-1", null, null, null, null, null, null, null, null),
                new DeclaredAction("DELIVERY_DROPPED", null, null, null, null, null, null, null)));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("match.orderId"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void an_expansion_whose_delivery_rules_narrow_on_a_request_fact_is_refused_once_for_both() {
    NewScenarioRule narrowed =
        new NewScenarioRule(
            ProviderDouble.ID,
            DEFAULT_RUN,
            0,
            new ScenarioMatch(null, "order-1", null, null, null, null, null, null, null, Map.of()),
            new ScenarioAction.DeliveryDropped(),
            null);

    ScenarioAdmission.Verdict verdict =
        admission.expand(
            provider.expanding(new PresetExpansion.Rules(List.of(narrowed, narrowed))),
            new DeclaredPreset(null, "webhook-duplicate", null, null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("match.orderId"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void a_preview_of_an_expansion_this_provider_can_make_writes_no_rule_and_registers_no_run() {
    ScenarioAdmission.Expansion preview =
        admission.preview(
            provider.expanding(
                new PresetExpansion.Rules(
                    List.of(expanded(new ScenarioAction.FixedSuccess(), NAMED_RUN)))),
            new DeclaredPreset(NAMED_RUN.value(), "cancel-hold-success", null, null));

    assertThat(preview)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Expansion.Expanded.class,
            expanded -> {
              assertThat(expanded.testRunId()).isEqualTo(NAMED_RUN);
              assertThat(expanded.preset()).isEqualTo(ScenarioPreset.CANCEL_HOLD_SUCCESS);
              assertThat(expanded.rules()).hasSize(1);
            });
    assertThat(rules.of(ProviderDouble.ID, NAMED_RUN)).isEmpty();
    assertThat(testRuns.registered()).isEmpty();
  }

  @Test
  void a_preview_carries_the_priority_the_caller_asked_for_so_confirming_writes_that_priority() {
    ScenarioAdmission.Expansion preview =
        admission.preview(
            provider.expanding(
                new PresetExpansion.Rules(
                    List.of(expanded(new ScenarioAction.FixedSuccess(), DEFAULT_RUN)))),
            new DeclaredPreset(null, "cancel-hold-success", 7, null));

    assertThat(preview)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Expansion.Expanded.class,
            expanded -> assertThat(expanded.priority()).isEqualTo(7));
  }

  @Test
  void a_preview_turns_into_the_document_the_same_rules_are_taken_back_from() {
    ScenarioAdmission.Expansion preview =
        admission.preview(
            provider.expanding(
                new PresetExpansion.Rules(
                    List.of(expanded(new ScenarioAction.FixedSuccess(), DEFAULT_RUN)))),
            new DeclaredPreset(null, "cancel-hold-success", null, null));

    ScenarioDocument document =
        ((ScenarioAdmission.Expansion.Expanded) preview).asDocument(ProviderDouble.ID);

    assertThat(document.provider()).isEqualTo(ProviderDouble.ID.value());
    assertThat(document.preset()).isEqualTo("cancel-hold-success");
    assertThat(document.carriedRules()).hasSize(1);
  }

  @Test
  void a_preset_this_provider_cannot_express_is_refused_by_the_preview_as_well_as_by_the_write() {
    provider.expanding(new PresetExpansion.NotExpressible("this provider has no card vault"));
    DeclaredPreset asked = new DeclaredPreset(null, "card-add-success", null, null);

    assertThat(admission.preview(provider, asked))
        .isInstanceOfSatisfying(
            ScenarioAdmission.Expansion.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("preset"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void a_preview_of_an_expansion_that_narrows_on_a_header_is_refused_by_the_header_property() {
    ScenarioAdmission.Expansion preview =
        admission.preview(
            provider.expanding(new PresetExpansion.Rules(List.of(narrowedByAHeader()))),
            new DeclaredPreset(null, "cancel-hold-success", null, null));

    assertThat(preview)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Expansion.Refused.class,
            refused -> {
              assertThat(refused.refusals())
                  .extracting(ScenarioRefusal::property)
                  .containsExactly("match.header.Authorization");
              assertThat(refused.refusals())
                  .extracting(ScenarioRefusal::reason)
                  .noneSatisfy(reason -> assertThat(reason).contains(A_SUPPLIED_SECRET));
            });
  }

  @Test
  void an_expansion_that_narrows_on_a_header_is_still_written_although_no_document_carries_it() {
    ScenarioAdmission.Verdict verdict =
        admission.expand(
            provider.expanding(new PresetExpansion.Rules(List.of(narrowedByAHeader()))),
            new DeclaredPreset(null, "cancel-hold-success", null, null));

    assertThat(verdict).isInstanceOf(ScenarioAdmission.Verdict.Written.class);
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).hasSize(1);
  }

  @Test
  void taking_a_document_back_writes_every_rule_it_carries_and_registers_the_run_it_names() {
    ScenarioDocument document =
        ScenarioDocument.of(
            NAMED_RUN,
            ProviderDouble.ID,
            ScenarioPreset.CANCEL_HOLD_SUCCESS,
            List.of(
                expanded(new ScenarioAction.FixedSuccess(), NAMED_RUN),
                expanded(new ScenarioAction.TerminalFailure("813"), NAMED_RUN)));

    ScenarioAdmission.Verdict verdict = admission.takeBack(provider, document);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Written.class,
            written ->
                assertThat(written.rules())
                    .extracting(ScenarioRule::action)
                    .containsExactly(
                        new ScenarioAction.FixedSuccess(),
                        new ScenarioAction.TerminalFailure("813")));
    assertThat(rules.of(ProviderDouble.ID, NAMED_RUN)).hasSize(2);
    assertThat(testRuns.exists(NAMED_RUN)).isTrue();
  }

  @Test
  void a_document_taken_back_into_another_run_writes_its_rules_into_that_run_instead() {
    ScenarioDocument document =
        new ScenarioDocument(
            NAMED_RUN.value(),
            ProviderDouble.ID.value(),
            "cancel-hold-success",
            ScenarioDocument.of(
                    DEFAULT_RUN,
                    ProviderDouble.ID,
                    ScenarioPreset.CANCEL_HOLD_SUCCESS,
                    List.of(expanded(new ScenarioAction.FixedSuccess(), DEFAULT_RUN)))
                .carriedRules());

    admission.takeBack(provider, document);

    assertThat(rules.of(ProviderDouble.ID, NAMED_RUN)).hasSize(1);
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void a_document_naming_another_provider_is_refused_and_writes_none_of_its_rules() {
    ScenarioDocument document =
        new ScenarioDocument(
            DEFAULT_RUN.value(),
            "somebodyelse",
            "cancel-hold-success",
            ScenarioDocument.of(
                    DEFAULT_RUN,
                    ProviderDouble.ID,
                    ScenarioPreset.CANCEL_HOLD_SUCCESS,
                    List.of(expanded(new ScenarioAction.FixedSuccess(), DEFAULT_RUN)))
                .carriedRules());

    assertThat(admission.takeBack(provider, document))
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("provider"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void a_document_carrying_no_rule_is_refused_rather_than_reported_as_nothing_written() {
    ScenarioDocument empty =
        new ScenarioDocument(DEFAULT_RUN.value(), ProviderDouble.ID.value(), null, List.of());

    assertThat(admission.takeBack(provider, empty))
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("rules"));
    assertThat(testRuns.registered()).isEmpty();
  }

  static Stream<Arguments> theActionFieldsNoRuleOfThisSimulatorPerforms() {
    return Stream.of(
        Arguments.of(
            "action.failuresBeforeSuccess",
            "7",
            new DeclaredAction("FIXED_SUCCESS", 7, null, null, null, null, null, null)),
        Arguments.of(
            "action.httpStatus",
            "599",
            new DeclaredAction("FIXED_SUCCESS", null, null, null, 599, null, null, null)),
        Arguments.of(
            "action.fault",
            "DROPPED_CONNECTION",
            new DeclaredAction(
                "FIXED_SUCCESS", null, null, "DROPPED_CONNECTION", null, null, null, null)),
        Arguments.of(
            "action.settlesAfterMs",
            "4242",
            new DeclaredAction("FIXED_SUCCESS", null, null, null, null, null, null, 4242L)),
        Arguments.of(
            "action.terminalStatus",
            "PARTIALLY_REFUNDED",
            new DeclaredAction(
                "FIXED_SUCCESS", null, null, null, null, "PARTIALLY_REFUNDED", null, null)),
        Arguments.of(
            "action.delayMs",
            "4242",
            new DeclaredAction("FIXED_SUCCESS", null, null, null, null, null, 4242L, null)));
  }

  @ParameterizedTest(
      name = "a document naming {0} is refused by that property and not by its value")
  @MethodSource("theActionFieldsNoRuleOfThisSimulatorPerforms")
  void a_document_whose_action_names_a_field_this_simulator_cannot_perform_names_only_that_field(
      String property, String value, DeclaredAction action) {
    assertThat(admission.takeBack(provider, documentCarrying(action)))
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.refusals())
                  .extracting(ScenarioRefusal::property)
                  .containsExactly(property);
              assertThat(refused.refusals())
                  .extracting(ScenarioRefusal::reason)
                  .noneSatisfy(reason -> assertThat(reason).contains(value));
            });
  }

  @Test
  void a_document_whose_action_names_a_field_this_simulator_cannot_perform_writes_no_rule() {
    ScenarioDocument document =
        documentCarrying(
            new DeclaredAction(
                "FIXED_SUCCESS", null, null, null, null, "PARTIALLY_REFUNDED", null, null));

    admission.takeBack(provider, document);

    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
    assertThat(testRuns.registered()).isEmpty();
  }

  private static ScenarioDocument documentCarrying(DeclaredAction action) {
    return new ScenarioDocument(
        DEFAULT_RUN.value(),
        ProviderDouble.ID.value(),
        null,
        List.of(new PortableScenario(null, null, null, null, null, null, action)));
  }

  @Test
  void a_document_naming_a_retired_action_type_is_refused_rather_than_read_as_another_action() {
    ScenarioDocument document =
        documentCarrying(
            new DeclaredAction(
                "TRANSIENT_THEN_SUCCESS", null, "813", null, null, null, null, null));

    assertThat(admission.takeBack(provider, document))
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("action.type"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void a_document_whose_action_this_layer_cannot_read_writes_none_of_its_rules() {
    ScenarioDocument document =
        new ScenarioDocument(
            DEFAULT_RUN.value(),
            ProviderDouble.ID.value(),
            null,
            List.of(
                PortableScenario.of(expanded(new ScenarioAction.FixedSuccess(), DEFAULT_RUN)),
                new PortableScenario(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new DeclaredAction(
                        "TRANSIENT_THEN_SUCCESS", null, null, null, null, null, null, null))));

    assertThat(admission.takeBack(provider, document))
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("action.type"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void a_document_naming_a_test_run_no_namespace_can_hold_is_rejected_without_the_value() {
    ScenarioDocument document =
        new ScenarioDocument(
            A_SUPPLIED_SECRET + " leaked",
            ProviderDouble.ID.value(),
            null,
            List.of(PortableScenario.of(expanded(new ScenarioAction.FixedSuccess(), DEFAULT_RUN))));

    assertThat(admission.takeBack(provider, document))
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Rejected.class,
            rejected -> {
              assertThat(rejected.message()).startsWith("testRunId");
              assertThat(rejected.message()).doesNotContain(A_SUPPLIED_SECRET);
            });
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
  }

  @Test
  void a_document_whose_rule_this_provider_refuses_writes_none_of_the_rules_beside_it() {
    ScenarioDocument document =
        new ScenarioDocument(
            DEFAULT_RUN.value(),
            ProviderDouble.ID.value(),
            null,
            List.of(
                PortableScenario.of(expanded(new ScenarioAction.FixedSuccess(), DEFAULT_RUN)),
                new PortableScenario(
                    null,
                    null,
                    null,
                    null,
                    null,
                    new PortableMatch(null, "order-1", null, null, null, null, null, null, null),
                    new DeclaredAction(
                        "DELIVERY_DROPPED", null, null, null, null, null, null, null))));

    assertThat(admission.takeBack(provider, document))
        .isInstanceOfSatisfying(
            ScenarioAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(ScenarioRefusal::property)
                    .containsExactly("match.orderId"));
    assertThat(rules.of(ProviderDouble.ID, DEFAULT_RUN)).isEmpty();
    assertThat(testRuns.registered()).isEmpty();
  }

  private static NewScenarioRule narrowedByAHeader() {
    return new NewScenarioRule(
        ProviderDouble.ID,
        DEFAULT_RUN,
        0,
        new ScenarioMatch(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Map.of("Authorization", A_SUPPLIED_SECRET)),
        new ScenarioAction.FixedSuccess(),
        null);
  }

  private static NewScenarioRule expanded(ScenarioAction action, TestRunId testRunId) {
    return new NewScenarioRule(
        ProviderDouble.ID, testRunId, 0, ScenarioMatch.anyRequest(), action, null);
  }

  private static DeclaredScenario declining(
      @Nullable String providerCode, @Nullable TestRunId run) {
    return new DeclaredScenario(
        run == null ? null : run.value(),
        null,
        null,
        null,
        null,
        null,
        null,
        declining(providerCode));
  }

  private static DeclaredAction declining(@Nullable String providerCode) {
    return new DeclaredAction("TERMINAL_FAILURE", null, providerCode, null, null, null, null, null);
  }

  private static DeclaredScenario acting(DeclaredAction action) {
    return new DeclaredScenario(null, null, null, null, null, null, null, action);
  }

  private static final class RegisteredRuns implements TestRuns {

    private final Set<TestRunId> registered = new LinkedHashSet<>();

    Set<TestRunId> registered() {
      return Set.copyOf(registered);
    }

    @Override
    public void register(TestRunId testRunId) {
      registered.add(testRunId);
    }

    @Override
    public int forget(TestRunId testRunId) {
      return registered.remove(testRunId) ? 1 : 0;
    }

    @Override
    public boolean exists(TestRunId testRunId) {
      return registered.contains(testRunId);
    }

    @Override
    public List<TestRunId> all() {
      return List.copyOf(registered);
    }
  }

  @Test
  void every_arm_of_a_scenario_verdict_says_whether_it_refused_and_under_which_status() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD);

    assertThat(new ScenarioAdmission.Verdict.Rejected("unreadable").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(subject));
    assertThat(
            new ScenarioAdmission.Verdict.Refused(List.of(new ScenarioRefusal("action", "no")))
                .theRefusalItReports())
        .isEqualTo(ActionVerdict.refused(subject));
    assertThat(new ScenarioAdmission.Verdict.Written(List.of()).theRefusalItReports()).isNull();
  }
}
