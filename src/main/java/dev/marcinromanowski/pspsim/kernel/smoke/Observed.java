package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class Observed {

  private Observed() {}

  public static void put(Map<String, String> read, String field, @Nullable String value) {
    if (value != null) {
      read.put(field, value);
    }
  }
}
