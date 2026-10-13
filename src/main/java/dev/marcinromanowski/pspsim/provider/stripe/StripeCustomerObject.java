package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.util.Map;

final class StripeCustomerObject {

  static final String ACCOUNT = "account";

  static final String A_CUSTOMER_STRIPE_STILL_HOLDS = StripeCustomerView.OBJECT;

  static final String A_CUSTOMER_STRIPE_HAS_DELETED =
      StripeCustomerView.OBJECT + " " + StripeCustomerView.DELETED + "=" + StripeFlag.TRUE;

  static final String A_DELETED_CUSTOMER_TAKES_NO_FURTHER_OPERATION =
      "Deleting customers prevents any further operations to be performed. This customer has been"
          + " deleted. It can still be retrieved, which is what provider stripe publishes for a"
          + " deleted customer so that its history stays readable, and it can no longer be"
          + " updated or deleted again.";

  static final String A_CUSTOMER_IS_NOT_A_PAYMENT =
      "provider stripe moves no money on a customer. A customer carries no amount, no currency and"
          + " no operation list, and every operation stripe publishes for capturing, cancelling or"
          + " refunding names a PaymentIntent. A payment of this customer begins at "
          + StripeApiController.PAYMENT_INTENTS
          + " with the customer named in its published customer field, and it is that PaymentIntent"
          + " that this simulator offers those actions on.";

  private StripeCustomerObject() {}

  static ProviderObjectState stateOf(StripeCustomer customer) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(customer.customerId()),
        StripeObjectKinds.ofACustomer(),
        customer.deleted() ? A_CUSTOMER_STRIPE_HAS_DELETED : A_CUSTOMER_STRIPE_STILL_HOLDS,
        customer.deleted() ? NormalizedStatus.CANCELLED : NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(ACCOUNT, customer.accountId()),
        customer.origin(),
        customer.updatedAt());
  }

  static ObjectActionOutcome noActionMovesMoneyOnACustomer() {
    return new ObjectActionOutcome.RefusedByTheProvider(A_CUSTOMER_IS_NOT_A_PAYMENT);
  }
}
