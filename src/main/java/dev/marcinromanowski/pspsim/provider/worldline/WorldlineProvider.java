package dev.marcinromanowski.pspsim.provider.worldline;

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
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
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
import org.jspecify.annotations.Nullable;

final class WorldlineProvider implements SimulatorProvider {

  @Override
  public List<ContractCheck> compatibilityChecks(CheckJson json, IdGenerator ids) {
    return new WorldlineCompatibilityChecks(json, ids, clock).all();
  }

  static final String NO_WEBHOOK_LEAVES_THIS_MODULE =
      "provider worldline publishes webhooks in full — thirteen payment events, a bare-array"
          + " delivery, five retries over 24 hours and a retry-count header — and this slice"
          + " serves none of them. The operator scoped this module to hosted tokenization, a"
          + " pre-authorization with its capture and its cancel, and an off-session charge, and"
          + " put webhooks outside it. Worldline's own answer to \"is my endpoint reachable and"
          + " are my credentials right\" is two dedicated endpoints, POST"
          + " /v2/{merchantId}/webhooks/validateCredentials and POST"
          + " /v2/{merchantId}/webhooks/sendtest, and those are outside this slice with them";

  private final WorldlineMerchants merchants;
  private final WorldlineTokenizations sessions;
  private final WorldlinePayments payments;
  private final WorldlineIdempotenceKeys keys;
  private final AuditTrail audit;
  private final PaymentMethodFixtures fixtures;
  private final ScenarioRules scenarios;
  private final DelayedDeliveries heldResponses;
  private final SimulatorClock clock;
  private final CreateHostedTokenization createHostedTokenization;
  private final CreatePayment createPayment;
  private final CapturePayment capturePayment;
  private final CancelPayment cancelPayment;
  private final TokenizeOnTheHostedPage tokenizeOnTheHostedPage;
  private final WorldlineWireFaults wireFaults;

  WorldlineProvider(
      WorldlineMerchants merchants,
      WorldlineTokenizations sessions,
      WorldlinePayments payments,
      WorldlineIdempotenceKeys keys,
      AuditTrail audit,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SimulatorClock clock,
      CreateHostedTokenization createHostedTokenization,
      CreatePayment createPayment,
      CapturePayment capturePayment,
      CancelPayment cancelPayment,
      TokenizeOnTheHostedPage tokenizeOnTheHostedPage,
      IdGenerator ids) {
    this.merchants = merchants;
    this.sessions = sessions;
    this.payments = payments;
    this.keys = keys;
    this.audit = audit;
    this.fixtures = fixtures;
    this.scenarios = scenarios;
    this.heldResponses = heldResponses;
    this.clock = clock;
    this.createHostedTokenization = createHostedTokenization;
    this.createPayment = createPayment;
    this.capturePayment = capturePayment;
    this.cancelPayment = cancelPayment;
    this.tokenizeOnTheHostedPage = tokenizeOnTheHostedPage;
    this.wireFaults = new WorldlineWireFaults(ids);
  }

  @Override
  public ProviderId id() {
    return WorldlineApi.WORLDLINE;
  }

