package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum GrowMerchantBalance {
  AVAILABLE(true),

  EMPTY(false);

  static final String SETTING = "merchantBalance";

  private final boolean fundsARefund;

  GrowMerchantBalance(boolean fundsARefund) {
    this.fundsARefund = fundsARefund;
  }

  boolean fundsARefund() {
    return fundsARefund;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            AVAILABLE.name(),
            "the balance funds a refund of this account, so no refund is refused for want of"
                + " funds"),
        new AcceptedValue(
            EMPTY.name(),
            "the balance funds nothing, so a refund that passes every other check is refused 115"
                + " for want of funds"));
  }

  static GrowMerchantBalance of(@Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return AVAILABLE;
    }
    for (GrowMerchantBalance balance : values()) {
      if (balance.name().equals(configured)) {
        return balance;
      }
    }
    throw new IllegalArgumentException("must be one of " + AVAILABLE + ", " + EMPTY);
  }
}
