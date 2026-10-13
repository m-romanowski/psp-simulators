package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum GrowRefundAllowance {
  ONE_REFUND(1),

  TWO_REFUNDS(2);

  static final String SETTING = "refundAllowance";

  private final int maximum;

  GrowRefundAllowance(int maximum) {
    this.maximum = maximum;
  }

  boolean admits(int refundsAlreadyMade) {
    return refundsAlreadyMade < maximum;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            ONE_REFUND.name(), "one refund per transaction, and a second one is refused"),
        new AcceptedValue(
            TWO_REFUNDS.name(), "two refunds per transaction, and a third one is refused"));
  }

  static GrowRefundAllowance of(@Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return ONE_REFUND;
    }
    for (GrowRefundAllowance allowance : values()) {
      if (allowance.name().equals(configured)) {
        return allowance;
      }
    }
    throw new IllegalArgumentException("must be one of " + ONE_REFUND + ", " + TWO_REFUNDS);
  }
}
