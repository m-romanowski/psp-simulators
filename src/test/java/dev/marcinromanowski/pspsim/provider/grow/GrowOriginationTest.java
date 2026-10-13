package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationControl;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrowOriginationTest {

  private static final TestRunId RUN = TestRunId.of("grow-origination");

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

  private static final String MERCHANT = "merchant-1";

  private static final String A_HOLD = "createPaymentProcess:chargeType=2";

  private static final String A_SAVED_CARD = "createPaymentProcess:chargeType=3";

  private static final String A_SAVED_CARD_CHARGE = "createTransactionWithToken";

  private static final String THE_CARD_THE_PAYER_NAMED = "sandbox-4580";

  private static final String CHARGED = "41.50";

  private static final String THE_TARGET_THIS_RUN_CONFIGURES = "https://merchant.example/hook";

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          SimulatorDoubles.clockAt(NOW),
          ids,
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new GrowCallbackAcknowledgement(accounts)),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final InMemoryPaymentMethodFixtures seededMethods = new InMemoryPaymentMethodFixtures();

  private final GrowCallbacks callbacks =
      SimulatorDoubles.callbacks(
          processes, transactions, accounts, dispatcher, SimulatorDoubles.clockAt(NOW), ids);

  private final SimulatorProvider provider =
      new GrowProvider(
          accounts,
          audit,
          processes,
          transactions,
          dispatcher,
          callbacks,
          SimulatorDoubles.clockAt(NOW),
          seededMethods,
          scenarioRules,
          SimulatorDoubles.heldResponses(),
          new SettleSuspendedTransaction(
              transactions, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          new RefundTransaction(
              transactions, ids, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          SimulatorDoubles.paymentProcessCreation(processes, scenarioRules, ids),
          SimulatorDoubles.storedCardCharge(processes, transactions, ids, scenarioRules),
          SimulatorDoubles.retraceOfAStoredCardCharge(processes, transactions, scenarioRules),
          new CompleteHostedProcess(processes, callbacks, new GrowPayerCards(seededMethods)));

  @Test
  void grow_offers_a_hold_a_card_registration_and_a_charge_of_a_card_already_saved() {
    assertThat(provider.originationFields().names())
        .containsExactly(A_HOLD, A_SAVED_CARD, A_SAVED_CARD_CHARGE);
    assertThat(provider.originationFields().wayNamed(A_HOLD).orElseThrow().keys())
        .containsExactly(
            "sum",
            "description",
            "successUrl",
            "cancelUrl",
            "pageField[fullName]",
            "pageField[phone]",
            "pageField[email]",
            "notifyUrl",
            "saveCardToken");
  }

  @Test
  void a_charge_of_a_saved_card_asks_for_what_that_call_reads_and_for_no_page_of_its_own() {
    DeclaredOrigination charge =
        provider.originationFields().wayNamed(A_SAVED_CARD_CHARGE).orElseThrow();

    assertThat(charge.keys())
        .containsExactly(
            "cardToken",
            "sum",
            "description",
            "paymentType",
            "paymentNum",
            "transactionUniqueIdentifier",
            "transactionGroupIdentifier",
            "pageField[fullName]",
            "pageField[phone]",
            "pageField[email]");
    assertThat(charge.thePayerStep())
        .as("a stored-card charge reaches no payer, so it promises no page of this build")
        .isInstanceOf(ThePayerStep.NoPageThisSimulatorServes.class);
    assertThat(charge.keys())
        .as("grow reads no notifyUrl, pageCode or chargeType on a stored-card charge")
        .doesNotContain("notifyUrl", "pageCode", "chargeType");
  }

  @Test
  void the_card_to_charge_is_suggested_out_of_what_this_run_produced_and_the_amount_is_not() {
    DeclaredOrigination charge =
        provider.originationFields().wayNamed(A_SAVED_CARD_CHARGE).orElseThrow();

    assertThat(charge.field(GrowFields.CARD_TOKEN).orElseThrow().choices())
        .as("the tokens come from the run and never from a vocabulary this provider declares")
        .isEmpty();
    assertThat(
            charge
                .field(GrowFields.CARD_TOKEN)
                .orElseThrow()
                .namesAValueThisTestRunHasAlreadyProduced())
        .isTrue();
    assertThat(
            charge.field(GrowFields.SUM).orElseThrow().namesAValueThisTestRunHasAlreadyProduced())
        .isFalse();
  }

  @Test
  void a_run_that_has_taken_no_card_through_a_hosted_page_offers_no_token_to_charge() {
    aConfiguredAccount();

    assertThat(theSavedCardsOfferedFor(MERCHANT)).isEmpty();
  }

  @Test
  void the_token_a_finished_registration_minted_is_offered_under_a_label_naming_that_process() {
    aConfiguredAccount();
    String processId = theProcessOf(startedAs(A_SAVED_CARD, Map.of())).processId();
    String cardToken = theCardTokenOf(processId);

    List<AValueAFieldOffers> offered = theSavedCardsOfferedFor(MERCHANT);

    assertThat(offered).extracting(AValueAFieldOffers::value).containsExactly(cardToken);
    assertThat(offered.get(0).label())
        .as("a tester tells two saved cards apart by the process and payer, not by the token")
        .contains(processId)
        .contains("Dana Levi")
        .doesNotContain(cardToken);
    assertThat(offered.get(0).label())
        .as(
            "the card itself is named as well, by the brand and the last digits grow recorded for"
                + " the card the payer finished the page with, because two processes of one payer"
                + " are told apart by the card and not by the process id alone")
        .contains(GrowRecordedCard.THE_PUBLISHED_SANDBOX_CARD.brand())
        .contains(GrowRecordedCard.THE_PUBLISHED_SANDBOX_CARD.suffix());
  }

  @Test
  void the_token_a_saved_card_minted_is_offered_to_the_account_it_was_saved_on_and_no_other() {
    aConfiguredAccount();
    accounts.merge(RUN, List.of(SimulatorDoubles.account(RUN, "merchant-2", "u-2", "p-2")));
    String processId = theProcessOf(startedAs(A_SAVED_CARD, Map.of())).processId();
    theCardTokenOf(processId);

    assertThat(theSavedCardsOfferedFor("merchant-2")).isEmpty();
    assertThat(theSavedCardsOfferedFor(MERCHANT)).hasSize(1);
  }

  @Test
  void a_field_the_provider_does_not_fill_from_the_run_is_offered_nothing_the_run_produced() {
    aConfiguredAccount();
    theCardTokenOf(theProcessOf(startedAs(A_SAVED_CARD, Map.of())).processId());

    assertThat(provider.theValuesThisTestRunHasAlreadyProducedFor(RUN, MERCHANT, GrowFields.SUM))
        .isEmpty();
  }

  @Test
  void a_saved_card_charged_from_the_wizard_is_a_paid_transaction_recorded_as_the_testers() {
    aConfiguredAccount();
    String cardToken = theCardTokenOf(theProcessOf(startedAs(A_SAVED_CARD, Map.of())).processId());

    OriginationOutcome outcome = startedAs(A_SAVED_CARD_CHARGE, aChargeOf(cardToken));

    assertThat(outcome).isInstanceOf(OriginationOutcome.Originated.class);
    String transactionId = ((OriginationOutcome.Originated) outcome).providerObjectId();
    GrowTransaction charged = transactions.findByTransactionId(RUN, transactionId).orElseThrow();
    assertThat(charged.kind()).isEqualTo(GrowTransactionKind.TOKEN_PAYMENT);
    assertThat(charged.amount()).isEqualTo(Amount.of(CHARGED));
    assertThat(charged.isReleased())
        .as("a stored-card charge is money taken and leaves no hold to settle")
        .isTrue();
    assertThat(charged.origin())
        .as("a charge a tester clicked is never reported as one a client application sent")
        .isEqualTo(ObjectOrigin.UI);
  }

  @Test
  void the_same_charge_asked_for_over_the_wire_is_recorded_as_the_client_applications() {
    aConfiguredAccount();
    String cardToken = theCardTokenOf(theProcessOf(startedAs(A_SAVED_CARD, Map.of())).processId());

    OriginationOutcome outcome =
        provider.originate(
            asked(A_SAVED_CARD_CHARGE, aChargeOf(cardToken), ObjectOrigin.CONTROL_API));

    String transactionId = ((OriginationOutcome.Originated) outcome).providerObjectId();
    assertThat(transactions.findByTransactionId(RUN, transactionId).orElseThrow().origin())
        .isEqualTo(ObjectOrigin.CONTROL_API);
  }

  @Test
  void a_charge_of_a_token_this_business_never_saved_is_refused_in_grows_own_words() {
    aConfiguredAccount();

    OriginationOutcome refused =
        startedAs(A_SAVED_CARD_CHARGE, aChargeOf("not-a-token-of-this-run"));

    assertThat(refused)
        .isEqualTo(
            new OriginationOutcome.RefusedByTheProvider(
                GrowErrors.unknownCardToken().id()
                    + " "
                    + GrowErrors.unknownCardToken().message()));
    assertThat(transactions.all()).isEmpty();
  }

  @Test
  void the_call_a_saved_card_charge_sends_carries_the_user_id_and_nothing_of_a_hosted_page() {
    aConfiguredAccount();
    GrowAccount account = accounts.find(RUN, MERCHANT).orElseThrow();

    GrowForm form =
        GrowOrigination.theSavedCardChargeFormOf(
            asked(A_SAVED_CARD_CHARGE, aChargeOf("tok-1"), ObjectOrigin.UI), account);

    assertThat(form.values().keySet())
        .startsWith(GrowFields.USER_ID)
        .contains(GrowFields.CARD_TOKEN);
    assertThat(form.values().keySet())
        .as("grow reads no pageCode and no chargeType on a stored-card charge, so none is sent")
        .doesNotContain(GrowFields.PAGE_CODE, GrowFields.CHARGE_TYPE);
    assertThat(form.first(GrowFields.USER_ID)).isEqualTo(account.userId());
  }

  @Test
  void the_form_a_hosted_process_sends_is_in_the_order_grow_declares_that_ways_fields() {
    aConfiguredAccount();
    GrowAccount account = accounts.find(RUN, MERCHANT).orElseThrow();
    DeclaredOrigination way = GrowOrigination.DECLARED.wayNamed(A_HOLD).orElseThrow();

    GrowForm form =
        GrowOrigination.theHostedProcessFormOf(aPaymentSubmittedBackToFront(way), account);

    List<String> theOrderGrowSendsThemIn = new ArrayList<>();
    theOrderGrowSendsThemIn.add(GrowFields.USER_ID);
    theOrderGrowSendsThemIn.add(GrowFields.PAGE_CODE);
    theOrderGrowSendsThemIn.add(GrowFields.CHARGE_TYPE);
    theOrderGrowSendsThemIn.addAll(way.keys());
    assertThat(form.values().keySet())
        .as(
            "grow writes its form by iterating the values the payment carries, so the fields it"
                + " fills in itself come first and the %d fields the way declares follow in the"
                + " order it declares them, however the wizard happened to submit them: these"
                + " were submitted back to front, and far too many orders exist for a pass here"
                + " to be a coincidence",
            way.keys().size())
        .containsExactlyElementsOf(theOrderGrowSendsThemIn);
  }

  private static OriginationRequest aPaymentSubmittedBackToFront(DeclaredOrigination way) {
    Map<String, String> submitted = new LinkedHashMap<>();
    for (AFieldATesterFillsIn field : way.fields().reversed()) {
      submitted.put(field.key(), theValueATesterPutsIn(field));
    }
    return OriginationControl.asked(
        GrowOrigination.DECLARED,
        RUN,
        MERCHANT,
        way.name(),
        submitted,
        List.of(THE_TARGET_THIS_RUN_CONFIGURES),
        ObjectOrigin.UI);
  }

  private static String theValueATesterPutsIn(AFieldATesterFillsIn field) {
    if (field.namesTheCallbackTarget()) {
      return THE_TARGET_THIS_RUN_CONFIGURES;
    }
    if (!field.choices().isEmpty()) {
      return field.choices().getFirst().value();
    }
    return "what a tester typed into " + field.key();
  }

  private List<AValueAFieldOffers> theSavedCardsOfferedFor(String accountId) {
    return provider.theValuesThisTestRunHasAlreadyProducedFor(
        RUN, accountId, GrowFields.CARD_TOKEN);
  }

  private String theCardTokenOf(String processId) {
    HostedCompletionOutcome finished =
        provider.complete(
            new HostedCompletionRequest(
                RUN, processId, PayerOutcome.PAID, THE_CARD_THE_PAYER_NAMED, ObjectOrigin.UI));

    assertThat(finished).isInstanceOf(HostedCompletionOutcome.Completed.class);
    GrowProcessOutcome outcome = processes.findByProcessId(RUN, processId).orElseThrow().outcome();
    assertThat(outcome).isNotNull();
    return String.valueOf(outcome.cardToken());
  }

  private static Map<String, String> aChargeOf(String cardToken) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(GrowFields.CARD_TOKEN, cardToken);
    values.put(GrowFields.SUM, CHARGED);
    values.put(GrowFields.DESCRIPTION, "the money this token was saved for");
    values.put(GrowFields.PAYMENT_TYPE, "2");
    values.put(GrowFields.PAYMENT_NUM, "1");
    values.put(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "4001");
    values.put(GrowFields.FULL_NAME, "Dana Levi");
    values.put(GrowFields.PHONE, "0501234567");
    return values;
  }

  @Test
  void grow_never_asks_a_tester_for_the_credentials_it_signs_the_call_with() {
    assertThat(provider.originationFields().fieldsItFillsInItself())
        .containsOnlyKeys(GrowAccount.USER_ID, GrowAccount.PAGE_CODE, GrowFields.CHARGE_TYPE);
  }

  @Test
  void a_hold_a_tester_started_is_a_suspended_process_of_the_account_they_picked() {
    aConfiguredAccount();

    OriginationOutcome outcome = startedAs(A_HOLD, Map.of());

    GrowPaymentProcess process = theProcessOf(outcome);
    assertThat(process.chargeType()).isEqualTo(GrowFields.CHARGE_TYPE_SUSPENDED);
    assertThat(process.merchantId()).isEqualTo(MERCHANT);
    assertThat(process.origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(process.saveCardToken()).isFalse();
  }

  @Test
  void a_card_registration_a_tester_started_carries_the_save_flag_grow_would_otherwise_refuse() {
    aConfiguredAccount();

    GrowPaymentProcess process = theProcessOf(startedAs(A_SAVED_CARD, Map.of()));

    assertThat(process.chargeType()).isEqualTo(GrowFields.CHARGE_TYPE_TOKEN_ONLY);
    assertThat(process.saveCardToken()).isTrue();
  }

  @Test
  void a_hold_a_tester_asked_to_keep_the_card_on_keeps_it_and_holds_the_money_as_well() {
    aConfiguredAccount();

    GrowPaymentProcess process =
        theProcessOf(
            startedAs(A_HOLD, Map.of(GrowFields.SAVE_CARD_TOKEN, GrowFields.SAVED_CARD_TOKEN)));

    assertThat(process.chargeType()).isEqualTo(GrowFields.CHARGE_TYPE_SUSPENDED);
    assertThat(process.saveCardToken()).isTrue();
  }

  @Test
  void the_same_payment_started_by_a_harness_is_stored_as_the_harnesss_and_not_the_testers() {
    aConfiguredAccount();

    OriginationOutcome outcome =
        provider.originate(asked(A_HOLD, Map.of(), ObjectOrigin.CONTROL_API));

    assertThat(theProcessOf(outcome).origin()).isEqualTo(ObjectOrigin.CONTROL_API);
  }

  @Test
  void a_payment_started_on_an_account_this_run_no_longer_configures_names_no_such_account() {
    assertThat(startedAs(A_HOLD, Map.of())).isEqualTo(new OriginationOutcome.NoSuchAccount());
  }

  @Test
  void an_amount_grow_would_not_take_is_refused_in_grows_own_words_and_stores_no_process() {
    aConfiguredAccount();

    OriginationOutcome refused = startedAs(A_HOLD, Map.of(GrowFields.SUM, "0"));

    assertThat(refused)
        .isEqualTo(
            new OriginationOutcome.RefusedByTheProvider(
                GrowErrors.amountNotValid().id() + " " + GrowErrors.amountNotValid().message()));
    assertThat(processes.all()).isEmpty();
  }

  @Test
  void a_payment_a_scenario_rule_declined_carries_the_code_the_rule_named_and_that_rules_id() {
    aConfiguredAccount();
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(GrowOperations.CREATE_PAYMENT_PROCESS),
            new ScenarioAction.TerminalFailure("803"),
            null));

    OriginationOutcome refused = startedAs(A_HOLD, Map.of());

    assertThat(refused)
        .isInstanceOf(OriginationOutcome.RefusedByTheProvider.class)
        .extracting(outcome -> ((OriginationOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .asString()
        .startsWith("803");
    assertThat(refused.theRuleThatDecidedIt()).isNotNull();
  }

  @Test
  void a_payment_a_scenario_rule_answered_instead_of_running_starts_nothing_and_says_so() {
    aConfiguredAccount();
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(GrowOperations.CREATE_PAYMENT_PROCESS),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    OriginationOutcome outcome = startedAs(A_HOLD, Map.of());

    assertThat(outcome).isInstanceOf(OriginationOutcome.AnsweredByAScenarioInstead.class);
    assertThat(processes.all()).isEmpty();
  }

  private GrowPaymentProcess theProcessOf(OriginationOutcome outcome) {
    assertThat(outcome).isInstanceOf(OriginationOutcome.Originated.class);
    String processId = ((OriginationOutcome.Originated) outcome).providerObjectId();
    return processes.findByProcessId(RUN, processId).orElseThrow();
  }

  private OriginationOutcome startedAs(String way, Map<String, String> extra) {
    return provider.originate(asked(way, extra, ObjectOrigin.UI));
  }

  private static OriginationRequest asked(
      String way, Map<String, String> extra, ObjectOrigin askedBy) {
    if (A_SAVED_CARD_CHARGE.equals(way)) {
      return OriginationRequest.of(RUN, MERCHANT, way, extra, askedBy);
    }
    Map<String, String> values = new LinkedHashMap<>();
    values.put(GrowFields.SUM, "10.00");
    values.put(GrowFields.DESCRIPTION, "one seat, row F");
    values.put(GrowFields.SUCCESS_URL, "https://merchant.example/paid");
    values.put(GrowFields.CANCEL_URL, "https://merchant.example/gaveup");
    values.put(GrowFields.FULL_NAME, "Dana Levi");
    values.put(GrowFields.PHONE, "0501234567");
    values.putAll(extra);
    return OriginationRequest.of(RUN, MERCHANT, way, values, askedBy);
  }

  private void aConfiguredAccount() {
    accounts.merge(RUN, List.of(SimulatorDoubles.account(RUN, MERCHANT, "u-1", "p-1")));
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, "https://merchant.example/hook"));
  }
}
