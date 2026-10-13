package dev.marcinromanowski.pspsim.provider.worldline;

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
class WorldlineApiController {

  static final String VERSION = "/v2";

  static final String OF_A_MERCHANT = VERSION + "/{merchantId}";

  static final String HOSTED_TOKENIZATIONS = OF_A_MERCHANT + "/hostedtokenizations";

  static final String OF_A_HOSTED_TOKENIZATION = HOSTED_TOKENIZATIONS + "/{hostedTokenizationId}";

  static final String PAYMENTS = OF_A_MERCHANT + "/payments";

  static final String OF_A_PAYMENT = PAYMENTS + "/{paymentId}";

  static final String CAPTURE = "/capture";

  static final String CANCEL = "/cancel";

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

  private final WorldlineApi api;
  private final DelayedDeliveries delays;
  private final long deliveryDeadlineMillis;

  WorldlineApiController(
      WorldlineApi api, DelayedDeliveries delays, FaultDelayCeiling faultDelayCeiling) {
    this.api = api;
    this.delays = delays;
    this.deliveryDeadlineMillis = faultDelayCeiling.deliveryDeadline().toMillis();
  }

  static String theHostedTokenizationsOf(String merchantId) {
    return VERSION + "/" + merchantId + "/hostedtokenizations";
  }

  static String theHostedTokenizationOf(String merchantId, String hostedTokenizationId) {
    return theHostedTokenizationsOf(merchantId) + "/" + hostedTokenizationId;
  }

  static String thePaymentsOf(String merchantId) {
    return VERSION + "/" + merchantId + "/payments";
  }

  static String thePaymentResourceOf(String merchantId, String paymentId) {
    return thePaymentsOf(merchantId) + "/" + paymentId;
  }

  @PostMapping(HOSTED_TOKENIZATIONS)
  Object createHostedTokenization(HttpServletRequest request, @PathVariable String merchantId) {
    return answered(
        () -> api.createHostedTokenization(WorldlineWireRequest.callOf(request), merchantId));
  }

  @GetMapping(OF_A_HOSTED_TOKENIZATION)
  Object getHostedTokenization(
      HttpServletRequest request,
      @PathVariable String merchantId,
      @PathVariable String hostedTokenizationId) {
    return answered(
        () ->
            api.getHostedTokenization(
                WorldlineWireRequest.callOf(request), merchantId, hostedTokenizationId));
  }

  @PostMapping(PAYMENTS)
  Object createPayment(HttpServletRequest request, @PathVariable String merchantId) {
    return answered(() -> api.createPayment(WorldlineWireRequest.callOf(request), merchantId));
  }

  @PostMapping(OF_A_PAYMENT + CAPTURE)
  Object capturePayment(
      HttpServletRequest request, @PathVariable String merchantId, @PathVariable String paymentId) {
    return answered(
        () -> api.capturePayment(WorldlineWireRequest.callOf(request), merchantId, paymentId));
  }

  @PostMapping(OF_A_PAYMENT + CANCEL)
  Object cancelPayment(
      HttpServletRequest request, @PathVariable String merchantId, @PathVariable String paymentId) {
    return answered(
        () -> api.cancelPayment(WorldlineWireRequest.callOf(request), merchantId, paymentId));
  }

  private Object answered(Supplier<WorldlineApiResponse> operation) {
    return delivered(operation.get());
  }

  private Object delivered(WorldlineApiResponse response) {
    Duration delay = response.delay();
    if (delay.isZero()) {
      return written(response);
    }
    DeferredResult<ResponseEntity<byte[]>> held = new DeferredResult<>(deliveryDeadlineMillis);
    HeldDelivery delivery =
        delays.hold(
            WorldlineApi.WORLDLINE,
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

  private static ResponseEntity<byte[]> written(WorldlineApiResponse response) {
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
