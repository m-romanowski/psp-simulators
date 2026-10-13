package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record AzericardPayerSession(
    String sessionId,
    TestRunId testRunId,
    AzericardRoute route,
    String terminalId,
    String merchantId,
    Map<String, List<String>> form,
    Map<String, List<String>> headers,
    @Nullable PayerOutcome outcome,
    @Nullable String intRef,
    ObjectOrigin origin,
    Instant createdAt,
    @Nullable Instant settledAt) {

  static final int SESSION_ID_LENGTH = 32;

  AzericardPayerSession {
    form = deeplyImmutable(form);
    headers = deeplyImmutable(headers);
    if (form.keySet().stream().anyMatch(AzericardFields.CARD_DATA::contains)) {
      throw new ACardWouldBeKeptException();
    }
    if (form.containsKey(AzericardFields.M_INFO)) {
      throw new BrowserDataWouldBeKeptException();
    }
  }

  static AzericardPayerSession opened(
      String sessionId,
      TestRunId testRunId,
      AzericardMessage message,
      AzericardTerminal terminal,
      Map<String, List<String>> headers,
      Instant now) {
    return new AzericardPayerSession(
        sessionId,
        testRunId,
        message.route(),
        terminal.terminalId(),
        terminal.accountId(),
        message.withoutWhatIsNeverKept(),
        headers,
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        now,
        null);
  }

  AzericardMessage message() {
    return AzericardMessage.of(route, form);
  }

  boolean wasOpenedBy(AzericardMessage sent) {
    return message().fingerprint().equals(sent.fingerprint());
  }

  boolean isSettled() {
    return outcome != null;
  }

  AzericardPayerSession settledAs(
      PayerOutcome reached, @Nullable String paymentIntRef, Instant at) {
    return new AzericardPayerSession(
        sessionId,
        testRunId,
        route,
        terminalId,
        merchantId,
        form,
        headers,
        reached,
        paymentIntRef,
        origin,
        createdAt,
        at);
  }

  private static Map<String, List<String>> deeplyImmutable(Map<String, List<String>> values) {
    Map<String, List<String>> copied = new LinkedHashMap<>();
    values.forEach((name, sent) -> copied.put(name, List.copyOf(sent)));
    return Map.copyOf(copied);
  }
}
