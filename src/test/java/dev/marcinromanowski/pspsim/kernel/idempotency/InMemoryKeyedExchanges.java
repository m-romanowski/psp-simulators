package dev.marcinromanowski.pspsim.kernel.idempotency;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeLog;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeRow;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeRows;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.SequencedRows;
import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKey;
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
import tools.jackson.databind.json.JsonMapper;

public final class InMemoryKeyedExchanges implements KeyedExchangeRows {

  public static final FingerprintKey THE_KEY_OF_EVERY_TEST_RUN =
      new FingerprintKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");

  private final Map<TestRunId, List<RecordedKeyedRequest>> byTestRun = new LinkedHashMap<>();

  public KeyedExchangeLog logOf(ProviderKeyMechanisms mechanisms) {
    return new KeyedExchangeLog(
        mechanisms,
        testRunId -> THE_KEY_OF_EVERY_TEST_RUN,
        this,
        new SequencedRows(),
        JsonMapper.builder().build());
  }

  @Override
  public synchronized int recordedAtTheNextFreeSequence(KeyedExchangeRow row) {
    if (takenMeanwhile > 0) {
      takenMeanwhile--;
      return 0;
    }
    List<RecordedKeyedRequest> earlier =
        under(row.testRunId(), new UnderOneKey(row.accountId(), row.keyName(), row.keyValue()));
    boolean claimed = false;
    for (RecordedKeyedRequest recorded : earlier) {
      claimed |= recorded.verdict().couldHaveChangedState();
    }
    arrivals++;
    byTestRun
        .computeIfAbsent(row.testRunId(), run -> new ArrayList<>())
        .add(
            new RecordedKeyedRequest(
                row.accountId(),
                row.keyName(),
                row.keyValue(),
                arrivals,
                earlier.size(),
                claimed
                    ? row.afterTheRequestThatClaimedTheKey()
                    : row.whereNoRequestClaimedTheKey(),
                row.fingerprint(),
                row.operation(),
                row.httpStatus(),
                row.contentType(),
                row.body(),
                row.providerObjectId(),
                row.stateChanged(),
                row.transportFault(),
                row.at()));
    return 1;
  }

  private long arrivals;

  private int takenMeanwhile;

  public synchronized void takenMeanwhile(int howOften) {
    takenMeanwhile = howOften;
  }

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

  public synchronized List<RecordedKeyedRequest> recorded() {
    List<RecordedKeyedRequest> all = new ArrayList<>();
    for (List<RecordedKeyedRequest> rows : byTestRun.values()) {
      all.addAll(rows);
    }
    all.sort(Comparator.comparingLong(RecordedKeyedRequest::arrival));
    return List.copyOf(all);
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
