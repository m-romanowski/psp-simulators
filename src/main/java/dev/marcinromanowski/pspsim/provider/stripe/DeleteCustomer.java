package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;

final class DeleteCustomer {

  private final StripeCustomers customers;
  private final StripePaymentMethods paymentMethods;

  DeleteCustomer(StripeCustomers customers, StripePaymentMethods paymentMethods) {
    this.customers = customers;
    this.paymentMethods = paymentMethods;
  }

  private StripeOperationResult theCustomerAndTheCardDetailsItHeld(
      StripeCustomer customer, Instant now) {
    paymentMethods.detachedEveryMethodOf(
        customer.testRunId(), customer.accountId(), customer.customerId(), now);
    return StripeOperationResult.succeeded(
        customer.customerId(), StripeCustomerView.deletedFormOf(customer));
  }

  StripeOperationResult handle(
      TestRunId testRunId, StripeAccount account, String customerId, Instant now) {
    Optional<StripeCustomer> addressed =
        StripeCustomerLookup.of(customers, testRunId, account, customerId);
    if (addressed.isEmpty()) {
      return StripeOperationResult.refusedBeforeExecuting(
          RefusalSubject.noObjectAnsweredTo(StripeFields.CUSTOMER),
          StripeErrors.resourceMissing(StripeFields.CUSTOMER));
    }
    StripeCustomer customer = addressed.get();
    if (customer.deleted()) {
      return StripeCustomerLookup.refusedBecauseItIsDeleted(customer);
    }
    return StripeCustomerLookup.answeredFor(
        customers.deleted(customer, now),
        customer,
        () -> theCustomerAndTheCardDetailsItHeld(customer, now));
  }
}
