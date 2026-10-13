package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class WorldlineApiResponse {

  private final TestRunId testRunId;
  private final FaultedDelivery delivery;
  private final ProviderOutcome outcome;
  private final @Nullable String providerObjectId;
  private final Map<String, List<String>> headers;

  WorldlineApiResponse(
      TestRunId testRunId,
      FaultedDelivery delivery,
      ProviderOutcome outcome,
      @Nullable String providerObjectId,
      Map<String, List<String>> headers) {
    this.testRunId = testRunId;
    this.delivery = delivery;
    this.outcome = outcome;
    this.providerObjectId = providerObjectId;
    this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
  }

  public TestRunId testRunId() {
    return testRunId;
  }

  public int httpStatus() {
    return delivery.httpStatus();
  }

  public String contentType() {
    return delivery.contentType();
  }

  public Map<String, List<String>> headers() {
    return DeliveredHeaders.of(delivery, headers);
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

  public ProviderOutcome outcome() {
    return outcome;
  }

  public @Nullable String providerObjectId() {
    return providerObjectId;
  }
}
