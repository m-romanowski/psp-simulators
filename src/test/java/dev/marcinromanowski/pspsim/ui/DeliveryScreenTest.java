package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.TransportFailure;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookAttempt;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DeliveryScreenTest {

  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final RetryPlan PLAN =
      new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5)));
  private static final DeliveryScreen.Attempts NO_ATTEMPTS = deliveryId -> List.of();
  private static final String PAYER_NAME = "A Payer";
  private static final String TARGET = "http://localhost:9/callback";
  private static final String A_SECOND_TARGET = "http://localhost:9/second";
  private static final List<WebhookTarget> BOTH_TARGETS =
      List.of(
          new WebhookTarget("account-1", TARGET), new WebhookTarget("account-2", A_SECOND_TARGET));

  @Test
  void a_callback_the_merchant_refused_and_that_will_be_tried_again_is_reported_as_failing() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.PENDING, 1, NOW.plusSeconds(5))),
            NOW,
            null,
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(queue.failing())
        .extracting(DeliveryScreen.Row::deliveryId)
        .containsExactly("whjob_1");
    assertThat(queue.pending()).isEqualTo(1);
  }

  @Test
  void a_callback_the_merchant_took_first_time_is_not_reported_as_failing() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.DELIVERED, 1, null)),
            NOW,
            null,
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(queue.failing()).isEmpty();
    assertThat(queue.delivered()).isEqualTo(1);
  }

  @Test
  void a_callback_that_ran_out_of_attempts_is_reported_as_failing_and_not_as_pending() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.EXHAUSTED, 3, null)),
            NOW,
            null,
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(queue.exhausted()).isEqualTo(1);
    assertThat(queue.pending()).isZero();
    assertThat(queue.failing()).hasSize(1);
  }

  @Test
  void a_callback_a_rule_dropped_is_counted_apart_and_is_neither_pending_nor_failing() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.DROPPED, 0, null)),
            NOW,
            null,
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(queue.dropped()).isEqualTo(1);
    assertThat(queue.pending()).isZero();
    assertThat(queue.exhausted()).isZero();
    assertThat(queue.due()).isZero();
    assertThat(queue.failing()).isEmpty();
    assertThat(queue.rows())
        .singleElement()
        .extracting(DeliveryScreen.Row::state, DeliveryScreen.Row::dropped)
        .containsExactly(new StatusBadge("DROPPED", StatusBadge.Tone.NEUTRAL), true);
  }

  @Test
  void a_callback_whose_next_attempt_is_still_ahead_of_this_runs_clock_is_pending_but_not_due() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.PENDING, 0, NOW.plusSeconds(30))),
            NOW,
            null,
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(queue.pending()).isEqualTo(1);
    assertThat(queue.due()).isZero();
    assertThat(queue.rows().get(0).due()).isFalse();
    assertThat(queue.failing()).isEmpty();
  }

  @Test
  void a_callback_whose_moment_this_runs_clock_has_reached_is_due() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.PENDING, 0, NOW)),
            NOW,
            null,
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(queue.due()).isEqualTo(1);
  }

  @Test
  void only_the_delivery_a_tester_opened_costs_a_read_of_its_attempt_ledger() {
    List<String> read = new ArrayList<>();
    DeliveryScreen.Attempts recording =
        deliveryId -> {
          read.add(deliveryId);
          return List.of(anAttempt(1, Acknowledgement.REFUSED));
        };

    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(
                aDelivery("whjob_1", DeliveryState.PENDING, 1, NOW),
                aDelivery("whjob_2", DeliveryState.PENDING, 1, NOW)),
            NOW,
            "whjob_2",
            false,
            recording,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(read).containsExactly("whjob_2");
    assertThat(queue.rows().get(0).attempts()).isEmpty();
    assertThat(queue.rows().get(1).attempts()).hasSize(1);
  }

  @Test
  void only_the_delivery_a_tester_opened_puts_the_headers_it_signs_with_on_the_page() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(
                aDelivery("whjob_1", DeliveryState.PENDING, 0, NOW),
                aDelivery("whjob_2", DeliveryState.PENDING, 0, NOW)),
            NOW,
            "whjob_2",
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(headerNamesOf(queue.rows().get(0))).isEmpty();
    assertThat(headerNamesOf(queue.rows().get(1))).contains("X-Grow-Signature");
  }

  @Test
  void an_opened_payload_keeps_the_payers_name_off_the_page_until_it_is_asked_for() {
    DeliveryScreen.Queue withheld =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.PENDING, 0, NOW)),
            NOW,
            "whjob_1",
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());
    DeliveryScreen.Queue shown =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.PENDING, 0, NOW)),
            NOW,
            "whjob_1",
            true,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(payloadValuesOf(withheld)).doesNotContain(PAYER_NAME);
    assertThat(payloadValuesOf(shown)).contains(PAYER_NAME);
    assertThat(withheld.valuesShown()).isFalse();
  }

  @Test
  void a_refused_attempt_reports_the_status_the_merchant_answered_with() {
    DeliveryScreen.Attempts refused = deliveryId -> List.of(anAttempt(1, Acknowledgement.REFUSED));

    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.PENDING, 1, NOW)),
            NOW,
            "whjob_1",
            false,
            refused,
            List.of(),
            DeliveryForm.noneOffered());

    DeliveryScreen.Attempt attempt = queue.rows().get(0).attempts().get(0);
    assertThat(attempt.verdict()).isEqualTo(new StatusBadge("REFUSED", StatusBadge.Tone.FAILURE));
    assertThat(attempt.httpStatus()).isEqualTo(500);
    assertThat(attempt.transportFailure()).isNull();
  }

  @Test
  void a_delivery_still_owed_a_separate_call_is_counted_apart_from_one_that_is_done() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(anAcknowledgementOwed("whjob_1")),
            NOW,
            null,
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(queue.awaitingCall()).isEqualTo(1);
    assertThat(queue.rows().get(0).acknowledgementOwed()).isTrue();
  }

  @Test
  void an_opened_delivery_offers_the_targets_this_run_configures_other_than_its_own() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.EXHAUSTED, 1, null)),
            NOW,
            "whjob_1",
            false,
            NO_ATTEMPTS,
            BOTH_TARGETS,
            DeliveryForm.noneOffered());

    DeliveryScreen.Row row = queue.rows().get(0);
    assertThat(row.offersAnotherTarget()).isTrue();
    assertThat(row.otherTargets())
        .containsExactly(new DeliveryForm.Target("account-2", A_SECOND_TARGET));
  }

  @Test
  void a_run_whose_only_configured_target_is_the_delivery_s_own_offers_nowhere_else() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.EXHAUSTED, 1, null)),
            NOW,
            "whjob_1",
            false,
            NO_ATTEMPTS,
            List.of(new WebhookTarget("account-1", TARGET)),
            DeliveryForm.noneOffered());

    assertThat(queue.rows().get(0).offersAnotherTarget()).isFalse();
  }

  @Test
  void a_delivery_a_tester_has_not_opened_offers_no_target_to_retarget_it_to() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.EXHAUSTED, 1, null)),
            NOW,
            null,
            false,
            NO_ATTEMPTS,
            BOTH_TARGETS,
            DeliveryForm.noneOffered());

    assertThat(queue.rows().get(0).otherTargets()).isEmpty();
  }

  @Test
  void an_attempt_puts_the_signature_it_was_sent_under_on_the_page_as_a_field_that_was_sent() {
    DeliveryScreen.Queue queue = withAttempts(anAttemptTo(1, TARGET));

    assertThat(queue.rows().get(0).attempts().get(0).sentHeaders())
        .extracting(Disclosure.Field::name)
        .containsExactlyInAnyOrder("Content-Type", "Stripe-Signature");
  }

  @Test
  void an_attempt_that_recorded_the_wire_says_so_and_names_the_content_type_it_posted_under() {
    DeliveryScreen.Queue queue = withAttempts(anAttemptTo(1, TARGET));

    DeliveryScreen.Attempt attempt = queue.rows().get(0).attempts().get(0);
    assertThat(attempt.theWireWasRecorded()).isTrue();
    assertThat(attempt.sentHeaders()).extracting(Disclosure.Field::name).contains("Content-Type");
  }

  @Test
  void an_attempt_made_before_the_wire_was_recorded_shows_no_field_and_does_not_claim_none_went() {
    DeliveryScreen.Queue queue = withAttempts(anAttemptThatRecordedNoWire());

    DeliveryScreen.Attempt attempt = queue.rows().get(0).attempts().get(0);
    assertThat(attempt.theWireWasRecorded()).isFalse();
    assertThat(attempt.sentHeaders()).isEmpty();
  }

  @Test
  void the_value_of_an_attempt_s_signature_stays_hidden_until_a_tester_asks_for_it() {
    DeliveryScreen.Queue queue = withAttempts(anAttemptTo(1, TARGET));

    assertThat(queue.rows().get(0).attempts().get(0).sentHeaders())
        .extracting(Disclosure.Field::visibility, Disclosure.Field::values)
        .containsOnly(tuple(Disclosure.Visibility.WITHHELD, List.of()));
  }

  @Test
  void a_tester_who_asks_for_values_reads_the_signature_that_attempt_actually_carried() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(aDelivery("whjob_1", DeliveryState.PENDING, 1, NOW)),
            NOW,
            "whjob_1",
            true,
            deliveryId -> List.of(anAttemptTo(1, TARGET)),
            BOTH_TARGETS,
            DeliveryForm.noneOffered());

    assertThat(queue.rows().get(0).attempts().get(0).sentHeaders())
        .extracting(Disclosure.Field::values)
        .containsExactlyInAnyOrder(List.of("application/json"), List.of("t=1,v1=abc"));
  }

  @Test
  void an_attempt_names_the_target_it_was_posted_to() {
    DeliveryScreen.Queue queue = withAttempts(anAttemptTo(1, TARGET));

    DeliveryScreen.Attempt attempt = queue.rows().get(0).attempts().get(0);
    assertThat(attempt.targetUrl()).isEqualTo(TARGET);
    assertThat(attempt.wentElsewhere()).isFalse();
  }

  @Test
  void an_attempt_posted_elsewhere_is_marked_apart_from_the_target_it_was_scheduled_for() {
    DeliveryScreen.Queue queue = withAttempts(anAttemptTo(1, A_SECOND_TARGET));

    DeliveryScreen.Attempt attempt = queue.rows().get(0).attempts().get(0);
    assertThat(attempt.targetUrl()).isEqualTo(A_SECOND_TARGET);
    assertThat(attempt.wentElsewhere()).isTrue();
  }

  @Test
  void a_delivery_with_no_target_offers_no_attempt_and_marks_no_attempt_as_gone_elsewhere() {
    WebhookDelivery noTarget = notSent("whjob_1", "none to post to");
    WebhookDelivery withNoTarget =
        new WebhookDelivery(
            noTarget.deliveryId(),
            noTarget.provider(),
            noTarget.testRunId(),
            noTarget.merchantId(),
            noTarget.eventId(),
            noTarget.eventType(),
            noTarget.providerObjectId(),
            null,
            noTarget.payload(),
            noTarget.retryPlan(),
            noTarget.state(),
            noTarget.acknowledgementOwed(),
            noTarget.attemptsMade(),
            noTarget.nextAttemptAt(),
            noTarget.createdAt(),
            noTarget.updatedAt(),
            noTarget.shapedBy(),
            noTarget.notSentBecause());

    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(withNoTarget),
            NOW,
            "whjob_1",
            false,
            deliveryId -> List.of(anAttemptTo(1, TARGET)),
            BOTH_TARGETS,
            DeliveryForm.noneOffered());

    DeliveryScreen.Row row = queue.rows().get(0);
    assertThat(row.canBeAttempted()).isFalse();
    assertThat(row.targetUrl()).isNull();
    assertThat(row.attempts().get(0).wentElsewhere()).isFalse();
  }

  @Test
  void a_delivery_with_a_target_offers_an_attempt() {
    assertThat(withAttempts().rows().get(0).canBeAttempted()).isTrue();
  }

  private static DeliveryScreen.Queue withAttempts(WebhookAttempt... made) {
    return DeliveryScreen.queueOf(
        List.of(aDelivery("whjob_1", DeliveryState.PENDING, 1, NOW)),
        NOW,
        "whjob_1",
        false,
        deliveryId -> List.of(made),
        BOTH_TARGETS,
        DeliveryForm.noneOffered());
  }

  private static WebhookAttempt anAttemptTo(int number, String targetUrl) {
    return new WebhookAttempt(
        "whjob_1",
        number,
        NOW,
        targetUrl,
        Map.of(
            "Content-Type", List.of("application/json"), "Stripe-Signature", List.of("t=1,v1=abc")),
        Acknowledgement.ACKNOWLEDGED,
        200,
        (TransportFailure) null,
        "OK");
  }

  private static WebhookAttempt anAttemptThatRecordedNoWire() {
    return new WebhookAttempt(
        "whjob_1",
        1,
        NOW,
        TARGET,
        (Map<String, List<String>>) null,
        Acknowledgement.ACKNOWLEDGED,
        200,
        (TransportFailure) null,
        "OK");
  }

  private static List<String> headerNamesOf(DeliveryScreen.Row row) {
    return row.headers().stream().map(Disclosure.Field::name).toList();
  }

  private static List<String> payloadValuesOf(DeliveryScreen.Queue queue) {
    return queue.rows().stream()
        .flatMap(row -> row.body().stream())
        .flatMap(field -> field.values().stream())
        .toList();
  }

  @Test
  void a_callback_the_simulator_would_not_send_is_counted_apart_and_says_on_the_row_why() {
    DeliveryScreen.Queue queue =
        DeliveryScreen.queueOf(
            List.of(notSent("whjob_1", "the account it belongs to is no longer configured")),
            NOW,
            null,
            false,
            NO_ATTEMPTS,
            List.of(),
            DeliveryForm.noneOffered());

    assertThat(queue.undeliverable()).isEqualTo(1);
    assertThat(queue.pending()).isZero();
    assertThat(queue.exhausted()).isZero();
    assertThat(queue.dropped()).isZero();
    assertThat(queue.due()).isZero();
    assertThat(queue.rows())
        .singleElement()
        .extracting(DeliveryScreen.Row::state, DeliveryScreen.Row::notSentBecause)
        .containsExactly(
            new StatusBadge("UNDELIVERABLE", StatusBadge.Tone.FAILURE),
            "the account it belongs to is no longer configured");
  }

  private static WebhookDelivery notSent(String deliveryId, String reason) {
    WebhookDelivery attempted = aDelivery(deliveryId, DeliveryState.UNDELIVERABLE, 1, null);
    return new WebhookDelivery(
        attempted.deliveryId(),
        attempted.provider(),
        attempted.testRunId(),
        attempted.merchantId(),
        attempted.eventId(),
        attempted.eventType(),
        attempted.providerObjectId(),
        attempted.targetUrl(),
        attempted.payload(),
        attempted.retryPlan(),
        attempted.state(),
        attempted.acknowledgementOwed(),
        attempted.attemptsMade(),
        attempted.nextAttemptAt(),
        attempted.createdAt(),
        attempted.updatedAt(),
        attempted.shapedBy(),
        reason);
  }

  private static WebhookAttempt anAttempt(int number, Acknowledgement verdict) {
    return new WebhookAttempt(
        "whjob_1",
        number,
        NOW,
        TARGET,
        Map.of(
            "Content-Type",
            List.of("application/x-www-form-urlencoded"),
            "X-Grow-Signature",
            List.of("sig")),
        verdict,
        500,
        (TransportFailure) null,
        "merchant said no");
  }

  private static WebhookDelivery aDelivery(
      String deliveryId, DeliveryState state, int attemptsMade, @Nullable Instant nextAttemptAt) {
    return delivery(deliveryId, state, attemptsMade, nextAttemptAt, false);
  }

  private static WebhookDelivery anAcknowledgementOwed(String deliveryId) {
    return delivery(deliveryId, DeliveryState.PENDING, 1, NOW.plusSeconds(1), true);
  }

  private static WebhookDelivery delivery(
      String deliveryId,
      DeliveryState state,
      int attemptsMade,
      @Nullable Instant nextAttemptAt,
      boolean acknowledgementOwed) {
    return new WebhookDelivery(
        deliveryId,
        ProviderId.of("grow"),
        TestRunId.of("ui-webhooks"),
        "merchant-1",
        "evt_1",
        "process.completed",
        "proc_1",
        TARGET,
        aPayload(),
        PLAN,
        state,
        acknowledgementOwed,
        attemptsMade,
        nextAttemptAt,
        NOW,
        NOW,
        null,
        null);
  }

  private static WebhookPayload aPayload() {
    return new WebhookPayload(
        "application/x-www-form-urlencoded",
        Map.of("X-Grow-Signature", List.of("sig")),
        "data%5BfullName%5D=A+Payer".getBytes(StandardCharsets.UTF_8),
        Map.of(
            "data[fullName]",
            List.of(PAYER_NAME),
            "data[processToken]",
            List.of(Redaction.REDACTED)));
  }
}
