package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeliveryFormTest {

  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final TestRunId RUN = TestRunId.of("ui-webhook-lab");
  private static final String RUNS_OWN_TARGET = "https://merchant.example/hook";
  private static final String A_SECOND_TARGET = "https://merchant.example/second";
  private static final String A_TARGET_NO_TEST_DECLARED = "https://attacker.example/collect";

  private static final List<WebhookTarget> TWO_TARGETS =
      List.of(
          new WebhookTarget("account-1", RUNS_OWN_TARGET),
          new WebhookTarget("account-2", A_SECOND_TARGET));

  @Test
  void the_event_type_a_refused_send_was_given_comes_back_under_this_form_s_own_name() {
    assertThat(
            DeliveryForm.theEventTypeTypedIn("charge.settled")
                .valueOf(DeliveryForm.THE_EVENT_TYPE_A_SEND_NAMES))
        .as(
            "a screen drawing a start form reads the field names a provider declares, so this"
                + " form names its own field apart from them or it fills a box nobody typed in")
        .isEqualTo("charge.settled");
    assertThat(DeliveryForm.theEventTypeTypedIn("charge.settled").valueOf("eventType")).isEmpty();
  }

  @Test
  void a_send_that_named_no_event_type_carries_an_empty_one_rather_than_the_word_null() {
    assertThat(
            DeliveryForm.theEventTypeTypedIn(null)
                .valueOf(DeliveryForm.THE_EVENT_TYPE_A_SEND_NAMES))
        .isEmpty();
  }

  @Test
  void a_target_the_run_configures_is_the_one_attempt_is_posted_to() {
    DeliveryForm.Posting posting = DeliveryForm.postingTo(TWO_TARGETS, A_SECOND_TARGET);

    assertThat(posting).isEqualTo(new DeliveryForm.Posting.ToAConfiguredTarget(A_SECOND_TARGET));
  }

  @Test
  void a_url_no_test_run_configured_is_refused_rather_than_posted_to() {
    DeliveryForm.Posting posting = DeliveryForm.postingTo(TWO_TARGETS, A_TARGET_NO_TEST_DECLARED);

    assertThat(posting)
        .isEqualTo(
            new DeliveryForm.Posting.Refused(
                DeliveryForm.THE_TARGET_IS_NOT_CONFIGURED_FOR_THIS_RUN));
  }

  @Test
  void a_refusal_names_the_rule_and_never_the_url_it_refused() {
    DeliveryForm.Posting.Refused refused =
        (DeliveryForm.Posting.Refused)
            DeliveryForm.postingTo(TWO_TARGETS, A_TARGET_NO_TEST_DECLARED);

    assertThat(refused.message()).doesNotContain(A_TARGET_NO_TEST_DECLARED);
    assertThat(refused.message()).contains("targetUrl");
  }

  @Test
  void a_run_that_configures_no_target_is_told_that_rather_than_that_its_choice_was_wrong() {
    DeliveryForm.Posting posting = DeliveryForm.postingTo(List.of(), A_TARGET_NO_TEST_DECLARED);

    assertThat(posting)
        .isEqualTo(new DeliveryForm.Posting.Refused(DeliveryForm.THIS_RUN_CONFIGURES_NO_TARGET));
  }

  @Test
  void choosing_nothing_leaves_the_delivery_posted_to_the_target_it_was_scheduled_for() {
    assertThat(DeliveryForm.postingTo(TWO_TARGETS, null))
        .isEqualTo(new DeliveryForm.Posting.ToTheTargetItWasScheduledFor());
    assertThat(DeliveryForm.postingTo(TWO_TARGETS, "  "))
        .isEqualTo(new DeliveryForm.Posting.ToTheTargetItWasScheduledFor());
  }

  @Test
  void a_target_is_offered_with_the_account_that_declared_it_so_a_tester_reads_whose_it_is() {
    List<DeliveryForm.Target> offered = DeliveryForm.targetsOffered(TWO_TARGETS);

    assertThat(offered)
        .containsExactly(
            new DeliveryForm.Target("account-1", RUNS_OWN_TARGET),
            new DeliveryForm.Target("account-2", A_SECOND_TARGET));
  }

  @Test
  void an_event_is_composed_with_the_delay_a_tester_asked_for_in_milliseconds() {
    DeliveryForm.Composed composed =
        DeliveryForm.composed(RUN.value(), "pi_123", "payment_intent.succeeded", "2500", null);

    assertThat(composed).isInstanceOf(DeliveryForm.Composed.Send.class);
    assertThat(((DeliveryForm.Composed.Send) composed).call().delayMs()).isEqualTo(2500L);
  }

  @Test
  void an_event_composed_with_no_delay_is_asked_for_without_one() {
    DeliveryForm.Composed.Send send =
        (DeliveryForm.Composed.Send) DeliveryForm.composed(RUN.value(), "pi_123", null, "", null);

    assertThat(send.call().delayMs()).isNull();
    assertThat(send.call().eventType()).isNull();
  }

  @Test
  void a_delay_that_is_not_a_number_is_refused_naming_the_field_and_not_what_was_typed() {
    DeliveryForm.Composed.Refused refused =
        (DeliveryForm.Composed.Refused)
            DeliveryForm.composed(RUN.value(), "pi_123", null, "in a bit", null);

    assertThat(refused.message()).isEqualTo(DeliveryForm.A_DELAY_IS_A_WHOLE_NUMBER_OF_MILLISECONDS);
    assertThat(refused.message()).doesNotContain("in a bit");
  }

  @Test
  void a_delay_before_the_moment_it_was_asked_for_is_refused() {
    DeliveryForm.Composed composed = DeliveryForm.composed(RUN.value(), "pi_123", null, "-1", null);

    assertThat(composed)
        .isEqualTo(new DeliveryForm.Composed.Refused(DeliveryForm.A_DELAY_IS_NOT_NEGATIVE));
  }

  @Test
  void a_delay_of_zero_is_a_send_now_rather_than_a_refusal() {
    DeliveryForm.Composed.Send send =
        (DeliveryForm.Composed.Send) DeliveryForm.composed(RUN.value(), "pi_123", null, "0", null);

    assertThat(send.call().delayMs()).isZero();
  }

  @Test
  void an_event_composed_without_an_object_is_refused_before_a_provider_is_asked() {
    assertThat(DeliveryForm.composed(RUN.value(), null, null, null, null))
        .isEqualTo(new DeliveryForm.Composed.Refused(DeliveryForm.AN_OBJECT_IS_REQUIRED));
    assertThat(DeliveryForm.composed(RUN.value(), "   ", null, null, null))
        .isEqualTo(new DeliveryForm.Composed.Refused(DeliveryForm.AN_OBJECT_IS_REQUIRED));
  }

  @Test
  void an_id_a_tester_pasted_with_surrounding_space_still_addresses_the_object() {
    DeliveryForm.Composed.Send send =
        (DeliveryForm.Composed.Send)
            DeliveryForm.composed(RUN.value(), "  pi_123  ", "  evt.type  ", "  250  ", null);

    assertThat(send.call().providerObjectId()).isEqualTo("pi_123");
    assertThat(send.call().eventType()).isEqualTo("evt.type");
    assertThat(send.call().delayMs()).isEqualTo(250L);
  }

  @Test
  void an_event_id_a_tester_pasted_with_surrounding_space_still_names_the_event_to_replay() {
    DeliveryForm.Composed.Send send =
        (DeliveryForm.Composed.Send)
            DeliveryForm.composed(RUN.value(), "pi_123", null, null, "  evt_abc  ");

    assertThat(send.call().reuseEventId()).isEqualTo("evt_abc");
  }

  @Test
  void replaying_asks_for_the_event_id_the_queue_already_carries() {
    DeliveryForm.Composed.Send send =
        (DeliveryForm.Composed.Send)
            DeliveryForm.composed(RUN.value(), "pi_123", null, null, "evt_abc");

    assertThat(send.call().reuseEventId()).isEqualTo("evt_abc");
  }

  @Test
  void the_event_ids_offered_for_a_replay_are_the_ones_this_run_has_queued_each_named_once() {
    DeliveryForm.Composer composer =
        DeliveryForm.composerOf(
            List.of(
                aDelivery("whjob_1", "evt_abc"),
                aDelivery("whjob_2", "evt_abc"),
                aDelivery("whjob_3", "evt_def")),
            null,
            null,
            null,
            "evt_def");

    assertThat(composer.eventIds())
        .containsExactly(
            new DeliveryForm.Replayable("evt_abc", false),
            new DeliveryForm.Replayable("evt_def", true));
  }

  @Test
  void a_screen_that_offers_no_composer_offers_no_event_id_to_replay() {
    assertThat(DeliveryForm.noneOffered().eventIds()).isEmpty();
  }

  @Test
  void the_trail_records_the_delay_a_scheduled_send_was_asked_for() {
    DeliveryForm.Composed.Send send =
        (DeliveryForm.Composed.Send)
            DeliveryForm.composed(RUN.value(), "pi_123", null, "2500", null);

    assertThat(DeliveryForm.factsOf(send.call(), List.of("whjob_1")))
        .containsEntry("delayMs", List.of("2500"));
  }

  @Test
  void a_send_that_named_no_delay_records_no_delay_rather_than_a_zero_it_never_asked_for() {
    DeliveryForm.Composed.Send send =
        (DeliveryForm.Composed.Send) DeliveryForm.composed(RUN.value(), "pi_123", null, null, null);

    assertThat(DeliveryForm.factsOf(send.call(), List.of("whjob_1"))).doesNotContainKey("delayMs");
  }

  @Test
  void the_trail_records_the_event_the_replay_and_every_job_the_send_produced() {
    DeliveryForm.Composed.Send send =
        (DeliveryForm.Composed.Send)
            DeliveryForm.composed(RUN.value(), "pi_123", "payment_intent.succeeded", null, "evt_a");

    assertThat(DeliveryForm.factsOf(send.call(), List.of("whjob_1", "whjob_2")))
        .containsEntry("eventType", List.of("payment_intent.succeeded"))
        .containsEntry("reuseEventId", List.of("evt_a"))
        .containsEntry("webhookJobIds", List.of("whjob_1", "whjob_2"));
  }

  @Test
  void the_trail_records_the_target_an_attempt_was_retargeted_to() {
    assertThat(DeliveryForm.factsOf("whjob_1", A_SECOND_TARGET))
        .containsEntry("webhookJobId", List.of("whjob_1"))
        .containsEntry("targetUrl", List.of(A_SECOND_TARGET));
  }

  @Test
  void an_attempt_left_at_its_own_target_records_no_target_rather_than_repeating_the_delivery_s() {
    assertThat(DeliveryForm.factsOf("whjob_1", null))
        .containsEntry("webhookJobId", List.of("whjob_1"))
        .doesNotContainKey("targetUrl");
  }

  private static WebhookDelivery aDelivery(String deliveryId, String eventId) {
    return new WebhookDelivery(
        deliveryId,
        ProviderId.of("stripe"),
        RUN,
        "account-1",
        eventId,
        "payment_intent.succeeded",
        "pi_123",
        RUNS_OWN_TARGET,
        new WebhookPayload("application/json", Map.of(), new byte[] {1}, Map.of()),
        new RetryPlan(List.of(Duration.ZERO)),
        DeliveryState.PENDING,
        false,
        0,
        NOW,
        NOW,
        NOW,
        null,
        null);
  }
}
