package dev.marcinromanowski.pspsim.kernel.spi;

public record PairingSetting(String name, PairingValue value) {

  public PairingSetting {
    if (name.isBlank()) {
      throw new IllegalArgumentException(
          "a pairing setting with no name names nothing a tester could set in an application, and"
              + " a snippet line drawn from it would carry a value against no key");
    }
  }
}
