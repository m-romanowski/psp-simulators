package dev.marcinromanowski.pspsim.provider.stripe;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class StripeAuthentication {

  static final String BASIC = "basic ";

  static final String BEARER = "bearer ";

  private static final char BETWEEN_USERNAME_AND_PASSWORD = ':';

  private StripeAuthentication() {}

  static @Nullable String secretKeyIn(Map<String, List<String>> headers) {
    for (Map.Entry<String, List<String>> header : headers.entrySet()) {
      if (StripeFields.AUTHORIZATION_HEADER.equalsIgnoreCase(header.getKey())) {
        for (String sent : header.getValue()) {
          String key = secretKeyOf(sent);
          if (key != null) {
            return key;
          }
        }
      }
    }
    return null;
  }

  private static @Nullable String secretKeyOf(String sent) {
    String scheme = sent.toLowerCase(Locale.ROOT);
    if (scheme.startsWith(BEARER)) {
      return blankToNothing(sent.substring(BEARER.length()).trim());
    }
    if (scheme.startsWith(BASIC)) {
      return usernameOf(sent.substring(BASIC.length()).trim());
    }
    return null;
  }

  private static @Nullable String usernameOf(String encoded) {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException notBase64) {
      return null;
    }
    String credentials = new String(decoded, StandardCharsets.UTF_8);
    int separator = credentials.indexOf(BETWEEN_USERNAME_AND_PASSWORD);
    return blankToNothing(separator < 0 ? credentials : credentials.substring(0, separator));
  }

  private static @Nullable String blankToNothing(String value) {
    return value.isBlank() ? null : value;
  }
}
