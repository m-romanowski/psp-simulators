package dev.marcinromanowski.pspsim.journey;

import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.DAHLIA;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.ENDIVE;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.OTHER_RUN_SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.OTHER_SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.PUBLIC;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.RUN;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SIGNING_SECRET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.EphemeralKey;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.EphemeralKeyCreateParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentRetrieveParams;
import com.stripe.param.SetupIntentConfirmParams;
import com.stripe.param.SetupIntentCreateParams;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "simulator.webhook.poll-interval=PT59M")
class StripeJavaAcceptanceJourneyIntegrationTest extends PostgresBackedTest {

  @LocalServerPort private int port;
  private StripeJourneyClient operator;
  private StripeClient server;
  private StripeClient app;

  private record Saved(Customer customer, SetupIntent setup, PaymentMethod method) {}

  @BeforeEach
  void startTheOperatorServerAndClientOnAFreshApplication() {
    operator = new StripeJourneyClient(port);
    server = client(SECRET);
    app = client(PUBLIC);
  }

  @AfterEach
  void removeTheMerchantsObjectsAndRules() {
    operator.close();
  }

  @RepeatedTest(2)
  void the_typed_client_saves_both_card_forms_and_charges_independently_of_setup()
      throws StripeException {
    Saved cardData = save("data");
    Saved published = save("pm_card_visa");
    for (Saved saved : List.of(cardData, published)) {
      assertThat(saved.method().getCustomer()).isEqualTo(saved.customer().getId());
      assertThat(saved.method().getCard().getLast4()).isEqualTo("4242");
      assertThat(server.v1().customers().paymentMethods().list(saved.customer().getId()).getData())
          .extracting(PaymentMethod::getId)
          .containsExactly(saved.method().getId());
      PaymentIntent paid = server.v1().paymentIntents().create(offSession(saved, 1000L));
      assertPayment(paid, "succeeded", 1000L, 0L);
    }
    operator.rule("POST /v1/payment_intents", "1200", "insufficient_funds");
    assertThatThrownBy(() -> server.v1().paymentIntents().create(offSession(published, 1200L)))
        .isInstanceOfSatisfying(
            CardException.class,
            failure -> {
              assertThat(failure.getStatusCode()).isEqualTo(402);
              assertThat(failure.getDeclineCode()).isEqualTo("insufficient_funds");
              assertThat(failure.getStripeError().getPaymentIntent().getStatus())
                  .isEqualTo("requires_payment_method");
            });
    assertThat(server.v1().setupIntents().retrieve(published.setup().getId()).getStatus())
        .isEqualTo("succeeded");
    Saved failsAfterSaving = save("pm_card_chargeCustomerFail");
    assertThat(failsAfterSaving.setup().getStatus()).isEqualTo("succeeded");
    assertThatThrownBy(
            () -> server.v1().paymentIntents().create(offSession(failsAfterSaving, 1300L)))
        .isInstanceOfSatisfying(
            CardException.class,
            failure -> {
              assertThat(failure.getStatusCode()).isEqualTo(402);
              assertThat(failure.getDeclineCode()).isEqualTo("generic_decline");
              assertThat(failure.getStripeError().getPaymentMethod().getCard().getLast4())
                  .isEqualTo("0341");
            });
  }

