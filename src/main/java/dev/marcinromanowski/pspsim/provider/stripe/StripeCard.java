package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record StripeCard(
    String brand,
    String last4,
    int expMonth,
    int expYear,
    String funding,
    @Nullable String country,
    @Nullable String displayBrand,
    @Nullable List<String> networks,
    @Nullable String regulatedStatus,
    @Nullable Boolean threeDSecureSupported,
    @Nullable StripeTestCardBehavior testBehavior) {

  static final Set<String> THE_PUBLISHED_REGULATED_STATUSES = Set.of("regulated", "unregulated");

  static final String A_REGULATED_STATUS_IS_ONE_STRIPE_PUBLISHES =
      "provider stripe publishes regulated and unregulated as the regulated_status of a card, and"
          + " a card hash carrying any other value would answer a status no client can read";

  StripeCard {
    FixtureCard checked = new FixtureCard(brand, last4, expMonth, expYear, funding);
    brand = checked.brand();
    funding = checked.funding();
    networks = networks == null ? null : List.copyOf(networks);
    if (regulatedStatus != null && !THE_PUBLISHED_REGULATED_STATUSES.contains(regulatedStatus)) {
      throw new IllegalArgumentException(A_REGULATED_STATUS_IS_ONE_STRIPE_PUBLISHES);
    }
  }

  StripeCard(
      String brand,
      String last4,
      int expMonth,
      int expYear,
      String funding,
      @Nullable String country,
      @Nullable String displayBrand,
      @Nullable List<String> networks,
      @Nullable String regulatedStatus,
      @Nullable Boolean threeDSecureSupported) {
    this(
        brand,
        last4,
        expMonth,
        expYear,
        funding,
        country,
        displayBrand,
        networks,
        regulatedStatus,
        threeDSecureSupported,
        null);
  }

  StripeCard withoutTheCvcCheck() {
    return new StripeCard(
        brand,
        last4,
        expMonth,
        expYear,
        funding,
        country,
        displayBrand,
        networks,
        regulatedStatus,
        threeDSecureSupported,
        testBehavior == StripeTestCardBehavior.INCORRECT_CVC ? null : testBehavior);
  }

  static StripeCard ofAFixture(FixtureCard card) {
    return new StripeCard(
        card.brand(),
        card.last4(),
        card.expMonth(),
        card.expYear(),
        card.funding(),
        null,
        null,
        List.of(card.brand()),
        null,
        null);
  }
}
