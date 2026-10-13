package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.RecordedActions;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlError;
import dev.marcinromanowski.pspsim.control.ControlPayloads.DeclaredFieldView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.FilledInFieldView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.OriginationResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.OriginationsResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WayToStartAPaymentView;
import dev.marcinromanowski.pspsim.control.ThePaymentsAProviderStarts.Answer;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationControl;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAStartedPaymentLeaves;
import dev.marcinromanowski.pspsim.kernel.spi.WhereAFieldsValuesComeFrom;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ThePaymentsAProviderStartsTest {

  private static final String PROVIDER = "aprovider";

  private static final String ANOTHER_PROVIDER = "anotherprovider";

  private static final String RUN = "a-named-run";

  private static final String ACCOUNT = "merchant-1";

  private static final String THE_HOSTED_PAGE = "aHostedPage";

  private static final String THE_SAVED_CARD_CHARGE = "aSavedCardCharge";

  private static final String THE_PAYER_PAGE = "/aprovider/pay/{sessionId}";

  private static final String AMOUNT = "sum";

  private static final String DESCRIPTION = "description";

  private static final String THE_CALLBACK = "notifyUrl";

  private static final String THE_CARD = "cardToken";

  private static final String THE_TARGET_THIS_RUN_CONFIGURES = "https://merchant.example/callbacks";

  private static final String WHAT_A_HOSTED_PAGE_LEAVES_BEHIND =
      "a payment process waiting for the payer on this provider's own page, with nothing held on"
          + " any card yet";

  private static final String WHAT_A_SAVED_CARD_CHARGE_LEAVES_BEHIND =
      "a transaction that is already paid, because this provider takes the money off the saved"
          + " card as it answers";

  private static final String WHERE_THE_USER_ID_COMES_FROM =
      "this provider names the business by the userId configured for the account the payment is"
          + " started on, which this simulator sends and never reads back onto a screen";

  private static final AFieldATesterFillsIn AMOUNT_FIELD =
      AFieldATesterFillsIn.typed(
          AMOUNT, "amount", true, "The amount this provider puts on the card.", "10.00");

  private static final AFieldATesterFillsIn DESCRIPTION_FIELD =
      AFieldATesterFillsIn.typed(
          DESCRIPTION,
          "what the payer is paying for",
          false,
          "The line this provider prints on its page.",
          null);

  private static final AFieldATesterFillsIn THE_CALLBACK_FIELD =
      AFieldATesterFillsIn.theCallbackTarget(
          THE_CALLBACK,
          "where this provider posts the callback",
          "This provider posts the callback here.");

  private static final AFieldATesterFillsIn THE_CARD_FIELD =
      AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
          THE_CARD, "the saved card to charge", true, "The cards the pages of this run saved.");

  private static final ProviderOriginationFields THE_WAYS_IT_DECLARES = theWaysOf(THE_CARD_FIELD);

  @Test
  void a_suite_discovers_every_way_a_provider_declares_with_the_words_it_describes_each_by() {
    Answer declared = ways(aModuleDeclaring(THE_WAYS_IT_DECLARES), null, null, null);

    assertThat(declared.status()).isEqualTo(200);
    OriginationsResponse body = waysIn(declared);
    assertThat(body.testRunId()).isEqualTo("default");
    assertThat(body.provider()).isEqualTo(PROVIDER);
    assertThat(body.accountId()).isNull();
    assertThat(body.waysToStartAPayment())
        .extracting(WayToStartAPaymentView::startedAs)
        .containsExactly(THE_HOSTED_PAGE, THE_SAVED_CARD_CHARGE);
    WayToStartAPaymentView hosted = wayOf(declared, THE_HOSTED_PAGE);
    assertThat(hosted.label()).isEqualTo("Hold the money on the payer's card");
    assertThat(hosted.whatItLeavesBehind()).isEqualTo(WHAT_A_HOSTED_PAGE_LEAVES_BEHIND);
    assertThat(hosted.whatThePayerDoesNext())
        .as("the payer step reaches a caller in the words the kernel renders it for a tester")
        .isEqualTo(new ThePayerStep.APageThisSimulatorServes(THE_PAYER_PAGE).asATesterReadsIt());
    assertThat(hosted.thePayerPageThisSimulatorServes()).isEqualTo(THE_PAYER_PAGE);
  }

  @Test
  void a_way_that_leaves_a_payer_no_page_names_none_and_says_what_stands_in_its_place() {
    Answer declared = ways(aModuleDeclaring(THE_WAYS_IT_DECLARES), RUN, null, null);

    WayToStartAPaymentView charge = wayOf(declared, THE_SAVED_CARD_CHARGE);
    assertThat(charge.thePayerPageThisSimulatorServes()).isNull();
    assertThat(charge.whatThePayerDoesNext()).contains("no payer in this call");
    assertThat(charge.whatItLeavesBehind()).isEqualTo(WHAT_A_SAVED_CARD_CHARGE_LEAVES_BEHIND);
  }

  @Test
  void the_fields_a_way_asks_for_are_answered_in_the_order_the_provider_declared_them() {
    Answer declared = ways(aModuleDeclaring(THE_WAYS_IT_DECLARES), RUN, null, null);

    assertThat(wayOf(declared, THE_HOSTED_PAGE).fields())
        .extracting(DeclaredFieldView::key)
        .containsExactly(AMOUNT, DESCRIPTION, THE_CALLBACK);
  }

  @Test
  void a_field_is_answered_with_its_own_sentence_and_the_query_parameter_a_value_is_sent_in() {
    Answer declared = ways(aModuleDeclaring(THE_WAYS_IT_DECLARES), RUN, null, null);

    DeclaredFieldView amount = fieldOf(declared, THE_HOSTED_PAGE, AMOUNT);
    assertThat(amount.label()).isEqualTo("amount");
    assertThat(amount.whatItIsFor()).isEqualTo("The amount this provider puts on the card.");
    assertThat(amount.required()).isTrue();
    assertThat(amount.theValueItSuggests()).isEqualTo("10.00");
    assertThat(amount.theQueryParameterItIsSentIn()).isEqualTo("field=sum=<value>");
    assertThat(amount.whereItsValuesComeFrom())
        .isEqualTo(WhereAFieldsValuesComeFrom.WHATEVER_THE_TESTER_TYPES);
    assertThat(fieldOf(declared, THE_HOSTED_PAGE, DESCRIPTION).required()).isFalse();
    assertThat(fieldOf(declared, THE_HOSTED_PAGE, DESCRIPTION).theValueItSuggests()).isNull();
  }

  @Test
  void the_fields_this_simulator_fills_in_itself_are_published_beside_the_ways_and_in_no_way() {
    Answer declared = ways(aModuleDeclaring(THE_WAYS_IT_DECLARES), RUN, null, null);

    assertThat(waysIn(declared).theFieldsThisSimulatorFillsInItself())
        .extracting(FilledInFieldView::key, FilledInFieldView::whereTheValueComesFrom)
        .containsExactly(tuple("userId", WHERE_THE_USER_ID_COMES_FROM));
    assertThat(waysIn(declared).waysToStartAPayment())
        .as("a field the simulator fills in is asked of nobody, so no way of starting one asks it")
        .allSatisfy(
            way ->
                assertThat(way.fields())
                    .extracting(DeclaredFieldView::key)
                    .doesNotContain("userId"));
  }

  @Test
  void a_callback_field_offers_the_targets_this_run_configures_and_the_accounts_are_published() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    Answer declared = ways(module, RUN, ACCOUNT, THE_HOSTED_PAGE);

    assertThat(waysIn(declared).accountId()).isEqualTo(ACCOUNT);
    assertThat(waysIn(declared).theAccountsThisRunConfigures()).containsExactly(ACCOUNT);
    DeclaredFieldView callback = fieldOf(declared, THE_HOSTED_PAGE, THE_CALLBACK);
    assertThat(callback.whereItsValuesComeFrom())
        .isEqualTo(WhereAFieldsValuesComeFrom.THE_CALLBACK_TARGETS_THIS_RUN_CONFIGURES);
    assertThat(callback.theValuesItOffers())
        .extracting(offered -> offered.value() + " (" + offered.whatItNames() + ")")
        .containsExactly(
            THE_TARGET_THIS_RUN_CONFIGURES
                + " ("
                + ThePaymentsAProviderStarts.A_CALLBACK_TARGET_THIS_RUN_CONFIGURES
                + ")");
  }

  @Test
  void a_field_whose_values_this_run_produced_is_answered_with_them_once_an_account_is_named() {
    AProviderModuleAPaymentIsStartedOn module =
        aModuleDeclaring(THE_WAYS_IT_DECLARES)
            .havingAlreadyProduced(
                THE_CARD, List.of(new AValueAFieldOffers("tok_1", "the card saved at noon")));

    Answer withoutAWayNamed = ways(module, RUN, ACCOUNT, null);
    Answer withOne = ways(module, RUN, ACCOUNT, THE_SAVED_CARD_CHARGE);

    assertThat(fieldOf(withoutAWayNamed, THE_SAVED_CARD_CHARGE, THE_CARD).theValuesItOffers())
        .as("a run's own values are resolved for the account and the way a caller named")
        .isEmpty();
    assertThat(fieldOf(withOne, THE_SAVED_CARD_CHARGE, THE_CARD).theValuesItOffers())
        .extracting(offered -> offered.value() + " (" + offered.whatItNames() + ")")
        .containsExactly("tok_1 (the card saved at noon)");
  }

  @Test
  void a_field_whose_values_the_provider_declares_is_answered_with_each_of_them_and_its_label() {
    ProviderOriginationFields oneOfTwoValues =
        theWaysOf(
            AFieldATesterFillsIn.chosenFrom(
                "paymentType",
                "the kind of payment this charge is",
                true,
                "The kinds this provider names.",
                List.of(
                    new AValueAFieldOffers("1", "a standing order"),
                    new AValueAFieldOffers("2", "a regular payment"))));

    Answer declared = ways(aModuleDeclaring(oneOfTwoValues), RUN, null, null);

    DeclaredFieldView kind = fieldOf(declared, THE_SAVED_CARD_CHARGE, "paymentType");
    assertThat(kind.whereItsValuesComeFrom())
        .isEqualTo(WhereAFieldsValuesComeFrom.THE_VALUES_THE_PROVIDER_DECLARES);
    assertThat(kind.theValuesItOffers())
        .extracting(offered -> offered.value() + " (" + offered.whatItNames() + ")")
        .containsExactly("1 (a standing order)", "2 (a regular payment)");
  }

  @Test
  void a_provider_this_simulator_does_not_know_is_answered_four_hundred_and_four_on_both_routes() {
    ThePaymentsAProviderStarts originations = originations(aModuleDeclaring(THE_WAYS_IT_DECLARES));

    Answer discovery = originations.theWaysThisProviderDeclares("nosuchprovider", RUN, null, null);
    Answer starting =
        originations.thePaymentItStarts("nosuchprovider", RUN, ACCOUNT, THE_HOSTED_PAGE, List.of());

    assertThat(discovery.status()).isEqualTo(404);
    assertThat(starting.status()).isEqualTo(404);
    assertThat(messageIn(discovery)).contains(PROVIDER).contains("unknown provider");
    assertThat(messageIn(starting)).isEqualTo(messageIn(discovery));
  }

  @Test
  void a_test_run_id_nothing_can_read_is_answered_four_hundred_on_both_routes() {
    ThePaymentsAProviderStarts originations = originations(aModuleDeclaring(THE_WAYS_IT_DECLARES));

    Answer discovery =
        originations.theWaysThisProviderDeclares(PROVIDER, "not a run id", null, null);
    Answer starting =
        originations.thePaymentItStarts(PROVIDER, "not a run id", ACCOUNT, THE_HOSTED_PAGE, null);

    assertThat(discovery.status()).isEqualTo(400);
    assertThat(starting.status()).isEqualTo(400);
    assertThat(messageIn(discovery)).contains("testRunId");
    assertThat(messageIn(starting)).contains("testRunId");
  }

  @Test
  void a_started_payment_is_answered_two_hundred_and_one_with_what_the_provider_left_behind() {
    AProviderModuleAPaymentIsStartedOn module =
        aModuleDeclaring(THE_WAYS_IT_DECLARES)
            .answering(
                new OriginationOutcome.Originated(
                    "prc_1", WHAT_A_HOSTED_PAGE_LEAVES_BEHIND, "scn_1"));

    Answer started = start(module, THE_HOSTED_PAGE, "sum=10.00");

    assertThat(started.status()).isEqualTo(201);
    OriginationResponse body = startedIn(started);
    assertThat(body.testRunId()).isEqualTo(RUN);
    assertThat(body.provider()).isEqualTo(PROVIDER);
    assertThat(body.accountId()).isEqualTo(ACCOUNT);
    assertThat(body.startedAs()).isEqualTo(THE_HOSTED_PAGE);
    assertThat(body.providerObjectId()).isEqualTo("prc_1");
    assertThat(body.whatExistsNow()).isEqualTo(WHAT_A_HOSTED_PAGE_LEAVES_BEHIND);
    assertThat(body.theRuleThatDecidedIt()).isEqualTo("scn_1");
  }

  @Test
  void the_values_a_caller_sent_reach_the_module_under_the_keys_the_provider_reads_them_under() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    start(module, THE_HOSTED_PAGE, "description=one seat", "sum=10.00", "notifyUrl=");

    assertThat(module.thePaymentsItWasAskedToStart()).hasSize(1);
    OriginationRequest asked = module.thePaymentsItWasAskedToStart().getFirst();
    assertThat(asked.accountId()).isEqualTo(ACCOUNT);
    assertThat(asked.startedAs()).isEqualTo(THE_HOSTED_PAGE);
    assertThat(asked.testRunId()).isEqualTo(TestRunId.of(RUN));
    assertThat(asked.askedBy()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(asked.values())
        .as(
            "a payment is started with the fields in the order the way declares them, and a blank"
                + " is no value")
        .containsExactly(Map.entry(AMOUNT, "10.00"), Map.entry(DESCRIPTION, "one seat"));
  }

  @Test
  void a_value_carrying_an_equals_sign_of_its_own_reaches_the_module_whole() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    start(module, THE_HOSTED_PAGE, "sum=10.00", "description=a=b=c");

    assertThat(module.thePaymentsItWasAskedToStart().getFirst().values())
        .containsEntry(DESCRIPTION, "a=b=c");
  }

  @Test
  void a_field_sent_without_the_key_it_belongs_to_is_refused_before_the_module_is_asked() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    Answer refused = start(module, THE_HOSTED_PAGE, "sum 10.00");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(messageIn(refused))
        .isEqualTo(
            ThePaymentsAProviderStarts.A_FIELD_CARRIES_THE_KEY_IT_IS_SENT_FOR_AND_THE_VALUE_AFTER_IT
                + "sum 10.00");
    assertThat(module.thePaymentsItWasAskedToStart()).isEmpty();
  }

  @Test
  void a_field_that_names_no_key_at_all_is_refused_before_the_module_is_asked() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    Answer refused = start(module, THE_HOSTED_PAGE, "=10.00");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(messageIn(refused))
        .isEqualTo(TheFieldParameterBothRoutesRead.A_FIELD_NAMES_THE_KEY_IT_IS_SENT_FOR + "=10.00");
    assertThat(module.thePaymentsItWasAskedToStart()).isEmpty();
  }

  @Test
  void one_field_sent_twice_is_refused_rather_than_one_of_the_two_values_being_dropped() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    Answer refused = start(module, THE_HOSTED_PAGE, "sum=10.00", "sum=20.00");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(messageIn(refused))
        .isEqualTo(
            ThePaymentsAProviderStarts.ONE_WAY_OF_STARTING_A_PAYMENT_ASKS_EACH_OF_ITS_FIELDS_ONCE
                + AMOUNT
                + TheFieldParameterBothRoutesRead.AND_ONE_OF_THEM_WOULD_BE_DROPPED);
    assertThat(module.thePaymentsItWasAskedToStart()).isEmpty();
  }

  @Test
  void a_payment_started_without_the_field_its_way_cannot_start_without_is_refused_in_its_words() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    Answer refused = start(module, THE_HOSTED_PAGE, "description=one seat");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(messageIn(refused))
        .contains("amount")
        .contains("The amount this provider puts on the card.");
    assertThat(module.thePaymentsItWasAskedToStart()).isEmpty();
  }

  @Test
  void a_field_no_way_of_this_provider_asks_for_is_refused_rather_than_dropped() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    Answer refused = start(module, THE_HOSTED_PAGE, "sum=10.00", "invented=1");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(messageIn(refused)).contains("invented").contains(AMOUNT);
    assertThat(module.thePaymentsItWasAskedToStart()).isEmpty();
  }

  @Test
  void a_payment_started_on_no_account_is_refused_because_nothing_could_sign_the_call() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    Answer refused =
        originations(module)
            .thePaymentItStarts(PROVIDER, RUN, null, THE_HOSTED_PAGE, List.of("sum=10.00"));

    assertThat(refused.status()).isEqualTo(400);
    assertThat(messageIn(refused)).isEqualTo(OriginationControl.AN_ACCOUNT_IS_REQUIRED);
    assertThat(module.thePaymentsItWasAskedToStart()).isEmpty();
  }

  @Test
  void
      a_way_of_starting_a_payment_this_provider_does_not_declare_is_refused_naming_the_ones_it_has() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    Answer refused = start(module, "somethingElse", "sum=10.00");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(messageIn(refused)).contains(THE_HOSTED_PAGE).contains(THE_SAVED_CARD_CHARGE);
    assertThat(module.thePaymentsItWasAskedToStart()).isEmpty();

    Answer named = originations(module).thePaymentItStarts(PROVIDER, RUN, ACCOUNT, null, List.of());

    assertThat(named.status()).isEqualTo(400);
    assertThat(messageIn(named)).contains(THE_HOSTED_PAGE);
  }

  @Test
  void a_callback_target_this_run_does_not_configure_is_refused_by_the_core_and_not_the_provider() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    Answer refused =
        start(module, THE_HOSTED_PAGE, "sum=10.00", "notifyUrl=https://elsewhere.example/hook");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(messageIn(refused))
        .contains(THE_TARGET_THIS_RUN_CONFIGURES)
        .contains(OriginationControl.THIS_SIMULATOR_IS_THE_PARTY_THAT_POSTS_A_CALLBACK);
    assertThat(module.thePaymentsItWasAskedToStart()).isEmpty();
  }

  @Test
  void a_callback_target_this_run_configures_reaches_the_module_as_it_was_sent() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);

    start(module, THE_HOSTED_PAGE, "sum=10.00", "notifyUrl=" + THE_TARGET_THIS_RUN_CONFIGURES);

    assertThat(module.thePaymentsItWasAskedToStart().getFirst().values())
        .containsEntry(THE_CALLBACK, THE_TARGET_THIS_RUN_CONFIGURES);
  }

  @Test
  void a_provider_that_refused_the_payment_is_answered_with_its_own_refusal_and_no_object_id() {
    AProviderModuleAPaymentIsStartedOn module =
        aModuleDeclaring(THE_WAYS_IT_DECLARES)
            .answering(
                new OriginationOutcome.RefusedByTheProvider(
                    "err.id 1013 transactionUniqueIdentifier is not a positive whole number",
                    "scn_2"));

    Answer refused = start(module, THE_HOSTED_PAGE, "sum=10.00");

    assertThat(refused.status()).isEqualTo(422);
    assertThat(messageIn(refused))
        .isEqualTo("err.id 1013 transactionUniqueIdentifier is not a positive whole number");
  }

  @Test
  void a_rule_of_this_run_that_answered_instead_of_the_provider_is_named_in_the_refusal() {
    AProviderModuleAPaymentIsStartedOn module =
        aModuleDeclaring(THE_WAYS_IT_DECLARES)
            .answering(new OriginationOutcome.AnsweredByAScenarioInstead("scn_3"));

    Answer refused = start(module, THE_HOSTED_PAGE, "sum=10.00");

    assertThat(refused.status()).isEqualTo(422);
    assertThat(messageIn(refused))
        .isEqualTo(ThePaymentsAProviderStarts.A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD + "scn_3");
  }

  @Test
  void an_account_this_test_run_does_not_configure_is_answered_four_hundred_and_four() {
    AProviderModuleAPaymentIsStartedOn module =
        aModuleDeclaring(THE_WAYS_IT_DECLARES).answering(new OriginationOutcome.NoSuchAccount());

    Answer refused = start(module, THE_HOSTED_PAGE, "sum=10.00");

    assertThat(refused.status()).isEqualTo(404);
    assertThat(messageIn(refused))
        .isEqualTo(
            "no account with that accountId in test run " + RUN + " of provider " + PROVIDER);
  }

  @Test
  void a_module_that_starts_no_payment_of_this_kind_is_reported_in_the_words_it_answered_with() {
    AProviderModuleAPaymentIsStartedOn module =
        aModuleDeclaring(THE_WAYS_IT_DECLARES)
            .answering(
                new OriginationOutcome.NotOffered("this module starts no payment of that kind"));

    Answer refused = start(module, THE_HOSTED_PAGE, "sum=10.00");

    assertThat(refused.status()).isEqualTo(400);
    assertThat(messageIn(refused)).isEqualTo("this module starts no payment of that kind");
  }

  @Test
  void a_started_payment_is_written_to_the_trail_as_an_action_of_the_control_api() {
    AProviderModuleAPaymentIsStartedOn module =
        aModuleDeclaring(THE_WAYS_IT_DECLARES)
            .answering(
                new OriginationOutcome.Originated(
                    "prc_1", WHAT_A_HOSTED_PAGE_LEAVES_BEHIND, "scn_1"));
    RecordedActions trail = new RecordedActions();

    originations(module, trail)
        .thePaymentItStarts(PROVIDER, RUN, ACCOUNT, THE_HOSTED_PAGE, List.of("sum=10.00"));

    assertThat(trail.written()).hasSize(1);
    AuditRecord written = trail.written().getFirst();
    assertThat(written.provider()).isEqualTo(ProviderId.of(PROVIDER));
    assertThat(written.testRunId()).isEqualTo(TestRunId.of(RUN));
    assertThat(written.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(written.method()).isEqualTo("POST");
    assertThat(written.path()).isEqualTo("/__simulator/" + PROVIDER + "/originations");
    assertThat(written.httpStatus()).isEqualTo(201);
    assertThat(written.providerObjectId()).isEqualTo("prc_1");
    assertThat(written.accountId()).isEqualTo(ACCOUNT);
    assertThat(written.scenarioId()).isEqualTo("scn_1");
    assertThat(written.sanitizedBody())
        .containsEntry("startedAs", List.of(THE_HOSTED_PAGE))
        .containsEntry(AMOUNT, List.of("10.00"))
        .containsEntry("providerObjectId", List.of("prc_1"));
  }

  @Test
  void a_refusal_is_written_to_the_trail_with_the_words_it_was_refused_in_and_no_object_id() {
    AProviderModuleAPaymentIsStartedOn module =
        aModuleDeclaring(THE_WAYS_IT_DECLARES)
            .answering(new OriginationOutcome.RefusedByTheProvider("err.id 140", "scn_4"));
    RecordedActions trail = new RecordedActions();

    originations(module, trail)
        .thePaymentItStarts(PROVIDER, RUN, ACCOUNT, THE_HOSTED_PAGE, List.of("sum=10.00"));

    AuditRecord written = trail.written().getFirst();
    assertThat(written.httpStatus()).isEqualTo(422);
    assertThat(written.providerObjectId()).isNull();
    assertThat(written.scenarioId()).isEqualTo("scn_4");
    assertThat(written.refusal())
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                TheTrailRowAStartedPaymentLeaves.THE_PAYMENT_IT_ASKED_THIS_PROVIDER_TO_START));
    assertThat(written.sanitizedBody()).containsEntry("refusedBecause", List.of("err.id 140"));
  }

  @Test
  void a_request_the_core_could_not_read_is_written_to_the_trail_as_a_rejected_action() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);
    RecordedActions trail = new RecordedActions();

    originations(module, trail)
        .thePaymentItStarts(PROVIDER, RUN, ACCOUNT, THE_HOSTED_PAGE, List.of("invented=1"));

    AuditRecord written = trail.written().getFirst();
    assertThat(written.httpStatus()).isEqualTo(400);
    assertThat(written.accountId()).isEqualTo(ACCOUNT);
    assertThat(written.sanitizedBody())
        .containsEntry("startedAs", List.of(THE_HOSTED_PAGE))
        .hasEntrySatisfying(
            ActionFacts.REFUSED_BECAUSE,
            refusal -> assertThat(refusal.getFirst()).contains("invented"));
  }

  @Test
  void
      a_payment_asked_for_under_a_run_this_simulator_cannot_read_is_written_under_the_reserved_run() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(THE_WAYS_IT_DECLARES);
    RecordedActions trail = new RecordedActions();

    Answer refused =
        originations(module, trail)
            .thePaymentItStarts(
                PROVIDER,
                "a run no namespace can hold",
                ACCOUNT,
                THE_HOSTED_PAGE,
                List.of("sum=10.00"));

    assertThat(refused.status()).isEqualTo(400);
    assertThat(trail.written())
        .as(
            "every other mutating route of this control api files the row of a request naming a"
                + " run it could not read under the reserved run, and a suite that mistyped the"
                + " run on this one route alone would find the call it made nowhere at all")
        .hasSize(1);
    AuditRecord written = trail.written().getFirst();
    assertThat(written.testRunId()).isEqualTo(TestRunId.UNKNOWN);
    assertThat(written.httpStatus()).isEqualTo(400);
    assertThat(written.path()).isEqualTo("/__simulator/" + PROVIDER + "/originations");
    assertThat(written.accountId()).isEqualTo(ACCOUNT);
    assertThat(written.sanitizedBody())
        .containsEntry("startedAs", List.of(THE_HOSTED_PAGE))
        .hasEntrySatisfying(
            ActionFacts.REFUSED_BECAUSE,
            refusal -> assertThat(refusal.getFirst()).contains("testRunId"));
  }

  @Test
  void a_discovery_of_a_run_this_simulator_cannot_read_writes_nothing_to_the_trail() {
    RecordedActions trail = new RecordedActions();

    Answer refused =
        originations(aModuleDeclaring(THE_WAYS_IT_DECLARES), trail)
            .theWaysThisProviderDeclares(
                PROVIDER, "a run no namespace can hold", ACCOUNT, THE_HOSTED_PAGE);

    assertThat(refused.status()).isEqualTo(400);
    assertThat(trail.written())
        .as("reading what a provider declares moves nothing, whatever run it was asked under")
        .isEmpty();
  }

  @Test
  void a_discovery_of_the_ways_a_provider_declares_writes_nothing_to_the_trail() {
    RecordedActions trail = new RecordedActions();

    originations(aModuleDeclaring(THE_WAYS_IT_DECLARES), trail)
        .theWaysThisProviderDeclares(PROVIDER, RUN, ACCOUNT, THE_HOSTED_PAGE);

    assertThat(trail.written())
        .as("reading what a provider declares moves nothing, so the trail records no action")
        .isEmpty();
  }

  @Test
  void one_providers_ways_are_never_answered_for_another_of_the_same_simulator() {
    ThePaymentsAProviderStarts originations =
        new ThePaymentsAProviderStarts(
            new ProviderRegistry(
                List.of(
                    aModuleDeclaring(THE_WAYS_IT_DECLARES),
                    new AProviderModuleAPaymentIsStartedOn(ANOTHER_PROVIDER)
                        .declaring(
                            new ProviderOriginationFields(
                                ProviderId.of(ANOTHER_PROVIDER),
                                List.of(
                                    new DeclaredOrigination(
                                        "theOnlyWayItHas",
                                        "Take the money in one call",
                                        "a charge that is already paid",
                                        new ThePayerStep.NoPageThisSimulatorServes(
                                            "this provider has no payer page at all"),
                                        List.of(AMOUNT_FIELD))),
                                new LinkedHashMap<>())))),
            new TheAccountsAndTargetsOfARun(),
            new RecordedActions().asTrail(),
            "default");

    Answer mine = originations.theWaysThisProviderDeclares(PROVIDER, RUN, null, null);
    Answer theirs = originations.theWaysThisProviderDeclares(ANOTHER_PROVIDER, RUN, null, null);

    assertThat(waysIn(mine).waysToStartAPayment()).hasSize(2);
    assertThat(waysIn(theirs).waysToStartAPayment())
        .extracting(WayToStartAPaymentView::startedAs)
        .containsExactly("theOnlyWayItHas");
    assertThat(waysIn(theirs).provider()).isEqualTo(ANOTHER_PROVIDER);
    assertThat(waysIn(theirs).theFieldsThisSimulatorFillsInItself()).isEmpty();
  }

  @Test
  void a_way_nothing_was_sent_for_is_started_with_no_value_at_all_rather_than_refused_here() {
    AProviderModuleAPaymentIsStartedOn module = aModuleDeclaring(theWaysOf(DESCRIPTION_FIELD));

    Answer started =
        originations(module)
            .thePaymentItStarts(PROVIDER, RUN, ACCOUNT, THE_SAVED_CARD_CHARGE, null);

    assertThat(started.status()).isEqualTo(201);
    assertThat(module.thePaymentsItWasAskedToStart().getFirst().values())
        .as("a request that carries no field parameter at all sends no value, not an empty one")
        .isEmpty();
  }

  @Test
  void the_grammar_of_a_sent_field_is_the_cores_own_and_names_no_field_of_any_provider() {
    assertThatCode(
            () ->
                TheFieldParameterBothRoutesRead.theValuesItWasSent(
                    null, ThePaymentsAProviderStarts.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .as("a request with no field parameter at all reads as a request that sent no value")
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                TheFieldParameterBothRoutesRead.theValuesItWasSent(
                    List.of(), ThePaymentsAProviderStarts.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .as("the core reads a key and a value and knows the meaning of neither")
        .doesNotThrowAnyException();
    assertThat(
            TheFieldParameterBothRoutesRead.theValuesItWasSent(
                List.of("anything=at all"),
                ThePaymentsAProviderStarts.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .containsExactly(Map.entry("anything", "at all"));
  }

  private static ProviderOriginationFields theWaysOf(AFieldATesterFillsIn theFieldOfACharge) {
    LinkedHashMap<String, String> filledIn = new LinkedHashMap<>();
    filledIn.put("userId", WHERE_THE_USER_ID_COMES_FROM);
    return new ProviderOriginationFields(
        ProviderId.of(PROVIDER),
        List.of(
            new DeclaredOrigination(
                THE_HOSTED_PAGE,
                "Hold the money on the payer's card",
                WHAT_A_HOSTED_PAGE_LEAVES_BEHIND,
                new ThePayerStep.APageThisSimulatorServes(THE_PAYER_PAGE),
                List.of(AMOUNT_FIELD, DESCRIPTION_FIELD, THE_CALLBACK_FIELD)),
            new DeclaredOrigination(
                THE_SAVED_CARD_CHARGE,
                "Charge a card one of this run's pages already saved",
                WHAT_A_SAVED_CARD_CHARGE_LEAVES_BEHIND,
                new ThePayerStep.NoPageThisSimulatorServes(
                    "This provider takes a stored-card charge server to server, so there is no"
                        + " payer in this call and no page for one to reach."),
                List.of(theFieldOfACharge))),
        filledIn);
  }

  private static AProviderModuleAPaymentIsStartedOn aModuleDeclaring(
      ProviderOriginationFields ways) {
    return new AProviderModuleAPaymentIsStartedOn(PROVIDER).declaring(ways);
  }

  private static Answer ways(
      AProviderModuleAPaymentIsStartedOn module,
      @Nullable String run,
      @Nullable String accountId,
      @Nullable String startedAs) {
    return originations(module).theWaysThisProviderDeclares(PROVIDER, run, accountId, startedAs);
  }

  private static Answer start(
      AProviderModuleAPaymentIsStartedOn module, String startedAs, String... field) {
    return originations(module)
        .thePaymentItStarts(PROVIDER, RUN, ACCOUNT, startedAs, List.of(field));
  }

  private static ThePaymentsAProviderStarts originations(
      AProviderModuleAPaymentIsStartedOn module) {
    return originations(module, new RecordedActions());
  }

  private static ThePaymentsAProviderStarts originations(
      AProviderModuleAPaymentIsStartedOn module, RecordedActions trail) {
    return new ThePaymentsAProviderStarts(
        new ProviderRegistry(List.of(module)),
        new TheAccountsAndTargetsOfARun(),
        trail.asTrail(),
        "default");
  }

  private static OriginationsResponse waysIn(Answer answered) {
    return (OriginationsResponse) answered.body();
  }

  private static OriginationResponse startedIn(Answer answered) {
    return (OriginationResponse) answered.body();
  }

  private static String messageIn(Answer answered) {
    return ((ControlError) answered.body()).message();
  }

  private static WayToStartAPaymentView wayOf(Answer answered, String startedAs) {
    return waysIn(answered).waysToStartAPayment().stream()
        .filter(way -> way.startedAs().equals(startedAs))
        .findFirst()
        .orElseThrow();
  }

  private static DeclaredFieldView fieldOf(Answer answered, String startedAs, String key) {
    return wayOf(answered, startedAs).fields().stream()
        .filter(field -> field.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private static final class TheAccountsAndTargetsOfARun implements ProviderAccounts {

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      throw new UnsupportedOperationException(
          "starting a payment reads the accounts and the callback targets a run configures and"
              + " writes neither");
    }

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      throw new UnsupportedOperationException(
          "starting a payment reads the accounts and the callback targets a run configures and"
              + " removes neither");
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.of(ACCOUNT);
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      List<WebhookTarget> configured = new ArrayList<>();
      configured.add(new WebhookTarget(ACCOUNT, THE_TARGET_THIS_RUN_CONFIGURES));
      configured.add(new WebhookTarget(ACCOUNT, THE_TARGET_THIS_RUN_CONFIGURES));
      return List.copyOf(configured);
    }
  }
}
