package dev.marcinromanowski.pspsim.kernel.money;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record MonetaryOperation(
    int sequence,
    OperationKind kind,
    Amount amount,
    @Nullable Boolean closesAuthorization,
    @Nullable String createdObjectId,
    String nativeStatus,
    NormalizedStatus normalizedStatus,
    Instant at) {}
