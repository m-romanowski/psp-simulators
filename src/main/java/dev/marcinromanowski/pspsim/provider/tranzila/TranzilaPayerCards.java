package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;

final class TranzilaPayerCards {

  static final int UNSEEN_DIGITS = 12;

  static final String TOKEN_STANDS_FOR_A_CARD = "token:";

  private final PaymentMethodFixtures fixtures;

  TranzilaPayerCards(PaymentMethodFixtures fixtures) {
    this.fixtures = fixtures;
  }

  Optional<TranzilaCard> holdingTheToken(TestRunId testRunId, String token) {
    return fixtures.of(TranzilaApi.TRANZILA, testRunId).stream()
        .filter(seeded -> token.equals(seeded.providerIds().get(ProviderPaymentMethodFields.TOKEN)))
        .map(PaymentMethodFixture::card)
        .flatMap(card -> Optional.ofNullable(card).stream())
        .findFirst()
        .map(card -> theCardOf(testRunId, token, card));
  }

  static TranzilaCard theCardOf(TestRunId testRunId, String token, FixtureCard card) {
    return new TranzilaCard(
        "x".repeat(UNSEEN_DIGITS) + card.last4(),
        TranzilaCard.cardTypeNamed(card.brand())
            .orElse(Integer.valueOf(TranzilaCard.VISA))
            .intValue(),
        card.expMonth(),
        card.expYear() % 100,
        TranzilaCard.fingerprintOf(testRunId.value() + ":" + TOKEN_STANDS_FOR_A_CARD + token));
  }
}
