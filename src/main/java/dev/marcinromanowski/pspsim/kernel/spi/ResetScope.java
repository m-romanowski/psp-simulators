package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public enum ResetScope {
  TEST_RUN,
  ALL;

  public static ResetScope declared(@Nullable String scope) {
    if (scope == null || scope.isBlank()) {
      return TEST_RUN;
    }
    for (ResetScope known : values()) {
      if (known.name().equals(scope)) {
        return known;
      }
    }
    throw new IllegalArgumentException("scope must be one of TEST_RUN, ALL");
  }
}
