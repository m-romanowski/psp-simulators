package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record StripeCustomer(
    TestRunId testRunId,
    String customerId,
    String accountId,
    @Nullable String email,
    @Nullable Instant deletedAt,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  static final int LONGEST_CUSTOMER_ID = 128;

  boolean deleted() {
    return deletedAt != null;
  }

  StripeCustomer withEmail(@Nullable String changed, Instant at) {
    return new StripeCustomer(
        testRunId, customerId, accountId, changed, deletedAt, origin, createdAt, at);
  }

  StripeCustomer deletedAt(Instant at) {
    return new StripeCustomer(testRunId, customerId, accountId, email, at, origin, createdAt, at);
  }
}
