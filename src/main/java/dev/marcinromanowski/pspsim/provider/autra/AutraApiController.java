package dev.marcinromanowski.pspsim.provider.autra;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
class AutraApiController {

  static final String OAUTH_TOKEN = "/v1/oauth/token";

  static final String PAYMENTS = "/v1/acquiring/payments";

  static final String CARD_SESSIONS = "/v1/acquiring/card-sessions";

  static final String INITIALIZE = PAYMENTS + "/initialize";

  static final String PRE_AUTHORIZATION = PAYMENTS + "/pre-authorization";

  static final String OF_A_PAYMENT = PAYMENTS + "/{paymentId}";

  static final String PIX = "/v1/acquiring/pix";

  static final String PIX_INITIALIZE = PIX + "/initialize";

  static final String PIX_CHECKOUT = PIX + "/checkout";

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

  private final AutraApi api;
  private final DelayedDeliveries delays;
  private final long deliveryDeadlineMillis;

  AutraApiController(AutraApi api, DelayedDeliveries delays, FaultDelayCeiling faultDelayCeiling) {
    this.api = api;
    this.delays = delays;
    this.deliveryDeadlineMillis = faultDelayCeiling.deliveryDeadline().toMillis();
  }

  @GetMapping(OF_A_PAYMENT)
  Object payment(HttpServletRequest request, @PathVariable String paymentId) {
    return answered(() -> api.payment(AutraWireRequest.callOf(request), paymentId));
  }

  @GetMapping(PAYMENTS)
  Object payments(HttpServletRequest request) {
    return answered(() -> api.payments(AutraWireRequest.callOf(request)));
  }

  @PostMapping(OAUTH_TOKEN)
  Object token(HttpServletRequest request) {
    return answered(() -> api.token(AutraWireRequest.callOf(request)));
  }

  @PostMapping(CARD_SESSIONS)
  Object cardSession(HttpServletRequest request) {
    return answered(() -> api.cardSession(AutraWireRequest.callOf(request)));
  }

  @PostMapping(PAYMENTS)
  Object sale(HttpServletRequest request) {
    return answered(() -> api.sale(AutraWireRequest.callOf(request)));
  }

  @PostMapping(INITIALIZE)
  Object initialize(HttpServletRequest request) {
    return answered(() -> api.initialize(AutraWireRequest.callOf(request)));
  }

  @PostMapping(PIX_INITIALIZE)
  Object pixInitialize(HttpServletRequest request) {
    return answered(() -> api.pixInitialize(AutraWireRequest.callOf(request)));
  }

  @PostMapping(PIX_CHECKOUT)
  Object pixCheckout(HttpServletRequest request) {
    return answered(() -> api.pixCheckout(AutraWireRequest.callOf(request)));
  }

  @PostMapping(PRE_AUTHORIZATION)
  Object preAuthorization(HttpServletRequest request) {
    return answered(() -> api.preAuthorization(AutraWireRequest.callOf(request)));
  }

  @PostMapping(OF_A_PAYMENT + "/capture")
  Object capture(HttpServletRequest request, @PathVariable String paymentId) {
    return answered(() -> api.capture(AutraWireRequest.callOf(request), paymentId));
  }

  @PostMapping(OF_A_PAYMENT + "/void")
  Object voidPayment(HttpServletRequest request, @PathVariable String paymentId) {
    return answered(() -> api.voidPayment(AutraWireRequest.callOf(request), paymentId));
  }

  private Object answered(Supplier<AutraApiResponse> operation) {
    return delivered(operation.get());
  }

  private Object delivered(AutraApiResponse response) {
    Duration delay = response.delay();
    if (delay.isZero()) {
      return written(response);
    }
    DeferredResult<ResponseEntity<byte[]>> held = new DeferredResult<>(deliveryDeadlineMillis);
    HeldDelivery delivery =
        delays.hold(
            AutraApi.AUTRA,
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

  private static ResponseEntity<byte[]> written(AutraApiResponse response) {
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
