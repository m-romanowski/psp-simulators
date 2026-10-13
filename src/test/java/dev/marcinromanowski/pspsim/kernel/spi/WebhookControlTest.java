package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookControlTest {

  private static final TestRunId RUN = TestRunId.of("run-a");

  private final ProviderDouble provider =
      new ProviderDouble().answering(new ScheduledWebhook("whjob_1", "evt_1"));

  @Test
  void a_webhook_call_that_names_no_operation_sends_a_new_one() {
    assertThat(WebhookControl.operationOf(null)).isEqualTo(WebhookControl.Operation.SEND);
    assertThat(WebhookControl.operationOf(" ")).isEqualTo(WebhookControl.Operation.SEND);
  }

  @Test
  void a_webhook_call_that_names_a_retry_retries_rather_than_sending_a_second_event() {
    assertThat(WebhookControl.operationOf("RETRY")).isEqualTo(WebhookControl.Operation.RETRY);
    assertThat(WebhookControl.operationOf("SEND")).isEqualTo(WebhookControl.Operation.SEND);
  }

  @ParameterizedTest(name = "the webhook operation {0} is answered with the ones that exist")
  @ValueSource(strings = {"REPLAY", "send", "DELETE"})
  void an_operation_this_control_api_does_not_publish_is_answered_with_the_ones_it_does(
      String named) {
    assertThatThrownBy(() -> WebhookControl.operationOf(named))
        .hasMessage("operation must be one of SEND, RETRY");
  }

  @Test
  void scheduling_a_new_callback_is_answered_with_created_and_retrying_an_existing_one_with_ok() {
    assertThat(WebhookControl.Operation.SEND.successStatusCode()).isEqualTo(201);
    assertThat(WebhookControl.Operation.RETRY.successStatusCode()).isEqualTo(200);
  }

  @Test
  void sending_a_webhook_reports_the_job_the_provider_scheduled() {
    assertThat(
            WebhookControl.send(
                provider, RUN, sending("9000001", null, null), ObjectOrigin.CONTROL_API))
        .containsExactly("whjob_1");
  }

  @Test
  void a_webhook_is_scheduled_for_the_object_the_event_and_the_test_run_the_caller_named() {
    WebhookControl.send(
        provider, RUN, sending("9000001", "payment.succeeded", null), ObjectOrigin.CONTROL_API);

    WebhookScheduleRequest request = provider.theOnlyRequestItWasAsked();
    assertThat(request.testRunId()).isEqualTo(RUN);
    assertThat(request.providerObjectId()).isEqualTo("9000001");
    assertThat(request.eventType()).isEqualTo("payment.succeeded");
    assertThat(request.reuseEventId()).isEqualTo("evt_sim_123");
    assertThat(request.delay()).isNull();
  }

  @Test
  void a_delay_a_caller_names_is_carried_to_the_provider_as_the_hold_it_asked_for() {
    WebhookControl.send(provider, RUN, sending("9000001", null, 250L), ObjectOrigin.CONTROL_API);

    assertThat(provider.theOnlyRequestItWasAsked().delay()).isEqualTo(Duration.ofMillis(250));
  }

  @Test
  void a_delay_of_no_milliseconds_schedules_the_webhook_now_rather_than_being_refused() {
    WebhookControl.send(provider, RUN, sending("9000001", null, 0L), ObjectOrigin.CONTROL_API);

    assertThat(provider.theOnlyRequestItWasAsked().delay()).isEqualTo(Duration.ZERO);
  }

  @Test
  void a_delay_that_would_schedule_a_webhook_in_the_past_is_rejected_by_the_property() {
    assertThatThrownBy(
            () ->
                WebhookControl.send(
                    provider, RUN, sending("9000001", null, -1L), ObjectOrigin.CONTROL_API))
        .hasMessage("delayMs must not be negative");
  }

  @ParameterizedTest(name = "a webhook call naming the object {0} cannot say what it is about")
  @ValueSource(strings = {"", " "})
  void a_webhook_call_that_names_no_object_is_rejected_by_the_property_that_broke_it(String named) {
    assertThatThrownBy(
            () ->
                WebhookControl.send(
                    provider, RUN, sending(named, null, null), ObjectOrigin.CONTROL_API))
        .hasMessage("providerObjectId is required to send a webhook");
  }

  @Test
  void a_webhook_call_naming_no_object_at_all_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(
            () ->
                WebhookControl.send(
                    provider, RUN, sending(null, null, null), ObjectOrigin.CONTROL_API))
        .hasMessage("providerObjectId is required to send a webhook");
  }

  @Test
  void a_callback_a_tester_asked_for_from_a_screen_reaches_the_provider_named_as_a_manual_one() {
    WebhookControl.send(provider, RUN, sending("9000001", null, null), ObjectOrigin.UI);

    assertThat(provider.theOnlyRequestItWasAsked().askedBy()).isEqualTo(ObjectOrigin.UI);
  }

  @Test
  void a_callback_a_harness_asked_for_reaches_the_provider_named_as_a_control_one() {
    WebhookControl.send(provider, RUN, sending("9000001", null, null), ObjectOrigin.CONTROL_API);

    assertThat(provider.theOnlyRequestItWasAsked().askedBy()).isEqualTo(ObjectOrigin.CONTROL_API);
  }

  @Test
  void a_retry_names_the_job_it_retries() {
    assertThat(WebhookControl.requiredWebhookJobId(retrying("whjob_7"))).isEqualTo("whjob_7");
  }

  @ParameterizedTest(name = "a retry naming the job {0} says nothing to retry")
  @ValueSource(strings = {"", " "})
  void a_retry_that_names_no_job_is_rejected_by_the_property_that_broke_it(String named) {
    assertThatThrownBy(() -> WebhookControl.requiredWebhookJobId(retrying(named)))
        .hasMessage("webhookJobId is required to retry a webhook");
  }

  @Test
  void a_retry_naming_no_job_at_all_is_rejected_by_the_property_that_broke_it() {
    assertThatThrownBy(() -> WebhookControl.requiredWebhookJobId(retrying(null)))
        .hasMessage("webhookJobId is required to retry a webhook");
  }

  @Test
  void a_provider_that_schedules_nothing_reports_no_job_rather_than_inventing_one() {
    assertThat(
            WebhookControl.send(
                new ProviderDouble(),
                RUN,
                sending("9000001", null, null),
                ObjectOrigin.CONTROL_API))
        .isEmpty();
  }

  @Test
  void a_provider_that_schedules_two_deliveries_reports_both() {
    ProviderDouble two =
        new ProviderDouble()
            .answering(new ScheduledWebhook("whjob_1", "evt_1"))
            .answering(new ScheduledWebhook("whjob_2", "evt_1"));

    assertThat(
            WebhookControl.send(two, RUN, sending("9000001", null, null), ObjectOrigin.CONTROL_API))
        .containsExactly("whjob_1", "whjob_2");
  }

  private static DeclaredWebhookCall sending(
      @Nullable String providerObjectId, @Nullable String eventType, @Nullable Long delayMs) {
    return new DeclaredWebhookCall(
        null, "merchant-1", providerObjectId, eventType, null, delayMs, "evt_sim_123", null, null);
  }

  private static DeclaredWebhookCall retrying(@Nullable String webhookJobId) {
    return new DeclaredWebhookCall(null, null, null, null, "RETRY", null, null, null, webhookJobId);
  }
}
