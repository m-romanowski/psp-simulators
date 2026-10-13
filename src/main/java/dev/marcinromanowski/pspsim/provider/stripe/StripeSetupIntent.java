package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record StripeSetupIntent(
    TestRunId testRunId,
    String setupIntentId,
    String accountId,
    @Nullable String customerId,
    @Nullable String paymentMethodId,
    StripeSetupUsage usage,
    StripeSetupStatus status,
    String clientSecret,
    @Nullable StripeDecline lastSetupError,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final String A_FAILED_SETUP_RETURNS_TO_REQUIRES_PAYMENT_METHOD =
      "provider stripe publishes that a failed setup returns the SetupIntent to"
          + " requires_payment_method and carries the reason in last_setup_error, so a stored"
          + " setup intent that names a setup error in any other status would be a status this"
          + " simulator invented for a failure";

  static final String A_SUCCEEDED_SETUP_SET_A_METHOD_UP =
      "provider stripe publishes that a SetupIntent reaches succeeded by setting a payment method"
          + " up for later use, so a stored setup intent that succeeded and names no payment"
          + " method would report a setup of nothing";

  StripeSetupIntent {
    if (lastSetupError != null && status != StripeSetupStatus.REQUIRES_PAYMENT_METHOD) {
      throw new IllegalArgumentException(A_FAILED_SETUP_RETURNS_TO_REQUIRES_PAYMENT_METHOD);
    }
    if (status == StripeSetupStatus.SUCCEEDED && paymentMethodId == null) {
      throw new IllegalArgumentException(A_SUCCEEDED_SETUP_SET_A_METHOD_UP);
    }
  }

  NormalizedStatus normalizedStatus() {
    if (status == StripeSetupStatus.REQUIRES_PAYMENT_METHOD && lastSetupError != null) {
      return NormalizedStatus.FAILED;
    }
    return status.normalized();
  }

  StripeSetupIntent succeededWith(String paymentMethod, Instant at) {
    return new StripeSetupIntent(
        testRunId,
        setupIntentId,
        accountId,
        customerId,
        paymentMethod,
        usage,
        StripeSetupStatus.SUCCEEDED,
        clientSecret,
        null,
        origin,
        createdAt,
        at);
  }

  StripeSetupIntent failedWith(StripeDecline decline, String paymentMethod, Instant at) {
    return new StripeSetupIntent(
        testRunId,
        setupIntentId,
        accountId,
        customerId,
        paymentMethod,
        usage,
        StripeSetupStatus.REQUIRES_PAYMENT_METHOD,
        clientSecret,
        decline,
        origin,
        createdAt,
        at);
  }
}
