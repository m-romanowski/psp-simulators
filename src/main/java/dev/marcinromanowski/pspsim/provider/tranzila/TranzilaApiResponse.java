package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record TranzilaApiResponse(
    TestRunId testRunId,
    FaultedDelivery delivery,
    ProviderOutcome outcome,
    @Nullable String providerObjectId) {

  public int httpStatus() {
    return delivery.httpStatus();
  }

  public Map<String, List<String>> headers() {
    return DeliveredHeaders.of(delivery);
  }

  public byte[] body() {
    return delivery.body();
  }

  public long declaredContentLength() {
    return delivery.declaredContentLength();
  }

  public Duration delay() {
    return delivery.delay();
  }
}
