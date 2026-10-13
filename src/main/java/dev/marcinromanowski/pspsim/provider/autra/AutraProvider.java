package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.StoryKeyWitness;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtureRemoval;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AccountProperty;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUp;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
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
import org.jspecify.annotations.Nullable;

final class AutraProvider implements SimulatorProvider {

  @Override
  public List<ContractCheck> compatibilityChecks(CheckJson json, IdGenerator ids) {
    return new AutraCompatibilityChecks(json, ids).all();
  }

  private final AutraMerchants merchants;
  private final AutraSessions sessions;
  private final AutraPayments payments;
  private final AutraPixPayments pixPayments;
  private final AutraCardSessions cardSessions;
  private final AuditTrail audit;
  private final PaymentMethodFixtures fixtures;
  private final ScenarioRules scenarios;
  private final DelayedDeliveries heldResponses;
  private final SimulatorClock clock;
  private final OpenAcquiringSession openAcquiringSession;
  private final OpenCardSession openCardSession;
  private final ChargeStoredCard chargeStoredCard;
  private final TokenizeOnTheHostedPage tokenizeOnTheHostedPage;
  private final PreAuthorize preAuthorize;
  private final CaptureHold captureHold;
  private final ReverseOrRelease reverseOrRelease;
  private final CreatePixCheckout createPixCheckout;
  private final LookUpPayment lookUpPayment;
  private final ListPayments listPayments;
  private final CompletePixPayment completePixPayment;
  private final SettlePixPayment settlePixPayment;
  private final AutraCallbacks callbacks;
  private final WebhookDispatcher callbackDeliveries;

  AutraProvider(
      AutraMerchants merchants,
      AutraSessions sessions,
      AutraPayments payments,
      AutraPixPayments pixPayments,
      AutraCardSessions cardSessions,
      AuditTrail audit,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SimulatorClock clock,
      OpenAcquiringSession openAcquiringSession,
      OpenCardSession openCardSession,
      ChargeStoredCard chargeStoredCard,
      TokenizeOnTheHostedPage tokenizeOnTheHostedPage,
      PreAuthorize preAuthorize,
      CaptureHold captureHold,
      ReverseOrRelease reverseOrRelease,
      CreatePixCheckout createPixCheckout,
      LookUpPayment lookUpPayment,
      ListPayments listPayments,
      CompletePixPayment completePixPayment,
      SettlePixPayment settlePixPayment,
      AutraCallbacks callbacks,
      WebhookDispatcher callbackDeliveries) {
    this.merchants = merchants;
    this.sessions = sessions;
    this.payments = payments;
    this.pixPayments = pixPayments;
    this.cardSessions = cardSessions;
    this.audit = audit;
    this.fixtures = fixtures;
    this.scenarios = scenarios;
    this.heldResponses = heldResponses;
    this.clock = clock;
    this.openAcquiringSession = openAcquiringSession;
    this.openCardSession = openCardSession;
    this.chargeStoredCard = chargeStoredCard;
    this.tokenizeOnTheHostedPage = tokenizeOnTheHostedPage;
    this.preAuthorize = preAuthorize;
    this.captureHold = captureHold;
    this.reverseOrRelease = reverseOrRelease;
    this.createPixCheckout = createPixCheckout;
    this.lookUpPayment = lookUpPayment;
    this.listPayments = listPayments;
    this.completePixPayment = completePixPayment;
    this.settlePixPayment = settlePixPayment;
    this.callbacks = callbacks;
    this.callbackDeliveries = callbackDeliveries;
  }

  @Override
  public ProviderId id() {
    return AutraApi.AUTRA;
  }

