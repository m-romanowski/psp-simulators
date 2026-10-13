package dev.marcinromanowski.pspsim.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class BareHostNameIntegrationTest extends PostgresBackedTest {

  private static final String LOCATION = "Location";

  @LocalServerPort private int port;

  private HttpResponse<Void> answerTo(String path) {
    try (HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
          HttpResponse.BodyHandlers.discarding());
    } catch (IOException unreachable) {
      throw new IllegalStateException("the simulator did not answer " + path, unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while asking for " + path, interrupted);
    }
  }

  @Test
  void a_tester_who_types_the_host_name_and_nothing_else_lands_on_a_screen() {
    HttpResponse<Void> answer = answerTo(BareHostNameConfiguration.THE_ADDRESS_A_TESTER_TYPES);

    assertThat(answer.statusCode()).isEqualTo(302);
    assertThat(answer.headers().firstValue(LOCATION).orElseThrow())
        .endsWith(BareHostNameConfiguration.THE_SCREEN_IT_OPENS);
  }

  @Test
  void the_provider_and_test_run_a_bare_address_names_are_carried_to_the_screen() {
    HttpResponse<Void> answer = answerTo("/?provider=grow&testRunId=a-typed-run");

    assertThat(answer.statusCode()).isEqualTo(302);
    assertThat(answer.headers().firstValue(LOCATION).orElseThrow())
        .endsWith(
            BareHostNameConfiguration.THE_SCREEN_IT_OPENS + "?provider=grow&testRunId=a-typed-run");
  }
}
