package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record KeyedExchangeRow(
    TestRunId testRunId,
    String accountId,
    String keyName,
    String keyValue,
    String operation,
    String fingerprint,
    ExchangeVerdict whereNoRequestClaimedTheKey,
    ExchangeVerdict afterTheRequestThatClaimedTheKey,
    @Nullable Integer httpStatus,
    @Nullable String contentType,
    @Nullable String body,
    @Nullable String providerObjectId,
    boolean stateChanged,
    @Nullable AppliedFault transportFault,
    Instant at) {}
