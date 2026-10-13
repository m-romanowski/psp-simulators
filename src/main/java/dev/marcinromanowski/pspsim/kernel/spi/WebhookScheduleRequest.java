package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

public record WebhookScheduleRequest(
    TestRunId testRunId,
    String providerObjectId,
    @Nullable String eventType,
    @Nullable String reuseEventId,
    @Nullable Duration delay,
    ObjectOrigin askedBy) {}