  @RepeatedTest(2)
  void the_typed_client_captures_releases_expires_and_reconciles_a_lost_response()
      throws StripeException {
    PaymentIntent held = hold(1000L);
    assertPayment(held, "requires_capture", 0L, 1000L);
    PaymentIntent full = server.v1().paymentIntents().capture(held.getId());
    assertPayment(full, "succeeded", 1000L, 0L);
    assertThat(server.v1().charges().retrieve(full.getLatestCharge()).getAmountCaptured())
        .isEqualTo(1000L);

    PaymentIntent partialHold = hold(1000L);
    PaymentIntent partial =
        server
            .v1()
            .paymentIntents()
            .capture(
                partialHold.getId(),
                PaymentIntentCaptureParams.builder()
                    .setAmountToCapture(300L)
                    .setFinalCapture(false)
                    .build());
    assertPayment(partial, "requires_capture", 300L, 700L);
    PaymentIntent finalPart =
        server
            .v1()
            .paymentIntents()
            .capture(
                partialHold.getId(),
                PaymentIntentCaptureParams.builder()
                    .setAmountToCapture(250L)
                    .setFinalCapture(true)
                    .build());
    assertPayment(finalPart, "succeeded", 550L, 0L);
    Charge partialCharge = server.v1().charges().retrieve(finalPart.getLatestCharge());
    assertThat(partialCharge.getAmount()).isEqualTo(1000L);
    assertThat(partialCharge.getAmountCaptured()).isEqualTo(550L);
    assertThat(partialCharge.getCaptured()).isTrue();

    PaymentIntent toCancel = hold(700L);
    assertPayment(server.v1().paymentIntents().cancel(toCancel.getId()), "canceled", 0L, 0L);
    PaymentIntent expires = hold(900L);
    operator.advance("PT1H59M59S");
    assertPayment(
        server.v1().paymentIntents().retrieve(expires.getId()), "requires_capture", 0L, 900L);
    operator.advance("PT1S");
    PaymentIntent expired = server.v1().paymentIntents().retrieve(expires.getId());
    assertPayment(expired, "canceled", 0L, 0L);
    assertThat(expired.getCancellationReason()).isEqualTo("expired");
    assertThatThrownBy(() -> server.v1().paymentIntents().capture(expires.getId()))
        .isInstanceOfSatisfying(
            StripeException.class,
            failure -> assertThat(failure.getCode()).isEqualTo("charge_expired_for_capture"));

    PaymentIntent losesItsAnswer = hold(1050L);
    operator.loseTheCaptureResponse();
    RequestOptions keyed = RequestOptions.builder().setIdempotencyKey("java-lost-capture").build();
    assertThatThrownBy(() -> server.v1().paymentIntents().capture(losesItsAnswer.getId(), keyed))
        .isInstanceOf(ApiConnectionException.class);
    PaymentIntent reconciled = server.v1().paymentIntents().retrieve(losesItsAnswer.getId());
    assertPayment(reconciled, "succeeded", 1050L, 0L);
    JsonObject ledger = operator.ledger(reconciled.getId());
    assertThat(ledger.getAsJsonArray("operations"))
        .extracting(element -> StripeJourneyClient.value(element.getAsJsonObject(), "kind"))
        .containsExactly("AUTHORIZATION", "CAPTURE");
    String chargeBefore = server.v1().charges().retrieve(reconciled.getLatestCharge()).toJson();
    PaymentIntent replayed = server.v1().paymentIntents().capture(losesItsAnswer.getId(), keyed);
    assertThat(replayed.getLastResponse().headers().firstValue("Idempotent-Replayed"))
        .contains("true");
    assertPayment(replayed, "succeeded", 1050L, 0L);
    assertThat(replayed.getLatestCharge()).isEqualTo(reconciled.getLatestCharge());
    assertThat(operator.ledger(reconciled.getId())).isEqualTo(ledger);
    assertThat(server.v1().charges().retrieve(replayed.getLatestCharge()).toJson())
        .isEqualTo(chargeBefore);
  }

