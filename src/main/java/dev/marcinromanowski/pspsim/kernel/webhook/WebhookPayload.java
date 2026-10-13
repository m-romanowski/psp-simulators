package dev.marcinromanowski.pspsim.kernel.webhook;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

public final class WebhookPayload {

  private static final String CONTENT_TYPE = "Content-Type";

  private final String contentType;
  private final byte[] body;
  private final SequencedMap<String, List<String>> wireHeaders;
  private final SequencedMap<String, List<String>> sanitizedBody;

  public WebhookPayload(
      String contentType,
      Map<String, List<String>> headers,
      byte[] body,
      Map<String, List<String>> sanitizedBody) {
    this.contentType = contentType;
    this.body = body.clone();
    LinkedHashMap<String, List<String>> onTheWire = new LinkedHashMap<>();
    onTheWire.put(CONTENT_TYPE, List.of(contentType));
    for (Map.Entry<String, List<String>> header : headers.entrySet()) {
      if (CONTENT_TYPE.equalsIgnoreCase(header.getKey())) {
        throw new IllegalArgumentException(
            "the content type is stated once, as contentType, and never again in headers");
      }
      onTheWire.put(header.getKey(), List.copyOf(header.getValue()));
    }
    this.wireHeaders = Collections.unmodifiableSequencedMap(onTheWire);
    LinkedHashMap<String, List<String>> copiedBody = new LinkedHashMap<>();
    sanitizedBody.forEach((name, values) -> copiedBody.put(name, List.copyOf(values)));
    this.sanitizedBody = Collections.unmodifiableSequencedMap(copiedBody);
  }

  public String contentType() {
    return contentType;
  }

  public SequencedMap<String, List<String>> wireHeaders() {
    return wireHeaders;
  }

  public SequencedMap<String, List<String>> providerHeaders() {
    LinkedHashMap<String, List<String>> providerOwned = new LinkedHashMap<>(wireHeaders);
    providerOwned.remove(CONTENT_TYPE);
    return Collections.unmodifiableSequencedMap(providerOwned);
  }

  public byte[] body() {
    return body.clone();
  }

  public SequencedMap<String, List<String>> sanitizedBody() {
    return sanitizedBody;
  }

  @Override
  public String toString() {
    return "WebhookPayload[contentType=" + contentType + ", bytes=" + body.length + "]";
  }
}
