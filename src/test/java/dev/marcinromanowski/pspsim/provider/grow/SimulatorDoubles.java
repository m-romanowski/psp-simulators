package dev.marcinromanowski.pspsim.provider.grow;

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
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.idempotency.InMemoryKeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ConfiguredAccount;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
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
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

final class SimulatorDoubles {

  private static final AtomicLong ARRIVALS = new AtomicLong();

  private SimulatorDoubles() {}

  private static long arrivedAt(Map<String, Long> arrivals, TestRunId testRunId, String objectId) {
    return arrivals.computeIfAbsent(
        testRunId.value() + '\u0000' + objectId, key -> ARRIVALS.incrementAndGet());
  }

  static ScheduledDeliveries heldResponses() {
    return new ScheduledDeliveries((delay, delivery) -> () -> {});
  }

  static GrowAccount account(
      TestRunId testRunId, String accountId, String userId, String pageCode) {
    return account(testRunId, accountId, userId, pageCode, Map.of());
  }

  static GrowAccount account(
      TestRunId testRunId,
      String accountId,
      String userId,
      String pageCode,
      Map<String, String> configuration) {
    Map<String, String> declared = new LinkedHashMap<>(configuration);
    declared.put(GrowAccount.USER_ID, userId);
    declared.put(GrowAccount.PAGE_CODE, pageCode);
    return GrowAccount.of(
        "accounts[0].configuration.", testRunId, new ConfiguredAccount(accountId, declared));
  }

  static final class Accounts implements GrowAccounts, ProviderAccounts {

    private final List<GrowAccount> accounts = new ArrayList<>();
    private final Map<TestRunId, List<String>> identities = new LinkedHashMap<>();
    private final Map<TestRunId, List<WebhookTarget>> targets = new LinkedHashMap<>();

    Accounts with(GrowAccount account) {
      accounts.add(account);
      return this;
    }

    Accounts reconfigured(GrowAccount account) {
      accounts.removeIf(
          held ->
              held.testRunId().equals(account.testRunId())
                  && held.accountId().equals(account.accountId()));
      accounts.add(account);
      return this;
    }

    Accounts withTarget(TestRunId testRunId, WebhookTarget target) {
      targets.computeIfAbsent(testRunId, run -> new ArrayList<>()).add(target);
      return this;
    }

    List<GrowAccount> all() {
      return List.copyOf(accounts);
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.copyOf(identities.getOrDefault(testRunId, List.of()));
    }

    @Override
    public void merge(TestRunId testRunId, List<GrowAccount> configured) {
      for (GrowAccount account : configured) {
        accounts.removeIf(
            held ->
                held.testRunId().equals(testRunId) && held.accountId().equals(account.accountId()));
        accounts.add(account);
      }
    }

    @Override
    public List<GrowAccount> ofTheRunHoldingEither(
        TestRunId testRunId, String userId, String pageCode) {
      return accounts.stream()
          .filter(account -> account.testRunId().equals(testRunId))
          .filter(account -> userId.equals(account.userId()) || pageCode.equals(account.pageCode()))
          .toList();
    }

    @Override
    public void remove(TestRunId testRunId, String accountId) {
      accounts.removeIf(
          account ->
              account.testRunId().equals(testRunId) && account.accountId().equals(accountId));
    }

    void forget(TestRunId testRunId) {
      accounts.removeIf(account -> account.testRunId().equals(testRunId));
    }

    void forgetTargets(TestRunId testRunId) {
      targets.remove(testRunId);
    }

    @Override
    public List<GrowAccount> byUserIdAndPageCode(String userId, String pageCode) {
      return accounts.stream()
          .filter(account -> userId.equals(account.userId()))
          .filter(account -> pageCode.equals(account.pageCode()))
          .toList();
    }

    @Override
    public List<GrowAccount> byUserId(String userId) {
      return accounts.stream().filter(account -> userId.equals(account.userId())).toList();
    }

