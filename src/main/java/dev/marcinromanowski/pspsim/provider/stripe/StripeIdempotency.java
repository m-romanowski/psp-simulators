package dev.marcinromanowski.pspsim.provider.stripe;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class StripeIdempotency {

  static final Duration WINDOW = Duration.ofHours(24);

  static final int LONGEST_KEY = 255;

  static final String KEY_IS_TOO_LONG =
      "The "
          + StripeFields.IDEMPOTENCY_KEY_HEADER
          + " header takes at most "
          + LONGEST_KEY
          + " characters.";

  private StripeIdempotency() {}

  static @Nullable String keyIn(Map<String, List<String>> headers) {
    for (Map.Entry<String, List<String>> header : headers.entrySet()) {
      if (StripeFields.IDEMPOTENCY_KEY_HEADER.equalsIgnoreCase(header.getKey())) {
        for (String sent : header.getValue()) {
          if (!sent.isBlank()) {
            return sent;
          }
        }
      }
    }
    return null;
  }

  static boolean isTooLong(String key) {
    return key.codePointCount(0, key.length()) > LONGEST_KEY;
  }
}
