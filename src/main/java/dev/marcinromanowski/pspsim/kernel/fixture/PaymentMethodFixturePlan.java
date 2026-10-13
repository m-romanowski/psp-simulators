package dev.marcinromanowski.pspsim.kernel.fixture;

public record PaymentMethodFixturePlan(ProviderRows rows) {

  @FunctionalInterface
  public interface ProviderRows {

    void storeTheFixtureTheSeedNamed(PaymentMethodFixture seeded);
  }

  public static PaymentMethodFixturePlan storingNothingOfItsOwn() {
    return new PaymentMethodFixturePlan(seeded -> {});
  }

  public void store(PaymentMethodFixture seeded) {
    rows.storeTheFixtureTheSeedNamed(seeded);
  }
}
