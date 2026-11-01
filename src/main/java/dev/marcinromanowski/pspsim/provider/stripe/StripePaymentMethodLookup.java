package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;

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

  static final List<String> THE_STEMS_OF_THE_PUBLISHED_TEST_METHODS =
      List.of("pm_card_", "pm_usBankAccount_", "pm_sepaDebit_");

  static final String A_METHOD_OF_ANOTHER_CUSTOMER_PAYS_FOR_NO_INTENT =
      "Payment methods attached to other Customers cannot be used with this PaymentIntent. This"
          + " PaymentMethod is attached to a Customer other than the one this PaymentIntent names,"
          + " and provider stripe publishes that a PaymentMethod attached to a Customer must match"
          + " the customer set on the PaymentIntent.";

  static final String A_METHOD_OF_A_CUSTOMER_PAYS_FOR_NO_INTENT_WITHOUT_ONE =
      "Payment methods attached to other Customers cannot be used with this PaymentIntent. This"
          + " PaymentMethod is attached to a Customer and this PaymentIntent names none, and"
          + " provider stripe publishes that a PaymentMethod attached to a Customer must match the"
          + " customer set on the PaymentIntent.";

  private StripePaymentMethodLookup() {}

  static boolean aPublishedTestMethod(String paymentMethodId) {
    for (String stem : THE_STEMS_OF_THE_PUBLISHED_TEST_METHODS) {
      if (paymentMethodId.startsWith(stem)) {
        return true;
      }
    }
    return false;
  }

  static Optional<StripePaymentMethod> of(
      StripePaymentMethods methods,
      TestRunId testRunId,
      StripeAccount account,
      String paymentMethodId) {
    return methods
        .find(testRunId, paymentMethodId)
        .filter(method -> method.accountId().equals(account.accountId()));
  }
}
