package dev.marcinromanowski.pspsim.kernel.repro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record RecordedAttempt(
    int number,
    @Nullable String at,
    @Nullable String targetUrl,
    @Nullable String verdict,
    @Nullable Integer httpStatus,
    @Nullable String transportFailure,
    @Nullable String responsePreview,
    @Nullable Map<String, List<String>> sentHeaders) {

  public RecordedAttempt {
    Map<String, List<String>> named = sentHeaders;
    if (named != null) {
      sentHeaders = copied(named);
    }
  }

  public boolean theWireWasRecorded() {
    return sentHeaders != null;
  }

  public Map<String, List<String>> carriedHeaders() {
    Map<String, List<String>> carried = sentHeaders;
    return carried == null ? Map.of() : carried;
  }

  private static SequencedMap<String, List<String>> copied(Map<String, List<String>> named) {
    LinkedHashMap<String, List<String>> inTheOrderTheAttemptPutThemOnTheWire =
        new LinkedHashMap<>();
    named.forEach(
        (name, values) -> inTheOrderTheAttemptPutThemOnTheWire.put(name, List.copyOf(values)));
    return Collections.unmodifiableSequencedMap(inTheOrderTheAttemptPutThemOnTheWire);
  }
}
