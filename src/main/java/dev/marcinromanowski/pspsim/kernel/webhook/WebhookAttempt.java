package dev.marcinromanowski.pspsim.kernel.webhook;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record WebhookAttempt(
    String deliveryId,
    int number,
    Instant attemptedAt,
    String targetUrl,
    @Nullable Map<String, List<String>> sentHeaders,
    Acknowledgement verdict,
    @Nullable Integer httpStatus,
    @Nullable TransportFailure failure,
    @Nullable String responsePreview) {

  private static final String CONTENT_TYPE = "Content-Type";

  public WebhookAttempt {
    Map<String, List<String>> onTheWire = sentHeaders;
    if (onTheWire != null) {
      SequencedMap<String, List<String>> copied = copied(onTheWire);
      if (contentTypeIn(copied) == null) {
        throw new IllegalArgumentException(
            "an attempt that recorded what it put on the wire names the content type it posted"
                + " under, and this one names "
                + copied.keySet());
      }
      sentHeaders = copied;
    }
  }

  public boolean theWireWasRecorded() {
    return sentHeaders != null;
  }

  public @Nullable String contentTypeSent() {
    Map<String, List<String>> onTheWire = sentHeaders;
    return onTheWire == null ? null : contentTypeIn(onTheWire);
  }

  private static SequencedMap<String, List<String>> copied(Map<String, List<String>> onTheWire) {
    LinkedHashMap<String, List<String>> inTheOrderTheAttemptPutThemOnTheWire =
        new LinkedHashMap<>();
    inTheOrderTheAttemptPutThemOnTheWire.putAll(onTheWire);
    return Collections.unmodifiableSequencedMap(inTheOrderTheAttemptPutThemOnTheWire);
  }

  private static @Nullable String contentTypeIn(Map<String, List<String>> headers) {
    for (Map.Entry<String, List<String>> header : headers.entrySet()) {
      if (CONTENT_TYPE.equalsIgnoreCase(header.getKey()) && !header.getValue().isEmpty()) {
        return header.getValue().getFirst();
      }
    }
    return null;
  }
}
