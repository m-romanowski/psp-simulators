package com.example.tester.ui;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public final class ATesterRoute {

  private static final List<String> WHAT_ONLY_A_TESTER_REACHES = List.of("__simulator", "ui");

  private ATesterRoute() {}

  public static boolean isNamedBy(String url) {
    String path = canonicalPathOf(url);
    return WHAT_ONLY_A_TESTER_REACHES.stream()
        .anyMatch(first -> path.equals("/" + first) || path.startsWith("/" + first + "/"));
  }

  static String canonicalPathOf(String url) {
    String path = decodedUntilSettled(rawPathOf(url));
    Deque<String> segments = new ArrayDeque<>();
    for (String segment : path.split("/")) {
      String bare = segment.contains(";") ? segment.substring(0, segment.indexOf(';')) : segment;
      if (bare.isEmpty() || bare.equals(".")) {
        continue;
      }
      if (bare.equals("..")) {
        segments.pollLast();
        continue;
      }
      segments.addLast(bare.toLowerCase(Locale.ROOT));
    }
    return "/" + String.join("/", segments);
  }

  private static String rawPathOf(String url) {
    String withoutFragment = url.contains("#") ? url.substring(0, url.indexOf('#')) : url;
    String withoutQuery =
        withoutFragment.contains("?")
            ? withoutFragment.substring(0, withoutFragment.indexOf('?'))
            : withoutFragment;
    int scheme = withoutQuery.indexOf("://");
    if (scheme < 0) {
      return withoutQuery;
    }
    int path = withoutQuery.indexOf('/', scheme + 3);
    return path < 0 ? "/" : withoutQuery.substring(path);
  }

  private static String decodedUntilSettled(String path) {
    String current = path;
    for (int round = 0; round < 5; round++) {
      String decoded;
      try {
        decoded = URLDecoder.decode(current.replace("+", "%2B"), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException notPercentEncoded) {
        return current;
      }
      if (decoded.equals(current)) {
        return decoded;
      }
      current = decoded;
    }
    return current;
  }
}
