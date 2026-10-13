package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.AttemptWithheldException;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer.Answer;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer.ReceivedCallback;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantEndpoint;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import dev.marcinromanowski.pspsim.kernel.webhook.internal.HttpMerchantEndpoint;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StripeWebhookTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("stripe-run");

  private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

  private static final String ACCOUNT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51Simulator";
  private static final String SIGNING_SECRET = "whsec_theEndpointSecret";
  private static final String FIRST_INTENT = "pi_sim_0000000000000001";

  private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

  private static final Duration SETTLES_AFTER = Duration.ofSeconds(5);

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final StripeDoubles.Accounts accounts = new StripeDoubles.Accounts();
  private final StripeDoubles.Runs testRuns = new StripeDoubles.Runs(DEFAULT_RUN);
  private final StripeDoubles.Audit audit = new StripeDoubles.Audit();
  private final StripeDoubles.Intents intents = new StripeDoubles.Intents();
  private final StripeDoubles.Charges charges = new StripeDoubles.Charges(intents);
  private final StripeDoubles.Refunds refunds = new StripeDoubles.Refunds(intents);
  private final StripeDoubles.Customers customers = new StripeDoubles.Customers();
  private final StripeDoubles.PaymentMethods paymentMethods = new StripeDoubles.PaymentMethods();
  private final StripeDoubles.SetupIntents setupIntents =
      new StripeDoubles.SetupIntents(paymentMethods);
  private final StripeDoubles.Keys keys = new StripeDoubles.Keys();
  private final StripeDoubles.EphemeralKeys ephemeralKeys = new StripeDoubles.EphemeralKeys();
  private final StripeDoubles.CountingIds ids = new StripeDoubles.CountingIds();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
  private final LocalMerchantServer merchant = new LocalMerchantServer();
  private final MovingClock clock = new MovingClock(NOW);

  @AfterEach
  void stopTheMerchant() {
    merchant.close();
  }

  @Test
  void delivers_the_event_the_payment_intent_reached_signed_over_the_bytes_it_sent() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();

    ScheduledWebhook scheduled = send(null);

    ReceivedCallback delivered = onlyCallback();
    assertThat(delivered.contentType()).isEqualTo(StripeEvent.CONTENT_TYPE);
    assertThat(delivered.body())
        .contains("\"type\":\"payment_intent.amount_capturable_updated\"")
        .contains("\"object\":\"event\"")
        .contains("\"id\":\"" + scheduled.eventId() + "\"")
        .contains("\"api_version\":\"" + StripeEvent.API_VERSION + "\"")
        .contains("\"livemode\":false")
        .contains("\"pending_webhooks\":0")
        .contains("\"object\":\"payment_intent\"")
        .contains("\"status\":\"requires_capture\"");
    assertThat(scheduled.eventId()).startsWith("evt_");
    assertThat(signatureOf(delivered).timestamp()).isEqualTo(NOW.getEpochSecond());
    assertThat(signatureOf(delivered).v1())
        .isEqualTo(hmac(SIGNING_SECRET, NOW.getEpochSecond() + "." + delivered.body()));
  }

  @Test
  void a_retry_is_signed_afresh_so_its_bytes_differ_from_the_first_attempts() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    merchant.thenAnswering(new Answer.Respond(500, "not now"));
    WebhookDispatcher dispatcher = dispatcher();

    send(dispatcher, null);
    clock.moveTo(NOW.plusSeconds(1));
    dispatcher.deliverDue(StripeApi.STRIPE, E2E_RUN);

    List<ReceivedCallback> attempts = merchant.received();
    assertThat(attempts).hasSize(2);
    assertThat(attempts.get(1).body()).isEqualTo(attempts.get(0).body());
    assertThat(signatureOf(attempts.get(0)).timestamp()).isEqualTo(NOW.getEpochSecond());
    assertThat(signatureOf(attempts.get(1)).timestamp())
        .isEqualTo(NOW.plusSeconds(1).getEpochSecond());
    assertThat(signatureOf(attempts.get(1)).v1()).isNotEqualTo(signatureOf(attempts.get(0)).v1());
    assertThat(signatureOf(attempts.get(1)).v1())
        .isEqualTo(
            hmac(
                SIGNING_SECRET,
                NOW.plusSeconds(1).getEpochSecond() + "." + attempts.get(1).body()));
  }

  @Test
  void two_attempts_this_runs_clock_does_not_separate_carry_one_signature_between_them() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    merchant.thenAnswering(new Answer.Respond(500, "not now"));
    WebhookDispatcher dispatcher = dispatcher();

    ScheduledWebhook scheduled = send(dispatcher, null);
    dispatcher.retry(StripeApi.STRIPE, E2E_RUN, scheduled.webhookJobId());

    List<ReceivedCallback> attempts = merchant.received();
    assertThat(attempts).hasSize(2);
    assertThat(signatureOf(attempts.get(1)).timestamp())
        .isEqualTo(signatureOf(attempts.get(0)).timestamp());
    assertThat(signatureOf(attempts.get(1)).v1()).isEqualTo(signatureOf(attempts.get(0)).v1());
  }

  @Test
  void an_account_that_dates_its_deliveries_in_the_past_emits_a_stale_t_and_is_refused_nothing() {
    anAccountSigningWith(
        SIGNING_SECRET, Map.of(StripeWebhookSigning.AGE_SETTING, FIVE_MINUTES.toString()));
    aHeldPaymentIntent();
    WebhookDispatcher dispatcher = dispatcher();

    ScheduledWebhook scheduled = send(dispatcher, null);

    ReceivedCallback delivered = onlyCallback();
    Signature signature = signatureOf(delivered);
    assertThat(signature.timestamp()).isEqualTo(NOW.minus(FIVE_MINUTES).getEpochSecond());
    assertThat(signature.v1())
        .isEqualTo(hmac(SIGNING_SECRET, signature.timestamp() + "." + delivered.body()));
    assertThat(deliveryOf(dispatcher, scheduled).state()).isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void an_account_that_corrupts_its_signature_emits_a_v1_the_merchants_own_check_cannot_verify() {
    anAccountSigningWith(
        SIGNING_SECRET,
        Map.of(StripeWebhookSigning.EMISSION_SETTING, StripeSignatureEmission.CORRUPTED.name()));
    aHeldPaymentIntent();

    send(null);

    ReceivedCallback delivered = onlyCallback();
    Signature signature = signatureOf(delivered);
    assertThat(signature.timestamp()).isEqualTo(NOW.getEpochSecond());
    assertThat(signature.v1()).hasSize(64);
    assertThat(signature.v1())
        .isNotEqualTo(hmac(SIGNING_SECRET, signature.timestamp() + "." + delivered.body()));
  }

  @Test
  void the_test_mode_element_is_never_the_v1_a_merchant_verifies() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();

    send(null);

    Signature signature = signatureOf(onlyCallback());
    assertThat(signature.v0()).hasSize(64).isNotEqualTo(signature.v1());
  }

  @Test
  void an_account_given_no_signing_secret_delivers_the_event_under_no_signature_at_all() {
    anAccount(Map.of());
    aHeldPaymentIntent();

    send(null);

    assertThat(onlyCallback().header(StripeSignature.HEADER)).isEmpty();
    assertThat(onlyCallback().body()).contains("\"object\":\"event\"");
  }

  @Test
  void a_replayed_event_id_is_the_id_inside_the_body_the_merchant_reads() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    WebhookDispatcher dispatcher = dispatcher();

    ScheduledWebhook first = send(dispatcher, null);
    ScheduledWebhook replay =
        onlyOne(
            webhooks(dispatcher)
                .schedule(
                    new WebhookScheduleRequest(
                        E2E_RUN,
                        FIRST_INTENT,
                        null,
                        first.eventId(),
                        null,
                        ObjectOrigin.CONTROL_API)));

    assertThat(replay.eventId()).isEqualTo(first.eventId());
    assertThat(replay.webhookJobId()).isNotEqualTo(first.webhookJobId());
    assertThat(merchant.received()).hasSize(2);
    assertThat(merchant.received().get(1).body())
        .contains("\"id\":\"" + first.eventId() + "\"")
        .isEqualTo(merchant.received().get(0).body());
  }

  @Test
  void reports_a_captured_payment_as_succeeded_and_a_cancelled_hold_as_canceled() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    api()
        .capturePaymentIntent(
            call("/v1/payment_intents/" + FIRST_INTENT + "/capture", Map.of()), FIRST_INTENT);

    send(null);

    assertThat(onlyCallback().body())
        .contains("\"type\":\"payment_intent.succeeded\"")
        .contains("\"status\":\"succeeded\"");
  }

  @Test
  void reports_a_cancelled_hold_as_canceled() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    api()
        .cancelPaymentIntent(
            call("/v1/payment_intents/" + FIRST_INTENT + "/cancel", Map.of()), FIRST_INTENT);

    send(null);

    assertThat(onlyCallback().body())
        .contains("\"type\":\"payment_intent.canceled\"")
        .contains("\"status\":\"canceled\"");
  }

  @Test
  void reports_a_declined_confirmation_as_payment_failed() {
    anAccountSigningWith(SIGNING_SECRET);
    decliningTheConfirmation();
    aHeldPaymentIntent();

    send(null);

    assertThat(onlyCallback().body())
        .contains("\"type\":\"payment_intent.payment_failed\"")
        .contains("\"status\":\"requires_payment_method\"");
  }

  @Test
  void refuses_an_intent_that_has_reached_no_state_this_simulator_publishes_an_event_for() {
    anAccountSigningWith(SIGNING_SECRET);
    anUnconfirmedPaymentIntent();

    assertThatThrownBy(() -> send(null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining(StripeWebhooks.NOTHING_TO_REPORT)
        .hasMessageContaining("payment_intent.canceled")
        .hasMessageNotContaining("refund.created");
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void refuses_to_report_a_failure_for_a_payment_that_stands_unattempted_rather_than_declined() {
    anAccountSigningWith(SIGNING_SECRET);
    anIntentAwaitingAPaymentMethod();

    String paymentFailed = StripeEventType.PAYMENT_FAILED.wireValue();

    assertThatThrownBy(() -> send(paymentFailed))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining(StripeWebhooks.NOT_WHAT_THE_INTENT_REACHED);
    assertThatThrownBy(() -> send(null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining(StripeWebhooks.NOTHING_TO_REPORT);
    assertThat(merchant.received()).isEmpty();
  }

  private void anIntentAwaitingAPaymentMethod() {
    Map<String, List<String>> fields = new LinkedHashMap<>(fieldsOfAHold());
    fields.remove(StripeFields.CONFIRM);
    fields.remove(StripeFields.PAYMENT_METHOD);
    api().createPaymentIntent(call("/v1/payment_intents", fields));
  }

  @Test
  void refuses_an_event_stripe_publishes_for_a_state_the_intent_is_not_in() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();

    String succeeded = StripeEventType.SUCCEEDED.wireValue();

    assertThatThrownBy(() -> send(succeeded))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining(StripeWebhooks.NOT_WHAT_THE_INTENT_REACHED)
        .hasMessageContaining("requires_capture");
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void refuses_an_event_name_stripe_does_not_publish() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();

    assertThatThrownBy(() -> send("payment_intent.held"))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("payment_intent.amount_capturable_updated")
        .hasMessageContaining("payment_intent.succeeded")
        .hasMessageContaining("payment_intent.payment_failed")
        .hasMessageContaining("payment_intent.canceled")
        .hasMessageContaining("refund.created");
  }

  @Test
  void delivers_the_event_a_control_call_names_when_the_intent_is_in_that_state() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();

    send(StripeEventType.AMOUNT_CAPTURABLE_UPDATED.wireValue());

    assertThat(onlyCallback().body())
        .contains("\"type\":\"payment_intent.amount_capturable_updated\"");
  }

  @Test
  void the_provider_the_control_api_addresses_schedules_the_delivery_of_its_own_events() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    WebhookDispatcher dispatcher = dispatcher();

    List<ScheduledWebhook> scheduled =
        provider(dispatcher)
            .schedule(
                new WebhookScheduleRequest(
                    E2E_RUN, FIRST_INTENT, null, null, null, ObjectOrigin.CONTROL_API));

    assertThat(scheduled)
        .singleElement()
        .satisfies(
            job -> {
              assertThat(job.webhookJobId()).startsWith("whjob_");
              assertThat(job.eventId()).startsWith("evt_");
            });
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void resetting_a_test_run_counts_its_deliveries_beside_its_intents_and_its_rules() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    WebhookDispatcher dispatcher = dispatcher();
    send(dispatcher, null);
    decliningTheConfirmation();

    ResetOutcome outcome = provider(dispatcher).reset(E2E_RUN, ResetScope.TEST_RUN);

    assertThat(outcome.clearedObjects()).isEqualTo(3);
    assertThat(dispatcher.of(StripeApi.STRIPE, E2E_RUN)).isEmpty();
  }

  @Test
  void resetting_the_whole_simulator_counts_every_deliverys_run_beside_the_intents() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    WebhookDispatcher dispatcher = dispatcher();
    send(dispatcher, null);
    decliningTheConfirmation();

    ResetOutcome outcome = provider(dispatcher).reset(E2E_RUN, ResetScope.ALL);

    assertThat(outcome.clearedObjects()).isEqualTo(3);
    assertThat(dispatcher.of(StripeApi.STRIPE, E2E_RUN)).isEmpty();
  }

  private SimulatorProvider provider(WebhookDispatcher dispatcher) {
    return new StripeProvider(
        accounts,
        intents,
        charges,
        refunds,
        customers,
        paymentMethods,
        setupIntents,
        ephemeralKeys,
        keys,
        audit,
        new InMemoryPaymentMethodFixtures(),
        scenarioRules,
        StripeDoubles.heldResponses(),
        clock,
        dispatcher,
        webhooks(dispatcher),
        new CapturePaymentIntent(intents, new ScenarioEngine(scenarioRules)),
        new CancelPaymentIntent(intents, new ScenarioEngine(scenarioRules)),
        new CreateRefund(intents, refunds, charges, ids, new ScenarioEngine(scenarioRules)),
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            new ScenarioEngine(scenarioRules),
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(
            setupIntents, paymentMethods, customers, new ScenarioEngine(scenarioRules)));
  }

  @Test
  void refuses_to_report_a_payment_intent_no_test_run_of_this_simulator_holds() {
    anAccountSigningWith(SIGNING_SECRET);

    assertThatThrownBy(() -> send(null))
        .isInstanceOf(UnknownProviderObjectException.class)
        .hasMessageContaining(StripeWebhooks.NO_SUCH_OBJECT);
  }

  @Test
  void refuses_to_deliver_to_an_account_whose_test_run_configured_no_single_endpoint() {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D"));
    accounts.merge(
        StripeApi.STRIPE, E2E_RUN, List.of(ACCOUNT), List.of(), StripeDoubles.storingNothing());
    aHeldPaymentIntent();

    assertThatThrownBy(() -> send(null))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining(StripeWebhooks.NO_SINGLE_TARGET);
  }

  @Test
  void a_merchant_that_never_acknowledges_runs_the_delivery_out_of_attempts() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    merchant.alwaysAnswering(new Answer.Respond(503, "never"));
    WebhookDispatcher dispatcher = dispatcher();

    ScheduledWebhook scheduled = send(dispatcher, null);
    clock.moveTo(NOW.plusSeconds(1));
    dispatcher.deliverDue(StripeApi.STRIPE, E2E_RUN);
    clock.moveTo(NOW.plusSeconds(6));
    dispatcher.deliverDue(StripeApi.STRIPE, E2E_RUN);

    assertThat(deliveryOf(dispatcher, scheduled).state()).isEqualTo(DeliveryState.EXHAUSTED);
    assertThat(merchant.received()).hasSize(3);
  }

  @Test
  void a_redirect_the_merchant_answers_with_is_a_failure_rather_than_a_delivery() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    merchant.alwaysAnswering(new Answer.Respond(302, "elsewhere"));
    WebhookDispatcher dispatcher = dispatcher();

    ScheduledWebhook scheduled = send(dispatcher, null);

    assertThat(deliveryOf(dispatcher, scheduled).state()).isEqualTo(DeliveryState.PENDING);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void a_merchant_that_answers_at_the_edge_of_the_successful_range_is_read_as_refusing() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    merchant.alwaysAnswering(new Answer.Respond(300, "look elsewhere"));
    WebhookDispatcher dispatcher = dispatcher();

    ScheduledWebhook scheduled = send(dispatcher, null);

    assertThat(deliveryOf(dispatcher, scheduled).state()).isEqualTo(DeliveryState.PENDING);
  }

  @Test
  void a_merchant_that_drops_the_connection_answers_nothing_and_the_event_is_sent_again() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    merchant.thenAnswering(new Answer.DropTheConnection());
    WebhookDispatcher dispatcher = dispatcher();

    ScheduledWebhook scheduled = send(dispatcher, null);
    assertThat(deliveryOf(dispatcher, scheduled).state()).isEqualTo(DeliveryState.PENDING);

    clock.moveTo(NOW.plusSeconds(1));
    dispatcher.deliverDue(StripeApi.STRIPE, E2E_RUN);

    assertThat(deliveryOf(dispatcher, scheduled).state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(merchant.received()).hasSize(2);
  }

  @Test
  void audits_the_signature_each_attempt_travelled_under_and_never_the_intents_client_secret() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    merchant.thenAnswering(new Answer.Respond(500, "not now"));
    WebhookDispatcher dispatcher = dispatcher();

    send(dispatcher, null);
    clock.moveTo(NOW.plusSeconds(1));
    dispatcher.deliverDue(StripeApi.STRIPE, E2E_RUN);

    List<AuditRecord> attempts =
        audit.all().stream()
            .filter(record -> record.direction() == AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT)
            .toList();
    assertThat(attempts).hasSize(2);
    assertThat(attempts.get(0).sanitizedHeaders().get(StripeSignature.HEADER))
        .isNotEqualTo(attempts.get(1).sanitizedHeaders().get(StripeSignature.HEADER));
    assertThat(attempts)
        .as(
            "the second attempt's body is minted by the rendering that re-signs it, out of the"
                + " body the scheduling class already withheld, so a row written from the wire"
                + " bytes instead would read the secret back here")
        .allSatisfy(
            attempt ->
                assertThat(attempt.sanitizedBody())
                    .containsEntry("data.object.client_secret", List.of(Redaction.REDACTED))
                    .containsEntry("type", List.of("payment_intent.amount_capturable_updated"))
                    .containsEntry("data.object.object", List.of("payment_intent"))
                    .containsEntry("object", List.of("event")));
  }

  @Test
  void a_delivery_whose_account_was_removed_reaches_the_merchant_no_longer_rather_than_unsigned() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    merchant.alwaysAnswering(new Answer.Respond(503, "not now"));
    WebhookDispatcher dispatcher = dispatcher();
    ScheduledWebhook scheduled = send(dispatcher, null);
    assertThat(merchant.received()).hasSize(1);

    accounts.remove(E2E_RUN, ACCOUNT);
    clock.moveTo(NOW.plusSeconds(1));
    int attempted = dispatcher.deliverDue(StripeApi.STRIPE, E2E_RUN);

    assertThat(attempted).isZero();
    assertThat(merchant.received()).hasSize(1);
    WebhookDelivery undeliverable = deliveryOf(dispatcher, scheduled);
    assertThat(undeliverable.state()).isEqualTo(DeliveryState.UNDELIVERABLE);
    assertThat(undeliverable.attemptsMade()).isEqualTo(1);
    assertThat(undeliverable.notSentBecause())
        .isEqualTo(StripeWebhookRendering.NO_ACCOUNT_TO_SIGN_IT);
    assertThat(dispatcher.attemptsOf(scheduled.webhookJobId())).hasSize(1);
  }

  @Test
  void
      a_retry_to_another_target_of_a_delivery_whose_account_was_removed_is_refused_with_its_reason() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    WebhookDispatcher dispatcher = dispatcher();
    ScheduledWebhook scheduled = send(dispatcher, null);
    WebhookDelivery before = deliveryOf(dispatcher, scheduled);
    accounts.remove(E2E_RUN, ACCOUNT);

    assertThatThrownBy(
            () ->
                dispatcher.retry(
                    StripeApi.STRIPE,
                    E2E_RUN,
                    scheduled.webhookJobId(),
                    "https://elsewhere.example/stripe"))
        .isInstanceOf(AttemptWithheldException.class)
        .hasMessage(
            AttemptWithheldException.THE_PROVIDER_WITHHELD_IT
                + StripeWebhookRendering.NO_ACCOUNT_TO_SIGN_IT);

    WebhookDelivery after = deliveryOf(dispatcher, scheduled);
    assertThat(after.state()).isEqualTo(before.state());
    assertThat(after.attemptsMade()).isEqualTo(before.attemptsMade());
    assertThat(after.nextAttemptAt()).isEqualTo(before.nextAttemptAt());
  }

  @Test
  void a_delivery_held_back_by_a_removed_account_goes_out_signed_once_the_account_is_back() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    merchant.thenAnswering(new Answer.Respond(503, "not now"));
    WebhookDispatcher dispatcher = dispatcher();
    ScheduledWebhook scheduled = send(dispatcher, null);
    accounts.remove(E2E_RUN, ACCOUNT);
    clock.moveTo(NOW.plusSeconds(1));
    dispatcher.deliverDue(StripeApi.STRIPE, E2E_RUN);

    anAccountSigningWith(SIGNING_SECRET);
    clock.moveTo(NOW.plusSeconds(2));
    dispatcher.retry(StripeApi.STRIPE, E2E_RUN, scheduled.webhookJobId());

    List<ReceivedCallback> sent = merchant.received();
    assertThat(sent).hasSize(2);
    assertThat(signatureOf(sent.get(1)).timestamp()).isEqualTo(NOW.plusSeconds(2).getEpochSecond());
    WebhookDelivery delivered = deliveryOf(dispatcher, scheduled);
    assertThat(delivered.state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(delivered.notSentBecause()).isNull();
  }

  @Test
  void an_account_still_configured_without_a_signing_secret_still_delivers_the_event_unsigned() {
    anAccount(Map.of());
    aHeldPaymentIntent();
    WebhookDispatcher dispatcher = dispatcher();

    ScheduledWebhook scheduled = send(dispatcher, null);

    assertThat(onlyCallback().header(StripeSignature.HEADER)).isEmpty();
    assertThat(deliveryOf(dispatcher, scheduled).state()).isEqualTo(DeliveryState.DELIVERED);
  }

  private void decliningTheConfirmation() {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            new ScenarioMatch(
                null, null, null, null, null, null, null, StripeOperations.CREATE, null, Map.of()),
            new ScenarioAction.TerminalFailure(StripeDecline.GENERIC_DECLINE.scenarioCode()),
            null));
  }

  private void anAccount(Map<String, String> signing) {
    accounts.with(StripeDoubles.account(E2E_RUN, ACCOUNT, SECRET_KEY, "P3D", signing));
    accounts.merge(
        StripeApi.STRIPE,
        E2E_RUN,
        List.of(ACCOUNT),
        List.of(new WebhookTarget(ACCOUNT, merchant.url())),
        StripeDoubles.storingNothing());
  }

  private void anAccountSigningWith(String secret) {
    anAccountSigningWith(secret, Map.of());
  }

  private void anAccountSigningWith(String secret, Map<String, String> alsoDeclaring) {
    Map<String, String> signing = new LinkedHashMap<>();
    signing.put(StripeWebhookSigning.SECRET_SETTING, secret);
    signing.putAll(alsoDeclaring);
    anAccount(signing);
  }

  private void aHeldPaymentIntent() {
    api().createPaymentIntent(call("/v1/payment_intents", fieldsOfAHold()));
  }

  private String aRefundOfAPaymentCapturedInFull() {
    aHeldPaymentIntent();
    StripeApi api = api();
    api.capturePaymentIntent(
        call("/v1/payment_intents/" + FIRST_INTENT + "/capture", Map.of()), FIRST_INTENT);
    Map<String, List<String>> form = new LinkedHashMap<>();
    form.put(StripeFields.PAYMENT_INTENT, List.of(FIRST_INTENT));
    form.put(StripeFields.AMOUNT, List.of("400"));
    return api.createRefund(call("/v1/refunds", form)).providerObjectId();
  }

  private ScheduledWebhook sendAbout(String providerObjectId, String eventType) {
    return onlyOne(
        webhooks(dispatcher())
            .schedule(
                new WebhookScheduleRequest(
                    E2E_RUN, providerObjectId, eventType, null, null, ObjectOrigin.CONTROL_API)));
  }

  @Test
  void delivers_a_created_refund_as_the_refund_event_that_carries_a_refund_object() {
    anAccountSigningWith(SIGNING_SECRET);
    String refundId = aRefundOfAPaymentCapturedInFull();

    ScheduledWebhook scheduled = sendAbout(refundId, null);

    ReceivedCallback delivered = onlyCallback();
    assertThat(delivered.body())
        .contains("\"type\":\"refund.created\"")
        .contains("\"object\":\"refund\"")
        .contains("\"id\":\"" + refundId + "\"")
        .contains("\"payment_intent\":\"" + FIRST_INTENT + "\"")
        .contains("\"status\":\"succeeded\"");
    assertThat(scheduled.eventId()).startsWith("evt_");
    assertThat(signatureOf(delivered).v1())
        .isEqualTo(hmac(SIGNING_SECRET, NOW.getEpochSecond() + "." + delivered.body()));
  }

  @Test
  void delivers_the_refund_event_a_control_call_names_for_a_refund_it_holds() {
    anAccountSigningWith(SIGNING_SECRET);
    String refundId = aRefundOfAPaymentCapturedInFull();

    sendAbout(refundId, StripeEventType.REFUND_CREATED.wireValue());

    assertThat(onlyCallback().body())
        .contains("\"type\":\"refund.created\"")
        .contains("\"id\":\"" + refundId + "\"");
  }

  @Test
  void refuses_to_report_a_refund_with_an_event_that_reports_a_payment_intent() {
    anAccountSigningWith(SIGNING_SECRET);
    String refundId = aRefundOfAPaymentCapturedInFull();

    assertThatThrownBy(() -> sendAbout(refundId, "payment_intent.succeeded"))
        .isInstanceOf(WebhookNotSchedulableException.class)
        .hasMessageContaining("payment_intent.succeeded reports a PaymentIntent")
        .hasMessageContaining("refund.created")
        .hasMessageNotContaining("payment_intent.canceled");
  }

  @Test
  void reports_a_payment_whose_remainder_a_non_final_capture_left_capturable() {
    anAccountSigningWith(SIGNING_SECRET);
    aHeldPaymentIntent();
    Map<String, List<String>> partial = new LinkedHashMap<>();
    partial.put(StripeFields.AMOUNT_TO_CAPTURE, List.of("400"));
    partial.put(StripeFields.FINAL_CAPTURE, List.of("false"));
    api()
        .capturePaymentIntent(
            call("/v1/payment_intents/" + FIRST_INTENT + "/capture", partial), FIRST_INTENT);

    sendAbout(FIRST_INTENT, null);

    assertThat(onlyCallback().body())
        .contains("\"type\":\"payment_intent.amount_capturable_updated\"")
        .contains("\"amount_capturable\":699")
        .contains("\"amount_received\":400");
  }

  private void anUnconfirmedPaymentIntent() {
    Map<String, List<String>> fields = new LinkedHashMap<>(fieldsOfAHold());
    fields.remove(StripeFields.CONFIRM);
    api().createPaymentIntent(call("/v1/payment_intents", fields));
  }

  private ScheduledWebhook send(String eventType) {
    return send(dispatcher(), eventType);
  }

  private ScheduledWebhook send(WebhookDispatcher dispatcher, String eventType) {
    return onlyOne(
        webhooks(dispatcher)
            .schedule(
                new WebhookScheduleRequest(
                    E2E_RUN, FIRST_INTENT, eventType, null, null, ObjectOrigin.CONTROL_API)));
  }

  private static ScheduledWebhook onlyOne(List<ScheduledWebhook> scheduled) {
    assertThat(scheduled).hasSize(1);
    return scheduled.get(0);
  }

  private StripeWebhooks webhooks(WebhookDispatcher dispatcher) {
    return new StripeWebhooks(
        intents,
        refunds,
        customers,
        paymentMethods,
        setupIntents,
        accounts,
        dispatcher,
        clock,
        ids);
  }

  private WebhookDispatcher dispatcher() {
    return StripeDoubles.eventDeliveries(
        deliveries, endpoint(), audit, clock, ids, accounts, scenarioRules);
  }

  private static MerchantEndpoint endpoint() {
    return new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofSeconds(5), 64);
  }

  private static WebhookDelivery deliveryOf(
      WebhookDispatcher dispatcher, ScheduledWebhook scheduled) {
    return dispatcher.find(StripeApi.STRIPE, E2E_RUN, scheduled.webhookJobId()).orElseThrow();
  }

  private ReceivedCallback onlyCallback() {
    assertThat(merchant.received()).hasSize(1);
    return merchant.received().get(0);
  }

  private static Signature signatureOf(ReceivedCallback delivered) {
    List<String> header = delivered.header(StripeSignature.HEADER);
    assertThat(header).hasSize(1);
    Map<String, String> elements = new LinkedHashMap<>();
    for (String element : header.get(0).split(",", -1)) {
      int separator = element.indexOf('=');
      elements.put(element.substring(0, separator), element.substring(separator + 1));
    }
    return new Signature(Long.parseLong(elements.get("t")), elements.get("v1"), elements.get("v0"));
  }

  private static String hmac(String secret, String material) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  @Test
  void a_payment_a_rule_left_in_flight_queues_the_event_reporting_the_outcome_it_is_owed() {
    anAccountSigningWith(SIGNING_SECRET);
    inFlight(null);
    WebhookDispatcher dispatcher = dispatcher();

    anAsynchronousChargeThrough(dispatcher);

    assertThat(dispatcher.of(StripeApi.STRIPE, E2E_RUN))
        .singleElement()
        .satisfies(
            queued -> {
              assertThat(queued.eventType()).isEqualTo(StripeEventType.SUCCEEDED.wireValue());
              assertThat(queued.providerObjectId()).isEqualTo(FIRST_INTENT);
              assertThat(queued.state()).isEqualTo(DeliveryState.PENDING);
              assertThat(queued.nextAttemptAt()).isEqualTo(NOW.plus(SETTLES_AFTER));
              assertThat(bodyOf(queued)).contains("\"status\":\"succeeded\"");
            });
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void a_payment_a_rule_left_in_flight_to_fail_queues_the_failure_event_and_the_code_it_carries() {
    anAccountSigningWith(SIGNING_SECRET);
    inFlight(StripeDecline.INSUFFICIENT_FUNDS);
    WebhookDispatcher dispatcher = dispatcher();

    anAsynchronousChargeThrough(dispatcher);

    assertThat(dispatcher.of(StripeApi.STRIPE, E2E_RUN))
        .singleElement()
        .satisfies(
            queued -> {
              assertThat(queued.eventType()).isEqualTo(StripeEventType.PAYMENT_FAILED.wireValue());
              assertThat(bodyOf(queued))
                  .contains("\"status\":\"requires_payment_method\"")
                  .contains(StripeDecline.INSUFFICIENT_FUNDS.declineCode());
            });
  }

  @Test
  void the_event_a_pending_payment_is_owed_reaches_the_merchant_once_the_run_clock_passes_it() {
    anAccountSigningWith(SIGNING_SECRET);
    inFlight(null);
    WebhookDispatcher dispatcher = dispatcher();
    anAsynchronousChargeThrough(dispatcher);

    clock.moveTo(NOW.plus(SETTLES_AFTER));
    int attempted = dispatcher.deliverDue(StripeApi.STRIPE, E2E_RUN);

    assertThat(attempted).isEqualTo(1);
    assertThat(onlyCallback().body()).contains("\"status\":\"succeeded\"");
  }

  @Test
  void a_pending_payment_of_an_account_with_no_endpoint_is_owed_an_outcome_and_no_callback() {
    accounts.with(
        StripeDoubles.account(
            E2E_RUN,
            ACCOUNT,
            SECRET_KEY,
            "P3D",
            Map.of(StripeWebhookSigning.SECRET_SETTING, SIGNING_SECRET)));
    inFlight(null);
    WebhookDispatcher dispatcher = dispatcher();

    anAsynchronousChargeThrough(dispatcher);

    assertThat(dispatcher.of(StripeApi.STRIPE, E2E_RUN)).isEmpty();
    assertThat(intents.all().get(0).statusAt(NOW.plus(SETTLES_AFTER)))
        .isEqualTo(StripeIntentStatus.SUCCEEDED);
  }

  @Test
  void a_confirmation_that_leaves_a_payment_in_flight_queues_the_event_it_is_owed_too() {
    anAccountSigningWith(SIGNING_SECRET);
    inFlightOn(StripeOperations.CONFIRM, null);
    WebhookDispatcher dispatcher = dispatcher();
    anUnconfirmedCharge();

    aConfirmationThrough(dispatcher);

    assertThat(dispatcher.of(StripeApi.STRIPE, E2E_RUN))
        .singleElement()
        .satisfies(
            queued -> {
              assertThat(queued.eventType()).isEqualTo(StripeEventType.SUCCEEDED.wireValue());
              assertThat(queued.providerObjectId()).isEqualTo(FIRST_INTENT);
              assertThat(queued.nextAttemptAt()).isEqualTo(NOW.plus(SETTLES_AFTER));
              assertThat(bodyOf(queued)).contains("\"status\":\"succeeded\"");
            });
  }

  @Test
  void the_event_a_confirmation_left_owed_reaches_the_merchant_once_the_run_clock_passes_it() {
    anAccountSigningWith(SIGNING_SECRET);
    inFlightOn(StripeOperations.CONFIRM, StripeDecline.INSUFFICIENT_FUNDS);
    WebhookDispatcher dispatcher = dispatcher();
    anUnconfirmedCharge();
    aConfirmationThrough(dispatcher);

    clock.moveTo(NOW.plus(SETTLES_AFTER));
    int attempted = dispatcher.deliverDue(StripeApi.STRIPE, E2E_RUN);

    assertThat(attempted).isEqualTo(1);
    assertThat(onlyCallback().body())
        .contains("\"type\":\"payment_intent.payment_failed\"")
        .contains(StripeDecline.INSUFFICIENT_FUNDS.declineCode());
  }

  private void anUnconfirmedCharge() {
    Map<String, List<String>> fields = new LinkedHashMap<>(fieldsOfAnAsynchronousCharge());
    fields.remove("confirm");
    new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            new ScenarioEngine(scenarioRules),
            StripeDoubles.NO_SCHEDULED_OUTCOMES)
        .handle(
            StripeForm.of(fields),
            Map.of(),
            E2E_RUN,
            accounts.find(E2E_RUN, ACCOUNT).orElseThrow(),
            NOW,
            ObjectOrigin.PROVIDER_API);
  }

  private void aConfirmationThrough(WebhookDispatcher dispatcher) {
    new ConfirmPaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            new ScenarioEngine(scenarioRules),
            webhooks(dispatcher))
        .handle(
            StripeForm.of(Map.of()),
            Map.of(),
            E2E_RUN,
            accounts.find(E2E_RUN, ACCOUNT).orElseThrow(),
            FIRST_INTENT,
            NOW);
  }

  private void anAsynchronousChargeThrough(WebhookDispatcher dispatcher) {
    new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            new ScenarioEngine(scenarioRules),
            webhooks(dispatcher))
        .handle(
            StripeForm.of(fieldsOfAnAsynchronousCharge()),
            Map.of(),
            E2E_RUN,
            accounts.find(E2E_RUN, ACCOUNT).orElseThrow(),
            NOW,
            ObjectOrigin.PROVIDER_API);
  }

  private void inFlight(@Nullable StripeDecline comingToRestWith) {
    inFlightOn(StripeOperations.CREATE, comingToRestWith);
  }

  private void inFlightOn(String operation, @Nullable StripeDecline comingToRestWith) {
    scenarioRules.add(
        new NewScenarioRule(
            StripeApi.STRIPE,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming(operation),
            new ScenarioAction.ProcessingThenTerminal(
                SETTLES_AFTER, comingToRestWith == null ? null : comingToRestWith.scenarioCode()),
            null));
  }

  private static Map<String, List<String>> fieldsOfAnAsynchronousCharge() {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("amount", List.of("1099"));
    fields.put("currency", List.of("pln"));
    fields.put("payment_method", List.of("pm_card_visa"));
    fields.put("confirm", List.of("true"));
    return fields;
  }

  private static String bodyOf(WebhookDelivery delivery) {
    return new String(delivery.payload().body(), StandardCharsets.UTF_8);
  }

  private StripeApi api() {
    return new StripeApi(
        accounts,
        keys,
        ephemeralKeys,
        testRuns,
        audit,
        clock,
        ids,
        DEFAULT_RUN,
        new CreatePaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            new ScenarioEngine(scenarioRules),
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new ConfirmPaymentIntent(
            intents,
            paymentMethods,
            charges,
            ids,
            new ScenarioEngine(scenarioRules),
            StripeDoubles.NO_SCHEDULED_OUTCOMES),
        new CapturePaymentIntent(intents, new ScenarioEngine(scenarioRules)),
        new CancelPaymentIntent(intents, new ScenarioEngine(scenarioRules)),
        new RetrievePaymentIntent(intents),
        new RetrieveCharge(charges, intents),
        new CreateRefund(intents, refunds, charges, ids, new ScenarioEngine(scenarioRules)),
        customers,
        paymentMethods,
        new CreateSetupIntent(setupIntents, ids),
        new ConfirmSetupIntent(
            setupIntents, paymentMethods, customers, StripeDoubles.noScenarios()));
  }

  private static StripeApiCall call(String path, Map<String, List<String>> fields) {
    return new StripeApiCall(
        "POST", path, Map.of("Authorization", List.of("Bearer " + SECRET_KEY)), fields);
  }

  private static Map<String, List<String>> fieldsOfAHold() {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("amount", List.of("1099"));
    fields.put("currency", List.of("pln"));
    fields.put("capture_method", List.of("manual"));
    fields.put("payment_method", List.of("pm_card_visa"));
    fields.put("customer", List.of("cus_sim_1"));
    fields.put("confirm", List.of("true"));
    return fields;
  }

  private record Signature(long timestamp, String v1, String v0) {}

  private static final class MovingClock implements SimulatorClock {

    private volatile Instant now;

    MovingClock(Instant now) {
      this.now = now;
    }

    void moveTo(Instant instant) {
      this.now = instant;
    }

    @Override
    public Instant now(TestRunId testRunId) {
      return now;
    }
  }
}
