package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;

public record DeclaredKey(
    String name, KeyRole role, KeyCarriage carriedIn, String scopedBy, KeyClaim whileItIsHeld) {

  public DeclaredKey {
    if (name.isBlank()) {
      throw new IllegalArgumentException(
          "a declared key with no name names nothing a client sends, so an inspector row drawn"
              + " from it could never be matched to a request");
    }
    if (scopedBy.isBlank()) {
      throw new IllegalArgumentException(
          "key "
              + name
              + " must say, in this provider's own words, what its repeat is compared against:"
              + " the core owns what a repeat does under each role and only the provider knows"
              + " what counts as a repeat");
    }
  }
}
