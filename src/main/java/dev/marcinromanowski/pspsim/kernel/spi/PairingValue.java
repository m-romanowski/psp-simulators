package dev.marcinromanowski.pspsim.kernel.spi;

public sealed interface PairingValue {

  record Given(String text) implements PairingValue {

    public Given {
      if (text.isBlank()) {
        throw new IllegalArgumentException(
            "a pairing setting given a blank literal tells a tester to configure nothing, and a"
                + " snippet carrying it would be pasted into an application as an empty value");
      }
    }
  }

  record FromAccountField(String key) implements PairingValue {

    public FromAccountField {
      if (key.isBlank()) {
        throw new IllegalArgumentException(
            "a pairing setting drawn from a blank account field names no field of any provider, so"
                + " nothing could ever resolve it");
      }
    }
  }

  record TheProviderBaseUrl() implements PairingValue {}

  record AnAddressOfThisSimulator(String path) implements PairingValue {

    public AnAddressOfThisSimulator {
      if (!path.startsWith("/") || path.endsWith("/")) {
        throw new IllegalArgumentException(
            "a pairing setting naming an address of this simulator names the path it serves a"
                + " provider's host or version under, absolute and with no trailing slash, and "
                + path
                + " is not one, so the address a tester copies would not be one this simulator"
                + " answers on");
      }
    }
  }

  record TheTestRunId() implements PairingValue {}

  record TheTestRunHeader() implements PairingValue {}

  record TheAccountId() implements PairingValue {}
}
