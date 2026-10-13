package dev.marcinromanowski.pspsim.kernel.idempotency;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record RecordedKeyedRequest(
    String accountId,
    String keyName,
    String keyValue,
    long arrival,
    int sequence,
    ExchangeVerdict verdict,
    String fingerprint,
    String operation,
    @Nullable Integer httpStatus,
    @Nullable String contentType,
    @Nullable String body,
    @Nullable String providerObjectId,
    boolean stateChanged,
    @Nullable AppliedFault transportFault,
    Instant at) {

  public KeyedRequest asRequest() {
    return new KeyedRequest(
        sequence,
        verdict,
        fingerprint,
        operation,
        httpStatus,
        contentType,
        body,
        providerObjectId,
        stateChanged,
        transportFault,
        at);
  }
}
