package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtureRemoval;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AccountProperty;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PlacedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPages;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

final class GrowProvider implements SimulatorProvider {

  @Override
  public List<ContractCheck> compatibilityChecks(CheckJson json, IdGenerator ids) {
    return new GrowCompatibilityChecks(json).all();
  }

  private final GrowAccounts accounts;
  private final AuditTrail audit;
  private final GrowPaymentProcesses processes;
  private final GrowTransactions transactions;
  private final WebhookDispatcher callbackDeliveries;
  private final GrowCallbacks callbacks;
  private final CompleteHostedProcess completions;
  private final SimulatorClock clock;
  private final PaymentMethodFixtures fixtures;
  private final ScenarioRules scenarios;
  private final DelayedDeliveries heldResponses;
  private final SettleSuspendedTransaction settleSuspendedTransaction;
  private final RefundTransaction refundTransaction;
  private final CreatePaymentProcess createPaymentProcess;
  private final CreateTransactionWithToken createTransactionWithToken;
  private final GetTokenTransactionsByExternalIdentifiers retraceTheChargeOfASavedCard;

  GrowProvider(
      GrowAccounts accounts,
      AuditTrail audit,
      GrowPaymentProcesses processes,
      GrowTransactions transactions,
      WebhookDispatcher callbackDeliveries,
      GrowCallbacks callbacks,
      SimulatorClock clock,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SettleSuspendedTransaction settleSuspendedTransaction,
      RefundTransaction refundTransaction,
      CreatePaymentProcess createPaymentProcess,
      CreateTransactionWithToken createTransactionWithToken,
      GetTokenTransactionsByExternalIdentifiers retraceTheChargeOfASavedCard,
      CompleteHostedProcess completions) {
    this.settleSuspendedTransaction = settleSuspendedTransaction;
    this.refundTransaction = refundTransaction;
    this.createPaymentProcess = createPaymentProcess;
    this.createTransactionWithToken = createTransactionWithToken;
    this.retraceTheChargeOfASavedCard = retraceTheChargeOfASavedCard;
    this.accounts = accounts;
    this.audit = audit;
    this.processes = processes;
    this.transactions = transactions;
    this.callbackDeliveries = callbackDeliveries;
    this.callbacks = callbacks;
    this.completions = completions;
    this.clock = clock;
    this.fixtures = fixtures;
    this.scenarios = scenarios;
    this.heldResponses = heldResponses;
  }

  @Override
  public ProviderId id() {
    return GrowLightApi.GROW;
  }

