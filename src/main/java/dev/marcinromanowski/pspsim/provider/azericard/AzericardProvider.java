package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
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
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
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
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class AzericardProvider implements SimulatorProvider {

  @Override
  public List<ContractCheck> compatibilityChecks(CheckJson json, IdGenerator ids) {
    return new AzericardCompatibilityChecks(json, ids, clock).all();
  }

  static final String NO_PAGE_TO_COMPLETE =
      "provider azericard leaves a payer step on a payment page and on nothing else, and this test"
          + " run holds no azericard payment page under that id. A payment page is what a browser"
          + " post of the signed form to "
          + AzericardGatewayController.CGI_LINK
          + " or "
          + AzericardGatewayController.TOKEN_CGI_LINK
          + " opens when the form carries no card; an INT_REF names the authorization a page or a"
          + " server-to-server request already decided, and a TOKEN names a stored card, and"
          + " neither has a page left for a payer to finish.";

  static final String A_PAYMENT_PAGE = "a payment page";

  static final String A_STORED_CARD_IS_SUCCEEDED =
      AzericardOutcomes.ACTION_SUCCEEDED
          + AzericardPayment.NATIVE_STATUS_SEPARATOR
          + AzericardOutcomes.RC_APPROVED;

  private final AzericardTerminals terminals;
  private final AuditTrail audit;
  private final AzericardPayments payments;
  private final AzericardStoredCards cards;
  private final PaymentMethodFixtures fixtures;
  private final ScenarioRules scenarios;
  private final DelayedDeliveries heldResponses;
  private final AzericardCallbacks callbacks;
  private final WebhookDispatcher callbackDeliveries;
  private final SimulatorClock clock;
  private final IdGenerator ids;
  private final Authorize authorize;
  private final CompleteOrReverse completeOrReverse;
  private final AzericardPayerSessions sessions;
  private final AzericardPayerCheckout checkout;

  AzericardProvider(
      AzericardTerminals terminals,
      AuditTrail audit,
      AzericardPayments payments,
      AzericardStoredCards cards,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      AzericardCallbacks callbacks,
      WebhookDispatcher callbackDeliveries,
      SimulatorClock clock,
      IdGenerator ids,
      Authorize authorize,
      CompleteOrReverse completeOrReverse,
      AzericardPayerSessions sessions,
      AzericardPayerCheckout checkout) {
    this.terminals = terminals;
    this.audit = audit;
    this.payments = payments;
    this.cards = cards;
    this.fixtures = fixtures;
    this.scenarios = scenarios;
    this.heldResponses = heldResponses;
    this.callbacks = callbacks;
    this.callbackDeliveries = callbackDeliveries;
    this.clock = clock;
    this.ids = ids;
    this.authorize = authorize;
    this.completeOrReverse = completeOrReverse;
    this.sessions = sessions;
    this.checkout = checkout;
  }

  @Override
  public ProviderId id() {
    return AzericardGateway.AZERICARD;
  }

  @Override
  public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
    TestRunId testRunId = configuration.testRunId();
    List<ConfiguredAccount> declared = configuration.accounts();
    List<AzericardTerminal> parsed = new ArrayList<>();
    List<StoryKeyWitness> witnesses = new ArrayList<>();
    Set<String> terminalIds = new LinkedHashSet<>();
    Set<String> rewritten = new LinkedHashSet<>();
    for (ConfiguredAccount named : declared) {
      rewritten.add(named.accountId());
    }
    for (int index = 0; index < declared.size(); index++) {
      String property = AccountProperty.prefixOf(index);
      AzericardTerminal terminal = AzericardTerminal.of(property, testRunId, declared.get(index));
      if (!terminalIds.add(terminal.terminalId())) {
        throw new ProviderConfigurationRejectedException(
            property
                + AzericardTerminal.TERMINAL_PROPERTY
                + " is already configured for another account in this test run");
      }
      rejectATerminalAnAccountThisCallLeavesInPlaceHolds(property, terminal, rewritten);
      parsed.add(terminal);
      witnesses.add(
          new StoryKeyWitness(
              property + AzericardTerminal.TERMINAL_PROPERTY,
              AzericardStoryKey.theLongestPaymentKeyOf(terminal.terminalId())));
    }
    List<AzericardTerminal> itWillKeep = List.copyOf(parsed);
    return new ProviderConfigurationPlan(
        AzericardTerminal.CONFIGURATION_KEYS,
        List.copyOf(witnesses),
        () -> terminals.merge(testRunId, itWillKeep));
  }

  private void rejectATerminalAnAccountThisCallLeavesInPlaceHolds(
      String property, AzericardTerminal terminal, Set<String> rewritten) {
    terminals
        .ofTheRunHolding(terminal.testRunId(), terminal.terminalId())
        .filter(held -> !rewritten.contains(held.accountId()))
        .ifPresent(
            held -> {
              throw new ProviderConfigurationRejectedException(
                  property
                      + AzericardTerminal.TERMINAL_PROPERTY
                      + " is already configured for another account in this test run");
            });
  }

  @Override
  public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
    return new AccountRemovalPlan(() -> terminals.remove(testRunId, accountId));
  }

  @Override
  public ProviderVocabulary vocabulary() {
    return AzericardOperations.VOCABULARY;
  }

  @Override
  public ProviderWireRoutes wireRoutes() {
    return AzericardWireFaults.ROUTES;
  }

  @Override
  public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
    return AzericardWireFaults.THE_ANSWER;
  }

  @Override
  public ProviderAccountFields accountFields() {
    return AzericardTerminal.ACCOUNT_FIELDS;
  }

  @Override
  public ProviderPaymentMethodFields paymentMethodFields() {
    return AzericardFixtureCards.PAYMENT_METHOD_FIELDS;
  }

  @Override
  public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
    AzericardTerminal terminal =
        AzericardFixtureCards.theTerminalItIsSeededFor(
            fixture, terminals.find(fixture.testRunId(), fixture.merchantId()).orElse(null));
    return new PaymentMethodFixturePlan(
        seeded -> {
          if (!cards.claim(
              AzericardStoredCard.seededBy(
                  seeded.testRunId(),
                  AzericardFixtureCards.tokenOf(seeded),
                  terminal.terminalId(),
                  terminal.accountId(),
                  seeded.fixtureId(),
                  fixture.askedBy(),
                  seeded.createdAt()))) {
            throw new ProviderFixtureRejectedException(
                AzericardFixtureCards.TOKEN_PROPERTY,
                AzericardFixtureCards.A_TOKEN_IS_CLAIMED_ONCE);
          }
        });
  }

  @Override
  public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
    return new PaymentMethodFixtureRemoval(
        () -> cards.forgetSeeded(fixture.testRunId(), fixture.fixtureId()));
  }

  @Override
  public ProviderKeyMechanisms keyMechanisms() {
    return AzericardKeyMechanisms.MECHANISMS;
  }

  @Override
  public ProviderPairing pairing() {
    return AzericardPairing.PAIRING;
  }

  @Override
  public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
    return payments.exchanges(query);
  }

  @Override
  public ProviderPersonas personas(IdGenerator ids) {
    return AzericardPersonas.of(ids);
  }

  @Override
  public PresetExpansion expand(PresetRequest request) {
    return AzericardPresets.of(request);
  }

  @Override
  public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
    if (scope == ResetScope.ALL) {
      heldResponses.cancelAll(AzericardGateway.AZERICARD);
      return new ResetOutcome(
          sessions.clearAll()
              + payments.clearAll()
              + cards.clearAll()
              + fixtures.clearAll(AzericardGateway.AZERICARD)
              + callbackDeliveries.clearAll(AzericardGateway.AZERICARD)
              + scenarios.clearAll(AzericardGateway.AZERICARD),
          audit.clearAll(AzericardGateway.AZERICARD));
    }
    heldResponses.cancel(AzericardGateway.AZERICARD, testRunId);
    return new ResetOutcome(
        sessions.clear(testRunId)
            + payments.clear(testRunId)
            + cards.clear(testRunId)
            + fixtures.clear(AzericardGateway.AZERICARD, testRunId)
            + callbackDeliveries.clear(AzericardGateway.AZERICARD, testRunId)
            + scenarios.clear(AzericardGateway.AZERICARD, testRunId),
        audit.clear(AzericardGateway.AZERICARD, testRunId));
  }

  @Override
  public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
    Optional<AzericardPayment> payment = payments.findByIntRef(testRunId, providerObjectId);
    if (payment.isPresent()) {
      return payment.map(AzericardProvider::stateOf);
    }
    Optional<AzericardPayerSession> session = sessions.find(testRunId, providerObjectId);
    if (session.isPresent()) {
      return session.map(this::stateOf);
    }
    return cards.findByToken(testRunId, providerObjectId).map(AzericardProvider::stateOf);
  }

  @Override
  public List<ProviderObjectState> theObjectsItHoldsUnder(
      TestRunId testRunId, List<String> providerObjectIds) {
    List<String> asked = List.copyOf(providerObjectIds);
    List<ProviderObjectState> held = new ArrayList<>();
    payments.allByIntRef(testRunId, asked).forEach(payment -> held.add(stateOf(payment)));
    List<AzericardPayerSession> sessionsItHolds = sessions.allById(testRunId, asked);
    Map<String, AzericardPayment> thePaymentsTheySent =
        thePaymentsTheySent(testRunId, sessionsItHolds);
    sessionsItHolds.forEach(
        session ->
            held.add(
                AzericardPayerSessionObject.stateOf(
                    session, thePaymentsTheySent.get(session.intRef()))));
    cards.allByToken(testRunId, asked).forEach(card -> held.add(stateOf(card)));
    return List.copyOf(held);
  }

  private Map<String, AzericardPayment> thePaymentsTheySent(
      TestRunId testRunId, List<AzericardPayerSession> held) {
    return payments
        .allByIntRef(
            testRunId,
            held.stream()
                .map(AzericardPayerSession::intRef)
                .filter(Objects::nonNull)
                .distinct()
                .toList())
        .stream()
        .collect(Collectors.toMap(AzericardPayment::intRef, payment -> payment));
  }

  private ProviderObjectState stateOf(AzericardPayerSession session) {
    String intRef = session.intRef();
    return AzericardPayerSessionObject.stateOf(
        session,
        intRef == null ? null : payments.findByIntRef(session.testRunId(), intRef).orElse(null));
  }

  @Override
  public ObjectActions objectActions() {
    return AzericardObjectActions.DECLARED;
  }

  @Override
  public ProviderOriginationFields originationFields() {
    return AzericardOrigination.DECLARED;
  }

  @Override
  public OriginationOutcome originate(OriginationRequest request) {
    TestRunId testRunId = request.testRunId();
    Optional<AzericardTerminal> terminal = terminals.find(testRunId, request.accountId());
    if (terminal.isEmpty()) {
      return new OriginationOutcome.NoSuchAccount();
    }
    Instant now = clock.now(testRunId);
    AzericardMessage message = AzericardOrigination.messageOf(request, terminal.get(), ids, now);
    AzericardRequiredField unusable = message.unusableFieldOfARequestThisSimulatorPlacedItself();
    if (unusable != null) {
      return new OriginationOutcome.RefusedByTheProvider(AzericardErrorPage.messageFor(unusable));
    }
    return AzericardOrigination.outcomeOf(
        authorize.handle(message, Map.of(), testRunId, terminal.get(), now, request.askedBy()),
        request);
  }

  @Override
  public HostedPageContract hostedPages() {
    return new HostedPageContract.ObjectsAPayerFinishes(
        List.of(new HostedPageContract.AnObjectItKeepsWaiting(A_PAYMENT_PAGE, true)));
  }

  @Override
  public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
    return sessions
        .find(testRunId, providerObjectId)
        .map(checkout::offeredOn)
        .orElseGet(
            () ->
                new HostedCompletionOffer.NotOffered(
                    AzericardGateway.AZERICARD, NO_PAGE_TO_COMPLETE));
  }

  @Override
  public HostedCompletionOutcome complete(HostedCompletionRequest request) {
    Optional<AzericardPayerSession> session =
        sessions.find(request.testRunId(), request.providerObjectId());
    if (session.isPresent()) {
      return checkout.complete(session.get(), request);
    }
    if (state(request.testRunId(), request.providerObjectId()).isPresent()) {
      return new HostedCompletionOutcome.NotOffered(NO_PAGE_TO_COMPLETE);
    }
    return new HostedCompletionOutcome.NoSuchObject();
  }

  @Override
  public ObjectActionOutcome act(ObjectActionRequest request) {
    TestRunId testRunId = request.testRunId();
    Optional<AzericardPayment> found = payments.findByIntRef(testRunId, request.providerObjectId());
    if (found.isEmpty()) {
      if (sessions.find(testRunId, request.providerObjectId()).isPresent()) {
        return new ObjectActionOutcome.RefusedByTheProvider(
            AzericardPayerSessionObject.A_PAGE_IS_NOT_A_TRANSACTION);
      }
      if (cards.findByToken(testRunId, request.providerObjectId()).isPresent()) {
        return new ObjectActionOutcome.RefusedByTheProvider(
            AzericardObjectActions.A_STORED_CARD_IS_NOT_A_TRANSACTION);
      }
      return new ObjectActionOutcome.NoSuchObject();
    }
    AzericardPayment payment = found.get();
    Optional<AzericardTerminal> terminal =
        terminals.ofTheRunHolding(testRunId, payment.terminalId());
    if (terminal.isEmpty()) {
      return AzericardObjectActions.theRunConfiguresNoTerminalFor(payment);
    }
    Instant now = clock.now(testRunId);
    AzericardMessage message = AzericardObjectActions.messageOf(request, payment, ids, now);
    return AzericardObjectActions.outcomeOf(
        completeOrReverse.handle(message, Map.of(), testRunId, terminal.get(), now), payment);
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
              .or(() -> cards.placeOf(testRunId, cursor))
              .or(() -> sessions.placeOf(testRunId, cursor))
              .orElse(null);
      if (after == null) {
        return new ProviderObjectPage.NoSuchCursor();
      }
    }
    List<PlacedProviderObject> placed = new ArrayList<>();
    for (ArrivalOf<AzericardPayment> arrived :
        payments.newestFirst(testRunId, after, query.limit())) {
      AzericardPayment payment = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), payment.intRef()), stateOf(payment)));
    }
    for (ArrivalOf<AzericardStoredCard> arrived :
        cards.newestFirst(testRunId, after, query.limit())) {
      AzericardStoredCard card = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), card.token()), stateOf(card)));
    }
    List<ArrivalOf<AzericardPayerSession>> arrivedSessions =
        sessions.newestFirst(testRunId, after, query.limit());
    Map<String, AzericardPayment> thePaymentsTheySent =
        thePaymentsTheySent(testRunId, arrivedSessions.stream().map(ArrivalOf::row).toList());
    for (ArrivalOf<AzericardPayerSession> arrived : arrivedSessions) {
      AzericardPayerSession session = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), session.sessionId()),
              AzericardPayerSessionObject.stateOf(
                  session, thePaymentsTheySent.get(session.intRef()))));
    }
    return ProviderObjectPages.newestFirst(placed, query.limit());
  }

  @Override
  public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
    return terminals.find(testRunId, accountId).map(AzericardTerminal::disclosure);
  }

  private static ProviderObjectState stateOf(AzericardPayment payment) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(payment.intRef()),
        AzericardObjectKinds.of(payment),
        payment.nativeStatus(),
        payment.normalizedStatus(),
        payment.amount(),
        payment.currency(),
        payment.ledger(),
        payment.authorizationOpen(),
        payment.capturesAccepted(),
        payment.reversalsAccepted(),
        relatedObjectsOf(payment),
        payment.origin(),
        payment.updatedAt());
  }

  private static ProviderObjectState stateOf(AzericardStoredCard card) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(card.token()),
        AzericardObjectKinds.ofASavedCard(),
        A_STORED_CARD_IS_SUCCEEDED,
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjectsOf(card),
        card.origin(),
        card.createdAt());
  }

  private static Map<String, String> relatedObjectsOf(AzericardStoredCard card) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put("merchantId", card.merchantId());
    related.put("terminal", card.terminalId());
    String order = card.order();
    if (order != null) {
      related.put("order", order);
    }
    String storedBy = card.storedByIntRef();
    if (storedBy != null) {
      related.put("storedBy", storedBy);
    }
    String fixtureId = card.fixtureId();
    if (fixtureId != null) {
      related.put("fixtureId", fixtureId);
    }
    return related;
  }

  private static Map<String, String> relatedObjectsOf(AzericardPayment payment) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put("merchantId", payment.merchantId());
    related.put("terminal", payment.terminalId());
    related.put("order", payment.order());
    related.put("rrn", payment.rrn());
    String approval = payment.approval();
    if (approval != null) {
      related.put("approval", approval);
    }
    String token = payment.token();
    if (token != null) {
      related.put("token", token);
    }
    return related;
  }

  @Override
  public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    return callbacks.schedule(request);
  }
}
