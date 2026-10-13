package dev.marcinromanowski.pspsim.provider.worldline;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;

class WorldlineWireRequestTest {

  private static final String THE_PAYMENTS = "/v2/pspid-1/payments";

  private static final String A_QUERY_THIS_CONTRACT_NEVER_CARRIES = "notAField";

  @Test
  void carries_the_method_and_the_route_the_call_arrived_on() {
    WorldlineApiCall call = WorldlineWireRequest.callOf(posted("{}"));

    assertThat(call.method()).isEqualTo("POST");
    assertThat(call.path()).isEqualTo(THE_PAYMENTS);
  }

  @Test
  void carries_every_header_the_merchant_sent_including_one_sent_twice() {
    MockHttpServletRequest request = posted("{}");
    request.addHeader("Accept", "application/json");
    request.addHeader("Accept", "text/plain");

    assertThat(WorldlineWireRequest.callOf(request).headers())
        .containsEntry("Accept", List.of("application/json", "text/plain"));
  }

  @Test
  void reads_the_json_body_this_provider_publishes_as_the_request_encoding() {
    WorldlineApiCall call =
        WorldlineWireRequest.callOf(
            posted("{\"merchantReference\":\"order-123\",\"amount\":5000}"));

    assertThat(call.carriesABody()).isTrue();
    assertThat(call.body().orElseThrow().text(WorldlineFields.MERCHANT_REFERENCE))
        .contains("order-123");
    assertThat(call.body().orElseThrow().number(WorldlineFields.AMOUNT)).contains("5000");
  }

  @Test
  void reads_the_notAField_out_of_the_query_where_the_token_endpoint_publishes_it() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", THE_PAYMENTS);
    request.setQueryString("notAField=client_credentials&unused=a+b");
    request.setContent(new byte[0]);

    WorldlineApiCall call = WorldlineWireRequest.callOf(request);

    assertThat(call.namesTheQueryParameter(A_QUERY_THIS_CONTRACT_NEVER_CARRIES)).isTrue();
    assertThat(call.queryParameter(A_QUERY_THIS_CONTRACT_NEVER_CARRIES))
        .contains("client_credentials");
    assertThat(call.queryParameter("unused")).contains("a b");
    assertThat(call.queryParameter("absent")).isEmpty();
  }

  @Test
  void a_query_parameter_written_with_no_value_at_all_is_named_and_carries_nothing() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", THE_PAYMENTS);
    request.setQueryString("notAField&other=");
    request.setContent(new byte[0]);

    WorldlineApiCall call = WorldlineWireRequest.callOf(request);

    assertThat(call.namesTheQueryParameter(A_QUERY_THIS_CONTRACT_NEVER_CARRIES)).isTrue();
    assertThat(call.queryParameter(A_QUERY_THIS_CONTRACT_NEVER_CARRIES)).isEmpty();
    assertThat(call.namesTheQueryParameter("other")).isTrue();
    assertThat(call.queryParameter("other")).isEmpty();
  }

  @Test
  void a_query_pair_that_opens_with_the_separator_names_the_empty_parameter_and_keeps_its_value() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", THE_PAYMENTS);
    request.setQueryString("=orphan&notAField=client_credentials");
    request.setContent(new byte[0]);

    WorldlineApiCall call = WorldlineWireRequest.callOf(request);

    assertThat(call.namesTheQueryParameter("")).isTrue();
    assertThat(call.queryParameter("")).contains("orphan");
    assertThat(call.namesTheQueryParameter("=orphan")).isFalse();
    assertThat(call.queryParameter(A_QUERY_THIS_CONTRACT_NEVER_CARRIES))
        .contains("client_credentials");
  }

  @Test
  void a_call_with_no_query_at_all_names_no_query_parameter() {
    WorldlineApiCall call = WorldlineWireRequest.callOf(posted("{}"));

    assertThat(call.namesTheQueryParameter(A_QUERY_THIS_CONTRACT_NEVER_CARRIES)).isFalse();
    assertThat(call.queryParameter(A_QUERY_THIS_CONTRACT_NEVER_CARRIES)).isEmpty();
  }

  @Test
  void a_query_the_transport_cannot_decode_is_read_as_no_query_rather_than_raising() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", THE_PAYMENTS);
    request.setQueryString("notAField=%zz");
    request.setContent(new byte[0]);

    assertThat(
            WorldlineWireRequest.callOf(request)
                .namesTheQueryParameter(A_QUERY_THIS_CONTRACT_NEVER_CARRIES))
        .isFalse();
  }

  @Test
  void a_header_named_in_any_casing_is_found_and_a_blank_one_is_read_as_absent() {
    MockHttpServletRequest request = posted("{}");
    request.addHeader("authorization", "Bearer abc");
    request.addHeader("X-Blank", "");

    WorldlineApiCall call = WorldlineWireRequest.callOf(request);

    assertThat(call.header(WorldlineFields.AUTHORIZATION)).contains("Bearer abc");
    assertThat(call.header("X-Blank")).isEmpty();
    assertThat(call.header("X-Absent")).isEmpty();
  }

  @Test
  void reads_no_body_from_a_request_the_transport_never_finished_delivering() {
    WorldlineApiCall call = WorldlineWireRequest.callOf(unreadable());

    assertThat(call.carriesABody()).isFalse();
    assertThat(call.body()).isEmpty();
  }

  private static MockHttpServletRequest posted(String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", THE_PAYMENTS);
    request.setContentType(WorldlineFields.JSON);
    request.setContent(body.getBytes(UTF_8));
    return request;
  }

  private static MockHttpServletRequest unreadable() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", THE_PAYMENTS) {

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
    request.setContentType(WorldlineFields.JSON);
    return request;
  }
}
