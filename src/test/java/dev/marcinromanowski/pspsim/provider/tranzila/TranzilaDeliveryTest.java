package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveryAbandonment;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.RenderedResponse;
import dev.marcinromanowski.pspsim.kernel.fault.ScheduledDeliveries;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

class TranzilaDeliveryTest {

  private final List<Runnable> scheduled = new ArrayList<>();

  private final List<Duration> delays = new ArrayList<>();

  private final ScheduledDeliveries deliveries =
      new ScheduledDeliveries(
          (delay, delivery) -> {
            delays.add(delay);
            scheduled.add(delivery);
            return () -> {};
          });

  private final TranzilaDelivery delivery = new TranzilaDelivery(deliveries, Duration.ofSeconds(9));

  @Test
  void an_answer_no_rule_delays_is_written_at_once_with_its_status_headers_and_body() {
    Object written = delivery.delivered(response(rendered().asDelivered()));

    ResponseEntity<?> entity = (ResponseEntity<?>) written;
    assertThat(entity.getStatusCode().value()).isEqualTo(200);
    assertThat(entity.getHeaders().getFirst("Content-Type")).isEqualTo("application/json");
    assertThat(entity.getHeaders().getContentLength()).isEqualTo(2);
    assertThat(new String((byte[]) entity.getBody(), StandardCharsets.UTF_8)).isEqualTo("{}");
    assertThat(scheduled).isEmpty();
  }

  @Test
  void an_answer_with_no_body_declared_is_written_without_one() {
    ResponseEntity<byte[]> written =
        TranzilaDelivery.written(
            response(new RenderedResponse(204, "application/json", new byte[0]).asDelivered()));

    assertThat(written.getStatusCode().value()).isEqualTo(204);
    assertThat(written.getBody()).isNull();
  }

  @Test
  void a_delayed_answer_is_held_for_the_delay_and_then_written() {
    FaultedDelivery delayed =
        new TransportFault.DelayedResponse(Duration.ofMillis(400)).applyTo(rendered());

    @SuppressWarnings("unchecked")
    DeferredResult<ResponseEntity<byte[]>> held =
        (DeferredResult<ResponseEntity<byte[]>>) delivery.delivered(response(delayed));

    assertThat(held.hasResult()).isFalse();
    assertThat(delays).containsExactly(Duration.ofMillis(400));
    scheduled.get(0).run();
    assertThat(((ResponseEntity<?>) held.getResult()).getStatusCode().value()).isEqualTo(200);
  }

  @Test
  void a_held_answer_whose_run_is_reset_is_answered_503_saying_so() {
    FaultedDelivery delayed =
        new TransportFault.DelayedResponse(Duration.ofMillis(400)).applyTo(rendered());
    @SuppressWarnings("unchecked")
    DeferredResult<ResponseEntity<byte[]>> held =
        (DeferredResult<ResponseEntity<byte[]>>) delivery.delivered(response(delayed));

    deliveries.cancel(TranzilaApi.TRANZILA, TranzilaWiring.RUN);

    ResponseEntity<?> answered = (ResponseEntity<?>) held.getResult();
    assertThat(answered.getStatusCode().value()).isEqualTo(503);
    assertThat(answered.getHeaders().getFirst("Content-Type"))
        .isEqualTo("text/plain; charset=utf-8");
    assertThat(new String((byte[]) answered.getBody(), StandardCharsets.UTF_8))
        .isEqualTo(TranzilaDelivery.RESET_WHILE_HELD);
  }

  @Test
  void a_held_answer_the_simulator_stops_under_is_answered_503_saying_so() {
    ResponseEntity<byte[]> answered =
        TranzilaDelivery.abandoned(DeliveryAbandonment.SIMULATOR_STOPPING);

    assertThat(answered.getStatusCode().value()).isEqualTo(503);
    assertThat(new String(answered.getBody(), StandardCharsets.UTF_8))
        .isEqualTo(TranzilaDelivery.STOPPING_WHILE_HELD);
    assertThat(answered.getHeaders().getContentLength())
        .isEqualTo(TranzilaDelivery.STOPPING_WHILE_HELD.length());
  }

  @Test
  void a_held_answer_past_the_deadline_is_answered_504_once_and_only_if_still_unsettled() {
    FaultedDelivery delayed =
        new TransportFault.DelayedResponse(Duration.ofMillis(400)).applyTo(rendered());
    var holding =
        deliveries.hold(
            TranzilaApi.TRANZILA,
            TranzilaWiring.RUN,
            Duration.ofMillis(400),
            () -> {},
            reason -> {});
    DeferredResult<ResponseEntity<byte[]>> held = new DeferredResult<>();

    TranzilaDelivery.whenTheDeadlinePasses(holding, held);

    ResponseEntity<?> answered = (ResponseEntity<?>) held.getResult();
    assertThat(answered.getStatusCode().value()).isEqualTo(504);
    assertThat(new String((byte[]) answered.getBody(), StandardCharsets.UTF_8))
        .isEqualTo(TranzilaDelivery.UNDELIVERED);
    DeferredResult<ResponseEntity<byte[]>> settledAlready = new DeferredResult<>();
    TranzilaDelivery.whenTheDeadlinePasses(holding, settledAlready);
    assertThat(settledAlready.hasResult()).isFalse();
    assertThat(delayed.delay()).isEqualTo(Duration.ofMillis(400));
  }

  private static RenderedResponse rendered() {
    return new RenderedResponse(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8));
  }

  private static TranzilaApiResponse response(FaultedDelivery delivered) {
    return new TranzilaApiResponse(
        TestRunId.of("tranzila-unit"), delivered, ProviderOutcome.SUCCESS, null);
  }
}
