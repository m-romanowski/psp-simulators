package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TheWebhookEndpointTest {

  private static final Instant PINNED = Instant.parse("2026-09-15T10:00:00Z");

  private static final String PAYLOAD =
      "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\","
          + "\"data\":{\"object\":{\"id\":\"pi_1\",\"object\":\"payment_intent\"}}}";

  private static final String NAMING_NO_OBJECT =
      "{\"id\":\"evt_2\",\"object\":\"event\",\"type\":\"payment_intent.succeeded\","
          + "\"data\":{\"object\":{\"object\":\"payment_intent\"}}}";

  private final StoryRuns runs = new StoryRuns();
  private final StripeWebhooks webhooks = new StripeWebhooks(runs);
  private final StoryRun run =
      new StoryRun("shop-run-1", Story.A_CAPTURE_THAT_GOES_THROUGH, PINNED);

  {
    runs.keep(run);
  }

  @Test
  @DisplayName("an event signed with the run's own secret is received and listed")
  void anEventSignedWithTheRunsSecretIsReceived() {
    ReceivedEvent received =
        webhooks.receive(
            run.testRunId(),
            PAYLOAD,
            AnInMemorySimulator.signed(PAYLOAD, run.webhookSecret(), PINNED.getEpochSecond()));

    assertThat(received).isEqualTo(new ReceivedEvent("evt_1", "payment_intent.succeeded", "pi_1"));
    assertThat(run.received()).containsExactly(received);
  }

  @Test
  @DisplayName("an event Stripe delivers twice is listed once, and both deliveries are accepted")
  void anEventDeliveredTwiceIsListedOnce() {
    String signature =
        AnInMemorySimulator.signed(PAYLOAD, run.webhookSecret(), PINNED.getEpochSecond());

    ReceivedEvent first = webhooks.receive(run.testRunId(), PAYLOAD, signature);
    ReceivedEvent again = webhooks.receive(run.testRunId(), PAYLOAD, signature);

    assertThat(again).isEqualTo(first);
    assertThat(run.received()).containsExactly(first);
  }

  @Test
  @DisplayName("a signed event whose data.object names no id is refused and not listed")
  void aSignedEventNamingNoObjectIsRefused() {
    String signature =
        AnInMemorySimulator.signed(NAMING_NO_OBJECT, run.webhookSecret(), PINNED.getEpochSecond());

    assertThatThrownBy(() -> webhooks.receive(run.testRunId(), NAMING_NO_OBJECT, signature))
        .isInstanceOf(TheWebhookNamesNoObject.class)
        .hasMessageContaining("evt_2");
    assertThat(run.received()).isEmpty();
  }

  @Test
  @DisplayName("a signed event whose data.object names no id is answered 400 at the endpoint")
  void aSignedEventNamingNoObjectIsAnswered400() throws Exception {
    MockMvc endpoint =
        MockMvcBuilders.standaloneSetup(new StripeWebhookController(webhooks)).build();

    endpoint
        .perform(
            post(StripeWebhookController.PATH + run.testRunId())
                .contentType(MediaType.APPLICATION_JSON)
                .header(
                    "Stripe-Signature",
                    AnInMemorySimulator.signed(
                        NAMING_NO_OBJECT, run.webhookSecret(), PINNED.getEpochSecond()))
                .content(NAMING_NO_OBJECT))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("names no object")));
    assertThat(run.received()).isEmpty();
  }

  @Test
  @DisplayName("an event signed with another secret is refused and not listed")
  void anEventSignedWithAnotherSecretIsRefused() {
    String forged =
        AnInMemorySimulator.signed(PAYLOAD, "whsec_somebodyelse", PINNED.getEpochSecond());

    assertThatThrownBy(() -> webhooks.receive(run.testRunId(), PAYLOAD, forged))
        .isInstanceOf(TheWebhookIsNotStripes.class);
    assertThat(run.received()).isEmpty();
  }

  @Test
  @DisplayName("an event whose body was changed after signing is refused")
  void anEventChangedAfterSigningIsRefused() {
    String signature =
        AnInMemorySimulator.signed(PAYLOAD, run.webhookSecret(), PINNED.getEpochSecond());

    assertThatThrownBy(
            () -> webhooks.receive(run.testRunId(), PAYLOAD.replace("pi_1", "pi_2"), signature))
        .isInstanceOf(TheWebhookIsNotStripes.class);
  }

  @Test
  @DisplayName(
      "an event signed further in the past than Stripe's tolerance, by the run's clock, is refused")
  void anEventSignedTooLongAgoByTheRunsClockIsRefused() {
    String stale =
        AnInMemorySimulator.signed(PAYLOAD, run.webhookSecret(), PINNED.getEpochSecond() - 301);

    assertThatThrownBy(() -> webhooks.receive(run.testRunId(), PAYLOAD, stale))
        .isInstanceOf(TheWebhookIsNotStripes.class);
  }

  @Test
  @DisplayName(
      "an event signed within Stripe's tolerance before the run's clock is received, however long"
          + " ago that is by the machine's clock")
  void anEventSignedWithinTheToleranceByTheRunsClockIsReceived() {
    String recent =
        AnInMemorySimulator.signed(PAYLOAD, run.webhookSecret(), PINNED.getEpochSecond() - 299);

    assertThat(PINNED).isBefore(Instant.now().minusSeconds(300));
    assertThat(webhooks.receive(run.testRunId(), PAYLOAD, recent).eventId()).isEqualTo("evt_1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"t=abc,v1=x", "t", "t=1,v1", "t=99999999999999999999,v1=x"})
  @DisplayName("a Stripe-Signature header shaped unlike Stripe's is answered 400 and not listed")
  void aSignatureShapedUnlikeStripesIsAnswered400(String signature) throws Exception {
    MockMvc endpoint =
        MockMvcBuilders.standaloneSetup(new StripeWebhookController(webhooks)).build();

    endpoint
        .perform(
            post(StripeWebhookController.PATH + run.testRunId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(PAYLOAD))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("shaped unlike Stripe's")));
    assertThat(run.received()).isEmpty();
  }

  @Test
  @DisplayName("an event for a run the shop does not keep is refused")
  void anEventForARunTheShopDoesNotKeepIsRefused() {
    assertThatThrownBy(() -> webhooks.receive("shop-gone", PAYLOAD, "t=1,v1=00"))
        .isInstanceOf(NoSuchStoryRun.class);
  }
}
