package dev.marcinromanowski.pspsim.kernel.spi;

import org.jspecify.annotations.Nullable;

public class ProviderConfigurationRejectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final @Nullable String property;

  public ProviderConfigurationRejectedException(String propertyAndRule) {
    this(null, propertyAndRule);
  }

  public ProviderConfigurationRejectedException(@Nullable String property, String propertyAndRule) {
    super(propertyAndRule);
    this.property = property;
  }

  public @Nullable String property() {
    return property;
  }
}