  @RepeatedTest(2)
  void the_typed_client_obeys_versions_expansion_keys_and_boundaries_and_verifies_a_signed_event()
      throws StripeException {
    assertThat(com.stripe.Stripe.API_VERSION).isEqualTo("2026-08-26.dahlia");
    Saved saved = save("pm_card_visa");
    PaymentIntent created =
        server
            .v1()
            .paymentIntents()
            .create(
                PaymentIntentCreateParams.builder()
                    .setAmount(1000L)
                    .setCurrency("usd")
                    .setCustomer(saved.customer().getId())
                    .addPaymentMethodType("card")
                    .addExpand("customer")
                    .build());
    assertThat(created.getCustomerObject().getEmail()).isEqualTo("payer@example.com");
    assertThat(created.getPaymentMethod()).isNull();
    assertThat(created.getLatestCharge()).isNull();
    assertThat(created.getLastResponse().headers().firstValue("Stripe-Version"))
        .contains("2026-08-26.dahlia");
    PaymentIntent confirmed =
        app.v1()
            .paymentIntents()
            .confirm(
                created.getId(),
                PaymentIntentConfirmParams.builder()
                    .putExtraParam("client_secret", created.getClientSecret())
                    .setPaymentMethod(saved.method().getId())
                    .build());
    assertThat(confirmed.getStatus()).isEqualTo("succeeded");
    PaymentIntent expanded =
        server
            .v1()
            .paymentIntents()
            .retrieve(
                created.getId(),
                PaymentIntentRetrieveParams.builder()
                    .addExpand("customer")
                    .addExpand("payment_method")
                    .addExpand("latest_charge")
                    .build());
    assertThat(expanded.getCustomerObject().getId()).isEqualTo(saved.customer().getId());
    assertThat(expanded.getPaymentMethodObject().getCard().getLast4()).isEqualTo("4242");
    assertThat(expanded.getLatestChargeObject().getAmountCaptured()).isEqualTo(1000L);
    PaymentIntent plain = server.v1().paymentIntents().retrieve(created.getId());
    assertThat(plain.getCustomerObject()).isNull();
    assertThat(plain.getPaymentMethodObject()).isNull();
    assertThat(plain.getLatestChargeObject()).isNull();
    PaymentIntent mobile =
        app.v1()
            .paymentIntents()
            .retrieve(
                created.getId(),
                PaymentIntentRetrieveParams.builder()
                    .setClientSecret(created.getClientSecret())
                    .build(),
                version("2020-08-27"));
    assertThat(mobile.getStatus()).isEqualTo("succeeded");
    assertThat(mobile.getLastResponse().headers().firstValue("Stripe-Version"))
        .contains("2020-08-27");
    assertThatThrownBy(
            () ->
                server
                    .v1()
                    .setupIntents()
                    .create(
                        SetupIntentCreateParams.builder().addPaymentMethodType("card").build(),
                        version(ENDIVE)))
        .isInstanceOfSatisfying(
            StripeException.class,
            failure ->
                assertThat(failure.getCode())
                    .isEqualTo("payment_method_types_no_longer_supported"));
    assertThat(
            server
                .v1()
                .setupIntents()
                .create(
                    SetupIntentCreateParams.builder().addPaymentMethodType("card").build(),
                    version(DAHLIA))
                .getPaymentMethodTypes())
        .containsExactly("card");

    assertThatThrownBy(() -> app.v1().paymentIntents().capture(created.getId()))
        .isInstanceOfSatisfying(
            StripeException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(403));
    assertThatThrownBy(() -> app.v1().paymentIntents().cancel(created.getId()))
        .isInstanceOfSatisfying(
            StripeException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(403));
    assertThatThrownBy(() -> app.v1().customers().retrieve(saved.customer().getId()))
        .isInstanceOfSatisfying(
            StripeException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(403));
    assertThatThrownBy(() -> app.v1().paymentMethods().retrieve(saved.method().getId()))
        .isInstanceOfSatisfying(
            StripeException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(403));
    assertThatThrownBy(
            () ->
                app.v1()
                    .paymentIntents()
                    .retrieve(
                        created.getId(),
                        PaymentIntentRetrieveParams.builder().setClientSecret("wrong").build()))
        .isInstanceOfSatisfying(
            StripeException.class,
            failure -> {
              assertThat(failure.getStatusCode()).isEqualTo(400);
              assertThat(failure.getCode()).isEqualTo("payment_intent_invalid_parameter");
            });
    EphemeralKey ephemeral =
        server
            .v1()
            .ephemeralKeys()
            .create(
                EphemeralKeyCreateParams.builder()
                    .setCustomer(saved.customer().getId())
                    .setStripeVersion(DAHLIA)
                    .build(),
                version(DAHLIA));
    StripeClient shortLived = client(ephemeral.getSecret());
    assertThat(shortLived.v1().customers().retrieve(saved.customer().getId()).getId())
        .isEqualTo(saved.customer().getId());
    assertThatThrownBy(() -> shortLived.v1().paymentIntents().retrieve(created.getId()))
        .isInstanceOfSatisfying(
            StripeException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(401));
    for (String key : List.of(OTHER_SECRET, OTHER_RUN_SECRET)) {
      StripeClient stranger = client(key);
      assertThatThrownBy(() -> stranger.v1().paymentIntents().retrieve(created.getId()))
          .isInstanceOfSatisfying(
              StripeException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(404));
      assertThatThrownBy(() -> stranger.v1().paymentMethods().retrieve(saved.method().getId()))
          .isInstanceOfSatisfying(
              StripeException.class, failure -> assertThat(failure.getStatusCode()).isEqualTo(404));
    }
    operator.control(
        "POST", "/webhooks", Map.of("testRunId", RUN, "providerObjectId", created.getId()));
    var callback = operator.endpoint.received().getFirst();
    assertThat(callback.body()).doesNotContain("4242424242424242", "\"cvc\"");
    assertThat(callback.header("Stripe-Signature").getFirst()).startsWith("t=1793523600,v1=");
    Event event =
        Webhook.constructEvent(
            callback.body(), callback.header("Stripe-Signature").getFirst(), SIGNING_SECRET, 0L);
    assertThat(event.getApiVersion()).isEqualTo("2026-08-26.dahlia");
    assertThat(event.getType()).isEqualTo("payment_intent.succeeded");
    assertThat(event.getDataObjectDeserializer().getObject()).isPresent();
    assertThat(
            ((PaymentIntent) event.getDataObjectDeserializer().getObject().orElseThrow()).getId())
        .isEqualTo(created.getId());
  }

