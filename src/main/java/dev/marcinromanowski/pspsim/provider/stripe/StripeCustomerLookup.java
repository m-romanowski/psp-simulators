package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;
import java.util.function.Supplier;

final class StripeCustomerLookup {

  private StripeCustomerLookup() {}

  static Optional<StripeCustomer> of(
      StripeCustomers customers, TestRunId testRunId, StripeAccount account, String customerId) {
    return customers
        .find(testRunId, customerId)
        .filter(customer -> customer.accountId().equals(account.accountId()));
  }

  static StripeOperationResult refusedBecauseItIsDeleted(StripeCustomer customer) {
    return StripeOperationResult.refused(
        RefusalSubject.theObject(customer.customerId()),
        StripeErrors.furtherOperationRefused(
            StripeCustomerObject.A_DELETED_CUSTOMER_TAKES_NO_FURTHER_OPERATION));
  }

  static StripeOperationResult answeredFor(
      StripeCustomers.Changed applied,
      StripeCustomer customer,
      Supplier<StripeOperationResult> whenApplied) {
    return switch (applied) {
      case APPLIED -> whenApplied.get();
      case ALREADY_DELETED -> refusedBecauseItIsDeleted(customer);
      case OBJECT_GONE ->
          StripeOperationResult.refused(
              RefusalSubject.noObjectAnsweredTo(StripeFields.CUSTOMER),
              StripeErrors.resourceMissing(StripeFields.CUSTOMER));
    };
  }
}
