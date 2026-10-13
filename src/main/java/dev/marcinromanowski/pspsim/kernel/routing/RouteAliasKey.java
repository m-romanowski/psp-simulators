package dev.marcinromanowski.pspsim.kernel.routing;

import java.util.Locale;

public record RouteAliasKey(String host, String path) {

  public RouteAliasKey {
    host = host.toLowerCase(Locale.ROOT);
  }

  public String value() {
    return host + path;
  }

  public int length() {
    return value().length();
  }
}