    @Override
    public List<GrowAccount> byPageCode(String pageCode) {
      return accounts.stream().filter(account -> pageCode.equals(account.pageCode())).toList();
    }

    @Override
    public List<GrowAccount> byEitherCredential(
        @Nullable String userId, @Nullable String pageCode) {
      if (userId == null && pageCode == null) {
        return List.of();
      }
      return accounts.stream()
          .filter(account -> userId == null || userId.equals(account.userId()))
          .filter(account -> pageCode == null || pageCode.equals(account.pageCode()))
          .toList();
    }

    @Override
    public Optional<GrowAccount> find(TestRunId testRunId, String accountId) {
      return accounts.stream()
          .filter(account -> account.testRunId().equals(testRunId))
          .filter(account -> account.accountId().equals(accountId))
          .findFirst();
    }

    @Override
    public List<GrowAccount> allById(TestRunId testRunId, List<String> accountIds) {
      return accounts.stream()
          .filter(account -> account.testRunId().equals(testRunId))
          .filter(account -> accountIds.contains(account.accountId()))
          .toList();
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

  static final class Processes implements GrowPaymentProcesses, Undoable {

    private final List<GrowPaymentProcess> stored = new ArrayList<>();
    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final Set<String> claimedElsewhere = new LinkedHashSet<>();
    private final Set<String> claimed = new LinkedHashSet<>();
    private int idsToReject;
    private Runnable whileReachingAnOutcome = () -> {};

    Processes rejectingTheNext(int count) {
      this.idsToReject = count;
      return this;
    }

    Processes withTheProcessIdsAlreadyClaimed(String... processIds) {
      claimedElsewhere.addAll(List.of(processIds));
      return this;
    }

    @Override
    public boolean claimProcessId(TestRunId testRunId, String processId) {
      return !claimedElsewhere.contains(processId) && claimed.add(claimOf(testRunId, processId));
    }

    private static String claimOf(TestRunId testRunId, String processId) {
      return testRunId.value() + '\u0000' + processId;
    }

    @Override
    public boolean insertIfAbsent(GrowPaymentProcess process) {
      if (idsToReject > 0) {
        idsToReject--;
        return false;
      }
      boolean taken =
          stored.stream()
              .anyMatch(
                  p ->
                      p.testRunId().equals(process.testRunId())
                          && p.processId().equals(process.processId()));
      if (taken) {
        return false;
      }
      arrivedAt(arrivals, process.testRunId(), process.processId());
      stored.add(process);
      return true;
    }

    @Override
    public Optional<GrowPaymentProcess> find(
        TestRunId testRunId, String processId, String processToken) {
      return stored.stream()
          .filter(
              p ->
                  p.testRunId().equals(testRunId)
                      && p.processId().equals(processId)
                      && p.processToken().equals(processToken))
          .findFirst();
    }

    @Override
    public Optional<GrowPaymentProcess> findByProcessId(TestRunId testRunId, String processId) {
      return stored.stream()
          .filter(p -> p.testRunId().equals(testRunId) && p.processId().equals(processId))
          .findFirst();
    }

    @Override
    public List<GrowPaymentProcess> allByProcessId(TestRunId testRunId, List<String> processIds) {
      return stored.stream()
          .filter(p -> p.testRunId().equals(testRunId) && processIds.contains(p.processId()))
          .toList();
    }

    @Override
    public Optional<GrowPaymentProcess> findByHostedLink(String hostedLink) {
      return stored.stream().filter(p -> p.hostedLink().equals(hostedLink)).findFirst();
    }

    @Override
    public Optional<GrowPaymentProcess> findByCardToken(
        TestRunId testRunId, String merchantId, String cardToken) {
      return stored.stream()
          .filter(p -> p.testRunId().equals(testRunId) && p.merchantId().equals(merchantId))
          .filter(p -> p.outcome() != null && cardToken.equals(p.outcome().cardToken()))
          .findFirst();
    }

    @Override
    public List<GrowPaymentProcess> theProcessesThatSavedACard(
        TestRunId testRunId, String merchantId) {
      return stored.stream()
          .filter(p -> p.testRunId().equals(testRunId) && p.merchantId().equals(merchantId))
          .filter(p -> p.outcome() != null && p.outcome().cardToken() != null)
          .toList();
    }

    @Override
    public Optional<GrowPaymentProcess> findByTransaction(
        TestRunId testRunId, String merchantId, String transactionId, String transactionToken) {
      return stored.stream()
          .filter(p -> p.testRunId().equals(testRunId) && p.merchantId().equals(merchantId))
          .filter(
              p ->
                  p.outcome() != null
                      && transactionId.equals(p.outcome().transactionId())
                      && transactionToken.equals(p.outcome().transactionToken()))
          .findFirst();
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String processId) {
      return findByProcessId(testRunId, processId)
          .map(p -> new ProviderObjectCursor(arrivedAt(arrivals, testRunId, processId), processId));
    }

    @Override
    public List<ArrivalOf<GrowPaymentProcess>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<GrowPaymentProcess>> found = new ArrayList<>();
      for (GrowPaymentProcess process : stored) {
        long arrival = arrivedAt(arrivals, process.testRunId(), process.processId());
        if (process.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, process));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    Processes whileReachingAnOutcome(Runnable action) {
      this.whileReachingAnOutcome = action;
      return this;
    }

    @Override
    public boolean reachOutcome(GrowPaymentProcess process) {
      Runnable once = whileReachingAnOutcome;
      whileReachingAnOutcome = () -> {};
      once.run();
      return write(process);
    }

    private synchronized boolean write(GrowPaymentProcess process) {
      for (int i = 0; i < stored.size(); i++) {
        GrowPaymentProcess existing = stored.get(i);
        if (existing.testRunId().equals(process.testRunId())
            && existing.processId().equals(process.processId())) {
          if (existing.outcome() != null) {
            return false;
          }
          stored.set(i, process);
          return true;
        }
      }
      return false;
    }

    @Override
    public Runnable snapshot() {
      List<GrowPaymentProcess> contents = List.copyOf(stored);
      return () -> {
        stored.clear();
        stored.addAll(contents);
      };
    }

    @Override
    public int clear(TestRunId testRunId) {
      claimed.removeIf(claim -> claim.startsWith(testRunId.value() + '\u0000'));
      List<GrowPaymentProcess> doomed =
          stored.stream().filter(p -> p.testRunId().equals(testRunId)).toList();
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      claimed.clear();
      int size = stored.size();
      stored.clear();
      return size;
    }

    List<GrowPaymentProcess> all() {
      return List.copyOf(stored);
    }
  }

  static final class Transactions implements GrowTransactions, Undoable {

    private final List<GrowTransaction> stored = new ArrayList<>();
    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final List<GrowStoredCardCharge> submissions = new ArrayList<>();
    private final InMemoryKeyedExchanges exchanged = new InMemoryKeyedExchanges();
    private final Set<String> claimed = new LinkedHashSet<>();
    private final Set<String> claimedElsewhere = new LinkedHashSet<>();
    private final Set<String> claimedByAProcess = new LinkedHashSet<>();
    private int idsToReject;
    private Runnable whileAppending = () -> {};
    private Runnable whileRefunding = () -> {};

    Transactions rejectingTheNext(int count) {
      this.idsToReject = count;
      return this;
    }

    Transactions holding(GrowTransaction transaction) {
      arrivedAt(arrivals, transaction.testRunId(), transaction.transactionId());
      stored.add(transaction);
      return this;
    }

    Transactions withTheTransactionIdsAlreadyClaimed(String... transactionIds) {
      claimedElsewhere.addAll(List.of(transactionIds));
      return this;
    }

    Transactions withTheIdsClaimedByAPaymentProcess(String... objectIds) {
      claimedByAProcess.addAll(List.of(objectIds));
      return this;
    }

    @Override
    public boolean claimTransactionId(TestRunId testRunId, String transactionId) {
      return !claimedElsewhere.contains(transactionId)
          && !claimedByAProcess.contains(transactionId)
          && claimed.add(testRunId.value() + '\u0000' + transactionId);
    }

    @Override
    public Charged charge(GrowTransaction transaction, GrowStoredCardCharge submission) {
      Runnable undo = snapshot();
      Submitted submitted = submit(submission);
      if (submitted != Submitted.APPLIED) {
        return submitted == Submitted.GROUP_ALREADY_CHARGED
            ? Charged.GROUP_ALREADY_CHARGED
            : Charged.UNIQUE_IDENTIFIER_TAKEN;
      }
      if (!insertIfAbsent(transaction)) {
        undo.run();
        return Charged.TRANSACTION_ID_TAKEN;
      }
      return Charged.APPLIED;
    }

    @Override
    public Submitted submit(GrowStoredCardCharge submission) {
      String group = submission.groupIdentifier();
      if (group != null
          && submissions.stream().anyMatch(held -> charged(held, submission, group))) {
        return Submitted.GROUP_ALREADY_CHARGED;
      }
      if (submissions.stream().anyMatch(held -> sameIdentifier(held, submission))) {
        return Submitted.UNIQUE_IDENTIFIER_TAKEN;
      }
      submissions.add(submission);
      return Submitted.APPLIED;
    }

    @Override
    public void observed(KeyedObservation observation) {
      exchanged.logOf(GrowKeyMechanisms.MECHANISMS).record(observation);
    }

    @Override
    public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
      return exchanged.exchanges(GrowKeyMechanisms.MECHANISMS, query);
    }

    private static boolean charged(
        GrowStoredCardCharge held, GrowStoredCardCharge submission, String group) {
      return held.testRunId().equals(submission.testRunId())
          && held.merchantId().equals(submission.merchantId())
          && group.equals(held.groupIdentifier())
          && held.transactionId() != null;
    }

    private static boolean sameIdentifier(
        GrowStoredCardCharge held, GrowStoredCardCharge submission) {
      return held.testRunId().equals(submission.testRunId())
          && held.merchantId().equals(submission.merchantId())
          && held.uniqueIdentifier().equals(submission.uniqueIdentifier());
    }

    @Override
    public Optional<GrowStoredCardCharge> submissionOf(TestRunId testRunId, String transactionId) {
      return submissions.stream()
          .filter(
              held ->
                  held.testRunId().equals(testRunId) && transactionId.equals(held.transactionId()))
          .findFirst();
    }

    @Override
    public Optional<GrowStoredCardCharge> submissionUnder(
        TestRunId testRunId, String merchantId, String uniqueIdentifier) {
      return submissions.stream()
          .filter(
              held ->
                  held.testRunId().equals(testRunId)
                      && held.merchantId().equals(merchantId)
                      && held.uniqueIdentifier().equals(uniqueIdentifier))
          .findFirst();
    }

    List<GrowStoredCardCharge> submitted() {
      return List.copyOf(submissions);
    }

    Transactions whileAppending(Runnable action) {
      this.whileAppending = action;
      return this;
    }

    @Override
    public boolean insertIfAbsent(GrowTransaction transaction) {
      if (idsToReject > 0) {
        idsToReject--;
        return false;
      }
      if (claimedByAProcess.contains(transaction.transactionId())) {
        return false;
      }
      if (find(transaction.testRunId(), transaction.transactionId()).isPresent()) {
        return false;
      }
      claimTransactionId(transaction.testRunId(), transaction.transactionId());
      arrivedAt(arrivals, transaction.testRunId(), transaction.transactionId());
      stored.add(transaction);
      return true;
    }

    @Override
    public Optional<GrowTransaction> find(
        TestRunId testRunId, String merchantId, String transactionId, String transactionToken) {
      return find(testRunId, transactionId)
          .filter(t -> t.merchantId().equals(merchantId))
          .filter(t -> transactionToken.equals(t.transactionToken()));
    }

    @Override
    public Optional<GrowTransaction> findByTransactionId(
        TestRunId testRunId, String transactionId) {
      return find(testRunId, transactionId);
    }

    @Override
    public List<GrowTransaction> allByTransactionId(
        TestRunId testRunId, List<String> transactionIds) {
      return stored.stream()
          .filter(t -> t.testRunId().equals(testRunId))
          .filter(t -> transactionIds.contains(t.transactionId()))
          .toList();
    }

    private Optional<GrowTransaction> find(TestRunId testRunId, String transactionId) {
      return stored.stream()
          .filter(t -> t.testRunId().equals(testRunId) && t.transactionId().equals(transactionId))
          .findFirst();
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String transactionId) {
      return find(testRunId, transactionId)
          .map(t -> new ProviderObjectCursor(arrivalOf(t), t.transactionId()));
    }

    @Override
    public List<ArrivalOf<GrowTransaction>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<GrowTransaction>> found = new ArrayList<>();
      for (GrowTransaction transaction : stored) {
        long arrival = arrivalOf(transaction);
        if (transaction.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, transaction));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    private long arrivalOf(GrowTransaction transaction) {
      return arrivedAt(arrivals, transaction.testRunId(), transaction.transactionId());
    }

    @Override
    public LedgerClaim append(GrowTransaction transaction, MonetaryOperation operation) {
      Runnable once = whileAppending;
      whileAppending = () -> {};
      once.run();
      return write(transaction, operation);
    }

    private synchronized LedgerClaim write(
        GrowTransaction transaction, MonetaryOperation operation) {
      for (int i = 0; i < stored.size(); i++) {
        GrowTransaction existing = stored.get(i);
        if (existing.testRunId().equals(transaction.testRunId())
            && existing.transactionId().equals(transaction.transactionId())) {
          if (existing.ledger().nextSequence() != operation.sequence()) {
            return LedgerClaim.CLAIM_TAKEN;
          }
          stored.set(i, transaction);
          return LedgerClaim.APPLIED;
        }
      }
      return LedgerClaim.OBJECT_GONE;
    }

    Transactions whileRefunding(Runnable action) {
      this.whileRefunding = action;
      return this;
    }

    @Override
    public Refunded refund(
        GrowTransaction refunded, MonetaryOperation refund, GrowTransaction row) {
      Runnable once = whileRefunding;
      whileRefunding = () -> {};
      once.run();
      return writeRefund(refunded, refund, row);
    }

    private synchronized Refunded writeRefund(
        GrowTransaction refunded, MonetaryOperation refund, GrowTransaction row) {
      Runnable undo = snapshot();
      LedgerClaim operation = write(refunded, refund);
      if (operation == LedgerClaim.CLAIM_TAKEN) {
        return Refunded.CLAIM_TAKEN;
      }
      if (operation == LedgerClaim.OBJECT_GONE) {
        return Refunded.OBJECT_GONE;
      }
      if (!insertIfAbsent(row)) {
        undo.run();
        return Refunded.ID_TAKEN;
      }
      return Refunded.APPLIED;
    }

    @Override
    public Runnable snapshot() {
      List<GrowTransaction> contents = List.copyOf(stored);
      List<GrowStoredCardCharge> submitted = List.copyOf(submissions);
      return () -> {
        stored.clear();
        stored.addAll(contents);
        submissions.clear();
        submissions.addAll(submitted);
      };
    }

    @Override
    public int clear(TestRunId testRunId) {
      submissions.removeIf(held -> held.testRunId().equals(testRunId));
      claimed.removeIf(claim -> claim.startsWith(testRunId.value() + '\u0000'));
      List<GrowTransaction> doomed =
          stored.stream().filter(t -> t.testRunId().equals(testRunId)).toList();
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      submissions.clear();
      claimed.clear();
      int size = stored.size();
      stored.clear();
      return size;
    }

    List<GrowTransaction> all() {
      return List.copyOf(stored);
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

  static final class ScriptedIds implements IdGenerator {

    private final CountingIds otherwise = new CountingIds();
    private final List<String> digits;
    private int drawn;

    ScriptedIds(String... digits) {
      this.digits = List.of(digits);
    }

    @Override
    public String hex(int length) {
      return otherwise.hex(length);
    }

    @Override
    public String digits(int length) {
      return drawn < digits.size() ? digits.get(drawn++) : otherwise.digits(length);
    }
  }

  interface Undoable {

    Runnable snapshot();
  }

  static TransactionTemplate unitOfWorkOver(Undoable... stores) {
    List<Undoable> undoable = List.of(stores);
    return new TransactionTemplate(
        new PlatformTransactionManager() {

          private List<Runnable> undo = List.of();

          @Override
          public TransactionStatus getTransaction(TransactionDefinition definition) {
            undo = undoable.stream().map(Undoable::snapshot).toList();
            return new SimpleTransactionStatus();
          }

          @Override
          public void commit(TransactionStatus status) {
            undo = List.of();
          }

          @Override
          public void rollback(TransactionStatus status) {
            undo.forEach(Runnable::run);
          }
        });
  }

  static GrowCallbacks callbacks(
      Processes processes,
      Transactions transactions,
      Accounts accounts,
      WebhookDispatcher dispatcher,
      SimulatorClock clock,
      IdGenerator ids) {
    return new GrowCallbacks(
        processes,
        transactions,
        accounts,
        dispatcher,
        clock,
        ids,
        unitOfWorkOver(processes, transactions));
  }

  static CreateTransactionWithToken storedCardCharge(
      Processes processes, Transactions transactions, IdGenerator ids, ScenarioRules rules) {
    return new CreateTransactionWithToken(
        processes, transactions, ids, new ScenarioEngine(rules), GrowScalarType.INTEGER);
  }

  static GetTokenTransactionsByExternalIdentifiers retraceOfAStoredCardCharge(
      Processes processes, Transactions transactions, ScenarioRules rules) {
    return new GetTokenTransactionsByExternalIdentifiers(
        processes, transactions, new ScenarioEngine(rules), GrowScalarType.INTEGER);
  }

  static CreatePaymentProcess paymentProcessCreation(
      Processes processes, ScenarioRules scenarioRules, IdGenerator ids) {
    return new CreatePaymentProcess(
        processes,
        ids,
        "http://localhost:8080",
        new ScenarioEngine(scenarioRules),
        GrowScalarType.INTEGER);
  }

  static CompleteHostedProcess completions(Processes processes) {
    Transactions transactions = new Transactions();
    Accounts accounts = new Accounts();
    CountingIds ids = new CountingIds();
    SimulatorClock clock = clockAt(Instant.parse("2026-09-07T12:00:00Z"));
    return new CompleteHostedProcess(
        processes,
        callbacks(
            processes,
            transactions,
            accounts,
            idleDeliveries(new Audit(), ids, Instant.parse("2026-09-07T12:00:00Z")),
            clock,
            ids),
        new GrowPayerCards(new InMemoryPaymentMethodFixtures()));
  }

  static SimulatorClock clockAt(Instant instant) {
    return testRunId -> instant;
  }

  static WebhookDispatcher idleDeliveries(AuditTrail audit, IdGenerator ids, Instant now) {
    return new WebhookDispatcher(
        new InMemoryWebhookDeliveries(),
        (url, payload) -> new MerchantResponse.Answered(200, ""),
        audit,
        clockAt(now),
        ids,
        new RetryPlan(List.of(Duration.ZERO)),
        new DeliveryShaping(new InMemoryScenarioRules()),
        List.of(new GrowCallbackAcknowledgement(new Accounts())),
        List.of(),
        OutsideAnyUnitOfWork.RUNS_AT_ONCE);
  }
}
