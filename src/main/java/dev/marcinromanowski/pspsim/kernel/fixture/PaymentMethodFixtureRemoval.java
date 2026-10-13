package dev.marcinromanowski.pspsim.kernel.fixture;

public record PaymentMethodFixtureRemoval(ProviderRows rows) {

  @FunctionalInterface
  public interface ProviderRows {

    void removeTheRowsOfTheFixture();
  }

  public void remove() {
    rows.removeTheRowsOfTheFixture();
  }
}
