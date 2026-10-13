package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record StripeObservedRequest(
    TestRunId testRunId,
    String accountId,
    String idempotencyKey,
    String operation,
    String fingerprint,
    ExchangeVerdict verdict,
    @Nullable Integer httpStatus,
    @Nullable String contentType,
    @Nullable String body,
    @Nullable String providerObjectId,
    boolean stateChanged,
    @Nullable AppliedFault transportFault,
    Instant at) {}
