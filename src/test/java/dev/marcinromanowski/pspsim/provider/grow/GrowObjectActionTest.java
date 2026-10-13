package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionControl;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GrowObjectActionTest {

  private static final TestRunId RUN = TestRunId.of("grow-object-actions");

  private static final TestRunId ANOTHER_RUN = TestRunId.of("another-run");

  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

  private static final String HELD = "120.50";

  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();
  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final InMemoryPaymentMethodFixtures seededMethods = new InMemoryPaymentMethodFixtures();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          deliveries,
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          SimulatorDoubles.clockAt(NOW),
          ids,
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new GrowCallbackAcknowledgement(accounts)),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final SimulatorProvider provider =
      new GrowProvider(
          accounts,
          audit,
          processes,
          transactions,
          dispatcher,
          SimulatorDoubles.callbacks(
              processes, transactions, accounts, dispatcher, SimulatorDoubles.clockAt(NOW), ids),
          SimulatorDoubles.clockAt(NOW),
          seededMethods,
          scenarioRules,
          SimulatorDoubles.heldResponses(),
          new SettleSuspendedTransaction(
              transactions, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          new RefundTransaction(
              transactions, ids, new ScenarioEngine(scenarioRules), GrowScalarType.INTEGER),
          SimulatorDoubles.paymentProcessCreation(processes, scenarioRules, ids),
          SimulatorDoubles.storedCardCharge(processes, transactions, ids, scenarioRules),
          SimulatorDoubles.retraceOfAStoredCardCharge(processes, transactions, scenarioRules),
          SimulatorDoubles.completions(processes));

  @Test
  void a_tester_who_captures_a_hold_moves_the_money_grows_own_settle_route_moves() {
    aConfiguredRunHolding(aHold());

    ObjectActionOutcome outcome = asked(ObjectAction.CAPTURE, HELD, ObjectOrigin.UI);

    assertThat(outcome).isEqualTo(new ObjectActionOutcome.Applied("770001", null));
    ProviderObjectState settled = theState("770001");
    assertThat(settled.nativeStatus()).isEqualTo(GrowNativeStatus.PAID_CODE);
    assertThat(settled.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(settled.derivedBalances().orElseThrow().capturedAmount()).isEqualTo(Amount.of(HELD));
    assertThat(settled.derivedBalances().orElseThrow().refundableRemainder())
        .isEqualTo(Amount.of(HELD));
  }

  @Test
  void a_refund_a_tester_took_is_recorded_as_the_testers_own_and_not_the_applications() {
    aConfiguredRunHolding(aHold());
    asked(ObjectAction.CAPTURE, HELD, ObjectOrigin.UI);

    ObjectActionOutcome outcome = asked(ObjectAction.REFUND, HELD, ObjectOrigin.UI);

    String refundId = ((ObjectActionOutcome.Applied) outcome).createdObjectId();
    assertThat(refundId).isNotNull().isNotEqualTo("770001");
    assertThat(theState(String.valueOf(refundId)).origin()).isEqualTo(ObjectOrigin.UI);
    assertThat(theState("770001").origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(theState("770001").normalizedStatus()).isEqualTo(NormalizedStatus.REFUNDED);
  }

  @Test
  void a_refund_a_harness_took_through_the_control_api_is_recorded_as_that_harnesss_own() {
    aConfiguredRunHolding(aHold());
    asked(ObjectAction.CAPTURE, HELD, ObjectOrigin.CONTROL_API);

    ObjectActionOutcome outcome = asked(ObjectAction.REFUND, HELD, ObjectOrigin.CONTROL_API);

    String refundId = String.valueOf(((ObjectActionOutcome.Applied) outcome).createdObjectId());
    assertThat(theState(refundId).origin()).isEqualTo(ObjectOrigin.CONTROL_API);
  }

  @Test
  void a_capture_grow_refuses_comes_back_in_grows_own_words_and_not_as_a_failure_of_its_own() {
    aConfiguredRunHolding(aHold());
    asked(ObjectAction.CAPTURE, HELD, ObjectOrigin.UI);

    ObjectActionOutcome refused = asked(ObjectAction.CAPTURE, HELD, ObjectOrigin.UI);

    assertThat(refused)
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                GrowErrors.alreadyReleased().id() + " " + GrowErrors.alreadyReleased().message()));
  }

  @Test
  void grow_offers_no_cancellation_and_says_what_it_documents_in_place_of_one() {
    aConfiguredRunHolding(aHold());

    ObjectActionOutcome outcome =
        ObjectActionControl.perform(
            provider,
            ObjectActionRequest.of(RUN, "770001", ObjectAction.CANCEL, null, ObjectOrigin.UI));

    assertThat(provider.objectActions().offers(ObjectAction.CANCEL)).isFalse();
    assertThat(provider.objectActions().offers(ObjectAction.CAPTURE)).isTrue();
    assertThat(outcome).isEqualTo(new ObjectActionOutcome.NotOffered(GrowObjectActions.NO_CANCEL));
    assertThat(theState("770001").normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_payment_this_test_run_never_held_is_answered_as_no_such_object() {
    aConfiguredRunHolding(aHold());

    assertThat(
            provider.act(
                ObjectActionRequest.of(
                    ANOTHER_RUN, "770001", ObjectAction.CAPTURE, HELD, ObjectOrigin.UI)))
        .isEqualTo(new ObjectActionOutcome.NoSuchObject());
  }

  @Test
  void a_payment_whose_business_this_run_no_longer_configures_is_refused_in_grows_own_words() {
    transactions.holding(aHold());

    ObjectActionOutcome refused = asked(ObjectAction.CAPTURE, HELD, ObjectOrigin.UI);

    assertThat(refused)
        .isInstanceOf(ObjectActionOutcome.RefusedByTheProvider.class)
        .extracting(outcome -> ((ObjectActionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .asString()
        .contains(String.valueOf(GrowErrors.noBusinessForTheUserId().id()))
        .contains("merchant-1");
  }

  @Test
  void a_capture_a_scenario_rule_declined_carries_the_code_the_rule_named() {
    aConfiguredRunHolding(aHold());
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(GrowOperations.SETTLE_SUSPENDED_TRANSACTION),
            new ScenarioAction.TerminalFailure("803"),
            null));

    ObjectActionOutcome refused = asked(ObjectAction.CAPTURE, HELD, ObjectOrigin.UI);

    assertThat(refused)
        .isInstanceOf(ObjectActionOutcome.RefusedByTheProvider.class)
        .extracting(outcome -> ((ObjectActionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .asString()
        .startsWith("803");
    assertThat(theState("770001").normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_capture_a_scenario_rule_answered_instead_of_running_moves_no_money_and_says_so() {
    aConfiguredRunHolding(aHold());
    scenarioRules.add(
        new NewScenarioRule(
            GrowLightApi.GROW,
            RUN,
            0,
            ScenarioMatch.anyRequest().naming(GrowOperations.SETTLE_SUSPENDED_TRANSACTION),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    ObjectActionOutcome outcome = asked(ObjectAction.CAPTURE, HELD, ObjectOrigin.UI);

    assertThat(outcome).isInstanceOf(ObjectActionOutcome.AnsweredByAScenarioInstead.class);
    assertThat(theState("770001").normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_grow_refund_row_carries_no_token_so_grow_refuses_to_settle_it_by_the_field_it_lacks() {
    aConfiguredRunHolding(aHold());
    asked(ObjectAction.CAPTURE, HELD, ObjectOrigin.UI);
    String refundId =
        String.valueOf(
            ((ObjectActionOutcome.Applied) asked(ObjectAction.REFUND, HELD, ObjectOrigin.UI))
                .createdObjectId());

    ObjectActionOutcome refused =
        provider.act(
            ObjectActionRequest.of(RUN, refundId, ObjectAction.CAPTURE, HELD, ObjectOrigin.UI));

    assertThat(refused)
        .isEqualTo(
            new ObjectActionOutcome.RefusedByTheProvider(
                GrowErrors.missingRequiredField(GrowFields.TRANSACTION_TOKEN).id()
                    + " "
                    + GrowErrors.missingRequiredField(GrowFields.TRANSACTION_TOKEN).message()));
  }

  @Test
  void a_listing_reads_the_hold_window_off_the_account_each_transaction_was_taken_on() {
    accounts.merge(
        RUN,
        List.of(
            SimulatorDoubles.account(
                RUN,
                "merchant-1",
                "u-1",
                "p-1",
                Map.of(GrowHoldWindow.AUTOMATIC_VOID_AFTER, "P1D"))));
    transactions.holding(aHoldTakenThreeDaysAgo());

    ProviderObjectPage page = provider.objects(new ProviderObjectQuery(RUN, null, 10));

    assertThat(page)
        .isInstanceOfSatisfying(
            ProviderObjectPage.Listed.class,
            listed ->
                assertThat(listed.objects())
                    .as(
                        "one answer carrying the accounts a whole page of transactions was taken"
                            + " on must place each account against its own transactions: read off"
                            + " no account at all this hold is still capturable for the ten"
                            + " published days, and read off this one it was voided a day after"
                            + " it was taken")
                    .extracting(ProviderObjectState::normalizedStatus)
                    .containsExactly(NormalizedStatus.CANCELLED));
    assertThat(theState("770001").normalizedStatus()).isEqualTo(NormalizedStatus.CANCELLED);
  }

  private static GrowTransaction aHoldTakenThreeDaysAgo() {
    GrowTransaction taken = aHold();
    Instant then = NOW.minus(Duration.ofDays(3));
    return new GrowTransaction(
        taken.testRunId(),
        taken.kind(),
        taken.transactionId(),
        taken.transactionToken(),
        taken.merchantId(),
        taken.processId(),
        taken.refundedTransactionId(),
        taken.asmachta(),
        taken.paymentDate(),
        taken.description(),
        taken.payerFullName(),
        taken.payerPhone(),
        taken.payerEmail(),
        taken.card(),
        taken.amount(),
        taken.ledger(),
        taken.origin(),
        then,
        then);
  }

  private ObjectActionOutcome asked(ObjectAction action, String amount, ObjectOrigin askedBy) {
    return provider.act(ObjectActionRequest.of(RUN, "770001", action, amount, askedBy));
  }

  private ProviderObjectState theState(String providerObjectId) {
    Optional<ProviderObjectState> state = provider.state(RUN, providerObjectId);
    assertThat(state).isPresent();
    return state.orElseThrow();
  }

  private void aConfiguredRunHolding(GrowTransaction transaction) {
    accounts.merge(RUN, List.of(SimulatorDoubles.account(RUN, "merchant-1", "u-1", "p-1")));
    transactions.holding(transaction);
  }

  private static GrowTransaction aHold() {
    return new GrowTransaction(
        RUN,
        GrowTransactionKind.PAYMENT,
        "770001",
        "token-770001",
        "merchant-1",
        "900770001",
        null,
        "89474280",
        "07/09/26",
        "A payment",
        "Israel Israeli",
        "0505503144",
        null,
        GrowRecordedCard.THE_PUBLISHED_SANDBOX_CARD,
        Amount.of(HELD),
        new Ledger(
            List.of(
                new MonetaryOperation(
                    1,
                    OperationKind.AUTHORIZATION,
                    Amount.of(HELD),
                    false,
                    null,
                    GrowNativeStatus.SUSPENDED_CODE,
                    NormalizedStatus.READY_FOR_CAPTURE,
                    NOW))),
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }
}
