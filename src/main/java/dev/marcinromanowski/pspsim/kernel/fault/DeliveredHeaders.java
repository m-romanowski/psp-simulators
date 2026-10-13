package dev.marcinromanowski.pspsim.kernel.fault;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeliveredHeaders {

  public static final String CONTENT_TYPE = "Content-Type";

  public static final String CONNECTION = "Connection";

  public static final String CLOSE = "close";

  private DeliveredHeaders() {}

  public static Map<String, List<String>> of(FaultedDelivery delivery) {
    return of(delivery, Map.of());
  }

  public static Map<String, List<String>> of(
      FaultedDelivery delivery, Map<String, List<String>> providerHeaders) {
    Map<String, List<String>> headers = new LinkedHashMap<>(providerHeaders);
    headers.put(CONTENT_TYPE, List.of(delivery.contentType()));
    if (!delivery.completesTheBody()) {
      headers.put(CONNECTION, List.of(CLOSE));
    }
    return Collections.unmodifiableMap(headers);
  }
}
