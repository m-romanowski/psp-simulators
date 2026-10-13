package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrowHostedCompletionTest {

  private static final TestRunId RUN = TestRunId.of("grow-hosted-completion");
  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String CARD = "sandbox-4580";
  private static final String NOTIFY = "https://merchant.example/callbacks/grow";

  private final SimulatorDoubles.Processes processes = new SimulatorDoubles.Processes();
  private final SimulatorDoubles.Transactions transactions = new SimulatorDoubles.Transactions();
  private final SimulatorDoubles.Accounts accounts = new SimulatorDoubles.Accounts();
  private final SimulatorDoubles.CountingIds ids = new SimulatorDoubles.CountingIds();
  private final SimulatorDoubles.Audit audit = new SimulatorDoubles.Audit();

  private GrowCallbacks callbacks;
  private CompleteHostedProcess completions;

  @BeforeEach
  void aRunWithOneMerchantAndOneCallbackTarget() {
    accounts.withTarget(RUN, new WebhookTarget("merchant-1", NOTIFY));
    WebhookDispatcher dispatcher = SimulatorDoubles.idleDeliveries(audit, ids, NOW);
    callbacks =
        SimulatorDoubles.callbacks(
            processes, transactions, accounts, dispatcher, SimulatorDoubles.clockAt(NOW), ids);
    completions =
        new CompleteHostedProcess(
            processes, callbacks, new GrowPayerCards(new InMemoryPaymentMethodFixtures()));
  }

  @Test
  void a_process_created_for_a_hold_completes_into_a_transaction_that_holds_funds() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    CompleteHostedProcess.Result result = complete(process, PayerOutcome.PAID, CARD);

    CompleteHostedProcess.Result.Completed completed = completedOf(result);
    assertThat(completed.process().normalizedStatus())
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(completed.process().nativeStatusCode()).isEqualTo(GrowNativeStatus.SUSPENDED_CODE);
    GrowTransaction created = theTransaction(completed);
    assertThat(created.holdsAnAuthorization()).isTrue();
    assertThat(completed.callbacks()).hasSize(1);
  }

  @Test
  void a_process_created_to_register_a_card_completes_into_a_token_and_holds_nothing() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_TOKEN_ONLY, true);

    CompleteHostedProcess.Result.Completed completed =
        completedOf(complete(process, PayerOutcome.PAID, CARD));

    assertThat(completed.process().normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(theTransaction(completed).holdsAnAuthorization()).isFalse();
    GrowProcessOutcome outcome = completed.process().outcome();
    assertThat(outcome).isNotNull();
    assertThat(outcome.cardToken())
        .as("the card token is neither the process token nor the hosted link of the same shape")
        .isNotBlank()
        .isNotIn(process.processToken(), process.hostedLink());
  }

  @Test
  void a_hold_that_carries_no_payment_intent_field_still_holds_funds() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true, false);

    CompleteHostedProcess.Result.Completed completed =
        completedOf(complete(process, PayerOutcome.PAID, CARD));

    assertThat(theTransaction(completed).holdsAnAuthorization()).isTrue();
    assertThat(completed.process().normalizedStatus())
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void the_chargetype_and_not_the_custom_field_decides_what_a_completion_produces() {
    GrowPaymentProcess holding = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true, false);
    GrowPaymentProcess registering = stored(GrowFields.CHARGE_TYPE_TOKEN_ONLY, true, true);

    assertThat(holding.holdsFunds()).isTrue();
    assertThat(registering.holdsFunds()).isFalse();
  }

  @Test
  void a_process_that_saves_no_card_mints_no_token() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, false);

    CompleteHostedProcess.Result.Completed completed =
        completedOf(complete(process, PayerOutcome.PAID, CARD));

    GrowProcessOutcome outcome = completed.process().outcome();
    assertThat(outcome).isNotNull();
    assertThat(outcome.cardToken()).isNull();
  }

  @Test
  void the_card_the_payer_named_is_the_card_the_completion_records() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    CompleteHostedProcess.Result.Completed completed =
        completedOf(complete(process, PayerOutcome.PAID, "sandbox-1121"));

    GrowProcessOutcome outcome = completed.process().outcome();
    assertThat(outcome).isNotNull();
    GrowPayerCard card = outcome.card();
    assertThat(card).isNotNull();
    assertThat(card.suffix()).isEqualTo("1121");
    assertThat(outcome.cardCharged().suffix()).isEqualTo("1121");
  }

  @Test
  void an_outcome_recorded_before_a_payer_chose_a_card_still_describes_the_published_card() {
    assertThat(GrowProcessOutcome.failed().cardCharged())
        .isEqualTo(GrowRecordedCard.THE_PUBLISHED_SANDBOX_CARD);
  }

  @Test
  void the_card_a_process_with_no_payer_charges_is_the_one_an_older_outcome_falls_back_to() {
    assertThat(GrowRecordedCard.chosenBy(GrowPayerCards.theCardAProcessWithNoPayerCharges()))
        .isEqualTo(GrowRecordedCard.THE_PUBLISHED_SANDBOX_CARD);
  }

  @Test
  void a_completion_that_reaches_the_provider_naming_no_card_is_refused_rather_than_guessed_at() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    assertThatThrownBy(
            () ->
                callbacks.reach(
                    process, GrowProcessOutcome.Kind.COMPLETED, null, ObjectOrigin.CONTROL_API))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void a_payer_who_meets_a_decline_fails_the_process_and_the_merchant_is_told() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    CompleteHostedProcess.Result.Completed completed =
        completedOf(complete(process, PayerOutcome.DECLINED, null));

    assertThat(completed.process().normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
    assertThat(completed.process().nativeStatusCode()).isEqualTo(GrowNativeStatus.NOT_PAID_CODE);
    assertThat(completed.createdTransactionId()).isNull();
    assertThat(completed.callbacks()).hasSize(1);
  }

  @Test
  void a_payer_who_abandons_the_page_leaves_it_uninitialized_and_tells_nobody() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    CompleteHostedProcess.Result.Completed completed =
        completedOf(complete(process, PayerOutcome.ABANDONED, null));

    assertThat(completed.process().normalizedStatus()).isEqualTo(NormalizedStatus.NOT_INITIALIZED);
    assertThat(completed.process().nativeStatusCode()).isEqualTo(GrowNativeStatus.NOT_PAID_CODE);
    assertThat(completed.callbacks()).isEmpty();
    assertThat(audit.all()).isEmpty();
  }

  @Test
  void
      a_completion_the_run_has_no_callback_target_for_is_refused_and_pays_once_one_is_configured() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    accounts.forgetTargets(RUN);

    CompleteHostedProcess.Result refused = complete(process, PayerOutcome.PAID, CARD);

    assertThat(refused).isInstanceOf(CompleteHostedProcess.Result.Refused.class);
    assertThat(((CompleteHostedProcess.Result.Refused) refused).inItsOwnWords())
        .contains(GrowCallbacks.NOTIFY_URL_IS_NOT_A_CONFIGURED_TARGET)
        .contains(CompleteHostedProcess.STILL_COMPLETABLE);
    assertThat(reread(process).outcome()).isNull();

    accounts.withTarget(RUN, new WebhookTarget("merchant-1", NOTIFY));
    CompleteHostedProcess.Result.Completed paid =
        completedOf(complete(process, PayerOutcome.PAID, CARD));

    assertThat(paid.createdTransactionId()).isNotNull();
    assertThat(paid.callbacks()).hasSize(1);
  }

  @Test
  void a_declined_completion_the_run_has_no_callback_target_for_is_refused_before_it_fails_it() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    accounts.forgetTargets(RUN);

    CompleteHostedProcess.Result refused = complete(process, PayerOutcome.DECLINED, null);

    assertThat(refused).isInstanceOf(CompleteHostedProcess.Result.Refused.class);
    assertThat(reread(process).outcome()).isNull();
  }

  @Test
  void a_payer_who_abandons_needs_no_configured_callback_target_because_no_callback_is_owed() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    accounts.forgetTargets(RUN);

    CompleteHostedProcess.Result.Completed left =
        completedOf(complete(process, PayerOutcome.ABANDONED, null));

    assertThat(left.callbacks()).isEmpty();
    GrowProcessOutcome outcome = reread(process).outcome();
    assertThat(outcome).isNotNull();
    assertThat(outcome.kind()).isEqualTo(GrowProcessOutcome.Kind.ABANDONED);
  }

  @Test
  void the_page_answers_a_run_with_no_callback_target_with_a_refusal_a_tester_can_act_on() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    accounts.forgetTargets(RUN);

    GrowHostedCheckout.Rendered refused = checkout.submit(process.hostedLink(), "PAID", CARD);

    assertThat(refused.httpStatus()).isEqualTo(GrowHostedCheckout.REFUSED);
    assertThat(refused.location()).isNull();
    assertThat(refused.body())
        .contains(GrowHostedPage.escaped(GrowCallbacks.NOTIFY_URL_IS_NOT_A_CONFIGURED_TARGET));
    assertThat(reread(process).outcome()).isNull();
  }

  @Test
  void
      a_completion_another_submission_carried_elsewhere_first_is_refused_as_already_at_an_outcome() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    processes.whileReachingAnOutcome(() -> complete(process, PayerOutcome.DECLINED, null));

    CompleteHostedProcess.Result raced = complete(process, PayerOutcome.PAID, CARD);

    assertThat(raced)
        .isEqualTo(
            new CompleteHostedProcess.Result.Refused(CompleteHostedProcess.ALREADY_COMPLETED));
    assertThat(((CompleteHostedProcess.Result.Refused) raced).inItsOwnWords())
        .doesNotContain(CompleteHostedProcess.STILL_COMPLETABLE);
    GrowProcessOutcome outcome = reread(process).outcome();
    assertThat(outcome).isNotNull();
    assertThat(outcome.kind()).isEqualTo(GrowProcessOutcome.Kind.FAILED);
  }

  @Test
  void a_completion_another_submission_carried_elsewhere_first_creates_no_transaction_of_its_own() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    processes.whileReachingAnOutcome(() -> complete(process, PayerOutcome.DECLINED, null));

    complete(process, PayerOutcome.PAID, CARD);

    assertThat(transactions.all()).isEmpty();
  }

  @Test
  void the_page_answers_a_submission_another_one_beat_as_a_refusal_and_not_as_a_server_fault() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    processes.whileReachingAnOutcome(() -> complete(process, PayerOutcome.DECLINED, null));

    GrowHostedCheckout.Rendered refused = checkout.submit(process.hostedLink(), "PAID", CARD);

    assertThat(refused.httpStatus()).isEqualTo(GrowHostedCheckout.REFUSED);
    assertThat(refused.location()).isNull();
    assertThat(refused.body()).contains("already reached an outcome");
  }

  @Test
  void an_abandoned_process_refuses_to_be_delivered_even_when_a_caller_asks_directly() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    CompleteHostedProcess.Result.Completed completed =
        completedOf(complete(process, PayerOutcome.ABANDONED, null));

    GrowPaymentProcess abandoned = completed.process();

    assertThatThrownBy(() -> callbacks.deliver(abandoned, NOTIFY, null, null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(GrowCallbacks.AN_ABANDONED_PROCESS_TELLS_NOBODY);
  }

  @Test
  void a_process_already_at_an_outcome_is_not_completed_a_second_time() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    complete(process, PayerOutcome.PAID, CARD);

    CompleteHostedProcess.Result again =
        completions.byProcessId(
            RUN, process.processId(), PayerOutcome.PAID, CARD, ObjectOrigin.PROVIDER_API);

    assertThat(again)
        .isEqualTo(
            new CompleteHostedProcess.Result.Refused(CompleteHostedProcess.ALREADY_COMPLETED));
  }

  @Test
  void a_card_the_run_does_not_offer_is_refused_and_the_refusal_names_what_it_does() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    CompleteHostedProcess.Result refused = complete(process, PayerOutcome.PAID, "no-such-card");

    assertThat(refused).isInstanceOf(CompleteHostedProcess.Result.Refused.class);
    String words = ((CompleteHostedProcess.Result.Refused) refused).inItsOwnWords();
    assertThat(words).contains("no-such-card").contains(CARD);
    assertThat(processes.findByProcessId(RUN, process.processId()).orElseThrow().outcome())
        .isNull();
  }

  @Test
  void a_process_no_run_holds_is_reported_as_no_such_process_by_either_door() {
    assertThat(
            completions.byProcessId(
                RUN, "000000", PayerOutcome.ABANDONED, null, ObjectOrigin.CONTROL_API))
        .isInstanceOf(CompleteHostedProcess.Result.NoSuchProcess.class);
    assertThat(
            completions.byHostedLink(
                "nothing", PayerOutcome.ABANDONED, null, ObjectOrigin.PROVIDER_API))
        .isInstanceOf(CompleteHostedProcess.Result.NoSuchProcess.class);
  }

  @Test
  void the_link_the_create_call_handed_out_reaches_the_process_that_issued_it() {
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    CompleteHostedProcess.Result.Completed completed =
        completedOf(
            completions.byHostedLink(
                process.hostedLink(), PayerOutcome.PAID, CARD, ObjectOrigin.PROVIDER_API));

    assertThat(completed.process().processId()).isEqualTo(process.processId());
  }

  @Test
  void the_page_and_the_control_route_reach_the_same_completion() {
    GrowHostedCheckout checkout =
        new GrowHostedCheckout(
            processes, completions, cards(), audit, SimulatorDoubles.clockAt(NOW), ids);
    GrowPaymentProcess throughThePage = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    GrowPaymentProcess throughTheControlApi = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    checkout.submit(throughThePage.hostedLink(), "PAID", CARD);
    completions.byProcessId(
        RUN, throughTheControlApi.processId(), PayerOutcome.PAID, CARD, ObjectOrigin.CONTROL_API);

    GrowPaymentProcess page = reread(throughThePage);
    GrowPaymentProcess control = reread(throughTheControlApi);
    assertThat(page.normalizedStatus()).isEqualTo(control.normalizedStatus());
    assertThat(page.nativeStatusCode()).isEqualTo(control.nativeStatusCode());
    GrowProcessOutcome ofThePage = page.outcome();
    GrowProcessOutcome ofTheControlApi = control.outcome();
    assertThat(ofThePage).isNotNull();
    assertThat(ofTheControlApi).isNotNull();
    assertThat(ofThePage.kind()).isEqualTo(ofTheControlApi.kind());
    assertThat(ofThePage.card()).isEqualTo(ofTheControlApi.card());
  }

  @Test
  void the_page_offers_the_process_it_was_opened_for_and_nothing_for_a_link_it_does_not_hold() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    GrowHostedCheckout.Rendered offered = checkout.open(process.hostedLink());
    assertThat(offered.httpStatus()).isEqualTo(GrowHostedCheckout.OFFERED);
    assertThat(offered.body()).contains(CARD);
    assertThat(offered.location()).isNull();

    assertThat(checkout.open("nothing").httpStatus()).isEqualTo(GrowHostedCheckout.NO_SUCH_LINK);
    assertThat(checkout.open(null).httpStatus()).isEqualTo(GrowHostedCheckout.NO_SUCH_LINK);
    assertThat(checkout.open("  ").httpStatus()).isEqualTo(GrowHostedCheckout.NO_SUCH_LINK);
  }

  @Test
  void every_document_this_page_answers_with_carries_four_defences_and_the_redirect_none() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    GrowHostedCheckout.Rendered offered = checkout.open(process.hostedLink());
    GrowHostedCheckout.Rendered unknown = checkout.open("nothing");
    GrowHostedCheckout.Rendered refused = checkout.submit(process.hostedLink(), "PAY", CARD);
    GrowHostedCheckout.Rendered redirected = checkout.submit(process.hostedLink(), "PAID", CARD);

    for (GrowHostedCheckout.Rendered document : List.of(offered, unknown, refused)) {
      assertThat(document.headers())
          .as(
              "the answer carries the declaration, so deleting the one line a controller writes"
                  + " it with is the only thing left that no unit test can see")
          .containsExactly(
              Map.entry("Cache-Control", "no-store"),
              Map.entry("X-Content-Type-Options", "nosniff"),
              Map.entry("Referrer-Policy", "no-referrer"),
              Map.entry(
                  "Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'"));
    }
    assertThat(redirected.location()).isNotNull();
    assertThat(redirected.headers())
        .as(
            "a redirect has no document, so it declares nothing: it is the answer of this route a"
                + " shop's own client reads, and a header the live provider does not send there is"
                + " a difference a client could tell")
        .isEmpty();
  }

  @Test
  void the_page_sends_a_payer_who_paid_on_to_the_merchant_and_records_both_halves_of_the_call() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    GrowHostedCheckout.Rendered answered = checkout.submit(process.hostedLink(), "PAID", CARD);

    assertThat(answered.httpStatus()).isEqualTo(GrowHostedCheckout.REDIRECTED);
    assertThat(answered.location()).startsWith("https://merchant.example/thanks");

    List<AuditRecord> ofThePage = onThePage();
    assertThat(ofThePage).hasSize(2);
    assertThat(ofThePage.get(0).origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(ofThePage.get(0).direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(ofThePage.get(1).direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(ofThePage.get(1).httpStatus()).isEqualTo(GrowHostedCheckout.REDIRECTED);
    assertThat(ofThePage.get(1).providerObjectId()).isEqualTo(process.processId());
    assertThat(audit.all())
        .anySatisfy(
            record ->
                assertThat(record.direction()).isEqualTo(AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT));
  }

  @Test
  void a_completion_the_page_carried_out_refused_nothing_and_states_no_refusal_subject() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    checkout.submit(process.hostedLink(), "PAID", CARD);

    assertThat(onThePage().get(1).refusal()).isNull();
  }

  @Test
  void a_completion_the_page_refused_states_the_process_it_refused_the_payer_on() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    checkout.submit(process.hostedLink(), "EXPIRED", CARD);

    assertThat(onThePage().get(1).refusal())
        .isEqualTo(RefusalSubject.theObject(process.processId()));
  }

  @Test
  void a_payer_who_named_no_card_leaves_no_card_field_in_the_trail() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    checkout.submit(process.hostedLink(), "DECLINED", null);

    assertThat(onThePage().get(0).sanitizedBody())
        .doesNotContainKey(GrowHostedPage.CARD)
        .containsEntry(GrowHostedPage.OUTCOME, List.of("DECLINED"));
  }

  @Test
  void the_row_the_page_writes_names_the_fields_in_the_order_the_page_put_them_and_not_a_salts() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    checkout.submit(process.hostedLink(), "PAID", CARD);

    assertThat(onThePage().get(0).sanitizedBody().keySet())
        .as(
            "the page takes a link, then an outcome, then the card a payer paid with, and a tester"
                + " reads this row beside the answer written from it, so the fields are listed in"
                + " the order the page put them rather than an order drawn from the keys' hashes"
                + " and a per-JVM salt, which is what a Map.of of the first two gave this row"
                + " before: reading the names as a sequence is what says which of the two it is")
        .containsExactly(GrowHostedPage.LINK, GrowHostedPage.OUTCOME, GrowHostedPage.CARD);
  }

  @Test
  void the_page_never_prints_the_link_it_was_opened_with_into_the_trail() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    checkout.submit(process.hostedLink(), "PAID", CARD);

    assertThat(onThePage().get(0).sanitizedBody().get(GrowHostedPage.LINK))
        .doesNotContain(process.hostedLink());
  }

  @Test
  void the_page_refuses_an_outcome_no_payer_can_meet_and_completes_nothing() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    GrowHostedCheckout.Rendered refused = checkout.submit(process.hostedLink(), "EXPIRED", CARD);

    assertThat(refused.httpStatus()).isEqualTo(GrowHostedCheckout.REFUSED);
    assertThat(refused.location()).isNull();
    assertThat(refused.body()).contains("PAID, DECLINED, ABANDONED");
    assertThat(reread(process).outcome()).isNull();

    List<AuditRecord> ofThePage = onThePage();
    assertThat(ofThePage).hasSize(2);
    assertThat(ofThePage.get(0).direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(ofThePage.get(0).sanitizedBody())
        .containsEntry(GrowHostedPage.OUTCOME, List.of("EXPIRED"));
    assertThat(ofThePage.get(1).direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(ofThePage.get(1).httpStatus()).isEqualTo(GrowHostedCheckout.REFUSED);
    assertThat(ofThePage.get(1).outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void the_page_refuses_a_completion_the_provider_refused_and_says_why() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);
    checkout.submit(process.hostedLink(), "PAID", CARD);

    GrowHostedCheckout.Rendered again = checkout.submit(process.hostedLink(), "PAID", CARD);

    assertThat(again.httpStatus()).isEqualTo(GrowHostedCheckout.REFUSED);
    assertThat(again.body()).contains("already reached an outcome");

    List<AuditRecord> ofThePage = onThePage();
    assertThat(ofThePage).hasSize(4);
    assertThat(ofThePage.get(3).direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(ofThePage.get(3).httpStatus()).isEqualTo(GrowHostedCheckout.REFUSED);
    assertThat(ofThePage.get(3).outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void the_page_refuses_a_submission_for_a_link_it_holds_no_process_for() {
    GrowHostedCheckout checkout = checkout();

    GrowHostedCheckout.Rendered nothing = checkout.submit("nothing", "PAID", CARD);

    assertThat(nothing.httpStatus()).isEqualTo(GrowHostedCheckout.NO_SUCH_LINK);
    assertThat(nothing.body()).contains("does not belong to any payment process");
  }

  @Test
  void a_payer_who_did_not_pay_reaches_the_provider_without_the_card_the_form_still_carried() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    GrowHostedCheckout.Rendered left = checkout.submit(process.hostedLink(), "ABANDONED", CARD);

    assertThat(left.httpStatus()).isEqualTo(GrowHostedCheckout.REDIRECTED);
    assertThat(left.location()).startsWith("https://merchant.example/cancel");
    GrowProcessOutcome outcome = reread(process).outcome();
    assertThat(outcome).isNotNull();
    assertThat(outcome.kind()).isEqualTo(GrowProcessOutcome.Kind.ABANDONED);
    assertThat(onThePage().get(0).sanitizedBody()).containsKey(GrowHostedPage.CARD);
  }

  private List<AuditRecord> onThePage() {
    return audit.all().stream()
        .filter(record -> GrowHostedPage.PATH.equals(record.path()))
        .toList();
  }

  private GrowHostedCheckout checkout() {
    return new GrowHostedCheckout(
        processes, completions, cards(), audit, SimulatorDoubles.clockAt(NOW), ids);
  }

  private static GrowPayerCards cards() {
    return new GrowPayerCards(new InMemoryPaymentMethodFixtures());
  }

  private CompleteHostedProcess.Result complete(
      GrowPaymentProcess process, PayerOutcome outcome, String card) {
    return completions.byProcessId(
        RUN, process.processId(), outcome, card, ObjectOrigin.PROVIDER_API);
  }

  private static CompleteHostedProcess.Result.Completed completedOf(
      CompleteHostedProcess.Result result) {
    assertThat(result).isInstanceOf(CompleteHostedProcess.Result.Completed.class);
    return (CompleteHostedProcess.Result.Completed) result;
  }

  private GrowTransaction theTransaction(CompleteHostedProcess.Result.Completed completed) {
    String transactionId = completed.createdTransactionId();
    assertThat(transactionId).isNotNull();
    return transactions.findByTransactionId(RUN, transactionId).orElseThrow();
  }

  private GrowPaymentProcess reread(GrowPaymentProcess process) {
    return processes.findByProcessId(RUN, process.processId()).orElseThrow();
  }

  private GrowPaymentProcess stored(String chargeType, boolean saveCardToken) {
    return stored(chargeType, saveCardToken, true);
  }

  private GrowPaymentProcess stored(
      String chargeType, boolean saveCardToken, boolean paymentIntentField) {
    String processId = ids.digits(6);
    GrowPaymentProcess process =
        new GrowPaymentProcess(
            RUN,
            processId,
            "token-" + processId,
            "merchant-1",
            chargeType,
            "120.50",
            "A hold",
            saveCardToken,
            "https://merchant.example/thanks",
            "https://merchant.example/cancel",
            NOTIFY,
            "Israel Israeli",
            "0505503144",
            "payer@merchant.example",
            paymentIntentField
                ? Map.of("cField1", "session-abc", "cField2", "intent-99")
                : Map.of("cField1", "session-abc"),
            "link-" + processId,
            GrowNativeStatus.NOT_PAID_CODE,
            GrowNativeStatus.NOT_PAID,
            NormalizedStatus.NOT_INITIALIZED,
            null,
            ObjectOrigin.PROVIDER_API,
            NOW,
            NOW);
    assertThat(processes.insertIfAbsent(process)).isTrue();
    return process;
  }

  @Test
  void
      the_row_this_page_answers_with_carries_the_status_it_moved_the_process_to_and_nothing_else() {
    GrowHostedCheckout checkout = checkout();
    GrowPaymentProcess process = stored(GrowFields.CHARGE_TYPE_SUSPENDED, true);

    checkout.submit(process.hostedLink(), "PAID", CARD);

    AuditRecord answered = onThePage().get(1);
    assertThat(answered.direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(answered.sanitizedBody())
        .as(
            "grow declares five keys, every one of them under data., and this row carries one key"
                + " that is not: its declaration is applied here and can match nothing")
        .containsExactly(
            entry("normalizedStatus", List.of(NormalizedStatus.READY_FOR_CAPTURE.name())));
  }
}
