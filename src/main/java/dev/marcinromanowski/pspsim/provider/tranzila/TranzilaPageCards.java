package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class TranzilaPageCards {

  static final String THE_PUBLISHED_CARD = "published-9022";

  static final String THE_PUBLISHED_CARDS_TOKEN = "t5d55d2922ca4029022";

  static final String THE_PUBLISHED_CARDS_LAST_FOUR = "9022";

  static final int THE_PUBLISHED_CARDS_EXPIRY_MONTH = 12;

  static final int THE_PUBLISHED_CARDS_EXPIRY_YEAR = 2030;

  static final String THE_PUBLISHED_CARDS_LABEL =
      "Visa ending 9022, the card tranzila publishes as a test card";

  static final String ENDING = " ending ";

  record Offered(
      String alias,
      String label,
      String token,
      int expiryMonth,
      int expiryYear,
      TranzilaCard card) {

    PayerCard asAPayerCard() {
      return new PayerCard(alias, label);
    }
  }

  private final PaymentMethodFixtures fixtures;

  TranzilaPageCards(PaymentMethodFixtures fixtures) {
    this.fixtures = fixtures;
  }

  static Offered thePublishedCard() {
    return new Offered(
        THE_PUBLISHED_CARD,
        THE_PUBLISHED_CARDS_LABEL,
        THE_PUBLISHED_CARDS_TOKEN,
        THE_PUBLISHED_CARDS_EXPIRY_MONTH,
        THE_PUBLISHED_CARDS_EXPIRY_YEAR,
        thePublishedCardAsCharged());
  }

  static TranzilaCard thePublishedCardAsCharged() {
    return new TranzilaCard(
        TranzilaCard.UNSEEN.repeat(TranzilaPayerCards.UNSEEN_DIGITS)
            + THE_PUBLISHED_CARDS_LAST_FOUR,
        TranzilaCard.VISA,
        THE_PUBLISHED_CARDS_EXPIRY_MONTH,
        THE_PUBLISHED_CARDS_EXPIRY_YEAR % 100,
        TranzilaThreeDSecureCards.PUBLISHED.get(TranzilaThreeDSecureCards.FRICTIONLESS_SUCCESS));
  }

  List<Offered> offeredBy(TestRunId testRunId) {
    List<Offered> offered = new ArrayList<>();
    offered.add(thePublishedCard());
    for (PaymentMethodFixture seeded : fixtures.of(TranzilaApi.TRANZILA, testRunId)) {
      cardOf(seeded).ifPresent(offered::add);
    }
    return List.copyOf(offered);
  }

  List<PayerCard> asPayerCards(TestRunId testRunId) {
    return offeredBy(testRunId).stream().map(Offered::asAPayerCard).toList();
  }

  Optional<Offered> resolve(TestRunId testRunId, String alias) {
    return offeredBy(testRunId).stream().filter(card -> card.alias().equals(alias)).findFirst();
  }

  String aliasesOffered(TestRunId testRunId) {
    return String.join(", ", offeredBy(testRunId).stream().map(Offered::alias).toList());
  }

  static Optional<Offered> cardOf(PaymentMethodFixture seeded) {
    FixtureCard card = seeded.card();
    String token = seeded.providerIds().get(ProviderPaymentMethodFields.TOKEN);
    if (card == null || token == null) {
      return Optional.empty();
    }
    return Optional.of(
        new Offered(
            seeded.fixtureAlias(),
            card.brand() + ENDING + card.last4(),
            token,
            card.expMonth(),
            card.expYear(),
            TranzilaPayerCards.theCardOf(card)));
  }
}
