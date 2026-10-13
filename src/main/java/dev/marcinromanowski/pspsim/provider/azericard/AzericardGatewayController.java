package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveryAbandonment;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fault.HeldDelivery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
class AzericardGatewayController {

  static final String CGI_LINK = "/cgi-bin/cgi_link";

  static final String TOKEN_CGI_LINK = "/token/cgi_link";

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

  private final AzericardGateway gateway;
  private final AzericardPayerCheckout checkout;
  private final DelayedDeliveries delays;
  private final long deliveryDeadlineMillis;

  AzericardGatewayController(
      AzericardGateway gateway,
      AzericardPayerCheckout checkout,
      DelayedDeliveries delays,
      FaultDelayCeiling faultDelayCeiling) {
    this.gateway = gateway;
    this.checkout = checkout;
    this.delays = delays;
    this.deliveryDeadlineMillis = faultDelayCeiling.deliveryDeadline().toMillis();
  }

  @PostMapping(CGI_LINK)
  Object cgiLink(HttpServletRequest request) {
    return answered(request, gateway::cgiLink, Map.of());
  }

  @PostMapping(TOKEN_CGI_LINK)
  Object tokenLink(HttpServletRequest request) {
    return answered(request, gateway::tokenLink, Map.of());
  }

  @PostMapping(AzericardPayerPage.PATH)
  Object payerPage(HttpServletRequest request, HttpServletResponse response) {
    try {
      return answered(request, checkout::submit, AzericardPayerPage.HEADERS);
    } catch (RuntimeException theAnswerThisPageOwesCouldNotBeBuilt) {
      AzericardPayerPage.defencesOnto(response);
      throw theAnswerThisPageOwesCouldNotBeBuilt;
    }
  }

  private Object answered(
      HttpServletRequest request,
      Function<AzericardApiCall, AzericardApiResponse> route,
      Map<String, List<String>> onEveryAnswer) {
    return delivered(route.apply(AzericardWireRequest.callOf(request)), onEveryAnswer);
  }

  private Object delivered(AzericardApiResponse response, Map<String, List<String>> onEveryAnswer) {
    Duration delay = response.delay();
    if (delay.isZero()) {
      return written(response);
    }
    DeferredResult<ResponseEntity<byte[]>> held = new DeferredResult<>(deliveryDeadlineMillis);
    HeldDelivery delivery =
        delays.hold(
            AzericardGateway.AZERICARD,
            response.testRunId(),
            delay,
            () -> held.setResult(written(response)),
            reason -> held.setResult(abandoned(reason, onEveryAnswer)));
    held.onTimeout(
        () -> {
          if (delivery.settle()) {
            held.setResult(
                diagnostic(HttpStatus.GATEWAY_TIMEOUT.value(), UNDELIVERED, onEveryAnswer));
          }
        });
    held.onError(error -> delivery.settle());
    return held;
  }

  private static ResponseEntity<byte[]> abandoned(
      DeliveryAbandonment reason, Map<String, List<String>> onEveryAnswer) {
    return diagnostic(
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        switch (reason) {
          case TEST_RUN_RESET -> RESET_WHILE_HELD;
          case SIMULATOR_STOPPING -> STOPPING_WHILE_HELD;
        },
        onEveryAnswer);
  }

  private static ResponseEntity<byte[]> diagnostic(
      int httpStatus, String explanation, Map<String, List<String>> onEveryAnswer) {
    return written(AzericardApiResponse.aDiagnostic(httpStatus, explanation, onEveryAnswer));
  }

  private static ResponseEntity<byte[]> written(AzericardApiResponse response) {
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
