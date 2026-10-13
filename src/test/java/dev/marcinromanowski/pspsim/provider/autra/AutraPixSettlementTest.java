package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUp;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpNotOfferedException;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class AutraPixSettlementTest {

  private static final TestRunId RUN = TestRunId.of("autra-pix-settlement");
  private static final Instant PAID_AT = Instant.parse("2026-09-22T10:00:00Z");
  private static final Instant NOW = Instant.parse("2026-09-22T10:01:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String PIX_ID = "cdb445694be7486e830f720658f9a1a1";
  private static final String TARGET = "https://merchant.example/autra/acquiring";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.PixPayments pixPayments = new AutraDoubles.PixPayments();
  private final AutraDoubles.Accounts accounts = new AutraDoubles.Accounts();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();

  private final WebhookDispatcher dispatcher =
      new WebhookDispatcher(
          deliveries,
          (url, payload) -> new MerchantResponse.Answered(200, ""),
          new AutraDoubles.Audit(),
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

  private final SettlePixPayment settlement =
      new SettlePixPayment(pixPayments, callbacks, new RecordedUnitsOfWork());

  @Test
  void a_pix_a_payer_paid_offers_the_settlement_in_autras_own_words_and_says_what_it_moves() {
    aPixThatIs(AutraStatus.AUTHORIZED);

    List<FollowUp> offered = settlement.offeredOn(RUN, PIX_ID, NOW);

    assertThat(offered).containsExactly(SettlePixPayment.THE_SETTLEMENT);
    assertThat(offered.get(0).name()).isEqualTo("settle");
    assertThat(offered.get(0).asTheProviderNamesIt()).isEqualTo("settle (payout)");
    assertThat(offered.get(0).whatItMoves()).contains("AUTHORIZED").contains("SETTLED");
  }

  @Test
  void a_pix_waiting_for_its_payer_one_that_expired_and_one_that_settled_offer_nothing() {
    for (String status : List.of(AutraStatus.PENDING, AutraStatus.EXPIRED, AutraStatus.SETTLED)) {
      AutraDoubles.PixPayments held = new AutraDoubles.PixPayments();
      held.insertIfAbsent(aPix(status));

      assertThat(
              new SettlePixPayment(held, callbacks, new RecordedUnitsOfWork())
                  .offeredOn(RUN, PIX_ID, NOW))
          .as(status)
          .isEmpty();
    }
  }

  @Test
  void a_qr_the_clock_has_passed_unpaid_offers_no_settlement_although_its_row_says_pending() {
    aPixThatIs(AutraStatus.PENDING);

    assertThat(settlement.offeredOn(RUN, PIX_ID, PAID_AT.plus(CreatePixCheckout.THE_QR_LIVES_FOR)))
        .isEmpty();
  }

  @Test
  void an_object_no_pix_of_this_run_answers_to_offers_no_settlement() {
    assertThat(settlement.offeredOn(RUN, PIX_ID, NOW)).isEmpty();
  }

  @Test
  void settling_moves_the_stored_pix_from_authorized_to_settled_and_it_still_reads_as_paid() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    settle();

    AutraPixPayment settled = pixPayments.findById(RUN, PIX_ID).orElseThrow();
    assertThat(settled.nativeStatus()).isEqualTo(AutraStatus.SETTLED);
    assertThat(settled.statusAt(NOW)).isEqualTo(AutraStatus.SETTLED);
    assertThat(settled.normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(settled.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void settling_queues_exactly_one_payout_whose_body_reports_the_status_the_pix_now_holds() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    List<ScheduledWebhook> jobs = settle();

    List<WebhookDelivery> queued = deliveries.of(AutraApi.AUTRA, RUN);
    assertThat(queued).hasSize(1);
    assertThat(jobs)
        .extracting(ScheduledWebhook::webhookJobId)
        .containsExactly(queued.get(0).deliveryId());
    assertThat(queued.get(0).eventType()).isEqualTo(AutraPixEvent.PAYOUT.eventType());
    assertThat(queued.get(0).providerObjectId()).isEqualTo(PIX_ID);
    assertThat(thePayoutStatusIn(queued.get(0))).contains(AutraStatus.SETTLED);
  }

  @Test
  void a_settlement_moves_the_pix_and_queues_its_payout_inside_one_kept_unit_of_work() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);
    List<String> seen = new ArrayList<>();
    SettlePixPayment observed =
        new SettlePixPayment(
            pixPayments,
            callbacks,
            new UnitOfWork() {
              @Override
              public <T> T keepingOnly(Predicate<? super T> kept, Supplier<T> work) {
                seen.add("opened on " + theStatusAndTheQueue());
                T done = work.get();
                seen.add("kept " + kept.test(done) + " on " + theStatusAndTheQueue());
                return done;
              }
            });

    observed.perform(FollowUpRequest.of(RUN, PIX_ID, "settle", ObjectOrigin.CONTROL_API), NOW);

    assertThat(seen)
        .containsExactly(
            "opened on AUTHORIZED with 0 queued", "kept true on SETTLED with 1 queued");
  }

  private String theStatusAndTheQueue() {
    return pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus()
        + " with "
        + deliveries.of(AutraApi.AUTRA, RUN).size()
        + " queued";
  }

  @Test
  void a_settlement_of_a_merchant_this_run_configures_no_target_for_moves_the_pix_and_sends_none() {
    aPixThatIs(AutraStatus.AUTHORIZED);

    assertThat(settle()).isEmpty();

    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.SETTLED);
  }

  @Test
  void a_second_settlement_is_refused_as_already_settled_and_queues_no_second_payout() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);
    settle();

    assertThatThrownBy(this::settle)
        .isInstanceOf(FollowUpNotOfferedException.class)
        .hasMessageContaining("already settled")
        .hasMessageContaining("acquiring.payout");
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).hasSize(1);
  }

  @Test
  void a_settlement_of_a_pix_still_waiting_for_its_payer_is_refused_and_moves_nothing() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);

    assertThatThrownBy(this::settle)
        .isInstanceOf(FollowUpNotOfferedException.class)
        .hasMessageContaining("still waiting for its payer");
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.PENDING);
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_settlement_of_a_pix_that_expired_unpaid_is_refused_and_moves_nothing() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.EXPIRED);

    assertThatThrownBy(this::settle)
        .isInstanceOf(FollowUpNotOfferedException.class)
        .hasMessageContaining("expired unpaid");
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.EXPIRED);
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_settlement_of_a_qr_the_clock_has_passed_is_refused_in_the_words_of_an_expired_qr() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.PENDING);
    FollowUpRequest late = FollowUpRequest.of(RUN, PIX_ID, "settle", ObjectOrigin.CONTROL_API);
    Instant past = PAID_AT.plus(CreatePixCheckout.THE_QR_LIVES_FOR);

    assertThatThrownBy(() -> settlement.perform(late, past))
        .isInstanceOf(FollowUpNotOfferedException.class)
        .hasMessageContaining("expired unpaid");
  }

  @Test
  void a_pix_holding_a_status_autra_publishes_nowhere_is_refused_saying_what_it_holds() {
    aPixThatIs("CHARGEBACK_OPENED");

    assertThatThrownBy(this::settle)
        .isInstanceOf(FollowUpNotOfferedException.class)
        .hasMessageContaining("CHARGEBACK_OPENED")
        .hasMessageContaining("publishes nowhere");
  }

  @Test
  void a_follow_up_autra_offers_under_no_such_name_is_refused_naming_the_one_it_does() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);
    FollowUpRequest refund = FollowUpRequest.of(RUN, PIX_ID, "refund", ObjectOrigin.CONTROL_API);

    assertThatThrownBy(() -> settlement.perform(refund, NOW))
        .isInstanceOf(FollowUpNotOfferedException.class)
        .hasMessage(SettlePixPayment.THE_ONLY_FOLLOW_UP_IS_THE_SETTLEMENT);
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.AUTHORIZED);
  }

  @Test
  void a_settlement_of_an_id_no_pix_of_this_run_answers_to_is_answered_as_an_unknown_object() {
    assertThatThrownBy(this::settle)
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessage(SettlePixPayment.THE_RUN_HOLDS_NO_SUCH_PIX);
  }

  @Test
  void a_pix_removed_between_the_read_and_the_write_is_answered_as_an_unknown_object() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);
    pixPayments.removedByAnotherRequestFirst(PIX_ID);

    assertThatThrownBy(this::settle)
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessage(SettlePixPayment.THE_RUN_HOLDS_NO_SUCH_PIX);
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_pix_another_request_settled_between_the_read_and_the_write_is_refused_on_what_it_holds() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);
    pixPayments.movedByAnotherRequestFirst(PIX_ID, AutraStatus.SETTLED);

    assertThatThrownBy(this::settle)
        .isInstanceOf(FollowUpNotOfferedException.class)
        .hasMessageContaining("already settled");
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_payout_sent_on_its_own_for_an_authorized_pix_is_refused_pointing_at_the_settlement() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);
    WebhookScheduleRequest payout = aSendOf(AutraPixEvent.PAYOUT.eventType());

    assertThatThrownBy(() -> callbacks.schedule(payout, NOW))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(AutraCallbacks.A_PAYOUT_FOLLOWS_A_SETTLEMENT);
    assertThat(AutraCallbacks.A_PAYOUT_FOLLOWS_A_SETTLEMENT).contains("follow-ups/settle");
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.AUTHORIZED);
    assertThat(deliveries.of(AutraApi.AUTRA, RUN)).isEmpty();
  }

  @Test
  void a_payout_sent_on_its_own_for_a_settled_pix_is_resent_reporting_the_settlement() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.SETTLED);

    List<ScheduledWebhook> resent = callbacks.schedule(aSendOf("acquiring.payout"), NOW);

    List<WebhookDelivery> queued = deliveries.of(AutraApi.AUTRA, RUN);
    assertThat(resent).hasSize(1);
    assertThat(queued).hasSize(1);
    assertThat(queued.get(0).eventType()).isEqualTo(AutraPixEvent.PAYOUT.eventType());
    assertThat(thePayoutStatusIn(queued.get(0))).contains(AutraStatus.SETTLED);
    assertThat(pixPayments.findById(RUN, PIX_ID).orElseThrow().nativeStatus())
        .isEqualTo(AutraStatus.SETTLED);
  }

  @Test
  void a_payout_sent_on_its_own_for_a_pix_that_expired_unpaid_is_still_refused_as_before() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.EXPIRED);
    WebhookScheduleRequest payout = aSendOf(AutraPixEvent.PAYOUT.eventType());

    assertThatThrownBy(() -> callbacks.schedule(payout, NOW))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessage(AutraCallbacks.A_PAYOUT_FOLLOWS_A_PAYMENT);
  }

  @Test
  void the_financial_transaction_of_an_authorized_pix_is_still_sent_on_its_own() {
    aMerchantCallingBack();
    aPixThatIs(AutraStatus.AUTHORIZED);

    assertThat(callbacks.schedule(aSendOf(null), NOW)).hasSize(1);
  }

  @Test
  void the_payout_body_reports_the_status_its_pix_holds_rather_than_a_status_written_into_it() {
    for (String status : List.of(AutraStatus.AUTHORIZED, AutraStatus.SETTLED)) {
      AutraJson.JsonObject body =
          AutraPixWebhook.bodyOf(AutraPixEvent.PAYOUT, aPix(status), "event-1", NOW);

      assertThat(
              body.object(AutraFields.OPERATION_DATA)
                  .orElseThrow()
                  .object(AutraFields.PAYLOAD)
                  .orElseThrow()
                  .text(AutraFields.STATUS))
          .as(status)
          .contains(status);
    }
  }

  private List<ScheduledWebhook> settle() {
    return settlement.perform(
        FollowUpRequest.of(RUN, PIX_ID, "settle", ObjectOrigin.CONTROL_API), NOW);
  }

  private Optional<String> thePayoutStatusIn(WebhookDelivery delivery) {
    return AutraJsonReader.objectIn(new String(delivery.payload().body(), StandardCharsets.UTF_8))
        .orElseThrow()
        .object(AutraFields.OPERATION_DATA)
        .orElseThrow()
        .object(AutraFields.PAYLOAD)
        .orElseThrow()
        .text(AutraFields.STATUS);
  }

  private static WebhookScheduleRequest aSendOf(String eventType) {
    return new WebhookScheduleRequest(RUN, PIX_ID, eventType, null, null, ObjectOrigin.CONTROL_API);
  }

  private void aMerchantCallingBack() {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, Map.of()));
    accounts.callingBack(RUN, ACCOUNT, TARGET);
  }

  private void aPixThatIs(String nativeStatus) {
    pixPayments.insertIfAbsent(aPix(nativeStatus));
  }

  private static AutraPixPayment aPix(String nativeStatus) {
    return new AutraPixPayment(
        RUN,
        PIX_ID,
        ACCOUNT,
        DOCUMENT,
        "pedido-abc-123",
        "Pedido #1234",
        Amount.of("99.90"),
        "BRL",
        nativeStatus,
        CreatePixCheckout.theQrCodeOf(PIX_ID),
        "A13CA0",
        "1A0B5369",
        PAID_AT.plus(CreatePixCheckout.THE_QR_LIVES_FOR),
        ObjectOrigin.PROVIDER_API,
        PAID_AT,
        PAID_AT);
  }
}
