package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum StripeRefundStatus {
  PENDING("pending", NormalizedStatus.PROCESSING, true),
  REQUIRES_ACTION("requires_action", NormalizedStatus.PROCESSING, true),
  SUCCEEDED("succeeded", NormalizedStatus.SUCCEEDED, true),
  FAILED("failed", NormalizedStatus.FAILED, false),
  CANCELED("canceled", NormalizedStatus.CANCELLED, false);

  private final String wireValue;
  private final NormalizedStatus normalized;
  private final boolean holdsTheMoney;

  StripeRefundStatus(String wireValue, NormalizedStatus normalized, boolean holdsTheMoney) {
    this.wireValue = wireValue;
    this.normalized = normalized;
    this.holdsTheMoney = holdsTheMoney;
  }

  String wireValue() {
    return wireValue;
  }

  NormalizedStatus normalized() {
    return normalized;
  }

  boolean holdsTheMoney() {
    return holdsTheMoney;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            PENDING.name(),
            "a refund of this account comes back \"pending\", so your client reads a refund still"
                + " being processed, and its amount is already off what the payment can refund"),
        new AcceptedValue(
            REQUIRES_ACTION.name(),
            "a refund comes back \"requires_action\", so your client reads a refund waiting on the"
                + " payer, and its amount is already off what the payment can refund"),
        new AcceptedValue(
            SUCCEEDED.name(),
            "a refund comes back \"succeeded\", where Stripe's own card refund comes to rest, and"
                + " its amount is off what the payment can refund"),
        new AcceptedValue(
            FAILED.name(),
            "a refund comes back \"failed\", and its amount stays refundable because no money"
                + " moved"),
        new AcceptedValue(
            CANCELED.name(),
            "a refund comes back \"canceled\", and its amount stays refundable because no money"
                + " moved"));
  }

  static Optional<StripeRefundStatus> of(String wireValue) {
    for (StripeRefundStatus status : values()) {
      if (status.wireValue.equals(wireValue)) {
        return Optional.of(status);
      }
    }
    return Optional.empty();
  }

  static Optional<StripeRefundStatus> named(String name) {
    for (StripeRefundStatus status : values()) {
      if (status.name().equals(name)) {
        return Optional.of(status);
      }
    }
    return Optional.empty();
  }

  static String theStatusesTheVendorPublishes() {
    List<String> names = new ArrayList<>();
    for (StripeRefundStatus status : values()) {
      names.add(status.name());
    }
    return String.join(", ", names);
  }
}
