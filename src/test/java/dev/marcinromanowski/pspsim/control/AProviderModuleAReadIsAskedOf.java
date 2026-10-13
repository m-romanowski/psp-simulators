package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtureRemoval;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
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
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
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
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class AProviderModuleAReadIsAskedOf implements SimulatorProvider {

  static final String NOTHING_A_READ_DOES_ASKS_THIS_DOUBLE_FOR_IT =
      "this double stands for a provider module a read is asked of, and a read asks a module for"
          + " the questions it publishes, the answer to one of them and the values this test run"
          + " has already produced for a field of one, and for nothing else";

  private final ProviderId id;

  private final List<ReadRequest> asked = new ArrayList<>();

  private final Map<String, List<AValueAFieldOffers>> produced = new LinkedHashMap<>();

  private @Nullable ProviderReads published;

  private ReadOutcome answer = new ReadOutcome.Answered(List.of());

  AProviderModuleAReadIsAskedOf(String name) {
    this.id = ProviderId.of(name);
  }

  AProviderModuleAReadIsAskedOf publishing(ProviderReads reads) {
    this.published = reads;
    return this;
  }

  AProviderModuleAReadIsAskedOf answering(ReadOutcome outcome) {
    this.answer = outcome;
    return this;
  }

  AProviderModuleAReadIsAskedOf havingAlreadyProduced(
      String fieldKey, List<AValueAFieldOffers> values) {
    produced.put(fieldKey, List.copyOf(values));
    return this;
  }

  List<ReadRequest> theReadsItWasAsked() {
    return List.copyOf(asked);
  }

  @Override
  public ProviderId id() {
    return id;
  }

  @Override
  public ProviderReads reads() {
    ProviderReads declared = published;
    return declared == null ? SimulatorProvider.super.reads() : declared;
  }

  @Override
  public ReadOutcome read(ReadRequest request) {
    asked.add(request);
    return answer;
  }

  @Override
  public List<AValueAFieldOffers> theValuesThisTestRunHasAlreadyProducedFor(
      TestRunId testRunId, String accountId, String fieldKey) {
    return produced.getOrDefault(fieldKey, List.of());
  }

  @Override
  public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ProviderVocabulary vocabulary() {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ProviderWireRoutes wireRoutes() {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ProviderAccountFields accountFields() {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ProviderPaymentMethodFields paymentMethodFields() {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ProviderKeyMechanisms keyMechanisms() {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ProviderPairing pairing() {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ProviderOriginationFields originationFields() {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public OriginationOutcome originate(OriginationRequest request) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public PresetExpansion expand(PresetRequest request) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ProviderObjectPage objects(ProviderObjectQuery query) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ObjectActions objectActions() {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public ObjectActionOutcome act(ObjectActionRequest request) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public HostedPageContract hostedPages() {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public HostedCompletionOutcome complete(HostedCompletionRequest request) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
    throw nothingElseIsAskedOfIt();
  }

  @Override
  public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
    throw nothingElseIsAskedOfIt();
  }

  private static UnsupportedOperationException nothingElseIsAskedOfIt() {
    return new UnsupportedOperationException(NOTHING_A_READ_DOES_ASKS_THIS_DOUBLE_FOR_IT);
  }
}
