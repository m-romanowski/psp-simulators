package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.util.LinkedHashMap;
import java.util.Map;

final class StripeSetupIntentObject {

  static final String ACCOUNT = "account";

  static final String A_SETUP_INTENT_IS_NOT_A_PAYMENT =
      "provider stripe moves no money on a SetupIntent. Stripe publishes it as the object that"
          + " sets a payment method up for later payments, so it carries no amount, no currency"
          + " and no operation list, and every operation stripe publishes for capturing,"
          + " cancelling or refunding names a PaymentIntent. The payment this setup was made for"
          + " begins at "
          + StripeApiController.PAYMENT_INTENTS
          + " with the payment method this setup attached, and it is that PaymentIntent that this"
          + " simulator offers those actions on.";

  private StripeSetupIntentObject() {}

  static ProviderObjectState stateOf(StripeSetupIntent intent) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(intent.setupIntentId()),
        StripeObjectKinds.ofACardBeingSetUp(),
        StripeSetupIntentView.OBJECT + " " + intent.status().wireValue(),
        intent.normalizedStatus(),
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjectsOf(intent),
        intent.origin(),
        intent.updatedAt());
  }

  static ObjectActionOutcome noActionMovesMoneyOnASetupIntent() {
    return new ObjectActionOutcome.RefusedByTheProvider(A_SETUP_INTENT_IS_NOT_A_PAYMENT);
  }

  private static Map<String, String> relatedObjectsOf(StripeSetupIntent intent) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put(ACCOUNT, intent.accountId());
    String customer = intent.customerId();
    if (customer != null) {
      related.put(StripeFields.CUSTOMER, customer);
    }
    String paymentMethod = intent.paymentMethodId();
    if (paymentMethod != null) {
      related.put("paymentMethod", paymentMethod);
    }
    return related;
  }
}
