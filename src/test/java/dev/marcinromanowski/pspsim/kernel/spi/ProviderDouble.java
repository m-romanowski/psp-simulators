package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.StoryKeyWitness;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtureRemoval;
import dev.marcinromanowski.pspsim.kernel.idempotency.InMemoryKeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ProviderDouble implements SimulatorProvider {

  static final ProviderId ID = ProviderId.of("twokeys");

  private final ProviderId id;

  private final List<WebhookScheduleRequest> scheduled = new ArrayList<>();
  private final List<ProviderConfiguration> stored = new ArrayList<>();
  private final List<String> forgotten = new ArrayList<>();
  private final List<ProviderObjectIdentity> held = new ArrayList<>();
  private final List<String> identifiersRead = new ArrayList<>();
  private Set<String> configurationKeys = Set.of();
  private List<StoryKeyWitness> storyKeys = List.of();
  private final List<ScheduledWebhook> answers = new ArrayList<>();

  private ProviderVocabulary vocabulary =
      new ProviderVocabulary(
          ID,
          List.of(
              new ProviderOperation(
                  "capture",
                  Set.of(MatchFact.MERCHANT_ID),
                  "the payment",
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
          Map.of(MatchFact.CURRENCY, "this provider names no currency anywhere"),
          new FailureCodes("[0-9]+", "this provider declines with a number"),
          ProcessingStates.noneOfThem("this provider names no processing state"));

  private PresetExpansion expansion = new PresetExpansion.NotExpressible("nothing is built yet");

  private ProviderAccountFields accountFields =
      new ProviderAccountFields(
          ID,
          List.of(
              new ConfigurableField(
                  "userId",
                  "The user id",
                  "a sentence a test does not read",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read")));

  ProviderDouble() {
    this(ID);
  }

  public ProviderDouble(String name) {
    this(ProviderId.of(name));
  }

  private final List<PaymentMethodFixture> seededFixtures = new ArrayList<>();
  private final List<String> forgottenFixtures = new ArrayList<>();
  private final InMemoryKeyedExchanges exchanged = new InMemoryKeyedExchanges();
  private ProviderPaymentMethodFields paymentMethodFields;
  private ProviderKeyMechanisms keyMechanisms;
  private @Nullable ProviderFixtureRejectedException refusingTheSeed;
  private @Nullable RuntimeException refusingToStoreTheSeed;
  private @Nullable ClaimedMeanwhileException claimingTheAccountMeanwhile;
  private @Nullable ProviderConfigurationRejectedException refusingTheConfiguration;

  private ProviderPersonas personas;

  ProviderDouble publishing(ProviderPersonas publishing) {
    this.personas = publishing;
    return this;
  }

  @Override
  public ProviderPersonas personas(IdGenerator ids) {
    return personas;
  }

  private ProviderDouble(ProviderId id) {
    this.id = id;
    this.personas = ProviderPersonas.noneOfThem(id, "this double publishes no persona");
    this.paymentMethodFields = aProviderThatDrivesASeededCardByItsToken(id);
    this.keyMechanisms = aProviderWithOneIdempotencyKey(id);
  }

  static ProviderPaymentMethodFields aProviderThatDrivesASeededCardByItsToken(ProviderId id) {
    return new ProviderPaymentMethodFields(
        id,
        List.of(
            new ConfigurableField(
                ProviderPaymentMethodFields.TOKEN,
                "the saved card token",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read")),
        Map.of(
            ProviderPaymentMethodFields.CUSTOMER_ID,
            "this provider names no customer anywhere",
            ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
            "this provider calls a saved card a token and nothing else"));
  }

  ProviderDouble seeding(ProviderPaymentMethodFields fields) {
    this.paymentMethodFields = fields;
    return this;
  }

  ProviderDouble refusingTheSeed(String property, String message) {
    this.refusingTheSeed = new ProviderFixtureRejectedException(property, message);
    return this;
  }

  ProviderDouble refusingToStoreTheSeed(String property, String message) {
    this.refusingToStoreTheSeed = new ProviderFixtureRejectedException(property, message);
    return this;
  }

  ProviderDouble findingTheAccountClaimedMeanwhile(String message) {
    this.claimingTheAccountMeanwhile = new ClaimedMeanwhileException(message);
    return this;
  }

  ProviderDouble refusingTheConfiguration(String property, String message) {
    this.refusingTheConfiguration = new ProviderConfigurationRejectedException(property, message);
    return this;
  }

  ProviderDouble findingTheSeedClaimedMeanwhile(String message) {
    this.refusingToStoreTheSeed = new ClaimedMeanwhileException(message);
    return this;
  }

  List<PaymentMethodFixture> itSeeded() {
    return List.copyOf(seededFixtures);
  }

  List<String> theFixturesItForgot() {
    return List.copyOf(forgottenFixtures);
  }

  ProviderDouble reading(String... keys) {
    this.configurationKeys = Set.of(keys);
    return this;
  }

  ProviderDouble asking(ConfigurableField... fields) {
    this.accountFields = new ProviderAccountFields(id, List.of(fields));
    return this;
  }

  ProviderDouble spelling(StoryKeyWitness... witnesses) {
    this.storyKeys = List.of(witnesses);
    return this;
  }

  List<ProviderConfiguration> itStored() {
    return List.copyOf(stored);
  }

  ProviderDouble refusing(ProviderVocabulary refusing) {
    this.vocabulary = refusing;
    return this;
  }

  ProviderDouble expanding(PresetExpansion expanding) {
    this.expansion = expanding;
    return this;
  }

  ProviderDouble answering(ScheduledWebhook answer) {
    answers.add(answer);
    return this;
  }

  WebhookScheduleRequest theOnlyRequestItWasAsked() {
    if (scheduled.size() != 1) {
      throw new IllegalStateException("this provider was asked to schedule " + scheduled.size());
    }
    return scheduled.get(0);
  }

  @Override
  public ProviderId id() {
    return id;
  }

  @Override
  public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
    ProviderConfigurationRejectedException refused = refusingTheConfiguration;
    if (refused != null) {
      throw refused;
    }
    return new ProviderConfigurationPlan(
        configurationKeys,
        storyKeys,
        () -> {
          ClaimedMeanwhileException claimed = claimingTheAccountMeanwhile;
          if (claimed != null) {
            throw claimed;
          }
          stored.add(configuration);
        });
  }

  @Override
  public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
    return new AccountRemovalPlan(() -> forgotten.add(testRunId.value() + "/" + accountId));
  }

  List<String> itForgot() {
    return List.copyOf(forgotten);
  }

  @Override
  public ProviderVocabulary vocabulary() {
    return vocabulary;
  }

  private List<String> namespaces = List.of();

  private final List<WireFault> faultsItWasAskedAbout = new ArrayList<>();

  private @Nullable ProviderWireAnswer offItsRoutes;

  public ProviderDouble answering(List<String> namespaces, ProviderWireAnswer offItsRoutes) {
    this.namespaces = List.copyOf(namespaces);
    this.offItsRoutes = offItsRoutes;
    return this;
  }

  public List<WireFault> theFaultsItWasAskedAbout() {
    return List.copyOf(faultsItWasAskedAbout);
  }

  @Override
  public ProviderWireRoutes wireRoutes() {
    return new ProviderWireRoutes(
        id(), namespaces.isEmpty() ? List.of("/" + id().value()) : namespaces);
  }

  @Override
  public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
    faultsItWasAskedAbout.add(fault);
    ProviderWireAnswer published = offItsRoutes;
    return published == null
        ? new ProviderWireAnswer.NothingThisContractPublishes(
            "this double stands for a provider module with no published answer for a request off"
                + " its own routes")
        : published;
  }

  @Override
  public ProviderAccountFields accountFields() {
    return accountFields;
  }

  @Override
  public ProviderPaymentMethodFields paymentMethodFields() {
    return paymentMethodFields;
  }

  @Override
  public ProviderKeyMechanisms keyMechanisms() {
    return keyMechanisms;
  }

  @Override
  public ProviderPairing pairing() {
    return new ProviderPairing(
        id(),
        "/double",
        "the double authenticates nothing",
        List.of(new PairingSetting("BASE_URL", new PairingValue.TheProviderBaseUrl())));
  }

  @Override
  public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
    return exchanged.exchanges(keyMechanisms, query);
  }

  InMemoryKeyedExchanges itsExchanges() {
    return exchanged;
  }

  ProviderDouble declaring(ProviderKeyMechanisms mechanisms) {
    this.keyMechanisms = mechanisms;
    return this;
  }

  static ProviderKeyMechanisms aProviderWithOneIdempotencyKey(ProviderId id) {
    return new ProviderKeyMechanisms(
        id,
        List.of(
            new DeclaredKey(
                "Idempotency-Key",
                KeyRole.TRUE_IDEMPOTENCY_KEY,
                KeyCarriage.HEADER,
                "the account this key was sent under",
                TestKeyClaims.NEVER_HELD)),
        Map.of(
            KeyRole.DUPLICATE_DETECTION_KEY,
            "this double refuses no repeat",
            KeyRole.CORRELATION_KEY,
            "this double correlates nothing"),
        answered -> answered);
  }

  @Override
  public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
    ProviderFixtureRejectedException refused = refusingTheSeed;
    if (refused != null) {
      throw refused;
    }
    return new PaymentMethodFixturePlan(
        seeded -> {
          RuntimeException refusedAtTheStore = refusingToStoreTheSeed;
          if (refusedAtTheStore != null) {
            throw refusedAtTheStore;
          }
          seededFixtures.add(seeded);
        });
  }

  @Override
  public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
    return new PaymentMethodFixtureRemoval(() -> forgottenFixtures.add(fixture.fixtureId()));
  }

  private @Nullable ScenarioAction expandingForTheRunAsked;

  ProviderDouble expandingForTheRunAsked(ScenarioAction action) {
    this.expandingForTheRunAsked = action;
    return this;
  }

  private @Nullable String stoppingAfterTheFirstExpansion;

  private int expansionsAsked;

  ProviderDouble stoppingAfterTheFirstExpansion(String reason) {
    this.stoppingAfterTheFirstExpansion = reason;
    return this;
  }

  @Override
  public PresetExpansion expand(PresetRequest request) {
    String stops = stoppingAfterTheFirstExpansion;
    if (stops != null && expansionsAsked++ > 0) {
      return new PresetExpansion.NotExpressible(stops);
    }
    ScenarioAction perRun = expandingForTheRunAsked;
    if (perRun == null) {
      return expansion;
    }
    return new PresetExpansion.Rules(
        List.of(
            new NewScenarioRule(
                id,
                request.testRunId(),
                request.priority(),
                request.narrowing().naming("capture"),
                perRun,
                null)));
  }

  @Override
  public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
    return new ResetOutcome(0, 0);
  }

  @Override
  public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
    identifiersRead.add(providerObjectId);
    for (ProviderObjectIdentity identity : held) {
      if (identity.answersTo(providerObjectId)) {
        return Optional.of(objectOf(identity));
      }
    }
    return Optional.empty();
  }

  private ProviderObjectState objectOf(ProviderObjectIdentity identity) {
    return new ProviderObjectState(
        identity,
        AKindOfObjectATestNames.ONE,
        "1",
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        Instant.EPOCH,
        factsOf(identity));
  }

  private HostedSessionFacts factsOf(ProviderObjectIdentity identity) {
    for (String waits : itKeeps.keySet()) {
      if (identity.answersTo(waits)) {
        return new HostedSessionFacts.AnObjectAPayerFinishes(
            "an account this double stands for",
            new HostedSessionFacts.Party.NoPartyThisProviderPublishes(
                "this double names no party on what waits"),
            "an operation this double stands for",
            Instant.EPOCH,
            new HostedSessionFacts.Deadline.NoDeadlineThisProviderPublishes(
                "this double publishes no deadline on what waits"));
      }
    }
    return new HostedSessionFacts.NotAnObjectAPayerFinishes();
  }

  private final Map<String, Boolean> itKeeps = new LinkedHashMap<>();

  private @Nullable HostedPageContract declared;

  ProviderDouble declaring(HostedPageContract declaration) {
    this.declared = declaration;
    return this;
  }

  ProviderDouble keepingWaiting(String providerObjectId, boolean aPayerNamesACardOnIt) {
    holding(providerObjectId);
    itKeeps.put(providerObjectId, aPayerNamesACardOnIt);
    return this;
  }

  static final String THIS_DOUBLE_KEEPS_NO_PAGE =
      "this double stands for a provider module whose contract keeps no object waiting for a payer";

  @Override
  public HostedPageContract hostedPages() {
    return declared == null
        ? new HostedPageContract.NoPageAPayerFinishes(THIS_DOUBLE_KEEPS_NO_PAGE)
        : declared;
  }

  private final Map<String, FollowUp> followUpsOffered = new LinkedHashMap<>();
  private List<ScheduledWebhook> followUpJobs = List.of();
  private @Nullable RuntimeException followUpRefusal;
  private boolean forgetsWhatItFollowsUp;
  private final List<FollowUpRequest> followedUp = new ArrayList<>();

  ProviderDouble offeringAFollowUp(
      String providerObjectId, FollowUp followUp, List<ScheduledWebhook> jobs) {
    holding(providerObjectId);
    followUpsOffered.put(providerObjectId, followUp);
    followUpJobs = List.copyOf(jobs);
    return this;
  }

  ProviderDouble refusingEveryFollowUpWith(RuntimeException refusal) {
    followUpRefusal = refusal;
    return this;
  }

  ProviderDouble forgettingTheObjectItFollowsUp() {
    forgetsWhatItFollowsUp = true;
    return this;
  }

  List<FollowUpRequest> followedUp() {
    return List.copyOf(followedUp);
  }

  @Override
  public List<FollowUp> followUps(TestRunId testRunId, String providerObjectId, Instant now) {
    if (followUpsOffered.isEmpty()) {
      return SimulatorProvider.super.followUps(testRunId, providerObjectId, now);
    }
    FollowUp offered = followUpsOffered.get(providerObjectId);
    return offered == null ? List.of() : List.of(offered);
  }

  @Override
  public List<ScheduledWebhook> followUp(FollowUpRequest request, Instant now) {
    followedUp.add(request);
    RuntimeException refusal = followUpRefusal;
    if (refusal != null) {
      throw refusal;
    }
    if (followUpsOffered.isEmpty()) {
      return SimulatorProvider.super.followUp(request, now);
    }
    if (forgetsWhatItFollowsUp) {
      held.removeIf(identity -> identity.answersTo(request.providerObjectId()));
    }
    return followUpJobs;
  }

  List<String> theIdentifiersItWasReadFor() {
    return List.copyOf(identifiersRead);
  }

  ProviderDouble holding(String providerObjectId) {
    refuseAnIdentifierAnotherObjectAlreadyAnswersTo(providerObjectId);
    held.add(ProviderObjectIdentity.of(providerObjectId));
    return this;
  }

  ProviderDouble mintingAFurtherIdentifierOn(String providerObjectId, String minted) {
    refuseAnIdentifierAnotherObjectAlreadyAnswersTo(minted);
    for (int object = 0; object < held.size(); object++) {
      if (held.get(object).answersTo(providerObjectId)) {
        held.set(object, held.get(object).answeringAlsoTo(minted));
        return this;
      }
    }
    throw new IllegalStateException("this double holds no object to mint a further identifier on");
  }

  List<ProviderObjectState> itsObjects() {
    List<ProviderObjectState> objects = new ArrayList<>();
    for (ProviderObjectIdentity identity : held) {
      objects.add(objectOf(identity));
    }
    return List.copyOf(objects);
  }

  private void refuseAnIdentifierAnotherObjectAlreadyAnswersTo(String identifier) {
    for (ProviderObjectIdentity identity : held) {
      if (identity.answersTo(identifier)) {
        throw new IllegalStateException(
            "this double was asked to give one identifier to two objects, and a lookup by it"
                + " could then reach either");
      }
    }
  }

  private boolean listsWhatItHolds;

  private boolean listingRefusesEveryCursor;

  ProviderDouble listingWhatItHolds() {
    listsWhatItHolds = true;
    return this;
  }

  ProviderDouble refusingEveryListingAsAnUnknownCursor() {
    listingRefusesEveryCursor = true;
    return this;
  }

  @Override
  public ProviderObjectPage objects(ProviderObjectQuery query) {
    if (listingRefusesEveryCursor) {
      return new ProviderObjectPage.NoSuchCursor();
    }
    if (!listsWhatItHolds) {
      return new ProviderObjectPage.Listed(List.of());
    }
    List<PlacedProviderObject> placed = new ArrayList<>();
    for (int object = 0; object < held.size(); object++) {
      ProviderObjectIdentity identity = held.get(object);
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(held.size() - object, identity.canonical()),
              objectOf(identity)));
    }
    return ProviderObjectPages.newestFirst(placed, query.limit());
  }

  @Override
  public ProviderOriginationFields originationFields() {
    return new ProviderOriginationFields(
        id,
        List.of(
            new DeclaredOrigination(
                "a sale",
                "Take the money in one call",
                "a payment this double reports and never stores",
                new ThePayerStep.NoPageThisSimulatorServes(
                    "this double serves no page a payer finishes."),
                List.of(
                    AFieldATesterFillsIn.typed(
                        "amount",
                        "amount",
                        true,
                        "the amount this double is asked for and does nothing with.",
                        "1000")))),
        new LinkedHashMap<>());
  }

  @Override
  public OriginationOutcome originate(OriginationRequest request) {
    return new OriginationOutcome.NoSuchAccount();
  }

  private final Map<ObjectAction, String> whatItHasInstead = new LinkedHashMap<>();

  private final List<ObjectActionRequest> asked = new ArrayList<>();

  ProviderDouble offeringNo(ObjectAction action, String whatItHasInsteadOfIt) {
    whatItHasInstead.put(action, whatItHasInsteadOfIt);
    return this;
  }

  List<ObjectActionRequest> theActionsAskedOfIt() {
    return List.copyOf(asked);
  }

  @Override
  public ObjectActions objectActions() {
    return new ObjectActions(id, whatItHasInstead);
  }

  @Override
  public ObjectActionOutcome act(ObjectActionRequest request) {
    asked.add(request);
    return new ObjectActionOutcome.NoSuchObject();
  }

  @Override
  public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
    Boolean namesACard = itKeeps.get(providerObjectId);
    if (namesACard == null) {
      return new HostedCompletionOffer.NotOffered(
          id, "this double stands for a provider module with no payer-facing page of its own");
    }
    PayerSteps steps =
        new PayerSteps(
            id,
            Map.of(
                PayerOutcome.PAID,
                "the payer paid on the page this double stands for",
                PayerOutcome.DECLINED,
                "the payer met a decline on the page this double stands for",
                PayerOutcome.ABANDONED,
                "the payer left the page this double stands for without paying"),
            Map.of(),
            "it runs the payer's own step on the page this double stands for");
    return namesACard
        ? new HostedCompletionOffer.Offered(
            id, List.of(new PayerCard("a-card", "the card this double offers")), steps)
        : new HostedCompletionOffer.OfferedWithoutACard(
            id, "this double keeps an object a payer finishes without naming a card on it", steps);
  }

  @Override
  public HostedCompletionOutcome complete(HostedCompletionRequest request) {
    return new HostedCompletionOutcome.NoSuchObject();
  }

  private final Map<String, Map<String, List<String>>> disclosures = new LinkedHashMap<>();

  ProviderDouble disclosing(String accountId, Map<String, List<String>> disclosed) {
    disclosures.put(accountId, Map.copyOf(disclosed));
    return this;
  }

  @Override
  public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
    Map<String, List<String>> disclosed = disclosures.get(accountId);
    return disclosed == null
        ? Optional.empty()
        : Optional.of(new DisclosedAccount(accountId, disclosed));
  }

  @Override
  public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    scheduled.add(request);
    return List.copyOf(answers);
  }
}
