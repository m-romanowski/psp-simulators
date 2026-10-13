package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

final class AutraStoredCards {

  private final AutraCardSessions cardSessions;
  private final PaymentMethodFixtures fixtures;

  AutraStoredCards(AutraCardSessions cardSessions, PaymentMethodFixtures fixtures) {
    this.cardSessions = cardSessions;
    this.fixtures = fixtures;
  }

  boolean holds(TestRunId testRunId, @Nullable String slugToken, @Nullable String slugStoredCard) {
    for (AutraStoredCard card : cardSessions.tokenizedIn(testRunId)) {
      if (names(card.slugToken(), card.slugStoredCard(), slugToken, slugStoredCard)) {
        return true;
      }
    }
    for (PaymentMethodFixture seeded : fixtures.of(AutraApi.AUTRA, testRunId)) {
      if (names(
          seeded.providerIds().get(ProviderPaymentMethodFields.TOKEN),
          seeded.providerIds().get(ProviderPaymentMethodFields.PAYMENT_METHOD_ID),
          slugToken,
          slugStoredCard)) {
        return true;
      }
    }
    return false;
  }

  private static boolean names(
      @Nullable String heldToken,
      @Nullable String heldStoredCard,
      @Nullable String slugToken,
      @Nullable String slugStoredCard) {
    return (slugToken == null || slugToken.equals(heldToken))
        && (slugStoredCard == null || slugStoredCard.equals(heldStoredCard));
  }
}
