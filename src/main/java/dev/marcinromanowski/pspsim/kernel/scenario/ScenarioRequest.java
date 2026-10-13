package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record ScenarioRequest(
    ProviderId provider,
    TestRunId testRunId,
    String storyKey,
    @Nullable String merchantId,
    @Nullable String orderId,
    @Nullable String providerObjectId,
    @Nullable String customerId,
    @Nullable String paymentMethodId,
    @Nullable Amount amount,
    @Nullable String currency,
    @Nullable String operation,
    Map<String, List<String>> headers) {

  public ScenarioRequest {
    if (storyKey.isBlank()) {
      throw new IllegalArgumentException(
          "storyKey must name the flow this request belongs to, which the provider resolves before"
              + " the decision is taken");
    }
    StoryKey.rejectWhatCannotBeStored(storyKey);
    headers = copied(headers);
  }

  private static SequencedMap<String, List<String>> copied(Map<String, List<String>> headers) {
    LinkedHashMap<String, List<String>> inTheOrderTheCallerSentThem = new LinkedHashMap<>();
    headers.forEach((name, sent) -> inTheOrderTheCallerSentThem.put(name, List.copyOf(sent)));
    return Collections.unmodifiableSequencedMap(inTheOrderTheCallerSentThem);
  }
}
