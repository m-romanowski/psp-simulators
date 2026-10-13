package dev.marcinromanowski.pspsim.provider.stripe;

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
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class StripeProvider implements SimulatorProvider {

  @Override
  public List<ContractCheck> compatibilityChecks(CheckJson json, IdGenerator ids) {
    return new StripeCompatibilityChecks(json, ids).all();
  }

  static final String NO_PAGE_TO_COMPLETE =
      "provider stripe hosts a payer-facing page as a Checkout Session, and this build serves no"
          + " route of that family. What it does serve is the PaymentIntent, whose payer step is"
          + " the published confirmation call "
          + StripeApiController.PAYMENT_INTENTS
          + "/{intentId}/confirm, a route a caller reaches directly and which this simulator"
          + " already answers. Nothing here is left pending for a second surface to finish.";

  private static final boolean A_REFUND_HAS_NO_LEDGER_OF_ITS_OWN = false;

  private final StripeAccounts accounts;
  private final StripePaymentIntents intents;
  private final StripeCharges charges;
  private final StripeRefunds refunds;
  private final StripeCustomers customers;
  private final StripePaymentMethods paymentMethods;
  private final StripeSetupIntents setupIntents;
  private final StripeEphemeralKeys ephemeralKeys;
  private final StripeIdempotencyKeys keys;
  private final AuditTrail audit;
  private final PaymentMethodFixtures fixtures;
  private final ScenarioRules scenarios;
  private final DelayedDeliveries heldResponses;
  private final SimulatorClock clock;
  private final WebhookDispatcher eventDeliveries;
  private final StripeWebhooks events;
  private final CapturePaymentIntent capturePaymentIntent;
  private final CancelPaymentIntent cancelPaymentIntent;
  private final CreateRefund createRefund;
  private final CreatePaymentIntent createPaymentIntent;
  private final CreateSetupIntent createSetupIntent;
  private final ConfirmSetupIntent confirmSetupIntent;

  StripeProvider(
      StripeAccounts accounts,
      StripePaymentIntents intents,
      StripeCharges charges,
      StripeRefunds refunds,
      StripeCustomers customers,
      StripePaymentMethods paymentMethods,
      StripeSetupIntents setupIntents,
      StripeEphemeralKeys ephemeralKeys,
      StripeIdempotencyKeys keys,
      AuditTrail audit,
      PaymentMethodFixtures fixtures,
      ScenarioRules scenarios,
      DelayedDeliveries heldResponses,
      SimulatorClock clock,
      WebhookDispatcher eventDeliveries,
      StripeWebhooks events,
      CapturePaymentIntent capturePaymentIntent,
      CancelPaymentIntent cancelPaymentIntent,
      CreateRefund createRefund,
      CreatePaymentIntent createPaymentIntent,
      CreateSetupIntent createSetupIntent,
      ConfirmSetupIntent confirmSetupIntent) {
    this.accounts = accounts;
    this.intents = intents;
    this.charges = charges;
    this.refunds = refunds;
    this.customers = customers;
    this.paymentMethods = paymentMethods;
    this.setupIntents = setupIntents;
    this.ephemeralKeys = ephemeralKeys;
    this.keys = keys;
    this.audit = audit;
    this.fixtures = fixtures;
    this.scenarios = scenarios;
    this.heldResponses = heldResponses;
    this.clock = clock;
    this.eventDeliveries = eventDeliveries;
    this.events = events;
    this.capturePaymentIntent = capturePaymentIntent;
    this.cancelPaymentIntent = cancelPaymentIntent;
    this.createRefund = createRefund;
    this.createPaymentIntent = createPaymentIntent;
    this.createSetupIntent = createSetupIntent;
    this.confirmSetupIntent = confirmSetupIntent;
  }

  @Override
  public ProviderId id() {
    return StripeApi.STRIPE;
  }

  @Override
  public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
    TestRunId testRunId = configuration.testRunId();
    List<ConfiguredAccount> declared = configuration.accounts();
    List<StripeAccount> parsed = new ArrayList<>();
    Set<String> secretKeys = new LinkedHashSet<>();
    Set<String> rewritten = new LinkedHashSet<>();
    for (ConfiguredAccount named : declared) {
      rewritten.add(named.accountId());
    }
    for (int index = 0; index < declared.size(); index++) {
      String property = AccountProperty.prefixOf(index);
      StripeAccount account = StripeAccount.of(property, testRunId, declared.get(index));
      if (!secretKeys.add(account.secretKey())) {
        throw new ProviderConfigurationRejectedException(
            property
                + StripeAccount.SECRET_KEY_PROPERTY
                + " is already configured for another account in this test run");
      }
      rejectASecretKeyAnAccountThisCallLeavesInPlaceHolds(property, account, rewritten);
      parsed.add(account);
    }
    List<StripeAccount> itWillKeep = List.copyOf(parsed);
    return new ProviderConfigurationPlan(
        StripeAccount.CONFIGURATION_KEYS, List.of(), () -> accounts.merge(testRunId, itWillKeep));
  }

  private void rejectASecretKeyAnAccountThisCallLeavesInPlaceHolds(
      String property, StripeAccount account, Set<String> rewritten) {
    accounts
        .ofTheRunHolding(account.testRunId(), account.secretKey())
        .filter(held -> !rewritten.contains(held.accountId()))
        .ifPresent(
            held -> {
              throw new ProviderConfigurationRejectedException(
                  property
                      + StripeAccount.SECRET_KEY_PROPERTY
                      + " is already configured for another account in this test run");
            });
  }

  @Override
  public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
    return new AccountRemovalPlan(() -> accounts.remove(testRunId, accountId));
  }

  @Override
  public ProviderVocabulary vocabulary() {
    return StripeOperations.VOCABULARY;
  }

  @Override
  public ProviderWireRoutes wireRoutes() {
    return StripeWireFaults.ROUTES;
  }

  @Override
  public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
    return StripeWireFaults.theAnswerTo(fault);
  }

  @Override
  public ProviderAccountFields accountFields() {
    return StripeAccount.ACCOUNT_FIELDS;
  }

  @Override
  public ProviderPaymentMethodFields paymentMethodFields() {
    return StripeFixtureCards.PAYMENT_METHOD_FIELDS;
  }

  @Override
  public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
    StripePaymentMethodType type = StripeFixtureCards.theTypeStripePublishesFor(fixture);
    StripeAccount account =
        StripeFixtureCards.theAccountThatWillServeTheMethod(
            fixture, accounts.find(fixture.testRunId(), fixture.merchantId()).orElse(null));
    String customerId = StripeFixtureCards.customerIdOf(fixture);
    String paymentMethodId = StripeFixtureCards.paymentMethodIdOf(fixture);
    return new PaymentMethodFixturePlan(
        seeded -> {
          if (customerId != null) {
            customers.insertIfAbsent(
                new StripeCustomer(
                    seeded.testRunId(),
                    customerId,
                    account.accountId(),
                    null,
                    null,
                    fixture.askedBy(),
                    seeded.createdAt(),
                    seeded.createdAt()));
          }
          paymentMethods.insertIfAbsent(
              new StripePaymentMethod(
                  seeded.testRunId(),
                  paymentMethodId,
                  account.accountId(),
                  customerId,
                  type,
                  seeded.card(),
                  null,
                  fixture.askedBy(),
                  seeded.createdAt(),
                  seeded.createdAt()));
        });
  }

  @Override
  public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
    String paymentMethodId = StripeFixtureCards.paymentMethodIdOf(fixture);
    return new PaymentMethodFixtureRemoval(
        () -> paymentMethods.forget(fixture.testRunId(), paymentMethodId));
  }

  @Override
  public ProviderKeyMechanisms keyMechanisms() {
    return StripeKeyMechanisms.MECHANISMS;
  }

  @Override
  public ProviderPairing pairing() {
    return StripePairing.PAIRING;
  }

  @Override
  public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
    return keys.exchanges(query);
  }

  @Override
  public ProviderPersonas personas(IdGenerator ids) {
    return StripePersonas.of(ids);
  }

  @Override
  public PresetExpansion expand(PresetRequest request) {
    return StripePresets.of(request);
  }

  @Override
  public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
    if (scope == ResetScope.ALL) {
      heldResponses.cancelAll(StripeApi.STRIPE);
      keys.clearAll();
      ephemeralKeys.clearAll();
      ResetOutcome cleared =
          new ResetOutcome(
              refunds.clearAll()
                  + setupIntents.clearAll()
                  + paymentMethods.clearAll()
                  + customers.clearAll()
                  + intents.clearAll()
                  + fixtures.clearAll(StripeApi.STRIPE)
                  + eventDeliveries.clearAll(StripeApi.STRIPE)
                  + scenarios.clearAll(StripeApi.STRIPE),
              audit.clearAll(StripeApi.STRIPE));
      charges.clearAll();
      return cleared;
    }
    heldResponses.cancel(StripeApi.STRIPE, testRunId);
    keys.clear(testRunId);
    ephemeralKeys.clear(testRunId);
    ResetOutcome cleared =
        new ResetOutcome(
            refunds.clear(testRunId)
                + setupIntents.clear(testRunId)
                + paymentMethods.clear(testRunId)
                + customers.clear(testRunId)
                + intents.clear(testRunId)
                + fixtures.clear(StripeApi.STRIPE, testRunId)
                + eventDeliveries.clear(StripeApi.STRIPE, testRunId)
                + scenarios.clear(StripeApi.STRIPE, testRunId),
            audit.clear(StripeApi.STRIPE, testRunId));
    charges.clear(testRunId);
    return cleared;
  }

  @Override
  public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
    Instant now = clock.now(testRunId);
    return intents
        .find(testRunId, providerObjectId)
        .map(intent -> stateOf(intent, now))
        .or(() -> refunds.find(testRunId, providerObjectId).map(StripeProvider::stateOf))
        .or(() -> customers.find(testRunId, providerObjectId).map(StripeCustomerObject::stateOf))
        .or(
            () ->
                paymentMethods
                    .find(testRunId, providerObjectId)
                    .map(StripePaymentMethodObject::stateOf))
        .or(
            () ->
                setupIntents
                    .find(testRunId, providerObjectId)
                    .map(StripeSetupIntentObject::stateOf));
  }

  @Override
  public List<ProviderObjectState> theObjectsItHoldsUnder(
      TestRunId testRunId, List<String> providerObjectIds) {
    List<String> asked = List.copyOf(providerObjectIds);
    Instant now = clock.now(testRunId);
    List<ProviderObjectState> held = new ArrayList<>();
    intents.allById(testRunId, asked).forEach(intent -> held.add(stateOf(intent, now)));
    refunds.allById(testRunId, asked).forEach(refund -> held.add(stateOf(refund)));
    customers
        .allById(testRunId, asked)
        .forEach(customer -> held.add(StripeCustomerObject.stateOf(customer)));
    paymentMethods
        .allById(testRunId, asked)
        .forEach(method -> held.add(StripePaymentMethodObject.stateOf(method)));
    setupIntents
        .allById(testRunId, asked)
        .forEach(intent -> held.add(StripeSetupIntentObject.stateOf(intent)));
    return List.copyOf(held);
  }

  @Override
  public ProviderObjectPage objects(ProviderObjectQuery query) {
    TestRunId testRunId = query.testRunId();
    String cursor = query.after();
    ProviderObjectCursor after = null;
    if (cursor != null) {
      after =
          intents
              .placeOf(testRunId, cursor)
              .or(() -> refunds.placeOf(testRunId, cursor))
              .or(() -> customers.placeOf(testRunId, cursor))
              .or(() -> paymentMethods.placeOf(testRunId, cursor))
              .or(() -> setupIntents.placeOf(testRunId, cursor))
              .orElse(null);
      if (after == null) {
        return new ProviderObjectPage.NoSuchCursor();
      }
    }
    Instant now = clock.now(testRunId);
    List<PlacedProviderObject> placed = new ArrayList<>();
    for (ArrivalOf<StripePaymentIntent> arrived :
        intents.newestFirst(testRunId, after, query.limit())) {
      StripePaymentIntent intent = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), intent.intentId()),
              stateOf(intent, now)));
    }
    for (ArrivalOf<StripeRefund> arrived : refunds.newestFirst(testRunId, after, query.limit())) {
      StripeRefund refund = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), refund.refundId()), stateOf(refund)));
    }
    for (ArrivalOf<StripeCustomer> arrived :
        customers.newestFirst(testRunId, after, query.limit())) {
      StripeCustomer customer = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), customer.customerId()),
              StripeCustomerObject.stateOf(customer)));
    }
    for (ArrivalOf<StripePaymentMethod> arrived :
        paymentMethods.newestFirst(testRunId, after, query.limit())) {
      StripePaymentMethod method = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), method.paymentMethodId()),
              StripePaymentMethodObject.stateOf(method)));
    }
    for (ArrivalOf<StripeSetupIntent> arrived :
        setupIntents.newestFirst(testRunId, after, query.limit())) {
      StripeSetupIntent setup = arrived.row();
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(arrived.arrival(), setup.setupIntentId()),
              StripeSetupIntentObject.stateOf(setup)));
    }
    return ProviderObjectPages.newestFirst(placed, query.limit());
  }

  @Override
  public ObjectActions objectActions() {
    return StripeObjectActions.DECLARED;
  }

  @Override
  public ProviderOriginationFields originationFields() {
    return StripeOrigination.DECLARED;
  }

  @Override
  public OriginationOutcome originate(OriginationRequest request) {
    Optional<StripeAccount> account = accounts.find(request.testRunId(), request.accountId());
    if (account.isEmpty()) {
      return new OriginationOutcome.NoSuchAccount();
    }
    Instant now = clock.now(request.testRunId());
    if (StripeOrigination.setsACardUp(request.startedAs())) {
      return setUpACardFor(request, account.get(), now);
    }
    StripeOperationResult result =
        createPaymentIntent.handle(
            StripeOrigination.formOf(request),
            Map.of(),
            request.testRunId(),
            account.get(),
            now,
            request.askedBy());
    return StripeOrigination.outcomeOf(result, request);
  }

  private OriginationOutcome setUpACardFor(
      OriginationRequest request, StripeAccount account, Instant now) {
    StripeOperationResult created =
        createSetupIntent.handle(
            StripeOrigination.formOf(request),
            request.testRunId(),
            account,
            now,
            request.askedBy());
    OriginationOutcome outcome = StripeOrigination.outcomeOf(created, request);
    if (!StripeOrigination.confirmsTheSetupItself(request.startedAs())
        || !(outcome instanceof OriginationOutcome.Originated originated)) {
      return outcome;
    }
    StripeOperationResult confirmed =
        confirmSetupIntent.handle(
            StripeOrigination.theConfirmationOf(request.required(StripeFields.PAYMENT_METHOD)),
            Map.of(),
            request.testRunId(),
            account,
            originated.providerObjectId(),
            now);
    return StripeOrigination.theConfirmationOutcomeOf(confirmed, request, originated);
  }

  @Override
  public HostedPageContract hostedPages() {
    return new HostedPageContract.NoPageAPayerFinishes(NO_PAGE_TO_COMPLETE);
  }

  @Override
  public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
    return new HostedCompletionOffer.NotOffered(StripeApi.STRIPE, NO_PAGE_TO_COMPLETE);
  }

  @Override
  public HostedCompletionOutcome complete(HostedCompletionRequest request) {
    return new HostedCompletionOutcome.NotOffered(NO_PAGE_TO_COMPLETE);
  }

  @Override
  public ObjectActionOutcome act(ObjectActionRequest request) {
    Optional<StripePaymentIntent> addressed =
        intents.find(request.testRunId(), request.providerObjectId());
    if (addressed.isEmpty()) {
      if (customers.find(request.testRunId(), request.providerObjectId()).isPresent()) {
        return StripeCustomerObject.noActionMovesMoneyOnACustomer();
      }
      if (paymentMethods.find(request.testRunId(), request.providerObjectId()).isPresent()) {
        return StripePaymentMethodObject.noActionMovesMoneyOnAPaymentMethod();
      }
      if (setupIntents.find(request.testRunId(), request.providerObjectId()).isPresent()) {
        return StripeSetupIntentObject.noActionMovesMoneyOnASetupIntent();
      }
      return new ObjectActionOutcome.NoSuchObject();
    }
    StripePaymentIntent intent = addressed.get();
    Optional<StripeAccount> account = accounts.find(request.testRunId(), intent.accountId());
    if (account.isEmpty()) {
      return StripeObjectActions.theRunConfiguresNoAccountFor(intent);
    }
    return StripeObjectActions.outcomeOf(
        answeredBy(request, account.get(), intent, clock.now(request.testRunId())), intent);
  }

  private StripeOperationResult answeredBy(
      ObjectActionRequest request, StripeAccount account, StripePaymentIntent intent, Instant now) {
    StripeForm form = StripeObjectActions.formOf(request, intent);
    TestRunId testRunId = request.testRunId();
    String intentId = intent.intentId();
    return switch (request.action()) {
      case CAPTURE ->
          capturePaymentIntent.handle(form, Map.of(), testRunId, account, intentId, now);
      case CANCEL -> cancelPaymentIntent.handle(form, Map.of(), testRunId, account, intentId, now);
      case REFUND ->
          createRefund.handle(form, Map.of(), testRunId, account, now, request.askedBy());
    };
  }

  @Override
  public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
    return accounts.find(testRunId, accountId).map(StripeAccount::disclosure);
  }

  private static ProviderObjectState stateOf(StripePaymentIntent intent, Instant now) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(intent.intentId()),
        StripeObjectKinds.of(intent),
        intent.statusAt(now).wireValue(),
        intent.normalizedStatusAt(now),
        intent.amount(),
        intent.currency(),
        intent.ledger(),
        intent.authorizationOpenAt(now),
        intent.capturesAcceptedAt(now),
        intent.refundsAccepted(),
        relatedObjectsOf(intent),
        intent.origin(),
        intent.updatedAt());
  }

  private static ProviderObjectState stateOf(StripeRefund refund) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(refund.refundId()),
        StripeObjectKinds.ofARefund(),
        refund.status().wireValue(),
        refund.normalizedStatus(),
        refund.amount(),
        refund.currency(),
        Ledger.empty(),
        false,
        false,
        A_REFUND_HAS_NO_LEDGER_OF_ITS_OWN,
        theObjectsARefundNames(refund),
        refund.origin(),
        refund.updatedAt());
  }

  private static Map<String, String> theObjectsARefundNames(StripeRefund refund) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put("account", refund.accountId());
    related.put("paymentIntent", refund.intentId());
    return related;
  }

  private static Map<String, String> relatedObjectsOf(StripePaymentIntent intent) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put("account", intent.accountId());
    String customer = intent.customer();
    if (customer != null) {
      related.put("customer", customer);
    }
    String paymentMethod = intent.paymentMethod();
    if (paymentMethod != null) {
      related.put("paymentMethod", paymentMethod);
    }
    return related;
  }

  @Override
  public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    return events.schedule(request);
  }
}
