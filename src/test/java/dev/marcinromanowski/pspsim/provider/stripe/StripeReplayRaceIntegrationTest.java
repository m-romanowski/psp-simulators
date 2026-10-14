package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.TheRequestsUnderOneKey;
import dev.marcinromanowski.pspsim.TheSurfacesOfAnUnrecordedKey;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

@DirtiesContext
@Import(StripeReplayRaceIntegrationTest.Wiring.class)
class StripeReplayRaceIntegrationTest extends PostgresBackedTest {

  private static final AtomicReference<Runnable> ARMED = new AtomicReference<>();

  private static final AtomicBoolean NOT_RECORDED = new AtomicBoolean();

  private static final TestRunId RUN = TestRunId.of("stripe-replay-race-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");
  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51ConcurrentSimulator";
  private static final String KEY = "a-key-repeated-while-stored";

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
  void a_repeat_answered_while_the_first_request_stores_its_answer_is_numbered_after_it() {
    Map<String, String> hold = new LinkedHashMap<>();
    hold.put("amount", "1099");
    hold.put("currency", "pln");
    hold.put("capture_method", "manual");
    hold.put("payment_method", "pm_card_visa");
    hold.put("confirm", "true");
    AtomicReference<Integer> repeated = new AtomicReference<>();
    ARMED.set(() -> repeated.set(post(hold).statusCode()));

    int first = post(hold).statusCode();

    assertThat(first).isEqualTo(200);
    assertThat(repeated.get())
        .as("the repeat sent while the first request was storing its answer is answered")
        .isEqualTo(200);
    TheRequestsUnderOneKey.areNumberedAfterTheOneThatClaimedTheKey(
        TheRequestsUnderOneKey.recordedUnderTheColumn(
            jdbc, "stripe_idempotency_exchange", "idempotency_key", KEY, RUN.value()),
        2,
        Set.of(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY));
    TheRequestsUnderOneKey.isReadableOnTheInspector(http, "stripe", RUN.value());
  }

  @Test
  void a_key_whose_first_request_went_unrecorded_reads_back_as_such_on_every_surface() {
    Map<String, String> hold = new LinkedHashMap<>();
    hold.put("amount", "1099");
    hold.put("currency", "pln");
    hold.put("capture_method", "manual");
    hold.put("payment_method", "pm_card_visa");
    hold.put("confirm", "true");
    assertThat(postUnder(hold, "a-key-recorded-whole").statusCode()).isEqualTo(200);
    NOT_RECORDED.set(true);

    int unrecorded = post(hold).statusCode();
    HttpResponse<byte[]> retried = post(hold);

    assertThat(unrecorded).isEqualTo(500);
    assertThat(retried.statusCode()).isEqualTo(200);
    assertThat(retried.headers().firstValue("Idempotent-Replayed")).contains("true");
    TheSurfacesOfAnUnrecordedKey.showIt(http, "stripe", RUN.value(), KEY, "a-key-recorded-whole");
  }

  private HttpResponse<byte[]> post(Map<String, String> form) {
    return postUnder(form, KEY);
  }

  private HttpResponse<byte[]> postUnder(Map<String, String> form, String key) {
    try {
      return wire.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/payment_intents"))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .header("Authorization", "Bearer " + SECRET_KEY)
              .header("Idempotency-Key", key)
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

  @AfterEach
  void disarm() {
    ARMED.set(null);
    NOT_RECORDED.set(false);
  }

  private void reset() {
    http.post()
        .uri("/__simulator/stripe/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Wiring {

    @Bean
    @Primary
    StripeIdempotencyKeys repeatingWhileTheAnswerIsStored(
        @Qualifier("stripeIdempotencyKeys") StripeIdempotencyKeys stored) {
      return new RepeatingWhileTheAnswerIsStored(stored);
    }
  }

  private record RepeatingWhileTheAnswerIsStored(StripeIdempotencyKeys stored)
      implements StripeIdempotencyKeys {

    @Override
    public StripeIdempotencyClaim claim(
        TestRunId testRunId,
        String accountId,
        String key,
        String fingerprint,
        Instant now,
        Instant prunedBefore) {
      return stored.claim(testRunId, accountId, key, fingerprint, now, prunedBefore);
    }

    @Override
    public void completed(
        TestRunId testRunId, String accountId, String key, StripeStoredAnswer answer) {
      stored.completed(testRunId, accountId, key, answer);
      Runnable repeat = ARMED.getAndSet(null);
      if (repeat != null) {
        repeat.run();
      }
    }

    @Override
    public void released(TestRunId testRunId, String accountId, String key) {
      stored.released(testRunId, accountId, key);
    }

    @Override
    public String fingerprintOf(TestRunId testRunId, FingerprintedRequest request) {
      return stored.fingerprintOf(testRunId, request);
    }

    @Override
    public void observed(KeyedObservation observation) {
      if (NOT_RECORDED.getAndSet(false)) {
        throw new IllegalStateException("the exchange could not be recorded");
      }
      stored.observed(observation);
    }

    @Override
    public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
      return stored.exchanges(query);
    }

    @Override
    public int clear(TestRunId testRunId) {
      return stored.clear(testRunId);
    }

    @Override
    public int clearAll() {
      return stored.clearAll();
    }
  }
}
