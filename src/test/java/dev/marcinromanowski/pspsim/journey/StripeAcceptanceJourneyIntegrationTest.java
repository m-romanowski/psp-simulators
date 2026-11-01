package dev.marcinromanowski.pspsim.journey;

import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.DAHLIA;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.ENDIVE;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.OTHER_PUBLIC;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.OTHER_RUN_PUBLIC;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.OTHER_RUN_SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.OTHER_SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.PAYMENTS;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.PUBLIC;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.RUN;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SETUPS;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SIGNING_SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.payment;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.value;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.with;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.journey.StripeJourneyClient.Reply;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "simulator.webhook.poll-interval=PT59M")
class StripeAcceptanceJourneyIntegrationTest extends PostgresBackedTest {

  @LocalServerPort private int port;
  private StripeJourneyClient app;

  private record Saved(String customer, String setup, String method) {}

  @BeforeEach
  void startWithAnEmptyMerchantAtThePinnedClock() {
    app = new StripeJourneyClient(port);
  }

  @AfterEach
  void removeTheMerchantsObjectsAndRules() {
    app.close();
  }

  @RepeatedTest(2)
  void saving_by_card_data_a_published_id_and_a_fixture_attaches_a_readable_method() {
    for (String variant : List.of("data", "published", "fixture")) {
      Saved saved = save(variant);
      Reply retrieved = app.ok("GET", "/v1/payment_methods/" + saved.method(), SECRET, Map.of());
      assertThat(retrieved.at("customer")).isEqualTo(saved.customer());
      assertThat(retrieved.at("card.last4")).isEqualTo("4242");
      assertThat(retrieved.at("type")).isEqualTo("card");
      Reply listed =
          app.ok(
              "GET",
              "/v1/customers/" + saved.customer() + "/payment_methods",
              SECRET,
              Map.of("type", "card"));
      assertThat(listed.json().getAsJsonArray("data"))
          .extracting(element -> value(element.getAsJsonObject(), "id"))
          .containsExactly(saved.method());
      assertThat(app.ok("GET", SETUPS + "/" + saved.setup(), SECRET, Map.of()).at("status"))
          .isEqualTo("succeeded");
    }
  }

  @RepeatedTest(2)
  void the_documented_manual_flow_reads_the_charge_on_the_merchants_server() throws Exception {
    String section =
        Files.readString(Path.of("doc/stripe.md"))
            .split("### Authorize, capture or release", 2)[1]
            .split("### Reconcile", 2)[0];
    assertThat(section)
        .containsSubsequence(
            "curl \"$BASE/v1/payment_intents\" -u \"$SK:\"",
            "curl \"$BASE/v1/payment_intents/$INTENT/confirm\" -u \"$PK:\"",
            "curl \"$BASE/v1/payment_intents/$INTENT\" -u \"$SK:\"");
    Reply held = hold("1000");
    assertPayment(held, "requires_capture", "0", "1000");
    Reply charged = charge(held);
    assertThat(charged.at("payment_intent")).isEqualTo(held.at("id"));
    assertThat(charged.at("amount")).isEqualTo("1000");
    assertThat(charged.at("captured")).isEqualTo("false");
  }

