package dev.marcinromanowski.pspsim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

public final class TheSurfacesOfAnUnrecordedKey {

  private record Answered(int status, String body) {}

  private TheSurfacesOfAnUnrecordedKey() {}

  public static void showIt(
      RestClient http, String provider, String testRunId, String unrecorded, String recorded) {
    Answered control =
        read(http, "/__simulator/" + provider + "/idempotency?testRunId=" + testRunId);
    Answered screen =
        read(http, "/ui/idempotency?provider=" + provider + "&testRunId=" + testRunId);
    Answered timeline = read(http, "/ui/timeline?provider=" + provider + "&testRunId=" + testRunId);

    for (Answered surface : List.of(control, screen, timeline)) {
      assertThat(surface.status()).as(surface.body()).isEqualTo(200);
    }
    assertThat(control.body())
        .contains(unrecorded)
        .contains(recorded)
        .contains(KeyedExchange.THE_REQUEST_THAT_RAN_WAS_NOT_RECORDED);
    assertThat(HtmlUtils.htmlUnescape(screen.body()))
        .contains(unrecorded)
        .contains(recorded)
        .contains(KeyedExchange.THE_REQUEST_THAT_RAN_WAS_NOT_RECORDED);
  }

  private static Answered read(RestClient http, String path) {
    return http.get()
        .uri(path)
        .exchange(
            (request, response) ->
                new Answered(
                    response.getStatusCode().value(),
                    new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
  }
}
