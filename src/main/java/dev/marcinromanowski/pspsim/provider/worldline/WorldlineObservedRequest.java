package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record WorldlineObservedRequest(
    TestRunId testRunId,
    String accountId,
    String keyName,
    String keyValue,
    String operation,
    String fingerprint,
    ExchangeVerdict verdict,
    @Nullable Integer httpStatus,
    @Nullable String contentType,
    @Nullable String body,
    @Nullable String providerObjectId,
    boolean stateChanged,
    Instant at) {}