  @RepeatedTest(2)
  void manual_authorizations_are_captured_in_full_in_parts_cancelled_and_expired() {
    Reply fullHold = hold("1000");
    String fullId = fullHold.at("id");
    assertThat(fullHold.at("status")).isEqualTo("requires_capture");
    assertThat(fullHold.at("amount_capturable")).isEqualTo("1000");
    Reply full = app.ok("POST", PAYMENTS + "/" + fullId + "/capture", SECRET, Map.of());
    assertPayment(full, "succeeded", "1000", "0");
    assertThat(charge(full).at("amount_captured")).isEqualTo("1000");

    Reply partialHold = hold("1000");
    String partialId = partialHold.at("id");
    Reply partial =
        app.ok(
            "POST",
            PAYMENTS + "/" + partialId + "/capture",
            SECRET,
            Map.of("amount_to_capture", "300", "final_capture", "false"));
    assertPayment(partial, "requires_capture", "300", "700");
    Reply finalPart =
        app.ok(
            "POST",
            PAYMENTS + "/" + partialId + "/capture",
            SECRET,
            Map.of("amount_to_capture", "250", "final_capture", "true"));
    assertPayment(finalPart, "succeeded", "550", "0");
    Reply partialCharge = charge(finalPart);
    assertThat(partialCharge.at("amount")).isEqualTo("1000");
    assertThat(partialCharge.at("amount_captured")).isEqualTo("550");
    assertThat(partialCharge.at("captured")).isEqualTo("true");
    assertThat(
            app.request("POST", PAYMENTS + "/" + partialId + "/capture", SECRET, Map.of(), Map.of())
                .status())
        .isEqualTo(400);

    Reply cancelHold = hold("700");
    Reply canceled =
        app.ok("POST", PAYMENTS + "/" + cancelHold.at("id") + "/cancel", SECRET, Map.of());
    assertPayment(canceled, "canceled", "0", "0");
    assertThat(charge(canceled).at("captured")).isEqualTo("false");

    Reply expires = hold("900");
    String expiresId = expires.at("id");
    app.advance("PT1H59M59S");
    assertPayment(
        app.ok("GET", PAYMENTS + "/" + expiresId, SECRET, Map.of()),
        "requires_capture",
        "0",
        "900");
    app.advance("PT1S");
    Reply expired = app.ok("GET", PAYMENTS + "/" + expiresId, SECRET, Map.of());
    assertPayment(expired, "canceled", "0", "0");
    assertThat(expired.at("cancellation_reason")).isEqualTo("expired");
    Reply refused =
        app.request("POST", PAYMENTS + "/" + expiresId + "/capture", SECRET, Map.of(), Map.of());
    assertThat(refused.status()).isEqualTo(400);
    assertThat(refused.at("error.code")).isEqualTo("charge_expired_for_capture");
  }

  @RepeatedTest(2)
  void a_successful_setup_can_be_charged_or_declined_independently_off_session() {
    Saved saved = save("published");
    Reply paid = app.ok("POST", PAYMENTS, SECRET, offSession(saved, "1000"));
    assertPayment(paid, "succeeded", "1000", "0");

    app.rule("POST /v1/payment_intents", "1200", "insufficient_funds");
    Reply declined = app.request("POST", PAYMENTS, SECRET, offSession(saved, "1200"), Map.of());
    assertThat(declined.status()).isEqualTo(402);
    assertThat(declined.at("error.decline_code")).isEqualTo("insufficient_funds");
    assertThat(declined.at("error.message")).isEqualTo("Your card has insufficient funds.");
    assertThat(declined.at("error.payment_intent.status")).isEqualTo("requires_payment_method");
    assertThat(app.ok("GET", SETUPS + "/" + saved.setup(), SECRET, Map.of()).at("status"))
        .isEqualTo("succeeded");

    String customer = app.ok("POST", "/v1/customers", SECRET, Map.of()).at("id");
    Reply setup =
        app.ok(
            "POST",
            SETUPS,
            SECRET,
            Map.of("customer", customer, "allowed_payment_method_types[]", "card"));
    Reply setUp =
        app.ok(
            "POST",
            SETUPS + "/" + setup.at("id") + "/confirm",
            PUBLIC,
            Map.of(
                "client_secret",
                setup.at("client_secret"),
                "payment_method",
                "pm_card_chargeCustomerFail"));
    assertThat(setUp.at("status")).isEqualTo("succeeded");
    Saved failsLater = new Saved(customer, setup.at("id"), setUp.at("payment_method"));
    Reply publishedDecline =
        app.request("POST", PAYMENTS, SECRET, offSession(failsLater, "1300"), Map.of());
    assertThat(publishedDecline.status()).isEqualTo(402);
    assertThat(publishedDecline.at("error.code")).isEqualTo("card_declined");
    assertThat(publishedDecline.at("error.decline_code")).isEqualTo("generic_decline");
    assertThat(publishedDecline.at("error.payment_method.card.last4")).isEqualTo("0341");
    assertThat(app.ok("GET", SETUPS + "/" + setup.at("id"), SECRET, Map.of()).at("status"))
        .isEqualTo("succeeded");
  }

