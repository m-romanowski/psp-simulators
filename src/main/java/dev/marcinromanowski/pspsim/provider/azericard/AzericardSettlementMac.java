package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum AzericardSettlementMac {
  LIVE_WIRE,

  PUBLISHED_EXAMPLE;

  static final String SETTING = "azericard.settlement-mac";

  static final String IS_ONE_OF_TWO =
      SETTING
          + " must be LIVE_WIRE or PUBLISHED_EXAMPLE. Provider azericard publishes one field"
          + " order for a completion and a reversal and a worked example that computes from it,"
          + " and the gateway a real merchant reaches accepts another, which adds TIMESTAMP and"
          + " NONCE that the manual lists as not in the MAC. Neither is this simulator's"
          + " invention and both are reachable, because a suite written against the manual and a"
          + " suite written against the bank are both suites a tester brings here";

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            LIVE_WIRE.name(),
            "sign as the gateway a real merchant reaches does: AMOUNT, CURRENCY, ORDER, RRN,"
                + " INT_REF, TERMINAL, TRTYPE, TIMESTAMP, NONCE"),
        new AcceptedValue(
            PUBLISHED_EXAMPLE.name(),
            "sign as the manual publishes and its worked example computes: AMOUNT, CURRENCY,"
                + " TERMINAL, TRTYPE, ORDER, RRN, INT_REF, with TIMESTAMP and NONCE left out"));
  }

  static byte[] theSourceTheManualPublishesFor(AzericardForm form) {
    return AzericardMac.sourceOf(
        form.required(AzericardFields.AMOUNT),
        form.required(AzericardFields.CURRENCY),
        form.required(AzericardFields.TERMINAL),
        form.required(AzericardFields.TRTYPE),
        form.required(AzericardFields.ORDER),
        form.required(AzericardFields.RRN),
        form.required(AzericardFields.INT_REF));
  }

  static AzericardSettlementMac of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return LIVE_WIRE;
    }
    for (AzericardSettlementMac signing : values()) {
      if (signing.name().equals(declared)) {
        return signing;
      }
    }
    throw new IllegalArgumentException(property + IS_ONE_OF_TWO);
  }
}