  @Override
  public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
    TestRunId testRunId = configuration.testRunId();
    List<ConfiguredAccount> declared = configuration.accounts();
    List<WorldlineMerchant> parsed = new ArrayList<>();
    Set<String> merchantIds = new LinkedHashSet<>();
    Set<String> apiKeys = new LinkedHashSet<>();
    Set<String> rewritten = new LinkedHashSet<>();
    for (ConfiguredAccount named : declared) {
      rewritten.add(named.accountId());
    }
    for (int index = 0; index < declared.size(); index++) {
      String property = AccountProperty.prefixOf(index);
      WorldlineMerchant merchant = WorldlineMerchant.of(property, testRunId, declared.get(index));
      if (!merchantIds.add(merchant.merchantId())) {
        throw new ProviderConfigurationRejectedException(
            property
                + WorldlineMerchant.MERCHANT_ID_PROPERTY
                + " is already configured for another account in this test run");
      }
      if (!apiKeys.add(merchant.apiKey())) {
        throw new ProviderConfigurationRejectedException(
            property
                + WorldlineMerchant.API_KEY_PROPERTY
                + " is already configured for another account in this test run");
      }
      rejectAMerchantAnAccountThisCallLeavesInPlaceHolds(property, merchant, rewritten);
      rejectAnApiKeyAnAccountThisCallLeavesInPlaceHolds(property, merchant, rewritten);
      parsed.add(merchant);
    }
    List<WorldlineMerchant> itWillKeep = List.copyOf(parsed);
    return new ProviderConfigurationPlan(
        WorldlineMerchant.CONFIGURATION_KEYS,
        List.of(),
        () -> merchants.merge(testRunId, itWillKeep));
  }

  private void rejectAMerchantAnAccountThisCallLeavesInPlaceHolds(
      String property, WorldlineMerchant merchant, Set<String> rewritten) {
    merchants
        .ofTheRunHolding(merchant.testRunId(), merchant.merchantId())
        .filter(held -> !rewritten.contains(held.accountId()))
        .ifPresent(
            held -> {
              throw new ProviderConfigurationRejectedException(
                  property
                      + WorldlineMerchant.MERCHANT_ID_PROPERTY
                      + " is already configured for another account in this test run");
            });
  }

  private void rejectAnApiKeyAnAccountThisCallLeavesInPlaceHolds(
      String property, WorldlineMerchant merchant, Set<String> rewritten) {
    for (WorldlineMerchant held : merchants.byApiKey(merchant.apiKey())) {
      if (held.testRunId().equals(merchant.testRunId()) && !rewritten.contains(held.accountId())) {
        throw new ProviderConfigurationRejectedException(
            property
                + WorldlineMerchant.API_KEY_PROPERTY
                + " is already configured for another account in this test run");
      }
    }
  }

  @Override
  public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
    return new AccountRemovalPlan(() -> merchants.remove(testRunId, accountId));
  }

  @Override
  public ProviderVocabulary vocabulary() {
    return WorldlineOperations.VOCABULARY;
  }

  @Override
  public ProviderWireRoutes wireRoutes() {
    return WorldlineWireFaults.ROUTES;
  }

  @Override
  public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
    return wireFaults.theAnswerTo(fault);
  }

  @Override
  public ProviderAccountFields accountFields() {
    return WorldlineMerchant.ACCOUNT_FIELDS;
  }

  @Override
  public ProviderPaymentMethodFields paymentMethodFields() {
    return WorldlineFixtureCards.PAYMENT_METHOD_FIELDS;
  }

  @Override
  public ProviderKeyMechanisms keyMechanisms() {
    return WorldlineKeyMechanisms.MECHANISMS;
  }

  @Override
  public ProviderPairing pairing() {
    return WorldlinePairing.PAIRING;
  }

  @Override
  public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
    return keys.exchanges(query);
  }

  @Override
  public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
    return WorldlineFixtureCards.thePlanFor(fixture);
  }

  @Override
  public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
    return new PaymentMethodFixtureRemoval(() -> {});
  }

  @Override
  public ProviderPersonas personas(IdGenerator ids) {
    return WorldlinePersonas.of(ids);
  }

  @Override
  public PresetExpansion expand(PresetRequest request) {
    return WorldlinePresets.of(request);
  }

  @Override
  public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
    if (scope == ResetScope.ALL) {
      heldResponses.cancelAll(WorldlineApi.WORLDLINE);
      return new ResetOutcome(
          payments.clearAll()
              + sessions.clearAll()
              + keys.clearAll()
              + fixtures.clearAll(WorldlineApi.WORLDLINE)
              + scenarios.clearAll(WorldlineApi.WORLDLINE),
          audit.clearAll(WorldlineApi.WORLDLINE));
    }
    heldResponses.cancel(WorldlineApi.WORLDLINE, testRunId);
    return new ResetOutcome(
        payments.clear(testRunId)
            + sessions.clear(testRunId)
            + keys.clear(testRunId)
            + fixtures.clear(WorldlineApi.WORLDLINE, testRunId)
            + scenarios.clear(WorldlineApi.WORLDLINE, testRunId),
        audit.clear(WorldlineApi.WORLDLINE, testRunId));
  }

  @Override
  public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
    Optional<ProviderObjectState> payment =
        payments.findById(testRunId, providerObjectId).map(WorldlineProvider::stateOf);
    if (payment.isPresent()) {
      return payment;
    }
    Instant now = clock.now(testRunId);
    return sessions
        .findById(testRunId, providerObjectId)
        .map(session -> stateOfSession(session, now));
  }

  @Override
  public List<ProviderObjectState> theObjectsItHoldsUnder(
      TestRunId testRunId, List<String> providerObjectIds) {
    List<String> asked = List.copyOf(providerObjectIds);
    List<ProviderObjectState> held = new ArrayList<>();
    payments.allById(testRunId, asked).forEach(payment -> held.add(stateOf(payment)));
    Instant now = clock.now(testRunId);
    sessions.allById(testRunId, asked).forEach(session -> held.add(stateOfSession(session, now)));
    return List.copyOf(held);
  }

  @Override
  public ObjectActions objectActions() {
    return WorldlineObjectActions.DECLARED;
  }

  @Override
  public ProviderOriginationFields originationFields() {
    return WorldlineOrigination.DECLARED;
  }

  @Override
  public OriginationOutcome originate(OriginationRequest request) {
    TestRunId testRunId = request.testRunId();
    Optional<WorldlineMerchant> merchant = merchants.find(testRunId, request.accountId());
    if (merchant.isEmpty()) {
      return new OriginationOutcome.NoSuchAccount();
    }
    Instant now = clock.now(testRunId);
    return WorldlineOrigination.outcomeOf(
        theWayItWasStarted(request, merchant.get(), testRunId, now), request);
  }

  private WorldlineOperationResult theWayItWasStarted(
      OriginationRequest request, WorldlineMerchant merchant, TestRunId testRunId, Instant now) {
    if (WorldlineOrigination.A_TOKENIZATION.equals(request.startedAs())) {
      return createHostedTokenization.handle(
          WorldlineOrigination.tokenizationCallOf(request, merchant),
          Map.of(),
          testRunId,
          merchant,
          now,
          request.askedBy());
    }
    return createPayment.handle(
        WorldlineOrigination.paymentCallOf(request, merchant),
        Map.of(),
        testRunId,
        merchant,
        now,
        request.askedBy());
  }

  @Override
  public HostedPageContract hostedPages() {
    return new HostedPageContract.ObjectsAPayerFinishes(
        List.of(
            new HostedPageContract.AnObjectItKeepsWaiting("a hosted tokenization session", true)));
  }

  @Override
  public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
    Optional<WorldlineTokenization> session = sessions.findById(testRunId, providerObjectId);
    if (session.isPresent()) {
      return tokenizeOnTheHostedPage.offerOn(session.get(), clock.now(testRunId));
    }
    return new HostedCompletionOffer.NotOffered(
        WorldlineApi.WORLDLINE, thePayerStepAPaymentDoesNotHave());
  }

  static String thePayerStepAPaymentDoesNotHave() {
    return "provider worldline publishes one page a payer of this slice ever meets, the hosted"
        + " tokenization page, and a payment is not it: the payments of this module are charged"
        + " against a token that page already produced, off-session, with no payer present. The"
        + " other page worldline publishes is the hosted checkout, POST"
        + " /v2/{merchantId}/hostedcheckouts, and that route is outside this slice";
  }

  @Override
  public HostedCompletionOutcome complete(HostedCompletionRequest request) {
    TestRunId testRunId = request.testRunId();
    Optional<WorldlineTokenization> session =
        sessions.findById(testRunId, request.providerObjectId());
    if (session.isEmpty()) {
      return new HostedCompletionOutcome.NoSuchObject();
    }
    return tokenizeOnTheHostedPage.complete(request, session.get(), clock.now(testRunId));
  }

  @Override
  public ObjectActionOutcome act(ObjectActionRequest request) {
    TestRunId testRunId = request.testRunId();
    Optional<WorldlinePayment> found = payments.findById(testRunId, request.providerObjectId());
    if (found.isEmpty()) {
      return new ObjectActionOutcome.NoSuchObject();
    }
    WorldlinePayment payment = found.get();
    Optional<WorldlineMerchant> merchant =
        merchants.ofTheRunHolding(testRunId, payment.merchantId());
    if (merchant.isEmpty()) {
      return WorldlineObjectActions.theRunConfiguresNoMerchantFor(payment);
    }
    Instant now = clock.now(testRunId);
    WorldlineApiCall call = WorldlineObjectActions.callOf(request, merchant.get());
    WorldlineOperationResult result =
        request.action() == ObjectAction.CAPTURE
            ? capturePayment.handle(
                call, Map.of(), testRunId, merchant.get(), request.providerObjectId(), now)
            : cancelPayment.handle(
                call, Map.of(), testRunId, merchant.get(), request.providerObjectId(), now);
    return WorldlineObjectActions.outcomeOf(result, payment);
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
              .or(() -> sessions.placeOf(testRunId, cursor))
              .orElse(null);
      if (after == null) {
        return new ProviderObjectPage.NoSuchCursor();
      }
    }
    List<PlacedProviderObject> placed = new ArrayList<>();
    for (ArrivalOf<WorldlinePayment> arrived :
        payments.newestFirst(testRunId, after, query.limit())) {
      WorldlinePayment payment = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), payment.paymentId()), stateOf(payment)));
    }
    Instant now = clock.now(testRunId);
    for (ArrivalOf<WorldlineTokenization> arrived :
        sessions.newestFirst(testRunId, after, query.limit())) {
      WorldlineTokenization session = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), session.hostedTokenizationId()),
              stateOfSession(session, now)));
    }
    return ProviderObjectPages.newestFirst(placed, query.limit());
  }

  @Override
  public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
    return merchants.find(testRunId, accountId).map(WorldlineMerchant::disclosure);
  }

  @Override
  public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    throw new WebhookNotSchedulableException(NO_WEBHOOK_LEAVES_THIS_MODULE);
  }

  private static ProviderObjectState stateOf(WorldlinePayment payment) {
    return new ProviderObjectState(
        payment.identity(),
        WorldlineObjectKinds.of(payment),
        payment.nativeStatus(),
        payment.normalizedStatus(),
        payment.amount(),
        payment.currencyCode(),
        payment.ledger(),
        payment.authorizationOpen(),
        payment.capturesAccepted(),
        payment.refundsAccepted(),
        relatedObjectsOf(payment),
        payment.origin(),
        payment.updatedAt());
  }

  private ProviderObjectState stateOfSession(WorldlineTokenization session, Instant now) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(session.hostedTokenizationId()),
        WorldlineObjectKinds.ofATokenization(),
        session.statusAt(now),
        session.normalizedStatusAt(now),
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjectsOfSession(session),
        session.origin(),
        session.updatedAt(),
        sessionFactsOf(session));
  }

  private static HostedSessionFacts sessionFactsOf(WorldlineTokenization session) {
    return new HostedSessionFacts.AnObjectAPayerFinishes(
        session.accountId(),
        new HostedSessionFacts.Party.NoPartyThisProviderPublishes(
            WorldlineOperations.NO_CUSTOMER_ON_A_TOKENIZATION),
        WorldlineOperations.CREATE_HOSTED_TOKENIZATION,
        session.createdAt(),
        new HostedSessionFacts.Deadline.ItExpiresAt(session.expiresAt()));
  }

  private static Map<String, String> relatedObjectsOf(WorldlinePayment payment) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put(WorldlineMerchant.MERCHANT_ID_PROPERTY, payment.merchantId());
    related.put(WorldlineFields.AUTHORIZATION_MODE, payment.authorizationMode().name());
    related.put(
        WorldlineFields.STATUS_CATEGORY + "." + WorldlineFields.STATUS_CODE,
        Integer.toString(payment.statusCode()));
    putIfNamed(related, WorldlineFields.MERCHANT_REFERENCE, payment.merchantReference());
    putIfNamed(related, WorldlineFields.MERCHANT_CUSTOMER_ID, payment.merchantCustomerId());
    putIfNamed(related, WorldlineFields.TOKEN, payment.token());
    putIfNamed(related, WorldlineFields.HOSTED_TOKENIZATION_ID, payment.hostedTokenizationId());
    putIfNamed(related, WorldlineFields.CARD_NUMBER, payment.cardNumber());
    putIfNamed(related, WorldlineFields.EXPIRY_DATE, payment.expiryDate());
    return related;
  }

  private static Map<String, String> relatedObjectsOfSession(WorldlineTokenization session) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put(WorldlineMerchant.MERCHANT_ID_PROPERTY, session.merchantId());
    related.put(
        WorldlineFields.EXPIRES_AT, WorldlineSignature.asWorldlineWritesIt(session.expiresAt()));
    putIfNamed(related, WorldlineFields.TOKEN, session.token());
    putIfNamed(related, WorldlineFields.CARD_NUMBER, session.cardNumber());
    putIfNamed(related, WorldlineFields.EXPIRY_DATE, session.expiryDate());
    return related;
  }

  private static void putIfNamed(Map<String, String> related, String name, @Nullable String value) {
    if (value != null) {
      related.put(name, value);
    }
  }
}
