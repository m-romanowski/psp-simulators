package dev.marcinromanowski.pspsim.kernel.fixture;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class InMemoryPaymentMethodFixtures implements PaymentMethodFixtures {

  private final Map<String, PaymentMethodFixture> held = new LinkedHashMap<>();
  private final Map<String, String> claims = new LinkedHashMap<>();
  private Instant seededAt = Instant.parse("2026-09-07T12:00:00Z");
  private int minted;

  private boolean refusingToRemove;

  public InMemoryPaymentMethodFixtures at(Instant now) {
    this.seededAt = now;
    return this;
  }

  public InMemoryPaymentMethodFixtures asIfAnotherCallHadJustRemovedIt() {
    this.refusingToRemove = true;
    return this;
  }

  @Override
  public Seeding seed(
      NewPaymentMethodFixture fixture,
      PaymentMethodFixturePlan plan,
      Set<String> theKeysManyFixturesMayShare) {
    String aliasKey =
        key(fixture.provider(), fixture.testRunId())
            + "/"
            + fixture.merchantId()
            + "/"
            + fixture.fixtureAlias();
    if (claims.containsKey(aliasKey)) {
      return new Seeding.Claimed("alias", fixture.fixtureAlias());
    }
    for (Map.Entry<String, String> named : fixture.providerIds().entrySet()) {
      if (theKeysManyFixturesMayShare.contains(named.getKey())) {
        continue;
      }
      String claim =
          key(fixture.provider(), fixture.testRunId())
              + "/"
              + named.getKey()
              + "/"
              + named.getValue();
      if (claims.containsKey(claim)) {
        return new Seeding.Claimed("providerIds." + named.getKey(), named.getValue());
      }
    }
    minted++;
    String fixtureId = "fx_" + minted;
    PaymentMethodFixture seeded =
        new PaymentMethodFixture(
            fixtureId,
            fixture.provider(),
            fixture.testRunId(),
            fixture.merchantId(),
            fixture.ownerId(),
            fixture.type(),
            fixture.fixtureAlias(),
            fixture.card(),
            fixture.providerIds(),
            fixture.defaultScenario(),
            seededAt);
    claims.put(aliasKey, fixtureId);
    for (Map.Entry<String, String> named : fixture.providerIds().entrySet()) {
      if (theKeysManyFixturesMayShare.contains(named.getKey())) {
        continue;
      }
      claims.put(
          key(fixture.provider(), fixture.testRunId())
              + "/"
              + named.getKey()
              + "/"
              + named.getValue(),
          fixtureId);
    }
    held.put(fixtureId, seeded);
    plan.store(seeded);
    return new Seeding.Seeded(seeded);
  }

  @Override
  public List<PaymentMethodFixture> of(ProviderId provider, TestRunId testRunId) {
    List<PaymentMethodFixture> found = new ArrayList<>();
    for (PaymentMethodFixture fixture : held.values()) {
      if (fixture.provider().equals(provider) && fixture.testRunId().equals(testRunId)) {
        found.add(fixture);
      }
    }
    return List.copyOf(found);
  }

  @Override
  public Optional<PaymentMethodFixture> find(
      ProviderId provider, TestRunId testRunId, String fixtureId) {
    return Optional.ofNullable(held.get(fixtureId))
        .filter(fixture -> fixture.provider().equals(provider))
        .filter(fixture -> fixture.testRunId().equals(testRunId));
  }

  @Override
  public boolean remove(
      ProviderId provider,
      TestRunId testRunId,
      String fixtureId,
      PaymentMethodFixtureRemoval removal) {
    if (refusingToRemove || find(provider, testRunId, fixtureId).isEmpty()) {
      return false;
    }
    held.remove(fixtureId);
    claims.values().removeIf(fixtureId::equals);
    removal.remove();
    return true;
  }

  @Override
  public int clear(ProviderId provider, TestRunId testRunId) {
    List<PaymentMethodFixture> doomed = of(provider, testRunId);
    for (PaymentMethodFixture fixture : doomed) {
      held.remove(fixture.fixtureId());
      claims.values().removeIf(fixture.fixtureId()::equals);
    }
    return doomed.size();
  }

  @Override
  public int clearAll(ProviderId provider) {
    List<PaymentMethodFixture> doomed = new ArrayList<>();
    for (PaymentMethodFixture fixture : held.values()) {
      if (fixture.provider().equals(provider)) {
        doomed.add(fixture);
      }
    }
    for (PaymentMethodFixture fixture : doomed) {
      held.remove(fixture.fixtureId());
      claims.values().removeIf(fixture.fixtureId()::equals);
    }
    return doomed.size();
  }

  private static String key(ProviderId provider, TestRunId testRunId) {
    return provider.value() + "/" + testRunId.value();
  }
}
