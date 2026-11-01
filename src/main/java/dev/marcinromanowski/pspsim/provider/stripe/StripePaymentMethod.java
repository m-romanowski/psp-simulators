package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record StripePaymentMethod(
    TestRunId testRunId,
    String paymentMethodId,
    String accountId,
    @Nullable String customerId,
    StripePaymentMethodType type,
    @Nullable StripeCard card,
    StripeBillingDetails billingDetails,
    Map<String, String> metadata,
    String allowRedisplay,
    @Nullable String copiedFrom,
    @Nullable Instant detachedAt,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final int LONGEST_PAYMENT_METHOD_ID = 128;

  static final String UNSPECIFIED = "unspecified";

  static final List<String> THE_PUBLISHED_REDISPLAY_VALUES =
      List.of("always", "limited", UNSPECIFIED);

  static final String A_CARD_IS_CARRIED_BY_THE_TYPE_THAT_PUBLISHES_ONE =
      "provider stripe publishes a card hash on a PaymentMethod of type card and on no other, so"
          + " a stored payment method carries the card facts exactly when its type is card";

  static final String A_DETACHED_METHOD_BELONGS_TO_NOBODY =
      "provider stripe publishes that detachment is permanent and irreversible, so a payment"
          + " method that carries a detachment and a customer at the same time would be a method"
          + " this simulator had re-attached";

  static final String A_REDISPLAY_IS_ONE_STRIPE_PUBLISHES =
      "provider stripe publishes always, limited and unspecified as the allow_redisplay of a"
          + " PaymentMethod, and unspecified as its default";

  StripePaymentMethod {
    if (type.describesACard() == (card == null)) {
      throw new IllegalArgumentException(A_CARD_IS_CARRIED_BY_THE_TYPE_THAT_PUBLISHES_ONE);
    }
    if (detachedAt != null && customerId != null) {
      throw new IllegalArgumentException(A_DETACHED_METHOD_BELONGS_TO_NOBODY);
    }
    if (!THE_PUBLISHED_REDISPLAY_VALUES.contains(allowRedisplay)) {
      throw new IllegalArgumentException(A_REDISPLAY_IS_ONE_STRIPE_PUBLISHES);
    }
    metadata = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(metadata));
  }

  StripePaymentMethod(
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
    this(
        testRunId,
        paymentMethodId,
        accountId,
        customerId,
        type,
        card == null ? null : StripeCard.ofAFixture(card),
        StripeBillingDetails.NONE,
        Map.of(),
        UNSPECIFIED,
        null,
        detachedAt,
        origin,
        createdAt,
        updatedAt);
  }

  String theIdARuleNames() {
    return copiedFrom == null ? paymentMethodId : copiedFrom;
  }

  boolean detached() {
    return detachedAt != null;
  }

  boolean attached() {
    return customerId != null;
  }

  StripePaymentMethod detachedAt(Instant at) {
    return new StripePaymentMethod(
        testRunId,
        paymentMethodId,
        accountId,
        null,
        type,
        card,
        billingDetails,
        metadata,
        allowRedisplay,
        copiedFrom,
        at,
        origin,
        createdAt,
        at);
  }

  StripePaymentMethod attachedTo(String customer, Instant at) {
    return new StripePaymentMethod(
        testRunId,
        paymentMethodId,
        accountId,
        customer,
        type,
        card,
        billingDetails,
        metadata,
        allowRedisplay,
        copiedFrom,
        null,
        origin,
        createdAt,
        at);
  }
}
