package dev.marcinromanowski.pspsim.provider.tranzila;

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

public record TranzilaApiResponse(
    TestRunId testRunId,
    FaultedDelivery delivery,
    ProviderOutcome outcome,
    @Nullable String providerObjectId,
    Map<String, List<String>> onEveryAnswer) {

  public TranzilaApiResponse {
    onEveryAnswer = Collections.unmodifiableMap(new LinkedHashMap<>(onEveryAnswer));
  }

  public TranzilaApiResponse(
      TestRunId testRunId,
      FaultedDelivery delivery,
      ProviderOutcome outcome,
      @Nullable String providerObjectId) {
    this(testRunId, delivery, outcome, providerObjectId, Map.of());
  }

  public int httpStatus() {
    return delivery.httpStatus();
  }

  public Map<String, List<String>> headers() {
    Map<String, List<String>> headers = new LinkedHashMap<>(DeliveredHeaders.of(delivery));
    headers.putAll(onEveryAnswer);
    return Collections.unmodifiableMap(headers);
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
