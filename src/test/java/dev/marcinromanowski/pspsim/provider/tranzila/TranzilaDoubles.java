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
import dev.marcinromanowski.pspsim.kernel.idempotency.InMemoryKeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

  static final class Records implements TranzilaRecords {

    private final Transactions transactions;

    Records(Transactions transactions) {
      this.transactions = transactions;
    }

    private List<TranzilaTransaction> of(TestRunId testRunId, String accountId) {
      return transactions.all().stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> held.accountId().equals(accountId))
          .toList();
    }

    private static boolean within(TranzilaTransaction held, LocalDate start, LocalDate end) {
      LocalDate on = LocalDate.ofInstant(held.createdAt(), ZoneOffset.UTC);
      return !on.isBefore(start) && !on.isAfter(end);
    }

    @Override
    public Optional<TranzilaRecorded> byIndex(
        TestRunId testRunId, String accountId, long transactionId) {
      return of(testRunId, accountId).stream()
          .filter(held -> held.transactionId() == transactionId)
          .findFirst()
          .map(transactions::recordedOf);
    }

    @Override
    public List<TranzilaRecorded> between(
        TestRunId testRunId, String accountId, TranzilaLookup.Window window) {
      List<TranzilaTransaction> listed = new ArrayList<>(of(testRunId, accountId));
      Map<TranzilaTransaction, Integer> arrival = new HashMap<>();
      for (int at = 0; at < listed.size(); at++) {
        arrival.put(listed.get(at), Integer.valueOf(at));
      }
      return listed.stream()
          .filter(held -> within(held, window.start(), window.end()))
          .sorted(
              (one, other) -> {
                int byTime = one.createdAt().compareTo(other.createdAt());
                int ordered = byTime != 0 ? byTime : arrival.get(one).compareTo(arrival.get(other));
                return ordered * window.direction();
              })
          .skip(window.offset())
          .limit(window.limit())
          .map(transactions::recordedOf)
          .toList();
    }

    @Override
    public long countedBetween(
        TestRunId testRunId, String accountId, LocalDate start, LocalDate end) {
      return of(testRunId, accountId).stream().filter(held -> within(held, start, end)).count();
    }

    @Override
    public List<TranzilaUserSlot> slotsOf(TestRunId testRunId, String accountId) {
      return transactions.slotsOf(testRunId, accountId);
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

    private final Map<String, List<TranzilaUserField>> described = new HashMap<>();
    private final Map<String, List<TranzilaRecorded.Item>> itemized = new HashMap<>();
    private long itemIds;

    List<TranzilaTransaction> all() {
      return List.copyOf(stored);
    }

    private final Map<String, Map<String, TranzilaUserSlot>> slots = new HashMap<>();

    private void describe(
        TranzilaTransaction transaction,
        List<TranzilaUserField> userDefinedFields,
        List<TranzilaItem> items) {
      String key = keyOf(transaction.testRunId(), transaction.transactionId());
      Map<String, TranzilaUserSlot> held =
          slots.computeIfAbsent(
              transaction.testRunId().value() + '\u0000' + transaction.accountId(),
              absent -> new LinkedHashMap<>());
      List<TranzilaUserField> kept = new ArrayList<>();
      for (TranzilaUserField field : TranzilaUserField.theOnesASlotKeeps(userDefinedFields)) {
        if (!held.containsKey(field.name()) && held.size() < TranzilaUserSlot.THE_MOST_SLOTS) {
          held.put(
              field.name(),
              new TranzilaUserSlot(held.size() + 1, field.name(), transaction.createdAt()));
        }
        if (held.containsKey(field.name())) {
          kept.add(field);
        }
      }
      described.put(key, List.copyOf(kept));
      List<TranzilaRecorded.Item> numbered = new ArrayList<>();
      for (TranzilaItem item : items) {
        itemIds++;
        numbered.add(new TranzilaRecorded.Item(itemIds, item));
      }
      itemized.put(key, List.copyOf(numbered));
    }

    List<TranzilaUserSlot> slotsOf(TestRunId testRunId, String accountId) {
      return List.copyOf(
          slots.getOrDefault(testRunId.value() + '\u0000' + accountId, Map.of()).values());
    }

    TranzilaRecorded recordedOf(TranzilaTransaction transaction) {
      String key = keyOf(transaction.testRunId(), transaction.transactionId());
      return new TranzilaRecorded(
          transaction,
          described.getOrDefault(key, List.of()),
          itemized.getOrDefault(key, List.of()));
    }

    @Override
    public TranzilaInsert insert(
        TranzilaTransaction transaction,
        List<TranzilaUserField> userDefinedFields,
        List<TranzilaItem> items) {
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
        insert(winner, List.of(), List.of());
        return TranzilaInsert.CLOSED_BY_ANOTHER_REQUEST_FIRST;
      }
      stored.add(transaction);
      describe(transaction, userDefinedFields, items);
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
    public Optional<TranzilaWallet> theWalletThatMinted(
        TestRunId testRunId, String accountId, String token) {
      return stored.stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> held.accountId().equals(accountId))
          .filter(held -> token.equals(held.token()))
          .filter(TranzilaTransaction::isTheOriginal)
          .map(TranzilaTransaction::broker)
          .filter(java.util.Objects::nonNull)
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

    private final InMemoryKeyedExchanges exchanged = new InMemoryKeyedExchanges();

    Exchanges takenMeanwhile(int howOften) {
      exchanged.takenMeanwhile(howOften);
      return this;
    }

    List<RecordedKeyedRequest> everythingItObserved() {
      return exchanged.recorded();
    }

    @Override
    public void observed(KeyedObservation observation) {
      exchanged.logOf(TranzilaKeyMechanisms.MECHANISMS).record(observation);
    }

    @Override
    public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
      return exchanged.exchanges(TranzilaKeyMechanisms.MECHANISMS, query);
    }

    @Override
    public int clear(TestRunId testRunId) {
      return exchanged.clear(testRunId);
    }

    @Override
    public int clearAll() {
      return exchanged.clearAll();
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

  static final class Challenges implements TranzilaChallenges {

    private final List<TranzilaChallenge> stored = new ArrayList<>();
    private final Map<String, Long> arrivedAt = new HashMap<>();
    private final Transactions arrivals;
    private int takenIds;

    Challenges(Transactions arrivals) {
      this.arrivals = arrivals;
    }

    Challenges theNextIdsAreTaken(int howMany) {
      takenIds = howMany;
      return this;
    }

    List<TranzilaChallenge> all() {
      return List.copyOf(stored);
    }

    @Override
    public int open(TranzilaChallenge challenge) {
      if (takenIds > 0) {
        takenIds--;
        return 0;
      }
      stored.add(challenge);
      arrivedAt.put(challenge.trackId(), Long.valueOf(arrivals.nextArrival()));
      return 1;
    }

    @Override
    public Optional<TranzilaChallenge> find(String trackId) {
      return stored.stream().filter(held -> held.trackId().equals(trackId)).findFirst();
    }

    @Override
    public Optional<TranzilaChallenge> find(TestRunId testRunId, String trackId) {
      return find(trackId).filter(held -> held.testRunId().equals(testRunId));
    }

    @Override
    public Optional<TranzilaChallenge> lockedUntilCompleted(
        TestRunId testRunId, String accountId, String trackId) {
      return find(testRunId, trackId)
          .filter(held -> held.accountId().equals(accountId))
          .filter(held -> !held.isCompleted());
    }

    @Override
    public int answer(TranzilaChallenge answered) {
      Optional<TranzilaChallenge> waiting =
          find(answered.trackId()).filter(held -> !held.isAnswered());
      if (waiting.isEmpty()) {
        return 0;
      }
      stored.replaceAll(held -> held.trackId().equals(answered.trackId()) ? answered : held);
      return 1;
    }

    @Override
    public int complete(TranzilaChallenge completed) {
      Optional<TranzilaChallenge> open =
          find(completed.trackId()).filter(held -> !held.isCompleted());
      if (open.isEmpty()) {
        return 0;
      }
      stored.replaceAll(held -> held.trackId().equals(completed.trackId()) ? completed : held);
      return 1;
    }

    @Override
    public List<TranzilaChallenge> allById(TestRunId testRunId, List<String> trackIds) {
      return stored.stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> trackIds.contains(held.trackId()))
          .toList();
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String trackId) {
      return find(testRunId, trackId)
          .map(held -> new ProviderObjectCursor(arrivedAt.get(trackId).longValue(), trackId));
    }

    @Override
    public List<ArrivalOf<TranzilaChallenge>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      List<ArrivalOf<TranzilaChallenge>> listed = new ArrayList<>();
      for (TranzilaChallenge held : stored.reversed()) {
        long arrival = arrivedAt.get(held.trackId()).longValue();
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
