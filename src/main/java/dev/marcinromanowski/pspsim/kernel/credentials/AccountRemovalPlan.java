package dev.marcinromanowski.pspsim.kernel.credentials;

public record AccountRemovalPlan(ProviderRows rows) {

  @FunctionalInterface
  public interface ProviderRows {

    void removeTheRowsOfTheAccount();
  }

  public void remove() {
    rows.removeTheRowsOfTheAccount();
  }
}
