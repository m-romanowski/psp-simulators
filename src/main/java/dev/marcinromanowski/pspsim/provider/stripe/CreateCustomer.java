package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

final class CreateCustomer {

  static final int ID_ATTEMPTS = 5;

  static final String ID_PREFIX = "cus_sim_";

  static final int ID_LENGTH = 16;

  private final StripeCustomers customers;
  private final IdGenerator ids;

  CreateCustomer(StripeCustomers customers, IdGenerator ids) {
    this.customers = customers;
    this.ids = ids;
  }

  static String aCustomerId(IdGenerator ids) {
    return ID_PREFIX + ids.hex(ID_LENGTH);
  }

  StripeOperationResult handle(
      StripeForm form,
      TestRunId testRunId,
      StripeAccount account,
      Instant now,
      ObjectOrigin askedBy) {
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      StripeCustomer customer =
          new StripeCustomer(
              testRunId,
              aCustomerId(ids),
              account.accountId(),
              form.first(StripeFields.EMAIL),
              null,
              askedBy,
              now,
              now);
      if (customers.insertIfAbsent(customer)) {
        return StripeOperationResult.succeeded(
            customer.customerId(), StripeCustomerView.of(customer));
      }
    }
    throw new IllegalStateException(
        "no Stripe customer id could be claimed in " + ID_ATTEMPTS + " attempts");
  }
}
