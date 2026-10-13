package dev.marcinromanowski.pspsim.kernel.smoke.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckRequest;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalHttpCheckTransportTest {

  private HttpServer server;
  private final List<String> received = new ArrayList<>();
  private LocalHttpCheckTransport transport;

  @BeforeEach
  void listen() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          received.add(
              exchange.getRequestMethod()
                  + " "
                  + exchange.getRequestURI()
                  + " "
                  + exchange.getRequestHeaders().getFirst("X-Simulator-Test-Run-Id")
                  + " "
                  + body);
          byte[] answer = "{\"status\":1}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Idempotent-Replayed", "true");
          exchange.sendResponseHeaders(201, answer.length);
          exchange.getResponseBody().write(answer);
          exchange.close();
        });
    server.start();
    transport =
        new LocalHttpCheckTransport(
            HttpClient.newHttpClient(), () -> server.getAddress().getPort(), Duration.ofSeconds(5));
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void a_check_reaches_the_route_over_real_http_and_reads_the_status_headers_and_body_back() {
    CheckResponse answer =
        transport.send(
            new CheckRequest(
                "POST",
                "/api/light/server/1.0/createPaymentProcess",
                Map.of("X-Simulator-Test-Run-Id", "smoke"),
                "sum=120.50"));

    assertThat(answer.status()).isEqualTo(201);
    assertThat(answer.body()).isEqualTo("{\"status\":1}");
    assertThat(answer.header("Idempotent-Replayed")).isEqualTo("true");
    assertThat(received)
        .containsExactly("POST /api/light/server/1.0/createPaymentProcess smoke sum=120.50");
  }

  @Test
  void a_header_is_read_back_whatever_case_the_server_wrote_it_in() {
    CheckResponse answer = transport.send(CheckRequest.get("/anything"));

    assertThat(answer.header("idempotent-replayed")).isEqualTo("true");
    assertThat(answer.header("Idempotent-Replayed")).isEqualTo("true");
    assertThat(answer.header("Stripe-Signature")).isNull();
  }

  @Test
  void a_request_with_no_body_is_sent_with_none() {
    transport.send(CheckRequest.get("/read"));

    assertThat(received).containsExactly("GET /read null ");
  }

  @Test
  void a_thread_interrupted_while_it_waits_is_left_interrupted_for_whoever_asked_to_stop() {
    CheckRequest anything = CheckRequest.get("/anything");
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> transport.send(anything))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(LocalHttpCheckTransport.THE_ROUTE_DID_NOT_ANSWER);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void a_route_that_does_not_answer_is_named_rather_than_leaking_a_transport_error() {
    LocalHttpCheckTransport nothingIsListening =
        new LocalHttpCheckTransport(HttpClient.newHttpClient(), () -> 1, Duration.ofSeconds(1));

    CheckRequest anything = CheckRequest.get("/anything");

    assertThatThrownBy(() -> nothingIsListening.send(anything))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(LocalHttpCheckTransport.THE_ROUTE_DID_NOT_ANSWER);
  }
}
