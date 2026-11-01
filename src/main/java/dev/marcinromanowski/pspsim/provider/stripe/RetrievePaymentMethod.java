package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;

final class RetrievePaymentMethod {

  private final StripePaymentMethods methods;

  RetrievePaymentMethod(StripePaymentMethods methods) {
    this.methods = methods;
  }

  StripeOperationResult handle(TestRunId testRunId, StripeAccount account, String paymentMethodId) {
    return StripePaymentMethodLookup.of(methods, testRunId, account, paymentMethodId)
        .map(
            method ->
                StripeOperationResult.answered(
                    method.paymentMethodId(), StripePaymentMethodView.of(method)))
        .orElseGet(
            () ->
                StripeOperationResult.refusedBeforeExecuting(
                    RefusalSubject.noObjectAnsweredTo(StripeFields.PAYMENT_METHOD),
                    StripeErrors.paymentMethodNotFound(paymentMethodId)));
  }
}
