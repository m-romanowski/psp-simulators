package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum GrowSettlementCeiling {
  FRAME_CAPTURE(BigDecimal.ONE) {
    @Override
    GrowError rejection() {
      return GrowErrors.aboveTheFrameCapture();
    }
  },

  THIRTY_PERCENT_ABOVE(new BigDecimal("1.3")) {
    @Override
    GrowError rejection() {
      return GrowErrors.aboveThirtyPercentOfTheTransaction();
    }
  };

  static final String SETTING = "settlementCeiling";

  private final BigDecimal multiplier;

  GrowSettlementCeiling(BigDecimal multiplier) {
    this.multiplier = multiplier;
  }

  abstract GrowError rejection();

  boolean admits(BigDecimal requested, BigDecimal held) {
    return requested.compareTo(held.multiply(multiplier)) <= 0;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            FRAME_CAPTURE.name(),
            "a settlement may not exceed the amount held, and one that does is refused 804"),
        new AcceptedValue(
            THIRTY_PERCENT_ABOVE.name(),
            "a settlement may reach 30% above the amount held, and one beyond that is refused"
                + " 814"));
  }

  static GrowSettlementCeiling of(@Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return FRAME_CAPTURE;
    }
    for (GrowSettlementCeiling ceiling : values()) {
      if (ceiling.name().equals(configured)) {
        return ceiling;
      }
    }
    throw new IllegalArgumentException(
        "must be one of " + FRAME_CAPTURE + ", " + THIRTY_PERCENT_ABOVE);
  }
}
