package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public record ConfigurationRefusal(@Nullable String field, String message) {

  public static ConfigurationRefusal of(String message) {
    return new ConfigurationRefusal(AccountProperty.theFieldNamedBy(message), message);
  }

  public boolean namesAField() {
    return field != null;
  }
}
