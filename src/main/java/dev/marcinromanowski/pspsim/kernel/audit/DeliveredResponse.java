package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

public record DeliveredResponse(
    @Nullable AppliedFault transportFault,
    long declaredContentLength,
    long writtenBodyBytes,
    Duration heldFor) {}
