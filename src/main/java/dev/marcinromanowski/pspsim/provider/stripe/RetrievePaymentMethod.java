package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class RetrievePaymentMethod {

  static final String EXPAND = "expand[]";

  private final StripePaymentMethods methods;
  private final StripeCustomers customers;

  RetrievePaymentMethod(StripePaymentMethods methods, StripeCustomers customers) {
    this.methods = methods;
    this.customers = customers;
  }

  StripeOperationResult handle(
      StripeForm query, TestRunId testRunId, StripeAccount account, String paymentMethodId) {
    Optional<StripePaymentMethod> addressed =
        StripePaymentMethodLookup.of(methods, testRunId, account, paymentMethodId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeFields.PAYMENT_METHOD),
          StripeErrors.resourceMissing(StripeFields.PAYMENT_METHOD));
    }
    StripePaymentMethod method = addressed.get();
    return StripeOperationResult.answered(
        method.paymentMethodId(),
        StripePaymentMethodView.of(method, expandedCustomerOf(query, testRunId, account, method)));
  }

  private @Nullable StripeJson expandedCustomerOf(
      StripeForm query, TestRunId testRunId, StripeAccount account, StripePaymentMethod method) {
    String customerId = method.customerId();
    if (customerId == null || !query.carries(EXPAND, StripeFields.CUSTOMER)) {
      return null;
    }
    return StripeCustomerLookup.of(customers, testRunId, account, customerId)
        .map(StripeCustomerView::of)
        .orElse(null);
  }
}