  @Override
  public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
    TestRunId testRunId = configuration.testRunId();
    List<ConfiguredAccount> declared = configuration.accounts();
    List<GrowAccount> parsed = new ArrayList<>();
    Set<String> userIds = new LinkedHashSet<>();
    Set<String> pageCodes = new LinkedHashSet<>();
    Set<String> rewritten = new LinkedHashSet<>();
    for (ConfiguredAccount named : declared) {
      rewritten.add(named.accountId());
    }
    for (int index = 0; index < declared.size(); index++) {
      String property = AccountProperty.prefixOf(index);
      GrowAccount account = GrowAccount.of(property, testRunId, declared.get(index));
      if (!userIds.add(account.userId())) {
        throw new ProviderConfigurationRejectedException(
            property
                + GrowAccount.USER_ID
                + " is already configured for another account in this test run");
      }
      if (!pageCodes.add(account.pageCode())) {
        throw new ProviderConfigurationRejectedException(
            property
                + GrowAccount.PAGE_CODE
                + " is already configured for another account in this test run");
      }
      rejectACredentialAnAccountThisCallLeavesInPlaceHolds(property, account, rewritten);
      parsed.add(account);
    }
    List<GrowAccount> itWillKeep = List.copyOf(parsed);
    return new ProviderConfigurationPlan(
        GrowAccount.CONFIGURATION_KEYS, List.of(), () -> accounts.merge(testRunId, itWillKeep));
  }

  private void rejectACredentialAnAccountThisCallLeavesInPlaceHolds(
      String property, GrowAccount account, Set<String> rewritten) {
    for (GrowAccount held :
        accounts.ofTheRunHoldingEither(account.testRunId(), account.userId(), account.pageCode())) {
      if (rewritten.contains(held.accountId())) {
        continue;
      }
      throw new ProviderConfigurationRejectedException(
          property
              + (held.userId().equals(account.userId())
                  ? GrowAccount.USER_ID
                  : GrowAccount.PAGE_CODE)
              + " is already configured for another account in this test run");
    }
  }

  @Override
  public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
    return new AccountRemovalPlan(() -> accounts.remove(testRunId, accountId));
  }

  @Override
  public ProviderVocabulary vocabulary() {
    return GrowOperations.VOCABULARY;
  }

  @Override
  public ProviderWireRoutes wireRoutes() {
    return GrowWireFaults.ROUTES;
  }

  @Override
  public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
    return GrowWireFaults.THE_ANSWER;
  }

  @Override
  public ProviderAccountFields accountFields() {
    return GrowAccount.ACCOUNT_FIELDS;
  }

  @Override
  public ProviderPaymentMethodFields paymentMethodFields() {
    return GrowFixtureCards.PAYMENT_METHOD_FIELDS;
  }

  @Override
  public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
    return PaymentMethodFixturePlan.storingNothingOfItsOwn();
  }

  @Override
  public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
    return new PaymentMethodFixtureRemoval(() -> {});
  }

  @Override
  public ProviderKeyMechanisms keyMechanisms() {
    return GrowKeyMechanisms.MECHANISMS;
  }

  @Override
  public ProviderPairing pairing() {
    return GrowPairing.PAIRING;
  }

  @Override
  public ProviderOriginationFields originationFields() {
    return GrowOrigination.DECLARED;
  }

  @Override
  public OriginationOutcome originate(OriginationRequest request) {
    Optional<GrowAccount> account = accounts.find(request.testRunId(), request.accountId());
    if (account.isEmpty()) {
      return new OriginationOutcome.NoSuchAccount();
    }
    return GrowOrigination.outcomeOf(
        GrowOrigination.chargesACardAHostedPageSaved(request)
            ? theSavedCardChargedBy(request, account.get())
            : theHostedProcessStartedBy(request, account.get()),
        request);
  }

  private GrowOperationResult theHostedProcessStartedBy(
      OriginationRequest request, GrowAccount account) {
    return createPaymentProcess.handle(
        GrowOrigination.theHostedProcessFormOf(request, account),
        Map.of(),
        request.testRunId(),
        List.of(account),
        clock.now(request.testRunId()),
        request.askedBy());
  }

  private GrowOperationResult theSavedCardChargedBy(
      OriginationRequest request, GrowAccount account) {
    return createTransactionWithToken.handle(
        GrowOrigination.theSavedCardChargeFormOf(request, account),
        Map.of(),
        request.testRunId(),
        List.of(account),
        clock.now(request.testRunId()),
        request.askedBy());
  }

  @Override
  public ProviderReads reads() {
    return GrowReads.DECLARED;
  }

  @Override
  public ReadOutcome read(ReadRequest request) {
    Optional<GrowAccount> account = accounts.find(request.testRunId(), request.accountId());
    if (account.isEmpty()) {
      return new ReadOutcome.NoSuchAccount();
    }
    return GrowReads.outcomeOf(
        retraceTheChargeOfASavedCard.handle(
            GrowReads.theRetraceFormOf(request, account.get()),
            Map.of(),
            request.testRunId(),
            List.of(account.get())));
  }

  @Override
  public List<AValueAFieldOffers> theValuesThisTestRunHasAlreadyProducedFor(
      TestRunId testRunId, String accountId, String fieldKey) {
    if (!GrowFields.CARD_TOKEN.equals(fieldKey)) {
      return SimulatorProvider.super.theValuesThisTestRunHasAlreadyProducedFor(
          testRunId, accountId, fieldKey);
    }
    return GrowOrigination.theSavedCardsThisRunHolds(
        processes.theProcessesThatSavedACard(testRunId, accountId));
  }

  @Override
  public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
    return transactions.exchanges(query);
  }

  @Override
  public ProviderPersonas personas(IdGenerator ids) {
    return GrowPersonas.of();
  }

  @Override
  public PresetExpansion expand(PresetRequest request) {
    return GrowPresets.of(request);
  }

  @Override
  public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
    if (scope == ResetScope.ALL) {
      heldResponses.cancelAll(GrowLightApi.GROW);
      return new ResetOutcome(
          processes.clearAll()
              + transactions.clearAll()
              + fixtures.clearAll(GrowLightApi.GROW)
              + callbackDeliveries.clearAll(GrowLightApi.GROW)
              + scenarios.clearAll(GrowLightApi.GROW),
          audit.clearAll(GrowLightApi.GROW));
    }
    heldResponses.cancel(GrowLightApi.GROW, testRunId);
    return new ResetOutcome(
        processes.clear(testRunId)
            + transactions.clear(testRunId)
            + fixtures.clear(GrowLightApi.GROW, testRunId)
            + callbackDeliveries.clear(GrowLightApi.GROW, testRunId)
            + scenarios.clear(GrowLightApi.GROW, testRunId),
        audit.clear(GrowLightApi.GROW, testRunId));
  }

  @Override
  public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
    Optional<GrowTransaction> transaction =
        transactions.findByTransactionId(testRunId, providerObjectId);
    Optional<GrowPaymentProcess> process = processes.findByProcessId(testRunId, providerObjectId);
    if (transaction.isPresent() && process.isPresent()) {
      throw new IllegalStateException(GrowObjectId.ONE_ID_NAMES_TWO_OBJECTS);
    }
    if (transaction.isPresent()) {
      return transaction.map(row -> stateOf(row, clock.now(testRunId)));
    }
    return process.map(GrowProcessObject::stateOf);
  }

  @Override
  public List<ProviderObjectState> theObjectsItHoldsUnder(
      TestRunId testRunId, List<String> providerObjectIds) {
    List<String> asked = List.copyOf(providerObjectIds);
    List<GrowTransaction> transactionsItHolds = transactions.allByTransactionId(testRunId, asked);
    List<GrowPaymentProcess> processesItHolds = processes.allByProcessId(testRunId, asked);
    refuseAnIdentifierNamingBothAProcessAndATransaction(transactionsItHolds, processesItHolds);
    Map<String, GrowAccount> theAccountsTheyWereTakenOn =
        theAccountsTheyWereTakenOn(
            testRunId, transactionsItHolds.stream().map(GrowTransaction::merchantId).toList());
    Instant now = clock.now(testRunId);
    List<ProviderObjectState> held = new ArrayList<>();
    transactionsItHolds.forEach(
        transaction ->
            held.add(
                stateOf(
                    transaction, now, theAccountsTheyWereTakenOn.get(transaction.merchantId()))));
    processesItHolds.forEach(process -> held.add(GrowProcessObject.stateOf(process)));
    return List.copyOf(held);
  }

  private static void refuseAnIdentifierNamingBothAProcessAndATransaction(
      List<GrowTransaction> transactionsItHolds, List<GrowPaymentProcess> processesItHolds) {
    Set<String> theProcessesItHolds =
        processesItHolds.stream().map(GrowPaymentProcess::processId).collect(Collectors.toSet());
    if (transactionsItHolds.stream()
        .anyMatch(transaction -> theProcessesItHolds.contains(transaction.transactionId()))) {
      throw new IllegalStateException(GrowObjectId.ONE_ID_NAMES_TWO_OBJECTS);
    }
  }

  @Override
  public ProviderObjectPage objects(ProviderObjectQuery query) {
    TestRunId testRunId = query.testRunId();
    String cursor = query.after();
    ProviderObjectCursor after = null;
    if (cursor != null) {
      after =
          transactions
              .placeOf(testRunId, cursor)
              .or(() -> processes.placeOf(testRunId, cursor))
              .orElse(null);
      if (after == null) {
        return new ProviderObjectPage.NoSuchCursor();
      }
    }
    Instant now = clock.now(testRunId);
    List<PlacedProviderObject> placed = new ArrayList<>();
    List<ArrivalOf<GrowTransaction>> arrivedTransactions =
        transactions.newestFirst(testRunId, after, query.limit());
    Map<String, GrowAccount> theAccountsTheyWereTakenOn =
        theAccountsTheyWereTakenOn(
            testRunId,
            arrivedTransactions.stream().map(arrival -> arrival.row().merchantId()).toList());
    for (ArrivalOf<GrowTransaction> arrived : arrivedTransactions) {
      GrowTransaction transaction = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), transaction.transactionId()),
              stateOf(transaction, now, theAccountsTheyWereTakenOn.get(transaction.merchantId()))));
    }
    for (ArrivalOf<GrowPaymentProcess> arrived :
        processes.newestFirst(testRunId, after, query.limit())) {
      GrowPaymentProcess process = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), process.processId()),
              GrowProcessObject.stateOf(process)));
    }
    return ProviderObjectPages.newestFirst(placed, query.limit());
  }

  @Override
  public ObjectActions objectActions() {
    return GrowObjectActions.DECLARED;
  }

  @Override
  public ObjectActionOutcome act(ObjectActionRequest request) {
    Optional<GrowTransaction> found =
        transactions.findByTransactionId(request.testRunId(), request.providerObjectId());
    if (found.isEmpty()) {
      if (processes.findByProcessId(request.testRunId(), request.providerObjectId()).isPresent()) {
        return GrowProcessObject.noActionMovesMoneyOnAProcess();
      }
      return new ObjectActionOutcome.NoSuchObject();
    }
    GrowTransaction transaction = found.get();
    Optional<GrowAccount> account = accounts.find(request.testRunId(), transaction.merchantId());
    if (account.isEmpty()) {
      return GrowObjectActions.theRunConfiguresNoAccountFor(transaction);
    }
    GrowForm form = GrowObjectActions.formOf(request, account.get(), transaction);
    Instant now = clock.now(request.testRunId());
    GrowOperationResult result =
        request.action() == ObjectAction.REFUND
            ? refundTransaction.handle(
                form, Map.of(), request.testRunId(), List.of(account.get()), now, request.askedBy())
            : settleSuspendedTransaction.handle(
                form, Map.of(), request.testRunId(), List.of(account.get()), now);
    return GrowObjectActions.outcomeOf(result, transaction);
  }

  @Override
  public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
    return accounts.find(testRunId, accountId).map(GrowAccount::disclosure);
  }

  private Map<String, GrowAccount> theAccountsTheyWereTakenOn(
      TestRunId testRunId, List<String> merchantIds) {
    return accounts.allById(testRunId, merchantIds.stream().distinct().toList()).stream()
        .collect(Collectors.toMap(GrowAccount::accountId, account -> account));
  }

  private ProviderObjectState stateOf(GrowTransaction transaction, Instant now) {
    return stateOf(
        transaction,
        now,
        accounts.find(transaction.testRunId(), transaction.merchantId()).orElse(null));
  }

  private static ProviderObjectState stateOf(
      GrowTransaction transaction, Instant now, @Nullable GrowAccount account) {
    GrowHoldWindow window = windowOf(account);
    return new ProviderObjectState(
        ProviderObjectIdentity.of(transaction.transactionId()),
        GrowObjectKinds.of(transaction),
        transaction.nativeStatusCode(),
        transaction.normalizedStatus(now, window),
        transaction.amount(),
        null,
        transaction.ledger(),
        !transaction.isAutomaticallyVoided(now, window),
        transaction.acceptsASettlement(now, window),
        transaction.acceptsARefund(refundAllowanceOf(account)),
        relatedObjectsOf(transaction),
        transaction.origin(),
        transaction.updatedAt());
  }

  private static GrowHoldWindow windowOf(@Nullable GrowAccount account) {
    return account == null ? GrowHoldWindow.PUBLISHED_DEFAULT : account.holdWindow();
  }

  private static GrowRefundAllowance refundAllowanceOf(@Nullable GrowAccount account) {
    return account == null ? GrowRefundAllowance.of(null) : account.refundAllowance();
  }

  private static Map<String, String> relatedObjectsOf(GrowTransaction transaction) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put("merchantId", transaction.merchantId());
    String processId = transaction.processId();
    if (processId != null) {
      related.put("processId", processId);
    }
    String refunded = transaction.refundedTransactionId();
    if (refunded != null) {
      related.put("refundedTransactionId", refunded);
    }
    return related;
  }

  @Override
  public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    return callbacks.schedule(request);
  }

  @Override
  public HostedPageContract hostedPages() {
    return new HostedPageContract.ObjectsAPayerFinishes(
        List.of(new HostedPageContract.AnObjectItKeepsWaiting("a payment process", true)));
  }

  @Override
  public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
    return completions.offeredOn(testRunId, providerObjectId);
  }

  @Override
  public HostedCompletionOutcome complete(HostedCompletionRequest request) {
    CompleteHostedProcess.Result result =
        completions.byProcessId(
            request.testRunId(),
            request.providerObjectId(),
            request.outcome(),
            request.card(),
            request.askedBy());
    return switch (result) {
      case CompleteHostedProcess.Result.Completed completed ->
          new HostedCompletionOutcome.Completed(
              completed.process().processId(),
              completed.createdTransactionId(),
              completed.outcome(),
              webhookJobsOf(completed));
      case CompleteHostedProcess.Result.Refused refused ->
          new HostedCompletionOutcome.RefusedByTheProvider(refused.inItsOwnWords());
      case CompleteHostedProcess.Result.NoSuchProcess _ ->
          new HostedCompletionOutcome.NoSuchObject();
    };
  }

  private static List<String> webhookJobsOf(CompleteHostedProcess.Result.Completed completed) {
    List<String> jobIds = new ArrayList<>();
    for (ScheduledWebhook scheduled : completed.callbacks()) {
      jobIds.add(scheduled.webhookJobId());
    }
    return List.copyOf(jobIds);
  }
}
