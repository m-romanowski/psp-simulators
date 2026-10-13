package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OriginationFormTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final TestRunId RUN = TestRunId.of("origination-form");

  private static final String A_HOLD = "aHold";

  private static final String A_SAVED_CARD = "aSavedCard";

  private static final String CONFIGURED_TARGET = "https://merchant.example/callbacks";

  private static final String LEAVES_A_HOLD = "a hold the Payments screen lists";

  private static final String LEAVES_A_CARD = "a saved card and no payment at all";

  private static final String LEAVES_A_CHARGE = "money taken off a card this run already saved";

  private static final String A_SAVED_CARD_CHARGE = "aSavedCardCharge";

  private static final String THE_PAGE_IS_SERVED_AT = "/aprovider/payer/{session}";

  private static final ThePayerStep THE_PAGE_A_PAYER_FINISHES_IT_ON =
      new ThePayerStep.APageThisSimulatorServes(THE_PAGE_IS_SERVED_AT);

  private static final AFieldATesterFillsIn SUM =
      AFieldATesterFillsIn.typed("sum", "amount", true, "what goes on the card.", "10.00");

  private static final AFieldATesterFillsIn SAVE_CARD =
      AFieldATesterFillsIn.chosenFrom(
          "saveCardToken",
          "keep the payer's card",
          false,
          "whether the card is kept.",
          List.of(new AValueAFieldOffers("1", "yes, keep it"), new AValueAFieldOffers("0", "no")));

  private static final AFieldATesterFillsIn NOTIFY_URL =
      AFieldATesterFillsIn.theCallbackTarget(
          "notifyUrl", "where the callback goes", "where the callback is posted.");

  private static final AFieldATesterFillsIn SAVED_CARD_TO_CHARGE =
      AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
          "cardToken",
          "the saved card to charge",
          true,
          "the card one of this run's own payments saved.");

  private static final AValueAFieldOffers THE_CARD_THIS_RUN_SAVED =
      new AValueAFieldOffers("tok_1", "the card Dana Levi saved on obj_1");

  private static final ProviderOriginationFields DECLARED =
      new ProviderOriginationFields(
          APROVIDER,
          List.of(
              new DeclaredOrigination(
                  A_HOLD,
                  "Hold the money and settle it later",
                  LEAVES_A_HOLD,
                  THE_PAGE_A_PAYER_FINISHES_IT_ON,
                  List.of(SUM, SAVE_CARD, NOTIFY_URL)),
              new DeclaredOrigination(
                  A_SAVED_CARD,
                  "Save the card and take nothing",
                  LEAVES_A_CARD,
                  THE_PAGE_A_PAYER_FINISHES_IT_ON,
                  List.of(SUM))),
          new LinkedHashMap<>(
              Map.of("userId", "taken from the account this payment is started on")));

  private static final ProviderOriginationFields A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED =
      new ProviderOriginationFields(
          APROVIDER,
          List.of(
              new DeclaredOrigination(
                  A_SAVED_CARD_CHARGE,
                  "Charge a card this run already saved",
                  LEAVES_A_CHARGE,
                  new ThePayerStep.NoPageThisSimulatorServes("no payer is part of this call."),
                  List.of(SAVED_CARD_TO_CHARGE, SUM))),
          new LinkedHashMap<>());

  @Test
  void a_tester_who_has_picked_no_kind_of_payment_is_asked_that_and_nothing_else() {
    OriginationForm.View step = drawnFor(null, null, List.of("merchant-1"), List.of());

    assertThat(step.panel()).isEqualTo(OriginationForm.Panel.WHICH_KIND_OF_PAYMENT);
    assertThat(step.chosenWay()).isNull();
    assertThat(step.fields()).isEmpty();
    assertThat(step.ways())
        .extracting(OriginationForm.Way::whatItLeavesBehind)
        .containsExactly(LEAVES_A_HOLD, LEAVES_A_CARD);
    assertThat(step.ways()).noneMatch(OriginationForm.Way::chosen);
  }

  @Test
  void every_choice_says_where_a_payer_finishes_it_in_the_words_of_the_step_the_way_declares() {
    OriginationForm.View step = drawnFor(null, null, List.of("merchant-1"), List.of());

    assertThat(step.ways())
        .extracting(OriginationForm.Way::whereThePayerFinishesIt)
        .allSatisfy(said -> assertThat(said).contains(THE_PAGE_IS_SERVED_AT))
        .allSatisfy(
            said -> assertThat(said).isEqualTo(THE_PAGE_A_PAYER_FINISHES_IT_ON.asATesterReadsIt()));
  }

  @Test
  void a_kind_of_payment_this_provider_never_declared_leaves_the_tester_at_the_first_step() {
    OriginationForm.View step = drawnFor(null, "aRefund", List.of("merchant-1"), List.of());

    assertThat(step.panel()).isEqualTo(OriginationForm.Panel.WHICH_KIND_OF_PAYMENT);
    assertThat(step.fields()).isEmpty();
  }

  @Test
  void the_kind_a_tester_picked_is_the_one_marked_and_the_only_one_whose_fields_are_drawn() {
    OriginationForm.View step =
        drawnFor("merchant-1", A_SAVED_CARD, List.of("merchant-1"), List.of());

    assertThat(step.panel()).isEqualTo(OriginationForm.Panel.WHAT_THIS_PAYMENT_SAYS);
    assertThat(step.chosenWay())
        .isEqualTo(
            new OriginationForm.Way(
                A_SAVED_CARD,
                "Save the card and take nothing",
                LEAVES_A_CARD,
                THE_PAGE_A_PAYER_FINISHES_IT_ON.asATesterReadsIt(),
                true));
    assertThat(step.fields()).extracting(TheFieldsAFormDraws.Field::key).containsExactly("sum");
    assertThat(step.ways())
        .filteredOn(OriginationForm.Way::chosen)
        .extracting(OriginationForm.Way::name)
        .containsExactly(A_SAVED_CARD);
  }

  @Test
  void a_field_whose_values_a_provider_publishes_is_a_box_those_values_are_suggested_beside() {
    OriginationForm.View step = drawnFor("merchant-1", A_HOLD, List.of("merchant-1"), List.of());

    assertThat(theField(step, "sum").isTypedIn()).isTrue();
    assertThat(theField(step, "sum").example()).isEqualTo("10.00");
    assertThat(theField(step, "sum").itSuggestsTheOffersBesideItWithoutImposingThem())
        .as("a field this provider declares no values for has none to suggest")
        .isFalse();
    assertThat(theField(step, "saveCardToken").isTypedIn())
        .as(
            "a value this provider does not publish is refused by this provider in its own words,"
                + " and a list a tester can only pick from would leave that refusal on no screen")
        .isTrue();
    assertThat(theField(step, "saveCardToken").itSuggestsTheOffersBesideItWithoutImposingThem())
        .isTrue();
    assertThat(theField(step, "saveCardToken").choices())
        .containsExactly(
            new TheFieldsAFormDraws.Choice("1", "yes, keep it", false),
            new TheFieldsAFormDraws.Choice("0", "no", false));
    assertThat(theField(step, "saveCardToken").whyTheseAreSuggestedAndTheBoxStillTakesAnother())
        .as(
            "the sentence beside a provider's own vocabulary says the provider published these"
                + " values, which is what tells a tester the box takes another one")
        .isEqualTo(
            TheFieldsAFormDraws.THE_VALUES_THIS_PROVIDER_PUBLISHES_ARE_OFFERED_AND_NONE_IS_IMPOSED);
    assertThat(theField(step, "saveCardToken").theSuggestionsOnIt())
        .isEqualTo(
            "saveCardToken"
                + TheFieldsAFormDraws.THE_SUGGESTIONS_OF_A_VOCABULARY_A_PROVIDER_PUBLISHES);
  }

  @Test
  void the_callback_target_field_offers_the_targets_this_run_configures_and_none_of_its_own() {
    OriginationForm.View step =
        drawnFor("merchant-1", A_HOLD, List.of("merchant-1"), List.of(CONFIGURED_TARGET));

    assertThat(theField(step, "notifyUrl").isTypedIn()).isFalse();
    assertThat(theField(step, "notifyUrl").itSuggestsTheOffersBesideItWithoutImposingThem())
        .as(
            "the addresses a run configures are this simulator's own facts, and this simulator is"
                + " the party that would post to one, so it is the party that refuses another"
                + " and the only control that may refuse what was typed is this one")
        .isFalse();
    assertThat(theField(step, "notifyUrl").thisSimulatorIsTheOneThatConsumesTheValue()).isTrue();
    assertThat(theField(step, "notifyUrl").choices())
        .containsExactly(
            new TheFieldsAFormDraws.Choice(CONFIGURED_TARGET, CONFIGURED_TARGET, false));
    assertThat(step.noCallbackTargetIsConfigured()).isFalse();
  }

  @Test
  void a_run_that_configures_no_callback_target_offers_none_and_says_it_has_none() {
    OriginationForm.View step = drawnFor("merchant-1", A_HOLD, List.of("merchant-1"), List.of());

    assertThat(theField(step, "notifyUrl").choices()).isEmpty();
    assertThat(step.noCallbackTargetIsConfigured()).isTrue();
    assertThat(step.whatToDoWithNoCallbackTarget())
        .isEqualTo(OriginationForm.NO_CALLBACK_TARGET_IS_CONFIGURED);
  }

  @Test
  void a_refused_payment_comes_back_with_every_value_the_tester_typed_still_in_its_field() {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("sum", "10.00");
    typed.put("saveCardToken", "1");
    typed.put("notifyUrl", CONFIGURED_TARGET);

    OriginationForm.View step =
        OriginationForm.of(
            "aprovider",
            RUN.value(),
            "merchant-1",
            A_HOLD,
            DECLARED,
            List.of("merchant-1"),
            List.of(CONFIGURED_TARGET),
            Map.of(),
            typed,
            null);

    assertThat(theField(step, "sum").value()).isEqualTo("10.00");
    assertThat(theField(step, "saveCardToken").choices())
        .contains(new TheFieldsAFormDraws.Choice("1", "yes, keep it", true));
    assertThat(theField(step, "notifyUrl").choices())
        .containsExactly(
            new TheFieldsAFormDraws.Choice(CONFIGURED_TARGET, CONFIGURED_TARGET, true));
  }

  @Test
  void a_run_that_configures_no_account_is_told_so_and_never_shown_a_kind_to_pick() {
    OriginationForm.View step = drawnFor(null, null, List.of(), List.of());

    assertThat(step.panel()).isEqualTo(OriginationForm.Panel.NO_ACCOUNT_IS_CONFIGURED);
    assertThat(step.whatToDoWithNoAccount())
        .isEqualTo(OriginationForm.THE_RUN_CONFIGURES_NO_ACCOUNT);
    assertThat(step.accountId()).isEmpty();
  }

  @Test
  void a_run_that_configures_no_account_is_told_so_even_when_a_kind_of_payment_was_named() {
    OriginationForm.View step = drawnFor("merchant-1", A_HOLD, List.of(), List.of());

    assertThat(step.panel()).isEqualTo(OriginationForm.Panel.NO_ACCOUNT_IS_CONFIGURED);
    assertThat(step.chosenWay()).isNotNull();
  }

  @Test
  void a_payment_that_exists_is_reported_as_existing_even_when_the_run_configures_no_account() {
    OriginationForm.View step =
        OriginationForm.of(
            "aprovider",
            RUN.value(),
            "merchant-1",
            A_HOLD,
            DECLARED,
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            "obj_1");

    assertThat(step.panel()).isEqualTo(OriginationForm.Panel.THE_PAYMENT_EXISTS_NOW);
  }

  @Test
  void a_payment_that_exists_is_reported_as_existing_rather_than_as_a_kind_still_to_pick() {
    OriginationForm.View step =
        OriginationForm.of(
            "aprovider",
            RUN.value(),
            "merchant-1",
            "",
            DECLARED,
            List.of("merchant-1"),
            List.of(),
            Map.of(),
            Map.of(),
            "obj_1");

    assertThat(step.panel()).isEqualTo(OriginationForm.Panel.THE_PAYMENT_EXISTS_NOW);
  }

  @Test
  void the_wizard_stands_on_one_of_four_panels_and_each_state_it_can_be_in_names_a_different_one() {
    assertThat(OriginationForm.Panel.values())
        .containsExactly(
            OriginationForm.Panel.THE_PAYMENT_EXISTS_NOW,
            OriginationForm.Panel.NO_ACCOUNT_IS_CONFIGURED,
            OriginationForm.Panel.WHICH_KIND_OF_PAYMENT,
            OriginationForm.Panel.WHAT_THIS_PAYMENT_SAYS);
    assertThat(
            List.of(
                started().panel(),
                drawnFor(null, null, List.of(), List.of()).panel(),
                drawnFor(null, null, List.of("merchant-1"), List.of()).panel(),
                drawnFor("merchant-1", A_HOLD, List.of("merchant-1"), List.of()).panel()))
        .containsExactly(OriginationForm.Panel.values());
  }

  private static OriginationForm.View started() {
    return OriginationForm.of(
        "aprovider",
        RUN.value(),
        "merchant-1",
        A_HOLD,
        DECLARED,
        List.of("merchant-1"),
        List.of(),
        Map.of(),
        Map.of(),
        "obj_1");
  }

  @Test
  void the_fields_the_simulator_fills_in_are_listed_in_the_order_the_provider_declared_them() {
    LinkedHashMap<String, String> declaredInThisOrder = new LinkedHashMap<>();
    for (int at = 1; at <= 7; at++) {
      declaredInThisOrder.put("field.number." + at, "taken from the account, number " + at);
    }
    ProviderOriginationFields filling =
        new ProviderOriginationFields(
            APROVIDER,
            List.of(
                new DeclaredOrigination(
                    A_HOLD,
                    "Hold the money and settle it later",
                    LEAVES_A_HOLD,
                    THE_PAGE_A_PAYER_FINISHES_IT_ON,
                    List.of(SUM))),
            declaredInThisOrder);

    OriginationForm.View step =
        OriginationForm.of(
            "aprovider",
            RUN.value(),
            "merchant-1",
            A_HOLD,
            filling,
            List.of("merchant-1"),
            List.of(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            null);

    assertThat(step.filledIn())
        .extracting(TheFieldsAFormDraws.FilledIn::key)
        .as(
            "the wizard tells a tester which values it sends for them and where each comes from,"
                + " and a tester reads that list against the provider's own documentation, so the"
                + " %s fields are listed in the order the provider declared them rather than in an"
                + " order drawn from the keys' hashes and a per-JVM salt",
            declaredInThisOrder.size())
        .containsExactlyElementsOf(declaredInThisOrder.keySet());
  }

  @Test
  void the_screen_names_every_field_the_simulator_fills_in_and_where_it_takes_it_from() {
    OriginationForm.View step = drawnFor("merchant-1", A_HOLD, List.of("merchant-1"), List.of());

    assertThat(step.filledIn())
        .containsExactly(
            new TheFieldsAFormDraws.FilledIn(
                "userId", "taken from the account this payment is started on"));
    assertThat(step.fields()).extracting(TheFieldsAFormDraws.Field::key).doesNotContain("userId");
  }

  @Test
  void a_payment_that_has_just_started_names_the_id_the_provider_gave_it() {
    OriginationForm.View step =
        OriginationForm.of(
            "aprovider",
            RUN.value(),
            "merchant-1",
            A_HOLD,
            DECLARED,
            List.of("merchant-1"),
            List.of(),
            Map.of(),
            Map.of(),
            "770001");

    assertThat(step.startedObjectId()).isEqualTo("770001");
  }

  @Test
  void every_field_of_a_way_is_drawn_under_the_one_parameter_a_testers_value_arrives_under() {
    OriginationForm.View step = drawnFor("merchant-1", A_HOLD, List.of("merchant-1"), List.of());

    assertThat(step.fields())
        .as(
            "the name a control is drawn under is the one the port reads a value back out of, so"
                + " a box a tester fills in is a box some port parses")
        .isNotEmpty();
    assertThat(step.fields().stream().map(TheFieldsAFormDraws.Field::theParameterItArrivesUnder))
        .containsExactlyElementsOf(
            step.fields().stream()
                .map(field -> TheParameterATestersValueArrivesUnder.onAFormControlFor(field.key()))
                .toList());
  }

  @Test
  void an_account_a_reset_took_away_mid_wizard_is_said_in_the_screens_own_words() {
    assertThat(OriginationForm.refusalOf(new OriginationOutcome.NoSuchAccount()))
        .isEqualTo(OriginationForm.THE_ACCOUNT_IS_GONE);
  }

  @Test
  void a_payment_that_started_is_the_only_outcome_the_screen_shows_no_refusal_for() {
    assertThat(
            OriginationForm.refusalOf(new OriginationOutcome.Originated("770001", LEAVES_A_HOLD)))
        .isNull();
    assertThat(OriginationForm.refusalOf(new OriginationOutcome.RefusedByTheProvider("803")))
        .isEqualTo("803");
    assertThat(OriginationForm.refusalOf(new OriginationOutcome.NotOffered("nothing like it")))
        .isEqualTo("nothing like it");
    assertThat(OriginationForm.refusalOf(new OriginationOutcome.AnsweredByAScenarioInstead("sc_1")))
        .isEqualTo(
            "a scenario answered this call instead of the provider, so no payment was started;"
                + " the rule that decided it is sc_1");
  }

  private static TheFieldsAFormDraws.Field theField(OriginationForm.View step, String key) {
    return step.fields().stream()
        .filter(field -> field.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void a_value_the_provider_reads_is_typed_with_what_this_run_produced_suggested_on_the_box() {
    OriginationForm.View step =
        drawnFor("merchant-1", A_SAVED_CARD_CHARGE, List.of(THE_CARD_THIS_RUN_SAVED));

    TheFieldsAFormDraws.Field token = theField(step, "cardToken");
    assertThat(token.isTypedIn())
        .as(
            "a token is forwarded to the provider, which is the party that resolves it and the"
                + " party that refuses one it never issued, so the control takes any value and a"
                + " list a tester could only pick from would leave that refusal unreachable")
        .isTrue();
    assertThat(token.itSuggestsTheOffersBesideItWithoutImposingThem())
        .as("what this run produced is suggested on the box the same way a vocabulary is")
        .isTrue();
    assertThat(token.whyTheseAreSuggestedAndTheBoxStillTakesAnother())
        .as(
            "the sentence beside these says they are a fact about this run rather than a"
                + " vocabulary a provider published, which the sentence beside a vocabulary says"
                + " the other way round")
        .isEqualTo(
            TheFieldsAFormDraws.THE_VALUES_THIS_RUN_PRODUCED_ARE_SUGGESTED_AND_THE_PROVIDER_JUDGES);
    assertThat(token.theSuggestionsOnIt())
        .as("the suggestions on a box name the source the values came from, so the two never mix")
        .isEqualTo(
            "cardToken" + TheFieldsAFormDraws.THE_SUGGESTIONS_OF_WHAT_THIS_TEST_RUN_HAS_PRODUCED);
    assertThat(token.choices())
        .containsExactly(
            new TheFieldsAFormDraws.Choice(
                THE_CARD_THIS_RUN_SAVED.value(), THE_CARD_THIS_RUN_SAVED.label(), false));
    assertThat(token.thisRunHasProducedNoneOfThese()).isFalse();
  }

  @Test
  void a_run_that_has_produced_nothing_this_field_could_suggest_is_told_so_beside_the_box() {
    OriginationForm.View step = drawnFor("merchant-1", A_SAVED_CARD_CHARGE, List.of());

    TheFieldsAFormDraws.Field token = theField(step, "cardToken");
    assertThat(token.choices()).isEmpty();
    assertThat(token.isTypedIn())
        .as(
            "a run that has produced nothing is the run a tester most needs the box for: the"
                + " token the provider refuses is one no run ever produced")
        .isTrue();
    assertThat(token.itSuggestsTheOffersBesideItWithoutImposingThem())
        .as("a box with nothing to suggest carries no suggestions and still takes a value")
        .isFalse();
    assertThat(token.thisRunHasProducedNoneOfThese()).isTrue();
    assertThat(token.whatHasToHappenBeforeThisRunSuggestsOne())
        .isEqualTo(TheFieldsAFormDraws.THIS_RUN_HAS_PRODUCED_NO_VALUE_THIS_FIELD_COULD_SUGGEST);
  }

  @Test
  void a_field_the_run_produces_no_value_for_is_offered_none_of_the_values_another_field_has() {
    OriginationForm.View step =
        drawnFor("merchant-1", A_SAVED_CARD_CHARGE, List.of(THE_CARD_THIS_RUN_SAVED));

    TheFieldsAFormDraws.Field sum = theField(step, "sum");
    assertThat(sum.isTypedIn()).isTrue();
    assertThat(sum.choices()).isEmpty();
    assertThat(sum.thisRunHasProducedNoneOfThese()).isFalse();
  }

  @Test
  void the_value_a_tester_already_typed_is_the_one_the_box_is_drawn_with_again() {
    OriginationForm.View step =
        OriginationForm.of(
            "aprovider",
            RUN.value(),
            "merchant-1",
            A_SAVED_CARD_CHARGE,
            A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED,
            List.of("merchant-1"),
            List.of(),
            Map.of("cardToken", List.of(THE_CARD_THIS_RUN_SAVED)),
            Map.of("cardToken", "tok_1"),
            null);

    assertThat(theField(step, "cardToken").value())
        .as(
            "what a tester typed is what the box is drawn with again, whether this run produced"
                + " that value or not")
        .isEqualTo("tok_1");
    assertThat(theField(step, "cardToken").choices())
        .containsExactly(
            new TheFieldsAFormDraws.Choice(
                THE_CARD_THIS_RUN_SAVED.value(), THE_CARD_THIS_RUN_SAVED.label(), true));
  }

  private static OriginationForm.View drawnFor(
      String accountId, String startedAs, List<AValueAFieldOffers> theCardsThisRunSaved) {
    return OriginationForm.of(
        "aprovider",
        RUN.value(),
        accountId,
        startedAs,
        A_PROVIDER_THAT_CHARGES_WHAT_IT_SAVED,
        List.of("merchant-1"),
        List.of(),
        theCardsThisRunSaved.isEmpty() ? Map.of() : Map.of("cardToken", theCardsThisRunSaved),
        Map.of(),
        null);
  }

  private static OriginationForm.View drawnFor(
      String accountId, String startedAs, List<String> accounts, List<String> callbackTargets) {
    return OriginationForm.of(
        "aprovider",
        RUN.value(),
        accountId,
        startedAs,
        DECLARED,
        accounts,
        callbackTargets,
        Map.of(),
        Map.of(),
        null);
  }
}
