package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.fault.DelayedDeliveries;
import dev.marcinromanowski.pspsim.kernel.fault.DeliveryAbandonment;
import dev.marcinromanowski.pspsim.kernel.fault.HeldDelivery;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.InMemoryKeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

final class WorldlineDoubles {

  private WorldlineDoubles() {}

  private static long arrivals;

  static synchronized long theNextArrival() {
    arrivals++;
    return arrivals;
  }

  static String placeKey(TestRunId testRunId, String id) {
    return testRunId.value() + '\u0000' + id;
  }

  static SimulatorClock clockAt(Instant instant) {
    return testRunId -> instant;
  }

  static WorldlineMerchant merchant(TestRunId testRunId, String accountId, String merchantId) {
    return merchant(testRunId, accountId, merchantId, Map.of());
  }

  static WorldlineMerchant merchant(
      TestRunId testRunId, String accountId, String merchantId, Map<String, String> configuration) {
    Map<String, String> declared = new LinkedHashMap<>(configuration);
    declared.putIfAbsent(WorldlineMerchant.API_KEY_PROPERTY, "key-" + accountId);
    declared.putIfAbsent(WorldlineMerchant.API_SECRET_PROPERTY, "secret-" + accountId);
    declared.putIfAbsent(
        WorldlineAuthentication.SETTING,
        WorldlineAuthentication.THE_SIGNATURE_IS_NOT_VERIFIED.name());
    declared.put(WorldlineMerchant.MERCHANT_ID_PROPERTY, merchantId);
    return WorldlineMerchant.of(
        "accounts[0].configuration.", testRunId, new ConfiguredAccount(accountId, declared));
  }

  static final class Merchants implements WorldlineMerchants {

    private final Map<String, WorldlineMerchant> stored = new LinkedHashMap<>();

    Merchants with(WorldlineMerchant merchant) {
      stored.put(placeKey(merchant.testRunId(), merchant.accountId()), merchant);
      return this;
    }

    @Override
    public void merge(TestRunId testRunId, List<WorldlineMerchant> configured) {
      for (WorldlineMerchant merchant : configured) {
        with(merchant);
      }
    }

    @Override
    public void remove(TestRunId testRunId, String accountId) {
      stored.remove(placeKey(testRunId, accountId));
    }

    @Override
    public Optional<WorldlineMerchant> find(TestRunId testRunId, String accountId) {
      return Optional.ofNullable(stored.get(placeKey(testRunId, accountId)));
    }

    @Override
    public Optional<WorldlineMerchant> ofTheRunHolding(TestRunId testRunId, String merchantId) {
      return stored.values().stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> held.merchantId().equals(merchantId))
          .findFirst();
    }

