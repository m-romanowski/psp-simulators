package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class GrowAuditFixtures {

  private GrowAuditFixtures() {}

  static AuditRecord record(TestRunId testRunId, String auditId) {
    return new AuditRecord(
        auditId,
        testRunId,
        TestRunResolutionRule.ACCOUNT_CREDENTIALS,
        GrowLightApi.GROW,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.SUCCESS,
        "POST",
        "/api/light/server/1.0/createPaymentProcess",
        200,
        "734754",
        null,
        null,
        1,
        Instant.parse("2026-09-07T12:00:00Z"),
        Map.of("Content-Type", List.of("application/json")),
        Map.of("status", List.of("1"), "data.processId", List.of("734754")),
        new DeliveredResponse(null, 61, 61, Duration.ZERO));
  }

  static GrowPaymentProcess process(TestRunId testRunId, String processId) {
    return new GrowPaymentProcess(
        testRunId,
        processId,
        "2153287cd9af46457ae842337250b78d",
        "merchant-1",
        "2",
        "120.50",
        "A hold",
        true,
        "https://merchant.example/thanks",
        "https://merchant.example/cancel",
        "https://merchant.example/callbacks/grow",
        "Israel Israeli",
        "0505503144",
        "payer@merchant.example",
        Map.of("cField1", "session-abc", "cField2", "intent-99"),
        "89a71a",
        GrowNativeStatus.NOT_PAID_CODE,
        GrowNativeStatus.NOT_PAID,
        NormalizedStatus.NOT_INITIALIZED,
        null,
        ObjectOrigin.PROVIDER_API,
        Instant.parse("2026-09-07T12:00:00Z"),
        Instant.parse("2026-09-07T12:00:00Z"));
  }
}
