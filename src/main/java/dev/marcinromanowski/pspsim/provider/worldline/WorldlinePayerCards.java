package dev.marcinromanowski.pspsim.provider.worldline;

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

final class WorldlinePayerCards {

  static final String THE_ONE_CARD_WORLDLINE_PRINTS = "published-1111";

  static final String THE_PUBLISHED_LAST4 = "1111";

  static final int THE_PUBLISHED_EXPIRATION_MONTH = 12;

  static final int THE_PUBLISHED_EXPIRATION_YEAR = 2030;

  static final String WORLDLINE_PUBLISHES_NO_TEST_CARD_LIST_ON_THESE_PAGES =
      "none of the provider worldline pages this contract was read from publishes a list of test"
          + " cards. The one card offered here is the card the published tokenCard and"
          + " GetHostedCheckoutStatus samples print, cardNumber \"************1111\" and"
          + " expiryDate \"1230\", which is a masked pan and an expiry and carries no brand"
          + " because those samples carry none. Seed a payment-method fixture to offer a second";

  private final PaymentMethodFixtures fixtures;

  WorldlinePayerCards(PaymentMethodFixtures fixtures) {
    this.fixtures = fixtures;
  }

  static WorldlinePayerCard theCardWorldlinePrints() {
    return new WorldlinePayerCard(
        THE_ONE_CARD_WORLDLINE_PRINTS,
        null,
        THE_PUBLISHED_LAST4,
        THE_PUBLISHED_EXPIRATION_MONTH,
        THE_PUBLISHED_EXPIRATION_YEAR,
        null);
  }

  List<WorldlinePayerCard> offeredBy(TestRunId testRunId) {
    Map<String, WorldlinePayerCard> byAlias = new LinkedHashMap<>();
    WorldlinePayerCard published = theCardWorldlinePrints();
    byAlias.put(published.alias(), published);
    for (PaymentMethodFixture seeded : fixtures.of(WorldlineApi.WORLDLINE, testRunId)) {
      cardOf(seeded).ifPresent(card -> byAlias.put(card.alias(), card));
    }
    return List.copyOf(byAlias.values());
  }

  Optional<WorldlinePayerCard> resolve(TestRunId testRunId, String alias) {
    for (WorldlinePayerCard offered : offeredBy(testRunId)) {
      if (offered.alias().equals(alias)) {
        return Optional.of(offered);
      }
    }
    return Optional.empty();
  }

  Optional<WorldlinePayerCard> holdingTheToken(TestRunId testRunId, String token) {
    for (WorldlinePayerCard offered : offeredBy(testRunId)) {
      if (token.equals(offered.seededToken())) {
        return Optional.of(offered);
      }
    }
    return Optional.empty();
  }

  String aliasesOffered(TestRunId testRunId) {
    List<String> aliases = new ArrayList<>();
    for (WorldlinePayerCard offered : offeredBy(testRunId)) {
      aliases.add(offered.alias());
    }
    return String.join(", ", aliases);
  }

  private static Optional<WorldlinePayerCard> cardOf(PaymentMethodFixture seeded) {
    FixtureCard card = seeded.card();
    if (card == null) {
      return Optional.empty();
    }
    return Optional.of(
        new WorldlinePayerCard(
            seeded.fixtureAlias(),
            card.brand(),
            card.last4(),
            card.expMonth(),
            card.expYear(),
            tokenIn(seeded)));
  }

  private static @Nullable String tokenIn(PaymentMethodFixture seeded) {
    String seededValue = seeded.providerIds().get(ProviderPaymentMethodFields.TOKEN);
    return seededValue == null || seededValue.isBlank() ? null : seededValue;
  }
}
