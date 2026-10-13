package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.util.LinkedHashMap;
import java.util.Map;

final class StripePaymentMethodObject {

  static final String ACCOUNT = "account";

  static final String A_METHOD_A_CUSTOMER_HOLDS = StripePaymentMethodView.OBJECT;

  static final String A_METHOD_NO_CUSTOMER_HOLDS =
      StripePaymentMethodView.OBJECT + " " + StripeFields.CUSTOMER + "=null";

  static final String A_PAYMENT_METHOD_IS_NOT_A_PAYMENT =
      "provider stripe moves no money on a PaymentMethod. A PaymentMethod carries no amount, no"
          + " currency and no operation list, and every operation stripe publishes for capturing,"
          + " cancelling or refunding names a PaymentIntent. A payment with this method begins at "
          + StripeApiController.PAYMENT_INTENTS
          + " with the method named in its published payment_method field, and it is that"
          + " PaymentIntent that this simulator offers those actions on.";

  private StripePaymentMethodObject() {}

  static ProviderObjectState stateOf(StripePaymentMethod method) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(method.paymentMethodId()),
        StripeObjectKinds.ofASavedCard(),
        method.attached() ? A_METHOD_A_CUSTOMER_HOLDS : A_METHOD_NO_CUSTOMER_HOLDS,
        method.detached() ? NormalizedStatus.CANCELLED : NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjectsOf(method),
        method.origin(),
        method.updatedAt());
  }

  static ObjectActionOutcome noActionMovesMoneyOnAPaymentMethod() {
    return new ObjectActionOutcome.RefusedByTheProvider(A_PAYMENT_METHOD_IS_NOT_A_PAYMENT);
  }

  private static Map<String, String> relatedObjectsOf(StripePaymentMethod method) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put(ACCOUNT, method.accountId());
    String customer = method.customerId();
    if (customer != null) {
      related.put(StripeFields.CUSTOMER, customer);
    }
    return related;
  }
}
