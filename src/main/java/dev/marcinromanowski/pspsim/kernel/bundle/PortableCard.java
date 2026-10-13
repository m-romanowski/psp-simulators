package dev.marcinromanowski.pspsim.kernel.bundle;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import org.jspecify.annotations.Nullable;

public record PortableCard(
    @Nullable String brand,
    @Nullable String last4,
    @Nullable Integer expMonth,
    @Nullable Integer expYear,
    @Nullable String funding) {

  public static PortableCard of(FixtureCard card) {
    return new PortableCard(
        card.brand(), card.last4(), card.expMonth(), card.expYear(), card.funding());
  }
}
