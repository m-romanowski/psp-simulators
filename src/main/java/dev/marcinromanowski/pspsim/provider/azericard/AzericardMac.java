package dev.marcinromanowski.pspsim.provider.azericard;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

final class AzericardMac {

  static final String ABSENT_VALUE = "-";

  private AzericardMac() {}

  static byte[] sourceOf(@Nullable String... values) {
    StringBuilder source = new StringBuilder();
    for (String value : values) {
      if (value == null || value.isEmpty()) {
        source.append(ABSENT_VALUE);
      } else {
        source.append(value.length()).append(value);
      }
    }
    return source.toString().getBytes(StandardCharsets.UTF_8);
  }
}