    @Override
    public List<WorldlineMerchant> byApiKey(String apiKey) {
      return stored.values().stream().filter(held -> held.apiKey().equals(apiKey)).toList();
    }
  }

  static final class Tokenizations implements WorldlineTokenizations {

    private final Map<String, WorldlineTokenization> stored = new LinkedHashMap<>();
    private final Map<String, Long> arrivedAt = new LinkedHashMap<>();
    private String movedByAnotherRequestFirst = "";

    private boolean refusingEveryInsert;

    Tokenizations refusingEveryInsert() {
      refusingEveryInsert = true;
      return this;
    }

    Tokenizations movedByAnotherRequestFirst(String hostedTokenizationId) {
      movedByAnotherRequestFirst = hostedTokenizationId;
      return this;
    }

    Tokenizations with(WorldlineTokenization session) {
      insertIfAbsent(session);
      return this;
    }

    @Override
    public boolean insertIfAbsent(WorldlineTokenization session) {
      String key = placeKey(session.testRunId(), session.hostedTokenizationId());
      if (refusingEveryInsert || findByIdInAnyRun(session.hostedTokenizationId()).isPresent()) {
        return false;
      }
      stored.put(key, session);
      arrivedAt.put(key, Long.valueOf(theNextArrival()));
      return true;
    }

    @Override
    public Optional<WorldlineTokenization> findById(
        TestRunId testRunId, String hostedTokenizationId) {
      return Optional.ofNullable(stored.get(placeKey(testRunId, hostedTokenizationId)));
    }

    @Override
    public List<WorldlineTokenization> allById(
        TestRunId testRunId, List<String> hostedTokenizationIds) {
      return stored.values().stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> hostedTokenizationIds.contains(held.hostedTokenizationId()))
          .toList();
    }

    @Override
    public Optional<WorldlineTokenization> findByIdInAnyRun(String hostedTokenizationId) {
      return stored.values().stream()
          .filter(held -> held.hostedTokenizationId().equals(hostedTokenizationId))
          .findFirst();
    }

    @Override
    public Optional<WorldlineTokenization> holdingTheToken(TestRunId testRunId, String token) {
      return stored.values().stream()
          .filter(held -> held.testRunId().equals(testRunId))
          .filter(held -> token.equals(held.token()))
          .findFirst();
    }

    @Override
    public boolean moveFromAwaitingThePayerTo(WorldlineTokenization moved) {
      String key = placeKey(moved.testRunId(), moved.hostedTokenizationId());
      WorldlineTokenization held = stored.get(key);
      if (held == null || !held.isAwaitingThePayer()) {
        return false;
      }
      if (moved.hostedTokenizationId().equals(movedByAnotherRequestFirst)) {
        stored.put(key, held.expiredAt(moved.updatedAt()));
        movedByAnotherRequestFirst = "";
        return false;
      }
      stored.put(key, moved);
      return true;
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(
        TestRunId testRunId, String hostedTokenizationId) {
      Long arrival = arrivedAt.get(placeKey(testRunId, hostedTokenizationId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival.longValue(), hostedTokenizationId));
    }

    @Override
    public List<ArrivalOf<WorldlineTokenization>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      List<ArrivalOf<WorldlineTokenization>> found = new ArrayList<>();
      for (Map.Entry<String, WorldlineTokenization> held : stored.entrySet()) {
        WorldlineTokenization session = held.getValue();
        if (!session.testRunId().equals(testRunId)) {
          continue;
        }
        long arrival = arrivedAt.getOrDefault(held.getKey(), Long.valueOf(0L)).longValue();
        if (arrival < ProviderObjectCursor.walkingBackFrom(after)) {
          found.add(new ArrivalOf<>(arrival, session));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() > limit ? found.subList(0, limit) : found);
    }

    @Override
    public int clear(TestRunId testRunId) {
      List<String> doomed =
          stored.entrySet().stream()
              .filter(held -> held.getValue().testRunId().equals(testRunId))
              .map(Map.Entry::getKey)
              .toList();
      doomed.forEach(stored::remove);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      int size = stored.size();
      stored.clear();
      return size;
    }
  }

  static final class Payments implements WorldlinePayments {

    private final Map<String, WorldlinePayment> stored = new LinkedHashMap<>();
    private final Map<String, String> identifiers = new LinkedHashMap<>();
    private final Set<String> operations = new LinkedHashSet<>();
    private final Map<String, Long> arrivedAt = new LinkedHashMap<>();
    private boolean theObjectIsGone;
    private boolean theClaimIsTaken;
    private boolean refusingToStore;
    private boolean refusingEveryClaim;

    Payments refusingEveryIdentifierClaim() {
      refusingEveryClaim = true;
      return this;
    }

    Payments refusingToStoreAnything() {
      refusingToStore = true;
      return this;
    }

    Payments storingAgain() {
      refusingToStore = false;
      return this;
    }

    Payments theObjectIsGone() {
      theObjectIsGone = true;
      return this;
    }

    Payments theClaimIsTaken() {
      theClaimIsTaken = true;
      return this;
    }

    Payments with(WorldlinePayment payment) {
      insertIfAbsent(payment);
      return this;
    }

    @Override
    public boolean claimIdentifier(
        TestRunId testRunId, String identifier, String paymentId, int payIdSub, Instant at) {
      String key = placeKey(testRunId, identifier);
      String operation = placeKey(testRunId, paymentId) + '\u0000' + payIdSub;
      if (refusingEveryClaim
          || payIdSub < WorldlineIdentifiers.THE_FIRST_PAYIDSUB
          || identifiers.containsKey(key)
          || operations.contains(operation)) {
        return false;
      }
      identifiers.put(key, paymentId);
      operations.add(operation);
      return true;
    }

    @Override
    public boolean insertIfAbsent(WorldlinePayment payment) {
      if (refusingToStore) {
        return false;
      }
      String key = placeKey(payment.testRunId(), payment.paymentId());
      if (stored.containsKey(key)) {
        return false;
      }
      stored.put(key, payment);
      arrivedAt.put(key, Long.valueOf(theNextArrival()));
      claimIdentifier(
          payment.testRunId(),
          payment.paymentId(),
          payment.paymentId(),
          WorldlineIdentifiers.THE_FIRST_PAYIDSUB,
          payment.createdAt());
      int payIdSub = WorldlineIdentifiers.THE_FIRST_PAYIDSUB;
      for (String minted : payment.maintenanceIds()) {
        payIdSub++;
        claimIdentifier(
            payment.testRunId(), minted, payment.paymentId(), payIdSub, payment.createdAt());
      }
      return true;
    }

    @Override
    public Optional<WorldlinePayment> findById(TestRunId testRunId, String anyIdentifier) {
      String canonical = identifiers.get(placeKey(testRunId, anyIdentifier));
      return canonical == null
          ? Optional.empty()
          : Optional.ofNullable(stored.get(placeKey(testRunId, canonical)));
    }

    @Override
    public List<WorldlinePayment> allById(TestRunId testRunId, List<String> anyIdentifiers) {
      return anyIdentifiers.stream()
          .map(asked -> findById(testRunId, asked))
          .flatMap(Optional::stream)
          .distinct()
          .toList();
    }

    @Override
    public LedgerClaim applyMaintenance(
        WorldlinePayment payment, WorldlineMaintenance maintenance) {
      if (theObjectIsGone) {
        return LedgerClaim.OBJECT_GONE;
      }
      if (theClaimIsTaken) {
        return LedgerClaim.CLAIM_TAKEN;
      }
      if (!claimIdentifier(
          payment.testRunId(),
          maintenance.mintedIdentifier(),
          payment.paymentId(),
          maintenance.payIdSub(),
          maintenance.at())) {
        return LedgerClaim.CLAIM_TAKEN;
      }
      List<String> minted = new ArrayList<>(payment.maintenanceIds());
      minted.add(maintenance.mintedIdentifier());
      MonetaryOperation operation = maintenance.operation();
      Ledger ledger = operation == null ? payment.ledger() : payment.ledger().with(operation);
      stored.put(
          placeKey(payment.testRunId(), payment.paymentId()),
          new WorldlinePayment(
              payment.testRunId(),
              payment.paymentId(),
              minted,
              payment.accountId(),
              payment.merchantId(),
              payment.merchantReference(),
              payment.merchantCustomerId(),
              payment.token(),
              payment.hostedTokenizationId(),
              payment.cardNumber(),
              payment.expiryDate(),
              payment.amount(),
              payment.currencyCode(),
              payment.authorizationMode(),
              maintenance.nativeStatus(),
              maintenance.statusCategory(),
              maintenance.statusCode(),
              maintenance.authorizationClosed(),
              ledger,
              payment.origin(),
              payment.createdAt(),
              maintenance.at()));
      return LedgerClaim.APPLIED;
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String paymentId) {
      Long arrival = arrivedAt.get(placeKey(testRunId, paymentId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival.longValue(), paymentId));
    }

    @Override
    public List<ArrivalOf<WorldlinePayment>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      List<ArrivalOf<WorldlinePayment>> found = new ArrayList<>();
      for (Map.Entry<String, WorldlinePayment> held : stored.entrySet()) {
        WorldlinePayment payment = held.getValue();
        if (!payment.testRunId().equals(testRunId)) {
          continue;
        }
        long arrival = arrivedAt.getOrDefault(held.getKey(), Long.valueOf(0L)).longValue();
        if (arrival < ProviderObjectCursor.walkingBackFrom(after)) {
          found.add(new ArrivalOf<>(arrival, payment));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() > limit ? found.subList(0, limit) : found);
    }

    @Override
    public int clear(TestRunId testRunId) {
      List<String> doomed =
          stored.entrySet().stream()
              .filter(held -> held.getValue().testRunId().equals(testRunId))
              .map(Map.Entry::getKey)
              .toList();
      doomed.forEach(stored::remove);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      int size = stored.size();
      stored.clear();
      identifiers.clear();
      operations.clear();
      return size;
    }
  }

  static final class Keys implements WorldlineIdempotenceKeys {

    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final InMemoryKeyedExchanges exchanged = new InMemoryKeyedExchanges();

    private record Claim(String state, @Nullable WorldlineStoredAnswer answer, Instant claimedAt) {}

    Keys stillRunningUnder(TestRunId testRunId, String accountId, String key, Instant at) {
      claims.put(keyOf(testRunId, accountId, key), new Claim("RUNNING", null, at));
      return this;
    }

    private static String keyOf(TestRunId testRunId, String accountId, String key) {
      return testRunId.value() + '\u0000' + accountId + '\u0000' + key;
    }

    @Override
    public WorldlineIdempotenceClaim claim(
        TestRunId testRunId,
        String accountId,
        String key,
        String headerName,
        String operation,
        String fingerprint,
        Instant now) {
      String at = keyOf(testRunId, accountId, key);
      Claim held = claims.get(at);
      if (held == null) {
        claims.put(at, new Claim("RUNNING", null, now));
        return new WorldlineIdempotenceClaim.Fresh();
      }
      WorldlineStoredAnswer answer = held.answer();
      return answer == null
          ? new WorldlineIdempotenceClaim.HeldByARequestStillRunning()
          : new WorldlineIdempotenceClaim.Replayed(answer, held.claimedAt());
    }

    @Override
    public void completed(
        TestRunId testRunId, String accountId, String key, WorldlineStoredAnswer answer) {
      String at = keyOf(testRunId, accountId, key);
      Claim held = claims.get(at);
      claims.put(
          at, new Claim("COMPLETED", answer, held == null ? Instant.EPOCH : held.claimedAt()));
    }

    @Override
    public void released(TestRunId testRunId, String accountId, String key) {
      claims.remove(keyOf(testRunId, accountId, key));
    }

    @Override
    public String fingerprintOf(TestRunId testRunId, FingerprintedRequest request) {
      return exchanged.logOf(WorldlineKeyMechanisms.MECHANISMS).fingerprintOf(testRunId, request);
    }

    @Override
    public void observed(KeyedObservation observation) {
      exchanged.logOf(WorldlineKeyMechanisms.MECHANISMS).record(observation);
    }

    List<RecordedKeyedRequest> everythingItObserved() {
      return exchanged.recorded();
    }

    @Override
    public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
      return exchanged.exchanges(WorldlineKeyMechanisms.MECHANISMS, query);
    }

    @Override
    public int clear(TestRunId testRunId) {
      int size = claims.size();
      claims.clear();
      return size;
    }

    @Override
    public int clearAll() {
      int size = claims.size();
      claims.clear();
      exchanged.clearAll();
      return size;
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
      return pad(Integer.toHexString(next(length)), length, '0');
    }

    @Override
    public String digits(int length) {
      return "1" + pad(Integer.toString(next(length)), length - 1, '0');
    }

    private int next(int length) {
      return issued.merge(length, 1, Integer::sum);
    }

    int howManyOfLength(int length) {
      return issued.getOrDefault(length, 0);
    }

    private static String pad(String value, int length, char with) {
      return String.valueOf(with).repeat(Math.max(0, length - value.length())) + value;
    }
  }
}
