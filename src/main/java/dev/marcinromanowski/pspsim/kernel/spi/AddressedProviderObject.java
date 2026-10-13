package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Optional;

public record AddressedProviderObject(String addressedBy, ProviderObjectState object) {

  public AddressedProviderObject {
    if (!object.identity().answersTo(addressedBy)) {
      throw new IllegalArgumentException(
          "a provider resolved an identifier to an object whose identity does not list that"
              + " identifier, so the object answers to a string it does not admit answering to"
              + " and every screen reading its identity would show a narrower set of rows than"
              + " the lookup itself accepts");
    }
  }

  public static AddressedProviderObject canonically(ProviderObjectState object) {
    return new AddressedProviderObject(object.identity().canonical(), object);
  }

  public static Optional<AddressedProviderObject> read(
      SimulatorProvider module, TestRunId testRunId, String providerObjectId) {
    return module
        .state(testRunId, providerObjectId)
        .map(object -> new AddressedProviderObject(providerObjectId, object));
  }

  public boolean addressedByItsCanonicalMember() {
    return object.identity().isTheCanonicalMember(addressedBy);
  }

  public String canonical() {
    return object.identity().canonical();
  }

  public List<String> answersTo() {
    return object.identity().members();
  }
}
