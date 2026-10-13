package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtureRemoval;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface SimulatorProvider {

  ProviderId id();

  ProviderConfigurationPlan parse(ProviderConfiguration configuration);

  AccountRemovalPlan forget(TestRunId testRunId, String accountId);

  ProviderVocabulary vocabulary();

  ProviderWireRoutes wireRoutes();

  ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address);

  ProviderAccountFields accountFields();

  ProviderPaymentMethodFields paymentMethodFields();

  ProviderKeyMechanisms keyMechanisms();

  ProviderPairing pairing();

  ProviderOriginationFields originationFields();

  OriginationOutcome originate(OriginationRequest request);

  KeyedExchangePage keyedExchanges(KeyedExchangeQuery query);

  PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture);

  PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture);

  PresetExpansion expand(PresetRequest request);

  ResetOutcome reset(TestRunId testRunId, ResetScope scope);

  Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId);

  ProviderObjectPage objects(ProviderObjectQuery query);

  ObjectActions objectActions();

  ObjectActionOutcome act(ObjectActionRequest request);

  HostedPageContract hostedPages();

  HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId);

  HostedCompletionOutcome complete(HostedCompletionRequest request);

  Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId);

  List<ScheduledWebhook> schedule(WebhookScheduleRequest request);

  default List<ProviderObjectState> states(TestRunId testRunId, List<String> providerObjectIds) {
    return TheOrderOneAnswerIsIn.theObjectsAskedFor(
        providerObjectIds, theObjectsItHoldsUnder(testRunId, providerObjectIds));
  }

  default List<ProviderObjectState> theObjectsItHoldsUnder(
      TestRunId testRunId, List<String> providerObjectIds) {
    List<ProviderObjectState> held = new ArrayList<>();
    for (String providerObjectId : providerObjectIds) {
      if (TheOrderOneAnswerIsIn.oneOfTheseAlreadyAnswersTo(held, providerObjectId)) {
        continue;
      }
      state(testRunId, providerObjectId).ifPresent(held::add);
    }
    return List.copyOf(held);
  }

  default List<ContractCheck> compatibilityChecks(CheckJson json, IdGenerator ids) {
    return List.of();
  }

  default ProviderPersonas personas(IdGenerator ids) {
    return ProviderPersonas.noneOfThem(id(), THIS_MODULE_PUBLISHES_NO_PERSONA);
  }

  default ProviderReads reads() {
    return ProviderReads.noneOfThem(id(), THIS_MODULE_PUBLISHES_NO_READ_A_TESTER_ASKS);
  }

  default ReadOutcome read(ReadRequest request) {
    return reads().theOutcomeOfAModuleThatPerformsNoRead(request.readNamed());
  }

  default List<AValueAFieldOffers> theValuesThisTestRunHasAlreadyProducedFor(
      TestRunId testRunId, String accountId, String fieldKey) {
    return List.of();
  }

  default List<FollowUp> followUps(TestRunId testRunId, String providerObjectId, Instant now) {
    return List.of();
  }

  default List<ScheduledWebhook> followUp(FollowUpRequest request, Instant now) {
    throw new FollowUpNotOfferedException(
        FollowUpControl.theObjectDoesNotOfferIt(id(), request.name()));
  }

  String THIS_MODULE_PUBLISHES_NO_READ_A_TESTER_ASKS =
      "this provider module publishes no read a tester asks with identifiers of their own, so"
          + " everything this test run holds of it is read on the Payments screen, which lists"
          + " what the simulator stored rather than what one of this provider's own addresses"
          + " would answer";

  String THIS_MODULE_PUBLISHES_NO_PERSONA =
      "this provider module publishes no persona of its own, so every intent is uncovered here"
          + " rather than declined for a reason of this provider's contract";
}
