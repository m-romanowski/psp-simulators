package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;

final class DetachPaymentMethod {

  private final StripePaymentMethods methods;

  DetachPaymentMethod(StripePaymentMethods methods) {
    this.methods = methods;
  }

  StripeOperationResult handle(
      TestRunId testRunId, StripeAccount account, String paymentMethodId, Instant now) {
    Optional<StripePaymentMethod> addressed =
        StripePaymentMethodLookup.of(methods, testRunId, account, paymentMethodId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeFields.PAYMENT_METHOD),
          StripeErrors.resourceMissing(StripeFields.PAYMENT_METHOD));
    }
    StripePaymentMethod method = addressed.get();
    if (method.detached()) {
      return refusedAsAlreadyDetached(method);
    }
    if (!method.attached()) {
      return StripeOperationResult.refused(
          RefusalSubject.theObject(method.paymentMethodId()),
          StripeErrors.paymentMethodRefused(
              StripePaymentMethodLookup.A_METHOD_NO_CUSTOMER_HOLDS_HAS_NONE_TO_DETACH_FROM));
    }
    return switch (methods.detached(method, now)) {
      case APPLIED ->
          StripeOperationResult.succeeded(
              method.paymentMethodId(), StripePaymentMethodView.of(method.detachedAt(now)));
      case ALREADY_DETACHED -> refusedAsAlreadyDetached(method);
      case OBJECT_GONE ->
          StripeOperationResult.refused(
              RefusalSubject.noObjectAnsweredTo(StripeFields.PAYMENT_METHOD),
              StripeErrors.resourceMissing(StripeFields.PAYMENT_METHOD));
    };
  }

  private static StripeOperationResult refusedAsAlreadyDetached(StripePaymentMethod method) {
    return StripeOperationResult.refused(
        RefusalSubject.theObject(method.paymentMethodId()),
        StripeErrors.paymentMethodRefused(
            StripePaymentMethodLookup.A_DETACHED_METHOD_IS_REFUSED_RATHER_THAN_IGNORED));
  }
}
