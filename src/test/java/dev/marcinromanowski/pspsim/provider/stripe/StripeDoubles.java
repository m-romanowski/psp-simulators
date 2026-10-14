package dev.marcinromanowski.pspsim.provider.stripe;

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
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
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
import dev.marcinromanowski.pspsim.kernel.webhook.AcknowledgementPolicy;
import dev.marcinromanowski.pspsim.kernel.webhook.AttemptRendering;
import dev.marcinromanowski.pspsim.kernel.webhook.InMemoryWebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantEndpoint;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

final class StripeDoubles {

  private static final String BETWEEN_PARTS = " ";

  private StripeDoubles() {}

  private static long arrivals;

  static synchronized long theNextArrival() {
    arrivals++;
    return arrivals;
  }

  static String placeKey(TestRunId testRunId, String id) {
    return testRunId.value() + '\u0000' + id;
  }

  static final StripeScheduledOutcomes NO_SCHEDULED_OUTCOMES = (settling, landing, after) -> {};

  static ScenarioEngine noScenarios() {
    return new ScenarioEngine(new InMemoryScenarioRules());
  }

  static ScheduledDeliveries heldResponses() {
    return new ScheduledDeliveries((delay, delivery) -> () -> {});
  }

  static SimulatorClock clockAt(Instant instant) {
    return testRunId -> instant;
  }

