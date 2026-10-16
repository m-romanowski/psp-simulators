package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.AtOnce;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.TheRequestsUnderOneKey;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class StripeConcurrentExchangeIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-concurrent-exchange-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");
  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51ConcurrentSimulator";
  private static final String KEY = "a-key-sent-at-once";
  private static final int CONCURRENT = 16;
  private static final int ROUNDS = 3;

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;

  private RestClient http;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(
        List.of(
            Map.of(
                "accountId",
                MERCHANT,
                "configuration",
                Map.of(
                    StripeAccount.SECRET_KEY_PROPERTY,
                    SECRET_KEY,
                    StripeCaptureWindow.SETTING,
                    "P3D"))));
  }

  @AfterEach
  void unconfigureTheAccount() {
    reset();
    configure(List.of());
  }

  @Test
  void intents_sent_at_once_under_one_key_are_numbered_after_the_one_that_claimed_it()
      throws Exception {
    Map<String, String> hold = new LinkedHashMap<>();
    hold.put("amount", "1099");
    hold.put("currency", "pln");
    hold.put("capture_method", "manual");
    hold.put("payment_method", "pm_card_visa");
    hold.put("confirm", "true");

    List<Integer> statuses = new ArrayList<>();
    for (int round = 0; round < ROUNDS; round++) {
      statuses.addAll(AtOnce.sent(CONCURRENT, () -> post(hold).statusCode()));
    }

    assertThat(statuses).hasSize(CONCURRENT * ROUNDS).containsOnly(200, 409);
    TheRequestsUnderOneKey.areNumberedAfterTheOneThatClaimedTheKey(
        TheRequestsUnderOneKey.recordedUnderTheColumn(
            jdbc, "stripe_idempotency_exchange", "idempotency_key", KEY, RUN.value()),
        CONCURRENT * ROUNDS,
        Set.of(
            ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY,
            ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD));
    TheRequestsUnderOneKey.isReadableOnTheInspector(http, "stripe", RUN.value());
  }

  private HttpResponse<byte[]> post(Map<String, String> form) {
    try {
      return wire.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/payment_intents"))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Authorization", "Bearer " + SECRET_KEY)
              .header("Idempotency-Key", KEY)
              .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
              .build(),
          HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (Exception unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private void configure(List<Map<String, Object>> accounts) {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                accounts.isEmpty() ? Map.of() : Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                accounts,
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/stripe/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }
}
