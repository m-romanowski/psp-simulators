package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum GrowAcknowledgementMode {
  HTTP_2XX,

  APPROVE_TRANSACTION;

  static final String SETTING = "callbackAcknowledgement";

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            HTTP_2XX.name(),
            "any 2xx answer from your endpoint acknowledges the callback, and nothing more is"
                + " expected of you"),
        new AcceptedValue(
            APPROVE_TRANSACTION.name(),
            "a 2xx answer leaves the callback waiting for your separate approveTransaction call,"
                + " and it stays unacknowledged until that call arrives"));
  }

  static GrowAcknowledgementMode of(@Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return HTTP_2XX;
    }
    for (GrowAcknowledgementMode mode : values()) {
      if (mode.name().equals(configured)) {
        return mode;
      }
    }
    throw new IllegalArgumentException("must be one of " + HTTP_2XX + ", " + APPROVE_TRANSACTION);
  }
}
