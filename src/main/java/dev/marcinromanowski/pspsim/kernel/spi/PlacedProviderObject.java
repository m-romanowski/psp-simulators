package dev.marcinromanowski.pspsim.kernel.spi;

public record PlacedProviderObject(ProviderObjectCursor at, ProviderObjectState object) {

  public PlacedProviderObject {
    if (!at.providerObjectId().equals(object.providerObjectId())) {
      throw new IllegalArgumentException(
          "a listed object is placed in the order by the id it carries, and this one is placed"
              + " under a different id, so a reader paging on it would ask for an object that is"
              + " not the one it was handed");
    }
  }

  public int newestFirstAgainst(PlacedProviderObject other) {
    return Long.compare(other.at().arrival(), at.arrival());
  }

  public boolean isNewerThan(PlacedProviderObject other) {
    return at.arrival() > other.at().arrival();
  }
}
