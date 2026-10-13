package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class AutraPayerCards {

  static final String THE_ONE_CARD_AUTRA_PRINTS = "published-4111";

  static final String AUTRA_PUBLISHES_NO_TEST_CARDS =
      "provider autra publishes no sandbox and no list of test cards anywhere — \"There is no"
          + " separate sandbox — validation runs against production\" — so the one card offered"
          + " here is the card its own AutraCardTokenizedResult example prints, brand VISA,"
          + " first4 4111, last4 1111, expiring 12/2030. Seed a payment-method fixture to offer a"
          + " second";

  static final String THE_PUBLISHED_BRAND = "VISA";

  static final String THE_PUBLISHED_FIRST4 = "4111";

  static final String THE_PUBLISHED_LAST4 = "1111";

  static final int THE_PUBLISHED_EXPIRATION_MONTH = 12;

  static final int THE_PUBLISHED_EXPIRATION_YEAR = 2030;

  private final PaymentMethodFixtures fixtures;

  AutraPayerCards(PaymentMethodFixtures fixtures) {
    this.fixtures = fixtures;
  }

  static AutraPayerCard theCardAutraPrints() {
    return new AutraPayerCard(
        THE_ONE_CARD_AUTRA_PRINTS,
        THE_PUBLISHED_BRAND,
        THE_PUBLISHED_FIRST4,
        THE_PUBLISHED_LAST4,
        THE_PUBLISHED_EXPIRATION_MONTH,
        THE_PUBLISHED_EXPIRATION_YEAR,
        null,
        null);
  }

  List<AutraPayerCard> offeredBy(TestRunId testRunId) {
    Map<String, AutraPayerCard> byAlias = new LinkedHashMap<>();
    AutraPayerCard published = theCardAutraPrints();
    byAlias.put(published.alias(), published);
    for (PaymentMethodFixture seeded : fixtures.of(AutraApi.AUTRA, testRunId)) {
      cardOf(seeded).ifPresent(card -> byAlias.put(card.alias(), card));
    }
    return List.copyOf(byAlias.values());
  }

  Optional<AutraPayerCard> resolve(TestRunId testRunId, String alias) {
    for (AutraPayerCard offered : offeredBy(testRunId)) {
      if (offered.alias().equals(alias)) {
        return Optional.of(offered);
      }
    }
    return Optional.empty();
  }

  String aliasesOffered(TestRunId testRunId) {
    List<String> aliases = new ArrayList<>();
    for (AutraPayerCard offered : offeredBy(testRunId)) {
      aliases.add(offered.alias());
    }
    return String.join(", ", aliases);
  }

  private static Optional<AutraPayerCard> cardOf(PaymentMethodFixture seeded) {
    FixtureCard card = seeded.card();
    if (card == null) {
      return Optional.empty();
    }
    return Optional.of(
        new AutraPayerCard(
            seeded.fixtureAlias(),
            card.brand(),
            null,
            card.last4(),
            card.expMonth(),
            card.expYear(),
            slugIn(seeded, ProviderPaymentMethodFields.TOKEN),
            slugIn(seeded, ProviderPaymentMethodFields.PAYMENT_METHOD_ID)));
  }

  private static @Nullable String slugIn(PaymentMethodFixture seeded, String key) {
    String seededValue = seeded.providerIds().get(key);
    return seededValue == null || seededValue.isBlank() ? null : seededValue;
  }
}
