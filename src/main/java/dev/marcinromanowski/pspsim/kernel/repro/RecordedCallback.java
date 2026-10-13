package dev.marcinromanowski.pspsim.kernel.repro;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record RecordedCallback(
    @Nullable String deliveryId,
    @Nullable String eventId,
    @Nullable String eventType,
    @Nullable String providerObjectId,
    @Nullable String state,
    @Nullable String targetUrl,
    int attemptsMade,
    int attemptCap,
    @Nullable String nextAttemptAt,
    @Nullable String shapedBy,
    @Nullable String notSentBecause,
    @Nullable String contentType,
    @Nullable Map<String, List<String>> sanitizedPayload,
    @Nullable List<RecordedAttempt> attempts) {

  public RecordedCallback {
    Map<String, List<String>> payload = sanitizedPayload;
    if (payload != null) {
      sanitizedPayload = copied(payload);
    }
    attempts = attempts == null ? null : List.copyOf(attempts);
  }

  public List<RecordedAttempt> carriedAttempts() {
    List<RecordedAttempt> carried = attempts;
    return carried == null ? List.of() : carried;
  }

  public Map<String, List<String>> carriedPayload() {
    Map<String, List<String>> carried = sanitizedPayload;
    return carried == null ? Map.of() : carried;
  }

  private static SequencedMap<String, List<String>> copied(Map<String, List<String>> payload) {
    LinkedHashMap<String, List<String>> inTheOrderTheCallbackWasRenderedIn = new LinkedHashMap<>();
    payload.forEach(
        (name, values) -> inTheOrderTheCallbackWasRenderedIn.put(name, List.copyOf(values)));
    return Collections.unmodifiableSequencedMap(inTheOrderTheCallbackWasRenderedIn);
  }
}
