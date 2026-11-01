package dev.marcinromanowski.pspsim.journey;

import static dev.marcinromanowski.pspsim.documentation.TrailWithholdingConvention.assertEverySurfaceWithholds;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.PAYMENTS;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.PUBLIC;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.RUN;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SETUPS;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SIGNING_SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.payment;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.with;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.net.Webhook;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveries;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class StripeSeamIntegrationTest extends PostgresBackedTest {
  @LocalServerPort private int port;
  @Autowired private WebhookDeliveries deliveries;
  private StripeJourneyClient operator;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configure() {
    operator = new StripeJourneyClient(port);
  }

  @AfterEach
  void close() {
    operator.close();
  }

  @Test
  void replay_does_not_restore_a_customer_expansion_for_a_publishable_key() throws Exception {
    String customer =
        operator
            .ok("POST", "/v1/customers", SECRET, Map.of("email", "private@merchant.example"))
            .at("id");
    var setup =
        operator.ok(
            "POST",
            SETUPS,
            SECRET,
            Map.of("customer", customer, "allowed_payment_method_types[]", "card"));
    String method =
        operator
            .ok(
                "POST",
                SETUPS + "/" + setup.at("id") + "/confirm",
                PUBLIC,
                Map.of(
                    "client_secret", setup.at("client_secret"), "payment_method", "pm_card_visa"))
            .at("payment_method");
    var created =
        operator.ok("POST", PAYMENTS, SECRET, with(payment("1000"), "customer", customer));
    Map<String, String> params =
        Map.of(
            "client_secret",
            created.at("client_secret"),
            "payment_method",
            method,
            "expand[]",
            "payment_method.customer");
    String path = PAYMENTS + "/" + created.at("id") + "/confirm";
    var first =
        send("POST", path, SECRET, params, Map.of("Idempotency-Key", "cross-key-expansion"));
    var replay =
        send("POST", path, PUBLIC, params, Map.of("Idempotency-Key", "cross-key-expansion"));
    var fresh =
        send(
            "GET",
            PAYMENTS + "/" + created.at("id"),
            PUBLIC,
            Map.of(
                "client_secret",
                created.at("client_secret"),
                "expand[]",
                "payment_method.customer"),
            Map.of());
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(fresh.statusCode()).isEqualTo(200);
    assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
    assertThat(object(replay).getAsJsonObject("payment_method").get("customer").isJsonPrimitive())
        .as("publishable replay keeps the fresh publishable expansion boundary")
        .isTrue();
  }

  @Test
  void rejected_intent_creation_does_not_leave_a_minted_method() throws Exception {
    operator.control(
        "POST",
        "/scenarios",
        Map.of(
            "testRunId",
            RUN,
            "priority",
            100,
            "match",
            Map.of("operation", "POST /v1/payment_intents", "amount", "1000"),
            "action",
            Map.of("type", "TERMINAL_FAILURE", "providerCode", "insufficient_funds"),
            "story",
            "r1-create",
            "keyedOn",
            "TEST_RUN",
            "whenState",
            "Started",
            "thenState",
            "Consumed"));
    Map<String, String> params =
        with(
            cardData(),
            "amount",
            "1000",
            "currency",
            "usd",
            "confirm",
            "true",
            "customer",
            "cus_" + "x".repeat(130),
            "allowed_payment_method_types[]",
            "card");
    var response =
        send("POST", PAYMENTS, SECRET, params, Map.of("Idempotency-Key", "invalid-customer"));
    var methods = send("GET", "/v1/payment_methods", SECRET, Map.of(), Map.of());
    var events =
        send("GET", "/__simulator/stripe/webhooks", SECRET, Map.of("testRunId", RUN), Map.of());
    assertThat(events.statusCode()).isEqualTo(200);
    assertThat(JsonParser.parseString(events.body()).getAsJsonArray()).isEmpty();
    var retry =
        send(
            "POST",
            PAYMENTS,
            SECRET,
            with(
                cardData(),
                "amount",
                "1000",
                "currency",
                "usd",
                "confirm",
                "true",
                "allowed_payment_method_types[]",
                "card"),
            Map.of("Idempotency-Key", "invalid-customer"));
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(methods.statusCode()).isEqualTo(200);
    assertThat(object(methods).getAsJsonArray("data"))
        .as("no method survives an invalid intent create")
        .isEmpty();
    assertThat(retry.statusCode())
        .as("the invalid request must not consume the first scenario story step")
        .isEqualTo(402);
  }

  @Test
  void every_recorded_displayed_and_delivered_surface_withholds_card_numbers() throws Exception {
    operator.rule("POST /v1/payment_intents", "1200", "insufficient_funds");
    Map<String, String> form =
        with(
            cardData(),
            "payment_method_data[metadata][order]",
            "4242424242424242",
            "payment_method_data[metadata][order_4242424242424242]",
            "merchant-reference",
            "payment_method_data[billing_details][name]",
            "4000000000009995",
            "amount",
            "1200",
            "currency",
            "usd",
            "allowed_payment_method_types[]",
            "card",
            "confirm",
            "true");
    Map<String, String> keyed = Map.of("Idempotency-Key", "pan-surface-flow");
    var declined = send("POST", PAYMENTS, SECRET, form, keyed);
    assertThat(declined.statusCode()).isEqualTo(402);
    assertThat(declined.body()).contains("4242424242424242", "4000000000009995");
    String id =
        object(declined)
            .getAsJsonObject("error")
            .getAsJsonObject("payment_intent")
            .get("id")
            .getAsString();
    assertThat(send("POST", PAYMENTS, SECRET, form, keyed).body()).isEqualTo(declined.body());
    operator.control("POST", "/webhooks", Map.of("testRunId", RUN, "providerObjectId", id));
    var jobs =
        send("GET", "/__simulator/stripe/webhooks", SECRET, Map.of("testRunId", RUN), Map.of());
    assertThat(jobs.statusCode()).isEqualTo(200);
    var job = JsonParser.parseString(jobs.body()).getAsJsonArray().get(0).getAsJsonObject();
    String jobId = job.get("webhookJobId").getAsString();
    Map<String, String> surfaces = new LinkedHashMap<>();
    surfaces.put("delivery history and attempts", jobs.body());
    for (String route :
        List.of(
            "audit",
            "idempotency",
            "objects",
            "state/" + id,
            "run-bundle",
            "repro-bundle",
            "reads")) {
      var response =
          send(
              "GET",
              "/__simulator/stripe/" + route,
              SECRET,
              Map.of("testRunId", RUN, "key", id),
              Map.of());
      assertThat(response.statusCode()).as(route).isEqualTo(200);
      surfaces.put("control " + route, response.body());
    }
    for (String route :
        List.of(
            "audit",
            "idempotency",
            "objects",
            "timeline",
            "run-bundle",
            "repro-bundle",
            "webhooks",
            "payment-methods",
            "reads")) {
      var response =
          send(
              "GET",
              "/ui/" + route,
              SECRET,
              Map.of(
                  "provider",
                  "stripe",
                  "testRunId",
                  RUN,
                  "key",
                  id,
                  "open",
                  jobId,
                  "values",
                  "show"),
              Map.of());
      assertThat(response.statusCode()).as(route).isEqualTo(200);
      surfaces.put("UI " + route, response.body());
    }
    for (var record : JsonParser.parseString(surfaces.get("control audit")).getAsJsonArray()) {
      String auditId = record.getAsJsonObject().get("auditId").getAsString();
      for (String route : List.of("audit", "timeline")) {
        var pane =
            send(
                "GET",
                "/ui/" + route,
                SECRET,
                Map.of(
                    "provider",
                    "stripe",
                    "testRunId",
                    RUN,
                    "key",
                    id,
                    "open",
                    auditId,
                    "values",
                    "show"),
                Map.of());
        assertThat(pane.statusCode()).as(route + " " + auditId).isEqualTo(200);
        if (record
            .getAsJsonObject()
            .getAsJsonObject("sanitizedBody")
            .has("data.object.last_payment_error.payment_method.metadata.order")) {
          assertThat(pane.body())
              .contains("last_payment_error.payment_method.metadata.order", "[redacted]");
        }
        surfaces.put("UI " + route + " " + auditId, pane.body());
      }
    }
    var delivered = operator.endpoint.received().getFirst();
    surfaces.put("delivered webhook", delivered.body());
    var stored =
        deliveries.find(ProviderId.of("stripe"), TestRunId.of(RUN), jobId).orElseThrow().payload();
    String persisted = new String(stored.body(), UTF_8);
    assertThat(persisted).isEqualTo(delivered.body());
    surfaces.put("persisted signed webhook bytes", persisted);
    surfaces.put("persisted sanitized webhook view", stored.sanitizedBody().toString());
    assertThat(stored.sanitizedBody().get("data.object.client_secret"))
        .containsExactly("[redacted]");

    String signature =
        delivered.headers().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase("Stripe-Signature"))
            .findFirst()
            .orElseThrow()
            .getValue()
            .getFirst();
    assertThat(Webhook.constructEvent(delivered.body(), signature, SIGNING_SECRET, 0L).getType())
        .isEqualTo("payment_intent.payment_failed");
    operator.advance("PT1S");
    operator.control(
        "POST", "/webhooks", Map.of("testRunId", RUN, "operation", "RETRY", "webhookJobId", jobId));
    var retried = operator.endpoint.received().getLast();
    assertThat(retried.body()).isEqualTo(delivered.body());
    assertThat(retried.header("Stripe-Signature"))
        .isNotEqualTo(delivered.header("Stripe-Signature"));
    assertThat(
            Webhook.constructEvent(
                    retried.body(),
                    retried.header("Stripe-Signature").getFirst(),
                    SIGNING_SECRET,
                    0L)
                .getId())
        .isEqualTo(Webhook.constructEvent(delivered.body(), signature, SIGNING_SECRET, 0L).getId());
    surfaces.put("retried webhook", retried.body());
    assertEverySurfaceWithholds(
        surfaces, "4242424242424242", "4000000000009995", "4242 4242 4242 4242");
    var event = JsonParser.parseString(delivered.body()).getAsJsonObject();
    var method =
        event
            .getAsJsonObject("data")
            .getAsJsonObject("object")
            .getAsJsonObject("last_payment_error")
            .getAsJsonObject("payment_method");
    assertThat(method.getAsJsonObject("metadata").get("order").getAsString())
        .isEqualTo("[redacted]");
    assertThat(method.getAsJsonObject("billing_details").get("name").getAsString())
        .isEqualTo("[redacted]");
    assertThat(method.getAsJsonObject("card").get("last4").getAsString()).isEqualTo("4242");
    var apiRead =
        send(
            "GET",
            "/v1/payment_methods/" + method.get("id").getAsString(),
            SECRET,
            Map.of(),
            Map.of());
    assertThat(apiRead.statusCode()).isEqualTo(200);
    assertThat(apiRead.body()).contains("4242424242424242", "4000000000009995");
  }

  private static Map<String, String> cardData() {
    return Map.of(
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
  }

  private HttpResponse<String> send(
      String method,
      String path,
      String key,
      Map<String, String> params,
      Map<String, String> headers)
      throws Exception {
    List<String> values = new ArrayList<>();
    params.forEach(
        (name, value) ->
            values.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    String encoded = String.join("&", values);
    String uri =
        "http://localhost:"
            + port
            + path
            + ("GET".equals(method) && !encoded.isEmpty() ? "?" + encoded : "");
    var request = HttpRequest.newBuilder(URI.create(uri)).header("Authorization", "Bearer " + key);
    headers.forEach(request::header);
    if ("GET".equals(method)) {
      request.GET();
    } else {
      request
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(encoded));
    }
    return wire.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private static JsonObject object(HttpResponse<String> response) {
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }
}
