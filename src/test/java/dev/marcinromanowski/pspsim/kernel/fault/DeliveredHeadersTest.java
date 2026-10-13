package dev.marcinromanowski.pspsim.kernel.fault;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeliveredHeadersTest {

  private static final String JSON = "application/json";

  private static final String BODY = "{\"status\":1,\"statusCode\":\"2\"}";

  private static final String PLAIN = "text/plain; charset=utf-8";

  @Test
  void a_delivery_that_stops_short_of_the_length_it_declared_ends_the_connection() {
    FaultedDelivery dropped =
        new TransportFault.DroppedConnection()
            .applyTo(new RenderedResponse(200, JSON, BODY.getBytes(UTF_8)));

    assertThat(dropped.completesTheBody()).isFalse();
    assertThat(DeliveredHeaders.of(dropped))
        .containsEntry(DeliveredHeaders.CONNECTION, List.of(DeliveredHeaders.CLOSE));
  }

  @Test
  void a_delivery_that_writes_every_byte_it_declared_leaves_the_connection_open() {
    FaultedDelivery whole = new RenderedResponse(200, JSON, BODY.getBytes(UTF_8)).asDelivered();

    assertThat(whole.completesTheBody()).isTrue();
    assertThat(DeliveredHeaders.of(whole))
        .containsExactly(Map.entry(DeliveredHeaders.CONTENT_TYPE, List.of(JSON)));
  }

  @Test
  void a_truncated_body_declares_the_bytes_it_writes_and_so_keeps_the_connection_open() {
    FaultedDelivery truncated =
        new TransportFault.TruncatedBody()
            .applyTo(new RenderedResponse(200, JSON, BODY.getBytes(UTF_8)));

    assertThat(DeliveredHeaders.of(truncated))
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(DeliveredHeaders.CONNECTION))
        .containsEntry(DeliveredHeaders.CONTENT_TYPE, List.of(JSON));
  }

  @Test
  void a_gateway_answer_names_the_media_type_of_its_own_bytes_and_keeps_its_connection() {
    FaultedDelivery gateway =
        new TransportFault.ServerError(503)
            .applyTo(new RenderedResponse(200, JSON, BODY.getBytes(UTF_8)));

    assertThat(gateway.contentType()).isEqualTo(PLAIN);
    assertThat(DeliveredHeaders.of(gateway))
        .containsExactly(Map.entry(DeliveredHeaders.CONTENT_TYPE, List.of(PLAIN)));
  }

  @Test
  void the_connection_closes_only_where_a_delivery_stops_short_of_the_length_it_declared() {
    RenderedResponse response = new RenderedResponse(200, JSON, BODY.getBytes(UTF_8));

    FaultedDelivery gateway = new TransportFault.ServerError(502).applyTo(response);
    FaultedDelivery truncated = new TransportFault.TruncatedBody().applyTo(response);
    FaultedDelivery dropped = new TransportFault.DroppedConnection().applyTo(response);

    assertThat(DeliveredHeaders.of(gateway))
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(DeliveredHeaders.CONNECTION));
    assertThat(DeliveredHeaders.of(truncated))
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling(DeliveredHeaders.CONNECTION));
    assertThat(DeliveredHeaders.of(dropped))
        .containsEntry(DeliveredHeaders.CONNECTION, List.of(DeliveredHeaders.CLOSE));
    assertThat(new String(truncated.body(), UTF_8)).startsWith("{\"status\"");
    assertThat(new String(dropped.body(), UTF_8)).startsWith("{\"status\"");
  }

  @Test
  void the_headers_a_provider_names_of_its_own_are_kept_beside_the_ones_the_delivery_decides() {
    FaultedDelivery dropped =
        new TransportFault.DroppedConnection()
            .applyTo(new RenderedResponse(200, JSON, BODY.getBytes(UTF_8)));

    Map<String, List<String>> headers =
        DeliveredHeaders.of(dropped, Map.of("Request-Id", List.of("req_1")));

    assertThat(headers)
        .containsEntry("Request-Id", List.of("req_1"))
        .containsEntry(DeliveredHeaders.CONTENT_TYPE, List.of(JSON))
        .containsEntry(DeliveredHeaders.CONNECTION, List.of(DeliveredHeaders.CLOSE));
  }

  @Test
  void a_media_type_a_provider_named_itself_is_replaced_by_the_one_the_delivery_rendered() {
    FaultedDelivery whole = new RenderedResponse(200, JSON, BODY.getBytes(UTF_8)).asDelivered();

    Map<String, List<String>> headers =
        DeliveredHeaders.of(whole, Map.of(DeliveredHeaders.CONTENT_TYPE, List.of("text/plain")));

    assertThat(headers).containsEntry(DeliveredHeaders.CONTENT_TYPE, List.of(JSON));
  }
}
