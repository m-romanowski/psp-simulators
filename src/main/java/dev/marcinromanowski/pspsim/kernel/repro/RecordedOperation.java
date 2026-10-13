package dev.marcinromanowski.pspsim.kernel.repro;

import org.jspecify.annotations.Nullable;

public record RecordedOperation(
    int sequence,
    @Nullable String kind,
    @Nullable String amount,
    @Nullable Boolean closesAuthorization,
    @Nullable String createdObjectId,
    @Nullable String nativeStatus,
    @Nullable String normalizedStatus,
    @Nullable String at) {}
