package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;

final class RetrieveCustomer {

  private final StripeCustomers customers;

  RetrieveCustomer(StripeCustomers customers) {
    this.customers = customers;
  }

  StripeOperationResult handle(TestRunId testRunId, StripeAccount account, String customerId) {
    Optional<StripeCustomer> addressed =
        StripeCustomerLookup.of(customers, testRunId, account, customerId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeFields.CUSTOMER),
          StripeErrors.resourceMissing(StripeFields.CUSTOMER));
    }
    StripeCustomer customer = addressed.get();
    return StripeOperationResult.answered(customer.customerId(), StripeCustomerView.of(customer));
  }
}
