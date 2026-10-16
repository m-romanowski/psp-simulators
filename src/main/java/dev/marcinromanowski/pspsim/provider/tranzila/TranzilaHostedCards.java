package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;

final class TranzilaHostedCards {

  record Named(String token, TranzilaCard card) {}

  private final PaymentMethodFixtures fixtures;
  private final IdGenerator ids;

  TranzilaHostedCards(PaymentMethodFixtures fixtures, IdGenerator ids) {
    this.fixtures = fixtures;
    this.ids = ids;
  }

  Named theCardNamedBy(
      TestRunId testRunId, TranzilaHostedCard typed, int expiryMonth, int expiryYear) {
    return aFixtureNamedBy(testRunId, typed, expiryMonth, expiryYear)
        .orElseGet(
            () ->
                new Named(
                    TranzilaIdentifiers.aTokenFor(ids, typed.lastFour()),
                    typed.withoutItsNumber(expiryMonth, expiryYear)));
  }

  private Optional<Named> aFixtureNamedBy(
      TestRunId testRunId, TranzilaHostedCard typed, int expiryMonth, int expiryYear) {
    for (PaymentMethodFixture seeded : fixtures.of(TranzilaApi.TRANZILA, testRunId)) {
      FixtureCard card = seeded.card();
      String token = seeded.providerIds().get(ProviderPaymentMethodFields.TOKEN);
      if (card != null
          && token != null
          && card.last4().equals(typed.lastFour())
          && TranzilaCard.cardTypeNamed(card.brand())
              .filter(type -> type.intValue() == typed.cardType())
              .isPresent()) {
        return Optional.of(
            new Named(
                token, TranzilaPayerCards.theCardOf(card).expiringIn(expiryMonth, expiryYear)));
      }
    }
    return Optional.empty();
  }
}
