package dev.marcinromanowski.pspsim.kernel.repro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record RecordedExchange(
    int place,
    @Nullable String auditId,
    @Nullable String surface,
    @Nullable String direction,
    @Nullable String method,
    @Nullable String path,
    @Nullable Integer httpStatus,
    @Nullable String outcome,
    @Nullable String refusedAs,
    @Nullable String refusalSubject,
    @Nullable String scenarioId,
    int attempt,
    @Nullable String at,
    @Nullable Map<String, List<String>> sanitizedHeaders,
    @Nullable Map<String, List<String>> sanitizedBody) {

  public RecordedExchange {
    sanitizedHeaders = sanitizedHeaders == null ? null : copied(sanitizedHeaders);
    sanitizedBody = sanitizedBody == null ? null : copied(sanitizedBody);
  }

  public Map<String, List<String>> carriedHeaders() {
    Map<String, List<String>> carried = sanitizedHeaders;
    return carried == null ? Map.of() : carried;
  }

  public Map<String, List<String>> carriedBody() {
    Map<String, List<String>> carried = sanitizedBody;
    return carried == null ? Map.of() : carried;
  }

  public boolean saysWhatWasOnTheWire() {
    return !carriedHeaders().isEmpty() || !carriedBody().isEmpty();
  }

  private static SequencedMap<String, List<String>> copied(Map<String, List<String>> named) {
    LinkedHashMap<String, List<String>> inTheOrderTheTrailRecordedThem = new LinkedHashMap<>();
    named.forEach((name, values) -> inTheOrderTheTrailRecordedThem.put(name, List.copyOf(values)));
    return Collections.unmodifiableSequencedMap(inTheOrderTheTrailRecordedThem);
  }
}
