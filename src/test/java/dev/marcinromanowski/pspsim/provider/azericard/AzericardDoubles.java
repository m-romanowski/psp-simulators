package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.MergedIdentities;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fault.ScheduledDeliveries;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class AzericardDoubles {

  private AzericardDoubles() {}

  private static long arrivals;

  static synchronized long theNextArrival() {
    arrivals++;
    return arrivals;
  }

  static String placeKey(TestRunId testRunId, String id) {
    return testRunId.value() + '\u0000' + id;
  }

  static ScheduledDeliveries heldResponses() {
    return new ScheduledDeliveries((delay, delivery) -> () -> {});
  }

  static SimulatorClock clockAt(Instant instant) {
    return testRunId -> instant;
  }

  static AzericardTerminal terminal(TestRunId testRunId, String accountId, String terminalId) {
    return terminal(testRunId, accountId, terminalId, Map.of());
  }

  static AzericardTerminal terminal(
      TestRunId testRunId, String accountId, String terminalId, Map<String, String> configuration) {
    Map<String, String> declared = new LinkedHashMap<>(configuration);
    declared.put(AzericardTerminal.TERMINAL_PROPERTY, terminalId);
    declared.put(AzericardTerminal.MERCHANT_KEY_PROPERTY, AzericardKeyFixture.MERCHANT_PUBLIC);
    declared.put(AzericardTerminal.GATEWAY_KEY_PROPERTY, AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8);
    return AzericardTerminal.of(
        "accounts[0].configuration.", testRunId, new ConfiguredAccount(accountId, declared));
  }

  static final class Accounts implements AzericardTerminals, ProviderAccounts {

    private final List<AzericardTerminal> terminals = new ArrayList<>();
    private final Map<TestRunId, List<String>> identities = new LinkedHashMap<>();
    private final Map<TestRunId, List<WebhookTarget>> targets = new LinkedHashMap<>();

    Accounts with(AzericardTerminal terminal) {
      terminals.add(terminal);
      return this;
    }

    List<AzericardTerminal> all() {
      return List.copyOf(terminals);
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.copyOf(identities.getOrDefault(testRunId, List.of()));
    }

    @Override
    public void merge(TestRunId testRunId, List<AzericardTerminal> configured) {
      for (AzericardTerminal terminal : configured) {
        terminals.removeIf(
            held ->
                held.testRunId().equals(testRunId)
                    && held.accountId().equals(terminal.accountId()));
        terminals.add(terminal);
      }
    }

    @Override
    public Optional<AzericardTerminal> ofTheRunHolding(TestRunId testRunId, String terminalId) {
      return terminals.stream()
          .filter(terminal -> terminal.testRunId().equals(testRunId))
          .filter(terminal -> terminalId.equals(terminal.terminalId()))
          .findFirst();
    }

    @Override
    public void remove(TestRunId testRunId, String accountId) {
      terminals.removeIf(
          terminal ->
              terminal.testRunId().equals(testRunId) && terminal.accountId().equals(accountId));
    }

    void forget(TestRunId testRunId) {
      terminals.removeIf(terminal -> terminal.testRunId().equals(testRunId));
    }

    @Override
    public List<AzericardTerminal> byTerminalId(String terminalId) {
      return terminals.stream()
          .filter(terminal -> terminalId.equals(terminal.terminalId()))
          .toList();
    }

    @Override
    public Optional<AzericardTerminal> find(TestRunId testRunId, String accountId) {
      return terminals.stream()
          .filter(terminal -> terminal.testRunId().equals(testRunId))
          .filter(terminal -> accountId.equals(terminal.accountId()))
          .findFirst();
    }

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      if (!MergedIdentities.outOf(identities, targets, testRunId, accountId)) {
        return false;
      }
      plan.remove();
      return true;
    }

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      plan.store();
      MergedIdentities.into(identities, targets, testRunId, accountIds, webhookTargets);
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return List.copyOf(targets.getOrDefault(testRunId, List.of()));
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

  static final class Payments implements AzericardPayments {

    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final List<AzericardPayment> stored = new ArrayList<>();
    private final Set<String> claimedIntRefs = new LinkedHashSet<>();
    private final Set<String> intRefsClaimedElsewhere = new LinkedHashSet<>();
    private final Map<String, String> claimedTraces = new LinkedHashMap<>();
    private final List<ObservedUnderOneKey> observed = new ArrayList<>();
    private int insertsToReject;
    private int appendsToRace;
    private int appendsToLose;

    Payments withTheIntRefsAlreadyClaimed(String... intRefs) {
      intRefsClaimedElsewhere.addAll(List.of(intRefs));
      return this;
    }

    Payments rejectingTheNextInsert() {
      this.insertsToReject = 1;
      return this;
    }

    Payments racingTheNextAppend() {
      this.appendsToRace = 1;
      return this;
    }

    Payments losingThePaymentOnTheNextAppend() {
      this.appendsToLose = 1;
      return this;
    }

    @Override
    public boolean claimIntRef(TestRunId testRunId, String intRef) {
      return !intRefsClaimedElsewhere.contains(intRef)
          && claimedIntRefs.add(keyOf(testRunId, intRef));
    }

    private Runnable beforeTheNextTraceClaim = () -> {};

    Payments runningBeforeTheNextTraceClaim(Runnable interleaved) {
      this.beforeTheNextTraceClaim = interleaved;
      return this;
    }

    @Override
    public boolean claimOrderTrace(
        TestRunId testRunId, String terminalId, LocalDate day, String trace) {
      Runnable interleaved = beforeTheNextTraceClaim;
      beforeTheNextTraceClaim = () -> {};
      interleaved.run();
      return claimedTraces.putIfAbsent(traceOf(testRunId, terminalId, day, trace), "") == null;
    }

    @Override
    public boolean holdOrderTrace(
        TestRunId testRunId, String terminalId, LocalDate day, String trace, String sessionId) {
      return claimedTraces.putIfAbsent(traceOf(testRunId, terminalId, day, trace), sessionId)
          == null;
    }

    @Override
    public Optional<String> theSessionHoldingOrderTrace(
        TestRunId testRunId, String terminalId, LocalDate day, String trace) {
      return Optional.ofNullable(claimedTraces.get(traceOf(testRunId, terminalId, day, trace)))
          .filter(holder -> !holder.isEmpty());
    }

    @Override
    public boolean takeOverOrderTrace(TestRunId testRunId, String sessionId) {
      Runnable interleaved = beforeTheNextTraceClaim;
      beforeTheNextTraceClaim = () -> {};
      interleaved.run();
      for (Map.Entry<String, String> held : claimedTraces.entrySet()) {
        if (held.getKey().startsWith(testRunId.value() + '\u0000')
            && held.getValue().equals(sessionId)) {
          held.setValue("");
          return true;
        }
      }
      return false;
    }

    boolean isClaimedByAPayment(
        TestRunId testRunId, String terminalId, LocalDate day, String trace) {
      return "".equals(claimedTraces.get(traceOf(testRunId, terminalId, day, trace)));
    }

    private static String traceOf(
        TestRunId testRunId, String terminalId, LocalDate day, String trace) {
      return keyOf(testRunId, terminalId + " " + day + " " + trace);
    }

    private static String keyOf(TestRunId testRunId, String value) {
      return testRunId.value() + '\u0000' + value;
    }

    @Override
    public boolean insertIfAbsent(AzericardPayment payment) {
      if (insertsToReject > 0) {
        insertsToReject--;
        return false;
      }
      if (findByIntRef(payment.testRunId(), payment.intRef()).isPresent()) {
        return false;
      }
      claimIntRef(payment.testRunId(), payment.intRef());
      arrivals.put(placeKey(payment.testRunId(), payment.intRef()), theNextArrival());
      stored.add(payment);
      return true;
    }

    @Override
    public Optional<AzericardPayment> findByIntRef(TestRunId testRunId, String intRef) {
      return stored.stream()
          .filter(p -> p.testRunId().equals(testRunId) && p.intRef().equals(intRef))
          .findFirst();
    }

    @Override
    public List<AzericardPayment> byOrder(TestRunId testRunId, String terminalId, String order) {
      return stored.stream()
          .filter(p -> p.testRunId().equals(testRunId))
          .filter(p -> p.terminalId().equals(terminalId) && p.order().equals(order))
          .toList();
    }

    @Override
    public List<AzericardPayment> allByIntRef(TestRunId testRunId, List<String> intRefs) {
      return stored.stream()
          .filter(p -> p.testRunId().equals(testRunId))
          .filter(p -> intRefs.contains(p.intRef()))
          .toList();
    }

    @Override
    public Optional<AzericardPayment> findAddressed(
        TestRunId testRunId, String terminalId, String order, String rrn, String intRef) {
      return findByIntRef(testRunId, intRef)
          .filter(
              p ->
                  p.terminalId().equals(terminalId)
                      && p.order().equals(order)
                      && p.rrn().equals(rrn));
    }

    @Override
    public boolean hasSeen(TestRunId testRunId, String accountId, String keyName, String keyValue) {
      return observed.stream()
          .anyMatch(row -> row.isUnder(testRunId, accountId, keyName, keyValue));
    }

    @Override
    public void observed(AzericardObservedRequest request) {
      observed.add(new ObservedUnderOneKey(theNextArrival(), sequenceOf(request), request));
    }

    private int sequenceOf(AzericardObservedRequest request) {
      int under = 0;
      for (ObservedUnderOneKey row : observed) {
        if (row.isUnder(
            request.testRunId(), request.accountId(), request.keyName(), request.keyValue())) {
          under++;
        }
      }
      return under;
    }

    @Override
    public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
      List<RecordedKeyedRequest> rows = new ArrayList<>();
      for (ObservedUnderOneKey row : observed) {
        if (row.request().testRunId().equals(query.testRunId())) {
          rows.add(row.asRecorded());
        }
      }
      return KeyedExchanges.pageOf(AzericardKeyMechanisms.MECHANISMS, query.testRunId(), rows);
    }

    @Override
    public LedgerClaim reverseTheHold(AzericardPayment payment, Instant at) {
      if (appendsToLose > 0) {
        appendsToLose--;
        return LedgerClaim.OBJECT_GONE;
      }
      Optional<AzericardPayment> held = findByIntRef(payment.testRunId(), payment.intRef());
      if (held.isEmpty()) {
        return LedgerClaim.OBJECT_GONE;
      }
      AzericardPayment current = held.get();
      if (appendsToRace > 0 || current.holdReversedAt() != null) {
        appendsToRace = Math.max(0, appendsToRace - 1);
        return LedgerClaim.CLAIM_TAKEN;
      }
      stored.set(stored.indexOf(current), released(current, at));
      return LedgerClaim.APPLIED;
    }

    private static AzericardPayment released(AzericardPayment payment, Instant at) {
      return new AzericardPayment(
          payment.testRunId(),
          payment.intRef(),
          payment.terminalId(),
          payment.merchantId(),
          payment.order(),
          payment.transactionType(),
          payment.amount(),
          payment.currency(),
          payment.approval(),
          payment.rrn(),
          payment.action(),
          payment.responseCode(),
          payment.ledger(),
          at,
          payment.backref(),
          payment.email(),
          payment.token(),
          payment.cardLast4(),
          payment.extNetRef(),
          payment.origin(),
          payment.createdAt(),
          at);
    }

    @Override
    public LedgerClaim append(AzericardPayment payment, MonetaryOperation operation, Instant at) {
      if (appendsToLose > 0) {
        appendsToLose--;
        return LedgerClaim.OBJECT_GONE;
      }
      Optional<AzericardPayment> held = findByIntRef(payment.testRunId(), payment.intRef());
      if (held.isEmpty()) {
        return LedgerClaim.OBJECT_GONE;
      }
      AzericardPayment current = held.get();
      if (appendsToRace > 0) {
        appendsToRace--;
        return LedgerClaim.CLAIM_TAKEN;
      }
      if (current.ledger().nextSequence() != operation.sequence()) {
        return LedgerClaim.CLAIM_TAKEN;
      }
      stored.set(stored.indexOf(current), withTheOperation(current, operation, at));
      return LedgerClaim.APPLIED;
    }

    private static AzericardPayment withTheOperation(
        AzericardPayment payment, MonetaryOperation operation, Instant at) {
      return new AzericardPayment(
          payment.testRunId(),
          payment.intRef(),
          payment.terminalId(),
          payment.merchantId(),
          payment.order(),
          payment.transactionType(),
          payment.amount(),
          payment.currency(),
          payment.approval(),
          payment.rrn(),
          payment.action(),
          payment.responseCode(),
          payment.ledger().with(operation),
          payment.holdReversedAt(),
          payment.backref(),
          payment.email(),
          payment.token(),
          payment.cardLast4(),
          payment.extNetRef(),
          payment.origin(),
          payment.createdAt(),
          at);
    }

    @Override
    public int clear(TestRunId testRunId) {
      observed.removeIf(row -> row.request().testRunId().equals(testRunId));
      claimedIntRefs.removeIf(claim -> claim.startsWith(testRunId.value() + '\u0000'));
      claimedTraces.keySet().removeIf(claim -> claim.startsWith(testRunId.value() + '\u0000'));
      List<AzericardPayment> doomed =
          stored.stream().filter(p -> p.testRunId().equals(testRunId)).toList();
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      observed.clear();
      claimedIntRefs.clear();
      claimedTraces.clear();
      int size = stored.size();
      stored.clear();
      return size;
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String intRef) {
      Long arrival = arrivals.get(placeKey(testRunId, intRef));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival, intRef));
    }

    @Override
    public List<ArrivalOf<AzericardPayment>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<AzericardPayment>> found = new ArrayList<>();
      for (AzericardPayment payment : stored) {
        Long arrival = arrivals.get(placeKey(payment.testRunId(), payment.intRef()));
        if (arrival != null && payment.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, payment));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    List<AzericardPayment> all() {
      return List.copyOf(stored);
    }
  }

  record ObservedUnderOneKey(long arrival, int sequence, AzericardObservedRequest request) {

    boolean isUnder(TestRunId testRunId, String accountId, String keyName, String keyValue) {
      return request.testRunId().equals(testRunId)
          && request.accountId().equals(accountId)
          && request.keyName().equals(keyName)
          && request.keyValue().equals(keyValue);
    }

    RecordedKeyedRequest asRecorded() {
      return new RecordedKeyedRequest(
          request.accountId(),
          request.keyName(),
          request.keyValue(),
          arrival,
          sequence,
          request.verdict(),
          request.fingerprint(),
          request.operation(),
          request.httpStatus(),
          request.contentType(),
          request.body(),
          request.providerObjectId(),
          request.stateChanged(),
          null,
          request.at());
    }
  }

  static final class StoredCards implements AzericardStoredCards {

    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final List<AzericardStoredCard> stored = new ArrayList<>();
    private final Set<String> tokensClaimedElsewhere = new LinkedHashSet<>();

    StoredCards withTheTokensAlreadyClaimed(String... tokens) {
      tokensClaimedElsewhere.addAll(List.of(tokens));
      return this;
    }

    StoredCards holding(AzericardStoredCard card) {
      stored.add(card);
      return this;
    }

    @Override
    public int forgetSeeded(TestRunId testRunId, String fixtureId) {
      List<AzericardStoredCard> doomed = new ArrayList<>();
      for (AzericardStoredCard card : stored) {
        if (card.testRunId().equals(testRunId) && fixtureId.equals(card.fixtureId())) {
          doomed.add(card);
        }
      }
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public boolean claim(AzericardStoredCard card) {
      if (tokensClaimedElsewhere.contains(card.token())
          || findByToken(card.testRunId(), card.token()).isPresent()) {
        return false;
      }
      arrivals.put(placeKey(card.testRunId(), card.token()), theNextArrival());
      stored.add(card);
      return true;
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String token) {
      Long arrival = arrivals.get(placeKey(testRunId, token));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival, token));
    }

    @Override
    public List<ArrivalOf<AzericardStoredCard>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<AzericardStoredCard>> found = new ArrayList<>();
      for (AzericardStoredCard card : stored) {
        Long arrival = arrivals.get(placeKey(card.testRunId(), card.token()));
        if (arrival != null && card.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, card));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    @Override
    public Optional<AzericardStoredCard> findByToken(TestRunId testRunId, String token) {
      return stored.stream()
          .filter(card -> card.testRunId().equals(testRunId) && card.token().equals(token))
          .findFirst();
    }

    @Override
    public List<AzericardStoredCard> allByToken(TestRunId testRunId, List<String> tokens) {
      return stored.stream()
          .filter(card -> card.testRunId().equals(testRunId))
          .filter(card -> tokens.contains(card.token()))
          .toList();
    }

    @Override
    public int clear(TestRunId testRunId) {
      List<AzericardStoredCard> doomed =
          stored.stream().filter(card -> card.testRunId().equals(testRunId)).toList();
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      int size = stored.size();
      stored.clear();
      return size;
    }

    List<AzericardStoredCard> all() {
      return List.copyOf(stored);
    }
  }

  static AzericardPayerCheckout checkoutOf(
      Payments payments,
      StoredCards cards,
      Accounts accounts,
      ScenarioEngine scenarios,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids) {
    return checkoutOf(new PayerSessions(), payments, cards, accounts, scenarios, audit, clock, ids);
  }

  static AzericardPayerCheckout checkoutOf(
      PayerSessions sessions,
      Payments payments,
      StoredCards cards,
      Accounts accounts,
      ScenarioEngine scenarios,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids) {
    return checkoutOf(
        sessions,
        payments,
        accounts,
        new Authorize(payments, cards, ids, scenarios),
        callbacksOf(payments, accounts, audit, clock, ids),
        audit,
        clock,
        ids);
  }

  static AzericardPayerCheckout checkoutOf(
      PayerSessions sessions,
      Payments payments,
      Accounts accounts,
      Authorize authorize,
      AzericardCallbacks callbacks,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids) {
    return new AzericardPayerCheckout(
        sessions, payments, accounts, authorize, callbacks, audit, clock, ids, sessions.units());
  }

  static AzericardCallbacks callbacksOf(
      Payments payments,
      Accounts accounts,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids) {
    return new AzericardCallbacks(
        payments, accounts, accounts, dispatcherOf(audit, clock), clock, ids);
  }

  static WebhookDispatcher dispatcherOf(AuditTrail audit, SimulatorClock clock) {
    return new WebhookDispatcher(
        new InMemoryWebhookDeliveries(),
        (url, payload) -> new MerchantResponse.Answered(200, ""),
        audit,
        clock,
        new CountingIds(),
        new RetryPlan(List.of(Duration.ZERO)),
        new DeliveryShaping(new InMemoryScenarioRules()),
        List.of(new AzericardCallbackAcknowledgement()),
        List.of(),
        OutsideAnyUnitOfWork.RUNS_AT_ONCE);
  }

  static final class PayerSessions implements AzericardPayerSessions {

    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final Map<String, AzericardPayerSession> held = new LinkedHashMap<>();
    private final Deque<UnitFrame> frames = new ArrayDeque<>();
    private final Units units = new Units(this);
    private Runnable beforeTheNextLock = () -> {};

    Units units() {
      return units;
    }

    PayerSessions runningBeforeTheNextLock(Runnable interleaved) {
      this.beforeTheNextLock = interleaved;
      return this;
    }

    void aUnitBegan() {
      frames.push(new UnitFrame(new ArrayList<>(), new LinkedHashSet<>()));
    }

    void theUnitEnded(boolean kept) {
      UnitFrame ended = frames.pop();
      if (!kept) {
        ended.opened().forEach(held::remove);
      }
    }

    private boolean lockedByAnotherUnit(String sessionId) {
      return frames.stream().anyMatch(frame -> frame.locked().contains(sessionId));
    }

    @Override
    public boolean open(AzericardPayerSession session) {
      if (held.containsKey(session.sessionId())) {
        return false;
      }
      arrivals.put(session.sessionId(), theNextArrival());
      held.put(session.sessionId(), session);
      UnitFrame frame = frames.peek();
      if (frame != null) {
        frame.opened().add(session.sessionId());
      }
      return true;
    }

    @Override
    public Optional<AzericardPayerSession> find(String sessionId) {
      return Optional.ofNullable(held.get(sessionId));
    }

    @Override
    public Optional<AzericardPayerSession> find(TestRunId testRunId, String sessionId) {
      return find(sessionId).filter(session -> session.testRunId().equals(testRunId));
    }

    @Override
    public List<AzericardPayerSession> allById(TestRunId testRunId, List<String> sessionIds) {
      return held.values().stream()
          .filter(session -> session.testRunId().equals(testRunId))
          .filter(session -> sessionIds.contains(session.sessionId()))
          .toList();
    }

    @Override
    public Optional<AzericardPayerSession> lockedWhileOpen(TestRunId testRunId, String sessionId) {
      Runnable interleaved = beforeTheNextLock;
      beforeTheNextLock = () -> {};
      interleaved.run();
      Optional<AzericardPayerSession> open =
          find(testRunId, sessionId)
              .filter(session -> !session.isSettled() && !lockedByAnotherUnit(sessionId));
      UnitFrame frame = frames.peek();
      if (open.isPresent() && frame != null) {
        frame.locked().add(sessionId);
      }
      return open;
    }

    @Override
    public void settle(
        TestRunId testRunId,
        String sessionId,
        PayerOutcome outcome,
        @Nullable String intRef,
        Instant at) {
      find(testRunId, sessionId)
          .filter(session -> !session.isSettled())
          .ifPresent(session -> held.put(sessionId, session.settledAs(outcome, intRef, at)));
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String sessionId) {
      return find(testRunId, sessionId)
          .map(session -> new ProviderObjectCursor(arrivals.get(sessionId), sessionId));
    }

    @Override
    public List<ArrivalOf<AzericardPayerSession>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<AzericardPayerSession>> found = new ArrayList<>();
      for (AzericardPayerSession session : held.values()) {
        long arrival = arrivals.get(session.sessionId());
        if (session.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, session));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    @Override
    public int clear(TestRunId testRunId) {
      List<String> doomed =
          held.values().stream()
              .filter(session -> session.testRunId().equals(testRunId))
              .map(AzericardPayerSession::sessionId)
              .toList();
      doomed.forEach(held::remove);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      int size = held.size();
      held.clear();
      return size;
    }

    List<AzericardPayerSession> all() {
      return List.copyOf(held.values());
    }
  }

  record UnitFrame(List<String> opened, Set<String> locked) {}

  static final class Units implements UnitOfWork {

    private final List<RecordedUnitsOfWork.Ending> endings = new ArrayList<>();
    private final PayerSessions sessions;

    Units(PayerSessions sessions) {
      this.sessions = sessions;
    }

    @Override
    public <T> T keepingOnly(Predicate<? super T> kept, Supplier<T> work) {
      sessions.aUnitBegan();
      boolean keeps = false;
      RecordedUnitsOfWork.Ending ending = RecordedUnitsOfWork.Ending.THREW;
      try {
        T done = work.get();
        keeps = kept.test(done);
        ending = keeps ? RecordedUnitsOfWork.Ending.KEPT : RecordedUnitsOfWork.Ending.DISCARDED;
        return done;
      } finally {
        endings.add(ending);
        sessions.theUnitEnded(keeps);
      }
    }

    List<RecordedUnitsOfWork.Ending> endings() {
      return List.copyOf(endings);
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

    private static String pad(String value, int length, char with) {
      return String.valueOf(with).repeat(Math.max(0, length - value.length())) + value;
    }
  }
}
