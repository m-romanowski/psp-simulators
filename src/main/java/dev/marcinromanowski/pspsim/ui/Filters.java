package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record Filters(
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
    @Nullable String limit) {

  public List<String> directions() {
    return AuditQuery.theDirectionsThisTrailHolds();
  }

  public List<String> origins() {
    return AuditQuery.theOriginsThisTrailHolds();
  }
}
