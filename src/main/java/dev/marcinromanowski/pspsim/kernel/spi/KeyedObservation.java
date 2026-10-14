package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record KeyedObservation(
    TestRunId testRunId,
    String accountId,
    String keyName,
    String keyValue,
    String operation,
    KeyedOutcome outcome,
    FingerprintedRequest request,
    @Nullable KeyedAnswer answer,
    @Nullable String providerObjectId,
    boolean stateChanged,
    @Nullable AppliedFault transportFault,
    Instant at) {}
