package dev.marcinromanowski.pspsim.kernel.idempotency;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record KeyedRequest(
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

  public static final String NOTHING_WAS_ANSWERED =
      "this simulator answered nothing under this key at this point";

  public KeyedRequest {
    if (sequence < 0) {
      throw new IllegalArgumentException(
          "a request under a key is numbered from the first one this simulator answered, so "
              + sequence
              + " names a request that came before it");
    }
    if (fingerprint.isBlank()) {
      throw new IllegalArgumentException(
          "every request under a key carries the fingerprint of what it sent, because that is the"
              + " only thing that tells a repeat of the same request from a repeat of the key with"
              + " something else in it");
    }
    if (operation.isBlank()) {
      throw new IllegalArgumentException(
          "every request under a key names the operation it asked for, because a key reused on"
              + " another endpoint is a conflict and not a replay");
    }
    if (stateChanged && !verdict.couldHaveChangedState()) {
      throw new IllegalArgumentException(
          "a request recorded as "
              + verdict.name()
              + " is "
              + verdict.describedAs()
              + ", so no operation of this simulator ran under it and stateChanged cannot be true:"
              + " that would show a tester an effect nothing produced");
    }
  }

  public boolean carriesAResponse() {
    return httpStatus != null && body != null;
  }

  public @Nullable String whenTheFaultLanded() {
    AppliedFault applied = transportFault;
    if (applied == null) {
      return null;
    }
    FaultPlacement placement = applied.placement();
    return switch (placement) {
      case INSTEAD_OF_THE_OPERATION ->
          applied.kind().name()
              + " replaced the operation, so this request changed nothing before it broke";
      case AFTER_THE_OPERATION ->
          applied.kind().name()
              + " landed on the response, after the operation had already run and taken effect";
    };
  }
}
