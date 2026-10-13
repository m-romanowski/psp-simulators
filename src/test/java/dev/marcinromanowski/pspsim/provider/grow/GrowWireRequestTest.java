package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Part;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockPart;

class GrowWireRequestTest {

  private static final String MULTIPART = "multipart/form-data; boundary=----grow";
  private static final String URLENCODED = "application/x-www-form-urlencoded";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String UPDATE_MY_URL = "/api/light/server/1.0/updateMyUrl/";

  @Test
  void carries_the_method_and_path_the_call_arrived_on() {
    GrowApiCall call = GrowWireRequest.callOf(multipart(field("userId", "grow-user")));

    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path()).isEqualTo(CREATE);
  }

  @Test
  void carries_the_headers_the_merchant_sent_including_one_sent_twice() {
    MockHttpServletRequest request = multipart(field("userId", "grow-user"));
    request.addHeader("X-Simulator-Test-Run", "grow-it");
    request.addHeader("Accept", "application/json");
    request.addHeader("Accept", "text/html");

    GrowApiCall call = GrowWireRequest.callOf(request);

    assertThat(call.headers())
        .containsEntry("X-Simulator-Test-Run", List.of("grow-it"))
        .containsEntry("Accept", List.of("application/json", "text/html"));
  }

  @Test
  void reads_a_multipart_body_into_the_fields_the_merchant_sent() {
    GrowApiCall call =
        GrowWireRequest.callOf(
            multipart(
                field("pageCode", "grow-page"),
                field("userId", "grow-user"),
                field("description", "A hold")));

    assertThat(call.form())
        .containsEntry("pageCode", List.of("grow-page"))
        .containsEntry("userId", List.of("grow-user"))
        .containsEntry("description", List.of("A hold"));
  }

  @Test
  void ignores_a_file_part_and_reads_the_fields_beside_it() {
    GrowApiCall call =
        GrowWireRequest.callOf(
            multipart(
                new MockPart("receipt", "receipt.txt", "not a field".getBytes(UTF_8)),
                field("sum", "120.50")));

    assertThat(call.form()).containsOnlyKeys("sum").containsEntry("sum", List.of("120.50"));
  }

  @Test
  void keeps_both_values_of_a_multipart_field_sent_twice() {
    GrowApiCall call =
        GrowWireRequest.callOf(multipart(field("sum", "120.50"), field("sum", "1.00")));

    assertThat(call.form()).containsEntry("sum", List.of("120.50", "1.00"));
  }

  @Test
  void reads_a_form_urlencoded_body_into_the_same_fields_as_the_multipart_body_that_carries_them() {
    GrowApiCall multipartCall =
        GrowWireRequest.callOf(
            multipart(
                field("pageCode", "grow-page"),
                field("userId", "grow-user"),
                field("description", "A hold")));

    GrowApiCall urlencodedCall =
        GrowWireRequest.callOf(
            urlencoded("pageCode=grow-page&userId=grow-user&description=A+hold"));

    assertThat(urlencodedCall.form()).isEqualTo(multipartCall.form());
  }

  @Test
  void decodes_a_percent_encoded_value_and_reads_a_plus_as_a_space() {
    GrowApiCall call =
        GrowWireRequest.callOf(
            urlencoded(
                "successUrl=https%3A%2F%2Fmerchant.example%2Fthanks&fullName=Israel+Israeli"));

    assertThat(call.form())
        .containsEntry("successUrl", List.of("https://merchant.example/thanks"))
        .containsEntry("fullName", List.of("Israel Israeli"));
  }

  @Test
  void decodes_a_percent_encoded_field_name() {
    GrowApiCall call = GrowWireRequest.callOf(urlencoded("pageField%5BfullName%5D=Israel+Israeli"));

    assertThat(call.form()).containsEntry("pageField[fullName]", List.of("Israel Israeli"));
  }

  @Test
  void reads_a_key_the_merchant_sent_without_a_value_as_an_empty_value() {
    GrowApiCall call = GrowWireRequest.callOf(urlencoded("sum=120.50&description"));

    assertThat(call.form())
        .containsEntry("sum", List.of("120.50"))
        .containsEntry("description", List.of(""));
  }

  @Test
  void reads_a_value_the_merchant_sent_without_a_key_under_the_empty_name() {
    GrowApiCall call = GrowWireRequest.callOf(urlencoded("sum=120.50&=orphan"));

    assertThat(call.form())
        .containsEntry("sum", List.of("120.50"))
        .containsEntry("", List.of("orphan"));
  }

  @Test
  void skips_an_empty_pair_and_the_separator_a_body_ends_with() {
    GrowApiCall call = GrowWireRequest.callOf(urlencoded("sum=120.50&&description=A+hold&"));

    assertThat(call.form())
        .containsOnlyKeys("sum", "description")
        .containsEntry("sum", List.of("120.50"))
        .containsEntry("description", List.of("A hold"));
  }

  @Test
  void keeps_both_values_of_a_form_urlencoded_field_sent_twice() {
    GrowApiCall call = GrowWireRequest.callOf(urlencoded("sum=120.50&sum=1.00"));

    assertThat(call.form()).containsEntry("sum", List.of("120.50", "1.00"));
  }

  @Test
  void reads_a_content_type_the_merchant_spelled_in_upper_case() {
    MockHttpServletRequest request = urlencoded("sum=120.50");
    request.setContentType("APPLICATION/X-WWW-FORM-URLENCODED");

    GrowApiCall call = GrowWireRequest.callOf(request);

    assertThat(call.form()).containsEntry("sum", List.of("120.50"));
  }

  @Test
  void answers_a_body_that_is_not_form_data_with_no_fields() {
    MockHttpServletRequest request = urlencoded("{\"pageCode\":\"grow-page\"}");
    request.setContentType("application/json");

    assertThat(GrowWireRequest.callOf(request).form()).isEmpty();
  }

  @Test
  void answers_a_request_that_declares_no_content_type_with_no_fields() {
    MockHttpServletRequest request = urlencoded("pageCode=grow-page");
    request.setContentType(null);

    assertThat(GrowWireRequest.callOf(request).form()).isEmpty();
  }

  @Test
  void answers_a_form_urlencoded_body_carrying_a_malformed_escape_with_no_fields() {
    assertThat(GrowWireRequest.callOf(urlencoded("pageCode=grow-page&userId=%zz")).form())
        .isEmpty();
  }

  @Test
  void answers_a_multipart_body_the_container_cannot_parse_with_no_fields() {
    MockHttpServletRequest request =
        multipartWhose(
            () -> {
              throw new ServletException("the multipart body carries no boundary");
            });

    assertThat(GrowWireRequest.callOf(request).form()).isEmpty();
  }

  @Test
  void answers_a_multipart_body_the_container_refuses_for_its_size_with_no_fields() {
    MockHttpServletRequest request =
        multipartWhose(
            () -> {
              throw new IllegalStateException("the part exceeds the configured maximum size");
            });

    assertThat(GrowWireRequest.callOf(request).form()).isEmpty();
  }

  @Test
  void lets_a_failure_to_read_a_part_the_container_handed_over_leave_rather_than_answering_it() {
    MockHttpServletRequest request =
        multipart(
            new MockPart("sum", "120.50".getBytes(UTF_8)) {
              @Override
              public InputStream getInputStream() {
                throw new IllegalStateException("getInputStream() has already been called");
              }
            });

    assertThatThrownBy(() -> GrowWireRequest.callOf(request))
        .isInstanceOf(IllegalStateException.class);
  }

  @FunctionalInterface
  private interface ContainerParts {
    Collection<Part> handedOver() throws ServletException;
  }

  private static MockHttpServletRequest multipartWhose(ContainerParts parts) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", CREATE) {
          @Override
          public Collection<Part> getParts() throws ServletException {
            return parts.handedOver();
          }
        };
    request.setContentType(MULTIPART);
    return request;
  }

  @Test
  void reads_the_url_a_tester_named_in_the_query_string_of_a_callback_test() {
    GrowApiCall call =
        GrowWireRequest.queryCallOf(get("url=https%3A%2F%2Fmerchant.example%2Fhook"));

    assertThat(call.method()).isEqualTo("GET");
    assertThat(call.path()).isEqualTo(UPDATE_MY_URL);
    assertThat(call.form()).containsExactly(entry("url", List.of("https://merchant.example/hook")));
  }

  @Test
  void a_callback_test_with_no_query_string_at_all_supplies_no_url() {
    assertThat(GrowWireRequest.queryCallOf(get(null)).form()).isEmpty();
  }

  @Test
  void a_callback_test_reads_the_query_string_and_never_the_body_beside_it() {
    MockHttpServletRequest request = get("url=https%3A%2F%2Fmerchant.example%2Fhook");
    request.setContentType(URLENCODED);
    request.setContent("userId=grow-user&pageCode=grow-page".getBytes(UTF_8));

    assertThat(GrowWireRequest.queryCallOf(request).form()).containsOnlyKeys("url");
  }

  @Test
  void a_callback_test_carries_the_headers_the_tester_sent() {
    MockHttpServletRequest request = get("url=https%3A%2F%2Fmerchant.example%2Fhook");
    request.addHeader("X-Simulator-Test-Run", "grow-it");

    assertThat(GrowWireRequest.queryCallOf(request).headers())
        .containsEntry("X-Simulator-Test-Run", List.of("grow-it"));
  }

  private static MockHttpServletRequest get(String queryString) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", UPDATE_MY_URL);
    request.setQueryString(queryString);
    return request;
  }

  private static MockPart field(String name, String value) {
    return new MockPart(name, value.getBytes(UTF_8));
  }

  private static MockHttpServletRequest multipart(Part... parts) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", CREATE);
    request.setContentType(MULTIPART);
    for (Part part : parts) {
      request.addPart(part);
    }
    return request;
  }

  private static MockHttpServletRequest urlencoded(String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", CREATE);
    request.setContentType(URLENCODED);
    request.setContent(body.getBytes(UTF_8));
    return request;
  }
}