  static final RetryPlan THREE_ATTEMPTS =
      new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5)));

  static StripeAccount account(
      TestRunId testRunId, String accountId, String secretKey, String captureWindow) {
    return account(testRunId, accountId, secretKey, captureWindow, Map.of());
  }

  static StripeAccount account(
      TestRunId testRunId,
      String accountId,
      String secretKey,
      String captureWindow,
      Map<String, String> alsoDeclaring) {
    Map<String, String> declared = new LinkedHashMap<>();
    declared.put(StripeAccount.SECRET_KEY_PROPERTY, secretKey);
    declared.put(StripeCaptureWindow.SETTING, captureWindow);
    declared.putAll(alsoDeclaring);
    return StripeAccount.of(
        "accounts[0].configuration.", testRunId, new ConfiguredAccount(accountId, declared));
  }

  static WebhookDispatcher eventDeliveries(
      InMemoryWebhookDeliveries deliveries,
      MerchantEndpoint endpoint,
      AuditTrail audit,
      SimulatorClock clock,
      IdGenerator ids,
      StripeAccounts accounts,
      ScenarioRules shaping) {
    return new WebhookDispatcher(
        deliveries,
        endpoint,
        audit,
        clock,
        ids,
        THREE_ATTEMPTS,
        new DeliveryShaping(shaping),
        List.of((AcknowledgementPolicy) new StripeEventAcknowledgement()),
        List.of((AttemptRendering) new StripeWebhookRendering(accounts)),
        OutsideAnyUnitOfWork.RUNS_AT_ONCE);
  }

  static ProviderConfigurationPlan storingNothing() {
    return new ProviderConfigurationPlan(Set.of(), List.of(), () -> {});
  }

  static WebhookDispatcher noDeliveries(StripeAccounts accounts) {
    return eventDeliveries(
        new InMemoryWebhookDeliveries(),
        (url, payload) -> new MerchantResponse.Answered(200, ""),
        new Audit(),
        clockAt(Instant.EPOCH),
        new CountingIds(),
        accounts,
        new InMemoryScenarioRules());
  }

  static final class Accounts implements StripeAccounts, ProviderAccounts {

    private final List<StripeAccount> accounts = new ArrayList<>();
    private final Map<TestRunId, List<String>> identities = new LinkedHashMap<>();
    private final Map<TestRunId, List<WebhookTarget>> targets = new LinkedHashMap<>();

    Accounts with(StripeAccount account) {
      accounts.add(account);
      return this;
    }

    List<StripeAccount> all() {
      return List.copyOf(accounts);
    }

    @Override
    public void merge(TestRunId testRunId, List<StripeAccount> configured) {
      for (StripeAccount account : configured) {
        accounts.removeIf(
            held ->
                held.testRunId().equals(testRunId) && held.accountId().equals(account.accountId()));
        accounts.add(account);
      }
    }

    @Override
    public Optional<StripeAccount> ofTheRunHolding(TestRunId testRunId, String secretKey) {
      return accounts.stream()
          .filter(account -> account.testRunId().equals(testRunId))
          .filter(account -> secretKey.equals(account.secretKey()))
          .findFirst();
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

    @Override
    public List<StripeAccount> bySecretKey(String secretKey) {
      return accounts.stream().filter(account -> secretKey.equals(account.secretKey())).toList();
    }

    @Override
    public Optional<StripeAccount> find(TestRunId testRunId, String accountId) {
      return accounts.stream()
          .filter(
              account ->
                  account.testRunId().equals(testRunId) && account.accountId().equals(accountId))
          .findFirst();
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.copyOf(identities.getOrDefault(testRunId, List.of()));
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

    private final List<AuditRecord> records = new CopyOnWriteArrayList<>();

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

  static final class Intents implements StripePaymentIntents {

    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final List<StripePaymentIntent> stored = new ArrayList<>();
    private final List<StripeCharge> charges = new ArrayList<>();
    private final Set<String> claimed = new LinkedHashSet<>();
    private final Set<String> claimedCharges = new LinkedHashSet<>();
    private final Set<String> claimedElsewhere = new LinkedHashSet<>();
    private int insertsToReject;
    private int writesToRace;
    private int writesToLose;
    private @Nullable CountDownLatch reachedTheInsert;
    private @Nullable CountDownLatch mayLeaveTheInsert;

    Intents withTheIdsAlreadyClaimed(String... intentIds) {
      claimedElsewhere.addAll(List.of(intentIds));
      return this;
    }

    Intents rejectingTheNextInsert() {
      this.insertsToReject = 1;
      return this;
    }

    Intents racingTheNextWrite() {
      this.writesToRace = 1;
      return this;
    }

    Intents losingTheIntentOnTheNextWrite() {
      this.writesToLose = 1;
      return this;
    }

    @Override
    public boolean claimIntentId(TestRunId testRunId, String intentId) {
      return !claimedElsewhere.contains(intentId) && claimed.add(keyOf(testRunId, intentId));
    }

    private static String keyOf(TestRunId testRunId, String value) {
      return testRunId.value() + BETWEEN_PARTS + value;
    }

    CountDownLatch holdingTheNextInsertUntil(CountDownLatch released) {
      CountDownLatch reached = new CountDownLatch(1);
      this.reachedTheInsert = reached;
      this.mayLeaveTheInsert = released;
      return reached;
    }

    private void waitThereIfThisTestAskedToHoldTheInsertOpen() {
      CountDownLatch reached = reachedTheInsert;
      CountDownLatch released = mayLeaveTheInsert;
      if (reached == null || released == null) {
        return;
      }
      reachedTheInsert = null;
      mayLeaveTheInsert = null;
      reached.countDown();
      try {
        released.await();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("the held insert was interrupted", interrupted);
      }
    }

    @Override
    public boolean insertIfAbsent(StripePaymentIntent intent, @Nullable StripeCharge charge) {
      waitThereIfThisTestAskedToHoldTheInsertOpen();
      if (insertsToReject > 0) {
        insertsToReject--;
        return false;
      }
      if (find(intent.testRunId(), intent.intentId()).isPresent()) {
        return false;
      }
      claimIntentId(intent.testRunId(), intent.intentId());
      arrivals.put(placeKey(intent.testRunId(), intent.intentId()), theNextArrival());
      stored.add(intent);
      if (charge != null) {
        charges.add(charge);
      }
      return true;
    }

    boolean claimCharge(TestRunId testRunId, String chargeId) {
      return claimedCharges.add(keyOf(testRunId, chargeId));
    }

    Optional<StripeCharge> charge(TestRunId testRunId, String chargeId) {
      return charges.stream()
          .filter(c -> c.testRunId().equals(testRunId) && c.chargeId().equals(chargeId))
          .findFirst();
    }

    void clearCharges(TestRunId testRunId) {
      claimedCharges.removeIf(claim -> claim.startsWith(testRunId.value() + BETWEEN_PARTS));
      charges.removeIf(c -> c.testRunId().equals(testRunId));
    }

    void clearAllCharges() {
      claimedCharges.clear();
      charges.clear();
    }

    List<StripeCharge> allCharges() {
      return List.copyOf(charges);
    }

    @Override
    public Optional<StripePaymentIntent> find(TestRunId testRunId, String intentId) {
      return stored.stream()
          .filter(i -> i.testRunId().equals(testRunId) && i.intentId().equals(intentId))
          .findFirst();
    }

    @Override
    public List<StripePaymentIntent> allById(TestRunId testRunId, List<String> intentIds) {
      return stored.stream()
          .filter(i -> i.testRunId().equals(testRunId) && intentIds.contains(i.intentId()))
          .toList();
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String intentId) {
      Long arrival = arrivals.get(placeKey(testRunId, intentId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival, intentId));
    }

    @Override
    public List<ArrivalOf<StripePaymentIntent>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<StripePaymentIntent>> found = new ArrayList<>();
      for (StripePaymentIntent intent : stored) {
        Long arrival = arrivals.get(placeKey(intent.testRunId(), intent.intentId()));
        if (arrival != null && intent.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, intent));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    @Override
    public LedgerClaim pending(
        StripePaymentIntent intent,
        String paymentMethod,
        Instant settlesAt,
        @Nullable StripeDecline settlesWith,
        Instant at) {
      return applied(
          intent,
          held -> held.pendingUntil(settlesAt, settlesWith, paymentMethod, at),
          intent.status());
    }

    @Override
    public LedgerClaim confirmed(
        StripePaymentIntent intent,
        StripeIntentStatus reached,
        String paymentMethod,
        MonetaryOperation operation,
        StripeCharge charge,
        Instant at) {
      return minted(
          applied(
              intent,
              held -> held.confirmedAs(reached, paymentMethod, operation, charge.chargeId(), at),
              intent.status()),
          charge);
    }

    @Override
    public LedgerClaim declined(
        StripePaymentIntent intent,
        StripeDecline decline,
        @Nullable String paymentMethod,
        StripeCharge charge,
        Instant at) {
      String usingPaymentMethod = paymentMethod == null ? "" : paymentMethod;
      return minted(
          applied(
              intent,
              held -> held.declinedWith(decline, usingPaymentMethod, charge.chargeId(), at),
              intent.status()),
          charge);
    }

    private LedgerClaim minted(LedgerClaim applied, StripeCharge charge) {
      if (applied == LedgerClaim.APPLIED) {
        charges.add(charge);
      }
      return applied;
    }

    @Override
    public LedgerClaim captured(
        StripePaymentIntent intent,
        StripeIntentStatus reached,
        MonetaryOperation operation,
        Instant at) {
      return applied(
          intent,
          held -> held.capturedBy(reached, operation, at),
          StripeIntentStatus.REQUIRES_CAPTURE);
    }

    @Override
    public LedgerClaim cancelled(
        StripePaymentIntent intent, @Nullable StripeCancellationReason reason, Instant at) {
      return applied(intent, held -> held.cancelledFor(reason, at), intent.status());
    }

    private LedgerClaim applied(
        StripePaymentIntent intent,
        UnaryOperator<StripePaymentIntent> change,
        StripeIntentStatus expected) {
      if (writesToLose > 0) {
        writesToLose--;
        return LedgerClaim.OBJECT_GONE;
      }
      Optional<StripePaymentIntent> held = find(intent.testRunId(), intent.intentId());
      if (held.isEmpty()) {
        return LedgerClaim.OBJECT_GONE;
      }
      StripePaymentIntent current = held.get();
      if (writesToRace > 0 || current.status() != expected) {
        writesToRace = Math.max(0, writesToRace - 1);
        return LedgerClaim.CLAIM_TAKEN;
      }
      stored.set(stored.indexOf(current), change.apply(current));
      return LedgerClaim.APPLIED;
    }

    @Override
    public int clear(TestRunId testRunId) {
      claimed.removeIf(claim -> claim.startsWith(testRunId.value() + BETWEEN_PARTS));
      List<StripePaymentIntent> doomed =
          stored.stream().filter(i -> i.testRunId().equals(testRunId)).toList();
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

    List<StripePaymentIntent> all() {
      return List.copyOf(stored);
    }

    void replace(StripePaymentIntent changed) {
      find(changed.testRunId(), changed.intentId())
          .ifPresent(held -> stored.set(stored.indexOf(held), changed));
    }
  }

  static final class Charges implements StripeCharges {

    private final StripeDoubles.Intents intents;
    private final Set<String> claimedElsewhere = new LinkedHashSet<>();

    Charges(StripeDoubles.Intents intents) {
      this.intents = intents;
    }

    Charges withTheIdsAlreadyTaken(String... chargeIds) {
      claimedElsewhere.addAll(List.of(chargeIds));
      return this;
    }

    @Override
    public boolean claimChargeId(TestRunId testRunId, String chargeId) {
      return !claimedElsewhere.contains(chargeId) && intents.claimCharge(testRunId, chargeId);
    }

    @Override
    public Optional<StripeCharge> find(TestRunId testRunId, String chargeId) {
      return intents.charge(testRunId, chargeId);
    }

    @Override
    public void clear(TestRunId testRunId) {
      intents.clearCharges(testRunId);
    }

    @Override
    public void clearAll() {
      intents.clearAllCharges();
    }

    List<StripeCharge> all() {
      return intents.allCharges();
    }
  }

  static final class Refunds implements StripeRefunds {

    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final List<StripeRefund> stored = new ArrayList<>();
    private final StripeDoubles.Intents intents;
    private final Set<String> claimedElsewhere = new LinkedHashSet<>();
    private int writesToRace;
    private int writesToLose;

    Refunds(StripeDoubles.Intents intents) {
      this.intents = intents;
    }

    Refunds withTheIdsAlreadyTaken(String... refundIds) {
      claimedElsewhere.addAll(List.of(refundIds));
      return this;
    }

    Refunds racingTheNextWrite() {
      this.writesToRace = 1;
      return this;
    }

    Refunds losingTheIntentOnTheNextWrite() {
      this.writesToLose = 1;
      return this;
    }

    @Override
    public Refunded refund(
        StripePaymentIntent intent,
        @Nullable MonetaryOperation operation,
        StripeRefund refund,
        Instant at) {
      if (writesToLose > 0) {
        writesToLose--;
        return Refunded.OBJECT_GONE;
      }
      Optional<StripePaymentIntent> held = intents.find(intent.testRunId(), intent.intentId());
      if (held.isEmpty()) {
        return Refunded.OBJECT_GONE;
      }
      if (writesToRace > 0) {
        writesToRace--;
        return Refunded.CLAIM_TAKEN;
      }
      if (claimedElsewhere.remove(refund.refundId())
          || find(refund.testRunId(), refund.refundId()).isPresent()) {
        return Refunded.ID_TAKEN;
      }
      if (operation != null) {
        intents.replace(held.get().refundedBy(operation, at));
      }
      arrivals.put(placeKey(refund.testRunId(), refund.refundId()), theNextArrival());
      stored.add(refund);
      return Refunded.APPLIED;
    }

    @Override
    public Optional<StripeRefund> find(TestRunId testRunId, String refundId) {
      return stored.stream()
          .filter(r -> r.testRunId().equals(testRunId) && r.refundId().equals(refundId))
          .findFirst();
    }

    @Override
    public List<StripeRefund> allById(TestRunId testRunId, List<String> refundIds) {
      return stored.stream()
          .filter(r -> r.testRunId().equals(testRunId) && refundIds.contains(r.refundId()))
          .toList();
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String refundId) {
      Long arrival = arrivals.get(placeKey(testRunId, refundId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival, refundId));
    }

    @Override
    public List<ArrivalOf<StripeRefund>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<StripeRefund>> found = new ArrayList<>();
      for (StripeRefund refund : stored) {
        Long arrival = arrivals.get(placeKey(refund.testRunId(), refund.refundId()));
        if (arrival != null && refund.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, refund));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    @Override
    public int clear(TestRunId testRunId) {
      List<StripeRefund> doomed =
          stored.stream().filter(r -> r.testRunId().equals(testRunId)).toList();
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      int size = stored.size();
      stored.clear();
      return size;
    }

    List<StripeRefund> all() {
      return List.copyOf(stored);
    }
  }

  static StripePaymentMethods noPaymentMethods() {
    return new PaymentMethods();
  }

  static final class PaymentMethods implements StripePaymentMethods {

    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final List<StripePaymentMethod> stored = new ArrayList<>();
    private int writesToLose;

    PaymentMethods losingTheMethodOnTheNextWrite() {
      this.writesToLose = 1;
      return this;
    }

    @Override
    public boolean insertIfAbsent(StripePaymentMethod method) {
      if (find(method.testRunId(), method.paymentMethodId()).isPresent()) {
        return false;
      }
      arrivals.put(placeKey(method.testRunId(), method.paymentMethodId()), theNextArrival());
      stored.add(method);
      return true;
    }

    @Override
    public Optional<StripePaymentMethod> find(TestRunId testRunId, String paymentMethodId) {
      return stored.stream()
          .filter(
              m -> m.testRunId().equals(testRunId) && m.paymentMethodId().equals(paymentMethodId))
          .findFirst();
    }

    @Override
    public List<StripePaymentMethod> allById(TestRunId testRunId, List<String> paymentMethodIds) {
      return stored.stream()
          .filter(m -> m.testRunId().equals(testRunId))
          .filter(m -> paymentMethodIds.contains(m.paymentMethodId()))
          .toList();
    }

    @Override
    public List<StripePaymentMethod> of(
        TestRunId testRunId,
        String accountId,
        @Nullable String customerId,
        @Nullable StripePaymentMethodType type) {
      List<StripePaymentMethod> found = new ArrayList<>();
      for (StripePaymentMethod method : stored) {
        if (!method.testRunId().equals(testRunId) || !method.accountId().equals(accountId)) {
          continue;
        }
        if (customerId != null && !customerId.equals(method.customerId())) {
          continue;
        }
        if (type != null && method.type() != type) {
          continue;
        }
        found.add(method);
      }
      found.sort(
          (left, right) ->
              Long.compare(
                  arrivals.get(placeKey(right.testRunId(), right.paymentMethodId())),
                  arrivals.get(placeKey(left.testRunId(), left.paymentMethodId()))));
      return List.copyOf(found);
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String paymentMethodId) {
      Long arrival = arrivals.get(placeKey(testRunId, paymentMethodId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival, paymentMethodId));
    }

    @Override
    public List<ArrivalOf<StripePaymentMethod>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<StripePaymentMethod>> found = new ArrayList<>();
      for (StripePaymentMethod method : stored) {
        Long arrival = arrivals.get(placeKey(method.testRunId(), method.paymentMethodId()));
        if (arrival != null && method.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, method));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    @Override
    public Detachment detached(StripePaymentMethod method, Instant at) {
      if (writesToLose > 0) {
        writesToLose--;
        return Detachment.OBJECT_GONE;
      }
      Optional<StripePaymentMethod> held = find(method.testRunId(), method.paymentMethodId());
      if (held.isEmpty()) {
        return Detachment.OBJECT_GONE;
      }
      StripePaymentMethod current = held.get();
      if (current.detached()) {
        return Detachment.ALREADY_DETACHED;
      }
      stored.set(stored.indexOf(current), current.detachedAt(at));
      return Detachment.APPLIED;
    }

    @Override
    public int detachedEveryMethodOf(
        TestRunId testRunId, String accountId, String customerId, Instant at) {
      int detached = 0;
      for (StripePaymentMethod method : List.copyOf(stored)) {
        if (method.testRunId().equals(testRunId)
            && method.accountId().equals(accountId)
            && customerId.equals(method.customerId())) {
          stored.set(stored.indexOf(method), method.detachedAt(at));
          detached++;
        }
      }
      return detached;
    }

    @Override
    public void forget(TestRunId testRunId, String paymentMethodId) {
      find(testRunId, paymentMethodId).ifPresent(stored::remove);
    }

    @Override
    public int clear(TestRunId testRunId) {
      List<StripePaymentMethod> doomed =
          stored.stream().filter(m -> m.testRunId().equals(testRunId)).toList();
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      int size = stored.size();
      stored.clear();
      return size;
    }

    void replace(StripePaymentMethod held, StripePaymentMethod changed) {
      stored.set(stored.indexOf(held), changed);
    }

    List<StripePaymentMethod> all() {
      return List.copyOf(stored);
    }
  }

  static final class SetupIntents implements StripeSetupIntents {

    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final List<StripeSetupIntent> stored = new ArrayList<>();
    private final PaymentMethods methods;
    private int writesToLose;
    private int writesToRace;
    private int attachmentsToLose;

    SetupIntents() {
      this(new PaymentMethods());
    }

    SetupIntents(PaymentMethods methods) {
      this.methods = methods;
    }

    SetupIntents losingTheSetupOnTheNextWrite() {
      this.writesToLose = 1;
      return this;
    }

    SetupIntents losingTheStatusRaceOnTheNextWrite() {
      this.writesToRace = 1;
      return this;
    }

    SetupIntents losingTheAttachmentOnTheNextWrite() {
      this.attachmentsToLose = 1;
      return this;
    }

    @Override
    public boolean insertIfAbsent(StripeSetupIntent intent) {
      if (find(intent.testRunId(), intent.setupIntentId()).isPresent()) {
        return false;
      }
      arrivals.put(placeKey(intent.testRunId(), intent.setupIntentId()), theNextArrival());
      stored.add(intent);
      return true;
    }

    @Override
    public Optional<StripeSetupIntent> find(TestRunId testRunId, String setupIntentId) {
      return stored.stream()
          .filter(
              intent ->
                  intent.testRunId().equals(testRunId)
                      && intent.setupIntentId().equals(setupIntentId))
          .findFirst();
    }

    @Override
    public List<StripeSetupIntent> allById(TestRunId testRunId, List<String> setupIntentIds) {
      return stored.stream()
          .filter(intent -> intent.testRunId().equals(testRunId))
          .filter(intent -> setupIntentIds.contains(intent.setupIntentId()))
          .toList();
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String setupIntentId) {
      Long arrival = arrivals.get(placeKey(testRunId, setupIntentId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival, setupIntentId));
    }

    @Override
    public List<ArrivalOf<StripeSetupIntent>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<StripeSetupIntent>> found = new ArrayList<>();
      for (StripeSetupIntent intent : stored) {
        Long arrival = arrivals.get(placeKey(intent.testRunId(), intent.setupIntentId()));
        if (arrival != null && intent.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, intent));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    @Override
    public Setup succeeded(
        StripeSetupIntent intent, String paymentMethodId, @Nullable String customerId, Instant at) {
      if (customerId != null && !attached(intent, paymentMethodId, customerId, at)) {
        return Setup.THE_METHOD_IS_NO_LONGER_ATTACHABLE;
      }
      return moved(intent, intent.succeededWith(paymentMethodId, at));
    }

    private boolean attached(
        StripeSetupIntent intent, String paymentMethodId, String customerId, Instant at) {
      if (attachmentsToLose > 0) {
        attachmentsToLose--;
        return false;
      }
      Optional<StripePaymentMethod> held = methods.find(intent.testRunId(), paymentMethodId);
      if (held.isEmpty()) {
        return false;
      }
      StripePaymentMethod method = held.get();
      if (!method.accountId().equals(intent.accountId())
          || method.detached()
          || (method.attached() && !customerId.equals(method.customerId()))) {
        return false;
      }
      methods.replace(
          method,
          new StripePaymentMethod(
              method.testRunId(),
              method.paymentMethodId(),
              method.accountId(),
              customerId,
              method.type(),
              method.card(),
              null,
              method.origin(),
              method.createdAt(),
              at));
      return true;
    }

    @Override
    public Setup failed(
        StripeSetupIntent intent, StripeDecline decline, String paymentMethodId, Instant at) {
      return moved(intent, intent.failedWith(decline, paymentMethodId, at));
    }

    private Setup moved(StripeSetupIntent intent, StripeSetupIntent reached) {
      if (writesToLose > 0) {
        writesToLose--;
        return Setup.OBJECT_GONE;
      }
      if (writesToRace > 0) {
        writesToRace--;
        return Setup.THE_SETUP_MOVED_ON;
      }
      Optional<StripeSetupIntent> held = find(intent.testRunId(), intent.setupIntentId());
      if (held.isEmpty()) {
        return Setup.OBJECT_GONE;
      }
      StripeSetupIntent current = held.get();
      if (current.status() != intent.status()) {
        return Setup.THE_SETUP_MOVED_ON;
      }
      stored.set(stored.indexOf(current), reached);
      return Setup.APPLIED;
    }

    @Override
    public int clear(TestRunId testRunId) {
      List<StripeSetupIntent> doomed =
          stored.stream().filter(intent -> intent.testRunId().equals(testRunId)).toList();
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      int size = stored.size();
      stored.clear();
      return size;
    }

    List<StripeSetupIntent> all() {
      return List.copyOf(stored);
    }
  }

  static final class Customers implements StripeCustomers {

    private final Map<String, Long> arrivals = new LinkedHashMap<>();
    private final List<StripeCustomer> stored = new ArrayList<>();
    private int writesToLose;
    private int writesToRace;

    Customers losingTheCustomerOnTheNextWrite() {
      this.writesToLose = 1;
      return this;
    }

    Customers deletingTheCustomerOnTheNextWrite() {
      this.writesToRace = 1;
      return this;
    }

    @Override
    public boolean insertIfAbsent(StripeCustomer customer) {
      if (find(customer.testRunId(), customer.customerId()).isPresent()) {
        return false;
      }
      arrivals.put(placeKey(customer.testRunId(), customer.customerId()), theNextArrival());
      stored.add(customer);
      return true;
    }

    @Override
    public Optional<StripeCustomer> find(TestRunId testRunId, String customerId) {
      return stored.stream()
          .filter(c -> c.testRunId().equals(testRunId) && c.customerId().equals(customerId))
          .findFirst();
    }

    @Override
    public List<StripeCustomer> allById(TestRunId testRunId, List<String> customerIds) {
      return stored.stream()
          .filter(c -> c.testRunId().equals(testRunId) && customerIds.contains(c.customerId()))
          .toList();
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String customerId) {
      Long arrival = arrivals.get(placeKey(testRunId, customerId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival, customerId));
    }

    @Override
    public List<ArrivalOf<StripeCustomer>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<StripeCustomer>> found = new ArrayList<>();
      for (StripeCustomer customer : stored) {
        Long arrival = arrivals.get(placeKey(customer.testRunId(), customer.customerId()));
        if (arrival != null && customer.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, customer));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    @Override
    public Changed updated(StripeCustomer customer, @Nullable String email, Instant at) {
      return applied(customer, held -> held.withEmail(email, at));
    }

    @Override
    public Changed deleted(StripeCustomer customer, Instant at) {
      return applied(customer, held -> held.deletedAt(at));
    }

    private Changed applied(StripeCustomer customer, UnaryOperator<StripeCustomer> change) {
      if (writesToLose > 0) {
        writesToLose--;
        return Changed.OBJECT_GONE;
      }
      Optional<StripeCustomer> held = find(customer.testRunId(), customer.customerId());
      if (held.isEmpty()) {
        return Changed.OBJECT_GONE;
      }
      StripeCustomer current = held.get();
      if (writesToRace > 0 || current.deleted()) {
        writesToRace = Math.max(0, writesToRace - 1);
        return Changed.ALREADY_DELETED;
      }
      stored.set(stored.indexOf(current), change.apply(current));
      return Changed.APPLIED;
    }

    @Override
    public int clear(TestRunId testRunId) {
      List<StripeCustomer> doomed =
          stored.stream().filter(c -> c.testRunId().equals(testRunId)).toList();
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      int size = stored.size();
      stored.clear();
      return size;
    }

    List<StripeCustomer> all() {
      return List.copyOf(stored);
    }
  }

  static final class Keys implements StripeIdempotencyKeys {

    private final Map<String, Held> held = new LinkedHashMap<>();

    private final InMemoryKeyedExchanges exchanged = new InMemoryKeyedExchanges();

    Keys heldInProgress(TestRunId testRunId, String accountId, String key, String fingerprint) {
      held.put(keyOf(testRunId, accountId, key), new Held(fingerprint, null, Instant.MAX));
      return this;
    }

    @Override
    public synchronized StripeIdempotencyClaim claim(
        TestRunId testRunId,
        String accountId,
        String key,
        String fingerprint,
        Instant now,
        Instant prunedBefore) {
      String at = keyOf(testRunId, accountId, key);
      Held existing = held.get(at);
      if (existing != null && !existing.claimedAt().isAfter(prunedBefore)) {
        held.remove(at);
        existing = null;
      }
      if (existing == null) {
        held.put(at, new Held(fingerprint, null, now));
        return new StripeIdempotencyClaim.Fresh();
      }
      StripeStoredAnswer answer = existing.answer();
      if (answer == null) {
        return new StripeIdempotencyClaim.InProgressElsewhere();
      }
      if (!existing.fingerprint().equals(fingerprint)) {
        return new StripeIdempotencyClaim.ReusedWithOtherParameters();
      }
      return new StripeIdempotencyClaim.Replayed(answer);
    }

    @Override
    public synchronized void completed(
        TestRunId testRunId, String accountId, String key, StripeStoredAnswer answer) {
      String at = keyOf(testRunId, accountId, key);
      Held existing = held.get(at);
      if (existing != null && existing.answer() == null) {
        held.put(at, new Held(existing.fingerprint(), answer, existing.claimedAt()));
      }
    }

    @Override
    public synchronized void released(TestRunId testRunId, String accountId, String key) {
      String at = keyOf(testRunId, accountId, key);
      Held existing = held.get(at);
      if (existing != null && existing.answer() == null) {
        held.remove(at);
      }
    }

    @Override
    public String fingerprintOf(TestRunId testRunId, FingerprintedRequest request) {
      return exchanged.logOf(StripeKeyMechanisms.MECHANISMS).fingerprintOf(testRunId, request);
    }

    @Override
    public synchronized void observed(KeyedObservation observation) {
      exchanged.logOf(StripeKeyMechanisms.MECHANISMS).record(observation);
    }

    @Override
    public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
      return exchanged.exchanges(StripeKeyMechanisms.MECHANISMS, query);
    }

    @Override
    public int clear(TestRunId testRunId) {
      exchanged.clear(testRunId);
      int size = held.size();
      held.keySet().removeIf(at -> at.startsWith(testRunId.value() + BETWEEN_PARTS));
      return size - held.size();
    }

    @Override
    public int clearAll() {
      exchanged.clearAll();
      int size = held.size();
      held.clear();
      return size;
    }

    int size() {
      return held.size();
    }

    private static String keyOf(TestRunId testRunId, String accountId, String key) {
      return testRunId.value() + BETWEEN_PARTS + accountId + BETWEEN_PARTS + key;
    }

    private record Held(
        String fingerprint, @Nullable StripeStoredAnswer answer, Instant claimedAt) {}
  }

  static final class EphemeralKeys implements StripeEphemeralKeys {

    private final List<StripeEphemeralKey> stored = new ArrayList<>();

    @Override
    public boolean insertIfAbsent(StripeEphemeralKey key) {
      for (StripeEphemeralKey held : stored) {
        if (held.testRunId().equals(key.testRunId())
            && held.ephemeralKeyId().equals(key.ephemeralKeyId())) {
          return false;
        }
        if (held.secret().equals(key.secret())) {
          return false;
        }
      }
      stored.add(key);
      return true;
    }

    @Override
    public List<StripeEphemeralKey> bySecret(String secret) {
      return stored.stream().filter(key -> key.secret().equals(secret)).toList();
    }

    @Override
    public void clear(TestRunId testRunId) {
      stored.removeIf(key -> key.testRunId().equals(testRunId));
    }

    @Override
    public void clearAll() {
      stored.clear();
    }

    List<StripeEphemeralKey> all() {
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

    private synchronized int next(int length) {
      return issued.merge(length, 1, Integer::sum);
    }

    private static String pad(String value, int length, char with) {
      return String.valueOf(with).repeat(Math.max(0, length - value.length())) + value;
    }
  }
}
