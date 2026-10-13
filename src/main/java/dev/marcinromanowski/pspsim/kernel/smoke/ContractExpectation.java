package dev.marcinromanowski.pspsim.kernel.smoke;

import org.jspecify.annotations.Nullable;

public record ContractExpectation(String field, Published published) {

  public ContractExpectation {
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("field must name what was read");
    }
    if (published == null) {
      throw new IllegalArgumentException(
          "published must carry the value a vendor sample gives " + field);
    }
  }

  public boolean isMetBy(@Nullable String observed) {
    return published.value().equals(observed);
  }
}
