package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class StripePaymentMethodLookup {

  static final String A_DETACHED_METHOD_IS_REFUSED_RATHER_THAN_IGNORED =
      "Detachment is permanent and irreversible. Once detached, a PaymentMethod can no longer be"
          + " used for payments or re-attached to a Customer. This PaymentMethod has been"
          + " detached, and this simulator refuses the request rather than ignoring it.";

  static final String A_METHOD_NO_CUSTOMER_HOLDS_HAS_NONE_TO_DETACH_FROM =
      "This PaymentMethod is not attached to a Customer, so there is no attachment for a detach to"
          + " end. Provider stripe publishes that customer is not set when the PaymentMethod has"
          + " not been saved to a Customer, and a method in that state has never been attached"
          + " rather than having been detached.";

  private StripePaymentMethodLookup() {}

  static Optional<StripePaymentMethod> of(
      StripePaymentMethods methods,
      TestRunId testRunId,
      StripeAccount account,
      String paymentMethodId) {
    return methods
        .find(testRunId, paymentMethodId)
        .filter(method -> method.accountId().equals(account.accountId()));
  }

  static @Nullable StripeError theRefusalADetachedMethodEarns(
      StripePaymentMethods methods,
      TestRunId testRunId,
      StripeAccount account,
      @Nullable String paymentMethodId) {
    if (paymentMethodId == null) {
      return null;
    }
    return of(methods, testRunId, account, paymentMethodId)
        .filter(StripePaymentMethod::detached)
        .map(
            detached ->
                StripeErrors.paymentMethodRefused(A_DETACHED_METHOD_IS_REFUSED_RATHER_THAN_IGNORED))
        .orElse(null);
  }
}