  @RepeatedTest(2)
  void retrieving_and_replaying_a_lost_capture_preserves_the_charge_and_the_ledger() {
    Reply authorized = hold("1050");
    String id = authorized.at("id");
    app.loseTheCaptureResponse();
    Map<String, String> headers = Map.of("Idempotency-Key", "lost-capture-journey");
    String path = PAYMENTS + "/" + id + "/capture";
    assertThatThrownBy(() -> app.request("POST", path, SECRET, Map.of(), headers))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("fixed content-length");

    Reply reconciled = app.ok("GET", PAYMENTS + "/" + id, SECRET, Map.of());
    assertPayment(reconciled, "succeeded", "1050", "0");
    assertThat(reconciled.at("latest_charge")).isEqualTo(authorized.at("latest_charge"));
    JsonObject ledger = app.ledger(id);
    assertThat(ledger.getAsJsonArray("operations"))
        .extracting(element -> value(element.getAsJsonObject(), "kind"))
        .containsExactly("AUTHORIZATION", "CAPTURE");
    JsonObject capturedCharge = charge(reconciled).json();

    Reply replayed = app.ok("POST", path, SECRET, Map.of(), headers);
    assertThat(replayed.header("Idempotent-Replayed")).isEqualTo("true");
    assertPayment(replayed, "succeeded", "1050", "0");
    assertThat(replayed.at("latest_charge")).isEqualTo(authorized.at("latest_charge"));
    assertThat(app.ledger(id)).isEqualTo(ledger);
    assertThat(charge(replayed).json()).isEqualTo(capturedCharge);
  }

