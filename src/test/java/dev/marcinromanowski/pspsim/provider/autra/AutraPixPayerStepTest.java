package dev.marcinromanowski.pspsim.provider.autra;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionControl;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.PayerSteps;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.TransportFailure;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraPixPayerStepTest {

  private static final TestRunId RUN = TestRunId.of("autra-pix-payer");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String PIX_ID = "cdb445694be7486e830f720658f9a1a1";
  private static final String ORDER = "pedido-abc-123";
  private static final String TARGET = "https://merchant.example/autra/acquiring";
  private static final String A_SECRET = "a-tenant-secret-of-thirty-two-chars";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.PixPayments pixPayments = new AutraDoubles.PixPayments();
  private final AutraDoubles.Accounts accounts = new AutraDoubles.Accounts();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          deliveries,
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          audit,
          AutraDoubles.clockAt(NOW),
          new AutraDoubles.CountingIds(),
          new RetryPlan(List.of(Duration.ZERO)),
          new DeliveryShaping(rules),
          List.of(new AutraCallbackAcknowledgement()),
          List.of(),
          OutsideAnyUnitOfWork.RUNS_AT_ONCE);

  private final AutraCallbacks callbacks =
      new AutraCallbacks(
          pixPayments, merchants, accounts, dispatcher, new AutraDoubles.CountingIds());

  private final CompletePixPayment payerStep =
      new CompletePixPayment(pixPayments, callbacks, new RecordedUnitsOfWork());

  @Test
  void a_pix_awaiting_its_payer_offers_a_payer_step_that_collects_no_card() {
    aPixThatIs(AutraStatus.PENDING);

    HostedCompletionOffer offer = payerStep.offer(RUN, PIX_ID, NOW);

    assertThat(offer).isInstanceOf(HostedCompletionOffer.OfferedWithoutACard.class);
    assertThat(offer.namesACard()).isFalse();
    assertThat(((HostedCompletionOffer.OfferedWithoutACard) offer).whatThePayerPaysWithInstead())
        .as(
            "this sentence is drawn above the payer choices, and a Pix is no page: a reading taken"
                + " from the constant it comes from would pass on any words at all")
        .contains("this Pix names no card and a payer step on it names none either");
  }

  @Test
  void a_pix_admits_the_two_steps_autra_has_and_offers_no_declined_choice_a_tester_could_pick() {
    aPixThatIs(AutraStatus.PENDING);

    PayerSteps admitted =
        ((HostedCompletionOffer.OfferedWithoutACard) payerStep.offer(RUN, PIX_ID, NOW))
            .theStepsItOffers();

    assertThat(admitted.theStepsAPayerMayTake())
        .containsExactly(PayerOutcome.PAID, PayerOutcome.ABANDONED);
    assertThat(admitted.asATesterReadsIt(PayerOutcome.PAID))
        .isEqualTo("the payer pasted the payload into their own bank app and paid it");
    assertThat(admitted.asATesterReadsIt(PayerOutcome.ABANDONED))
        .isEqualTo("the payer never paid the payload and let it expire");
    assertThat(admitted.offers(PayerOutcome.DECLINED)).isFalse();
    assertThat(admitted.whatItHasInsteadOf(PayerOutcome.DECLINED))
        .isEqualTo(CompletePixPayment.NO_PAYER_IS_DECLINED_ON_A_PIX_QR);
  }

  @Test
  void
      what_taking_a_step_on_a_pix_does_is_said_by_autra_and_charges_no_card_a_pix_never_collects() {
    aPixThatIs(AutraStatus.PENDING);

    PayerSteps admitted =
        ((HostedCompletionOffer.OfferedWithoutACard) payerStep.offer(RUN, PIX_ID, NOW))
            .theStepsItOffers();

    assertThat(admitted.whatTakingOneDoes())
        .isEqualTo(
            "it records the payer of this Pix as having paid the payload from their own bank app or"
                + " as having left it to expire, moves the Pix to the state autra publishes for"
                + " that, and sends the callback autra sends the merchant; a Pix collects no card,"
                + " so none is named here and nothing is charged");
    assertThat(admitted.whatTakingOneDoes())
        .as("a caution about charging a card is false of a payment method that collects none")
        .doesNotContain("charges the card")
        .doesNotContain("closing the page");
  }

  @Test
  void no_step_a_pix_admits_is_worded_as_a_page_the_payer_of_one_is_never_sent_to() {
    aPixThatIs(AutraStatus.PENDING);

    PayerSteps admitted =
        ((HostedCompletionOffer.OfferedWithoutACard) payerStep.offer(RUN, PIX_ID, NOW))
            .theStepsItOffers();

    for (PayerOutcome step : admitted.theStepsAPayerMayTake()) {
      assertThat(admitted.asATesterReadsIt(step)).doesNotContain("page");
    }
  }

  @Test
  void an_object_no_pix_of_this_run_answers_to_offers_no_payer_step_and_says_what_does() {
    assertThat(payerStep.offer(RUN, PIX_ID, NOW))
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                AutraApi.AUTRA, CompletePixPayment.ONLY_A_PIX_HAS_A_PAYER_STEP));
  }

  @Test
  void a_pix_a_payer_already_paid_offers_no_second_payer_step() {
    aPixThatIs(AutraStatus.AUTHORIZED);

    HostedCompletionOffer offer = payerStep.offer(RUN, PIX_ID, NOW);

    assertThat(offer).isInstanceOf(HostedCompletionOffer.NotOffered.class);
    assertThat(((HostedCompletionOffer.NotOffered) offer).whatThisProviderHasInstead())
        .contains("cannot pay it a second time");
  }

  @Test
  void a_payer_who_paid_moves_the_pix_to_the_status_autra_instructs_a_merchant_to_treat_as_paid() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    HostedCompletionOutcome outcome = complete(PayerOutcome.PAID, NOW);

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.Completed.class);
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.AUTHORIZED);
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().normalizedStatusAt(NOW))
        .isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void a_payer_who_left_moves_the_pix_to_the_only_state_autra_publishes_as_a_cancellation() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    HostedCompletionOutcome outcome = complete(PayerOutcome.ABANDONED, NOW);

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.Completed.class);
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.EXPIRED);
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().normalizedStatusAt(NOW))
        .isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void a_payer_who_paid_leaves_the_financial_transaction_event_queued_and_reported_back() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    HostedCompletionOutcome.Completed completed =
        (HostedCompletionOutcome.Completed) complete(PayerOutcome.PAID, NOW);

    assertThat(completed.webhookJobIds()).hasSize(1);
    WebhookDelivery queued = deliveries.of(AutraApi.AUTRA, RUN).get(0);
    assertThat(completed.webhookJobIds()).containsExactly(queued.deliveryId());
    assertThat(queued.eventType()).isEqualTo(AutraPixEvent.FINANCIAL_TRANSACTION.eventType());
    assertThat(queued.providerObjectId()).isEqualTo(PIX_ID);
    assertThat(queued.targetUrl()).isEqualTo(TARGET);
  }

  @Test
  void the_event_a_paid_pix_queues_reports_the_status_the_payer_reached() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    complete(PayerOutcome.PAID, NOW);

    assertThat(theBodyQueued()).contains("\"status\":\"AUTHORIZED\"");
    assertThat(theBodyQueued()).contains("\"transactionStatus\":\"AUTHORIZED\"");
  }

  @Test
  void the_event_an_abandoned_pix_queues_reports_the_expiry_rather_than_a_payment() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    complete(PayerOutcome.ABANDONED, NOW);

    assertThat(theBodyQueued()).contains("\"status\":\"EXPIRED\"");
    assertThat(theBodyQueued()).contains("\"transactionStatus\":\"EXPIRED\"");
  }

  @Test
  void a_completion_of_a_merchant_this_run_configures_no_target_for_moves_the_pix_and_sends_none() {
    aPixThatIs(AutraStatus.PENDING);

    HostedCompletionOutcome.Completed completed =
        (HostedCompletionOutcome.Completed) complete(PayerOutcome.PAID, NOW);

    assertThat(completed.webhookJobIds()).isEmpty();
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.AUTHORIZED);
  }

  @Test
  void a_pix_that_moved_says_why_nothing_was_sent_when_the_run_configures_no_target_for_it() {
    aPixThatIs(AutraStatus.PENDING);

    assertThat(callbacks.whyNothingWasSentFor(pixPayments.findById(RUN, PIX_ID).orElseThrow()))
        .isEqualTo(AutraCallbacks.THE_RUN_CONFIGURES_NO_TARGET_FOR_THIS_MERCHANT);
  }

  @Test
  void a_pix_whose_run_configures_a_target_reports_nothing_in_place_of_a_delivery() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    assertThat(callbacks.whyNothingWasSentFor(pixPayments.findById(RUN, PIX_ID).orElseThrow()))
        .isEmpty();
  }

  @Test
  void a_second_payer_step_on_a_pix_already_paid_is_refused_and_queues_no_second_event() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);
    complete(PayerOutcome.PAID, NOW);

    HostedCompletionOutcome second = complete(PayerOutcome.PAID, NOW);

    assertThat(second).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(((HostedCompletionOutcome.RefusedByTheProvider) second).inItsOwnWords())
        .contains("cannot pay it a second time");
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).hasSize(1);
  }

  @Test
  void
      a_pix_another_request_moved_between_the_read_and_the_write_is_refused_on_what_it_holds_now() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);
    pixPayments.movedByAnotherRequestFirst(PIX_ID, AutraStatus.AUTHORIZED);

    HostedCompletionOutcome outcome = complete(PayerOutcome.PAID, NOW);

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(((HostedCompletionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .isEqualTo(pixPayments.findById(RUN, PIX_ID).orElseThrow().whyItIsNoLongerWaitingAt(NOW));
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_pix_removed_between_the_read_and_the_write_is_answered_as_no_such_object() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);
    pixPayments.removedByAnotherRequestFirst(PIX_ID);

    HostedCompletionOutcome outcome = complete(PayerOutcome.PAID, NOW);

    assertThat(outcome).isEqualTo(new HostedCompletionOutcome.NoSuchObject());
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_pix_a_payer_let_expire_says_so_rather_than_saying_it_was_paid() {
    aPixThatIs(AutraStatus.EXPIRED);

    AutraPixPayment pix = pixPayments.findById(RUN, PIX_ID).orElseThrow();

    assertThat(pix.whyItIsNoLongerWaitingAt(NOW))
        .contains("expired unpaid")
        .contains("terminal state")
        .doesNotContain("second time");
    assertThat(payerStep.offer(RUN, PIX_ID, NOW))
        .isEqualTo(
            new HostedCompletionOffer.NotOffered(
                AutraApi.AUTRA, pix.whyItIsNoLongerWaitingAt(NOW)));
  }

  @Test
  void a_pix_a_payer_already_paid_says_it_was_paid_rather_than_that_it_expired() {
    aPixThatIs(AutraStatus.AUTHORIZED);

    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().whyItIsNoLongerWaitingAt(NOW))
        .contains("already paid")
        .contains("cannot pay it a second time")
        .doesNotContain("expired unpaid");
  }

  @Test
  void a_pix_that_settled_says_it_was_paid_by_the_same_words_an_authorized_one_does() {
    aPixThatIs(AutraStatus.SETTLED);

    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().whyItIsNoLongerWaitingAt(NOW))
        .contains("already paid");
  }

  @Test
  void a_merchant_answering_outside_the_two_hundreds_is_judged_a_refusal_of_the_delivery() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);
    callbacks.schedule(scheduleOf(null), NOW);
    WebhookDelivery queued = deliveries.of(AutraApi.AUTRA, RUN).get(0);
    AutraCallbackAcknowledgement judging = new AutraCallbackAcknowledgement();

    assertThat(judging.provider()).isEqualTo(AutraApi.AUTRA);
    assertThat(judging.judge(queued, new MerchantResponse.Answered(200, "")))
        .isEqualTo(Acknowledgement.ACKNOWLEDGED);
    assertThat(judging.judge(queued, new MerchantResponse.Answered(500, "")))
        .isEqualTo(Acknowledgement.REFUSED);
    assertThat(
            judging.judge(
                queued, new MerchantResponse.NotAnswered(TransportFailure.CONNECTION_ERROR)))
        .isEqualTo(Acknowledgement.REFUSED);
  }

  @Test
  void the_two_session_kinds_name_two_operations_two_story_key_spaces_and_one_code() {
    assertThat(AutraSessionKind.CARD.operation()).isEqualTo(AutraOperations.INITIALIZE);
    assertThat(AutraSessionKind.PIX.operation()).isEqualTo(AutraOperations.PIX_INITIALIZE);
    assertThat(AutraSessionKind.CARD.storyKeyOf(DOCUMENT))
        .isEqualTo(AutraStoryKey.ofSession(DOCUMENT))
        .isNotEqualTo(AutraSessionKind.PIX.storyKeyOf(DOCUMENT));
    assertThat(AutraSessionKind.PIX.storyKeyOf(DOCUMENT))
        .isEqualTo(AutraStoryKey.ofPixSession(DOCUMENT));
    assertThat(AutraSessionKind.CARD.answersWithACode()).isFalse();
    assertThat(AutraSessionKind.PIX.answersWithACode()).isTrue();
  }

  @Test
  void a_payer_a_pix_qr_declines_is_refused_because_autra_publishes_no_declined_pix_state() {
    aPixThatIs(AutraStatus.PENDING);

    HostedCompletionOutcome outcome = complete(PayerOutcome.DECLINED, NOW);

    assertThat(outcome)
        .isEqualTo(
            new HostedCompletionOutcome.RefusedByTheProvider(
                CompletePixPayment.NO_PAYER_IS_DECLINED_ON_A_PIX_QR));
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.PENDING);
  }

  @Test
  void a_step_a_pix_does_not_admit_is_refused_before_the_pix_can_be_moved_or_a_callback_queued() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    assertThatThrownBy(
            () ->
                HostedCompletionControl.asked(
                    payerStep.offer(RUN, PIX_ID, NOW),
                    RUN,
                    PIX_ID,
                    PayerOutcome.DECLINED.name(),
                    null,
                    ObjectOrigin.UI))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(CompletePixPayment.NO_PAYER_IS_DECLINED_ON_A_PIX_QR)
        .hasMessageContaining("it has no declined state at all");
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .as("a step nothing accounts for must not expire a Pix on the way to being reported")
        .isEqualTo(AutraStatus.PENDING);
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_payer_step_on_an_object_this_run_does_not_hold_reports_no_such_object() {
    assertThat(complete(PayerOutcome.PAID, NOW))
        .isEqualTo(new HostedCompletionOutcome.NoSuchObject());
  }

  @Test
  void a_payer_who_pays_a_qr_the_clock_has_passed_is_refused_in_the_words_of_an_expired_qr() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);
    Instant past = NOW.plus(CreatePixCheckout.THE_QR_LIVES_FOR);

    HostedCompletionOutcome outcome = complete(PayerOutcome.PAID, past);

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.RefusedByTheProvider.class);
    assertThat(((HostedCompletionOutcome.RefusedByTheProvider) outcome).inItsOwnWords())
        .contains("expired unpaid")
        .contains("terminal state");
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_qr_the_clock_has_passed_reads_expired_while_the_row_still_carries_what_was_written() {
    aPixThatIs(AutraStatus.PENDING);
    Instant past = NOW.plus(CreatePixCheckout.THE_QR_LIVES_FOR);
    AutraPixPayment pix = pixPayments.findById(RUN, PIX_ID).orElseThrow();

    assertThat(pix.statusAt(past)).isEqualTo("EXPIRED");
    assertThat(pix.normalizedStatusAt(past)).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(pix.statusAt(past.minusMillis(1))).isEqualTo("PENDING");
    assertThat(pix.normalizedStatusAt(past.minusMillis(1))).isEqualTo(NormalizedStatus.PROCESSING);
    assertThat(pix.nativeStatus()).isEqualTo("PENDING");
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus()).isEqualTo("PENDING");
  }

  @Test
  void the_deadline_expires_a_qr_still_waiting_and_leaves_a_paid_one_paid_and_holding_its_money() {
    aPixThatIs(AutraStatus.AUTHORIZED);
    Instant past = NOW.plus(CreatePixCheckout.THE_QR_LIVES_FOR);
    AutraPixPayment paid = pixPayments.findById(RUN, PIX_ID).orElseThrow();

    assertThat(paid.statusAt(past)).isEqualTo("AUTHORIZED");
    assertThat(paid.normalizedStatusAt(past)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(paid.isAwaitingThePayerAt(past)).isFalse();
    assertThat(paid.ledgerAt(past).holds(OperationKind.CAPTURE)).isTrue();
    assertThat(paid.whyItIsNoLongerWaitingAt(past))
        .contains("already paid")
        .doesNotContain("expired unpaid");
  }

  @Test
  void a_payer_who_pays_a_qr_a_moment_before_it_expires_is_still_admitted() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    HostedCompletionOutcome outcome =
        complete(PayerOutcome.PAID, NOW.plus(CreatePixCheckout.THE_QR_LIVES_FOR).minusMillis(1));

    assertThat(outcome).isInstanceOf(HostedCompletionOutcome.Completed.class);
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.AUTHORIZED);
  }

  @Test
  void a_callback_asked_for_a_pix_nothing_has_paid_yet_is_refused_in_the_vendors_own_words() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    WebhookScheduleRequest namingNoEvent = scheduleOf(null);

    assertThatThrownBy(() -> callbacks.schedule(namingNoEvent, NOW))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(AutraCallbacks.A_PIX_NOTHING_HAS_PAID_SENDS_NOTHING);
  }

  @Test
  void a_callback_asked_for_an_object_no_pix_of_this_run_answers_to_reports_no_such_object() {
    WebhookScheduleRequest namingNoEvent = scheduleOf(null);

    assertThatThrownBy(() -> callbacks.schedule(namingNoEvent, NOW))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessage(AutraCallbacks.ONLY_A_PIX_HAS_A_CALLBACK);
  }

  @Test
  void a_callback_of_a_paid_pix_is_the_financial_transaction_when_the_request_names_no_event() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    List<ScheduledWebhook> jobs = callbacks.schedule(scheduleOf(null), NOW);

    assertThat(jobs).hasSize(1);
    assertThat(deliveries.of(AutraApi.AUTRA, RUN).get(0).eventType())
        .isEqualTo(AutraPixEvent.FINANCIAL_TRANSACTION.eventType());
  }

  @Test
  void the_payout_of_a_settled_pix_is_resent_and_reports_the_settlement_it_publishes() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.SETTLED);

    callbacks.schedule(scheduleOf(AutraPixEvent.PAYOUT.eventType()), NOW);

    assertThat(theBodyQueued()).contains("\"eventType\":\"acquiring.payout\"");
    assertThat(theBodyQueued()).contains("\"subject\":\"PAYOUT\"");
    assertThat(theBodyQueued()).contains("\"status\":\"SETTLED\"");
  }

  @Test
  void the_payout_of_a_pix_that_expired_unpaid_is_refused_because_nothing_settled() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.EXPIRED);

    WebhookScheduleRequest askingForAPayout = scheduleOf(AutraPixEvent.PAYOUT.eventType());

    assertThatThrownBy(() -> callbacks.schedule(askingForAPayout, NOW))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(AutraCallbacks.A_PAYOUT_FOLLOWS_A_PAYMENT);
  }

  @Test
  void a_callback_naming_an_event_this_contract_renders_no_payload_for_is_refused_naming_both() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    WebhookScheduleRequest namingAnEventWithNoPayload = scheduleOf("acquiring.financial_cycle");

    assertThatThrownBy(() -> callbacks.schedule(namingAnEventWithNoPayload, NOW))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(AutraPixEvent.NO_SUCH_EVENT);
  }

  @Test
  void the_financial_transaction_reports_its_outcome_at_the_envelope_and_inside_the_payload() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    callbacks.schedule(scheduleOf(AutraPixEvent.FINANCIAL_TRANSACTION.eventType()), NOW);

    AutraJson.JsonObject body = AutraJsonReader.objectIn(theBodyQueued()).orElseThrow();
    assertThat(body.text(AutraFields.STATUS)).contains(AutraStatus.AUTHORIZED);
    assertThat(
            body.object(AutraFields.OPERATION_DATA)
                .orElseThrow()
                .object(AutraFields.PAYLOAD)
                .orElseThrow()
                .text(AutraFields.TRANSACTION_STATUS))
        .contains(AutraStatus.AUTHORIZED);
  }

  @Test
  void the_payout_carries_no_top_level_status_because_its_own_published_example_carries_none() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.SETTLED);

    callbacks.schedule(scheduleOf(AutraPixEvent.PAYOUT.eventType()), NOW);

    AutraJson.JsonObject body = AutraJsonReader.objectIn(theBodyQueued()).orElseThrow();
    assertThat(body.text(AutraFields.STATUS)).isEmpty();
    assertThat(
            body.object(AutraFields.OPERATION_DATA)
                .orElseThrow()
                .object(AutraFields.PAYLOAD)
                .orElseThrow()
                .text(AutraFields.STATUS))
        .contains(AutraStatus.SETTLED);
  }

  @Test
  void the_envelope_carries_the_routing_document_and_the_correlation_fields_this_contract_holds() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    callbacks.schedule(scheduleOf(null), NOW);

    AutraJson.JsonObject body = AutraJsonReader.objectIn(theBodyQueued()).orElseThrow();
    assertThat(body.text(AutraFields.USER_DOCUMENT)).contains(DOCUMENT);
    assertThat(body.text(AutraFields.EVENT_ID)).isPresent();
    assertThat(body.text(AutraFields.OCCURRED_AT)).contains("2026-09-22T10:00:00Z");
    AutraJson.JsonObject operationData = body.object(AutraFields.OPERATION_DATA).orElseThrow();
    assertThat(operationData.text(AutraFields.SUBJECT)).contains("FINANCIAL_TRANSACTION");
    assertThat(operationData.text(AutraFields.PRODUCT_TYPE)).contains(AutraFields.PIX);
    assertThat(operationData.text(AutraFields.PIX_ID)).contains(PIX_ID);
    assertThat(operationData.text(AutraFields.ORDER_ID)).contains(ORDER);
  }

  @Test
  void the_amount_an_event_carries_is_a_decimal_number_and_never_a_quoted_string() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.SETTLED);

    callbacks.schedule(scheduleOf(null), NOW);
    String transaction = theBodyQueued();

    callbacks.schedule(scheduleOf(AutraPixEvent.PAYOUT.eventType()), NOW);
    String payout = theBodyQueued();

    assertThat(transaction).contains("\"totalAmount\":99.90");
    assertThat(payout).contains("\"amount\":99.90");
  }

  @Test
  void a_callback_asked_to_reuse_an_event_id_sends_the_event_under_that_identifier() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    callbacks.schedule(
        new WebhookScheduleRequest(
            RUN,
            PIX_ID,
            null,
            "a58720d1-0a70-4012-b718-936e521f81b8",
            null,
            ObjectOrigin.CONTROL_API),
        NOW);

    assertThat(deliveries.of(AutraApi.AUTRA, RUN).get(0).eventId())
        .isEqualTo("a58720d1-0a70-4012-b718-936e521f81b8");
    assertThat(theBodyQueued()).contains("\"eventId\":\"a58720d1-0a70-4012-b718-936e521f81b8\"");
  }

  @Test
  void a_callback_asked_for_later_is_queued_for_later_rather_than_attempted_at_once() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    callbacks.schedule(
        new WebhookScheduleRequest(
            RUN, PIX_ID, null, null, Duration.ofMinutes(3), ObjectOrigin.CONTROL_API),
        NOW);

    WebhookDelivery queued = deliveries.of(AutraApi.AUTRA, RUN).get(0);
    assertThat(queued.attemptsMade()).isZero();
    assertThat(queued.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(3)));
  }

  @Test
  void a_callback_asked_for_now_is_attempted_before_the_request_comes_back() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    callbacks.schedule(scheduleOf(null), NOW);

    assertThat(deliveries.of(AutraApi.AUTRA, RUN).get(0).attemptsMade()).isEqualTo(1);
  }

  @Test
  void the_envelope_names_no_tenant_no_owner_and_no_counterparty_this_contract_has_no_value_for() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    callbacks.schedule(scheduleOf(null), NOW);

    AutraJson.JsonObject body = AutraJsonReader.objectIn(theBodyQueued()).orElseThrow();
    assertThat(body.has(AutraFields.TENANT_ID)).isFalse();
    assertThat(body.has(AutraFields.OWNER_ID)).isFalse();
    assertThat(body.has(AutraFields.COUNTERPARTY_NAME)).isFalse();
  }

  @Test
  void a_pix_started_without_an_order_carries_no_order_on_the_event_at_all() {
    aMerchantCallingBack();
    pixPayments.insertIfAbsent(
        new AutraPixPayment(
            RUN,
            PIX_ID,
            ACCOUNT,
            DOCUMENT,
            null,
            null,
            Amount.of("99.90"),
            "BRL",
            AutraStatus.AUTHORIZED,
            CreatePixCheckout.theQrCodeOf(PIX_ID),
            "A13CA0",
            "1A0B5369",
            NOW.plus(CreatePixCheckout.THE_QR_LIVES_FOR),
            ObjectOrigin.PROVIDER_API,
            NOW,
            NOW));

    callbacks.schedule(scheduleOf(null), NOW);

    assertThat(
            AutraJsonReader.objectIn(theBodyQueued())
                .orElseThrow()
                .object(AutraFields.OPERATION_DATA)
                .orElseThrow()
                .has(AutraFields.ORDER_ID))
        .isFalse();
  }

  @Test
  void the_payout_names_the_transaction_it_settles_by_the_slug_autra_correlates_them_on() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.SETTLED);

    callbacks.schedule(scheduleOf(AutraPixEvent.FINANCIAL_TRANSACTION.eventType()), NOW);
    String slug =
        AutraJsonReader.objectIn(theBodyQueued())
            .orElseThrow()
            .object(AutraFields.OPERATION_DATA)
            .orElseThrow()
            .object(AutraFields.PAYLOAD)
            .orElseThrow()
            .text(AutraFields.SLUG)
            .orElseThrow();

    callbacks.schedule(scheduleOf(AutraPixEvent.PAYOUT.eventType()), NOW);

    assertThat(
            AutraJsonReader.objectIn(theBodyQueued())
                .orElseThrow()
                .object(AutraFields.OPERATION_DATA)
                .orElseThrow()
                .object(AutraFields.PAYLOAD)
                .orElseThrow()
                .text(AutraFields.PAYOUT_ID))
        .contains(slug);
  }

  @Test
  void keeps_the_pix_event_row_readable_down_to_the_payers_document() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    callbacks.schedule(scheduleOf(null), NOW);

    assertThat(theDeliveryRows())
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.sanitizedBody())
                  .as(
                      "the routing half of (tenantId, userDocument) this build withholds in no"
                          + " direction, recorded as delivered in the clear")
                  .containsEntry("userDocument", List.of(DOCUMENT))
                  .containsEntry("eventType", List.of("acquiring.financial_transaction"))
                  .containsEntry("operationData.pixId", List.of(PIX_ID))
                  .containsEntry("operationData.orderId", List.of(ORDER))
                  .containsEntry("operationData.payload.currency", List.of("BRL"))
                  .containsEntry("operationData.payload.totalAmount", List.of("99.90"));
              assertThat(row.sanitizedBody().values())
                  .as(
                      "no value of this row reads as withheld. That AutraPixWebhook applies"
                          + " SENSITIVE_DELIVERY_FIELDS at all is read out of its compiled class"
                          + " by verifyEveryProviderWithholdsWhatItAnswers and by nothing here:"
                          + " the declaration is empty, so this row is the row the same class"
                          + " would write applying nothing")
                  .allSatisfy(values -> assertThat(values).doesNotContain(Redaction.REDACTED));
            });
  }

  @Test
  void every_event_carries_the_agent_autra_publishes_and_signs_nothing_without_a_tenant_secret() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    callbacks.schedule(scheduleOf(null), NOW);

    Map<String, List<String>> headers =
        deliveries.of(AutraApi.AUTRA, RUN).get(0).payload().providerHeaders();
    assertThat(headers.get(AutraPixWebhook.USER_AGENT))
        .containsExactly(AutraPixWebhook.THE_AGENT_AUTRA_SENDS);
    assertThat(headers).doesNotHave(aHeaderOfThisNameUnderAnySpelling(AutraPixWebhook.SIGNATURE));
  }

  @Test
  void an_event_of_an_account_that_configures_a_secret_is_signed_over_the_bytes_it_sends() {
    aMerchantCallingBack(A_SECRET);
    aPixThatIs(AutraStatus.AUTHORIZED);

    callbacks.schedule(scheduleOf(null), NOW);

    WebhookDelivery queued = deliveries.of(AutraApi.AUTRA, RUN).get(0);
    assertThat(queued.payload().providerHeaders().get(AutraPixWebhook.SIGNATURE))
        .containsExactly(AutraWebhookSecret.signatureOf(A_SECRET, queued.payload().body()));
    assertThat(queued.payload().providerHeaders().get(AutraPixWebhook.SIGNATURE).get(0))
        .startsWith(AutraWebhookSecret.PREFIX)
        .matches("sha256=[0-9a-f]{64}");
  }

  @Test
  void a_paid_pix_reads_back_as_money_taken_that_this_contract_offers_no_route_to_reverse() {
    aPixThatIs(AutraStatus.AUTHORIZED);

    AutraPixPayment pix = pixPayments.findById(RUN, PIX_ID).orElseThrow();

    assertThat(pix.ledgerAt(NOW).holds(OperationKind.CAPTURE)).isTrue();
    assertThat(pix.ledgerAt(NOW).holds(OperationKind.AUTHORIZATION)).isFalse();
  }

  @Test
  void a_pix_awaiting_its_payer_holds_no_money_at_all() {
    aPixThatIs(AutraStatus.PENDING);

    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().ledgerAt(NOW).operations())
        .isEmpty();
  }

  @Test
  void a_pix_that_expired_unpaid_holds_no_money_either() {
    aPixThatIs(AutraStatus.EXPIRED);

    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().ledgerAt(NOW).operations())
        .isEmpty();
  }

  @Test
  void a_status_autra_publishes_no_pix_flow_for_reads_back_as_unknown() {
    aPixThatIs("CHARGEBACK_OPENED");

    AutraPixPayment pix = pixPayments.findById(RUN, PIX_ID).orElseThrow();

    assertThat(pix.normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.UNKNOWN);
    assertThat(pix.whyItIsNoLongerWaitingAt(NOW)).contains("publishes nowhere");
  }

  @Test
  void a_settled_pix_reads_back_as_paid_because_settlement_follows_an_authorized_payment() {
    aPixThatIs(AutraStatus.SETTLED);

    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().normalizedStatusAt(NOW))
        .isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void the_two_terminal_states_are_the_two_the_state_machine_publishes_as_terminal() {
    assertThat(AutraStatus.isATerminalPixState(AutraStatus.SETTLED)).isTrue();
    assertThat(AutraStatus.isATerminalPixState(AutraStatus.EXPIRED)).isTrue();
    assertThat(AutraStatus.isATerminalPixState(AutraStatus.PENDING)).isFalse();
    assertThat(AutraStatus.isATerminalPixState(AutraStatus.AUTHORIZED)).isFalse();
    assertThat(AutraStatus.THE_PIX_STATE_MACHINE)
        .containsExactly(
            AutraStatus.PENDING, AutraStatus.AUTHORIZED, AutraStatus.SETTLED, AutraStatus.EXPIRED);
  }

  @Test
  void the_event_names_this_module_renders_are_the_two_a_pix_of_this_contract_reaches() {
    assertThat(AutraPixEvent.theEventTypesItRenders())
        .containsExactly("acquiring.financial_transaction", "acquiring.payout");
    assertThat(AutraPixEvent.named("acquiring.payout")).isEqualTo(AutraPixEvent.PAYOUT);
    assertThat(AutraPixEvent.named("acquiring.financial_transaction"))
        .isEqualTo(AutraPixEvent.FINANCIAL_TRANSACTION);
    assertThatThrownBy(() -> AutraPixEvent.named("acquiring.payout "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(AutraPixEvent.NO_SUCH_EVENT);
  }

  @Test
  void the_retry_plan_this_provider_declares_is_the_sixteen_attempts_autra_publishes() {
    assertThat(AutraSettings.thePublishedPlan())
        .isEqualTo(
            "PT0S,PT1M,PT1M,PT1M,PT1M,PT1M,PT1M,PT1M,PT1M,PT1M,PT1M,"
                + "PT10M,PT10M,PT10M,PT10M,PT10M");
    assertThat(AutraSettings.thePublishedPlan().split(",", -1))
        .hasSize(AutraSettings.PUBLISHED_ATTEMPTS);
  }

  @Test
  void a_callback_secret_of_exactly_the_length_autra_publishes_is_taken_and_a_shorter_one_is_not() {
    assertThat(AutraWebhookSecret.of("accounts[0].configuration.", "a".repeat(32)))
        .isEqualTo("a".repeat(32));
    String oneShortOfWhatAutraPublishes = "a".repeat(31);

    assertThatThrownBy(
            () -> AutraWebhookSecret.of("accounts[0].configuration.", oneShortOfWhatAutraPublishes))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(AutraWebhookSecret.IT_IS_AT_LEAST_THIRTY_TWO_CHARACTERS);
  }

  @Test
  void a_blank_callback_secret_names_none_rather_than_one_this_provider_would_sign_with() {
    assertThat(AutraWebhookSecret.of("accounts[0].configuration.", null)).isNull();
    assertThat(AutraWebhookSecret.of("accounts[0].configuration.", "   ")).isNull();
  }

  @Test
  void only_a_two_hundred_class_answer_counts_a_delivery_of_this_provider_as_made() {
    assertThat(AutraCallbackAcknowledgement.wasDelivered(200)).isTrue();
    assertThat(AutraCallbackAcknowledgement.wasDelivered(299)).isTrue();
    assertThat(AutraCallbackAcknowledgement.wasDelivered(199)).isFalse();
    assertThat(AutraCallbackAcknowledgement.wasDelivered(300)).isFalse();
  }

  private HostedCompletionOutcome complete(PayerOutcome outcome, Instant at) {
    return payerStep.complete(
        new HostedCompletionRequest(RUN, PIX_ID, outcome, null, ObjectOrigin.CONTROL_API), at);
  }

  private static WebhookScheduleRequest scheduleOf(String eventType) {
    return new WebhookScheduleRequest(RUN, PIX_ID, eventType, null, null, ObjectOrigin.CONTROL_API);
  }

  private List<AuditRecord> theDeliveryRows() {
    List<AuditRecord> rows =
        audit.of(AutraApi.AUTRA, RUN).stream()
            .filter(record -> record.direction() == AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT)
            .toList();
    assertThat(rows)
        .as("a run that kept no delivery row would let every assertion on one pass")
        .isNotEmpty();
    return rows;
  }

  private String theBodyQueued() {
    List<WebhookDelivery> queued = deliveries.of(AutraApi.AUTRA, RUN);
    return new String(queued.get(0).payload().body(), StandardCharsets.UTF_8);
  }

  private void aMerchantCallingBack() {
    aMerchantCallingBack(null);
  }

  private void aMerchantCallingBack(String secret) {
    Map<String, String> configuration =
        secret == null ? Map.of() : Map.of(AutraWebhookSecret.SETTING, secret);
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, configuration));
    accounts.callingBack(RUN, ACCOUNT, TARGET);
  }

  private void aPixThatIs(String nativeStatus) {
    pixPayments.insertIfAbsent(
        new AutraPixPayment(
            RUN,
            PIX_ID,
            ACCOUNT,
            DOCUMENT,
            ORDER,
            "Pedido #1234",
            Amount.of("99.90"),
            "BRL",
            nativeStatus,
            CreatePixCheckout.theQrCodeOf(PIX_ID),
            "A13CA0",
            "1A0B5369",
            NOW.plus(CreatePixCheckout.THE_QR_LIVES_FOR),
            ObjectOrigin.PROVIDER_API,
            NOW,
            NOW));
  }
}
