package dev.marcinromanowski.pspsim.kernel.fixture;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record PaymentMethodFixture(
    String fixtureId,
    ProviderId provider,
    TestRunId testRunId,
    String merchantId,
    String ownerId,
    PaymentMethodKind type,
    String fixtureAlias,
    @Nullable FixtureCard card,
    Map<String, String> providerIds,
    @Nullable String defaultScenario,
    Instant createdAt) {

  public PaymentMethodFixture {
    providerIds = theIdsInTheOrderTheSeedDeclaredThem(providerIds);
  }

  private static SequencedMap<String, String> theIdsInTheOrderTheSeedDeclaredThem(
      Map<String, String> declared) {
    LinkedHashMap<String, String> inTheOrderTheSeedDeclaredThem = new LinkedHashMap<>(declared);
    return Collections.unmodifiableSequencedMap(inTheOrderTheSeedDeclaredThem);
  }
}
