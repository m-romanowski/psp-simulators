package dev.marcinromanowski.pspsim.kernel.routing;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.ThisSimulatorsOwnAddresses;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class RouteAliasGrammar {

  public static final int LONGEST_KEY = 512;

  public static final String FORWARDED_HOST = "X-Forwarded-Host";

  public static final String FORWARDED_PROTO = "X-Forwarded-Proto";

  public static final String HOST = "Host";

  private static final List<String> SCHEMES = List.of("http", "https");

  private RouteAliasGrammar() {}

  public static boolean namesTheSimulatorsOwnNamespace(String path) {
    return ThisSimulatorsOwnAddresses.claims(path);
  }

  public static RouteAlias aliasOf(
      ProviderId provider, TestRunId testRunId, String property, String from, String to) {
    return new RouteAlias(
        provider,
        testRunId,
        keyOf(property + ".from", from),
        from,
        theRouteOf(property + ".to", to));
  }

  public static RouteAliasKey keyOf(String field, String from) {
    URI url = urlOf(field, from);
    String host = url.getHost();
    String path = url.getRawPath();
    if (!url.isAbsolute() || host == null || host.isBlank()) {
      throw refusal(field, "must be an absolute URL carrying a scheme and a host");
    }
    if (!SCHEMES.contains(declaredSchemeOf(url))) {
      throw refusal(field, "must use the http or https scheme");
    }
    if (url.getRawUserInfo() != null) {
      throw refusal(field, "must carry no user information before its host");
    }
    if (url.getRawQuery() != null || url.getRawFragment() != null) {
      throw refusal(field, "must carry no query string and no fragment");
    }
    if (path == null || path.isEmpty() || !path.startsWith("/")) {
      throw refusal(
          field,
          "must name the path the application calls, because an alias binds one exact URL and"
              + " not every URL beneath a base");
    }
    RouteAliasKey key = new RouteAliasKey(host, path);
    if (key.length() > LONGEST_KEY) {
      throw refusal(
          field, "must name a host and a path of at most " + LONGEST_KEY + " characters together");
    }
    return key;
  }

  public static @Nullable String hostOf(@Nullable String forwardedHost, @Nullable String host) {
    String authority = authorityOf(forwardedHost, host);
    if (authority == null) {
      return null;
    }
    int port = authority.lastIndexOf(':');
    int closingBracket = authority.lastIndexOf(']');
    String named = port > closingBracket ? authority.substring(0, port) : authority;
    return named.isBlank() ? null : named.toLowerCase(Locale.ROOT);
  }

  public static @Nullable String authorityOf(
      @Nullable String forwardedHost, @Nullable String host) {
    String sent = forwardedHost == null || forwardedHost.isBlank() ? host : forwardedHost;
    return nearestProxyOf(sent);
  }

  public static String calledUrlOf(String scheme, String authority, String path) {
    return scheme + "://" + authority + path;
  }

  public static String schemeOf(@Nullable String forwardedProto, String transport) {
    String nearest = nearestProxyOf(forwardedProto);
    return nearest == null ? transport : nearest.toLowerCase(Locale.ROOT);
  }

  private static @Nullable String nearestProxyOf(@Nullable String forwarded) {
    if (forwarded == null) {
      return null;
    }
    int nextProxy = forwarded.indexOf(',');
    String nearest = nextProxy < 0 ? forwarded : forwarded.substring(0, nextProxy);
    return nearest.isBlank() ? null : nearest.strip();
  }

  private static String theRouteOf(String field, String to) {
    if (!to.startsWith("/") || to.startsWith("//")) {
      throw refusal(field, "must name a route of this simulator, beginning with '/'");
    }
    URI route = urlOf(field, to);
    if (route.getRawQuery() != null || route.getRawFragment() != null) {
      throw refusal(field, "must carry no query string and no fragment");
    }
    if (namesTheSimulatorsOwnNamespace(to)) {
      throw refusal(
          field,
          "must name a provider route, never one of the simulator's own namespaces "
              + ThisSimulatorsOwnAddresses.RESERVED_FOR_THIS_SIMULATOR);
    }
    if (to.length() > LONGEST_KEY) {
      throw refusal(field, "must be at most " + LONGEST_KEY + " characters long");
    }
    return to;
  }

  private static String declaredSchemeOf(URI url) {
    String scheme = url.getScheme();
    return scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
  }

  private static URI urlOf(String field, String declared) {
    try {
      return new URI(declared);
    } catch (URISyntaxException malformed) {
      throw refusal(field, "is not a URL this simulator can read");
    }
  }

  private static RouteAliasRefusedException refusal(String field, String rule) {
    return new RouteAliasRefusedException(field, field + " " + rule);
  }
}
