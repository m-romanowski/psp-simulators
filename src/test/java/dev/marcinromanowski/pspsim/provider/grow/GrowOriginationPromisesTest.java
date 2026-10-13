package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GrowOriginationPromisesTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final Instant NOW = Instant.parse("2026-09-20T09:00:00Z");

  private static final String MERCHANT = "merchant-1";

  private static final String USER_ID = "grow-user";

  private static final String PAGE_CODE = "grow-page";

  private static final String CALLBACK = "https://merchant.example/hook";

  private static final String THE_CARD_THE_PAYER_NAMED = "sandbox-4580";

  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";

  private static final String CHARGE = "/api/light/server/1.0/createTransactionWithToken";

  private static final String A_SAVED_CARD_CHARGE = GrowOrigination.A_SAVED_CARD_CHARGE;

  private static final Pattern A_NUMBER_OF_OBJECTS =
      Pattern.compile("\\b(no|one|two|three|four)\\s+objects?\\b");

  private static final Pattern A_NAME_OF_THE_PRODUCTS_OWN =
      Pattern.compile("\\b[a-z][a-z0-9]*(?:[A-Z][A-Za-z0-9]*)+\\b");

  private static final Map<String, Integer> NUMBERS =
      Map.of("no", 0, "one", 1, "two", 2, "three", 3, "four", 4);

  private static final Set<String> THE_OPERATIONS_THIS_TEST_DRIVES =
      Set.of(
          GrowOperations.SETTLE_SUSPENDED_TRANSACTION,
          GrowOperations.CREATE_TRANSACTION_WITH_TOKEN);

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Runs testRuns = new SimulatorDoubles.Runs(DEFAULT_RUN);
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final InMemoryPaymentMethodFixtures seededMethods = new InMemoryPaymentMethodFixtures();
  private final SimulatorClock clock = SimulatorDoubles.clockAt(NOW);
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);

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

  private final GrowCallbacks callbacks =
      SimulatorDoubles.callbacks(processes, transactions, accounts, dispatcher, clock, ids);

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
          callbacks,
          clock,
          seededMethods,
          scenarioRules,
          SimulatorDoubles.heldResponses(),
          new SettleSuspendedTransaction(transactions, scenarioEngine, GrowScalarType.INTEGER),
          new RefundTransaction(transactions, ids, scenarioEngine, GrowScalarType.INTEGER),
          SimulatorDoubles.paymentProcessCreation(processes, scenarioRules, ids),
          SimulatorDoubles.storedCardCharge(processes, transactions, ids, scenarioRules),
          SimulatorDoubles.retraceOfAStoredCardCharge(processes, transactions, scenarioRules),
          new CompleteHostedProcess(processes, callbacks, new GrowPayerCards(seededMethods)));

  private record WhatAWayLeavesBehind(
      String name,
      String prose,
      List<Integer> objectsListed,
      Set<String> operationsThatApply,
      Set<NormalizedStatus> statusesReported) {}

  @Test
  void the_number_of_objects_the_wizard_promises_is_the_number_a_run_holds_at_each_stage() {
    for (WhatAWayLeavesBehind way : everyWayWalked()) {
      assertThat(theNumbersOfObjectsNarratedBy(way.prose()))
          .as(
              "%s tells a tester how many objects the Payments screen shows before and after a"
                  + " payer finishes, and the run answers %s",
              way.name(), way.objectsListed())
          .isEqualTo(way.objectsListed());
    }
  }

  @Test
  void every_way_of_starting_a_grow_payment_is_walked_by_one_of_these_sweeps_and_by_a_sweep() {
    assertThat(theWaysAPayerFinishesAPage()).isNotEmpty();
    assertThat(theWaysWithNoPayerPage())
        .as(
            "the walks below are written around what each group leaves, so a way in neither is a"
                + " way nothing here says anything about")
        .extracting(DeclaredOrigination::name)
        .containsExactly(A_SAVED_CARD_CHARGE);
    assertThat(theWaysAPayerFinishesAPage().size() + theWaysWithNoPayerPage().size())
        .isEqualTo(GrowOrigination.DECLARED.waysToStartAPayment().size());
  }

  @Test
  void the_number_of_objects_the_saved_card_charge_promises_is_the_number_it_adds_to_a_run() {
    TestRunId run = aRunFor(A_SAVED_CARD_CHARGE);
    String cardToken = theCardTokenMintedIn(run);
    int before = theObjectsListedIn(run).size();

    theSavedCardChargedIn(run, cardToken);

    assertThat(theNumbersOfObjectsNarratedBy(theChargeWay().whatItLeavesBehind()))
        .as(
            "a stored-card charge waits for nobody, so what it promises is the number of objects"
                + " it leaves behind and the run answers %s",
            theObjectsListedIn(run).size() - before)
        .containsExactly(theObjectsListedIn(run).size() - before);
  }

  @Test
  void
      the_operations_the_saved_card_charge_names_are_the_ones_its_transaction_admits_and_refuses() {
    TestRunId run = aRunFor(A_SAVED_CARD_CHARGE);
    String transactionId = theSavedCardChargedIn(run, theCardTokenMintedIn(run));

    ProviderObjectState charged = theStateOf(run, transactionId);

    assertThat(theOperationsNamedBy(theChargeWay().whatItLeavesBehind()))
        .contains(GrowOperations.CREATE_TRANSACTION_WITH_TOKEN);
    assertThat(charged.capturesAccepted())
        .as("it says settleSuspendedTransaction has nothing left to settle on this transaction")
        .isFalse();
    assertThat(charged.refundsAccepted())
        .as("it says refundTransaction is the one operation that still moves money on it")
        .isTrue();
    assertThat(charged.relatedObjects())
        .as("it says the transaction names the business and never the process that saved the card")
        .containsOnlyKeys(GrowProcessObject.MERCHANT_ID);
  }

  @Test
  void every_status_the_saved_card_charge_names_is_one_the_transaction_it_leaves_behind_reports() {
    TestRunId run = aRunFor(A_SAVED_CARD_CHARGE);
    String transactionId = theSavedCardChargedIn(run, theCardTokenMintedIn(run));

    assertThat(theStatusesNamedBy(theChargeWay().whatItLeavesBehind()))
        .isSubsetOf(Set.of(theStateOf(run, transactionId).normalizedStatus()));
  }

  @Test
  void the_wizard_tells_a_tester_the_two_objects_a_completion_leaves_name_each_other() {
    for (DeclaredOrigination page : theWaysAPayerFinishesAPage()) {
      String name = page.name();
      TestRunId run = aRunFor(name);
      String processId = theProcessStartedAs(name, run);
      String transactionId = theTransactionCompleting(run, processId);

      assertThat(theStateOf(run, processId).relatedObjects())
          .as("the process %s left behind names the transaction its completion created", name)
          .containsEntry(GrowProcessObject.TRANSACTION_ID, transactionId);
      assertThat(theStateOf(run, transactionId).relatedObjects())
          .as("the transaction %s left behind names the process it came from", name)
          .containsEntry(GrowFields.PROCESS_ID, processId);
    }
  }

  @Test
  void the_operation_each_way_names_is_one_that_acts_on_what_that_way_leaves_and_not_the_other() {
    List<WhatAWayLeavesBehind> walked = everyWayWalked();

    for (WhatAWayLeavesBehind way : walked) {
      Set<String> onlyThisWay = new LinkedHashSet<>(way.operationsThatApply());
      for (WhatAWayLeavesBehind other : walked) {
        if (!other.name().equals(way.name())) {
          onlyThisWay.removeAll(other.operationsThatApply());
        }
      }
      assertThat(onlyThisWay)
          .as("%s leaves behind something no other way of starting a payment does", way.name())
          .isNotEmpty();
      assertThat(theOperationsNamedBy(way.prose()))
          .as(
              "%s names the operation that acts on what it leaves behind and on nothing another"
                  + " way leaves",
              way.name())
          .containsAnyElementsOf(onlyThisWay);
    }
  }

  @Test
  void every_status_the_wizard_names_is_one_the_objects_that_way_leaves_behind_report() {
    for (WhatAWayLeavesBehind way : everyWayWalked()) {
      assertThat(theStatusesNamedBy(way.prose()))
          .as("%s names only statuses the objects it leaves behind report", way.name())
          .isSubsetOf(way.statusesReported());
    }
  }

  @Test
  void every_name_of_its_own_the_wizard_uses_is_one_this_product_answers_to() {
    for (DeclaredOrigination way : GrowOrigination.DECLARED.waysToStartAPayment()) {
      assertThat(theNamesOfTheProductsOwnIn(way.whatItLeavesBehind()))
          .as("%s spells a name a tester could look for and find", way.name())
          .isSubsetOf(theNamesThisProductAnswersTo());
    }
  }

  private List<WhatAWayLeavesBehind> everyWayWalked() {
    List<WhatAWayLeavesBehind> walked = new ArrayList<>();
    for (DeclaredOrigination declared : theWaysAPayerFinishesAPage()) {
      walked.add(theWayWalked(declared));
    }
    return walked;
  }

  private static List<DeclaredOrigination> theWaysAPayerFinishesAPage() {
    List<DeclaredOrigination> pages = new ArrayList<>();
    for (DeclaredOrigination way : GrowOrigination.DECLARED.waysToStartAPayment()) {
      if (way.thePayerStep() instanceof ThePayerStep.APageThisSimulatorServes) {
        pages.add(way);
      }
    }
    return List.copyOf(pages);
  }

  private static List<DeclaredOrigination> theWaysWithNoPayerPage() {
    List<DeclaredOrigination> serverToServer = new ArrayList<>();
    for (DeclaredOrigination way : GrowOrigination.DECLARED.waysToStartAPayment()) {
      if (way.thePayerStep() instanceof ThePayerStep.NoPageThisSimulatorServes) {
        serverToServer.add(way);
      }
    }
    return List.copyOf(serverToServer);
  }

  private static DeclaredOrigination theChargeWay() {
    return GrowOrigination.DECLARED.wayNamed(A_SAVED_CARD_CHARGE).orElseThrow();
  }

  private String theCardTokenMintedIn(TestRunId run) {
    String processId = theProcessStartedAs(GrowOrigination.A_SAVED_CARD, run);
    theTransactionCompleting(run, processId);
    GrowProcessOutcome outcome = processes.findByProcessId(run, processId).orElseThrow().outcome();
    assertThat(outcome).isNotNull();
    return String.valueOf(outcome.cardToken());
  }

  private String theSavedCardChargedIn(TestRunId run, String cardToken) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(GrowFields.CARD_TOKEN, cardToken);
    values.put(GrowFields.SUM, "41.50");
    values.put(GrowFields.DESCRIPTION, "the money this token was saved for");
    values.put(GrowFields.PAYMENT_TYPE, "2");
    values.put(GrowFields.PAYMENT_NUM, "1");
    values.put(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "4001");
    values.put(GrowFields.FULL_NAME, "Dana Levi");
    values.put(GrowFields.PHONE, "0501234567");
    OriginationOutcome outcome =
        provider.originate(
            OriginationRequest.of(run, MERCHANT, A_SAVED_CARD_CHARGE, values, ObjectOrigin.UI));

    assertThat(outcome).isInstanceOf(OriginationOutcome.Originated.class);
    return ((OriginationOutcome.Originated) outcome).providerObjectId();
  }

  private WhatAWayLeavesBehind theWayWalked(DeclaredOrigination declared) {
    TestRunId run = aRunFor(declared.name());
    String processId = theProcessStartedAs(declared.name(), run);
    List<Integer> listed = new ArrayList<>();
    listed.add(theObjectsListedIn(run).size());
    String transactionId = theTransactionCompleting(run, processId);
    listed.add(theObjectsListedIn(run).size());

    Set<NormalizedStatus> reported = new LinkedHashSet<>();
    for (ProviderObjectState object : theObjectsListedIn(run)) {
      reported.add(object.normalizedStatus());
    }
    return new WhatAWayLeavesBehind(
        declared.name(),
        declared.whatItLeavesBehind(),
        List.copyOf(listed),
        theOperationsThatApplyTo(run, processId, transactionId),
        Set.copyOf(reported));
  }

  private Set<String> theOperationsThatApplyTo(
      TestRunId run, String processId, String transactionId) {
    Set<String> applies = new LinkedHashSet<>();
    GrowTransaction transaction =
        transactions.findByTransactionId(run, transactionId).orElseThrow();
    if (settle(run, transaction).outcome() == ProviderOutcome.SUCCESS) {
      applies.add(GrowOperations.SETTLE_SUSPENDED_TRANSACTION);
    }
    GrowProcessOutcome outcome = processes.findByProcessId(run, processId).orElseThrow().outcome();
    String cardToken = outcome == null ? null : outcome.cardToken();
    if (cardToken != null && charge(run, cardToken).outcome() == ProviderOutcome.SUCCESS) {
      applies.add(GrowOperations.CREATE_TRANSACTION_WITH_TOKEN);
    }
    return Set.copyOf(applies);
  }

  private TestRunId aRunFor(String way) {
    TestRunId run = theRunFor(way);
    accounts.merge(
        run,
        List.of(SimulatorDoubles.account(run, MERCHANT, theUserIdOf(run), thePageCodeOf(run))));
    accounts.withTarget(run, new WebhookTarget(MERCHANT, CALLBACK));
    testRuns.register(run);
    return run;
  }

  private static TestRunId theRunFor(String way) {
    return TestRunId.of("grow-wizard-" + Integer.toHexString(way.hashCode()));
  }

  private static String theUserIdOf(TestRunId run) {
    return USER_ID + "-" + run.value();
  }

  private static String thePageCodeOf(TestRunId run) {
    return PAGE_CODE + "-" + run.value();
  }

  private String theProcessStartedAs(String way, TestRunId run) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(GrowFields.SUM, "120.50");
    values.put(GrowFields.DESCRIPTION, "one seat, row F");
    values.put(GrowFields.SUCCESS_URL, "https://merchant.example/paid");
    values.put(GrowFields.CANCEL_URL, "https://merchant.example/abandoned");
    values.put(GrowFields.FULL_NAME, "Dana Levi");
    values.put(GrowFields.PHONE, "0501234567");
    OriginationOutcome outcome =
        provider.originate(OriginationRequest.of(run, MERCHANT, way, values, ObjectOrigin.UI));

    assertThat(outcome).isInstanceOf(OriginationOutcome.Originated.class);
    return ((OriginationOutcome.Originated) outcome).providerObjectId();
  }

  private String theTransactionCompleting(TestRunId run, String processId) {
    HostedCompletionOutcome answered =
        provider.complete(
            new HostedCompletionRequest(
                run, processId, PayerOutcome.PAID, THE_CARD_THE_PAYER_NAMED, ObjectOrigin.UI));

    assertThat(answered).isInstanceOf(HostedCompletionOutcome.Completed.class);
    String created = ((HostedCompletionOutcome.Completed) answered).createdObjectId();
    assertThat(created).isNotNull();
    return created;
  }

  private List<ProviderObjectState> theObjectsListedIn(TestRunId run) {
    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(run, null, 50));
    return ((ProviderObjectPage.Listed) page).objects();
  }

  private ProviderObjectState theStateOf(TestRunId run, String providerObjectId) {
    return provider.state(run, providerObjectId).orElseThrow();
  }

  private GrowApiResponse settle(TestRunId run, GrowTransaction transaction) {
    Map<String, String> settlement = new LinkedHashMap<>();
    settlement.put(GrowFields.USER_ID, theUserIdOf(run));
    settlement.put(GrowFields.TRANSACTION_ID, transaction.transactionId());
    settlement.put(GrowFields.TRANSACTION_TOKEN, String.valueOf(transaction.transactionToken()));
    settlement.put(GrowFields.SUM, transaction.amount().value());
    return api.settleSuspendedTransaction(call(SETTLE, settlement));
  }

  private GrowApiResponse charge(TestRunId run, String cardToken) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(GrowFields.CARD_TOKEN, cardToken);
    form.put(GrowFields.USER_ID, theUserIdOf(run));
    form.put(GrowFields.SUM, "120.50");
    form.put(GrowFields.DESCRIPTION, "the money this token was saved for");
    form.put(GrowFields.PAYMENT_TYPE, "2");
    form.put(GrowFields.PAYMENT_NUM, "1");
    form.put(GrowFields.TRANSACTION_UNIQUE_IDENTIFIER, "1001");
    form.put(GrowFields.FULL_NAME, "Dana Levi");
    form.put(GrowFields.PHONE, "0501234567");
    return api.createTransactionWithToken(call(CHARGE, form));
  }

  private static GrowApiCall call(String path, Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return new GrowApiCall(
        "POST", path, Map.of("Content-Type", List.of("multipart/form-data; boundary=x")), fields);
  }

  private static List<Integer> theNumbersOfObjectsNarratedBy(String prose) {
    List<Integer> narrated = new ArrayList<>();
    Matcher numbers = A_NUMBER_OF_OBJECTS.matcher(prose);
    while (numbers.find()) {
      narrated.add(NUMBERS.get(numbers.group(1)));
    }
    return List.copyOf(narrated);
  }

  private static Set<String> theOperationsNamedBy(String prose) {
    Set<String> named = new LinkedHashSet<>();
    for (String operation : THE_OPERATIONS_THIS_TEST_DRIVES) {
      if (prose.contains(operation)) {
        named.add(operation);
      }
    }
    return Set.copyOf(named);
  }

  private static Set<NormalizedStatus> theStatusesNamedBy(String prose) {
    Set<NormalizedStatus> named = new LinkedHashSet<>();
    for (NormalizedStatus status : NormalizedStatus.values()) {
      if (prose.contains(status.name())) {
        named.add(status);
      }
    }
    return Set.copyOf(named);
  }

  private static Set<String> theNamesOfTheProductsOwnIn(String prose) {
    Set<String> spelled = new LinkedHashSet<>();
    Matcher names = A_NAME_OF_THE_PRODUCTS_OWN.matcher(prose);
    while (names.find()) {
      spelled.add(names.group());
    }
    return Set.copyOf(spelled);
  }

  private static Set<String> theNamesThisProductAnswersTo() {
    Set<String> answered = new LinkedHashSet<>();
    for (ProviderOperation operation : GrowOperations.VOCABULARY.operations()) {
      answered.add(operation.name());
    }
    for (Field field : GrowFields.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
        answered.add(theValueOf(field));
      }
    }
    for (RecordComponent component : ProviderObjectState.class.getRecordComponents()) {
      answered.add(component.getName());
    }
    return Set.copyOf(answered);
  }

  private static String theValueOf(Field field) {
    try {
      field.setAccessible(true);
      return String.valueOf(field.get(null));
    } catch (IllegalAccessException refused) {
      throw new AssertionError(field.getName(), refused);
    }
  }
}
