package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;

class StripeWireRequestTest {

  private static final String URLENCODED = "application/x-www-form-urlencoded";

  private static final String CREATE = "/v1/payment_intents";

  @Test
  void carries_the_method_and_the_route_the_call_arrived_on() {
    StripeApiCall call = StripeWireRequest.callOf(posted("amount=1099"));

    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path()).isEqualTo(CREATE);
  }

  @Test
  void carries_every_header_the_merchant_sent_including_one_sent_twice() {
    MockHttpServletRequest request = posted("amount=1099");
    request.addHeader("Authorization", "Bearer sk_test_1");
    request.addHeader("Accept", "application/json");
    request.addHeader("Accept", "text/plain");

    assertThat(StripeWireRequest.callOf(request).headers())
        .containsEntry("Authorization", List.of("Bearer sk_test_1"))
        .containsEntry("Accept", List.of("application/json", "text/plain"));
  }

  @Test
  void reads_the_form_encoded_body_every_published_curl_sample_posts() {
    StripeApiCall call =
        StripeWireRequest.callOf(
            posted("amount=1099&currency=pln&capture_method=manual&receipt_email=a%40b.example"));

    assertThat(call.form())
        .containsEntry("amount", List.of("1099"))
        .containsEntry("currency", List.of("pln"))
        .containsEntry("capture_method", List.of("manual"))
        .containsEntry("receipt_email", List.of("a@b.example"));
  }

  @Test
  void keeps_every_value_of_the_bracketed_parameter_the_vendor_spells_as_a_list() {
    StripeApiCall call =
        StripeWireRequest.callOf(posted("expand%5B%5D=customer&expand%5B%5D=payment_method"));

    assertThat(call.form()).containsEntry("expand[]", List.of("customer", "payment_method"));
  }

  @Test
  void reads_a_field_sent_with_no_value_and_one_sent_with_no_equals_sign_at_all() {
    StripeApiCall call = StripeWireRequest.callOf(posted("customer=&off_session&&amount=1099"));

    assertThat(call.form())
        .containsEntry("customer", List.of(""))
        .containsEntry("off_session", List.of(""))
        .containsEntry("amount", List.of("1099"));
  }

  @Test
  void reads_a_pair_whose_name_the_merchant_left_empty_as_a_field_with_no_name() {
    StripeApiCall call = StripeWireRequest.callOf(posted("=orphan&amount=1099"));

    assertThat(call.form())
        .containsEntry("", List.of("orphan"))
        .containsEntry("amount", List.of("1099"));
  }

  @Test
  void reads_no_field_from_a_body_sent_under_a_content_type_the_vendor_never_publishes() {
    MockHttpServletRequest request = posted("amount=1099");
    request.setContentType("application/json");

    assertThat(StripeWireRequest.callOf(request).form()).isEmpty();
  }

  @Test
  void reads_the_body_of_a_request_that_named_its_charset_beside_the_encoding() {
    MockHttpServletRequest request = posted("amount=1099");
    request.setContentType(URLENCODED + "; charset=UTF-8");

    assertThat(StripeWireRequest.callOf(request).form()).containsEntry("amount", List.of("1099"));
  }

  @Test
  void reads_no_field_from_a_request_that_declared_no_content_type() {
    MockHttpServletRequest request = posted("amount=1099");
    request.setContentType(null);

    assertThat(StripeWireRequest.callOf(request).form()).isEmpty();
  }

  @Test
  void reads_no_field_from_a_body_that_cannot_be_percent_decoded() {
    assertThat(StripeWireRequest.callOf(posted("amount=%zz")).form()).isEmpty();
  }

  @Test
  void reads_no_field_from_a_body_the_transport_never_finished_delivering() {
    assertThat(StripeWireRequest.callOf(unreadable()).form()).isEmpty();
  }

  @Test
  void carries_the_filters_a_list_call_sent_in_the_query_rather_than_in_a_body() {
    StripeApiCall call = StripeWireRequest.callOf(listing("customer=cus_1&type=card"));

    assertThat(call.query())
        .containsEntry("customer", List.of("cus_1"))
        .containsEntry("type", List.of("card"));
    assertThat(call.form()).isEmpty();
  }

  @Test
  void a_list_call_with_no_query_at_all_carries_no_filters_rather_than_a_blank_one() {
    assertThat(StripeWireRequest.callOf(listing(null)).query()).isEmpty();
    assertThat(StripeWireRequest.callOf(listing("")).query()).isEmpty();
  }

  @Test
  void an_expansion_a_client_asked_for_twice_is_carried_twice_rather_than_collapsed() {
    StripeApiCall call =
        StripeWireRequest.callOf(listing("expand%5B%5D=customer&expand%5B%5D=customer"));

    assertThat(call.query()).containsEntry("expand[]", List.of("customer", "customer"));
  }

  private static MockHttpServletRequest listing(@Nullable String query) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payment_methods");
    request.setQueryString(query);
    return request;
  }

  private static MockHttpServletRequest posted(String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", CREATE);
    request.setContentType(URLENCODED);
    request.setContent(body.getBytes(UTF_8));
    return request;
  }

  private static MockHttpServletRequest unreadable() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", CREATE) {

          @Override
          public DelegatingServletInputStream getInputStream() {
            return new DelegatingServletInputStream(new ByteArrayInputStream(new byte[0])) {

              @Override
              public byte[] readAllBytes() throws IOException {
                throw new IOException("the merchant stopped writing");
              }
            };
          }
        };
    request.setContentType(URLENCODED);
    return request;
  }
}
