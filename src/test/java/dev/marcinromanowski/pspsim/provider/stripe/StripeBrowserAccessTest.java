package dev.marcinromanowski.pspsim.provider.stripe;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class StripeBrowserAccessTest {

  private final StripeBrowserAccess filter = new StripeBrowserAccess(StripeWireFaults.ROUTES);

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/v1/payment_intents",
        "/v1/payment_intents/pi_1/confirm",
        "/v1/setup_intents/seti_1",
        "/v1/payment_methods",
        "/v1/customers/cus_1",
        "/v1/charges/ch_1",
        "/v1/refunds",
        "/v1/ephemeral_keys"
      })
  void every_answer_of_a_route_of_stripe_carries_the_recorded_browser_headers(String path)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("*");
    assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    assertThat(response.getHeader("Access-Control-Allow-Methods"))
        .isEqualTo("GET, HEAD, PUT, PATCH, POST, DELETE");
    assertThat(response.getHeader("Access-Control-Expose-Headers"))
        .isEqualTo(
            "Request-Id, Stripe-Manage-Version, Stripe-Should-Retry,"
                + " X-Stripe-External-Auth-Required, X-Stripe-Privileged-Session-Required");
    assertThat(response.getHeader("Access-Control-Max-Age")).isEqualTo("300");
    assertThat(response.getHeader("Vary")).isEqualTo("Origin");
    assertThat(answerOf(response).headers())
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Access-Control-Allow-Headers"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/v1/oauth/token",
        "/v1/acquiring/payments",
        "/v1",
        "/v1/payment_intentsx",
        "/ui/audit",
        "/__simulator/stripe/config",
        "/"
      })
  void a_route_stripe_does_not_answer_is_handed_on_untouched_even_a_preflight(String path)
      throws Exception {
    MockHttpServletRequest request = aPreflightTo(path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(answerOf(response).headers()).isEmpty();
  }

  @Test
  void a_preflight_is_answered_here_with_the_headers_it_asks_for_and_goes_no_further()
      throws Exception {
    MockHttpServletRequest request = aPreflightTo("/v1/payment_intents/pi_1/confirm");
    request.addHeader("Access-Control-Request-Headers", " authorization,content-type ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentLength()).isZero();
    assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("*");
    assertThat(response.getHeader("Access-Control-Allow-Headers"))
        .isEqualTo("authorization,content-type");
  }

  @Test
  void a_preflight_asking_for_no_header_is_allowed_none() throws Exception {
    MockHttpServletRequest asksNone = aPreflightTo("/v1/setup_intents/seti_1/confirm");
    MockHttpServletRequest asksBlank = aPreflightTo("/v1/setup_intents/seti_1/confirm");
    asksBlank.addHeader("Access-Control-Request-Headers", " ");
    MockHttpServletResponse none = new MockHttpServletResponse();
    MockHttpServletResponse blank = new MockHttpServletResponse();

    filter.doFilter(asksNone, none, new MockFilterChain());
    filter.doFilter(asksBlank, blank, new MockFilterChain());

    for (MockHttpServletResponse answered : List.of(none, blank)) {
      assertThat(answered.getStatus()).isEqualTo(200);
      assertThat(answerOf(answered).headers())
          .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Access-Control-Allow-Headers"));
    }
  }

  @Test
  void an_options_request_that_is_no_preflight_reaches_the_routes() throws Exception {
    MockHttpServletRequest noOrigin = new MockHttpServletRequest("OPTIONS", "/v1/refunds");
    noOrigin.addHeader("Access-Control-Request-Method", "POST");
    noOrigin.addHeader("Access-Control-Request-Headers", "authorization");
    MockHttpServletRequest noMethod = new MockHttpServletRequest("OPTIONS", "/v1/refunds");
    noMethod.addHeader("Origin", "https://shop.example");
    MockHttpServletRequest aPost = new MockHttpServletRequest("POST", "/v1/refunds");
    aPost.addHeader("Origin", "https://shop.example");
    aPost.addHeader("Access-Control-Request-Method", "POST");
    aPost.addHeader("Access-Control-Request-Headers", "authorization");

    for (MockHttpServletRequest request : List.of(noOrigin, noMethod, aPost)) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(chain.getRequest()).isSameAs(request);
      assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("*");
      assertThat(answerOf(response).headers())
          .doesNotHave(aHeaderOfThisNameUnderAnySpelling("Access-Control-Allow-Headers"));
    }
  }

  @Test
  void a_preflight_is_recognised_under_any_case_of_its_method() {
    MockHttpServletRequest lower = aPreflightTo("/v1/refunds");
    lower.setMethod("options");

    assertThat(StripeBrowserAccess.aPreflight(lower)).isTrue();
  }

  private static Answer answerOf(MockHttpServletResponse response) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (String name : response.getHeaderNames()) {
      headers.put(name, response.getHeaders(name));
    }
    return new Answer(headers);
  }

  private record Answer(Map<String, List<String>> headers) {}

  private static MockHttpServletRequest aPreflightTo(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
    request.addHeader("Origin", "https://shop.example");
    request.addHeader("Access-Control-Request-Method", "POST");
    return request;
  }
}
