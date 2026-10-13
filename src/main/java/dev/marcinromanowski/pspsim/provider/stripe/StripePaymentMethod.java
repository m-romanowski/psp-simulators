package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record StripePaymentMethod(
    TestRunId testRunId,
    String paymentMethodId,
    String accountId,
    @Nullable String customerId,
    StripePaymentMethodType type,
    @Nullable FixtureCard card,
    @Nullable Instant detachedAt,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final int LONGEST_PAYMENT_METHOD_ID = 128;

  static final String A_CARD_IS_CARRIED_BY_THE_TYPE_THAT_PUBLISHES_ONE =
      "provider stripe publishes a card hash on a PaymentMethod of type card and on no other, so"
          + " a stored payment method carries the card facts exactly when its type is card";

  static final String A_DETACHED_METHOD_BELONGS_TO_NOBODY =
      "provider stripe publishes that detachment is permanent and irreversible, so a payment"
          + " method that carries a detachment and a customer at the same time would be a method"
          + " this simulator had re-attached";

  StripePaymentMethod {
    if (type.describesACard() == (card == null)) {
      throw new IllegalArgumentException(A_CARD_IS_CARRIED_BY_THE_TYPE_THAT_PUBLISHES_ONE);
    }
    if (detachedAt != null && customerId != null) {
      throw new IllegalArgumentException(A_DETACHED_METHOD_BELONGS_TO_NOBODY);
    }
  }

  boolean detached() {
    return detachedAt != null;
  }

  boolean attached() {
    return customerId != null;
  }

  StripePaymentMethod detachedAt(Instant at) {
    return new StripePaymentMethod(
        testRunId, paymentMethodId, accountId, null, type, card, at, origin, createdAt, at);
  }
}
