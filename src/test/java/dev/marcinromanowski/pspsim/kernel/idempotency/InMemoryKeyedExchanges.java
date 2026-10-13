package dev.marcinromanowski.pspsim.kernel.idempotency;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class InMemoryKeyedExchanges {

  private final Map<TestRunId, List<RecordedKeyedRequest>> byTestRun = new LinkedHashMap<>();

  private long arrivals;

  public synchronized boolean hasSeen(
      TestRunId testRunId, String accountId, String keyName, String keyValue) {
    return !under(testRunId, new UnderOneKey(accountId, keyName, keyValue)).isEmpty();
  }

  public synchronized void observed(
      TestRunId testRunId,
      String accountId,
      String keyName,
      String keyValue,
      ExchangeVerdict verdict,
      String fingerprint,
      String operation,
      @Nullable Integer httpStatus,
      @Nullable String contentType,
      @Nullable String body,
      @Nullable String providerObjectId,
      boolean stateChanged,
      @Nullable AppliedFault transportFault,
      Instant at) {
    UnderOneKey key = new UnderOneKey(accountId, keyName, keyValue);
    arrivals++;
    byTestRun
        .computeIfAbsent(testRunId, run -> new ArrayList<>())
        .add(
            new RecordedKeyedRequest(
                accountId,
                keyName,
                keyValue,
                arrivals,
                under(testRunId, key).size(),
                verdict,
                fingerprint,
                operation,
                httpStatus,
                contentType,
                body,
                providerObjectId,
                stateChanged,
                transportFault,
                at));
  }

  public synchronized KeyedExchangePage exchanges(
      ProviderKeyMechanisms mechanisms, KeyedExchangeQuery query) {
    List<RecordedKeyedRequest> held = byTestRun.getOrDefault(query.testRunId(), List.of());
    List<RecordedKeyedRequest> firsts = new ArrayList<>();
    for (RecordedKeyedRequest row : held) {
      if (row.sequence() == 0 && row.arrival() < query.after()) {
        firsts.add(row);
      }
    }
    firsts.sort(Comparator.comparingLong(RecordedKeyedRequest::arrival).reversed());
    Set<UnderOneKey> page = new LinkedHashSet<>();
    for (RecordedKeyedRequest first : firsts) {
      if (page.size() == query.limit()) {
        break;
      }
      page.add(new UnderOneKey(first.accountId(), first.keyName(), first.keyValue()));
    }
    List<RecordedKeyedRequest> rows = new ArrayList<>();
    for (RecordedKeyedRequest row : held) {
      if (page.contains(new UnderOneKey(row.accountId(), row.keyName(), row.keyValue()))) {
        rows.add(row);
      }
    }
    return KeyedExchanges.pageOf(mechanisms, query.testRunId(), rows);
  }

  public int clear(TestRunId testRunId) {
    List<RecordedKeyedRequest> gone = byTestRun.remove(testRunId);
    return gone == null ? 0 : gone.size();
  }

  public int clearAll() {
    int held = 0;
    for (List<RecordedKeyedRequest> rows : byTestRun.values()) {
      held += rows.size();
    }
    byTestRun.clear();
    return held;
  }

  private List<RecordedKeyedRequest> under(TestRunId testRunId, UnderOneKey key) {
    List<RecordedKeyedRequest> found = new ArrayList<>();
    for (RecordedKeyedRequest row : byTestRun.getOrDefault(testRunId, List.of())) {
      if (key.equals(new UnderOneKey(row.accountId(), row.keyName(), row.keyValue()))) {
        found.add(row);
      }
    }
    return found;
  }

  private record UnderOneKey(String accountId, String keyName, String keyValue) {}
}
