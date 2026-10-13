package dev.marcinromanowski.pspsim.kernel.routing;

public final class RouteAliasRefusedException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final String property;

  public RouteAliasRefusedException(String property, String propertyAndRule) {
    super(propertyAndRule);
    this.property = property;
  }

  public String property() {
    return property;
  }
}
