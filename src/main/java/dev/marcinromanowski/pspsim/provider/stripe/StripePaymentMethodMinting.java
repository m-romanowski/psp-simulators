package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;

final class StripePaymentMethodMinting {

  static final String ID_PREFIX = "pm_sim_";

  static final int ID_LENGTH = 20;

  static final int ID_ATTEMPTS = 5;

  private final StripePaymentMethods methods;
  private final IdGenerator ids;

  StripePaymentMethodMinting(StripePaymentMethods methods, IdGenerator ids) {
    this.methods = methods;
    this.ids = ids;
  }

  static String aPaymentMethodId(IdGenerator ids) {
    return ID_PREFIX + ids.hex(ID_LENGTH);
  }

  StripePaymentMethods store() {
    return methods;
  }

  StripePaymentMethod described(
      TestRunId testRunId,
      StripeAccount account,
      StripeNewPaymentMethod.Description description,
      ObjectOrigin askedBy,
      Instant now) {
    return new StripePaymentMethod(
        testRunId,
        anUnheldId(testRunId),
        account.accountId(),
        null,
        StripePaymentMethodType.CARD,
        description.card(),
        description.billingDetails(),
        description.metadata(),
        description.allowRedisplay(),
        null,
        askedBy,
        now,
        now);
  }

  StripePaymentMethod clonedFrom(
      StripeTestCards.TestCard published,
      TestRunId testRunId,
      StripeAccount account,
      ObjectOrigin askedBy,
      Instant now) {
    return new StripePaymentMethod(
        testRunId,
        anUnheldId(testRunId),
        account.accountId(),
        null,
        StripePaymentMethodType.CARD,
        StripeTestCards.theCardATestNameStandsFor(published, now),
        StripeBillingDetails.NONE,
        Map.of(),
        StripePaymentMethod.UNSPECIFIED,
        null,
        askedBy,
        now,
        now);
  }

  void stored(StripePaymentMethod minted) {
    if (!methods.insertIfAbsent(minted)) {
      throw new IllegalStateException(
          "the Stripe PaymentMethod id "
              + minted.paymentMethodId()
              + " this request minted was taken by another request before it could be stored");
    }
  }

  private String anUnheldId(TestRunId testRunId) {
    for (int attempt = 0; attempt < ID_ATTEMPTS; attempt++) {
      String paymentMethodId = aPaymentMethodId(ids);
      if (methods.find(testRunId, paymentMethodId).isEmpty()) {
        return paymentMethodId;
      }
    }
    throw new IllegalStateException(
        "no Stripe PaymentMethod id could be drawn in " + ID_ATTEMPTS + " attempts");
  }
}
