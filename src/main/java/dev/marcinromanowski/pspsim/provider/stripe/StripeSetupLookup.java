package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;

final class StripeSetupLookup {

  static final String SETUP_INTENT_PARAMETER = "intent";

  static final String A_METHOD_ANOTHER_CUSTOMER_HOLDS_IS_REFUSED =
      "Payment methods attached to other Customers cannot be used with this SetupIntent. This"
          + " PaymentMethod is attached to a Customer other than the one this SetupIntent names,"
          + " and provider stripe publishes that attachment as the reason it cannot be used here.";

  static final String THE_METHOD_IS_NO_LONGER_ATTACHABLE =
      "The payment method this SetupIntent would attach stopped being attachable while the setup"
          + " was running: provider stripe publishes that detachment is permanent and irreversible"
          + " and that a payment method attached to another Customer cannot be used with this"
          + " SetupIntent, and the store refused the attachment for one of those two reasons."
          + " Nothing was attached and the SetupIntent did not move.";

  private StripeSetupLookup() {}

  static Optional<StripeSetupIntent> of(
      StripeSetupIntents intents,
      TestRunId testRunId,
      StripeAccount account,
      String setupIntentId) {
    return intents
        .find(testRunId, setupIntentId)
        .filter(intent -> intent.accountId().equals(account.accountId()));
  }

  static StripeOperationResult unexpectedState(StripeSetupIntent intent, String attempted) {
    return StripeOperationResult.refused(
        RefusalSubject.theObject(intent.setupIntentId()),
        StripeErrors.setupUnexpectedState(
                "A SetupIntent with a status of "
                    + intent.status().wireValue()
                    + " cannot be "
                    + attempted
                    + ".")
            .aboutTheSetup(StripeSetupIntentView.theTruncatedFormAnErrorCarries(intent)));
  }
}
