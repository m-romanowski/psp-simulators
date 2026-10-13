package dev.marcinromanowski.pspsim.kernel.smoke;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record CheckResponse(int status, Map<String, List<String>> headers, String body) {

  public CheckResponse {
    headers = theHeadersInTheOrderTheRouteAnsweredWith(headers);
    if (body == null) {
      throw new IllegalArgumentException("body must be read, even when it is empty");
    }
  }

  public @Nullable String header(String name) {
    for (Map.Entry<String, List<String>> carried : headers.entrySet()) {
      if (carried.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))
          && !carried.getValue().isEmpty()) {
        return carried.getValue().get(0);
      }
    }
    return null;
  }

  private static SequencedMap<String, List<String>> theHeadersInTheOrderTheRouteAnsweredWith(
      Map<String, List<String>> answered) {
    LinkedHashMap<String, List<String>> inTheOrderTheRouteAnsweredWith =
        new LinkedHashMap<>(answered);
    return Collections.unmodifiableSequencedMap(inTheOrderTheRouteAnsweredWith);
  }
}
