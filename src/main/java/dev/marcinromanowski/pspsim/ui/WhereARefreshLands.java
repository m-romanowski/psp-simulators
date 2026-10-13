package dev.marcinromanowski.pspsim.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class WhereARefreshLands {

  static final String THE_PROVIDER = "provider";

  static final String THE_RUN = "testRunId";

  static final String THE_SCREEN_A_RUN_IS_READ_ON = "/ui";

  private WhereARefreshLands() {}

  public static String theAddressARefreshIsSentTo(
      String address, @Nullable String provider, @Nullable String testRunId) {
    StringBuilder sent = new StringBuilder(theScreenBehind(address));
    char joinedBy = appended(sent, '?', THE_PROVIDER, provider);
    appended(sent, joinedBy, THE_RUN, testRunId);
    return sent.toString();
  }

  static String theScreenBehind(String address) {
    for (String path : theScreensLongestFirst()) {
      if (fallsUnder(address, path)) {
        return path;
      }
    }
    throw new IllegalArgumentException(
        address
            + " falls under no screen this rail carries. Only an address a screen was asked for"
            + " reaches here, and every one of those falls under "
            + THE_SCREEN_A_RUN_IS_READ_ON
            + ", which the rail carries as a screen of its own.");
  }

  static boolean fallsUnder(String address, String path) {
    return address.equals(path) || address.startsWith(path + "/");
  }

  static List<String> theScreensLongestFirst() {
    List<String> paths = new ArrayList<>();
    for (RailDestination destination : TheRail.theScreensThisSimulatorCarries()) {
      paths.add(destination.path());
    }
    paths.sort(Comparator.comparingInt(String::length).reversed());
    return List.copyOf(paths);
  }

  private static char appended(
      StringBuilder sent, char joinedBy, String name, @Nullable String value) {
    if (value == null || value.isBlank()) {
      return joinedBy;
    }
    sent.append(joinedBy)
        .append(name)
        .append('=')
        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    return '&';
  }
}
