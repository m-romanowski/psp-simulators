package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtureRemoval;
import dev.marcinromanowski.pspsim.kernel.idempotency.InMemoryKeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerSteps;
import dev.marcinromanowski.pspsim.kernel.spi.PlacedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
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
import dev.marcinromanowski.pspsim.kernel.spi.TestKeyClaims;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class ProviderModuleDouble implements SimulatorProvider {

  private static final int NOTHING_ANSWERS_TO_THAT_CURSOR = -1;

  private final ProviderId id;

  private final List<ProviderObjectState> listed = new ArrayList<>();

  private final List<String> disclosuresAsked = new ArrayList<>();

  private final List<ObjectActionRequest> asked = new ArrayList<>();

  private final Map<ObjectAction, String> unoffered = new LinkedHashMap<>();

  private final Map<ScenarioPreset, PresetExpansion> expansions = new LinkedHashMap<>();

  private final InMemoryKeyedExchanges exchanged = new InMemoryKeyedExchanges();

  private ObjectActionOutcome answer = new ObjectActionOutcome.NoSuchObject();

  private @Nullable HostedPageContract hostedPage;

  private final List<String> stillAwaitingTheirPayer = new ArrayList<>();

  private boolean itRefusesToReadTheRun;

  private final List<ReadRequest> readsAsked = new ArrayList<>();

  private final Map<String, List<AValueAFieldOffers>> produced = new LinkedHashMap<>();

  private @Nullable ProviderReads published;

  private ReadOutcome answerToARead = new ReadOutcome.Answered(List.of());

  private List<ProviderOperation> theOperationsThisDoubleNames =
      List.of(
          new ProviderOperation(
              "anOperationThisDoubleNames",
              Set.of(),
              "the object the call belongs to",
              StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE));

  ProviderModuleDouble(String name) {
    this.id = ProviderId.of(name);
  }

  List<String> theDisclosuresItWasAskedFor() {
    return List.copyOf(disclosuresAsked);
  }

  @Override
  public ProviderId id() {
    return id;
  }

  @Override
  public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
    throw nothingBehindThisDouble();
  }

  @Override
  public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
    throw nothingBehindThisDouble();
  }

  @Override
  public ProviderVocabulary vocabulary() {
    return new ProviderVocabulary(
        id(),
        theOperationsThisDoubleNames,
        Map.of(),
        new FailureCodes("[0-9]+", "a numeric code this double names"),
        ProcessingStates.noneOfThem("this double names no processing state"));
  }

  @Override
  public ProviderWireRoutes wireRoutes() {
    return new ProviderWireRoutes(id(), List.of("/" + id().value()));
  }

  @Override
  public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
    return new ProviderWireAnswer.NothingThisContractPublishes(
        "this double stands for a provider module with no published answer for a request off its"
            + " own routes");
  }

  @Override
  public ProviderAccountFields accountFields() {
    return new ProviderAccountFields(
        id,
        List.of(
            new ConfigurableField(
                "userId",
                "The user id",
                "a sentence a test does not read",
                true,
                true,
                List.of(),
                "a value a test does not read")));
  }

  @Override
  public ProviderPaymentMethodFields paymentMethodFields() {
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

  @Override
  public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
    return PaymentMethodFixturePlan.storingNothingOfItsOwn();
  }

  @Override
  public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
    return new PaymentMethodFixtureRemoval(() -> {});
  }

  InMemoryKeyedExchanges itsExchanges() {
    return exchanged;
  }

  static ProviderKeyMechanisms mechanismsOf(ProviderId id) {
    return new ProviderKeyMechanisms(
        id,
        List.of(
            new DeclaredKey(
                "Idempotency-Key",
                KeyRole.TRUE_IDEMPOTENCY_KEY,
                KeyCarriage.HEADER,
                "the account this key was sent under",
                TestKeyClaims.NEVER_HELD),
            new DeclaredKey(
                "orderReference",
                KeyRole.DUPLICATE_DETECTION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "every charge this account has submitted",
                TestKeyClaims.NEVER_HELD)),
        Map.of(KeyRole.CORRELATION_KEY, "this double correlates nothing"));
  }

  @Override
  public ProviderKeyMechanisms keyMechanisms() {
    return mechanismsOf(id);
  }

  @Override
  public ProviderPairing pairing() {
    return new ProviderPairing(
        id,
        "/double",
        "the double authenticates nothing",
        List.of(
            new PairingSetting("BASE_URL", new PairingValue.TheProviderBaseUrl()),
            new PairingSetting("USER_ID", new PairingValue.FromAccountField("userId"))));
  }

  @Override
  public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
    return exchanged.exchanges(keyMechanisms(), query);
  }

  @Override
  public PresetExpansion expand(PresetRequest request) {
    PresetExpansion declared = expansions.get(request.preset());
    if (declared == null) {
      return new PresetExpansion.NotExpressible(
          "this double was handed no expansion of " + request.preset().publishedName());
    }
    return declared;
  }

  ProviderModuleDouble expanding(ScenarioPreset preset, PresetExpansion expansion) {
    expansions.put(preset, expansion);
    return this;
  }

  @Override
  public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
    throw nothingBehindThisDouble();
  }

  @Override
  public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
    throw nothingBehindThisDouble();
  }

  @Override
  public ProviderObjectPage objects(ProviderObjectQuery query) {
    if (itRefusesToReadTheRun) {
      return new ProviderObjectPage.NoSuchCursor();
    }
    int from = theRowAfter(query.after());
    if (from == NOTHING_ANSWERS_TO_THAT_CURSOR) {
      return new ProviderObjectPage.NoSuchCursor();
    }
    List<PlacedProviderObject> placed = new ArrayList<>();
    for (int row = from; row < listed.size() && placed.size() < query.limit(); row++) {
      ProviderObjectState object = listed.get(row);
      placed.add(
          new PlacedProviderObject(
              new ProviderObjectCursor(listed.size() - row, object.providerObjectId()), object));
    }
    return new ProviderObjectPage.Listed(placed);
  }

  private int theRowAfter(@Nullable String cursor) {
    if (cursor == null) {
      return 0;
    }
    for (int row = 0; row < listed.size(); row++) {
      if (listed.get(row).providerObjectId().equals(cursor)) {
        return row + 1;
      }
    }
    return NOTHING_ANSWERS_TO_THAT_CURSOR;
  }

  ProviderModuleDouble refusingToReadTheRunAtAll() {
    this.itRefusesToReadTheRun = true;
    return this;
  }

  ProviderModuleDouble holding(ProviderObjectState object) {
    listed.add(object);
    return this;
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

  @Override
  public ProviderReads reads() {
    ProviderReads declared = published;
    return declared == null ? SimulatorProvider.super.reads() : declared;
  }

  @Override
  public ReadOutcome read(ReadRequest request) {
    readsAsked.add(request);
    return answerToARead;
  }

  @Override
  public List<AValueAFieldOffers> theValuesThisTestRunHasAlreadyProducedFor(
      TestRunId testRunId, String accountId, String fieldKey) {
    return produced.getOrDefault(fieldKey, List.of());
  }

  ProviderModuleDouble publishing(ProviderReads reads) {
    this.published = reads;
    return this;
  }

  ProviderModuleDouble namingInItsVocabulary(String... operations) {
    List<ProviderOperation> named = new ArrayList<>();
    for (String operation : operations) {
      named.add(
          new ProviderOperation(
              operation,
              Set.of(),
              "the object the call belongs to",
              StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE));
    }
    this.theOperationsThisDoubleNames = List.copyOf(named);
    return this;
  }

  ProviderModuleDouble answeringARead(ReadOutcome outcome) {
    this.answerToARead = outcome;
    return this;
  }

  ProviderModuleDouble havingProduced(String fieldKey, AValueAFieldOffers... values) {
    produced.put(fieldKey, List.of(values));
    return this;
  }

  List<ReadRequest> theReadsItWasAskedFor() {
    return List.copyOf(readsAsked);
  }

  @Override
  public ObjectActions objectActions() {
    return new ObjectActions(id, Map.copyOf(unoffered));
  }

  @Override
  public ObjectActionOutcome act(ObjectActionRequest request) {
    asked.add(request);
    return answer;
  }

  @Override
  public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
    if (stillAwaitingTheirPayer.contains(providerObjectId)) {
      return new HostedCompletionOffer.OfferedWithoutACard(
          id,
          "this double stands for a provider whose payer pays where this simulator serves no page",
          new PayerSteps(
              id,
              Map.of(
                  PayerOutcome.PAID,
                  "the payer paid it where this double does not say",
                  PayerOutcome.ABANDONED,
                  "the payer left it unpaid"),
              Map.of(
                  PayerOutcome.DECLINED,
                  "this double stands for a provider with no declined step on this object"),
              "it records what the payer of this object did and nothing else"));
    }
    return new HostedCompletionOffer.NotOffered(
        id, "this double stands for a provider module with no payer-facing page of its own");
  }

  ProviderModuleDouble stillAwaitingThePayerOn(String providerObjectId) {
    stillAwaitingTheirPayer.add(providerObjectId);
    return this;
  }

  @Override
  public HostedCompletionOutcome complete(HostedCompletionRequest request) {
    return new HostedCompletionOutcome.NoSuchObject();
  }

  ProviderModuleDouble keepingWaiting(String... theObjectsItKeepsWaiting) {
    List<HostedPageContract.AnObjectItKeepsWaiting> waiting = new ArrayList<>();
    for (String named : theObjectsItKeepsWaiting) {
      waiting.add(new HostedPageContract.AnObjectItKeepsWaiting(named, true));
    }
    this.hostedPage = new HostedPageContract.ObjectsAPayerFinishes(List.copyOf(waiting));
    return this;
  }

  @Override
  public HostedPageContract hostedPages() {
    return hostedPage == null
        ? new HostedPageContract.NoPageAPayerFinishes(
            "this double stands for a provider module whose contract keeps no object waiting for a"
                + " payer")
        : hostedPage;
  }

  ProviderModuleDouble offeringNo(ObjectAction action, String instead) {
    unoffered.put(action, instead);
    return this;
  }

  ProviderModuleDouble answering(ObjectActionOutcome outcome) {
    this.answer = outcome;
    return this;
  }

  List<ObjectActionRequest> theActionsItWasAskedFor() {
    return List.copyOf(asked);
  }

  @Override
  public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
    disclosuresAsked.add(testRunId.value() + "/" + accountId);
    return Optional.empty();
  }

  @Override
  public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    throw nothingBehindThisDouble();
  }

  private UnsupportedOperationException nothingBehindThisDouble() {
    return new UnsupportedOperationException(
        "this double stands for the module " + id.value() + " and answers nothing else");
  }
}
