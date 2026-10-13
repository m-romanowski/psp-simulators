package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.RunBundles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class PaymentMethodScreen {

  public static final String THE_MASK_A_FIXTURE_CARRIES = "**** ";

  public static final String A_REMOVAL_IS_CONFIRMED =
      "tick confirm before removing a seeded payment method: the fixture and the provider's own"
          + " row for it go together, and a removed fixture cannot be brought back";

  @FunctionalInterface
  public interface WhatTheProviderHoldsUnderTheseIds {

    List<ProviderObjectState> under(List<String> providerObjectIds);
  }

  public record ProviderNativeId(
      String key,
      String value,
      @Nullable String nativeStatus,
      @Nullable StatusBadge normalizedStatus) {

    public boolean namesAnObjectTheProviderHolds() {
      return nativeStatus != null;
    }
  }

  public record Row(
      String fixtureId,
      String fixtureAlias,
      String type,
      String ownerId,
      String merchantId,
      @Nullable String brand,
      @Nullable String maskedCard,
      @Nullable String expiry,
      @Nullable String funding,
      List<ProviderNativeId> providerIds,
      @Nullable String defaultScenario,
      boolean theRunHoldsTheRuleItNames,
      Instant createdAt) {

    public Row {
      providerIds = List.copyOf(providerIds);
    }

    public boolean describesACard() {
      return maskedCard != null;
    }

    public boolean namesAScenario() {
      return defaultScenario != null;
    }

    public boolean namesARuleThisRunNoLongerHolds() {
      return defaultScenario != null && !theRunHoldsTheRuleItNames;
    }
  }

  public record View(
      String provider,
      String testRunId,
      List<Row> rows,
      String theRefusalOfARuleThisRunNoLongerHolds) {

    public View {
      rows = List.copyOf(rows);
    }
  }

  private PaymentMethodScreen() {}

  public static View of(
      String provider,
      String testRunId,
      List<PaymentMethodFixture> seeded,
      List<ScenarioRule> rulesThisRunHolds,
      WhatTheProviderHoldsUnderTheseIds held) {
    List<String> ruleIds = new ArrayList<>();
    for (ScenarioRule rule : rulesThisRunHolds) {
      ruleIds.add(rule.scenarioId());
    }
    List<ProviderObjectState> found = theObjectsTheseFixturesAreDrivenBy(seeded, held);
    List<Row> rows = new ArrayList<>();
    for (PaymentMethodFixture fixture : seeded) {
      rows.add(rowOf(fixture, List.copyOf(ruleIds), found));
    }
    return new View(
        provider, testRunId, List.copyOf(rows), RunBundles.A_RULE_THIS_RUN_NO_LONGER_HOLDS);
  }

  private static List<ProviderObjectState> theObjectsTheseFixturesAreDrivenBy(
      List<PaymentMethodFixture> seeded, WhatTheProviderHoldsUnderTheseIds held) {
    List<String> named = new ArrayList<>();
    for (PaymentMethodFixture fixture : seeded) {
      named.addAll(fixture.providerIds().values());
    }
    return named.isEmpty() ? List.of() : held.under(List.copyOf(named));
  }

  private static List<ProviderNativeId> theIdsItIsDrivenBy(
      PaymentMethodFixture fixture, List<ProviderObjectState> found) {
    List<ProviderNativeId> named = new ArrayList<>();
    for (Map.Entry<String, String> id : fixture.providerIds().entrySet()) {
      named.add(theObjectNamedBy(id.getKey(), id.getValue(), found));
    }
    return List.copyOf(named);
  }

  private static ProviderNativeId theObjectNamedBy(
      String key, String value, List<ProviderObjectState> found) {
    return TheObjectAnIdentifierNames.among(found, value)
        .map(
            state ->
                new ProviderNativeId(
                    key, value, state.nativeStatus(), StatusBadge.of(state.normalizedStatus())))
        .orElseGet(() -> new ProviderNativeId(key, value, null, null));
  }

  private static Row rowOf(
      PaymentMethodFixture fixture, List<String> ruleIds, List<ProviderObjectState> found) {
    FixtureCard card = fixture.card();
    String named = fixture.defaultScenario();
    return new Row(
        fixture.fixtureId(),
        fixture.fixtureAlias(),
        fixture.type().publishedName(),
        fixture.ownerId(),
        fixture.merchantId(),
        card == null ? null : card.brand(),
        card == null ? null : THE_MASK_A_FIXTURE_CARRIES + card.last4(),
        card == null ? null : expiryOf(card),
        card == null ? null : card.funding(),
        theIdsItIsDrivenBy(fixture, found),
        named,
        named != null && ruleIds.contains(named),
        fixture.createdAt());
  }

  private static String expiryOf(FixtureCard card) {
    return (card.expMonth() < 10 ? "0" : "") + card.expMonth() + "/" + card.expYear();
  }
}
