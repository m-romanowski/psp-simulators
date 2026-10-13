package dev.marcinromanowski.pspsim.kernel.fault;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TransportFaultTest {

  private static final String SIGNED_BODY = "{\"status\":1,\"data\":{\"id\":\"9000001\"}}";

  private static final String CONTENT_TYPE = "application/json";

  private static final String GATEWAY_CONTENT_TYPE = "text/plain; charset=utf-8";

  private static final String GATEWAY_502 =
      "502 was answered in front of the provider. These bytes are the gateway's and not the"
          + " provider's, and whether the provider processed the request cannot be read from this"
          + " answer.";

  private final RenderedResponse rendered =
      new RenderedResponse(200, CONTENT_TYPE, SIGNED_BODY.getBytes(UTF_8));

  @Test
  void a_response_no_fault_touches_is_delivered_as_the_provider_rendered_it() {
    FaultedDelivery delivered = rendered.asDelivered();

    assertThat(delivered.httpStatus()).isEqualTo(200);
    assertThat(delivered.contentType()).isEqualTo(CONTENT_TYPE);
    assertThat(new String(delivered.body(), UTF_8)).isEqualTo(SIGNED_BODY);
    assertThat(delivered.declaredContentLength()).isEqualTo(SIGNED_BODY.length());
    assertThat(delivered.completesTheBody()).isTrue();
    assertThat(delivered.delay()).isEqualTo(Duration.ZERO);
  }

  @Test
  void a_provider_that_rendered_nothing_is_delivered_unfaulted_rather_than_refused() {
    RenderedResponse empty = new RenderedResponse(204, CONTENT_TYPE, new byte[0]);

    FaultedDelivery delivered = empty.asDelivered();

    assertThat(delivered.httpStatus()).isEqualTo(204);
    assertThat(delivered.body()).isEmpty();
    assertThat(delivered.declaredContentLength()).isZero();
    assertThat(delivered.completesTheBody()).isTrue();
    assertThat(delivered.fault()).isNull();
  }

  @Test
  void the_two_faults_that_need_no_bytes_still_act_on_a_response_that_rendered_none() {
    RenderedResponse empty = new RenderedResponse(204, CONTENT_TYPE, new byte[0]);

    assertThat(new TransportFault.ServerError(502).applyTo(empty).httpStatus()).isEqualTo(502);
    assertThat(new TransportFault.DelayedResponse(Duration.ofMillis(5)).applyTo(empty).delay())
        .isEqualTo(Duration.ofMillis(5));
  }

  @ParameterizedTest(name = "{0} needs bytes to damage and refuses a response that rendered none")
  @MethodSource("theBodyDamagingFaults")
  void a_fault_that_damages_a_body_is_refused_only_where_it_is_applied(
      String name, TransportFault fault) {
    RenderedResponse empty = new RenderedResponse(204, CONTENT_TYPE, new byte[0]);

    assertThatThrownBy(() -> fault.applyTo(empty))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs bytes to damage");
  }

  private static List<Arguments> theBodyDamagingFaults() {
    return List.of(
        Arguments.of("TRUNCATED_BODY", new TransportFault.TruncatedBody()),
        Arguments.of("CORRUPTED_BODY", new TransportFault.CorruptedBody()),
        Arguments.of("DROPPED_CONNECTION", new TransportFault.DroppedConnection()));
  }

  @Test
  void a_server_error_puts_a_gateway_answer_where_the_rendered_response_was() {
    FaultedDelivery delivered = new TransportFault.ServerError(502).applyTo(rendered);

    assertThat(delivered.httpStatus()).isEqualTo(502);
    assertThat(delivered.contentType()).isEqualTo(GATEWAY_CONTENT_TYPE);
    assertThat(new String(delivered.body(), UTF_8)).isEqualTo(GATEWAY_502);
    assertThat(delivered.declaredContentLength()).isEqualTo(GATEWAY_502.getBytes(UTF_8).length);
    assertThat(delivered.completesTheBody()).isTrue();
    assertThat(delivered.delay()).isEqualTo(Duration.ZERO);
    assertThat(new TransportFault.ServerError(502).kind())
        .isEqualTo(TransportFaultKind.SERVER_ERROR);
  }

  @Test
  void a_gateway_answer_is_a_body_a_client_can_read_and_never_the_bytes_a_provider_rendered() {
    FaultedDelivery delivered = new TransportFault.ServerError(502).applyTo(rendered);
    String answered = new String(delivered.body(), UTF_8);

    assertThat(delivered.body()).isNotEmpty();
    assertThat(answered).isEqualTo(GATEWAY_502);
    assertThat(answered).doesNotContain(SIGNED_BODY);
    assertThat(answered).contains("cannot be read from this answer");
    assertThat(answered).doesNotContainIgnoringCase("grow");
  }

  @ParameterizedTest(name = "{0} still delivers the provider's own bytes under its own media type")
  @MethodSource("theBodyDamagingFaults")
  void a_fault_that_damages_a_body_never_answers_a_gateway_answer_instead(
      String name, TransportFault fault) {
    FaultedDelivery delivered = fault.applyTo(rendered);

    assertThat(delivered.contentType()).isEqualTo(CONTENT_TYPE);
    assertThat(new String(delivered.body(), UTF_8))
        .startsWith("{\"status\"")
        .doesNotContain("in front of the provider");
  }

  @ParameterizedTest(name = "a transport fault cannot put status {0} where the response was")
  @ValueSource(ints = {200, 400, 499, 600, 700})
  void a_status_that_is_not_a_server_error_is_a_status_the_provider_would_have_chosen(int status) {
    assertThatThrownBy(() -> new TransportFault.ServerError(status))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("action.httpStatus");
  }

  @ParameterizedTest(name = "{0} is a server error a transport fault may deliver")
  @ValueSource(ints = {500, 502, 504, 599})
  void every_server_error_status_is_accepted(int status) {
    assertThat(new TransportFault.ServerError(status).applyTo(rendered).httpStatus())
        .isEqualTo(status);
  }

  @Test
  void a_truncated_body_is_a_genuine_prefix_of_the_bytes_the_provider_signed() {
    byte[] valid = rendered.body();
    FaultedDelivery delivered = new TransportFault.TruncatedBody().applyTo(rendered);
    byte[] faulted = delivered.body();

    assertThat(faulted).hasSize(valid.length / 2);
    assertThat(faulted).isEqualTo(Arrays.copyOf(valid, valid.length / 2));
    assertThat(new String(valid, UTF_8)).startsWith(new String(faulted, UTF_8));
    assertThat(new String(faulted, UTF_8)).isEqualTo("{\"status\":1,\"data\"");
    assertThat(delivered.httpStatus()).isEqualTo(200);
    assertThat(delivered.contentType()).isEqualTo(CONTENT_TYPE);
    assertThat(delivered.declaredContentLength()).isEqualTo(faulted.length);
    assertThat(delivered.completesTheBody()).isTrue();
    assertThat(delivered.delay()).isEqualTo(Duration.ZERO);
    assertThat(new TransportFault.TruncatedBody().kind())
        .isEqualTo(TransportFaultKind.TRUNCATED_BODY);
  }

  @Test
  void a_corrupted_body_is_the_signed_bytes_with_one_byte_of_them_changed()
      throws CharacterCodingException {
    byte[] valid = rendered.body();
    FaultedDelivery delivered = new TransportFault.CorruptedBody().applyTo(rendered);
    byte[] faulted = delivered.body();

    assertThat(faulted).hasSize(valid.length);
    assertThat(Arrays.copyOf(faulted, faulted.length - 1))
        .isEqualTo(Arrays.copyOf(valid, valid.length - 1));
    assertThat(faulted[faulted.length - 1]).isNotEqualTo(valid[valid.length - 1]);
    assertThat(new String(valid, UTF_8)).endsWith("}");
    assertThat(decodedStrictly(valid)).isEqualTo(SIGNED_BODY);
    assertThatThrownBy(() -> decodedStrictly(faulted)).isInstanceOf(CharacterCodingException.class);
    assertThat(delivered.declaredContentLength()).isEqualTo(valid.length);
    assertThat(delivered.completesTheBody()).isTrue();
    assertThat(delivered.contentType()).isEqualTo(CONTENT_TYPE);
    assertThat(delivered.delay()).isEqualTo(Duration.ZERO);
    assertThat(new TransportFault.CorruptedBody().kind())
        .isEqualTo(TransportFaultKind.CORRUPTED_BODY);
  }

  @Test
  void a_dropped_connection_promises_the_whole_signed_body_and_writes_a_prefix_of_it() {
    byte[] valid = rendered.body();
    FaultedDelivery delivered = new TransportFault.DroppedConnection().applyTo(rendered);
    byte[] faulted = delivered.body();

    assertThat(faulted).isEqualTo(Arrays.copyOf(valid, valid.length / 2));
    assertThat(delivered.declaredContentLength()).isEqualTo(valid.length);
    assertThat(delivered.completesTheBody()).isFalse();
    assertThat(delivered.httpStatus()).isEqualTo(200);
    assertThat(delivered.contentType()).isEqualTo(CONTENT_TYPE);
    assertThat(delivered.delay()).isEqualTo(Duration.ZERO);
    assertThat(new TransportFault.DroppedConnection().kind())
        .isEqualTo(TransportFaultKind.DROPPED_CONNECTION);
  }

  @Test
  void a_delayed_response_delivers_the_signed_bytes_unchanged_and_names_how_long_it_is_held() {
    FaultedDelivery delivered =
        new TransportFault.DelayedResponse(Duration.ofMillis(250)).applyTo(rendered);

    assertThat(delivered.body()).isEqualTo(rendered.body());
    assertThat(delivered.declaredContentLength()).isEqualTo(rendered.body().length);
    assertThat(delivered.completesTheBody()).isTrue();
    assertThat(delivered.httpStatus()).isEqualTo(200);
    assertThat(delivered.contentType()).isEqualTo(CONTENT_TYPE);
    assertThat(delivered.delay()).isEqualTo(Duration.ofMillis(250));
    assertThat(new TransportFault.DelayedResponse(Duration.ofMillis(1)).kind())
        .isEqualTo(TransportFaultKind.DELAYED_RESPONSE);
  }

  @ParameterizedTest(name = "a delay of {0}ms delays nothing")
  @ValueSource(longs = {0, -1, -250})
  void a_delay_that_holds_the_response_for_no_time_is_not_a_delay(long millis) {
    Duration delay = Duration.ofMillis(millis);

    assertThatThrownBy(() -> new TransportFault.DelayedResponse(delay))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("action.delayMs");
  }

  @ParameterizedTest(name = "a body ending in {0} is corrupted into bytes no UTF-8 parser accepts")
  @ValueSource(strings = {"}", "]", "\"", "0", "A", "~", " "})
  void the_corruption_is_malformed_for_any_parser_and_not_only_for_a_lucky_one(String lastChar)
      throws CharacterCodingException {
    byte[] valid = ("{\"status\":1" + lastChar).getBytes(UTF_8);
    RenderedResponse response = new RenderedResponse(200, CONTENT_TYPE, valid);

    byte[] faulted = new TransportFault.CorruptedBody().applyTo(response).body();

    assertThat(decodedStrictly(valid)).isEqualTo(new String(valid, UTF_8));
    assertThatThrownBy(() -> decodedStrictly(faulted)).isInstanceOf(CharacterCodingException.class);
  }

  private static String decodedStrictly(byte[] bytes) throws CharacterCodingException {
    CharsetDecoder strict =
        UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    return strict.decode(ByteBuffer.wrap(bytes)).toString();
  }

  @ParameterizedTest(name = "a delay of {0} is longer than any transport fault may hold")
  @ValueSource(longs = {300_001, 600_000})
  void a_delay_longer_than_the_types_own_bound_is_refused_wherever_it_is_built(long millis) {
    Duration delay = Duration.ofMillis(millis);

    assertThatThrownBy(() -> new TransportFault.DelayedResponse(delay))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PT5M");
  }

  @Test
  void the_longest_delay_the_type_admits_is_accepted() {
    assertThat(new TransportFault.DelayedResponse(TransportFault.LONGEST_DELAY).delay())
        .isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void every_fault_names_itself_on_the_delivery_it_produced_and_says_the_operation_had_run() {
    assertThat(new TransportFault.ServerError(502).applyTo(rendered).fault())
        .isEqualTo(
            new AppliedFault(TransportFaultKind.SERVER_ERROR, FaultPlacement.AFTER_THE_OPERATION));
    assertThat(new TransportFault.TruncatedBody().applyTo(rendered).fault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.TRUNCATED_BODY, FaultPlacement.AFTER_THE_OPERATION));
    assertThat(new TransportFault.CorruptedBody().applyTo(rendered).fault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.CORRUPTED_BODY, FaultPlacement.AFTER_THE_OPERATION));
    assertThat(new TransportFault.DroppedConnection().applyTo(rendered).fault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.DROPPED_CONNECTION, FaultPlacement.AFTER_THE_OPERATION));
    assertThat(new TransportFault.DelayedResponse(Duration.ofMillis(1)).applyTo(rendered).fault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.DELAYED_RESPONSE, FaultPlacement.AFTER_THE_OPERATION));
    assertThat(rendered.asDelivered().fault()).isNull();
  }

  @ParameterizedTest(name = "{0} answers no delivery without the operation and says why")
  @MethodSource("theFaultsThatNeedTheOperationToHaveRun")
  void a_fault_that_needs_rendered_bytes_says_what_it_does_with_them_instead_of_answering(
      String name, TransportFault fault, String whatItDoesWithThem) {
    assertThat(fault.answeredWithoutTheOperation())
        .isEqualTo(
            new TransportFault.WithoutTheOperation.NeedsTheRenderedBytes(whatItDoesWithThem));
  }

  private static List<Arguments> theFaultsThatNeedTheOperationToHaveRun() {
    return List.of(
        Arguments.of(
            "TRUNCATED_BODY",
            new TransportFault.TruncatedBody(),
            "sends a genuine prefix of the bytes an operation rendered"),
        Arguments.of(
            "CORRUPTED_BODY",
            new TransportFault.CorruptedBody(),
            "inverts one of the bytes an operation rendered"),
        Arguments.of(
            "DROPPED_CONNECTION",
            new TransportFault.DroppedConnection(),
            "writes part of the bytes an operation rendered under their full declared length"),
        Arguments.of(
            "DELAYED_RESPONSE",
            new TransportFault.DelayedResponse(Duration.ofMillis(250)),
            "delivers the bytes an operation rendered late"));
  }

  @Test
  void a_server_error_answered_without_the_operation_is_the_same_gateway_answer_placed_earlier() {
    FaultedDelivery answered = answeredWithoutTheOperationBy(new TransportFault.ServerError(502));

    assertThat(answered.httpStatus()).isEqualTo(502);
    assertThat(answered.contentType()).isEqualTo(GATEWAY_CONTENT_TYPE);
    assertThat(new String(answered.body(), UTF_8)).isEqualTo(GATEWAY_502);
    assertThat(answered.declaredContentLength()).isEqualTo(GATEWAY_502.getBytes(UTF_8).length);
    assertThat(answered.completesTheBody()).isTrue();
    assertThat(answered.delay()).isEqualTo(Duration.ZERO);
    assertThat(answered.fault())
        .isEqualTo(
            new AppliedFault(
                TransportFaultKind.SERVER_ERROR, FaultPlacement.INSTEAD_OF_THE_OPERATION));
  }

  @ParameterizedTest(name = "a server error of {0} needs no rendered bytes to be answered")
  @ValueSource(ints = {500, 502, 504, 599})
  void a_server_error_answers_the_status_it_names_whether_or_not_an_operation_ran(int status) {
    FaultedDelivery answered =
        answeredWithoutTheOperationBy(new TransportFault.ServerError(status));

    assertThat(answered.httpStatus()).isEqualTo(status);
    assertThat(answered.contentType()).isEqualTo(GATEWAY_CONTENT_TYPE);
    assertThat(new String(answered.body(), UTF_8)).startsWith(status + " was answered in front of");
    assertThat(answered.declaredContentLength()).isEqualTo(answered.writtenBodyBytes());
    assertThat(answered.declaredContentLength()).isGreaterThan(0);
  }

  private static FaultedDelivery answeredWithoutTheOperationBy(TransportFault fault) {
    return ((TransportFault.WithoutTheOperation.Answered) fault.answeredWithoutTheOperation())
        .delivery();
  }

  @Test
  void a_delivery_reports_how_many_bytes_it_wrote_beside_how_many_it_promised() {
    FaultedDelivery dropped = new TransportFault.DroppedConnection().applyTo(rendered);

    assertThat(dropped.writtenBodyBytes()).isEqualTo(dropped.body().length);
    assertThat(dropped.declaredContentLength()).isEqualTo(rendered.body().length);
    assertThat(dropped.writtenBodyBytes()).isLessThan(dropped.declaredContentLength());
  }

  @Test
  void a_fault_reads_the_rendered_bytes_and_never_writes_over_them() {
    byte[] before = rendered.body();

    new TransportFault.CorruptedBody().applyTo(rendered);

    assertThat(rendered.body()).isEqualTo(before);
  }
}
