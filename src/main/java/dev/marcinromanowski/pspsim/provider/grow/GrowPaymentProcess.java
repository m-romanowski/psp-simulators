package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record GrowPaymentProcess(
    TestRunId testRunId,
    String processId,
    String processToken,
    String merchantId,
    String chargeType,
    String chargeSum,
    String description,
    boolean saveCardToken,
    String successUrl,
    String cancelUrl,
    @Nullable String notifyUrl,
    String payerFullName,
    String payerPhone,
    @Nullable String payerEmail,
    Map<String, String> customFields,
    String hostedLink,
    String nativeStatusCode,
    String nativeStatus,
    NormalizedStatus normalizedStatus,
    @Nullable GrowProcessOutcome outcome,
    ObjectOrigin origin,
    Instant createdAt,
    Instant updatedAt) {

  GrowPaymentProcess {
    customFields = Map.copyOf(customFields);
  }

  boolean holdsFunds() {
    return GrowFields.CHARGE_TYPE_SUSPENDED.equals(chargeType);
  }

  GrowPaymentProcess reaching(
      GrowProcessOutcome reached,
      String reachedNativeStatusCode,
      String reachedNativeStatus,
      NormalizedStatus reachedNormalizedStatus,
      Instant at) {
    return new GrowPaymentProcess(
        testRunId,
        processId,
        processToken,
        merchantId,
        chargeType,
        chargeSum,
        description,
        saveCardToken,
        successUrl,
        cancelUrl,
        notifyUrl,
        payerFullName,
        payerPhone,
        payerEmail,
        customFields,
        hostedLink,
        reachedNativeStatusCode,
        reachedNativeStatus,
        reachedNormalizedStatus,
        reached,
        origin,
        createdAt,
        at);
  }
}