  @RepeatedTest(2)
  void expansion_key_permissions_secrets_and_version_resolution_work_over_raw_http() {
    Saved saved = save("data");
    Reply created =
        app.ok(
            "POST",
            PAYMENTS,
            SECRET,
            with(payment("1000"), "customer", saved.customer(), "expand[]", "customer"));
    String id = created.at("id");
    assertThat(created.header("Stripe-Version")).isEqualTo(DAHLIA);
    assertThat(created.at("customer.id")).isEqualTo(saved.customer());
    assertThat(created.json().get("payment_method").isJsonNull()).isTrue();
    assertThat(created.json().get("latest_charge").isJsonNull()).isTrue();
    Reply unfinishedSetup =
        app.ok(
            "POST",
            SETUPS,
            SECRET,
            Map.of("customer", saved.customer(), "allowed_payment_method_types[]", "card"));
    String otherSetupSecret =
        app.ok("GET", SETUPS + "/" + saved.setup(), SECRET, Map.of()).at("client_secret");
    for (String secret : List.of("", "wrong", otherSetupSecret, created.at("client_secret"))) {
      Reply refused =
          app.request(
              "POST",
              SETUPS + "/" + unfinishedSetup.at("id") + "/confirm",
              PUBLIC,
              Map.of("client_secret", secret, "payment_method", saved.method()),
              Map.of("Idempotency-Key", "correctable-setup"));
      assertThat(refused.status()).isEqualTo(400);
      assertThat(refused.at("error.param")).isEqualTo("client_secret");
      assertThat(
              app.ok("GET", SETUPS + "/" + unfinishedSetup.at("id"), SECRET, Map.of()).at("status"))
          .isEqualTo("requires_payment_method");
    }
    assertThat(
            app.ok(
                    "POST",
                    SETUPS + "/" + unfinishedSetup.at("id") + "/confirm",
                    PUBLIC,
                    Map.of(
                        "client_secret",
                        unfinishedSetup.at("client_secret"),
                        "payment_method",
                        saved.method()),
                    Map.of("Idempotency-Key", "correctable-setup"))
                .at("status"))
        .isEqualTo("succeeded");

    Reply another = app.ok("POST", PAYMENTS, SECRET, payment("1000"));
    for (String secret : List.of("", "wrong", another.at("client_secret"))) {
      Reply refusal =
          app.request(
              "POST",
              PAYMENTS + "/" + id + "/confirm",
              PUBLIC,
              Map.of("client_secret", secret, "payment_method", saved.method()),
              Map.of("Idempotency-Key", "correctable-confirm"));
      assertThat(refusal.status()).isEqualTo(400);
      assertThat(refusal.at("error.param")).isEqualTo("client_secret");
      assertThat(app.ok("GET", PAYMENTS + "/" + id, SECRET, Map.of()).at("status"))
          .isEqualTo("requires_payment_method");
    }
    Reply confirmed =
        app.ok(
            "POST",
            PAYMENTS + "/" + id + "/confirm",
            PUBLIC,
            Map.of("client_secret", created.at("client_secret"), "payment_method", saved.method()),
            Map.of("Idempotency-Key", "correctable-confirm"));
    assertThat(confirmed.at("status")).isEqualTo("succeeded");
    Reply expanded =
        app.ok(
            "GET",
            PAYMENTS + "/" + id,
            SECRET,
            Map.of(
                "expand[0]",
                "customer",
                "expand[1]",
                "payment_method",
                "expand[2]",
                "latest_charge"));
    assertThat(expanded.at("customer.id")).isEqualTo(saved.customer());
    assertThat(expanded.at("payment_method.card.last4")).isEqualTo("4242");
    assertThat(expanded.at("latest_charge.amount_captured")).isEqualTo("1000");
    Reply plain = app.ok("GET", PAYMENTS + "/" + id, SECRET, Map.of());
    assertThat(plain.at("customer")).isEqualTo(saved.customer());
    assertThat(plain.at("payment_method")).isEqualTo(saved.method());
    Reply mobile =
        app.ok(
            "GET",
            PAYMENTS + "/" + id,
            PUBLIC,
            Map.of("client_secret", created.at("client_secret")),
            Map.of("Stripe-Version", "2020-08-27"));
    assertThat(mobile.header("Stripe-Version")).isEqualTo("2020-08-27");
    assertThat(mobile.at("status")).isEqualTo("succeeded");

    Reply dahlia =
        app.ok(
            "POST",
            SETUPS,
            SECRET,
            Map.of("payment_method_types[]", "card"),
            Map.of("Stripe-Version", DAHLIA));
    assertThat(dahlia.json().getAsJsonArray("payment_method_types"))
        .extracting(element -> element.getAsString())
        .containsExactly("card");
    Reply indexed =
        app.ok(
            "POST",
            SETUPS,
            SECRET,
            Map.of("payment_method_types[0]", "card"),
            Map.of("Stripe-Version", DAHLIA));
    assertThat(indexed.json().get("payment_method_types"))
        .isEqualTo(dahlia.json().get("payment_method_types"));
    Reply endive =
        app.request(
            "POST",
            SETUPS,
            SECRET,
            Map.of("payment_method_types[]", "card"),
            Map.of("Stripe-Version", ENDIVE));
    assertThat(endive.status()).isEqualTo(400);
    assertThat(endive.at("error.code")).isEqualTo("payment_method_types_no_longer_supported");
    assertThat(
            app.request(
                    "POST",
                    PAYMENTS,
                    SECRET,
                    payment("1000"),
                    Map.of("Stripe-Version", "2024-06-20"))
                .status())
        .isEqualTo(400);

    Reply ephemeral =
        app.ok("POST", "/v1/ephemeral_keys", SECRET, Map.of("customer", saved.customer()));
    String ephemeralKey = ephemeral.at("secret");
    assertThat(app.ok("GET", "/v1/customers/" + saved.customer(), ephemeralKey, Map.of()).at("id"))
        .isEqualTo(saved.customer());
    assertThat(
            app.ok(
                    "GET",
                    "/v1/customers/" + saved.customer() + "/payment_methods",
                    ephemeralKey,
                    Map.of())
                .json()
                .getAsJsonArray("data"))
        .extracting(element -> value(element.getAsJsonObject(), "id"))
        .containsExactly(saved.method());
    for (String path :
        List.of(
            PAYMENTS,
            SETUPS,
            "/v1/customers",
            "/v1/ephemeral_keys",
            "/v1/refunds",
            PAYMENTS + "/" + id + "/capture",
            PAYMENTS + "/" + id + "/cancel")) {
      Reply refused = app.request("POST", path, PUBLIC, payment("1000"), Map.of());
      assertThat(refused.status()).isEqualTo(403);
      assertThat(refused.at("error.code")).isEqualTo("secret_key_required");
      assertThat(app.request("POST", path, ephemeralKey, payment("1000"), Map.of()).status())
          .isEqualTo(401);
    }
    for (String path :
        List.of("/v1/customers/" + saved.customer(), "/v1/payment_methods/" + saved.method())) {
      assertThat(app.request("GET", path, PUBLIC, Map.of(), Map.of()).status()).isEqualTo(403);
    }
    assertThat(app.request("GET", PAYMENTS + "/" + id, ephemeralKey, Map.of(), Map.of()).status())
        .isEqualTo(401);
    assertThat(app.request("GET", PAYMENTS + "/" + id, "", Map.of(), Map.of()).status())
        .isEqualTo(401);
    assertThat(
            app.request("GET", PAYMENTS + "/" + id, "sk_test_unknown", Map.of(), Map.of()).status())
        .isEqualTo(401);
    assertThat(app.ok("GET", PAYMENTS + "/" + id, SECRET, Map.of()).at("amount_received"))
        .isEqualTo("1000");
  }

