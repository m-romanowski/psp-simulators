package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveryAbandonment;
import dev.marcinromanowski.pspsim.kernel.fault.HeldDelivery;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class TranzilaDoubles {

  private TranzilaDoubles() {}

  static SimulatorClock clockAt(Instant instant) {
    return testRunId -> instant;
  }

  static final class MovableClock implements SimulatorClock {

    private Instant now;

    MovableClock(Instant now) {
      this.now = now;
    }

    void moveTo(Instant later) {
      now = later;
    }

    @Override
    public Instant now(TestRunId testRunId) {
      return now;
    }
  }

  static TranzilaTerminal terminal(TestRunId testRunId, String accountId, String terminalName) {
    return terminal(testRunId, accountId, terminalName, Map.of());
  }

  static TranzilaTerminal terminal(
      TestRunId testRunId,
      String accountId,
      String terminalName,
      Map<String, String> configuration) {
    Map<String, String> declared = new LinkedHashMap<>(configuration);
    declared.putIfAbsent(TranzilaTerminal.APP_KEY_PROPERTY, "key-" + accountId);
    declared.putIfAbsent(TranzilaTerminal.APP_SECRET_PROPERTY, "secret-" + accountId);
    declared.put(TranzilaTerminal.TERMINAL_NAME_PROPERTY, terminalName);
    return TranzilaTerminal.of(
        "accounts[0].configuration.", testRunId, new ConfiguredAccount(accountId, declared));
  }

  static final class Terminals implements TranzilaTerminals {

    private final Map<String, TranzilaTerminal> stored = new LinkedHashMap<>();

    Terminals with(TranzilaTerminal terminal) {
      stored.put(terminal.testRunId().value() + '\u0000' + terminal.accountId(), terminal);
      return this;
    }

    @Override
    public void merge(TestRunId testRunId, List<TranzilaTerminal> configured) {
      configured.forEach(this::with);
    }

    @Override
    public void remove(TestRunId testRunId, String accountId) {
      stored.remove(testRunId.value() + '\u0000' + accountId);
    }

    @Override
    public Optional<TranzilaTerminal> find(TestRunId testRunId, String accountId) {
      return Optional.ofNullable(stored.get(testRunId.value() + '\u0000' + accountId));
    }

    @Override
    public List<TranzilaTerminal> ofTheRun(TestRunId testRunId) {
      return stored.values().stream().filter(held -> held.testRunId().equals(testRunId)).toList();
    }

    @Override
    public List<TranzilaTerminal> byAppKey(String appKey) {
      return stored.values().stream().filter(held -> held.appKey().equals(appKey)).toList();
    }

    @Override
    public List<TranzilaTerminal> named(String terminalName) {
      return stored.values().stream()
          .filter(held -> held.terminalName().equals(terminalName))
          .toList();
    }
  }

  static final class Transactions implements TranzilaTransactions {

    private final List<TranzilaTransaction> stored = new ArrayList<>();
    private final Map<String, Long> arrivedAt = new HashMap<>();
    private long arrivals;
    private int takenIds;
    private boolean closedByAnotherRequestFirst;

    Transactions theNextIdsAreTaken(int howMany) {
      takenIds = howMany;
      return this;
    }

    Transactions closedByAnotherRequestFirst() {
      closedByAnotherRequestFirst = true;
      return this;
    }

    private @Nullable TranzilaTransaction theWinner;

    Transactions closedFirstBy(TranzilaTransaction winner) {
      theWinner = winner;
      return this;
    }

    List<TranzilaTransaction> all() {
      return List.copyOf(stored);
    }

    @Override
    public TranzilaInsert insert(TranzilaTransaction transaction) {
      if (takenIds > 0) {
        takenIds--;
        return TranzilaInsert.ID_TAKEN;
      }
      if (closedByAnotherRequestFirst) {
        return TranzilaInsert.CLOSED_BY_ANOTHER_REQUEST_FIRST;
      }
      TranzilaTransaction winner = theWinner;
      if (winner != null) {
        theWinner = null;
        insert(winner);
        return TranzilaInsert.CLOSED_BY_ANOTHER_REQUEST_FIRST;
      }
      stored.add(transaction);
      arrivedAt.put(
          keyOf(transaction.testRunId(), transaction.transactionId()), Long.valueOf(nextArrival()));
      return TranzilaInsert.STORED;
    }

    long nextArrival() {
      arrivals++;
      return arrivals;
    }

    private static String keyOf(TestRunId testRunId, long transactionId) {
      return testRunId.value() + '\u0000' + transactionId;
    }

    @Override
    public Optional<TranzilaPayment> paymentHolding(TestRunId testRunId, long transactionId) {
      return stored.stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> held.transactionId() == transactionId)
          .findFirst()
          .flatMap(held -> TranzilaGroups.theOnePaymentOf(ofTheRoot(testRunId, held.rootTxnId())));
    }

    private List<TranzilaTransaction> ofTheRoot(TestRunId testRunId, long root) {
      return stored.stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> held.rootTxnId() == root)
          .toList();
    }

    @Override
    public List<TranzilaPayment> paymentsHolding(TestRunId testRunId, List<Long> transactionIds) {
      Set<Long> roots = new LinkedHashSet<>();
      stored.stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> transactionIds.contains(held.transactionId()))
          .forEach(held -> roots.add(held.rootTxnId()));
      return TranzilaGroups.paymentsOf(
          stored.stream()
              .filter(held -> held.testRunId().equals(testRunId))
              .filter(held -> roots.contains(held.rootTxnId()))
              .toList());
    }

    @Override
    public Optional<TranzilaTransaction> carryingTheToken(
        TestRunId testRunId, String accountId, String token) {
      return stored.reversed().stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> held.accountId().equals(accountId))
          .filter(held -> token.equals(held.token()))
          .findFirst();
    }

    @Override
    public Optional<TranzilaTransaction> approvedUnderDcDisable(
        TranzilaDcDisableKey key, Instant since) {
      return stored.stream()
          .filter(held -> held.testRunId().equals(key.testRunId()))
          .filter(held -> held.accountId().equals(key.accountId()))
          .filter(held -> key.dcDisable().equals(held.dcDisable()))
          .filter(held -> held.type() == key.type())
          .filter(held -> Objects.equals(held.verifyMode(), key.verifyMode()))
          .filter(TranzilaTransaction::approved)
          .filter(held -> held.createdAt().isAfter(since))
          .findFirst();
    }

    @Override
    public synchronized <T> T oneAtATimeUnder(TranzilaDcDisableKey key, Supplier<T> decision) {
      held.add(key);
      return together(decision);
    }

    @Override
    public <T> T together(Supplier<T> work) {
      List<TranzilaTransaction> before = List.copyOf(stored);
      try {
        return work.get();
      } catch (RuntimeException rolledBack) {
        stored.clear();
        stored.addAll(before);
        throw rolledBack;
      }
    }

    private final List<TranzilaDcDisableKey> held = new ArrayList<>();

    List<TranzilaDcDisableKey> theKeysHeld() {
      return List.copyOf(held);
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, long transactionId) {
      Long arrival = arrivedAt.get(keyOf(testRunId, transactionId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival, Long.toString(transactionId)));
    }

    @Override
    public List<ArrivalOf<TranzilaObject>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      List<ArrivalOf<Long>> listed = new ArrayList<>();
      for (TranzilaTransaction held : stored.reversed()) {
        long arrival = arrivedAt.get(keyOf(held.testRunId(), held.transactionId()));
        if (held.testRunId().equals(testRunId)
            && arrival < ProviderObjectCursor.walkingBackFrom(after)
            && listed.size() < limit) {
          listed.add(new ArrivalOf<>(arrival, held.transactionId()));
        }
      }
      return TranzilaGroups.inTheOrderOf(
          listed, stored.stream().filter(held -> held.testRunId().equals(testRunId)).toList());
    }

    @Override
    public int clear(TestRunId testRunId) {
      int before = stored.size();
      stored.removeIf(held -> held.testRunId().equals(testRunId));
      return before - stored.size();
    }

    @Override
    public int clearAll() {
      int before = stored.size();
      stored.clear();
      return before;
    }
  }

  static final class Nonces implements TranzilaNonces {

    private final Map<String, Instant> seen = new HashMap<>();

    @Override
    public int claim(
        TestRunId testRunId, String accountId, String nonce, Instant now, Instant seenBefore) {
      String key = testRunId.value() + '\u0000' + accountId + '\u0000' + nonce;
      Instant earlier = seen.get(key);
      if (earlier != null && !earlier.isBefore(seenBefore)) {
        return 0;
      }
      seen.put(key, now);
      return 1;
    }

    @Override
    public int clear(TestRunId testRunId) {
      int before = seen.size();
      seen.keySet().removeIf(key -> key.startsWith(testRunId.value() + '\u0000'));
      return before - seen.size();
    }

    @Override
    public int clearAll() {
      int before = seen.size();
      seen.clear();
      return before;
    }
  }

  static final class Exchanges implements TranzilaExchanges {

    private final List<TranzilaObservedRequest> observed = new ArrayList<>();

    List<TranzilaObservedRequest> everythingItObserved() {
      return List.copyOf(observed);
    }

    @Override
    public int howOftenSeen(
        TestRunId testRunId, String accountId, String keyName, String keyValue) {
      return (int)
          observed.stream()
              .filter(held -> held.testRunId().equals(testRunId))
              .filter(held -> held.accountId().equals(accountId))
              .filter(held -> held.keyName().equals(keyName))
              .filter(held -> held.keyValue().equals(keyValue))
              .count();
    }

    @Override
    public int observed(TranzilaObservedRequest request) {
      if (refusals > 0) {
        refusals--;
        return 0;
      }
      observed.add(request);
      return 1;
    }

    private int refusals;

    Exchanges takenMeanwhile(int howOften) {
      refusals = howOften;
      return this;
    }

    @Override
    public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
      List<RecordedKeyedRequest> rows = new ArrayList<>();
      Map<String, Integer> sequences = new HashMap<>();
      long arrival = 0;
      for (TranzilaObservedRequest request : observed) {
        if (!request.testRunId().equals(query.testRunId())) {
          continue;
        }
        int seq = sequences.merge(request.keyName() + request.keyValue(), 1, Integer::sum) - 1;
        rows.add(
            new RecordedKeyedRequest(
                request.accountId(),
                request.keyName(),
                request.keyValue(),
                ++arrival,
                seq,
                request.verdict(),
                request.fingerprint(),
                request.operation(),
                request.httpStatus(),
                request.contentType(),
                request.body(),
                request.providerObjectId(),
                request.stateChanged(),
                null,
                request.at()));
      }
      return KeyedExchanges.pageOf(TranzilaKeyMechanisms.MECHANISMS, query.testRunId(), rows);
    }

    @Override
    public int clear(TestRunId testRunId) {
      int before = observed.size();
      observed.removeIf(held -> held.testRunId().equals(testRunId));
      return before - observed.size();
    }

    @Override
    public int clearAll() {
      int before = observed.size();
      observed.clear();
      return before;
    }
  }

  static final class HeldResponses implements DelayedDeliveries {

    private final List<TestRunId> cancelled = new ArrayList<>();
    private int cancelledEverything;

    @Override
    public HeldDelivery hold(
        ProviderId provider,
        TestRunId testRunId,
        Duration delay,
        Runnable deliver,
        Consumer<DeliveryAbandonment> abandon) {
      throw new UnsupportedOperationException("no test of this double holds a delivery");
    }

    @Override
    public int cancel(ProviderId provider, TestRunId testRunId) {
      cancelled.add(testRunId);
      return 0;
    }

    @Override
    public int cancelAll(ProviderId provider) {
      cancelledEverything++;
      return 0;
    }

    @Override
    public int stillHeld() {
      return 0;
    }

    List<TestRunId> theRunsItWasAskedToCancel() {
      return List.copyOf(cancelled);
    }

    int howOftenItWasAskedToCancelEverything() {
      return cancelledEverything;
    }
  }

  static final class Audit implements AuditTrail {

    private final List<AuditRecord> records = new ArrayList<>();

    @Override
    public void record(AuditRecord record) {
      records.add(record);
    }

    @Override
    public List<AuditRecord> of(ProviderId provider, TestRunId testRunId) {
      return records.stream()
          .filter(r -> r.provider().equals(provider) && r.testRunId().equals(testRunId))
          .toList();
    }

    @Override
    public int clear(ProviderId provider, TestRunId testRunId) {
      List<AuditRecord> doomed = of(provider, testRunId);
      records.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll(ProviderId provider) {
      int size = records.size();
      records.clear();
      return size;
    }

    List<AuditRecord> all() {
      return List.copyOf(records);
    }
  }

  static final class Runs implements TestRuns {

    private final TestRunId defaultTestRun;
    private final Set<TestRunId> registered = new LinkedHashSet<>();

    Runs(TestRunId defaultTestRun) {
      this.defaultTestRun = defaultTestRun;
    }

    @Override
    public void register(TestRunId testRunId) {
      registered.add(testRunId);
    }

    @Override
    public int forget(TestRunId testRunId) {
      return registered.remove(testRunId) ? 1 : 0;
    }

    @Override
    public boolean exists(TestRunId testRunId) {
      return defaultTestRun.equals(testRunId) || registered.contains(testRunId);
    }

    @Override
    public List<TestRunId> all() {
      Set<TestRunId> named = new LinkedHashSet<>();
      named.add(defaultTestRun);
      named.addAll(registered);
      return List.copyOf(named);
    }
  }

  static final class CountingIds implements IdGenerator {

    private final Map<Integer, Integer> issued = new HashMap<>();

    @Override
    public String hex(int length) {
      return pad(Integer.toHexString(next(length)), length);
    }

    @Override
    public String digits(int length) {
      return "1" + pad(Integer.toString(next(length)), length - 1);
    }

    private int next(int length) {
      return issued.merge(length, 1, Integer::sum);
    }

    private static String pad(String value, int length) {
      return "0".repeat(Math.max(0, length - value.length())) + value;
    }
  }

  static final class Pages implements TranzilaPages {

    private final List<TranzilaPage> stored = new ArrayList<>();
    private final Map<String, Long> arrivedAt = new HashMap<>();
    private final Transactions arrivals;
    private int takenIds;
    private final List<String> settledMeanwhile = new ArrayList<>();
    private final List<String> lockedMeanwhile = new ArrayList<>();

    Pages(Transactions arrivals) {
      this.arrivals = arrivals;
    }

    Pages theNextIdsAreTaken(int howMany) {
      takenIds = howMany;
      return this;
    }

    Pages settledByAnotherSubmissionFirst(String pageId) {
      settledMeanwhile.add(pageId);
      return this;
    }

    Pages heldByAnotherSubmission(String pageId) {
      lockedMeanwhile.add(pageId);
      return this;
    }

    List<TranzilaPage> all() {
      return List.copyOf(stored);
    }

    @Override
    public int open(TranzilaPage page) {
      if (takenIds > 0) {
        takenIds--;
        return 0;
      }
      stored.add(page);
      arrivedAt.put(page.pageId(), Long.valueOf(arrivals.nextArrival()));
      return 1;
    }

    @Override
    public Optional<TranzilaPage> find(String pageId) {
      return stored.stream().filter(held -> held.pageId().equals(pageId)).findFirst();
    }

    @Override
    public Optional<TranzilaPage> find(TestRunId testRunId, String pageId) {
      return find(pageId).filter(held -> held.testRunId().equals(testRunId));
    }

    @Override
    public Optional<TranzilaPage> lockedWhileOpen(TestRunId testRunId, String pageId) {
      if (lockedMeanwhile.remove(pageId)) {
        return Optional.empty();
      }
      if (settledMeanwhile.remove(pageId)) {
        find(testRunId, pageId)
            .ifPresent(
                held ->
                    settle(
                        held.settledAs(
                            PayerOutcome.ABANDONED, null, null, null, null, held.createdAt())));
        return Optional.empty();
      }
      return find(testRunId, pageId).filter(held -> !held.isSettled());
    }

    @Override
    public Optional<TranzilaPage> ofTheTransaction(TestRunId testRunId, long transactionId) {
      return stored.stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> Long.valueOf(transactionId).equals(held.transactionId()))
          .findFirst();
    }

    @Override
    public Optional<TranzilaPage> savingTheToken(
        TestRunId testRunId, String accountId, String token) {
      return stored.reversed().stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> held.accountId().equals(accountId))
          .filter(held -> token.equals(held.token()))
          .filter(held -> held.cardMask() != null)
          .findFirst();
    }

    @Override
    public List<TranzilaPage> allById(TestRunId testRunId, List<String> pageIds) {
      return stored.stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> pageIds.contains(held.pageId()))
          .toList();
    }

    @Override
    public void settle(TranzilaPage settled) {
      stored.replaceAll(held -> held.pageId().equals(settled.pageId()) ? settled : held);
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String pageId) {
      return find(testRunId, pageId)
          .map(held -> new ProviderObjectCursor(arrivedAt.get(pageId).longValue(), pageId));
    }

    @Override
    public List<ArrivalOf<TranzilaPage>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      List<ArrivalOf<TranzilaPage>> listed = new ArrayList<>();
      for (TranzilaPage held : stored.reversed()) {
        long arrival = arrivedAt.get(held.pageId()).longValue();
        if (held.testRunId().equals(testRunId)
            && arrival < ProviderObjectCursor.walkingBackFrom(after)
            && listed.size() < limit) {
          listed.add(new ArrivalOf<>(arrival, held));
        }
      }
      return List.copyOf(listed);
    }

    @Override
    public int clear(TestRunId testRunId) {
      int before = stored.size();
      stored.removeIf(held -> held.testRunId().equals(testRunId));
      return before - stored.size();
    }

    @Override
    public int clearAll() {
      int before = stored.size();
      stored.clear();
      return before;
    }
  }

  static final class Handshakes implements TranzilaHandshakes {

    private final List<TranzilaHandshake> stored = new ArrayList<>();
    private int takenThtks;

    Handshakes theNextThtksAreTaken(int howMany) {
      takenThtks = howMany;
      return this;
    }

    List<TranzilaHandshake> all() {
      return List.copyOf(stored);
    }

    @Override
    public int insert(TranzilaHandshake handshake) {
      if (takenThtks > 0) {
        takenThtks--;
        return 0;
      }
      stored.add(handshake);
      return 1;
    }

    @Override
    public Optional<TranzilaHandshake> find(TestRunId testRunId, String thtk) {
      return stored.stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> held.thtk().equals(thtk))
          .findFirst();
    }

    @Override
    public void retainUntil(TestRunId testRunId, String thtk, Instant expiresAt) {
      stored.replaceAll(
          held ->
              held.testRunId().equals(testRunId) && held.thtk().equals(thtk)
                  ? new TranzilaHandshake(
                      held.thtk(),
                      held.testRunId(),
                      held.accountId(),
                      held.terminalName(),
                      held.sum(),
                      held.requestParams(),
                      held.createdAt(),
                      expiresAt)
                  : held);
    }

    @Override
    public int clear(TestRunId testRunId) {
      int before = stored.size();
      stored.removeIf(held -> held.testRunId().equals(testRunId));
      return before - stored.size();
    }

    @Override
    public int clearAll() {
      int before = stored.size();
      stored.clear();
      return before;
    }
  }

  static final class Accounts implements ProviderAccounts {

    private final List<WebhookTarget> targets = new ArrayList<>();
    private final List<TestRunId> runs = new ArrayList<>();

    Accounts callingBack(TestRunId testRunId, String accountId, String url) {
      targets.add(new WebhookTarget(accountId, url));
      runs.add(testRunId);
      return this;
    }

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      plan.store();
    }

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      plan.remove();
      return true;
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.of();
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      List<WebhookTarget> ofTheRun = new ArrayList<>();
      for (int index = 0; index < targets.size(); index++) {
        if (runs.get(index).equals(testRunId)) {
          ofTheRun.add(targets.get(index));
        }
      }
      return List.copyOf(ofTheRun);
    }
  }
}
