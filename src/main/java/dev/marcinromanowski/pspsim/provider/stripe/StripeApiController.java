package dev.marcinromanowski.pspsim.provider.stripe;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
class StripeApiController {

  static final String PAYMENT_INTENTS = "/v1/payment_intents";

  static final String REFUNDS = "/v1/refunds";

  static final String CHARGES = "/v1/charges";

  static final String CUSTOMERS = "/v1/customers";

  static final String PAYMENT_METHODS = "/v1/payment_methods";

  static final String OF_A_CUSTOMER = "/payment_methods";

  static final String SETUP_INTENTS = "/v1/setup_intents";

  static final String EPHEMERAL_KEYS = "/v1/ephemeral_keys";

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

  private final StripeApi api;
  private final DelayedDeliveries delays;
  private final long deliveryDeadlineMillis;

  StripeApiController(
      StripeApi api, DelayedDeliveries delays, FaultDelayCeiling faultDelayCeiling) {
    this.api = api;
    this.delays = delays;
    this.deliveryDeadlineMillis = faultDelayCeiling.deliveryDeadline().toMillis();
  }

  @PostMapping(PAYMENT_INTENTS)
  Object createPaymentIntent(HttpServletRequest request) {
    return answered(() -> api.createPaymentIntent(StripeWireRequest.callOf(request)));
  }

  @GetMapping(PAYMENT_INTENTS + "/{intentId}")
  Object retrievePaymentIntent(HttpServletRequest request, @PathVariable String intentId) {
    return answered(() -> api.retrievePaymentIntent(StripeWireRequest.callOf(request), intentId));
  }

  @PostMapping(PAYMENT_INTENTS + "/{intentId}/confirm")
  Object confirmPaymentIntent(HttpServletRequest request, @PathVariable String intentId) {
    return answered(() -> api.confirmPaymentIntent(StripeWireRequest.callOf(request), intentId));
  }

  @PostMapping(PAYMENT_INTENTS + "/{intentId}/capture")
  Object capturePaymentIntent(HttpServletRequest request, @PathVariable String intentId) {
    return answered(() -> api.capturePaymentIntent(StripeWireRequest.callOf(request), intentId));
  }

  @PostMapping(PAYMENT_INTENTS + "/{intentId}/cancel")
  Object cancelPaymentIntent(HttpServletRequest request, @PathVariable String intentId) {
    return answered(() -> api.cancelPaymentIntent(StripeWireRequest.callOf(request), intentId));
  }

  @GetMapping(CHARGES + "/{chargeId}")
  Object retrieveCharge(HttpServletRequest request, @PathVariable String chargeId) {
    return answered(() -> api.retrieveCharge(StripeWireRequest.callOf(request), chargeId));
  }

  @PostMapping(REFUNDS)
  Object createRefund(HttpServletRequest request) {
    return answered(() -> api.createRefund(StripeWireRequest.callOf(request)));
  }

  @PostMapping(CUSTOMERS)
  Object createCustomer(HttpServletRequest request) {
    return answered(() -> api.createCustomer(StripeWireRequest.callOf(request)));
  }

  @GetMapping(CUSTOMERS + "/{customerId}")
  Object retrieveCustomer(HttpServletRequest request, @PathVariable String customerId) {
    return answered(() -> api.retrieveCustomer(StripeWireRequest.callOf(request), customerId));
  }

  @PostMapping(CUSTOMERS + "/{customerId}")
  Object updateCustomer(HttpServletRequest request, @PathVariable String customerId) {
    return answered(() -> api.updateCustomer(StripeWireRequest.callOf(request), customerId));
  }

  @DeleteMapping(CUSTOMERS + "/{customerId}")
  Object deleteCustomer(HttpServletRequest request, @PathVariable String customerId) {
    return answered(() -> api.deleteCustomer(StripeWireRequest.callOf(request), customerId));
  }

  @GetMapping(PAYMENT_METHODS)
  Object listPaymentMethods(HttpServletRequest request) {
    return answered(() -> api.listPaymentMethods(StripeWireRequest.callOf(request)));
  }

  @GetMapping(CUSTOMERS + "/{customerId}" + OF_A_CUSTOMER)
  Object listCustomerPaymentMethods(HttpServletRequest request, @PathVariable String customerId) {
    return answered(
        () -> api.listCustomerPaymentMethods(StripeWireRequest.callOf(request), customerId));
  }

  @GetMapping(PAYMENT_METHODS + "/{paymentMethodId}")
  Object retrievePaymentMethod(HttpServletRequest request, @PathVariable String paymentMethodId) {
    return answered(
        () -> api.retrievePaymentMethod(StripeWireRequest.callOf(request), paymentMethodId));
  }

  @PostMapping(PAYMENT_METHODS + "/{paymentMethodId}/detach")
  Object detachPaymentMethod(HttpServletRequest request, @PathVariable String paymentMethodId) {
    return answered(
        () -> api.detachPaymentMethod(StripeWireRequest.callOf(request), paymentMethodId));
  }

  @PostMapping(SETUP_INTENTS)
  Object createSetupIntent(HttpServletRequest request) {
    return answered(() -> api.createSetupIntent(StripeWireRequest.callOf(request)));
  }

  @PostMapping(SETUP_INTENTS + "/{setupIntentId}/confirm")
  Object confirmSetupIntent(HttpServletRequest request, @PathVariable String setupIntentId) {
    return answered(() -> api.confirmSetupIntent(StripeWireRequest.callOf(request), setupIntentId));
  }

  @PostMapping(EPHEMERAL_KEYS)
  Object createEphemeralKey(HttpServletRequest request) {
    return answered(() -> api.createEphemeralKey(StripeWireRequest.callOf(request)));
  }

  private Object answered(Supplier<StripeApiResponse> operation) {
    return delivered(operation.get());
  }

  private Object delivered(StripeApiResponse response) {
    Duration delay = response.delay();
    if (delay.isZero()) {
      return written(response);
    }
    DeferredResult<ResponseEntity<byte[]>> held = new DeferredResult<>(deliveryDeadlineMillis);
    HeldDelivery delivery =
        delays.hold(
            StripeApi.STRIPE,
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

  private static ResponseEntity<byte[]> written(StripeApiResponse response) {
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
