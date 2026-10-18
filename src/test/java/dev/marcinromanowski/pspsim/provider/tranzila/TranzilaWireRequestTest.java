package dev.marcinromanowski.pspsim.provider.tranzila;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.routing.InboundRoute;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;

class TranzilaWireRequestTest {

  @Test
  void carries_the_method_the_route_every_header_and_the_body_the_backend_sent() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", TranzilaApiController.CREATE);
    request.addHeader("Accept", "application/json");
    request.addHeader("Accept", "text/plain");
    request.addHeader(TranzilaFields.APP_KEY, "key");
    request.setContent("{\"terminal_name\":\"t\"}".getBytes(UTF_8));

    TranzilaApiCall call = TranzilaWireRequest.callOf(request);

    assertThat(call).isNotNull();
    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path()).isEqualTo("/v1/transaction/credit_card/create");
    assertThat(call.headers()).containsEntry("Accept", List.of("application/json", "text/plain"));
    assertThat(call.header("x-tranzila-api-app-key")).contains("key");
    assertThat(call.rawBody()).isEqualTo("{\"terminal_name\":\"t\"}");
    assertThat(call.body().orElseThrow().text("terminal_name")).contains("t");
  }

  @Test
  void a_page_call_carries_the_method_the_route_every_header_and_every_parameter_sent() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/tranzila/directng/myterminal/iframenew.php");
    request.addHeader("Accept", "text/html");
    request.addParameter("sum", "10.53");
    request.addParameter("orderid", "A-1", "A-2");
    request.setAttribute(
        InboundRoute.ATTRIBUTE,
        InboundRoute.notAliased("/tranzila/directng/myterminal/iframenew.php?aliased"));

    TranzilaPageCall call = TranzilaWireRequest.pageCallOf(request);

    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path()).isEqualTo("/tranzila/directng/myterminal/iframenew.php?aliased");
    assertThat(call.headers()).containsExactly(Map.entry("Accept", List.of("text/html")));
    assertThat(call.parameters())
        .containsExactly(
            Map.entry("sum", List.of("10.53")), Map.entry("orderid", List.of("A-1", "A-2")));
  }

  @Test
  void a_body_that_cannot_be_read_is_carried_as_none() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", TranzilaApiController.CREATE) {
          @Override
          public jakarta.servlet.ServletInputStream getInputStream() {
            return new DelegatingServletInputStream(new ByteArrayInputStream(new byte[0])) {
              @Override
              public byte[] readAllBytes() throws IOException {
                throw new IOException("the connection was reset");
              }
            };
          }
        };

    TranzilaApiCall call = TranzilaWireRequest.callOf(request);

    assertThat(call).isNotNull();
    assertThat(call.rawBody()).isEmpty();
  }

  @Test
  void a_header_is_found_by_any_case_and_a_blank_value_is_skipped() {
    TranzilaApiCall call =
        TranzilaApiCall.of(
            "POST",
            "/v1/transaction/credit_card/create",
            Map.of("X-Tranzila-Api-Nonce", List.of(" ", "n-1")),
            "");

    assertThat(call.header("x-tranzila-api-nonce")).contains("n-1");
    assertThat(call.header("X-tranzila-api-access-token")).isEmpty();
    assertThat(call.body()).isEmpty();
  }

  @Test
  void the_headers_a_call_carries_cannot_be_changed_after_it_was_made() {
    Map<String, List<String>> sent = new LinkedHashMap<>();
    sent.put("A", new ArrayList<>(List.of("1")));
    TranzilaApiCall call = TranzilaApiCall.of("POST", "/", sent, "");
    sent.get("A").add("2");
    sent.put("B", List.of("3"));

    assertThat(call.headers()).containsExactly(Map.entry("A", List.of("1")));
  }
}
