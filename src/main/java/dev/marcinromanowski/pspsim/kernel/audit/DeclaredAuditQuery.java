package dev.marcinromanowski.pspsim.kernel.audit;

import org.jspecify.annotations.Nullable;

public record DeclaredAuditQuery(
    @Nullable String testRunId,
    @Nullable String auditId,
    @Nullable String accountId,
    @Nullable String origin,
    @Nullable String direction,
    @Nullable String method,
    @Nullable String status,
    @Nullable String providerObjectId,
    @Nullable String scenarioId,
    @Nullable String from,
    @Nullable String until,
    @Nullable String after,
    @Nullable String limit) {}
