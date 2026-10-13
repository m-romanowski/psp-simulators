package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record StripeCharge(
    TestRunId testRunId,
    String chargeId,
    String intentId,
    String accountId,
    Amount amount,
    String currency,
    @Nullable String paymentMethod,
    StripeChargeStatus status,
    Instant createdAt) {

  boolean paid() {
    return status.paid();
  }

  BigDecimal amountCaptured(StripePaymentIntent intent) {
    return paid() ? intent.capturedTotal() : BigDecimal.ZERO;
  }

  BigDecimal amountRefunded(StripePaymentIntent intent) {
    return paid() ? intent.settledRefunds() : BigDecimal.ZERO;
  }

  boolean captured(StripePaymentIntent intent, Instant now) {
    return !intent.authorizationOpenAt(now) && amountCaptured(intent).signum() > 0;
  }

  boolean refunded(StripePaymentIntent intent, Instant now) {
    return captured(intent, now) && amountRefunded(intent).compareTo(amountCaptured(intent)) == 0;
  }
}
