package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;

record StripeEphemeralKey(
    TestRunId testRunId,
    String ephemeralKeyId,
    String accountId,
    String customerId,
    String secret,
    Instant createdAt,
    Instant expiresAt) {

  boolean hasExpiredAt(Instant now) {
    return !now.isBefore(expiresAt);
  }

  boolean reaches(String customerId) {
    return this.customerId.equals(customerId);
  }
}
