package dev.marcinromanowski.pspsim.kernel.bundle;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record PortableFixture(
    @Nullable String merchantId,
    @Nullable String ownerId,
    @Nullable String type,
    @Nullable String alias,
    @Nullable PortableCard card,
    @Nullable Map<String, String> providerIds,
    @Nullable Integer triggersRuleAt) {

  public static final String A_FIXTURE_NAMES_A_RULE_BY_ITS_PLACE =
      "a fixture names the scenario rule it triggers by the place that rule holds among the rules"
          + " this bundle carries, because the identity a rule was given belongs to the run that"
          + " wrote it and a bundle written back produces new rules rather than restoring old ones";

  public PortableFixture {
    providerIds = providerIds == null ? null : theIdsInTheOrderTheBundleCarriesThem(providerIds);
  }

  private static SequencedMap<String, String> theIdsInTheOrderTheBundleCarriesThem(
      Map<String, String> carried) {
    LinkedHashMap<String, String> inTheOrderTheBundleCarriesThem = new LinkedHashMap<>(carried);
    return Collections.unmodifiableSequencedMap(inTheOrderTheBundleCarriesThem);
  }

  public static PortableFixture of(PaymentMethodFixture fixture, @Nullable Integer triggersRuleAt) {
    FixtureCard card = fixture.card();
    return new PortableFixture(
        fixture.merchantId(),
        fixture.ownerId(),
        fixture.type().publishedName(),
        fixture.fixtureAlias(),
        card == null ? null : PortableCard.of(card),
        fixture.providerIds(),
        triggersRuleAt);
  }

  public Map<String, String> carriedProviderIds() {
    Map<String, String> carried = providerIds;
    return carried == null ? Map.of() : carried;
  }

  public List<BundleRefusal> theRuleItNamesThatThisBundleHasNot(String property, int rulesCarried) {
    Integer place = triggersRuleAt;
    if (place == null) {
      return List.of();
    }
    if (place < 0 || place >= rulesCarried) {
      return List.of(
          new BundleRefusal(property + ".triggersRuleAt", A_FIXTURE_NAMES_A_RULE_BY_ITS_PLACE));
    }
    return List.of();
  }
}
