package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveryAbandonment;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fault.HeldDelivery;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/api/light/server/1.0")
class GrowLightApiController {

  private static final String DIAGNOSTIC_CONTENT_TYPE = "text/plain; charset=utf-8";

  private static final String RESET_WHILE_HELD =
      "the simulator held this response for a scenario delay and the test run it belongs to was"
          + " reset before the delay elapsed, so the response it was holding no longer describes"
          + " any state this simulator has";

  private static final String STOPPING_WHILE_HELD =
      "the simulator held this response for a scenario delay and shut down before the delay"
          + " elapsed";

  private static final String UNDELIVERED =
      "the simulator held this response for a scenario delay and did not deliver it before the"
          + " asynchronous request deadline, which is simulator.fault.longest-delay plus a"
          + " margin; nothing about the operation itself failed";

  private final GrowLightApi api;
  private final DelayedDeliveries delays;
  private final long deliveryDeadlineMillis;

  GrowLightApiController(
      GrowLightApi api, DelayedDeliveries delays, FaultDelayCeiling faultDelayCeiling) {
    this.api = api;
    this.delays = delays;
    this.deliveryDeadlineMillis = faultDelayCeiling.deliveryDeadline().toMillis();
  }

  @PostMapping("/createPaymentProcess")
  Object createPaymentProcess(HttpServletRequest request) {
    return answered(() -> api.createPaymentProcess(GrowWireRequest.callOf(request)));
  }

  @PostMapping("/createTransactionWithToken")
  Object createTransactionWithToken(HttpServletRequest request) {
    return answered(() -> api.createTransactionWithToken(GrowWireRequest.callOf(request)));
  }

  @PostMapping("/settleSuspendedTransaction")
  Object settleSuspendedTransaction(HttpServletRequest request) {
    return answered(() -> api.settleSuspendedTransaction(GrowWireRequest.callOf(request)));
  }

  @PostMapping("/refundTransaction")
  Object refundTransaction(HttpServletRequest request) {
    return answered(() -> api.refundTransaction(GrowWireRequest.callOf(request)));
  }

  @PostMapping("/getTransactionInfo")
  Object getTransactionInfo(HttpServletRequest request) {
    return answered(() -> api.getTransactionInfo(GrowWireRequest.callOf(request)));
  }

  @PostMapping("/getPaymentProcessInfo")
  Object getPaymentProcessInfo(HttpServletRequest request) {
    return answered(() -> api.getPaymentProcessInfo(GrowWireRequest.callOf(request)));
  }

  @PostMapping({
    "/getTokenTransactionsByExternalIdentifiers",
    "/getTokenTransactionsByExternalIdentifiers/"
  })
  Object getTokenTransactionsByExternalIdentifiers(HttpServletRequest request) {
    return answered(
        () -> api.getTokenTransactionsByExternalIdentifiers(GrowWireRequest.callOf(request)));
  }

  @GetMapping({"/updateMyUrl", "/updateMyUrl/"})
  Object updateMyUrl(HttpServletRequest request) {
    return answered(() -> api.updateMyUrl(GrowWireRequest.queryCallOf(request)));
  }

  @PostMapping("/cancelBitTransaction")
  Object cancelBitTransaction(HttpServletRequest request) {
    return answered(() -> api.cancelBitTransaction(GrowWireRequest.callOf(request)));
  }

  @PostMapping("/approveTransaction")
  Object approveTransaction(HttpServletRequest request) {
    return answered(() -> api.approveTransaction(GrowWireRequest.callOf(request)));
  }

  private Object answered(Supplier<GrowApiResponse> operation) {
    return delivered(operation.get());
  }

  private Object delivered(GrowApiResponse response) {
    Duration delay = response.delay();
    if (delay.isZero()) {
      return written(response);
    }
    DeferredResult<ResponseEntity<byte[]>> held = new DeferredResult<>(deliveryDeadlineMillis);
    HeldDelivery delivery =
        delays.hold(
            GrowLightApi.GROW,
            response.testRunId(),
            delay,
            () -> held.setResult(written(response)),
            reason -> held.setResult(abandoned(reason)));
    held.onTimeout(
        () -> {
          if (delivery.settle()) {
            held.setResult(diagnostic(HttpStatus.GATEWAY_TIMEOUT.value(), UNDELIVERED));
          }
        });
    held.onError(error -> delivery.settle());
    return held;
  }

  private static ResponseEntity<byte[]> abandoned(DeliveryAbandonment reason) {
    return diagnostic(
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        switch (reason) {
          case TEST_RUN_RESET -> RESET_WHILE_HELD;
          case SIMULATOR_STOPPING -> STOPPING_WHILE_HELD;
        });
  }

  private static ResponseEntity<byte[]> diagnostic(int httpStatus, String explanation) {
    byte[] body = explanation.getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.status(httpStatus)
        .header("Content-Type", DIAGNOSTIC_CONTENT_TYPE)
        .contentLength(body.length)
        .body(body);
  }

  private static ResponseEntity<byte[]> written(GrowApiResponse response) {
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
