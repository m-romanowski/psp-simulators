package dev.marcinromanowski.pspsim.kernel.spi;

public class ProviderFixtureRejectedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String property;

  public ProviderFixtureRejectedException(String property, String propertyAndRule) {
    super(propertyAndRule);
    this.property = property;
  }

  public String property() {
    return property;
  }
}
