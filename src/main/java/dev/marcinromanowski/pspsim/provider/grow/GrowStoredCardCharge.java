package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record GrowStoredCardCharge(
    TestRunId testRunId,
    String merchantId,
    String uniqueIdentifier,
    @Nullable String groupIdentifier,
    @Nullable String transactionId,
    Map<String, String> customFields,
    Instant createdAt) {

  GrowStoredCardCharge {
    customFields = Map.copyOf(customFields);
  }

  GrowStoredCardCharge submittedWithoutACharge() {
    return new GrowStoredCardCharge(
        testRunId, merchantId, uniqueIdentifier, groupIdentifier, null, customFields, createdAt);
  }
}