  @RepeatedTest(2)
  void server_flows_signed_events_and_account_and_run_boundaries_stay_intact()
      throws GeneralSecurityException {
    Saved saved = save("fixture");
    Reply payment =
        app.ok(
            "POST", PAYMENTS, SECRET, offSession(saved, "2400"), Map.of("Stripe-Version", ENDIVE));
    Reply refund =
        app.ok(
            "POST",
            "/v1/refunds",
            SECRET,
            Map.of("payment_intent", payment.at("id"), "amount", "400"));
    assertThat(refund.at("status")).isEqualTo("succeeded");
    assertThat(charge(payment).at("amount_refunded")).isEqualTo("400");
    assertWebhook(payment.at("id"));
    for (String key : List.of(OTHER_SECRET, OTHER_RUN_SECRET)) {
      for (String path :
          List.of(
              PAYMENTS + "/" + payment.at("id"),
              SETUPS + "/" + saved.setup(),
              "/v1/customers/" + saved.customer(),
              "/v1/payment_methods/" + saved.method(),
              "/v1/charges/" + payment.at("latest_charge"))) {
        Reply refused = app.request("GET", path, key, Map.of(), Map.of());
        assertThat(refused.status()).isEqualTo(404);
        assertThat(refused.at("error.code")).isEqualTo("resource_missing");
      }
    }
    for (String key : List.of(OTHER_PUBLIC, OTHER_RUN_PUBLIC)) {
      Reply refused =
          app.request(
              "POST",
              PAYMENTS + "/" + payment.at("id") + "/confirm",
              key,
              Map.of(
                  "client_secret", payment.at("client_secret"), "payment_method", saved.method()),
              Map.of());
      assertThat(refused.status()).isEqualTo(404);
    }
    app.rule("POST /v1/payment_intents", "2600", "insufficient_funds");
    for (String key : List.of(OTHER_SECRET, OTHER_RUN_SECRET)) {
      Reply independent =
          app.ok(
              "POST",
              PAYMENTS,
              key,
              with(payment("2600"), "confirm", "true", "payment_method", "pm_card_visa"));
      assertThat(independent.at("status")).isEqualTo("succeeded");
    }
    Reply serverSetup =
        app.ok(
            "POST",
            SETUPS,
            SECRET,
            Map.of("customer", saved.customer(), "allowed_payment_method_types[]", "card"));
    assertThat(
            app.ok(
                    "POST",
                    SETUPS + "/" + serverSetup.at("id") + "/confirm",
                    SECRET,
                    Map.of("payment_method", saved.method()))
                .at("status"))
        .isEqualTo("succeeded");
  }

