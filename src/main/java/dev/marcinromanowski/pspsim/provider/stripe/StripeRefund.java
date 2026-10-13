package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record StripeRefund(
    TestRunId testRunId,
    String refundId,
    String intentId,
    @Nullable String chargeId,
    String accountId,
    Amount amount,
    String currency,
    @Nullable StripeRefundReason reason,
    StripeRefundStatus status,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  NormalizedStatus normalizedStatus() {
    return status.normalized();
  }
}
