package dev.marcinromanowski.pspsim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NothingConfiguredOutlivesATestIntegrationTest extends PostgresBackedTest {

  private static final String THE_URL_BOTH_RUNS_BIND =
      "https://outlived.provider.example/merchant-gateway/1.0/payments";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
  }

  @Test
  @Order(1)
  void a_test_binds_a_route_alias_and_ends_without_releasing_it() {
    assertThat(bindingTheUrlIn("outlived-first")).isEqualTo(200);
  }

  @Test
  @Order(2)
  void a_later_test_in_another_run_binds_the_same_url_because_the_first_left_nothing_bound() {
    assertThat(bindingTheUrlIn("outlived-second")).isEqualTo(200);
  }

  private int bindingTheUrlIn(String testRunId) {
    return http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                testRunId,
                "routeAliases",
                List.of(Map.of("from", THE_URL_BOTH_RUNS_BIND, "to", CREATE))))
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toBodilessEntity()
        .getStatusCode()
        .value();
  }
}
