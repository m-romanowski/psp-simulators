package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrowKeyInspectorTest {

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant NOW = Instant.parse("2026-11-28T09:15:00Z");

  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String MERCHANT = "merchant-1";
  private static final String NOTIFY_URL = "https://merchant.example/callbacks/grow";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String CHARGE = "/api/light/server/1.0/createTransactionWithToken";
  private static final String LOOK_UP = "/api/light/server/1.0/getTransactionInfo";

  private static final String CHARGED = "89.90";
  private static final String UNIQUE = "1001";
  private static final String ANOTHER_UNIQUE = "1002";
  private static final String YET_ANOTHER_UNIQUE = "1003";
  private static final String GROUP = "order-77";
  private static final int DUPLICATE_ERROR = 712;

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(DEFAULT_RUN);
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
  private final SimulatorClock clock = SimulatorDoubles.clockAt(NOW);

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          new InMemoryWebhookDeliveries(),
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          clock,
          ids,
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new GrowCallbackAcknowledgement(accounts)),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final GrowLightApi api =
      new GrowLightApi(
          accounts,
          testRuns,
          audit,
          clock,
          ids,
          DEFAULT_RUN,
          new CreatePaymentProcess(
              processes, ids, "https://simulator.internal", scenarioEngine, GrowScalarType.INTEGER),
          new CreateTransactionWithToken(
              processes, transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
          new ApproveTransaction(processes, dispatcher, scenarioEngine, GrowScalarType.INTEGER),
          new SettleSuspendedTransaction(transactions, scenarioEngine, GrowScalarType.INTEGER),
          new RefundTransaction(transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
          new GetTransactionInfo(transactions, processes, scenarioEngine, GrowScalarType.INTEGER),
          new GetPaymentProcessInfo(
              processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
          new GetTokenTransactionsByExternalIdentifiers(
              processes, transactions, scenarioEngine, GrowScalarType.INTEGER),
          new CancelBitTransaction(GrowScalarType.INTEGER),
          new UpdateMyUrl(dispatcher, GrowScalarType.INTEGER));

  private final SimulatorProvider provider =
      new GrowProvider(
          accounts,
          audit,
          processes,
          transactions,
          dispatcher,
          SimulatorDoubles.callbacks(processes, transactions, accounts, dispatcher, clock, ids),
          clock,
          new InMemoryPaymentMethodFixtures(),
          scenarioRules,
          SimulatorDoubles.heldResponses(),
          new SettleSuspendedTransaction(
              transactions, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          new RefundTransaction(
              transactions, ids, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          SimulatorDoubles.paymentProcessCreation(processes, scenarioRules, ids),
          SimulatorDoubles.storedCardCharge(processes, transactions, ids, scenarioRules),
          SimulatorDoubles.retraceOfAStoredCardCharge(processes, transactions, scenarioRules),
          SimulatorDoubles.completions(processes));

  @BeforeEach
  void configureTheBusiness() {
    accounts.with(SimulatorDoubles.account(RUN, MERCHANT, USER_ID, PAGE_CODE, Map.of()));
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, NOTIFY_URL));
    testRuns.register(RUN);
  }

  @Test
  void a_charge_repeated_under_one_unique_identifier_is_shown_refused_and_never_replayed() {
    String cardToken = aSavedCard();
    GrowApiResponse first = charge(aChargeOf(cardToken, UNIQUE, null));

    GrowApiResponse repeated = charge(aChargeOf(cardToken, UNIQUE, null));

    assertThat(first.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(repeated.outcome()).isEqualTo(ProviderOutcome.FAILURE);

    KeyedExchange exchange = theExchangeUnder(GrowKeyMechanisms.UNIQUE_IDENTIFIER, UNIQUE);
    assertThat(exchange.role()).isEqualTo(KeyRole.DUPLICATE_DETECTION_KEY);
    assertThat(exchange.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
    assertThat(exchange.replayTimestamps()).isEmpty();

    KeyedRequest refusal = exchange.repeats().get(0);
    assertThat(refusal.body()).contains(String.valueOf(DUPLICATE_ERROR));
    assertThat(refusal.body()).isNotEqualTo(exchange.firstRequest().body());
    assertThat(refusal.stateChanged()).isFalse();
    assertThat(exchange.firstRequest().stateChanged()).isTrue();
  }

  @Test
  void a_group_repeated_after_a_failed_payment_is_admitted_and_after_a_paid_one_is_refused() {
    String cardToken = aSavedCard();
    declineTheNextCharge();
    GrowApiResponse declined = charge(aChargeOf(cardToken, UNIQUE, GROUP));
    stopDecliningCharges();

    GrowApiResponse afterTheFailure = charge(aChargeOf(cardToken, ANOTHER_UNIQUE, GROUP));
    GrowApiResponse afterTheSuccess = charge(aChargeOf(cardToken, YET_ANOTHER_UNIQUE, GROUP));

    assertThat(declined.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(afterTheFailure.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(afterTheSuccess.outcome()).isEqualTo(ProviderOutcome.FAILURE);

    KeyedExchange group = theExchangeUnder(GrowKeyMechanisms.GROUP_IDENTIFIER, GROUP);
    assertThat(group.role()).isEqualTo(KeyRole.DUPLICATE_DETECTION_KEY);
    assertThat(group.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(
            ExchangeVerdict.FIRST_REQUEST,
            ExchangeVerdict.REPEAT_ADMITTED,
            ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
    assertThat(group.replayTimestamps()).isEmpty();
    assertThat(group.repeats().get(0).stateChanged()).isTrue();
    assertThat(group.repeats().get(1).stateChanged()).isFalse();
    assertThat(group.repeats().get(1).body()).contains(String.valueOf(DUPLICATE_ERROR));
  }

  @Test
  void the_three_unique_identifiers_of_one_group_stay_three_rows_of_their_own() {
    String cardToken = aSavedCard();
    declineTheNextCharge();
    charge(aChargeOf(cardToken, UNIQUE, GROUP));
    stopDecliningCharges();
    charge(aChargeOf(cardToken, ANOTHER_UNIQUE, GROUP));

    assertThat(theExchangeUnder(GrowKeyMechanisms.UNIQUE_IDENTIFIER, UNIQUE).requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST);
    assertThat(theExchangeUnder(GrowKeyMechanisms.UNIQUE_IDENTIFIER, ANOTHER_UNIQUE).requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST);
  }

  @Test
  void a_charge_that_names_no_group_leaves_the_inspector_with_one_row_and_not_two() {
    String cardToken = aSavedCard();

    charge(aChargeOf(cardToken, UNIQUE, null));

    assertThat(exchanges())
        .extracting(KeyedExchange::keyName)
        .containsExactly(GrowKeyMechanisms.UNIQUE_IDENTIFIER);
  }

  @Test
  void looking_a_transaction_up_twice_is_a_correlated_retry_and_neither_a_replay_nor_a_refusal() {
    String cardToken = aSavedCard();
    charge(aChargeOf(cardToken, UNIQUE, null));
    GrowTransaction charged = theOnlyStoredCardCharge();

    lookUp(charged);
    lookUp(charged);

    KeyedExchange exchange =
        theExchangeUnder(GrowKeyMechanisms.TRANSACTION_ID, charged.transactionId());
    assertThat(exchange.role()).isEqualTo(KeyRole.CORRELATION_KEY);
    assertThat(exchange.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.CORRELATED_RETRY);
    assertThat(exchange.replayTimestamps()).isEmpty();
    assertThat(exchange.stateChanged()).isFalse();
    assertThat(exchange.operation()).isEqualTo(GrowOperations.GET_TRANSACTION_INFO);
  }

  @Test
  void provider_grow_says_it_has_no_idempotency_key_rather_than_showing_that_row_empty() {
    assertThat(provider.keyMechanisms()).isSameAs(GrowKeyMechanisms.MECHANISMS);
    assertThat(GrowKeyMechanisms.MECHANISMS.has(KeyRole.TRUE_IDEMPOTENCY_KEY)).isFalse();
    assertThat(GrowKeyMechanisms.MECHANISMS.theAbsenceOf(KeyRole.TRUE_IDEMPOTENCY_KEY))
        .contains("publishes no idempotency header")
        .contains("duplicate checks whose repeat is refused");
    assertThat(GrowKeyMechanisms.MECHANISMS.inTheRoleOf(KeyRole.DUPLICATE_DETECTION_KEY))
        .extracting(DeclaredKey::name)
        .containsExactly(GrowKeyMechanisms.UNIQUE_IDENTIFIER, GrowKeyMechanisms.GROUP_IDENTIFIER);
  }

  @Test
  void the_two_duplicate_checks_say_in_grows_own_words_how_their_scopes_differ() {
    String unique =
        GrowKeyMechanisms.MECHANISMS
            .key(GrowKeyMechanisms.UNIQUE_IDENTIFIER)
            .orElseThrow()
            .scopedBy();
    String group =
        GrowKeyMechanisms.MECHANISMS
            .key(GrowKeyMechanisms.GROUP_IDENTIFIER)
            .orElseThrow()
            .scopedBy();

    assertThat(unique).contains("successful or failed");
    assertThat(group).contains("only the successful payments").contains("not correlation data");
  }

  private KeyedExchange theExchangeUnder(String keyName, String keyValue) {
    return exchanges().stream()
        .filter(found -> found.keyName().equals(keyName) && found.keyValue().equals(keyValue))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no exchange under " + keyName + " " + keyValue));
  }

  private List<KeyedExchange> exchanges() {
    return provider
        .keyedExchanges(new KeyedExchangeQuery(RUN, KeyedExchangeQuery.FROM_THE_NEWEST, 50))
        .exchanges();
  }

  private void declineTheNextCharge() {
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(GrowOperations.CREATE_TRANSACTION_WITH_TOKEN),
            new ScenarioAction.TerminalFailure("stolenCard"),
            null));
  }

  private void stopDecliningCharges() {
    scenarioRules.clearAll(GrowLightApi.GROW);
  }

  private String aSavedCard() {
    String processId = aTokenOnlyProcess();
    provider.schedule(
        new WebhookScheduleRequest(RUN, processId, null, null, null, ObjectOrigin.CONTROL_API));
    GrowPaymentProcess registered =
        processes.findByProcessId(RUN, processId).orElseThrow(AssertionError::new);
    GrowProcessOutcome outcome = registered.outcome();
    assertThat(outcome).isNotNull();
    assertThat(registered.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    return String.valueOf(outcome.cardToken());
  }

  private String aTokenOnlyProcess() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.PAGE_CODE, PAGE_CODE);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.CHARGE_TYPE, GrowFields.CHARGE_TYPE_TOKEN_ONLY);
    form.put(GrowFields.SUM, "1");
    form.put(GrowFields.SAVE_CARD_TOKEN, "1");
    form.put(GrowFields.DESCRIPTION, "A card registration");
    form.put(GrowFields.SUCCESS_URL, "https://merchant.example/thanks");
    form.put(GrowFields.CANCEL_URL, "https://merchant.example/cancel");
    form.put(GrowFields.NOTIFY_URL, NOTIFY_URL);
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    GrowApiResponse created = api.createPaymentProcess(call(CREATE, form));
    assertThat(created.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    return String.valueOf(created.providerObjectId());
  }

  private GrowTransaction theOnlyStoredCardCharge() {
    List<GrowTransaction> charges =
        transactions.all().stream()
            .filter(held -> held.kind() == GrowTransactionKind.TOKEN_PAYMENT)
            .toList();
    assertThat(charges).hasSize(1);
    return charges.get(0);
  }

  private GrowApiResponse lookUp(GrowTransaction transaction) {
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put(GrowFields.PAGE_CODE, PAGE_CODE);
    fields.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    fields.put(GrowFields.TRANSACTION_TOKEN, String.valueOf(transaction.transactionToken()));
    return api.getTransactionInfo(call(LOOK_UP, fields));
  }

  private GrowApiResponse charge(Map<String, String> form) {
    return api.createTransactionWithToken(call(CHARGE, form));
  }

  private static Map<String, String> aChargeOf(
      String cardToken, String uniqueIdentifier, String groupIdentifier) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.CARD_TOKEN, cardToken);
    form.put(GrowFields.USER_ID, USER_ID);
    form.put(GrowFields.SUM, CHARGED);
    form.put(GrowFields.DESCRIPTION, "A stored-card charge");
    form.put(GrowFields.PAYMENT_TYPE, "2");
    form.put(GrowFields.PAYMENT_NUM, "1");
    form.put(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, uniqueIdentifier);
    if (groupIdentifier != null) {
      form.put(GrowFields.TRANSACTION_GROUP_IDENTIFIER, groupIdentifier);
    }
    form.put(GrowFields.FULL_NAME, "Israel Israeli");
    form.put(GrowFields.PHONE, "0505503144");
    form.put(GrowFields.EMAIL, "payer@merchant.example");
    return form;
  }

  private static GrowApiCall call(String path, Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return new GrowApiCall(
        "POST", path, Map.of("Content-Type", List.of("multipart/form-data; boundary=x")), fields);
  }

  @Test
  void provider_grow_declares_every_key_it_has_as_one_no_request_can_ever_find_held() {
    assertThat(
            GrowKeyMechanisms.MECHANISMS.claimOf(GrowKeyMechanisms.UNIQUE_IDENTIFIER).canBeHeld())
        .isFalse();
    assertThat(GrowKeyMechanisms.MECHANISMS.claimOf(GrowKeyMechanisms.GROUP_IDENTIFIER).canBeHeld())
        .isFalse();
    assertThat(GrowKeyMechanisms.MECHANISMS.claimOf(GrowKeyMechanisms.TRANSACTION_ID).canBeHeld())
        .isFalse();
  }
}
