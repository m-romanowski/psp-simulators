package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record ProviderObjectCursor(long arrival, String providerObjectId) {

  public ProviderObjectCursor {
    if (providerObjectId.isBlank()) {
      throw new IllegalArgumentException(
          "a cursor names the object a page ended on, and a blank id names none");
    }
  }

  public static long walkingBackFrom(@Nullable ProviderObjectCursor after) {
    return after == null ? Long.MAX_VALUE : after.arrival();
  }
}
