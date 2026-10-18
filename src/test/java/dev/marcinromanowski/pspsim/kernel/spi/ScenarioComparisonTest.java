package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ScenarioComparisonTest {

  private static final Instant AT = Instant.parse("2026-09-25T09:00:00Z");
  private static final TestRunId RUN = TestRunId.of("compare-scenarios");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("compare-scenarios-2");
  private static final ProviderId GROW = ProviderId.of("grow");
  private static final ProviderId AZERICARD = ProviderId.of("azericard");
  private static final String SETTLE = "settleSuspendedTransaction";
  private static final String LOOKUP = "getTransactionInfo";
  private static final String THE_KEY_OF_A_SETTLEMENT = "the payment process the call belongs to";
  private static final String THE_KEY_OF_A_LOOKUP = "the transaction the lookup names";
  private static final String A_NUMERIC_ERROR_ID = "a numeric err.id";
  private static final String SIGNATURE_HEADER = "X-Signature";

  private static final ProviderVocabulary GROW_VOCABULARY =
      new ProviderVocabulary(
          GROW,
          List.of(
              new ProviderOperation(
                  SETTLE,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.AMOUNT),
                  THE_KEY_OF_A_SETTLEMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  LOOKUP,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.AMOUNT),
                  THE_KEY_OF_A_LOOKUP,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
          Map.of(MatchFact.CURRENCY, "provider grow publishes no currency parameter"),
          new FailureCodes("[0-9]+", A_NUMERIC_ERROR_ID),
          ProcessingStates.noneOfThem("provider grow publishes no processing status"));

  private static final ProviderVocabulary A_VOCABULARY_THAT_REPORTS_A_PROCESSING_STATE =
      new ProviderVocabulary(
          GROW,
          GROW_VOCABULARY.operations(),
          GROW_VOCABULARY.factsItNeverCarries(),
          GROW_VOCABULARY.failureCodes(),
          ProcessingStates.onTheseOperations(LOOKUP));

  @Test
  void two_rules_that_say_the_same_thing_in_every_field_produce_no_difference_at_all() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_first", 7L, narrowedTo("merchant-1", "10.00"), aDecline("101"), aStep()),
            rule("scn_second", 9L, narrowedTo("merchant-1", "10.00"), aDecline("101"), aStep()));

    assertThat(compared.differences()).isEmpty();
    assertThat(compared.thePropertiesBothSidesSayAlike())
        .contains(
            "priority",
            "match.operation",
            "match.merchantId",
            "match.amount",
            "action.type",
            "action.providerCode",
            "story",
            "keyedOn",
            "whenState",
            "thenState")
        .hasSize(20);
  }

  @Test
  void one_changed_field_is_the_only_difference_and_both_sides_values_are_carried() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_left", 1L, narrowedTo("merchant-1", "10.00"), aSuccess(), null),
            rule("scn_right", 2L, narrowedTo("merchant-1", "25.50"), aSuccess(), null));

    assertThat(compared.differences())
        .extracting(ScenarioComparison.Difference::property)
        .containsExactly("match.amount");
    assertThat(theOnly(compared).onTheLeft()).isEqualTo(ScenarioComparison.Named.of("10.00"));
    assertThat(theOnly(compared).onTheRight()).isEqualTo(ScenarioComparison.Named.of("25.50"));
    assertThat(compared.thePropertiesBothSidesSayAlike()).doesNotContain("match.amount");
  }

  @Test
  void a_fact_one_rule_narrows_on_and_the_other_does_not_says_so_rather_than_showing_a_blank() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_left", 1L, narrowedTo("merchant-1", "10.00"), aSuccess(), null),
            rule("scn_right", 2L, narrowedTo("merchant-1", null), aSuccess(), null));

    assertThat(theOnly(compared).property()).isEqualTo("match.amount");
    assertThat(theOnly(compared).onTheLeft().named()).isTrue();
    assertThat(theOnly(compared).onTheRight()).isEqualTo(ScenarioComparison.Named.nothing());
    assertThat(theOnly(compared).onTheRight().value()).isNull();
  }

  @Test
  void an_action_and_its_timing_are_both_compared_and_each_is_reported_under_its_own_property() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule(
                "scn_held",
                1L,
                ScenarioMatch.anyRequest().reporting("payment_process.completed"),
                new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5)),
                null),
            rule(
                "scn_dropped",
                2L,
                ScenarioMatch.anyRequest().reporting("payment_process.completed"),
                new ScenarioAction.DeliveryDropped(),
                null));

    assertThat(compared.differences())
        .extracting(ScenarioComparison.Difference::property)
        .containsExactly("action.type", "action.delayMs");
    assertThat(theDifferenceOf(compared, "action.type").onTheLeft())
        .isEqualTo(ScenarioComparison.Named.of(ScenarioGrammar.DELIVERY_DELAYED));
    assertThat(theDifferenceOf(compared, "action.type").onTheRight())
        .isEqualTo(ScenarioComparison.Named.of(ScenarioGrammar.DELIVERY_DROPPED));
    assertThat(theDifferenceOf(compared, "action.delayMs").onTheLeft())
        .isEqualTo(ScenarioComparison.Named.of("5000"));
    assertThat(theDifferenceOf(compared, "action.delayMs").onTheRight())
        .isEqualTo(ScenarioComparison.Named.nothing());
  }

  @Test
  void a_transport_fault_and_the_status_it_answers_with_are_compared_as_two_properties() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule(
                "scn_502",
                1L,
                ScenarioMatch.anyRequest().naming(SETTLE),
                new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(502)),
                null),
            rule(
                "scn_cut",
                2L,
                ScenarioMatch.anyRequest().naming(SETTLE),
                new ScenarioAction.TransportFaultAlways(new TransportFault.DroppedConnection()),
                null));

    assertThat(compared.differences())
        .extracting(ScenarioComparison.Difference::property)
        .containsExactly("action.fault", "action.httpStatus");
    assertThat(theDifferenceOf(compared, "action.httpStatus").onTheLeft())
        .isEqualTo(ScenarioComparison.Named.of("502"));
    assertThat(theDifferenceOf(compared, "action.httpStatus").onTheRight())
        .isEqualTo(ScenarioComparison.Named.nothing());
  }

  @Test
  void a_header_narrowing_is_reported_by_the_header_it_names_and_its_value_is_withheld() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_signed", 1L, narrowedByHeader("a-secret-a-tester-typed"), aSuccess(), null),
            rule("scn_unsigned", 2L, ScenarioMatch.anyRequest().naming(SETTLE), aSuccess(), null));

    assertThat(theOnly(compared).property())
        .isEqualTo(ScenarioComparison.HEADER + SIGNATURE_HEADER);
    assertThat(theOnly(compared).onTheLeft())
        .isEqualTo(ScenarioComparison.Named.whoseValueIsWithheld());
    assertThat(theOnly(compared).onTheLeft().value()).isNull();
    assertThat(theOnly(compared).onTheRight()).isEqualTo(ScenarioComparison.Named.nothing());
  }

  @Test
  void two_rules_narrowing_on_the_same_header_with_different_values_are_reported_as_saying_alike() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_one", 1L, narrowedByHeader("one-secret"), aSuccess(), null),
            rule("scn_two", 2L, narrowedByHeader("another-secret"), aSuccess(), null));

    assertThat(compared.differences()).isEmpty();
    assertThat(compared.thePropertiesBothSidesSayAlike())
        .contains(ScenarioComparison.HEADER + SIGNATURE_HEADER);
  }

  @Test
  void a_header_both_rules_narrow_on_is_said_alike_on_its_name_alone_and_the_answer_says_so() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_one", 1L, narrowedByHeader("one-secret"), aSuccess(), null),
            rule("scn_two", 2L, narrowedByHeader("another-secret"), aSuccess(), null));

    assertThat(compared.thePropertiesSaidAlikeOnTheirNameAlone())
        .containsExactly(ScenarioComparison.HEADER + SIGNATURE_HEADER);
    assertThat(compared.saysAPropertyAlikeOnItsNameAlone()).isTrue();
    assertThat(compared.whatAPropertySaidAlikeOnItsNameDoesNotCompare())
        .contains("compares the header's name and never its value")
        .contains("match a request the other refuses");
  }

  @Test
  void a_comparison_that_withheld_no_value_says_no_property_alike_on_its_name_alone() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_one", 1L, narrowedTo("merchant-1", "10.00"), aSuccess(), null),
            rule("scn_two", 2L, narrowedTo("merchant-1", "10.00"), aSuccess(), null));

    assertThat(compared.differences()).isEmpty();
    assertThat(compared.thePropertiesBothSidesSayAlike()).contains("match.merchantId");
    assertThat(compared.thePropertiesSaidAlikeOnTheirNameAlone()).isEmpty();
    assertThat(compared.saysAPropertyAlikeOnItsNameAlone()).isFalse();
    assertThat(compared.whatAPropertySaidAlikeOnItsNameDoesNotCompare()).isNull();
  }

  @Test
  void a_comparison_carrying_a_marker_beside_no_property_said_alike_on_its_name_is_refused() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_one", 1L, narrowedByHeader("one-secret"), aSuccess(), null),
            rule("scn_two", 2L, narrowedByHeader("another-secret"), aSuccess(), null));

    ScenarioComparison.Side onTheLeft = compared.onTheLeft();
    ScenarioComparison.Side onTheRight = compared.onTheRight();
    List<ScenarioComparison.Difference> differences = compared.differences();
    List<String> bothSidesSayAlike = compared.thePropertiesBothSidesSayAlike();
    List<String> alikeOnTheirNameAlone = compared.thePropertiesSaidAlikeOnTheirNameAlone();
    List<ScenarioComparison.NotHeldByAScenario> cannotCompare =
        compared.theDimensionsItCannotCompare();
    List<String> nothingSaidAlikeOnItsNameAlone = List.of();

    assertThatThrownBy(
            () ->
                new ScenarioComparison.Comparison.Compared(
                    onTheLeft,
                    onTheRight,
                    differences,
                    bothSidesSayAlike,
                    nothingSaidAlikeOnItsNameAlone,
                    ScenarioComparison.A_HEADER_SAID_ALIKE_IS_ALIKE_IN_ITS_NAME_ALONE,
                    cannotCompare))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a marker standing beside no property");
    assertThatThrownBy(
            () ->
                new ScenarioComparison.Comparison.Compared(
                    onTheLeft,
                    onTheRight,
                    differences,
                    bothSidesSayAlike,
                    alikeOnTheirNameAlone,
                    null,
                    cannotCompare))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a withholding standing beside no marker");
  }

  @Test
  void
      an_operation_difference_is_described_in_the_words_the_provider_publishes_for_its_operations() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_settle", 1L, ScenarioMatch.anyRequest().naming(SETTLE), aSuccess(), null),
            rule("scn_lookup", 2L, ScenarioMatch.anyRequest().naming(LOOKUP), aSuccess(), null));

    assertThat(theOnly(compared).inTheProvidersWords())
        .isEqualTo(
            "provider grow exposes "
                + SETTLE
                + ", "
                + LOOKUP
                + " on this contract, and keys a story about "
                + SETTLE
                + " on "
                + THE_KEY_OF_A_SETTLEMENT
                + " and about "
                + LOOKUP
                + " on "
                + THE_KEY_OF_A_LOOKUP);
  }

  @Test
  void an_operation_only_one_side_names_is_the_only_one_the_provider_words_key_a_story_about() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_settle", 1L, ScenarioMatch.anyRequest().naming(SETTLE), aSuccess(), null),
            rule("scn_any", 2L, ScenarioMatch.anyRequest(), aSuccess(), null));

    assertThat(theOnly(compared).inTheProvidersWords())
        .isEqualTo(
            "provider grow exposes "
                + SETTLE
                + ", "
                + LOOKUP
                + " on this contract, and keys a story about "
                + SETTLE
                + " on "
                + THE_KEY_OF_A_SETTLEMENT);
  }

  @Test
  void a_decline_code_difference_carries_the_shape_of_code_the_provider_says_it_answers_with() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_101", 1L, ScenarioMatch.anyRequest().naming(SETTLE), aDecline("101"), null),
            rule("scn_102", 2L, ScenarioMatch.anyRequest().naming(SETTLE), aDecline("102"), null));

    assertThat(theOnly(compared).property()).isEqualTo(ScenarioComparison.PROVIDER_CODE);
    assertThat(theOnly(compared).inTheProvidersWords())
        .isEqualTo("provider grow declines with " + A_NUMERIC_ERROR_ID);
  }

  @Test
  void a_decline_code_difference_names_the_codes_of_an_operation_that_declares_its_own() {
    ProviderVocabulary split =
        new ProviderVocabulary(
            GROW,
            GROW_VOCABULARY.operations(),
            GROW_VOCABULARY.factsItNeverCarries(),
            GROW_VOCABULARY.failureCodes(),
            GROW_VOCABULARY.processingStates(),
            Map.of(
                SETTLE,
                new FailureCodes("1[0-9]+", "a code from 100"),
                LOOKUP,
                GROW_VOCABULARY.failureCodes()));

    String said =
        ScenarioComparison.theCodesItDeclinesWith(
            rule("scn_101", 1L, ScenarioMatch.anyRequest().naming(SETTLE), aDecline("101"), null),
            rule("scn_102", 2L, ScenarioMatch.anyRequest().naming(LOOKUP), aDecline("102"), null),
            split);

    assertThat(said)
        .isEqualTo(
            "provider grow declines with "
                + A_NUMERIC_ERROR_ID
                + "; operation "
                + SETTLE
                + " with a code from 100; operation "
                + LOOKUP
                + " with "
                + A_NUMERIC_ERROR_ID);
    assertThat(
            ScenarioComparison.theCodesItDeclinesWith(
                rule(
                    "scn_101",
                    1L,
                    ScenarioMatch.anyRequest().naming(SETTLE),
                    aDecline("101"),
                    null),
                rule("scn_102", 2L, ScenarioMatch.anyRequest(), aDecline("102"), null),
                GROW_VOCABULARY))
        .isEqualTo("provider grow declines with " + A_NUMERIC_ERROR_ID);
  }

  @Test
  void a_scheduled_outcome_names_the_operations_that_provider_answers_a_processing_state_on() {
    ScenarioRule settling =
        rule(
            "scn_settling",
            1L,
            ScenarioMatch.anyRequest().naming(LOOKUP),
            new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null),
            null);
    ScenarioRule answered =
        rule("scn_answered", 2L, ScenarioMatch.anyRequest().naming(LOOKUP), aSuccess(), null);

    ScenarioComparison.Difference onAProviderThatReportsOne =
        theDifferenceOf(
            comparedBy(settling, answered, A_VOCABULARY_THAT_REPORTS_A_PROCESSING_STATE),
            "action.type");
    ScenarioComparison.Difference onAProviderThatReportsNone =
        theDifferenceOf(comparedBy(settling, answered, GROW_VOCABULARY), "action.type");

    assertThat(onAProviderThatReportsOne.inTheProvidersWords())
        .isEqualTo(
            "provider grow answers with a processing state on "
                + LOOKUP
                + " and on no other operation of this contract");
    assertThat(
            theDifferenceOf(
                    comparedBy(settling, answered, A_VOCABULARY_THAT_REPORTS_A_PROCESSING_STATE),
                    ScenarioComparison.SETTLES_AFTER)
                .inTheProvidersWords())
        .isEqualTo(onAProviderThatReportsOne.inTheProvidersWords());
    assertThat(onAProviderThatReportsNone.inTheProvidersWords()).isNull();
  }

  @Test
  void a_difference_between_two_actions_that_schedule_nothing_names_no_processing_state_at_all() {
    ScenarioComparison.Comparison.Compared compared =
        comparedBy(
            rule(
                "scn_held",
                1L,
                ScenarioMatch.anyRequest().reporting("payment_process.completed"),
                new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5)),
                null),
            rule(
                "scn_dropped",
                2L,
                ScenarioMatch.anyRequest().reporting("payment_process.completed"),
                new ScenarioAction.DeliveryDropped(),
                null),
            A_VOCABULARY_THAT_REPORTS_A_PROCESSING_STATE);

    assertThat(theDifferenceOf(compared, ScenarioComparison.ACTION_TYPE).inTheProvidersWords())
        .isNull();
    assertThat(theDifferenceOf(compared, "action.delayMs").inTheProvidersWords()).isNull();
  }

  @Test
  void a_field_no_provider_vocabulary_describes_carries_no_provider_words_at_all() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_left", 1L, narrowedTo("merchant-1", "10.00"), aSuccess(), null),
            rule("scn_right", 2L, narrowedTo("merchant-2", "10.00"), aSuccess(), null));

    assertThat(theOnly(compared).property()).isEqualTo("match.merchantId");
    assertThat(theOnly(compared).inTheProvidersWords()).isNull();
  }

  @Test
  void two_rules_of_different_providers_are_refused_rather_than_compared_and_both_are_named() {
    ScenarioComparison.Comparison comparison =
        ScenarioComparison.of(
            rule("scn_grow", 1L, ScenarioMatch.anyRequest(), aSuccess(), null),
            new ScenarioRule(
                "scn_azericard",
                AZERICARD,
                RUN,
                0,
                2L,
                ScenarioMatch.anyRequest(),
                aSuccess(),
                null,
                AT),
            GROW_VOCABULARY);

    assertThat(comparison)
        .isInstanceOfSatisfying(
            ScenarioComparison.Comparison.OfTwoProviders.class,
            refused -> {
              assertThat(refused.onTheLeft()).isEqualTo("grow");
              assertThat(refused.onTheRight()).isEqualTo("azericard");
              assertThat(refused.reason())
                  .isEqualTo(ScenarioComparison.A_RULE_OF_ONE_PROVIDER_MEANS_NOTHING_TO_ANOTHER);
            });
  }

  @Test
  void two_rules_of_one_provider_in_different_test_runs_are_compared_and_each_run_is_named() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_here", 1L, ScenarioMatch.anyRequest(), aSuccess(), null),
            new ScenarioRule(
                "scn_there",
                GROW,
                ANOTHER_RUN,
                0,
                2L,
                ScenarioMatch.anyRequest(),
                aSuccess(),
                null,
                AT));

    assertThat(compared.onTheLeft().testRunId()).isEqualTo(RUN.value());
    assertThat(compared.onTheRight().testRunId()).isEqualTo(ANOTHER_RUN.value());
    assertThat(compared.differences()).isEmpty();
  }

  @Test
  void the_priority_and_the_order_the_store_gave_each_rule_are_carried_beside_the_comparison() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            new ScenarioRule(
                "scn_urgent",
                GROW,
                RUN,
                100,
                7L,
                narrowedTo("merchant-1", null),
                aSuccess(),
                null,
                AT),
            rule("scn_ordinary", 9L, ScenarioMatch.anyRequest().naming(SETTLE), aSuccess(), null));

    assertThat(compared.onTheLeft().priority()).isEqualTo(100);
    assertThat(compared.onTheLeft().specificity()).isEqualTo(2);
    assertThat(compared.onTheLeft().arrival()).isEqualTo(7L);
    assertThat(compared.onTheRight().specificity()).isEqualTo(1);
    assertThat(compared.onTheRight().arrival()).isEqualTo(9L);
    assertThat(compared.differences())
        .extracting(ScenarioComparison.Difference::property)
        .containsExactly(ScenarioComparison.PRIORITY, "match.merchantId");
  }

  @Test
  void every_comparison_names_the_dimensions_no_field_of_a_scenario_holds() {
    ScenarioComparison.Comparison.Compared compared =
        compared(
            rule("scn_left", 1L, ScenarioMatch.anyRequest(), aSuccess(), null),
            rule("scn_right", 2L, ScenarioMatch.anyRequest(), aSuccess(), null));

    assertThat(compared.theDimensionsItCannotCompare())
        .isNotEmpty()
        .extracting(ScenarioComparison.NotHeldByAScenario::dimension)
        .contains(
            "the state a payment comes to rest in",
            "the webhook plan",
            "what a damaged answer puts on the wire",
            "the state a story stands in right now");
    assertThat(compared.theDimensionsItCannotCompare())
        .allSatisfy(absent -> assertThat(absent.reason()).isNotBlank());
  }

  @Test
  void a_side_that_names_nothing_carries_no_value_and_withholds_none() {
    assertThatThrownBy(() -> new ScenarioComparison.Named(false, true, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not name a property");
    assertThatThrownBy(() -> new ScenarioComparison.Named(false, false, "10.00"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not name a property");
  }

  @Test
  void a_withheld_value_does_not_travel_beside_the_marker_that_withholds_it() {
    assertThatThrownBy(() -> new ScenarioComparison.Named(true, true, "a-secret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("withheld from this comparison");
  }

  @Test
  void a_side_that_names_a_property_carries_the_value_it_names_it_with() {
    assertThatThrownBy(() -> new ScenarioComparison.Named(true, false, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("carries the value it names it with");
  }

  private static ScenarioComparison.Difference theOnly(
      ScenarioComparison.Comparison.Compared compared) {
    assertThat(compared.differences()).hasSize(1);
    return compared.differences().getFirst();
  }

  private static ScenarioComparison.Difference theDifferenceOf(
      ScenarioComparison.Comparison.Compared compared, String property) {
    for (ScenarioComparison.Difference difference : compared.differences()) {
      if (difference.property().equals(property)) {
        return difference;
      }
    }
    throw new AssertionError("no difference was reported for " + property);
  }

  private static ScenarioComparison.Comparison.Compared compared(
      ScenarioRule onTheLeft, ScenarioRule onTheRight) {
    return comparedBy(onTheLeft, onTheRight, GROW_VOCABULARY);
  }

  private static ScenarioComparison.Comparison.Compared comparedBy(
      ScenarioRule onTheLeft, ScenarioRule onTheRight, ProviderVocabulary vocabulary) {
    ScenarioComparison.Comparison comparison =
        ScenarioComparison.of(onTheLeft, onTheRight, vocabulary);
    assertThat(comparison).isInstanceOf(ScenarioComparison.Comparison.Compared.class);
    return (ScenarioComparison.Comparison.Compared) comparison;
  }

  private static ScenarioRule rule(
      String scenarioId,
      long arrival,
      ScenarioMatch match,
      ScenarioAction action,
      @Nullable ScenarioStory story) {
    return new ScenarioRule(scenarioId, GROW, RUN, 0, arrival, match, action, story, AT);
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
        SETTLE,
        null,
        Map.of());
  }

  private static ScenarioMatch narrowedByHeader(String value) {
    return new ScenarioMatch(
        null, null, null, null, null, null, null, SETTLE, null, Map.of(SIGNATURE_HEADER, value));
  }

  private static ScenarioAction aSuccess() {
    return new ScenarioAction.FixedSuccess();
  }

  private static ScenarioAction aDecline(String providerCode) {
    return new ScenarioAction.TerminalFailure(providerCode);
  }

  private static ScenarioStory aStep() {
    return new ScenarioStory(
        "a settlement that declines once", StoryKeying.FLOW, "Started", "Tried");
  }
}
