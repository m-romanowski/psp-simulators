package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum GrowRefundExcessRejection {
  ORIGINAL_AMOUNT {
    @Override
    GrowError rejection() {
      return GrowErrors.aboveTheOriginalAmount();
    }
  },

  CHARGE_AMOUNT {
    @Override
    GrowError rejection() {
      return GrowErrors.aboveTheChargeAmount();
    }
  },

  TRANSACTION_AMOUNT {
    @Override
    GrowError rejection() {
      return GrowErrors.aboveTheTransactionAmount();
    }
  };

  static final String SETTING = "refundExcessRejection";

  abstract GrowError rejection();

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            ORIGINAL_AMOUNT.name(),
            "too large a refund is refused 105, which names the original amount"),
        new AcceptedValue(
            CHARGE_AMOUNT.name(),
            "too large a refund is refused 190, which names the charge amount"),
        new AcceptedValue(
            TRANSACTION_AMOUNT.name(),
            "too large a refund is refused 218, which names the transaction amount"));
  }

  static GrowRefundExcessRejection of(@Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return ORIGINAL_AMOUNT;
    }
    for (GrowRefundExcessRejection rejection : values()) {
      if (rejection.name().equals(configured)) {
        return rejection;
      }
    }
    throw new IllegalArgumentException(
        "must be one of " + ORIGINAL_AMOUNT + ", " + CHARGE_AMOUNT + ", " + TRANSACTION_AMOUNT);
  }
}
