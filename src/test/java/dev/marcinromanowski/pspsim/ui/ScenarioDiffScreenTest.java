package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
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
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioComparison;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioCopy;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ScenarioDiffScreenTest {

  private static final Instant AT = Instant.parse("2026-09-25T09:00:00Z");
  private static final TestRunId RUN = TestRunId.of("ui-scenario-diff");
  private static final ProviderId GROW = ProviderId.of("grow");
  private static final String SETTLE = "settleSuspendedTransaction";
  private static final String LOOKUP = "getTransactionInfo";
  private static final String SIGNATURE_HEADER = "X-Signature";

  private static final ProviderVocabulary VOCABULARY =
      new ProviderVocabulary(
          GROW,
          List.of(
              new ProviderOperation(
                  SETTLE,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.AMOUNT),
                  "the payment process the call belongs to",
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  LOOKUP,
                  Set.of(MatchFact.MERCHANT_ID),
                  "the payment process the call belongs to",
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
          Map.of(MatchFact.CURRENCY, "provider grow publishes no currency parameter"),
          new FailureCodes("[0-9]+", "a numeric err.id"),
          ProcessingStates.noneOfThem("provider grow publishes no processing status"));

  @Test
  void the_rules_to_choose_from_stand_in_the_order_the_engine_consults_them() {
    ScenarioDiffScreen.View view =
        ScenarioDiffScreen.of(
            "grow",
            RUN.value(),
            List.of(
                rule("scn_ordinary", 0, 1L, ScenarioMatch.anyRequest(), aSuccess(), null),
                rule("scn_urgent", 100, 2L, ScenarioMatch.anyRequest(), aSuccess(), null)),
            List.of(),
            ScenarioDiffScreen.Asked.nothing(),
            null,
            null,
            null);

    assertThat(view.choices())
        .extracting(ScenarioDiffScreen.Choice::scenarioId)
        .containsExactly("scn_urgent", "scn_ordinary");
  }

  @Test
  void each_rule_to_choose_from_is_named_by_what_it_does_and_not_only_by_its_id() {
    ScenarioDiffScreen.View view =
        ScenarioDiffScreen.of(
            "grow",
            RUN.value(),
            List.of(
                rule(
                    "scn_step",
                    0,
                    1L,
                    ScenarioMatch.anyRequest().naming(SETTLE),
                    new ScenarioAction.TerminalFailure("101"),
                    aStep())),
            List.of(),
            ScenarioDiffScreen.Asked.nothing(),
            null,
            null,
            null);

    ScenarioDiffScreen.Choice only = view.choices().getFirst();
    assertThat(only.operation()).isEqualTo(SETTLE);
    assertThat(only.action()).isEqualTo(ScenarioGrammar.TERMINAL_FAILURE);
    assertThat(only.story()).isEqualTo("a settlement that declines once");
  }

  @Test
  void the_rules_offered_on_the_right_are_the_rules_of_the_test_run_the_right_hand_side_names() {
    ScenarioRule onlyInThisRun =
        rule("scn_only_here", 0, 1L, ScenarioMatch.anyRequest(), aSuccess(), null);
    ScenarioRule ofTheRunOnTheRight =
        rule("scn_over_there", 0, 1L, ScenarioMatch.anyRequest(), aSuccess(), null);

    ScenarioDiffScreen.View view =
        ScenarioDiffScreen.of(
            "grow",
            RUN.value(),
            List.of(onlyInThisRun),
            List.of(ofTheRunOnTheRight),
            new ScenarioDiffScreen.Asked(null, null, null, "ui-scenario-diff-elsewhere"),
            null,
            null,
            null);

    assertThat(view.choicesOnTheRight())
        .extracting(ScenarioDiffScreen.Choice::scenarioId)
        .containsExactly("scn_over_there")
        .doesNotContain("scn_only_here");
    assertThat(view.choices())
        .extracting(ScenarioDiffScreen.Choice::scenarioId)
        .containsExactly("scn_only_here");
  }

  @Test
  void two_rules_that_differ_in_nothing_and_withhold_nothing_differ_in_no_field_a_scenario_holds() {
    ScenarioDiffScreen.View view =
        compared(
            rule("scn_left", 0, 1L, narrowedTo("merchant-1", "10.00"), aSuccess(), null),
            rule("scn_right", 0, 2L, narrowedTo("merchant-1", "10.00"), aSuccess(), null));

    assertThat(view.differences()).isEmpty();
    assertThat(view.whenNothingDiffers())
        .isEqualTo(ScenarioDiffScreen.THEY_DIFFER_IN_NO_FIELD_A_SCENARIO_HOLDS)
        .contains("same thing in every field a scenario holds");
    assertThat(view.whatAPropertySaidAlikeOnItsNameDoesNotCompare()).isNull();
  }

  @Test
  void two_rules_that_differ_in_nothing_but_a_withheld_header_value_do_not_claim_every_field() {
    ScenarioDiffScreen.View view =
        compared(
            rule("scn_left", 0, 1L, narrowedByAHeader("one-secret"), aSuccess(), null),
            rule("scn_right", 0, 2L, narrowedByAHeader("another-secret"), aSuccess(), null));

    assertThat(view.differences()).isEmpty();
    assertThat(view.whenNothingDiffers())
        .isEqualTo(ScenarioDiffScreen.THEY_DIFFER_IN_NO_FIELD_THIS_COMPARISON_READS)
        .doesNotContain("same thing in every field a scenario holds")
        .contains("said alike on its name alone")
        .contains("match a request the other refuses");
    assertThat(view.thePropertiesSaidAlikeOnTheirNameAlone())
        .containsExactly(ScenarioComparison.HEADER + SIGNATURE_HEADER);
    assertThat(view.whatAPropertySaidAlikeOnItsNameDoesNotCompare())
        .isEqualTo(ScenarioComparison.A_HEADER_SAID_ALIKE_IS_ALIKE_IN_ITS_NAME_ALONE);
  }

  @Test
  void a_comparison_with_a_difference_says_nothing_about_two_rules_that_differ_in_nothing() {
    ScenarioDiffScreen.View view =
        compared(
            rule("scn_left", 0, 1L, narrowedTo("merchant-1", "10.00"), aSuccess(), null),
            rule("scn_right", 0, 2L, narrowedTo("merchant-1", "25.50"), aSuccess(), null));

    assertThat(view.differences()).isNotEmpty();
    assertThat(view.whenNothingDiffers()).isNull();
  }

  @Test
  void a_screen_that_was_asked_to_compare_nothing_compares_nothing_and_shows_no_side() {
    ScenarioDiffScreen.View view =
        ScenarioDiffScreen.of(
            "grow",
            RUN.value(),
            List.of(rule("scn_one", 0, 1L, ScenarioMatch.anyRequest(), aSuccess(), null)),
            List.of(),
            ScenarioDiffScreen.Asked.nothing(),
            null,
            null,
            null);

    assertThat(view.compared()).isFalse();
    assertThat(view.onTheLeft()).isNull();
    assertThat(view.onTheRight()).isNull();
    assertThat(view.differences()).isEmpty();
    assertThat(view.theDimensionsItCannotCompare()).isEmpty();
    assertThat(view.copyOfTheLeft()).isNull();
  }

  @Test
  void a_compared_screen_carries_both_sides_the_differences_and_what_it_cannot_compare() {
    ScenarioRule onTheLeft =
        rule("scn_left", 0, 1L, narrowedTo("merchant-1", "10.00"), aSuccess(), null);
    ScenarioRule onTheRight =
        rule("scn_right", 0, 2L, narrowedTo("merchant-1", "25.50"), aSuccess(), null);

    ScenarioDiffScreen.View view = compared(onTheLeft, onTheRight);

    assertThat(view.compared()).isTrue();
    assertThat(view.left()).isEqualTo("scn_left");
    assertThat(view.right()).isEqualTo("scn_right");
    assertThat(view.differences())
        .extracting(ScenarioComparison.Difference::property)
        .containsExactly("match.amount");
    assertThat(view.thePropertiesBothSidesSayAlike()).contains("match.merchantId");
    assertThat(view.theDimensionsItCannotCompare()).isNotEmpty();
    assertThat(view.theHeaderValueItWithholds())
        .isEqualTo(ScenarioComparison.THE_HEADER_VALUE_NO_COMPARISON_SHOWS);
    assertThat(view.aCopyIsANewRule()).isEqualTo(ScenarioCopy.A_COPY_IS_A_NEW_RULE);
  }

  @Test
  void the_copy_form_of_a_side_holds_every_field_the_rule_it_copies_was_written_with() {
    ScenarioRule written =
        new ScenarioRule(
            "scn_left",
            GROW,
            RUN,
            5,
            1L,
            new ScenarioMatch(
                "merchant-1",
                "order-1",
                "trx_1",
                "customer-1",
                "pm_1",
                Amount.of("10.00"),
                "ILS",
                SETTLE,
                null,
                Map.of()),
            new ScenarioAction.TerminalFailure("101"),
            aStep(),
            AT);

    ScenarioForm form = theCopyOf(written);

    assertThat(form.priority()).isEqualTo("5");
    assertThat(form.operation()).isEqualTo(SETTLE);
    assertThat(form.merchantId()).isEqualTo("merchant-1");
    assertThat(form.orderId()).isEqualTo("order-1");
    assertThat(form.providerObjectId()).isEqualTo("trx_1");
    assertThat(form.customerId()).isEqualTo("customer-1");
    assertThat(form.paymentMethodId()).isEqualTo("pm_1");
    assertThat(form.amount()).isEqualTo("10.00");
    assertThat(form.currency()).isEqualTo("ILS");
    assertThat(form.story()).isEqualTo("a settlement that declines once");
    assertThat(form.keyedOn()).isEqualTo(StoryKeying.FLOW.name());
    assertThat(form.whenState()).isEqualTo("Started");
    assertThat(form.thenState()).isEqualTo("Tried");
    assertThat(form.actionType()).isEqualTo(ScenarioGrammar.TERMINAL_FAILURE);
    assertThat(form.providerCode()).isEqualTo("101");
  }

  @Test
  void a_copy_form_holds_the_fault_and_the_timing_the_rule_it_copies_was_written_with() {
    ScenarioForm held =
        theCopyOf(
            rule(
                "scn_held",
                0,
                1L,
                ScenarioMatch.anyRequest().reporting("payment_process.completed"),
                new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5)),
                null));
    ScenarioForm answered =
        theCopyOf(
            rule(
                "scn_502",
                0,
                1L,
                ScenarioMatch.anyRequest().naming(SETTLE),
                new ScenarioAction.TransportFaultAlways(new TransportFault.ServerError(502)),
                null));
    ScenarioForm settling =
        theCopyOf(
            rule(
                "scn_settling",
                0,
                1L,
                ScenarioMatch.anyRequest().naming(LOOKUP),
                new ScenarioAction.ProcessingThenTerminal(Duration.ofSeconds(5), null),
                null));

    assertThat(held.delayMs()).isEqualTo("5000");
    assertThat(held.eventType()).isEqualTo("payment_process.completed");
    assertThat(answered.fault()).isEqualTo("SERVER_ERROR");
    assertThat(answered.httpStatus()).isEqualTo("502");
    assertThat(settling.settlesAfterMs()).isEqualTo("5000");
    assertThat(settling.delayMs()).isNull();
  }

  @Test
  void a_copy_form_never_carries_a_header_narrowing_and_says_why_it_offers_none() {
    ScenarioRule signed =
        rule(
            "scn_signed",
            0,
            1L,
            new ScenarioMatch(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                SETTLE,
                null,
                Map.of(SIGNATURE_HEADER, "a-secret-a-tester-typed")),
            aSuccess(),
            null);

    assertThat(ScenarioDiffScreen.theCopyItOffers(signed)).isNull();
    assertThat(ScenarioDiffScreen.whyItOffersNoCopy(signed))
        .isEqualTo(ScenarioDiffScreen.A_HEADER_NARROWING_IS_NOT_COPIED_ON_A_SCREEN);
  }

  @Test
  void a_rule_that_narrows_on_no_header_is_offered_as_a_copy_with_no_reason_withheld() {
    ScenarioRule plain =
        rule("scn_plain", 0, 1L, ScenarioMatch.anyRequest().naming(SETTLE), aSuccess(), null);

    assertThat(ScenarioDiffScreen.theCopyItOffers(plain)).isNotNull();
    assertThat(ScenarioDiffScreen.whyItOffersNoCopy(plain)).isNull();
  }

  @Test
  void a_side_that_was_never_read_is_offered_neither_as_a_copy_nor_as_a_reason() {
    assertThat(ScenarioDiffScreen.theCopyItOffers(null)).isNull();
    assertThat(ScenarioDiffScreen.whyItOffersNoCopy(null)).isNull();
  }

  @Test
  void a_rule_this_run_no_longer_holds_is_refused_by_the_side_that_named_it() {
    String refusal =
        ScenarioDiffScreen.theRunHoldsNoRuleNamedOnThe(
            ScenarioDiffScreen.THE_RIGHT, "scn_gone", "grow", RUN.value());

    assertThat(refusal)
        .contains("on the right")
        .contains("scn_gone")
        .contains("provider grow")
        .contains(RUN.value())
        .contains("Nothing was compared");
  }

  @Test
  void a_copy_the_screen_could_not_read_and_a_rule_it_cannot_copy_refuse_under_the_same_subject() {
    assertThat(ScenarioDiffScreen.theRefusalOfACopyItCouldNotRead())
        .isEqualTo(
            ActionVerdict.refused(
                RefusalSubject.noObjectWasNamed(
                    ScenarioCopy.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_COPY)));
    assertThat(ScenarioDiffScreen.theRefusalOfARuleItCannotCopy())
        .isEqualTo(
            ActionVerdict.notFound(
                RefusalSubject.noObjectWasNamed(
                    ScenarioCopy.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_COPY)));
  }

  private static ScenarioForm theCopyOf(ScenarioRule rule) {
    ScenarioForm form = ScenarioDiffScreen.theCopyItOffers(rule);
    assertThat(form).isNotNull();
    return form;
  }

  private static ScenarioDiffScreen.View compared(ScenarioRule onTheLeft, ScenarioRule onTheRight) {
    ScenarioComparison.Comparison comparison =
        ScenarioComparison.of(onTheLeft, onTheRight, VOCABULARY);
    assertThat(comparison).isInstanceOf(ScenarioComparison.Comparison.Compared.class);
    return ScenarioDiffScreen.of(
        "grow",
        RUN.value(),
        List.of(onTheLeft, onTheRight),
        List.of(onTheLeft, onTheRight),
        new ScenarioDiffScreen.Asked(onTheLeft.scenarioId(), onTheRight.scenarioId(), null, null),
        (ScenarioComparison.Comparison.Compared) comparison,
        onTheLeft,
        onTheRight);
  }

  private static ScenarioRule rule(
      String scenarioId,
      int priority,
      long arrival,
      ScenarioMatch match,
      ScenarioAction action,
      @Nullable ScenarioStory story) {
    return new ScenarioRule(scenarioId, GROW, RUN, priority, arrival, match, action, story, AT);
  }

  private static ScenarioMatch narrowedByAHeader(String value) {
    return new ScenarioMatch(
        null, null, null, null, null, null, null, SETTLE, null, Map.of(SIGNATURE_HEADER, value));
  }

  private static ScenarioMatch narrowedTo(String merchantId, String amount) {
    return new ScenarioMatch(
        merchantId, null, null, null, null, Amount.of(amount), null, SETTLE, null, Map.of());
  }

  private static ScenarioAction aSuccess() {
    return new ScenarioAction.FixedSuccess();
  }

  private static ScenarioStory aStep() {
    return new ScenarioStory(
        "a settlement that declines once", StoryKeying.FLOW, "Started", "Tried");
  }
}
