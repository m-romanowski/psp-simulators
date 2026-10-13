package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveredHeaders;
import dev.marcinromanowski.pspsim.kernel.fault.FaultedDelivery;
import dev.marcinromanowski.pspsim.kernel.fault.RenderedResponse;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class AzericardApiResponse {

  static final String DIAGNOSTIC_CONTENT_TYPE = "text/plain; charset=utf-8";

  private final TestRunId testRunId;
  private final FaultedDelivery delivery;
  private final ProviderOutcome outcome;
  private final @Nullable String providerObjectId;
  private final Map<String, List<String>> providerHeaders;

  static AzericardApiResponse ofTheGateway(
      TestRunId testRunId,
      FaultedDelivery delivery,
      ProviderOutcome outcome,
      @Nullable String providerObjectId,
      AzericardBody body) {
    return new AzericardApiResponse(testRunId, delivery, outcome, providerObjectId, body.headers());
  }

  static AzericardApiResponse ofThePayerPage(
      TestRunId testRunId,
      FaultedDelivery delivery,
      ProviderOutcome outcome,
      @Nullable String providerObjectId) {
    return new AzericardApiResponse(
        testRunId, delivery, outcome, providerObjectId, AzericardPayerPage.HEADERS);
  }

  static AzericardApiResponse aDiagnostic(
      int httpStatus, String explanation, Map<String, List<String>> onEveryAnswer) {
    return new AzericardApiResponse(
        TestRunId.UNKNOWN,
        new RenderedResponse(
                httpStatus, DIAGNOSTIC_CONTENT_TYPE, explanation.getBytes(StandardCharsets.UTF_8))
            .asDelivered(),
        ProviderOutcome.UNKNOWN,
        null,
        onEveryAnswer);
  }

  private AzericardApiResponse(
      TestRunId testRunId,
      FaultedDelivery delivery,
      ProviderOutcome outcome,
      @Nullable String providerObjectId,
      Map<String, List<String>> providerHeaders) {
    this.providerHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(providerHeaders));
    this.testRunId = testRunId;
    this.delivery = delivery;
    this.outcome = outcome;
    this.providerObjectId = providerObjectId;
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
    return DeliveredHeaders.of(delivery, providerHeaders);
  }

  public byte[] body() {
    return delivery.body();
  }

  public long declaredContentLength() {
    return delivery.declaredContentLength();
  }

  public boolean completesTheBody() {
    return delivery.completesTheBody();
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
