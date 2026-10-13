package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;

class AzericardWireRequestTest {

  private static final String URLENCODED = "application/x-www-form-urlencoded";

  @Test
  void carries_the_method_and_the_route_the_call_arrived_on() {
    AzericardApiCall call = AzericardWireRequest.callOf(posted("TERMINAL=77777777"));

    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path()).isEqualTo("/cgi-bin/cgi_link");
  }

  @Test
  void carries_every_header_the_merchant_sent_including_one_sent_twice() {
    MockHttpServletRequest request = posted("TERMINAL=77777777");
    request.addHeader("Accept", "application/xml");
    request.addHeader("Accept", "text/html");

    assertThat(AzericardWireRequest.callOf(request).headers())
        .containsEntry("Accept", List.of("application/xml", "text/html"));
  }

  @Test
  void reads_the_form_encoded_body_the_vendor_publishes_as_the_request_encoding() {
    AzericardApiCall call =
        AzericardWireRequest.callOf(
            posted("TERMINAL=77777777&TRTYPE=0&AMOUNT=11.48&DESC=IT+Books.+Qty%3A+2"));

    assertThat(call.form())
        .containsEntry("TERMINAL", List.of("77777777"))
        .containsEntry("TRTYPE", List.of("0"))
        .containsEntry("AMOUNT", List.of("11.48"))
        .containsEntry("DESC", List.of("IT Books. Qty: 2"));
  }

  @Test
  void keeps_every_value_of_a_field_the_merchant_sent_twice() {
    AzericardApiCall call = AzericardWireRequest.callOf(posted("ORDER=1&ORDER=2"));

    assertThat(call.form()).containsEntry("ORDER", List.of("1", "2"));
  }

  @Test
  void reads_a_field_sent_with_no_value_and_one_sent_with_no_equals_sign_at_all() {
    AzericardApiCall call = AzericardWireRequest.callOf(posted("APPROVAL=&RRN&&ORDER=1"));

    assertThat(call.form())
        .containsEntry("APPROVAL", List.of(""))
        .containsEntry("RRN", List.of(""))
        .containsEntry("ORDER", List.of("1"));
  }

  @Test
  void reads_a_pair_whose_name_the_merchant_left_empty_as_a_field_with_no_name() {
    AzericardApiCall call = AzericardWireRequest.callOf(posted("=orphan&ORDER=1"));

    assertThat(call.form())
        .containsEntry("", List.of("orphan"))
        .containsEntry("ORDER", List.of("1"));
  }

  @Test
  void reads_no_field_from_a_body_sent_under_a_content_type_the_vendor_never_publishes() {
    MockHttpServletRequest request = posted("TERMINAL=77777777");
    request.setContentType("application/json");

    assertThat(AzericardWireRequest.callOf(request).form()).isEmpty();
  }

  @Test
  void reads_no_field_from_a_request_that_declared_no_content_type() {
    MockHttpServletRequest request = posted("TERMINAL=77777777");
    request.setContentType(null);

    assertThat(AzericardWireRequest.callOf(request).form()).isEmpty();
  }

  @Test
  void reads_no_field_from_a_body_that_cannot_be_percent_decoded() {
    assertThat(AzericardWireRequest.callOf(posted("TERMINAL=%zz")).form()).isEmpty();
  }

  @Test
  void reads_no_field_from_a_body_the_transport_never_finished_delivering() {
    assertThat(AzericardWireRequest.callOf(unreadable()).form()).isEmpty();
  }

  private static MockHttpServletRequest posted(String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/cgi-bin/cgi_link");
    request.setContentType(URLENCODED);
    request.setContent(body.getBytes(UTF_8));
    return request;
  }

  private static MockHttpServletRequest unreadable() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/cgi-bin/cgi_link") {

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
