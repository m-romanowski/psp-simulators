package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record ProviderPairing(
    ProviderId provider,
    String apiBasePath,
    String howARequestNamesItsAccount,
    List<PairingSetting> settings) {

  public ProviderPairing {
    if (!apiBasePath.startsWith("/")) {
      throw new IllegalArgumentException(
          "provider "
              + provider.value()
              + " publishes its API on "
              + apiBasePath
              + ", which is not a path of this simulator: a base path is joined to the simulator's"
              + " public base URL and must begin with a slash");
    }
    if (howARequestNamesItsAccount.isBlank()) {
      throw new IllegalArgumentException(
          "provider "
              + provider.value()
              + " must say, in its own words, how a request of its contract carries the account it"
              + " belongs to; a blank sentence reads as a fact this simulator failed to record"
              + " rather than as a provider that authenticates nothing");
    }
    if (settings.isEmpty()) {
      throw new IllegalArgumentException(
          "provider "
              + provider.value()
              + " declares no pairing setting, so the snippet a tester copies would be empty and"
              + " the screen would publish a provider it cannot be paired against");
    }
    Set<String> named = new LinkedHashSet<>();
    for (PairingSetting setting : settings) {
      if (!named.add(setting.name())) {
        throw new IllegalArgumentException(
            "pairing setting "
                + setting.name()
                + " is declared twice by provider "
                + provider.value()
                + ", so one line of a copied snippet would be overwritten by another");
      }
    }
    settings = List.copyOf(settings);
  }

  public String providerBaseUrlWithin(String publicBaseUrl) {
    String host =
        publicBaseUrl.endsWith("/")
            ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
            : publicBaseUrl;
    return host + apiBasePath;
  }

  public List<String> accountFieldsItReads() {
    List<String> keys = new ArrayList<>();
    for (PairingSetting setting : settings) {
      if (setting.value() instanceof PairingValue.FromAccountField field) {
        keys.add(field.key());
      }
    }
    return List.copyOf(keys);
  }
}
