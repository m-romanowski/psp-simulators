package dev.marcinromanowski.pspsim.provider.autra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;

class AutraWireRequestTest {

  @Test
  void carries_the_method_and_the_route_the_call_arrived_on() {
    AutraApiCall call = AutraWireRequest.callOf(posted("{}"));

    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path()).isEqualTo(AutraApiController.PRE_AUTHORIZATION);
  }

  @Test
  void carries_every_header_the_merchant_sent_including_one_sent_twice() {
    MockHttpServletRequest request = posted("{}");
    request.addHeader("Accept", "application/json");
    request.addHeader("Accept", "text/plain");

    assertThat(AutraWireRequest.callOf(request).headers())
        .containsEntry("Accept", List.of("application/json", "text/plain"));
  }

  @Test
  void reads_the_json_body_this_provider_publishes_as_the_request_encoding() {
    AutraApiCall call =
        AutraWireRequest.callOf(posted("{\"documentId\":\"25055662000190\",\"amount\":500.00}"));

    assertThat(call.carriesABody()).isTrue();
    assertThat(call.body().orElseThrow().text(AutraFields.DOCUMENT_ID)).contains("25055662000190");
    assertThat(AutraAmounts.lexicalIn(call.body().orElseThrow(), AutraFields.AMOUNT))
        .contains("500.00");
  }

  @Test
  void reads_the_grant_type_out_of_the_query_where_the_token_endpoint_publishes_it() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/oauth/token");
    request.setQueryString("grant_type=client_credentials&unused=a+b");
    request.setContent(new byte[0]);

    AutraApiCall call = AutraWireRequest.callOf(request);

    assertThat(call.namesTheQueryParameter(AutraFields.GRANT_TYPE)).isTrue();
    assertThat(call.queryParameter(AutraFields.GRANT_TYPE))
        .contains(AutraFields.CLIENT_CREDENTIALS);
    assertThat(call.queryParameter("unused")).contains("a b");
    assertThat(call.queryParameter("absent")).isEmpty();
  }

  @Test
  void a_query_parameter_written_with_no_value_at_all_is_named_and_carries_nothing() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/oauth/token");
    request.setQueryString("grant_type&other=");
    request.setContent(new byte[0]);

    AutraApiCall call = AutraWireRequest.callOf(request);

    assertThat(call.namesTheQueryParameter(AutraFields.GRANT_TYPE)).isTrue();
    assertThat(call.queryParameter(AutraFields.GRANT_TYPE)).isEmpty();
    assertThat(call.namesTheQueryParameter("other")).isTrue();
    assertThat(call.queryParameter("other")).isEmpty();
  }

  @Test
  void a_query_pair_that_opens_with_the_separator_names_the_empty_parameter_and_keeps_its_value() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/oauth/token");
    request.setQueryString("=orphan&grant_type=client_credentials");
    request.setContent(new byte[0]);

    AutraApiCall call = AutraWireRequest.callOf(request);

    assertThat(call.namesTheQueryParameter("")).isTrue();
    assertThat(call.queryParameter("")).contains("orphan");
    assertThat(call.namesTheQueryParameter("=orphan")).isFalse();
    assertThat(call.queryParameter(AutraFields.GRANT_TYPE))
        .contains(AutraFields.CLIENT_CREDENTIALS);
  }

  @Test
  void a_call_with_no_query_at_all_names_no_query_parameter() {
    AutraApiCall call = AutraWireRequest.callOf(posted("{}"));

    assertThat(call.namesTheQueryParameter(AutraFields.GRANT_TYPE)).isFalse();
    assertThat(call.queryParameter(AutraFields.GRANT_TYPE)).isEmpty();
  }

  @Test
  void a_query_the_transport_cannot_decode_is_read_as_no_query_rather_than_raising() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/oauth/token");
    request.setQueryString("grant_type=%zz");
    request.setContent(new byte[0]);

    assertThat(AutraWireRequest.callOf(request).namesTheQueryParameter(AutraFields.GRANT_TYPE))
        .isFalse();
  }

  @Test
  void a_header_named_in_any_casing_is_found_and_a_blank_one_is_read_as_absent() {
    MockHttpServletRequest request = posted("{}");
    request.addHeader("authorization", "Bearer abc");
    request.addHeader("X-Blank", "");

    AutraApiCall call = AutraWireRequest.callOf(request);

    assertThat(call.header(AutraFields.AUTHORIZATION)).contains("Bearer abc");
    assertThat(call.header("X-Blank")).isEmpty();
    assertThat(call.header("X-Absent")).isEmpty();
  }

  @Test
  void reads_no_body_from_a_request_the_transport_never_finished_delivering() {
    AutraApiCall call = AutraWireRequest.callOf(unreadable());

    assertThat(call.carriesABody()).isFalse();
    assertThat(call.body()).isEmpty();
  }

  private static MockHttpServletRequest posted(String body) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", AutraApiController.PRE_AUTHORIZATION);
    request.setContentType(AutraFields.JSON);
    request.setContent(body.getBytes(UTF_8));
    return request;
  }

  private static MockHttpServletRequest unreadable() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", AutraApiController.PRE_AUTHORIZATION) {

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
    request.setContentType(AutraFields.JSON);
    return request;
  }
}