  @Override
  public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
    TestRunId testRunId = configuration.testRunId();
    List<ConfiguredAccount> declared = configuration.accounts();
    List<AutraMerchant> parsed = new ArrayList<>();
    List<StoryKeyWitness> witnesses = new ArrayList<>();
    Set<String> clientIds = new LinkedHashSet<>();
    Set<String> documentIds = new LinkedHashSet<>();
    Set<String> rewritten = new LinkedHashSet<>();
    for (ConfiguredAccount named : declared) {
      rewritten.add(named.accountId());
    }
    for (int index = 0; index < declared.size(); index++) {
      String property = AccountProperty.prefixOf(index);
      AutraMerchant merchant = AutraMerchant.of(property, testRunId, declared.get(index));
      if (!clientIds.add(merchant.clientId())) {
        throw new ProviderConfigurationRejectedException(
            property
                + AutraMerchant.CLIENT_ID_PROPERTY
                + " is already configured for another account in this test run");
      }
      if (!documentIds.add(merchant.documentId())) {
        throw new ProviderConfigurationRejectedException(
            property
                + AutraMerchant.DOCUMENT_ID_PROPERTY
                + " is already configured for another account in this test run");
      }
      rejectAClientIdAnAccountThisCallLeavesInPlaceHolds(property, merchant, rewritten);
      rejectADocumentAnAccountThisCallLeavesInPlaceHolds(property, merchant, rewritten);
      parsed.add(merchant);
      witnesses.add(
          new StoryKeyWitness(
              property + AutraMerchant.DOCUMENT_ID_PROPERTY,
              AutraStoryKey.theLongestSessionKeyOf(merchant.documentId())));
    }
    List<AutraMerchant> itWillKeep = List.copyOf(parsed);
    return new ProviderConfigurationPlan(
        AutraMerchant.CONFIGURATION_KEYS,
        List.copyOf(witnesses),
        () -> merchants.merge(testRunId, itWillKeep));
  }

  private void rejectAClientIdAnAccountThisCallLeavesInPlaceHolds(
      String property, AutraMerchant merchant, Set<String> rewritten) {
    for (AutraMerchant held : merchants.byClientId(merchant.clientId())) {
      if (held.testRunId().equals(merchant.testRunId()) && !rewritten.contains(held.accountId())) {
        throw new ProviderConfigurationRejectedException(
            property
                + AutraMerchant.CLIENT_ID_PROPERTY
                + " is already configured for another account in this test run");
      }
    }
  }

  private void rejectADocumentAnAccountThisCallLeavesInPlaceHolds(
      String property, AutraMerchant merchant, Set<String> rewritten) {
    merchants
        .ofTheRunHolding(merchant.testRunId(), merchant.documentId())
        .filter(held -> !rewritten.contains(held.accountId()))
        .ifPresent(
            held -> {
              throw new ProviderConfigurationRejectedException(
                  property
                      + AutraMerchant.DOCUMENT_ID_PROPERTY
                      + " is already configured for another account in this test run");
            });
  }

  @Override
  public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
    return new AccountRemovalPlan(() -> merchants.remove(testRunId, accountId));
  }

  @Override
  public ProviderVocabulary vocabulary() {
    return AutraOperations.VOCABULARY;
  }

  @Override
  public ProviderWireRoutes wireRoutes() {
    return AutraWireFaults.ROUTES;
  }

  @Override
  public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
    return AutraWireFaults.theAnswerTo(fault, address);
  }

  @Override
  public ProviderAccountFields accountFields() {
    return AutraMerchant.ACCOUNT_FIELDS;
  }

  @Override
  public ProviderPaymentMethodFields paymentMethodFields() {
    return AutraFixtureCards.PAYMENT_METHOD_FIELDS;
  }

  @Override
  public ProviderKeyMechanisms keyMechanisms() {
    return AutraKeyMechanisms.MECHANISMS;
  }

  @Override
  public ProviderPairing pairing() {
    return AutraPairing.PAIRING;
  }

  @Override
  public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
    return payments.exchanges(query);
  }

  @Override
  public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
    return AutraFixtureCards.thePlanFor(fixture);
  }

  @Override
  public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
    return new PaymentMethodFixtureRemoval(() -> {});
  }

  @Override
  public ProviderPersonas personas(IdGenerator ids) {
    return AutraPersonas.of(ids);
  }

  @Override
  public PresetExpansion expand(PresetRequest request) {
    return AutraPresets.of(request);
  }

  @Override
  public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
    if (scope == ResetScope.ALL) {
      heldResponses.cancelAll(AutraApi.AUTRA);
      return new ResetOutcome(
          payments.clearAll()
              + pixPayments.clearAll()
              + cardSessions.clearAll()
              + callbackDeliveries.clearAll(AutraApi.AUTRA)
              + sessions.clearAll()
              + fixtures.clearAll(AutraApi.AUTRA)
              + scenarios.clearAll(AutraApi.AUTRA),
          audit.clearAll(AutraApi.AUTRA));
    }
    heldResponses.cancel(AutraApi.AUTRA, testRunId);
    return new ResetOutcome(
        payments.clear(testRunId)
            + pixPayments.clear(testRunId)
            + cardSessions.clear(testRunId)
            + callbackDeliveries.clear(AutraApi.AUTRA, testRunId)
            + sessions.clear(testRunId)
            + fixtures.clear(AutraApi.AUTRA, testRunId)
            + scenarios.clear(AutraApi.AUTRA, testRunId),
        audit.clear(AutraApi.AUTRA, testRunId));
  }

  @Override
  public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
    Optional<ProviderObjectState> card =
        payments.findById(testRunId, providerObjectId).map(this::stateOf);
    if (card.isPresent()) {
      return card;
    }
    Instant now = clock.now(testRunId);
    Optional<ProviderObjectState> pix =
        pixPayments.findById(testRunId, providerObjectId).map(held -> stateOfPix(held, now));
    return pix.isPresent()
        ? pix
        : cardSessions
            .findById(testRunId, providerObjectId)
            .map(session -> stateOfCardSession(session, now));
  }

  @Override
  public List<ProviderObjectState> theObjectsItHoldsUnder(
      TestRunId testRunId, List<String> providerObjectIds) {
    List<String> asked = List.copyOf(providerObjectIds);
    List<ProviderObjectState> held = new ArrayList<>(theCardPaymentsAmong(testRunId, asked));
    Instant now = clock.now(testRunId);
    for (AutraPixPayment pix : pixPayments.allById(testRunId, asked)) {
      held.add(stateOfPix(pix, now));
    }
    for (AutraCardSession session : cardSessions.allById(testRunId, asked)) {
      held.add(stateOfCardSession(session, now));
    }
    return List.copyOf(held);
  }

  private List<ProviderObjectState> theCardPaymentsAmong(TestRunId testRunId, List<String> asked) {
    List<AutraPayment> found = payments.allById(testRunId, asked);
    Map<String, List<AutraPaymentEvent>> timelines =
        AutraPaymentTimelines.keyedByPayment(
            payments.everyEventOf(testRunId, AutraPaymentTimelines.theIdentifiersOf(found)));
    List<ProviderObjectState> read = new ArrayList<>();
    for (AutraPayment payment : found) {
      read.add(
          stateOf(payment, AutraPaymentTimelines.theTimelineOf(timelines, payment.paymentId())));
    }
    return List.copyOf(read);
  }

  @Override
  public ObjectActions objectActions() {
    return AutraObjectActions.DECLARED;
  }

  @Override
  public ProviderOriginationFields originationFields() {
    return AutraOrigination.DECLARED;
  }

  @Override
  public OriginationOutcome originate(OriginationRequest request) {
    TestRunId testRunId = request.testRunId();
    Optional<AutraMerchant> merchant = merchants.find(testRunId, request.accountId());
    if (merchant.isEmpty()) {
      return new OriginationOutcome.NoSuchAccount();
    }
    Instant now = clock.now(testRunId);
    return AutraOrigination.outcomeOf(
        theWayItWasStarted(request, merchant.get(), testRunId, now), request);
  }

  private AutraOperationResult theWayItWasStarted(
      OriginationRequest request, AutraMerchant merchant, TestRunId testRunId, Instant now) {
    if (AutraOrigination.A_CARD_SESSION.equals(request.startedAs())) {
      return openCardSession.handle(
          AutraOrigination.cardSessionCallOf(request, merchant),
          Map.of(),
          testRunId,
          merchant,
          now,
          request.askedBy());
    }
    String transactionalToken = aSessionOpenedForTheScreen(testRunId, merchant, now);
    if (AutraOrigination.A_SALE.equals(request.startedAs())) {
      return chargeStoredCard.handle(
          AutraOrigination.saleCallOf(request, merchant, transactionalToken),
          Map.of(),
          testRunId,
          merchant,
          now,
          request.askedBy());
    }
    if (AutraOrigination.A_PIX.equals(request.startedAs())) {
      return createPixCheckout.handle(
          AutraOrigination.pixCallOf(request, merchant, transactionalToken),
          Map.of(),
          testRunId,
          merchant,
          now,
          request.askedBy());
    }
    return preAuthorize.handle(
        AutraOrigination.callOf(request, merchant, transactionalToken),
        Map.of(),
        testRunId,
        merchant,
        now,
        request.askedBy());
  }

  @Override
  public ProviderReads reads() {
    return AutraReads.DECLARED;
  }

  @Override
  public ReadOutcome read(ReadRequest request) {
    Optional<AutraMerchant> merchant = merchants.find(request.testRunId(), request.accountId());
    if (merchant.isEmpty()) {
      return new ReadOutcome.NoSuchAccount();
    }
    return AutraReads.theAnswerTo(
        request, merchant.get(), lookUpPayment, listPayments, clock.now(request.testRunId()));
  }

  @Override
  public List<AValueAFieldOffers> theValuesThisTestRunHasAlreadyProducedFor(
      TestRunId testRunId, String accountId, String fieldKey) {
    if (!AutraFields.PAYMENT_ID_LOWERCASE_D.equals(fieldKey)) {
      return SimulatorProvider.super.theValuesThisTestRunHasAlreadyProducedFor(
          testRunId, accountId, fieldKey);
    }
    return merchants
        .find(testRunId, accountId)
        .map(
            merchant ->
                AutraReads.thePaymentsThisTestRunHasAlreadyTaken(
                    payments.ofMerchant(testRunId, merchant.documentId())))
        .orElseGet(List::of);
  }

  @Override
  public HostedPageContract hostedPages() {
    return new HostedPageContract.ObjectsAPayerFinishes(
        List.of(
            new HostedPageContract.AnObjectItKeepsWaiting("a card session", true),
            new HostedPageContract.AnObjectItKeepsWaiting("a Pix", false)));
  }

  @Override
  public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
    Instant now = clock.now(testRunId);
    Optional<AutraCardSession> session = cardSessions.findById(testRunId, providerObjectId);
    return session.isPresent()
        ? tokenizeOnTheHostedPage.offerOn(session.get(), now)
        : completePixPayment.offer(testRunId, providerObjectId, now);
  }

  @Override
  public HostedCompletionOutcome complete(HostedCompletionRequest request) {
    TestRunId testRunId = request.testRunId();
    Instant now = clock.now(testRunId);
    Optional<AutraCardSession> session =
        cardSessions.findById(testRunId, request.providerObjectId());
    return session.isPresent()
        ? tokenizeOnTheHostedPage.complete(request, session.get(), now)
        : completePixPayment.complete(request, now);
  }

  @Override
  public List<FollowUp> followUps(TestRunId testRunId, String providerObjectId, Instant now) {
    return settlePixPayment.offeredOn(testRunId, providerObjectId, now);
  }

  @Override
  public List<ScheduledWebhook> followUp(FollowUpRequest request, Instant now) {
    return settlePixPayment.perform(request, now);
  }

  @Override
  public ObjectActionOutcome act(ObjectActionRequest request) {
    TestRunId testRunId = request.testRunId();
    Optional<AutraPayment> found = payments.findById(testRunId, request.providerObjectId());
    if (found.isEmpty()) {
      return new ObjectActionOutcome.NoSuchObject();
    }
    AutraPayment payment = found.get();
    Optional<AutraMerchant> merchant = merchants.ofTheRunHolding(testRunId, payment.documentId());
    if (merchant.isEmpty()) {
      return AutraObjectActions.theRunConfiguresNoMerchantFor(payment);
    }
    Instant now = clock.now(testRunId);
    String transactionalToken = aSessionOpenedForTheScreen(testRunId, merchant.get(), now);
    AutraApiCall call = AutraObjectActions.callOf(request, payment, transactionalToken);
    AutraOperationResult result =
        request.action() == ObjectAction.CAPTURE
            ? captureHold.handle(
                call, Map.of(), testRunId, merchant.get(), payment.paymentId(), now)
            : reverseOrRelease.handle(
                call, Map.of(), testRunId, merchant.get(), payment.paymentId(), now);
    return AutraObjectActions.outcomeOf(result, payment);
  }

  private String aSessionOpenedForTheScreen(
      TestRunId testRunId, AutraMerchant merchant, Instant now) {
    return openAcquiringSession.aSessionClaimedFor(testRunId, merchant, now).transactionalToken();
  }

  @Override
  public ProviderObjectPage objects(ProviderObjectQuery query) {
    TestRunId testRunId = query.testRunId();
    String cursor = query.after();
    ProviderObjectCursor after = null;
    if (cursor != null) {
      after =
          payments
              .placeOf(testRunId, cursor)
              .or(() -> pixPayments.placeOf(testRunId, cursor))
              .or(() -> cardSessions.placeOf(testRunId, cursor))
              .orElse(null);
      if (after == null) {
        return new ProviderObjectPage.NoSuchCursor();
      }
    }
    Instant now = clock.now(testRunId);
    List<PlacedProviderObject> placed = new ArrayList<>();
    List<ArrivalOf<AutraPayment>> arrivedPayments =
        payments.newestFirst(testRunId, after, query.limit());
    Map<String, List<AutraPaymentEvent>> timelines =
        AutraPaymentTimelines.keyedByPayment(
            payments.everyEventOf(
                testRunId, AutraPaymentTimelines.theIdentifiersArrivedAt(arrivedPayments)));
    for (ArrivalOf<AutraPayment> arrived : arrivedPayments) {
      AutraPayment payment = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), payment.paymentId()),
              stateOf(
                  payment, AutraPaymentTimelines.theTimelineOf(timelines, payment.paymentId()))));
    }
    for (ArrivalOf<AutraPixPayment> arrived :
        pixPayments.newestFirst(testRunId, after, query.limit())) {
      AutraPixPayment pix = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), pix.pixId()), stateOfPix(pix, now)));
    }
    for (ArrivalOf<AutraCardSession> arrived :
        cardSessions.newestFirst(testRunId, after, query.limit())) {
      AutraCardSession session = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), session.cardSessionId()),
              stateOfCardSession(session, now)));
    }
    return ProviderObjectPages.newestFirst(placed, query.limit());
  }

  @Override
  public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
    return merchants.find(testRunId, accountId).map(AutraMerchant::disclosure);
  }

  @Override
  public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    return callbacks.schedule(request, clock.now(request.testRunId()));
  }

  private ProviderObjectState stateOf(AutraPayment payment) {
    return stateOf(payment, payments.timelineOf(payment.testRunId(), payment.paymentId()));
  }

  private static ProviderObjectState stateOf(
      AutraPayment payment, List<AutraPaymentEvent> timeline) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(payment.paymentId()),
        AutraObjectKinds.of(payment),
        payment.nativeStatus(),
        payment.normalizedStatus(),
        payment.amount(),
        payment.currency(),
        payment.ledger(),
        payment.authorizationOpen(),
        payment.capturesAccepted(),
        payment.reversalsAccepted(),
        relatedObjectsOf(payment, timeline),
        payment.origin(),
        payment.updatedAt());
  }

  private static ProviderObjectState stateOfPix(AutraPixPayment pix, Instant now) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(pix.pixId()),
        AutraObjectKinds.ofAPix(),
        pix.statusAt(now),
        pix.normalizedStatusAt(now),
        pix.amount(),
        pix.currency(),
        pix.ledgerAt(now),
        false,
        false,
        false,
        relatedObjectsOfPix(pix),
        pix.origin(),
        pix.updatedAt(),
        sessionFactsOfPix(pix));
  }

  private static ProviderObjectState stateOfCardSession(AutraCardSession session, Instant now) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(session.cardSessionId()),
        AutraObjectKinds.ofACardSession(),
        session.statusAt(now),
        session.normalizedStatusAt(now),
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjectsOfCardSession(session),
        session.origin(),
        session.updatedAt(),
        sessionFactsOfCardSession(session));
  }

  private static HostedSessionFacts sessionFactsOfCardSession(AutraCardSession session) {
    return new HostedSessionFacts.AnObjectAPayerFinishes(
        session.accountId(),
        new HostedSessionFacts.Party.NoPartyThisProviderPublishes(AutraOperations.NO_CUSTOMER),
        AutraOperations.CARD_SESSION,
        session.createdAt(),
        new HostedSessionFacts.Deadline.ItExpiresAt(session.expiresAt()));
  }

  private static HostedSessionFacts sessionFactsOfPix(AutraPixPayment pix) {
    return new HostedSessionFacts.AnObjectAPayerFinishes(
        pix.accountId(),
        new HostedSessionFacts.Party.NoPartyThisProviderPublishes(AutraOperations.NO_CUSTOMER),
        AutraOperations.PIX_CHECKOUT,
        pix.createdAt(),
        new HostedSessionFacts.Deadline.ItExpiresAt(pix.expiresAt()));
  }

  private static Map<String, String> relatedObjectsOfCardSession(AutraCardSession session) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put("merchantId", session.accountId());
    related.put(AutraFields.DOCUMENT_ID, session.documentId());
    related.put(AutraFields.EXPIRES_AT, CreatePixCheckout.asAutraWritesIt(session.expiresAt()));
    String nickname = session.nickname();
    if (nickname != null) {
      related.put(AutraFields.NICKNAME, nickname);
    }
    AutraStoredCard card = session.storedCard();
    if (card == null) {
      return related;
    }
    putIfNamed(related, AutraFields.SLUG_TOKEN, card.slugToken());
    putIfNamed(related, AutraFields.SLUG_STORED_CARD, card.slugStoredCard());
    putIfNamed(related, AutraFields.TOKEN_EXPIRATION_DATE, card.tokenExpirationDate());
    putIfNamed(related, AutraFields.BRAND, card.brand());
    putIfNamed(related, AutraFields.FIRST4, card.first4());
    putIfNamed(related, AutraFields.LAST4, card.last4());
    return related;
  }

  private static void putIfNamed(Map<String, String> related, String name, @Nullable String value) {
    if (value != null) {
      related.put(name, value);
    }
  }

  private static Map<String, String> relatedObjectsOfPix(AutraPixPayment pix) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put("merchantId", pix.accountId());
    related.put(AutraFields.DOCUMENT_ID, pix.documentId());
    related.put(AutraFields.QR_CODE, pix.qrCode());
    related.put(AutraFields.EXPIRES_AT, CreatePixCheckout.asAutraWritesIt(pix.expiresAt()));
    related.put(AutraFields.SLUG, pix.slug());
    related.put(AutraFields.MUID, pix.muid());
    String orderId = pix.orderId();
    if (orderId != null) {
      related.put(AutraFields.ORDER_ID, orderId);
    }
    String description = pix.description();
    if (description != null) {
      related.put(AutraFields.DESCRIPTION, description);
    }
    return related;
  }

  private static Map<String, String> relatedObjectsOf(
      AutraPayment payment, List<AutraPaymentEvent> timeline) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put("merchantId", payment.accountId());
    related.put(AutraFields.DOCUMENT_ID, payment.documentId());
    related.put(AutraFields.TYPE, payment.type());
    related.put(AutraFields.TRANSACTION_TYPE, payment.transactionType());
    related.put(AutraFields.TRANSACTION_METHOD, payment.transactionMethod());
    related.put(AutraFields.REFUNDED_AMOUNT, payment.refundedAmount());
    related.put(AutraFields.INSTALLMENTS, Integer.toString(payment.installments()));
    related.put(AutraFields.CREATED_AT, CreatePixCheckout.asAutraWritesIt(payment.createdAt()));
    related.put(
        AutraFields.VOID_RETRYABLE, Boolean.toString(AutraPaymentRead.isWorthAVoidNow(payment)));
    related.put(AutraFields.AUTHORIZATION_CODE, payment.authorizationCode());
    related.put(AutraFields.RRN, payment.rrn());
    AutraAcquirerTransaction.asTheInspectorNamesIt(related, payment);
    String orderId = payment.orderId();
    if (orderId != null) {
      related.put(AutraFields.ORDER_ID, orderId);
    }
    putIfNamed(related, AutraFields.SLUG_TOKEN, payment.slugToken());
    putIfNamed(related, AutraFields.SLUG_STORED_CARD, payment.slugStoredCard());
    int step = 0;
    for (AutraPaymentEvent event : timeline) {
      step++;
      related.put(AutraFields.EVENTS + "." + step, asOneLine(event));
    }
    return related;
  }

  private static String asOneLine(AutraPaymentEvent event) {
    String happened =
        event.type()
            + " "
            + event.status()
            + " "
            + event.amount().value()
            + " "
            + CreatePixCheckout.asAutraWritesIt(event.at());
    if (!event.reportsARefusal()) {
      return happened;
    }
    return happened
        + " "
        + event.theCodeItWasRefusedWith()
        + " "
        + event.theMessageItWasRefusedWith();
  }
}