  private void assertWebhook(String id) throws GeneralSecurityException {
    JsonObject scheduled =
        app.control("POST", "/webhooks", Map.of("testRunId", RUN, "providerObjectId", id))
            .getAsJsonObject();
    assertThat(value(scheduled, "status")).isEqualTo("DELIVERED");
    assertThat(app.endpoint.received())
        .singleElement()
        .satisfies(
            delivered -> {
              assertThat(delivered.contentType()).isEqualTo("application/json");
              JsonObject event = JsonParser.parseString(delivered.body()).getAsJsonObject();
              assertThat(value(event, "api_version")).isEqualTo("2026-08-26.dahlia");
              assertThat(value(event, "type")).isEqualTo("payment_intent.succeeded");
              assertThat(value(event, "data.object.id")).isEqualTo(id);
              assertThat(value(event, "data.object.status")).isEqualTo("succeeded");
              assertThat(delivered.body()).doesNotContain("4242424242424242", "\"cvc\"");
            });
    var delivered = app.endpoint.received().getFirst();
    String signature = delivered.header("Stripe-Signature").getFirst();
    assertThat(signature).startsWith("t=1793523600,v1=");
    Mac hmac = Mac.getInstance("HmacSHA256");
    hmac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(UTF_8), "HmacSHA256"));
    assertThat(
            java.util.Arrays.stream(signature.split(",", -1))
                .filter(part -> part.startsWith("v1="))
                .findFirst()
                .orElseThrow()
                .substring(3))
        .isEqualTo(
            HexFormat.of()
                .formatHex(hmac.doFinal(("1793523600." + delivered.body()).getBytes(UTF_8))));
  }

  private Saved save(String variant) {
    String customer =
        app.ok("POST", "/v1/customers", SECRET, Map.of("email", "payer@example.com")).at("id");
    Reply setup =
        app.ok(
            "POST",
            SETUPS,
            SECRET,
            Map.of(
                "customer",
                customer,
                "usage",
                "off_session",
                "allowed_payment_method_types[]",
                "card"));
    Map<String, String> form;
    if ("data".equals(variant)) {
      form =
          Map.of(
              "payment_method_data[type]",
              "card",
              "payment_method_data[card][number]",
              "4242424242424242",
              "payment_method_data[card][exp_month]",
              "12",
              "payment_method_data[card][exp_year]",
              "2030",
              "payment_method_data[card][cvc]",
              "123");
    } else if ("fixture".equals(variant)) {
      app.control(
          "POST",
          "/fixtures/payment-methods",
          Map.of(
              "testRunId",
              RUN,
              "merchantId",
              "merchant-1",
              "ownerId",
              "owner",
              "type",
              "card",
              "alias",
              "saved-fixture",
              "card",
              Map.of(
                  "brand",
                  "visa",
                  "last4",
                  "4242",
                  "expMonth",
                  12,
                  "expYear",
                  2030,
                  "funding",
                  "credit"),
              "providerIds",
              Map.of("paymentMethodId", "pm_acceptance_fixture")));
      form = Map.of("payment_method", "pm_acceptance_fixture");
    } else {
      form = Map.of("payment_method", "pm_card_visa");
    }
    Reply confirmed =
        app.ok(
            "POST",
            SETUPS + "/" + setup.at("id") + "/confirm",
            PUBLIC,
            with(form, "client_secret", setup.at("client_secret")));
    assertThat(confirmed.at("status")).isEqualTo("succeeded");
    String method = confirmed.at("payment_method");
    if (!"fixture".equals(variant)) {
      assertThat(method).startsWith("pm_sim_");
    }
    return new Saved(customer, setup.at("id"), method);
  }

  private static Map<String, String> offSession(Saved saved, String amount) {
    return Map.of(
        "amount",
        amount,
        "currency",
        "usd",
        "customer",
        saved.customer(),
        "payment_method",
        saved.method(),
        "confirm",
        "true",
        "off_session",
        "true");
  }

  private Reply hold(String amount) {
    Saved saved = save("published");
    Reply created =
        app.ok(
            "POST",
            PAYMENTS,
            SECRET,
            with(payment(amount), "customer", saved.customer(), "capture_method", "manual"));
    Reply confirmed =
        app.ok(
            "POST",
            PAYMENTS + "/" + created.at("id") + "/confirm",
            PUBLIC,
            Map.of("client_secret", created.at("client_secret"), "payment_method", saved.method()));
    assertThat(confirmed.at("status")).isEqualTo("requires_capture");
    assertThat(confirmed.json().keySet())
        .doesNotContain("amount_capturable", "amount_received", "latest_charge");
    return app.ok("GET", PAYMENTS + "/" + created.at("id"), SECRET, Map.of());
  }

  private Reply charge(Reply intent) {
    return app.ok("GET", "/v1/charges/" + intent.at("latest_charge"), SECRET, Map.of());
  }

  private static void assertPayment(
      Reply reply, String status, String received, String capturable) {
    assertThat(reply.at("status")).isEqualTo(status);
    assertThat(reply.at("amount_received")).isEqualTo(received);
    assertThat(reply.at("amount_capturable")).isEqualTo(capturable);
  }
}
