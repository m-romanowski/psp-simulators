package dev.marcinromanowski.pspsim.kernel.http;

import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import org.jspecify.annotations.Nullable;

public record AFailedRequest(
    WireFault fault,
    String method,
    String address,
    int statusNothingElseChose,
    @Nullable Throwable error,
    @Nullable String theRunItNamed) {}
