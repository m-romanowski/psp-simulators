package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class UpdateCustomer {

  private final StripeCustomers customers;

  UpdateCustomer(StripeCustomers customers) {
    this.customers = customers;
  }

  StripeOperationResult handle(
      StripeForm form, TestRunId testRunId, StripeAccount account, String customerId, Instant now) {
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
    String email = emailAfter(form, customer);
    return StripeCustomerLookup.answeredFor(
        customers.updated(customer, email, now),
        customer,
        () ->
            StripeOperationResult.succeeded(
                customer.customerId(), StripeCustomerView.of(customer.withEmail(email, now))));
  }

  private static @Nullable String emailAfter(StripeForm form, StripeCustomer customer) {
    return form.has(StripeFields.EMAIL) ? form.required(StripeFields.EMAIL) : customer.email();
  }
}
