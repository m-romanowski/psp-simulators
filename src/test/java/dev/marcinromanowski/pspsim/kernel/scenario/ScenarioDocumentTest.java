package dev.marcinromanowski.pspsim.kernel.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ScenarioDocumentTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final ProviderId AZERICARD = ProviderId.of("azericard");

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final String SECRET_HEADER_VALUE = "sk-live-0123456789";

  private final ScenarioGrammar grammar =
      new ScenarioGrammar(new FaultDelayCeiling(Duration.ofSeconds(30)));

  static Stream<ScenarioAction> theActionsThisGrammarReads() {
    return Stream.of(
        new ScenarioAction.FixedSuccess(),
        new ScenarioAction.TerminalFailure("813"),
        new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(502)),
        new ScenarioAction.TransportFaultAlways(new TransportFault.TruncatedBody()),
        new ScenarioAction.TransportFaultAlways(new TransportFault.CorruptedBody()),
        new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()),
        new ScenarioAction.TransportFaultAlways(
            new TransportFault.DelayedResponse(Duration.ofSeconds(2))),
        new ScenarioAction.TransportFaultInsteadOfOperation(new TransportFault.ServerError(503)),
        new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null),
        new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), "813"),
        new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5)),
        new ScenarioAction.DeliveryDuplicated(),
        new ScenarioAction.DeliveryDropped());
  }

  @ParameterizedTest(name = "{0} survives being written out and read back")
  @MethodSource("theActionsThisGrammarReads")
  void a_rule_carrying_any_action_this_grammar_reads_comes_back_as_the_same_rule(
      ScenarioAction action) {
    NewScenarioRule expanded = ruleOf(action);

    NewScenarioRule readBack =
        grammar.ruleOf(GROW, RUN, PortableScenario.of(expanded).asDeclared());

    assertThat(readBack).isEqualTo(expanded);
  }

  @Test
  void a_document_of_a_whole_expansion_reads_back_as_the_rules_it_was_written_from() {
    List<NewScenarioRule> expanded =
        List.of(ruleOf(new ScenarioAction.FixedSuccess()), ruleOf(theTerminalFailure()));

    ScenarioDocument document =
        ScenarioDocument.of(RUN, GROW, ScenarioPreset.CANCEL_HOLD_SUCCESS, expanded);

    assertThat(readBack(document)).isEqualTo(expanded);
  }

  @Test
  void a_document_names_the_run_the_provider_and_the_preset_it_was_expanded_from() {
    ScenarioDocument document =
        ScenarioDocument.of(
            RUN,
            GROW,
            ScenarioPreset.CANCEL_HOLD_SUCCESS,
            List.of(ruleOf(new ScenarioAction.FixedSuccess())));

    assertThat(document.testRunId()).isEqualTo(RUN.value());
    assertThat(document.provider()).isEqualTo(GROW.value());
    assertThat(document.preset()).isEqualTo("cancel-hold-success");
  }

  @Test
  void a_rule_that_narrows_on_a_header_is_named_by_the_header_and_never_by_the_value_it_matches() {
    List<ScenarioRefusal> withheld =
        ScenarioDocument.theNarrowingsNoDocumentCarries(List.of(narrowedByAnAuthorizationHeader()));

    assertThat(withheld)
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("match.header.Authorization");
              assertThat(refusal.reason()).doesNotContain(SECRET_HEADER_VALUE);
            });
  }

  @Test
  void a_rule_that_narrows_on_no_header_is_withheld_for_nothing() {
    assertThat(
            ScenarioDocument.theNarrowingsNoDocumentCarries(
                List.of(ruleOf(new ScenarioAction.FixedSuccess()))))
        .isEmpty();
  }

  @Test
  void building_a_document_from_a_rule_that_narrows_on_a_header_is_refused_without_the_value() {
    List<NewScenarioRule> expanded = List.of(narrowedByAnAuthorizationHeader());

    assertThatThrownBy(
            () -> ScenarioDocument.of(RUN, GROW, ScenarioPreset.CANCEL_HOLD_SUCCESS, expanded))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("header")
        .hasMessageNotContaining(SECRET_HEADER_VALUE);
  }

  @Test
  void a_rule_read_back_out_of_a_document_narrows_on_no_header_whatever_the_document_carried() {
    PortableMatch narrowing =
        new PortableMatch("m-1", null, null, null, null, null, null, "settle", null);

    assertThat(narrowing.asDeclared().header()).isNull();
    assertThat(ScenarioGrammar.matchOf(narrowing.asDeclared()).header()).isEmpty();
  }

  @Test
  void a_document_naming_another_provider_is_refused_by_the_provider_it_names() {
    ScenarioDocument document =
        ScenarioDocument.of(
            RUN,
            GROW,
            ScenarioPreset.CANCEL_HOLD_SUCCESS,
            List.of(ruleOf(new ScenarioAction.FixedSuccess())));

    assertThat(document.theReasonsItCannotBeTakenBackBy(AZERICARD))
        .extracting(ScenarioRefusal::property)
        .containsExactly("provider");
    assertThat(document.theReasonsItCannotBeTakenBackBy(AZERICARD).getFirst().reason())
        .as(
            "a tester pasting a document expanded for another provider has already picked this"
                + " provider in the bar of the screen, so a refusal naming neither of the two"
                + " leaves them reading that provider must name the provider")
        .contains("grow")
        .contains("azericard")
        .contains(ScenarioDocument.A_RULE_OF_ONE_PROVIDER_MEANS_NOTHING_TO_ANOTHER);
  }

  @Test
  void a_document_naming_no_provider_at_all_is_refused_by_the_same_property() {
    ScenarioDocument unnamed = new ScenarioDocument(RUN.value(), null, null, List.of(aPortable()));

    assertThat(unnamed.theReasonsItCannotBeTakenBackBy(GROW))
        .extracting(ScenarioRefusal::property)
        .containsExactly("provider");
    assertThat(unnamed.theReasonsItCannotBeTakenBackBy(GROW).getFirst().reason())
        .as(
            "a document carrying no provider cannot be reported as one expanded for nothing, and"
                + " the provider this request addresses is still worth naming")
        .contains(ScenarioDocument.THE_DOCUMENT_NAMES_NO_PROVIDER)
        .contains("grow");
  }

  @Test
  void a_document_carrying_no_rule_is_refused_by_the_rules_it_does_not_carry() {
    ScenarioDocument empty = new ScenarioDocument(RUN.value(), GROW.value(), null, List.of());

    assertThat(empty.theReasonsItCannotBeTakenBackBy(GROW))
        .extracting(ScenarioRefusal::property)
        .containsExactly("rules");
  }

  @Test
  void a_document_naming_this_provider_and_carrying_one_rule_is_refused_for_nothing() {
    ScenarioDocument document =
        new ScenarioDocument(RUN.value(), GROW.value(), null, List.of(aPortable()));

    assertThat(document.theReasonsItCannotBeTakenBackBy(GROW)).isEmpty();
  }

  @Test
  void a_document_whose_rules_are_absent_carries_none_rather_than_failing_to_be_read() {
    ScenarioDocument absent = new ScenarioDocument(RUN.value(), GROW.value(), null, null);

    assertThat(absent.carriedRules()).isEmpty();
    assertThat(absent.theReasonsItCannotBeTakenBackBy(GROW))
        .extracting(ScenarioRefusal::property)
        .containsExactly("rules");
  }

  @Test
  void a_carried_rule_that_names_no_match_reads_back_as_a_rule_that_narrows_on_nothing() {
    PortableScenario unnarrowed =
        new PortableScenario(
            null,
            null,
            null,
            null,
            null,
            null,
            new DeclaredAction("FIXED_SUCCESS", null, null, null, null, null, null, null));

    NewScenarioRule readBack = grammar.ruleOf(GROW, RUN, unnarrowed.asDeclared());

    assertThat(readBack.match()).isEqualTo(ScenarioMatch.anyRequest());
    assertThat(readBack.priority()).isZero();
    assertThat(readBack.story()).isNull();
  }

  @Test
  void two_rules_narrowing_on_one_header_are_withheld_once_rather_than_once_each() {
    NewScenarioRule narrowed = narrowedByAnAuthorizationHeader();

    assertThat(ScenarioDocument.theNarrowingsNoDocumentCarries(List.of(narrowed, narrowed)))
        .hasSize(1);
  }

  private List<NewScenarioRule> readBack(ScenarioDocument document) {
    return document.carriedRules().stream()
        .map(carried -> grammar.ruleOf(GROW, RUN, carried.asDeclared()))
        .toList();
  }

  private static ScenarioAction theTerminalFailure() {
    return new ScenarioAction.TerminalFailure("813");
  }

  private static PortableScenario aPortable() {
    return PortableScenario.of(ruleOf(new ScenarioAction.FixedSuccess()));
  }

  private static NewScenarioRule ruleOf(ScenarioAction action) {
    return new NewScenarioRule(
        GROW,
        RUN,
        7,
        new ScenarioMatch(
            "merchant-1",
            "order-1",
            "object-1",
            "customer-1",
            "card-1",
            Amount.of("120.50"),
            "ILS",
            "settleSuspendedTransaction",
            null,
            Map.of()),
        action,
        new ScenarioStory("a-story", StoryKeying.TEST_RUN, "Started", "Answered"));
  }

  private static NewScenarioRule narrowedByAnAuthorizationHeader() {
    return new NewScenarioRule(
        GROW,
        RUN,
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
            Map.of("Authorization", SECRET_HEADER_VALUE)),
        new ScenarioAction.FixedSuccess(),
        null);
  }
}
