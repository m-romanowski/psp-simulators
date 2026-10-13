package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.TheLabelEveryIdentifierGets;
import dev.marcinromanowski.pspsim.TheSequenceOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheOrderOneAnswerIsIn;
import dev.marcinromanowski.pspsim.kernel.spi.TheSingleIdentifierFloor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GrowProcessObjectTest {

  private static final TestRunId RUN = TestRunId.of("grow-process-objects");

  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

  private static final String MERCHANT = "merchant-1";

  private static final String CALLBACK = "https://merchant.example/hook";

  private static final String THE_PAYER = "Israel Israeli";

  private static final String THE_FIRST_ID_DRAWN = "100001";

  private static final String THE_ID_DRAWN_NEXT = "100002";

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
          new SimulatorDoubles.CountingIds(),
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(scenarioRules),
          List.of(new GrowCallbackAcknowledgement(accounts)),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final GrowCallbacks callbacks =
      SimulatorDoubles.callbacks(
          processes, transactions, accounts, dispatcher, SimulatorDoubles.clockAt(NOW), ids);

  private final SimulatorProvider provider =
      new GrowProvider(
          accounts,
          audit,
          processes,
          transactions,
          dispatcher,
          callbacks,
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
          new CompleteHostedProcess(processes, callbacks, new GrowPayerCards(seededMethods)));

  @Test
  void a_hosted_page_no_payer_has_finished_is_listed_and_opened_under_its_process_id() {
    aStoredProcess("900001");

    ProviderObjectState opened = provider.state(RUN, "900001").orElseThrow();

    assertThat(idsOn(provider.objects(aListOf(RUN, null, 10)))).containsExactly("900001");
    assertThat(opened.providerObjectId()).isEqualTo("900001");
    assertThat(opened.normalizedStatus()).isEqualTo(NormalizedStatus.NOT_INITIALIZED);
    assertThat(opened.nativeStatus()).isEqualTo(GrowNativeStatus.NOT_PAID_CODE);
  }

  @Test
  void a_completed_hosted_payment_is_two_objects_and_each_one_names_the_other() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, CALLBACK));
    aStoredProcess("900001");

    String transactionId = theTransactionCompleting("900001");

    assertThat(provider.state(RUN, "900001").orElseThrow().relatedObjects())
        .containsEntry(GrowProcessObject.TRANSACTION_ID, transactionId);
    assertThat(provider.state(RUN, transactionId).orElseThrow().relatedObjects())
        .containsEntry("processId", "900001");
  }

  @Test
  void the_card_token_a_completed_process_minted_is_printed_on_that_process_and_resolves_to_it() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, CALLBACK));
    aStoredProcessOf("900001", GrowFields.CHARGE_TYPE_TOKEN_ONLY);

    theTransactionCompleting("900001");

    String printed =
        provider.state(RUN, "900001").orElseThrow().relatedObjects().get(GrowFields.CARD_TOKEN);
    assertThat(printed)
        .as(GrowProcessObject.THE_TOKEN_A_SAVED_PROCESS_MINTED_IS_READ_FROM_ITS_OBJECT)
        .isNotBlank();
    assertThat(processes.findByCardToken(RUN, MERCHANT, printed))
        .as(
            "the two calls this value is required by resolve it against the process that minted"
                + " it, so a value printed anywhere else than against that process would name a"
                + " card no grow endpoint could charge")
        .map(GrowPaymentProcess::processId)
        .contains("900001");
  }

  @Test
  void a_completed_process_that_saved_no_card_prints_no_card_token_rather_than_an_empty_one() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, CALLBACK));
    aStoredProcessOf("900002", GrowFields.CHARGE_TYPE_SUSPENDED, false);

    theTransactionCompleting("900002");

    assertThat(provider.state(RUN, "900002").orElseThrow().relatedObjects())
        .as(
            "provider grow mints a cardToken only where the process it opened was asked to save"
                + " the card, so a process that saved none has no token to print and prints no"
                + " name a tester could read an empty or absent value under")
        .doesNotContainKey(GrowFields.CARD_TOKEN);
  }

  @ParameterizedTest
  @EnumSource(
      value = ObjectAction.class,
      names = {"CAPTURE", "REFUND"})
  void an_action_that_moves_money_on_a_process_names_the_transaction_to_act_on_instead(
      ObjectAction action) {
    aStoredProcess("900001");

    ObjectActionOutcome outcome =
        provider.act(
            new ObjectActionRequest(
                RUN, "900001", action, Amount.of("120.50"), ObjectOrigin.CONTROL_API));

    assertThat(outcome).isNotInstanceOf(ObjectActionOutcome.NoSuchObject.class);
    assertThat(outcome)
        .isInstanceOfSatisfying(
            ObjectActionOutcome.RefusedByTheProvider.class,
            refused ->
                assertThat(refused.inItsOwnWords())
                    .contains(GrowProcessObject.TRANSACTION_ID)
                    .contains("POST /__simulator/grow/hosted/{processId}/complete"));
  }

  @Test
  void a_page_of_objects_merges_processes_and_transactions_newest_first_by_when_each_arrived() {
    aStoredProcess("900001");
    transactions.insertIfAbsent(aTransaction("800001"));
    aStoredProcess("900002");

    assertThat(idsOn(provider.objects(aListOf(RUN, null, 2)))).containsExactly("900002", "800001");
  }

  @Test
  void a_page_carries_on_from_a_cursor_that_names_a_process_rather_than_a_transaction() {
    aStoredProcess("900001");
    transactions.insertIfAbsent(aTransaction("800001"));
    aStoredProcess("900002");

    assertThat(idsOn(provider.objects(aListOf(RUN, "900002", 10))))
        .containsExactly("800001", "900001");
  }

  @Test
  void a_completion_redraws_a_transaction_id_a_payment_process_already_holds_in_this_run() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, CALLBACK));
    aStoredProcess("900001");
    transactions.withTheIdsClaimedByAPaymentProcess(THE_FIRST_ID_DRAWN);

    String transactionId = theTransactionCompleting("900001");

    assertThat(transactionId).isEqualTo(THE_ID_DRAWN_NEXT);
    assertThat(transactions.findByTransactionId(RUN, THE_ID_DRAWN_NEXT)).isPresent();
  }

  @Test
  void a_completion_gives_up_loudly_rather_than_looping_when_no_transaction_id_can_be_claimed() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, CALLBACK));
    aStoredProcess("900001");
    transactions.withTheIdsClaimedByAPaymentProcess(theFirstDrawnIds(GrowCallbacks.ID_ATTEMPTS));

    HostedCompletionRequest completing =
        new HostedCompletionRequest(
            RUN, "900001", PayerOutcome.PAID, "sandbox-4580", ObjectOrigin.UI);

    assertThatThrownBy(() -> provider.complete(completing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("transactionId");
    assertThat(processes.findByProcessId(RUN, "900001").orElseThrow().outcome()).isNull();
  }

  @Test
  void one_answer_for_many_identifiers_holds_what_asking_one_at_a_time_holds() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, CALLBACK));
    aStoredProcess("900001");
    String transactionId = theTransactionCompleting("900001");
    aStoredProcess("900002");
    List<String> asked =
        List.of("900001", MERCHANT, transactionId, "nothing-of-this-run", "900002", "900001");

    List<ProviderObjectState> batched = provider.states(RUN, asked);

    assertThat(batched)
        .as(
            "a screen labels every identifier an object names from this answer, so one answer for"
                + " many identifiers that held anything other than what asking one at a time"
                + " holds would change what a tester reads beside those names. %s",
            TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactlyElementsOf(
            TheSequenceOneAnswerIsIn.askingOneAtATimeInTheOrderAsked(provider, RUN, asked));
    assertThat(TheLabelEveryIdentifierGets.inOneAnswer(batched, asked))
        .as(TheLabelEveryIdentifierGets.THE_FIRST_STATE_ANSWERING_TO_IT_IS_THE_LABEL)
        .isEqualTo(TheLabelEveryIdentifierGets.askingOneAtATime(provider, RUN, asked));
    assertThat(batched)
        .extracting(ProviderObjectState::providerObjectId)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly("900001", transactionId, "900002");
  }

  @Test
  void one_answer_for_two_transactions_gives_each_of_them_the_process_that_created_it() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, CALLBACK));
    aStoredProcess("900001");
    String first = theTransactionCompleting("900001");
    aStoredProcess("900002");
    String second = theTransactionCompleting("900002");

    List<ProviderObjectState> batched = provider.states(RUN, List.of(first, second));

    assertThat(batched)
        .extracting(
            ProviderObjectState::providerObjectId, state -> state.relatedObjects().get("processId"))
        .as(
            "one answer carrying two transactions must place the objects each of them names"
                + " against that transaction, or a screen opened on one payment names the hosted"
                + " page of another. The ledger read this answer batches is keyed the same way,"
                + " and that keying lives in JdbcGrowTransactions, which this test does not load:"
                + " the guard on it is the statement-counted read in"
                + " AScreenAsksOnceForWhatItNamesIntegrationTest, which compares every batched"
                + " state against the single read of the same identifier")
        .containsExactlyInAnyOrder(tuple(first, "900001"), tuple(second, "900002"));
  }

  @Test
  void one_answer_for_an_id_naming_both_a_process_and_a_transaction_is_refused_as_one_read_is() {
    aStoredProcess("900001");
    transactions.holding(aTransaction("900001"));

    assertThatThrownBy(() -> provider.states(RUN, List.of("900001")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(GrowObjectId.ONE_ID_NAMES_TWO_OBJECTS);
  }

  @Test
  void one_id_naming_both_a_process_and_a_transaction_is_refused_rather_than_read_as_either() {
    aStoredProcess("900001");
    transactions.holding(aTransaction("900001"));

    assertThatThrownBy(() -> provider.state(RUN, "900001"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(GrowObjectId.ONE_ID_NAMES_TWO_OBJECTS);
  }

  @Test
  void every_object_grow_reports_keeps_the_one_identifier_it_was_created_under() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, CALLBACK));
    aStoredProcess("900001");
    String transactionId = theTransactionCompleting("900001");

    TheSingleIdentifierFloor.holdsFor(provider, RUN, "900001");
    TheSingleIdentifierFloor.holdsFor(provider, RUN, transactionId);
    TheSingleIdentifierFloor.holdsForEveryObjectOf(provider, RUN);
  }

  @Test
  void a_process_is_an_object_a_payer_finishes_and_names_the_merchant_it_belongs_to() {
    aStoredProcess("900001");

    assertThat(theSessionFactsOf("900001").theAccountItBelongsTo()).isEqualTo(MERCHANT);
  }

  @Test
  void a_process_was_created_when_its_row_was_written_and_not_when_it_last_changed() {
    aStoredProcess("900001");

    assertThat(theSessionFactsOf("900001").createdAt()).isEqualTo(NOW);
  }

  @Test
  void a_process_names_the_payer_grow_requires_on_the_page_it_opened() {
    aStoredProcess("900001");

    assertThat(theSessionFactsOf("900001").aPartyIsNamed()).isTrue();
    assertThat(theSessionFactsOf("900001").thePartyAsItReads()).isEqualTo(THE_PAYER);
  }

  @Test
  void a_process_that_holds_money_was_started_for_the_charge_type_that_holds_it() {
    aStoredProcess("900001");

    assertThat(theSessionFactsOf("900001").theOperationItWasStartedFor())
        .isEqualTo(GrowOrigination.A_HOLD);
  }

  @Test
  void a_process_that_only_saves_a_card_was_started_for_the_charge_type_that_saves_one() {
    aStoredProcessOf("900002", GrowFields.CHARGE_TYPE_TOKEN_ONLY);

    assertThat(theSessionFactsOf("900002").theOperationItWasStartedFor())
        .isEqualTo(GrowOrigination.A_SAVED_CARD);
  }

  @Test
  void a_process_carries_grows_own_words_for_the_deadline_it_publishes_none_of() {
    aStoredProcess("900001");

    assertThat(theSessionFactsOf("900001").itExpiresAtAKnownTime()).isFalse();
    assertThat(theSessionFactsOf("900001").theDeadlineAsItReads())
        .isEqualTo(GrowProcessObject.NO_DEADLINE_ON_A_PROCESS);
  }

  @Test
  void a_process_is_never_past_a_deadline_however_far_the_clock_of_the_run_has_gone() {
    aStoredProcess("900001");

    assertThat(theSessionFactsOf("900001").itsDeadlineHasPassedBy(NOW.plus(Duration.ofDays(3650))))
        .isFalse();
  }

  @Test
  void a_transaction_a_completion_created_is_not_an_object_a_payer_finishes() {
    accounts.withTarget(RUN, new WebhookTarget(MERCHANT, CALLBACK));
    aStoredProcess("900001");

    String transactionId = theTransactionCompleting("900001");

    assertThat(provider.state(RUN, transactionId).orElseThrow().hostedSession())
        .isEqualTo(new HostedSessionFacts.NotAnObjectAPayerFinishes());
  }

  private HostedSessionFacts.AnObjectAPayerFinishes theSessionFactsOf(String processId) {
    return (HostedSessionFacts.AnObjectAPayerFinishes)
        provider.state(RUN, processId).orElseThrow().hostedSession();
  }

  private String theTransactionCompleting(String processId) {
    HostedCompletionOutcome answered =
        provider.complete(
            new HostedCompletionRequest(
                RUN, processId, PayerOutcome.PAID, "sandbox-4580", ObjectOrigin.CONTROL_API));

    assertThat(answered).isInstanceOf(HostedCompletionOutcome.Completed.class);
    String created = ((HostedCompletionOutcome.Completed) answered).createdObjectId();
    assertThat(created).isNotNull();
    return created;
  }

  private static String[] theFirstDrawnIds(int count) {
    String[] drawn = new String[count];
    for (int index = 0; index < count; index++) {
      drawn[index] = "1" + "0".repeat(4) + (index + 1);
    }
    return drawn;
  }

  private static ProviderObjectQuery aListOf(
      TestRunId testRunId, @Nullable String after, int limit) {
    return new ProviderObjectQuery(testRunId, after, limit);
  }

  private static List<String> idsOn(ProviderObjectPage page) {
    List<String> listed = new ArrayList<>();
    for (ProviderObjectState object : ((ProviderObjectPage.Listed) page).objects()) {
      listed.add(object.providerObjectId());
    }
    return listed;
  }

  private static GrowTransaction aTransaction(String transactionId) {
    return new GrowTransaction(
        RUN,
        GrowTransactionKind.PAYMENT,
        transactionId,
        "token-" + transactionId,
        MERCHANT,
        null,
        null,
        "89474280",
        "07/09/26",
        "A payment",
        "Israel Israeli",
        "0505503144",
        null,
        GrowRecordedCard.THE_PUBLISHED_SANDBOX_CARD,
        Amount.of("120.50"),
        Ledger.empty(),
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private void aStoredProcess(String processId) {
    aStoredProcessOf(processId, GrowFields.CHARGE_TYPE_SUSPENDED);
  }

  private void aStoredProcessOf(String processId, String chargeType) {
    aStoredProcessOf(processId, chargeType, true);
  }

  private void aStoredProcessOf(String processId, String chargeType, boolean savesTheCard) {
    assertThat(
            processes.insertIfAbsent(
                new GrowPaymentProcess(
                    RUN,
                    processId,
                    "token-" + processId,
                    MERCHANT,
                    chargeType,
                    "120.50",
                    "A hold",
                    savesTheCard,
                    "https://merchant.example/thanks",
                    "https://merchant.example/cancel",
                    CALLBACK,
                    THE_PAYER,
                    "0505503144",
                    "payer@merchant.example",
                    Map.of("cField1", "session-abc"),
                    "link-" + processId,
                    GrowNativeStatus.NOT_PAID_CODE,
                    GrowNativeStatus.NOT_PAID,
                    NormalizedStatus.NOT_INITIALIZED,
                    null,
                    ObjectOrigin.PROVIDER_API,
                    NOW,
                    NOW)))
        .isTrue();
  }
}
