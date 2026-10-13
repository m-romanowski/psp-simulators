package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TheStripeCallsTest {

  private static final Duration A_READ_TIMEOUT = Duration.ofMillis(300);

  private final CountDownLatch theTestIsOver = new CountDownLatch(1);
  private HttpServer stripe;

  @AfterEach
  void stopStripe() {
    theTestIsOver.countDown();
    if (stripe != null) {
      stripe.stop(0);
    }
  }

  @Test
  @DisplayName("a Stripe call the simulator never answers gives up after the read timeout")
  void aStripeCallNeverAnsweredGivesUpAfterTheReadTimeout() throws IOException {
    Payments payments = new StripePayments(settingsFor(aStripeThatNeverAnswers()));
    StoryRun run =
        new StoryRun(
            "shop-1", Story.A_CAPTURE_THAT_GOES_THROUGH, Instant.parse("2026-09-15T10:00:00Z"));

    assertTimeoutPreemptively(
        A_READ_TIMEOUT.multipliedBy(10),
        () ->
            assertThatThrownBy(
                    () -> payments.placeAHold(run, "order-shop-1", Product.THE_ONE_WE_SELL))
                .isInstanceOf(ThePaymentProviderFailed.class));
  }

  @Test
  @DisplayName(
      "a payment retried after Stripe never answered the first asks under the same idempotency key,"
          + " and the order is placed once")
  void aPaymentRetriedAfterATimeoutReusesItsIdempotencyKey() throws IOException {
    List<String> keys = new CopyOnWriteArrayList<>();
    StoryRuns runs = new StoryRuns();
    Checkout checkout =
        new Checkout(new StripePayments(settingsFor(aStripeThatAnswersOnlyTheRetry(keys))), runs);
    StoryRun run =
        new StoryRun(
            "shop-1", Story.A_CAPTURE_THAT_GOES_THROUGH, Instant.parse("2026-09-15T10:00:00Z"));
    runs.keep(run);

    assertThatThrownBy(() -> checkout.buy(run.testRunId()))
        .isInstanceOf(ThePaymentProviderFailed.class);
    Order order = checkout.buy(run.testRunId());

    assertThat(order.paymentIntentId()).isEqualTo("pi_1");
    assertThat(keys).hasSize(2).doesNotContainNull().containsOnly(keys.getFirst());
    assertThat(keys.getFirst()).contains(order.orderId());
  }

  private HttpServer aStripeThatAnswersOnlyTheRetry(List<String> keys) throws IOException {
    stripe = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    stripe.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    stripe.createContext(
        "/",
        exchange -> {
          keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
          if (keys.size() == 1) {
            try {
              theTestIsOver.await(1, TimeUnit.MINUTES);
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
            exchange.close();
            return;
          }
          byte[] created =
              "{\"id\":\"pi_1\",\"object\":\"payment_intent\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, created.length);
          exchange.getResponseBody().write(created);
          exchange.close();
        });
    stripe.start();
    return stripe;
  }

  private HttpServer aStripeThatNeverAnswers() throws IOException {
    stripe = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    stripe.createContext(
        "/",
        exchange -> {
          try {
            theTestIsOver.await(1, TimeUnit.MINUTES);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });
    stripe.start();
    return stripe;
  }

  private static ShopSettings settingsFor(HttpServer stripe) {
    String address = "http://127.0.0.1:" + stripe.getAddress().getPort();
    return new ShopSettings(address, address, address, Duration.ofSeconds(1), A_READ_TIMEOUT);
  }
}
