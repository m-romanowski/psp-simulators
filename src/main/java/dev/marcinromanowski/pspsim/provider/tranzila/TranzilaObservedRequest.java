package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record TranzilaObservedRequest(
    TestRunId testRunId,
    String accountId,
    String keyName,
    String keyValue,
    String operation,
    String fingerprint,
    ExchangeVerdict verdict,
    int httpStatus,
    String contentType,
    String body,
    @Nullable String providerObjectId,
    boolean stateChanged,
    Instant at) {}
