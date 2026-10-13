package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveryAbandonment;
import dev.marcinromanowski.pspsim.kernel.fault.HeldDelivery;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

final class TranzilaDelivery {

  static final String DIAGNOSTIC_CONTENT_TYPE = "text/plain; charset=utf-8";

  static final String RESET_WHILE_HELD =
      "the simulator held this response for a scenario delay and the test run it belongs to was"
          + " reset before the delay elapsed, so the response it was holding no longer describes"
          + " any state this simulator has";

  static final String STOPPING_WHILE_HELD =
      "the simulator held this response for a scenario delay and shut down before the delay"
          + " elapsed";

  static final String UNDELIVERED =
      "the simulator held this response for a scenario delay and did not deliver it before the"
          + " asynchronous request deadline, which is simulator.fault.longest-delay plus a"
          + " margin; nothing about the operation itself failed";

  private final DelayedDeliveries delays;
  private final long deliveryDeadlineMillis;

  TranzilaDelivery(DelayedDeliveries delays, Duration deliveryDeadline) {
    this.delays = delays;
    this.deliveryDeadlineMillis = deliveryDeadline.toMillis();
  }

  Object delivered(TranzilaApiResponse response) {
    Duration delay = response.delay();
    if (delay.isZero()) {
      return written(response);
    }
    DeferredResult<ResponseEntity<byte[]>> held = new DeferredResult<>(deliveryDeadlineMillis);
    HeldDelivery delivery =
        delays.hold(
            TranzilaApi.TRANZILA,
            response.testRunId(),
            delay,
            () -> held.setResult(written(response)),
            reason -> held.setResult(abandoned(reason)));
    held.onTimeout(() -> whenTheDeadlinePasses(delivery, held));
    held.onError(error -> delivery.settle());
    return held;
  }

  static void whenTheDeadlinePasses(
      HeldDelivery delivery, DeferredResult<ResponseEntity<byte[]>> held) {
    if (delivery.settle()) {
      held.setResult(diagnostic(HttpStatus.GATEWAY_TIMEOUT.value(), UNDELIVERED));
    }
  }

  static ResponseEntity<byte[]> abandoned(DeliveryAbandonment reason) {
    return diagnostic(
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        switch (reason) {
          case TEST_RUN_RESET -> RESET_WHILE_HELD;
          case SIMULATOR_STOPPING -> STOPPING_WHILE_HELD;
        });
  }

  static ResponseEntity<byte[]> diagnostic(int httpStatus, String explanation) {
    byte[] body = explanation.getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.status(httpStatus)
        .header("Content-Type", DIAGNOSTIC_CONTENT_TYPE)
        .contentLength(body.length)
        .body(body);
  }

  static ResponseEntity<byte[]> written(TranzilaApiResponse response) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.httpStatus());
    for (Map.Entry<String, List<String>> header : response.headers().entrySet()) {
      for (String value : header.getValue()) {
        builder = builder.header(header.getKey(), value);
      }
    }
    long declared = response.declaredContentLength();
    if (declared == 0) {
      return builder.build();
    }
    return builder.contentLength(declared).body(response.body());
  }
}