  private StripeClient client(String key) {
    return StripeClient.builder()
        .setApiKey(key)
        .setApiBase(operator.base())
        .setMaxNetworkRetries(0)
        .build();
  }

  private Saved save(String method) throws StripeException {
    Customer customer =
        server
            .v1()
            .customers()
            .create(CustomerCreateParams.builder().setEmail("payer@example.com").build());
    SetupIntent setup =
        server
            .v1()
            .setupIntents()
            .create(
                SetupIntentCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addAllowedPaymentMethodType(
                        SetupIntentCreateParams.AllowedPaymentMethodType.CARD)
                    .build());
    SetupIntentConfirmParams.Builder confirming =
        SetupIntentConfirmParams.builder().putExtraParam("client_secret", setup.getClientSecret());
    if ("data".equals(method)) {
      confirming.putExtraParam(
          "payment_method_data",
          Map.of(
              "type",
              "card",
              "card",
              Map.of(
                  "number", "4242424242424242", "exp_month", 12, "exp_year", 2030, "cvc", "123")));
    } else {
      confirming.setPaymentMethod(method);
    }
    SetupIntent saved = app.v1().setupIntents().confirm(setup.getId(), confirming.build());
    assertThat(saved.getStatus()).isEqualTo("succeeded");
    PaymentMethod retrieved = server.v1().paymentMethods().retrieve(saved.getPaymentMethod());
    assertThat(retrieved.getCustomer()).isEqualTo(customer.getId());
    return new Saved(customer, saved, retrieved);
  }

  private PaymentIntent hold(long amount) throws StripeException {
    return server
        .v1()
        .paymentIntents()
        .create(
            PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency("usd")
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                .setConfirm(true)
                .setPaymentMethod("pm_card_visa")
                .addAllowedPaymentMethodType(
                    PaymentIntentCreateParams.AllowedPaymentMethodType.CARD)
                .build());
  }

  private static PaymentIntentCreateParams offSession(Saved saved, long amount) {
    return PaymentIntentCreateParams.builder()
        .setAmount(amount)
        .setCurrency("usd")
        .setConfirm(true)
        .setOffSession(true)
        .setCustomer(saved.customer().getId())
        .setPaymentMethod(saved.method().getId())
        .build();
  }

  private static RequestOptions version(String version) {
    return RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride(
            RequestOptions.builder(), version)
        .build();
  }

  private static void assertPayment(
      PaymentIntent intent, String status, long received, long capturable) {
    assertThat(intent.getStatus()).isEqualTo(status);
    assertThat(intent.getAmountReceived()).isEqualTo(received);
    assertThat(intent.getAmountCapturable()).isEqualTo(capturable);
  }
}
