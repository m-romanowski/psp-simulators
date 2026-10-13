package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record AuditRecord(
    String auditId,
    TestRunId testRunId,
    @Nullable TestRunResolutionRule resolutionRule,
    ProviderId provider,
    @Nullable String accountId,
    ObjectOrigin origin,
    AuditDirection direction,
    @Nullable ProviderOutcome outcome,
    String method,
    @Nullable String path,
    @Nullable Integer httpStatus,
    @Nullable String providerObjectId,
    @Nullable RefusalSubject refusal,
    @Nullable String scenarioId,
    int attempt,
    Instant createdAt,
    Map<String, List<String>> sanitizedHeaders,
    Map<String, List<String>> sanitizedBody,
    @Nullable DeliveredResponse delivered) {

  public AuditRecord {
    if (path == null && direction != AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT) {
      throw new AuditRowNamesNoPathException(
          AuditRowNamesNoPathException.ONLY_A_CALLBACK_WITH_NO_TARGET_NAMES_NO_PATH);
    }
    accountId = TheAccountARowNames.theIdentityOrNone(accountId);
    if (refusal != null) {
      providerObjectId = refusal.theObjectItNames().orElse(null);
    }
  }

  public String theCalledPath() {
    if (path == null) {
      throw new AuditRowNamesNoPathException(
          AuditRowNamesNoPathException.ONLY_A_CALLBACK_WITH_NO_TARGET_NAMES_NO_PATH);
    }
    return path;
  }
}
