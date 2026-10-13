package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class GrowPayerCards {

  static final String MASKED = "•••• ";

  static final String SANDBOX_PREFIX = "sandbox-";

  private static final int SUFFIX_DIGITS = 4;

  private static final int YEAR_DIGITS = 2;

  private static final Map<String, String> BRAND_CODES =
      Map.of(
          "mastercard", "2",
          "visa", "3",
          "isracard", "5",
          "discover", "7",
          "diners", "8");

  private static final Map<String, String> TYPE_CODES =
      Map.of(
          "local", "1",
          "foreign", "2",
          "fuel", "3",
          "debit", "4",
          "gift", "5");

  private static final List<String> SANDBOX_PANS =
      List.of("4580458045804580", "4580000000000000", "4580111111111121");

  private final PaymentMethodFixtures fixtures;

  GrowPayerCards(PaymentMethodFixtures fixtures) {
    this.fixtures = fixtures;
  }

  static List<GrowPayerCard> sandboxCards() {
    List<GrowPayerCard> published = new ArrayList<>();
    for (String pan : SANDBOX_PANS) {
      String suffix = pan.substring(pan.length() - SUFFIX_DIGITS);
      published.add(
          new GrowPayerCard(
              SANDBOX_PREFIX + suffix,
              suffix,
              GrowSandboxCard.TYPE,
              GrowSandboxCard.TYPE_CODE,
              GrowSandboxCard.BRAND,
              GrowSandboxCard.BRAND_CODE,
              GrowSandboxCard.EXPIRY));
    }
    return List.copyOf(published);
  }

  static GrowPayerCard theCardAProcessWithNoPayerCharges() {
    return sandboxCards().get(0);
  }

  List<GrowPayerCard> offeredBy(TestRunId testRunId) {
    Map<String, GrowPayerCard> byAlias = new LinkedHashMap<>();
    for (GrowPayerCard published : sandboxCards()) {
      byAlias.put(published.alias(), published);
    }
    for (PaymentMethodFixture seeded : fixtures.of(GrowLightApi.GROW, testRunId)) {
      cardOf(seeded).ifPresent(card -> byAlias.put(card.alias(), card));
    }
    return List.copyOf(byAlias.values());
  }

  Optional<GrowPayerCard> resolve(TestRunId testRunId, String alias) {
    for (GrowPayerCard offered : offeredBy(testRunId)) {
      if (offered.alias().equals(alias)) {
        return Optional.of(offered);
      }
    }
    return Optional.empty();
  }

  private static Optional<GrowPayerCard> cardOf(PaymentMethodFixture seeded) {
    FixtureCard card = seeded.card();
    if (card == null) {
      return Optional.empty();
    }
    return Optional.of(
        new GrowPayerCard(
            seeded.fixtureAlias(),
            card.last4(),
            card.funding(),
            codeOf(TYPE_CODES, card.funding()),
            card.brand(),
            codeOf(BRAND_CODES, card.brand()),
            expiryOf(card)));
  }

  private static String codeOf(Map<String, String> published, String named) {
    return published.getOrDefault(named.toLowerCase(Locale.ROOT), GrowPayerCard.UNMAPPED);
  }

  private static String expiryOf(FixtureCard card) {
    String month = card.expMonth() < 10 ? "0" + card.expMonth() : String.valueOf(card.expMonth());
    String year = String.valueOf(card.expYear());
    return month + year.substring(year.length() - YEAR_DIGITS);
  }

  String aliasesOffered(TestRunId testRunId) {
    List<String> aliases = new ArrayList<>();
    for (GrowPayerCard offered : offeredBy(testRunId)) {
      aliases.add(offered.alias());
    }
    return String.join(", ", aliases);
  }
}
