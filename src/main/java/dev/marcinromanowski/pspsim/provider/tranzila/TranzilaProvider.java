package dev.marcinromanowski.pspsim.provider.tranzila;

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
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.spi.AccountProperty;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
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
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TranzilaProvider implements SimulatorProvider {

  static final String NO_PAGE_TO_COMPLETE =
      "this object is a transaction of provider tranzila's card API, which charges a card number"
          + " or a token the merchant already holds and keeps nothing waiting for a payer; the"
          + " object a payer finishes is the DirectNG payment page";

  static final String HOLD_EXPIRES_AT = "hold_expires_at";

  private final TranzilaTerminals terminals;
  private final TranzilaTransactions transactions;
  private final TranzilaNonces nonces;
  private final TranzilaExchanges exchanges;
  private final AuditTrail audit;
  private final PaymentMethodFixtures fixtures;
  private final ScenarioRules scenarios;
  private final DelayedDeliveries heldResponses;
  private final SimulatorClock clock;
  private final CreateTransaction createTransaction;
  private final TranzilaPages pages;
  private final TranzilaHandshakes handshakes;
  private final TranzilaDirectNg directNg;
  private final TranzilaNotify notify;

  TranzilaProvider(
      TranzilaTerminals terminals,
      TranzilaTransactions transactions,
      TranzilaNonces nonces,
      TranzilaExchanges exchanges,
      AuditTrail audit,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SimulatorClock clock,
      CreateTransaction createTransaction,
      TranzilaPages pages,
      TranzilaHandshakes handshakes,
      TranzilaDirectNg directNg,
      TranzilaNotify notify) {
    this.terminals = terminals;
    this.transactions = transactions;
    this.nonces = nonces;
    this.exchanges = exchanges;
    this.audit = audit;
    this.fixtures = fixtures;
    this.scenarios = scenarios;
    this.heldResponses = heldResponses;
    this.clock = clock;
    this.createTransaction = createTransaction;
    this.pages = pages;
    this.handshakes = handshakes;
    this.directNg = directNg;
    this.notify = notify;
  }

  @Override
  public List<ContractCheck> compatibilityChecks(CheckJson json, IdGenerator ids) {
    return new TranzilaCompatibilityChecks(json, ids, clock).all();
  }

  @Override
  public ProviderId id() {
    return TranzilaApi.TRANZILA;
  }

  @Override
  public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
    TestRunId testRunId = configuration.testRunId();
    List<ConfiguredAccount> declared = configuration.accounts();
    Set<String> rewritten = new LinkedHashSet<>();
    declared.forEach(named -> rewritten.add(named.accountId()));
    List<TranzilaTerminal> keptInPlace =
        terminals.ofTheRun(testRunId).stream()
            .filter(held -> !rewritten.contains(held.accountId()))
            .toList();
    Set<String> terminalNames = new LinkedHashSet<>();
    Set<String> appKeys = new LinkedHashSet<>();
    keptInPlace.forEach(
        held -> {
          terminalNames.add(held.terminalName());
          appKeys.add(held.appKey());
        });
    List<TranzilaTerminal> parsed = new ArrayList<>();
    for (int index = 0; index < declared.size(); index++) {
      String property = AccountProperty.prefixOf(index);
      TranzilaTerminal terminal = TranzilaTerminal.of(property, testRunId, declared.get(index));
      if (!terminalNames.add(terminal.terminalName())) {
        throw alreadyConfigured(property + TranzilaTerminal.TERMINAL_NAME_PROPERTY);
      }
      if (!appKeys.add(terminal.appKey())) {
        throw alreadyConfigured(property + TranzilaTerminal.APP_KEY_PROPERTY);
      }
      parsed.add(terminal);
    }
    List<TranzilaTerminal> itWillKeep = List.copyOf(parsed);
    return new ProviderConfigurationPlan(
        TranzilaTerminal.CONFIGURATION_KEYS,
        List.of(),
        () -> terminals.merge(testRunId, itWillKeep));
  }

  private static ProviderConfigurationRejectedException alreadyConfigured(String property) {
    return new ProviderConfigurationRejectedException(
        property + " is already configured for another account in this test run");
  }

  @Override
  public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
    return new AccountRemovalPlan(() -> terminals.remove(testRunId, accountId));
  }

  @Override
  public ProviderVocabulary vocabulary() {
    return TranzilaOperations.VOCABULARY;
  }

  @Override
  public ProviderWireRoutes wireRoutes() {
    return TranzilaWireFaults.ROUTES;
  }

  @Override
  public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
    return TranzilaWireFaults.theAnswerTo(fault, address);
  }

  @Override
  public ProviderAccountFields accountFields() {
    return TranzilaTerminal.ACCOUNT_FIELDS;
  }

  @Override
  public ProviderPaymentMethodFields paymentMethodFields() {
    return TranzilaFixtureCards.PAYMENT_METHOD_FIELDS;
  }

  @Override
  public ProviderKeyMechanisms keyMechanisms() {
    return TranzilaKeyMechanisms.MECHANISMS;
  }

  @Override
  public ProviderPairing pairing() {
    return TranzilaPairing.PAIRING;
  }

  @Override
  public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
    return exchanges.exchanges(query);
  }

  @Override
  public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
    return TranzilaFixtureCards.thePlanFor(fixture);
  }

  @Override
  public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
    return new PaymentMethodFixtureRemoval(() -> {});
  }

  @Override
  public ProviderPersonas personas(IdGenerator ids) {
    return TranzilaPersonas.of(ids);
  }

  @Override
  public PresetExpansion expand(PresetRequest request) {
    return TranzilaPresets.of(request);
  }

  @Override
  public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
    if (scope == ResetScope.ALL) {
      heldResponses.cancelAll(TranzilaApi.TRANZILA);
      return new ResetOutcome(
          transactions.clearAll()
              + pages.clearAll()
              + handshakes.clearAll()
              + nonces.clearAll()
              + exchanges.clearAll()
              + fixtures.clearAll(TranzilaApi.TRANZILA)
              + scenarios.clearAll(TranzilaApi.TRANZILA),
          audit.clearAll(TranzilaApi.TRANZILA));
    }
    heldResponses.cancel(TranzilaApi.TRANZILA, testRunId);
    return new ResetOutcome(
        transactions.clear(testRunId)
            + pages.clear(testRunId)
            + handshakes.clear(testRunId)
            + nonces.clear(testRunId)
            + exchanges.clear(testRunId)
            + fixtures.clear(TranzilaApi.TRANZILA, testRunId)
            + scenarios.clear(TranzilaApi.TRANZILA, testRunId),
        audit.clear(TranzilaApi.TRANZILA, testRunId));
  }

  @Override
  public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
    Instant now = clock.now(testRunId);
    Optional<TranzilaPage> page = pages.find(testRunId, providerObjectId);
    if (page.isPresent()) {
      return Optional.of(stateOf(page.get(), now));
    }
    return TranzilaIdentifiers.transactionIdIn(providerObjectId)
        .flatMap(
            id ->
                transactions
                    .paymentHolding(testRunId, id.longValue())
                    .flatMap(payment -> TranzilaObject.of(payment, id.longValue())))
        .map(object -> stateOf(object, now));
  }

  @Override
  public List<ProviderObjectState> theObjectsItHoldsUnder(
      TestRunId testRunId, List<String> providerObjectIds) {
    List<Long> asked =
        providerObjectIds.stream()
            .map(TranzilaIdentifiers::transactionIdIn)
            .flatMap(Optional::stream)
            .toList();
    Instant now = clock.now(testRunId);
    List<ProviderObjectState> held = new ArrayList<>();
    TranzilaGroups.theObjectsNamed(asked, transactions.paymentsHolding(testRunId, asked))
        .forEach(object -> held.add(stateOf(object, now)));
    pages.allById(testRunId, providerObjectIds).forEach(page -> held.add(stateOf(page, now)));
    return List.copyOf(held);
  }

  ProviderObjectState stateOf(TranzilaPage page, Instant now) {
    Long transactionId = page.transactionId();
    if (transactionId == null) {
      return TranzilaPageObject.stateOf(page, null, now);
    }
    long made = transactionId.longValue();
    return TranzilaPageObject.stateOf(
        page,
        transactions
            .paymentHolding(page.testRunId(), made)
            .flatMap(payment -> TranzilaObject.of(payment, made))
            .orElse(null),
        now);
  }

  @Override
  public ObjectActions objectActions() {
    return TranzilaObjectActions.DECLARED;
  }

  @Override
  public ObjectActionOutcome act(ObjectActionRequest request) {
    TestRunId testRunId = request.testRunId();
    Optional<TranzilaObject> found =
        TranzilaIdentifiers.transactionIdIn(request.providerObjectId())
            .flatMap(
                id ->
                    transactions
                        .paymentHolding(testRunId, id.longValue())
                        .flatMap(payment -> TranzilaObject.of(payment, id.longValue())));
    if (found.isEmpty()) {
      return new ObjectActionOutcome.NoSuchObject();
    }
    TranzilaObject object = found.get();
    TranzilaPayment payment = object.payment();
    Optional<TranzilaTerminal> terminal = terminals.find(testRunId, payment.root().accountId());
    if (terminal.isEmpty()) {
      return TranzilaObjectActions.theRunConfiguresNoTerminal();
    }
    Instant now = clock.now(testRunId);
    TranzilaOperationResult result =
        createTransaction.handle(
            TranzilaObjectActions.callOf(request, object, terminal.get(), now),
            Map.of(),
            testRunId,
            terminal.get(),
            now,
            request.askedBy());
    return TranzilaObjectActions.outcomeOf(result, object);
  }

  @Override
  public ProviderOriginationFields originationFields() {
    return TranzilaOrigination.DECLARED;
  }

  @Override
  public OriginationOutcome originate(OriginationRequest request) {
    TestRunId testRunId = request.testRunId();
    Optional<TranzilaTerminal> terminal = terminals.find(testRunId, request.accountId());
    if (terminal.isEmpty()) {
      return new OriginationOutcome.NoSuchAccount();
    }
    if (TranzilaOrigination.A_PAGE_PAYMENT.equals(request.startedAs())) {
      return TranzilaOrigination.outcomeOf(
          directNg.opening(
              terminal.get(),
              TranzilaOrigination.theParametersOf(request),
              request.askedBy(),
              clock.now(testRunId)),
          request);
    }
    TranzilaOperationResult result =
        createTransaction.handle(
            TranzilaOrigination.callOf(request, terminal.get()),
            Map.of(),
            testRunId,
            terminal.get(),
            clock.now(testRunId),
            request.askedBy());
    return TranzilaOrigination.outcomeOf(result, request);
  }

  @Override
  public HostedPageContract hostedPages() {
    return new HostedPageContract.ObjectsAPayerFinishes(
        List.of(
            new HostedPageContract.AnObjectItKeepsWaiting(
                TranzilaObjectKinds.A_PAYMENT_PAGE.asThisProviderNamesIt(), true)));
  }

  @Override
  public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
    return pages
        .find(testRunId, providerObjectId)
        .map(directNg::offeredOn)
        .orElseGet(
            () -> new HostedCompletionOffer.NotOffered(TranzilaApi.TRANZILA, NO_PAGE_TO_COMPLETE));
  }

  @Override
  public HostedCompletionOutcome complete(HostedCompletionRequest request) {
    Optional<TranzilaPage> page = pages.find(request.testRunId(), request.providerObjectId());
    if (page.isPresent()) {
      return directNg.complete(page.get(), request);
    }
    return state(request.testRunId(), request.providerObjectId()).isPresent()
        ? new HostedCompletionOutcome.NotOffered(NO_PAGE_TO_COMPLETE)
        : new HostedCompletionOutcome.NoSuchObject();
  }

  @Override
  public ProviderObjectPage objects(ProviderObjectQuery query) {
    TestRunId testRunId = query.testRunId();
    String cursor = query.after();
    ProviderObjectCursor after = null;
    if (cursor != null) {
      after =
          TranzilaIdentifiers.transactionIdIn(cursor)
              .flatMap(id -> transactions.placeOf(testRunId, id.longValue()))
              .or(() -> pages.placeOf(testRunId, cursor))
              .orElse(null);
      if (after == null) {
        return new ProviderObjectPage.NoSuchCursor();
      }
    }
    Instant now = clock.now(testRunId);
    List<PlacedProviderObject> placed = new ArrayList<>();
    for (ArrivalOf<TranzilaObject> arrived :
        transactions.newestFirst(testRunId, after, query.limit())) {
      TranzilaObject object = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), object.objectId()),
              stateOf(object, now)));
    }
    for (ArrivalOf<TranzilaPage> arrived : pages.newestFirst(testRunId, after, query.limit())) {
      TranzilaPage page = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), page.pageId()), stateOf(page, now)));
    }
    return ProviderObjectPages.newestFirst(placed, query.limit());
  }

  @Override
  public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
    return terminals.find(testRunId, accountId).map(TranzilaTerminal::disclosure);
  }

  @Override
  public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    return notify.schedule(request);
  }

  static ProviderObjectState stateOf(TranzilaObject object, Instant now) {
    TranzilaTransaction subject = object.subject();
    TranzilaPayment payment = object.payment();
    boolean original = object.isTheOriginal();
    boolean held = original && payment.isAJ5StillHeldAt(now);
    return new ProviderObjectState(
        ProviderObjectIdentity.of(object.objectId()),
        TranzilaObjectKinds.of(subject),
        object.nativeStatus(),
        object.normalizedStatusAt(now),
        Amount.of(subject.amount()),
        subject.currencyCode(),
        original ? payment.ledger() : Ledger.empty(),
        held,
        held,
        original && payment.refundsAccepted(),
        relatedObjectsOf(object),
        subject.origin(),
        object.updatedAt());
  }

  static Map<String, String> relatedObjectsOf(TranzilaObject object) {
    TranzilaTransaction subject = object.subject();
    Map<String, String> related = new LinkedHashMap<>();
    related.put(TranzilaTerminal.TERMINAL_NAME_PROPERTY, subject.terminalName());
    related.put(TranzilaFields.CARD_MASK, subject.card().cardMask());
    String authNumber = subject.authNumber();
    if (authNumber != null) {
      related.put(TranzilaFields.AUTH_NUMBER, authNumber);
    }
    Instant expires = subject.holdExpiresAt();
    if (expires != null) {
      related.put(HOLD_EXPIRES_AT, expires.toString());
    }
    Long reference = subject.referenceTxnId();
    if (reference != null) {
      related.put(TranzilaFields.REFERENCE_TXN_ID, reference.toString());
    }
    Map<String, Integer> seen = new LinkedHashMap<>();
    for (TranzilaTransaction followOn : object.payment().followOns()) {
      if (followOn.references(subject.transactionId())) {
        String type = followOn.type().wire();
        int nth = seen.merge(type, 1, Integer::sum);
        related.put(nth == 1 ? type : type + " " + nth, followOn.transactionIdAsText());
      }
    }
    return related;
  }
}
