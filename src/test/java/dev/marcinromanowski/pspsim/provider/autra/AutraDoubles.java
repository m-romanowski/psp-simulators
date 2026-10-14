package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
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
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
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
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

final class AutraDoubles {

  private AutraDoubles() {}

  private static long arrivals;

  static synchronized long theNextArrival() {
    arrivals++;
    return arrivals;
  }

  static String placeKey(TestRunId testRunId, String id) {
    return testRunId.value() + '\u0000' + id;
  }

  static final String THE_EXAMPLE_SLUG_TOKEN = "slug-token-abc";

  static final String THE_EXAMPLE_SLUG_STORED_CARD = "slug-stored-card-xyz";

  static InMemoryPaymentMethodFixtures fixturesHoldingTheExamplePair(TestRunId... runs) {
    InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
    for (TestRunId run : runs) {
      fixtures.seed(
          new NewPaymentMethodFixture(
              AutraApi.AUTRA,
              run,
              "merchant-1",
              "owner-1",
              PaymentMethodKind.CARD,
              "the-example-pair",
              new FixtureCard("visa", "1111", 12, 2030, "credit"),
              Map.of(
                  ProviderPaymentMethodFields.TOKEN,
                  THE_EXAMPLE_SLUG_TOKEN,
                  ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                  THE_EXAMPLE_SLUG_STORED_CARD),
              null,
              ObjectOrigin.CONTROL_API),
          PaymentMethodFixturePlan.storingNothingOfItsOwn(),
          Set.of());
    }
    return fixtures;
  }

  static AutraCardSession aTokenizedSession(
      TestRunId run,
      String accountId,
      String documentId,
      String slugToken,
      String slugStoredCard,
      Instant now) {
    return new AutraCardSession(
            run,
            "a-card-session-" + slugToken,
            accountId,
            documentId,
            null,
            AutraStatus.PENDING,
            "page-token-" + slugToken + run.value(),
            null,
            now.plus(AutraCardSession.THE_PUBLISHED_TTL),
            ObjectOrigin.PROVIDER_API,
            now,
            now)
        .tokenizedAs(
            new AutraStoredCard(
                slugToken, slugStoredCard, "122030", "VISA", null, "1111", 12, 2030),
            now);
  }

  static SimulatorClock clockAt(Instant instant) {
    return testRunId -> instant;
  }

  static AutraMerchant merchant(TestRunId testRunId, String accountId, String documentId) {
    return merchant(testRunId, accountId, documentId, Map.of());
  }

  static AutraMerchant merchant(
      TestRunId testRunId, String accountId, String documentId, Map<String, String> configuration) {
    Map<String, String> declared = new LinkedHashMap<>(configuration);
    declared.putIfAbsent(AutraMerchant.CLIENT_ID_PROPERTY, "client-" + accountId);
    declared.putIfAbsent(AutraMerchant.CLIENT_SECRET_PROPERTY, "secret-" + accountId);
    declared.put(AutraMerchant.DOCUMENT_ID_PROPERTY, documentId);
    return AutraMerchant.of(
        "accounts[0].configuration.", testRunId, new ConfiguredAccount(accountId, declared));
  }

  static final class Accounts implements ProviderAccounts {

    private final Map<TestRunId, List<WebhookTarget>> targets = new LinkedHashMap<>();

    Accounts callingBack(TestRunId testRunId, String accountId, String url) {
      targets
          .computeIfAbsent(testRunId, run -> new ArrayList<>())
          .add(new WebhookTarget(accountId, url));
      return this;
    }

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      targets.put(testRunId, List.copyOf(webhookTargets));
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
      return List.copyOf(targets.getOrDefault(testRunId, List.of()));
    }
  }

  static final class PixPayments implements AutraPixPayments {

    private final Map<String, AutraPixPayment> stored = new LinkedHashMap<>();
    private final Map<String, Long> arrivedAt = new LinkedHashMap<>();
    private String movedByAnotherRequestFirst = "";

    private String losingTo = "";

    PixPayments movedByAnotherRequestFirst(String pixId, String nativeStatus) {
      movedByAnotherRequestFirst = pixId;
      losingTo = nativeStatus;
      return this;
    }

    private String removedByAnotherRequestFirst = "";

    PixPayments removedByAnotherRequestFirst(String pixId) {
      removedByAnotherRequestFirst = pixId;
      return this;
    }

    private boolean removedFirst(String key, String pixId) {
      if (!pixId.equals(removedByAnotherRequestFirst)) {
        return false;
      }
      removedByAnotherRequestFirst = "";
      stored.remove(key);
      arrivedAt.remove(key);
      return true;
    }

    @Override
    public boolean insertIfAbsent(AutraPixPayment payment) {
      String key = placeKey(payment.testRunId(), payment.pixId());
      if (stored.containsKey(key)) {
        return false;
      }
      stored.put(key, payment);
      arrivedAt.put(key, Long.valueOf(theNextArrival()));
      return true;
    }

    @Override
    public Optional<AutraPixPayment> findById(TestRunId testRunId, String pixId) {
      return Optional.ofNullable(stored.get(placeKey(testRunId, pixId)));
    }

    @Override
    public List<AutraPixPayment> allById(TestRunId testRunId, List<String> pixIds) {
      List<AutraPixPayment> found = new ArrayList<>();
      for (String pixId : new LinkedHashSet<>(pixIds)) {
        findById(testRunId, pixId).ifPresent(found::add);
      }
      return List.copyOf(found);
    }

    @Override
    public boolean moveFromPendingTo(AutraPixPayment payment, String nativeStatus, Instant at) {
      String key = placeKey(payment.testRunId(), payment.pixId());
      AutraPixPayment held = stored.get(key);
      if (held == null || !AutraStatus.PENDING.equals(held.nativeStatus())) {
        return false;
      }
      if (removedFirst(key, payment.pixId())) {
        return false;
      }
      if (payment.pixId().equals(movedByAnotherRequestFirst)) {
        movedByAnotherRequestFirst = "";
        stored.put(key, held.movedTo(losingTo, at));
        return false;
      }
      stored.put(key, held.movedTo(nativeStatus, at));
      return true;
    }

    @Override
    public boolean moveFromAuthorizedToSettled(AutraPixPayment payment, Instant at) {
      String key = placeKey(payment.testRunId(), payment.pixId());
      AutraPixPayment held = stored.get(key);
      if (held == null || !AutraStatus.AUTHORIZED.equals(held.nativeStatus())) {
        return false;
      }
      if (removedFirst(key, payment.pixId())) {
        return false;
      }
      if (payment.pixId().equals(movedByAnotherRequestFirst)) {
        movedByAnotherRequestFirst = "";
        stored.put(key, held.movedTo(losingTo, at));
        return false;
      }
      stored.put(key, held.movedTo(AutraStatus.SETTLED, at));
      return true;
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String pixId) {
      Long arrival = arrivedAt.get(placeKey(testRunId, pixId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival.longValue(), pixId));
    }

    @Override
    public List<ArrivalOf<AutraPixPayment>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long walkingBackFrom = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<AutraPixPayment>> found = new ArrayList<>();
      for (AutraPixPayment pix : stored.values()) {
        long arrival = arrivedAt.get(placeKey(pix.testRunId(), pix.pixId())).longValue();
        if (pix.testRunId().equals(testRunId) && arrival < walkingBackFrom) {
          found.add(new ArrivalOf<>(arrival, pix));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.subList(0, Math.min(limit, found.size())));
    }

    @Override
    public int clear(TestRunId testRunId) {
      int cleared = 0;
      for (String key : List.copyOf(stored.keySet())) {
        if (stored.get(key).testRunId().equals(testRunId)) {
          stored.remove(key);
          arrivedAt.remove(key);
          cleared++;
        }
      }
      return cleared;
    }

    @Override
    public int clearAll() {
      int cleared = stored.size();
      stored.clear();
      arrivedAt.clear();
      return cleared;
    }
  }

  static final class CardSessions implements AutraCardSessions {

    private final Map<String, AutraCardSession> stored = new LinkedHashMap<>();
    private final Map<String, Long> arrivedAt = new LinkedHashMap<>();
    private String movedByAnotherRequestFirst = "";
    private int insertsToRefuse;

    CardSessions refusingTheNextInserts(int count) {
      this.insertsToRefuse = count;
      return this;
    }

    CardSessions movedByAnotherRequestFirst(String cardSessionId) {
      movedByAnotherRequestFirst = cardSessionId;
      return this;
    }

    CardSessions holding(AutraCardSession session) {
      insertIfAbsent(session);
      return this;
    }

    @Override
    public boolean insertIfAbsent(AutraCardSession session) {
      if (insertsToRefuse > 0) {
        insertsToRefuse--;
        return false;
      }
      String key = placeKey(session.testRunId(), session.cardSessionId());
      if (stored.containsKey(key) || findByPageToken(session.pageToken()).isPresent()) {
        return false;
      }
      stored.put(key, session);
      arrivedAt.put(key, Long.valueOf(theNextArrival()));
      return true;
    }

    @Override
    public Optional<AutraCardSession> findById(TestRunId testRunId, String cardSessionId) {
      return Optional.ofNullable(stored.get(placeKey(testRunId, cardSessionId)));
    }

    @Override
    public List<AutraCardSession> allById(TestRunId testRunId, List<String> cardSessionIds) {
      List<AutraCardSession> found = new ArrayList<>();
      for (String cardSessionId : new LinkedHashSet<>(cardSessionIds)) {
        findById(testRunId, cardSessionId).ifPresent(found::add);
      }
      return List.copyOf(found);
    }

    @Override
    public List<AutraStoredCard> tokenizedIn(TestRunId testRunId) {
      List<AutraStoredCard> cards = new ArrayList<>();
      for (AutraCardSession session : stored.values()) {
        AutraStoredCard card = session.storedCard();
        if (session.testRunId().equals(testRunId)
            && AutraCardSession.TOKENIZED.equals(session.nativeStatus())
            && card != null) {
          cards.add(card);
        }
      }
      return List.copyOf(cards);
    }

    @Override
    public Optional<AutraCardSession> findByPage(String cardSessionId, String pageToken) {
      return findByPageToken(pageToken)
          .filter(session -> session.cardSessionId().equals(cardSessionId));
    }

    private Optional<AutraCardSession> findByPageToken(String pageToken) {
      return stored.values().stream()
          .filter(session -> session.pageToken().equals(pageToken))
          .findFirst();
    }

    @Override
    public boolean moveFromPendingTo(AutraCardSession moved) {
      String key = placeKey(moved.testRunId(), moved.cardSessionId());
      AutraCardSession held = stored.get(key);
      if (held == null || !AutraStatus.PENDING.equals(held.nativeStatus())) {
        return false;
      }
      if (moved.cardSessionId().equals(movedByAnotherRequestFirst)) {
        movedByAnotherRequestFirst = "";
        stored.put(key, held.expiredAt(moved.updatedAt()));
        return false;
      }
      stored.put(key, moved);
      return true;
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String cardSessionId) {
      Long arrival = arrivedAt.get(placeKey(testRunId, cardSessionId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival.longValue(), cardSessionId));
    }

    @Override
    public List<ArrivalOf<AutraCardSession>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long walkingBackFrom = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<AutraCardSession>> found = new ArrayList<>();
      for (AutraCardSession session : stored.values()) {
        long arrival =
            arrivedAt.get(placeKey(session.testRunId(), session.cardSessionId())).longValue();
        if (session.testRunId().equals(testRunId) && arrival < walkingBackFrom) {
          found.add(new ArrivalOf<>(arrival, session));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.subList(0, Math.min(limit, found.size())));
    }

    @Override
    public int clear(TestRunId testRunId) {
      int cleared = 0;
      for (String key : List.copyOf(stored.keySet())) {
        if (stored.get(key).testRunId().equals(testRunId)) {
          stored.remove(key);
          arrivedAt.remove(key);
          cleared++;
        }
      }
      return cleared;
    }

    @Override
    public int clearAll() {
      int cleared = stored.size();
      stored.clear();
      arrivedAt.clear();
      return cleared;
    }
  }

  static final class Merchants implements AutraMerchants {

    private final List<AutraMerchant> stored = new ArrayList<>();

    Merchants with(AutraMerchant merchant) {
      stored.add(merchant);
      return this;
    }

    List<AutraMerchant> all() {
      return List.copyOf(stored);
    }

    @Override
    public void merge(TestRunId testRunId, List<AutraMerchant> configured) {
      for (AutraMerchant merchant : configured) {
        stored.removeIf(
            held ->
                held.testRunId().equals(testRunId)
                    && held.accountId().equals(merchant.accountId()));
        stored.add(merchant);
      }
    }

    @Override
    public void remove(TestRunId testRunId, String accountId) {
      stored.removeIf(
          merchant ->
              merchant.testRunId().equals(testRunId) && merchant.accountId().equals(accountId));
    }

    @Override
    public Optional<AutraMerchant> find(TestRunId testRunId, String accountId) {
      return stored.stream()
          .filter(merchant -> merchant.testRunId().equals(testRunId))
          .filter(merchant -> merchant.accountId().equals(accountId))
          .findFirst();
    }

    @Override
    public Optional<AutraMerchant> ofTheRunHolding(TestRunId testRunId, String documentId) {
      return stored.stream()
          .filter(merchant -> merchant.testRunId().equals(testRunId))
          .filter(merchant -> merchant.documentId().equals(documentId))
          .findFirst();
    }

    @Override
    public List<AutraMerchant> byClientId(String clientId) {
      return stored.stream().filter(merchant -> merchant.clientId().equals(clientId)).toList();
    }
  }

  static final class Sessions implements AutraSessions {

    private final Map<String, AutraAccessToken> tokens = new LinkedHashMap<>();
    private final List<AutraSession> opened = new ArrayList<>();
    private int mintsToRefuse;
    private int opensToRefuse;

    Sessions refusingTheNextMints(int count) {
      this.mintsToRefuse = count;
      return this;
    }

    Sessions refusingTheNextOpens(int count) {
      this.opensToRefuse = count;
      return this;
    }

    @Override
    public boolean mint(AutraAccessToken token) {
      if (mintsToRefuse > 0) {
        mintsToRefuse--;
        return false;
      }
      return tokens.putIfAbsent(token.value(), token) == null;
    }

    @Override
    public Optional<AutraAccessToken> accessToken(String value) {
      return Optional.ofNullable(tokens.get(value));
    }

    @Override
    public boolean open(AutraSession session) {
      if (opensToRefuse > 0) {
        opensToRefuse--;
        return false;
      }
      if (session(session.testRunId(), session.transactionalToken()).isPresent()) {
        return false;
      }
      opened.add(session);
      return true;
    }

    @Override
    public Optional<AutraSession> session(TestRunId testRunId, String transactionalToken) {
      return opened.stream()
          .filter(session -> session.testRunId().equals(testRunId))
          .filter(session -> session.transactionalToken().equals(transactionalToken))
          .findFirst();
    }

    @Override
    public int clear(TestRunId testRunId) {
      tokens.values().removeIf(token -> token.testRunId().equals(testRunId));
      List<AutraSession> doomed =
          opened.stream().filter(session -> session.testRunId().equals(testRunId)).toList();
      opened.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      tokens.clear();
      int size = opened.size();
      opened.clear();
      return size;
    }

    List<AutraSession> all() {
      return List.copyOf(opened);
    }
  }

  static final class Payments implements AutraPayments {

    private final Map<String, Long> arrivalOf = new LinkedHashMap<>();
    private final List<AutraPayment> stored = new ArrayList<>();
    private final Set<String> claimedPaymentIds = new LinkedHashSet<>();
    private final Map<String, Claim> orders = new LinkedHashMap<>();
    private final InMemoryKeyedExchanges exchanged = new InMemoryKeyedExchanges();
    private final List<AutraPaymentEvent> timeline = new ArrayList<>();
    private int claimsToLose;
    private int paymentIdsToRefuse;
    private int insertsToRefuse;

    Payments refusingTheNextInsert() {
      this.insertsToRefuse = 1;
      return this;
    }

    Payments refusingTheNextPaymentIds(int count) {
      this.paymentIdsToRefuse = count;
      return this;
    }

    Payments losingTheNextLedgerClaim() {
      this.claimsToLose = 1;
      return this;
    }

    Payments holdingAnUnfinishedClaimOn(TestRunId testRunId, String documentId, String orderId) {
      orders.put(orderKey(testRunId, documentId, orderId), new Claim(null));
      return this;
    }

    Payments holding(AutraPayment payment) {
      insertIfAbsent(payment);
      return this;
    }

    List<AutraPayment> all() {
      return List.copyOf(stored);
    }

    @Override
    public boolean claimPaymentId(TestRunId testRunId, String paymentId) {
      if (paymentIdsToRefuse > 0) {
        paymentIdsToRefuse--;
        return false;
      }
      return claimedPaymentIds.add(placeKey(testRunId, paymentId));
    }

    @Override
    public AutraOrderClaim claimOrder(
        TestRunId testRunId, String accountId, String documentId, String orderId, Instant at) {
      String key = orderKey(testRunId, documentId, orderId);
      Claim held = orders.get(key);
      if (held == null) {
        orders.put(key, new Claim(null));
        return new AutraOrderClaim.Fresh();
      }
      String paymentId = held.paymentId();
      return paymentId == null
          ? new AutraOrderClaim.HeldByARequestStillRunning()
          : new AutraOrderClaim.AlreadyPreAuthorized(paymentId);
    }

    @Override
    public Optional<String> theApprovedPaymentUnder(
        TestRunId testRunId, String documentId, String orderId) {
      for (AutraPayment payment : stored) {
        if (payment.testRunId().equals(testRunId)
            && payment.documentId().equals(documentId)
            && orderId.equals(payment.orderId())
            && payment.isAtAStatusTheSaleRouteRefusesAnotherOrderUnder()) {
          return Optional.of(payment.paymentId());
        }
      }
      return Optional.empty();
    }

    @Override
    public void releaseOrder(TestRunId testRunId, String documentId, String orderId) {
      orders.remove(orderKey(testRunId, documentId, orderId));
    }

    @Override
    public void settleOrder(
        TestRunId testRunId, String documentId, String orderId, String paymentId) {
      orders.put(orderKey(testRunId, documentId, orderId), new Claim(paymentId));
    }

    private static String orderKey(TestRunId testRunId, String documentId, String orderId) {
      return testRunId.value() + '\u0000' + documentId + '\u0000' + orderId;
    }

    @Override
    public boolean insertIfAbsent(AutraPayment payment) {
      if (insertsToRefuse > 0) {
        insertsToRefuse--;
        return false;
      }
      if (findById(payment.testRunId(), payment.paymentId()).isPresent()) {
        return false;
      }
      claimPaymentId(payment.testRunId(), payment.paymentId());
      arrivalOf.put(placeKey(payment.testRunId(), payment.paymentId()), theNextArrival());
      stored.add(payment);
      return true;
    }

    @Override
    public Optional<AutraPayment> findById(TestRunId testRunId, String paymentId) {
      return stored.stream()
          .filter(payment -> payment.testRunId().equals(testRunId))
          .filter(payment -> payment.paymentId().equals(paymentId))
          .findFirst();
    }

    @Override
    public List<AutraPayment> ofMerchant(TestRunId testRunId, String documentId) {
      return stored.stream()
          .filter(payment -> payment.testRunId().equals(testRunId))
          .filter(payment -> payment.documentId().equals(documentId))
          .sorted(
              Comparator.comparingLong(
                      (AutraPayment payment) ->
                          arrivalOf.getOrDefault(
                              placeKey(payment.testRunId(), payment.paymentId()), 0L))
                  .reversed())
          .toList();
    }

    @Override
    public void recordEvent(AutraPaymentEvent event) {
      timeline.add(event);
    }

    @Override
    public List<AutraPaymentEvent> timelineOf(TestRunId testRunId, String paymentId) {
      return timeline.stream()
          .filter(event -> event.testRunId().equals(testRunId))
          .filter(event -> event.paymentId().equals(paymentId))
          .toList();
    }

    @Override
    public List<AutraPaymentEvent> everyEventOf(TestRunId testRunId, List<String> paymentIds) {
      List<AutraPaymentEvent> found = new ArrayList<>();
      for (String paymentId : new LinkedHashSet<>(paymentIds)) {
        found.addAll(timelineOf(testRunId, paymentId));
      }
      return List.copyOf(found);
    }

    @Override
    public List<AutraPayment> allById(TestRunId testRunId, List<String> paymentIds) {
      List<AutraPayment> found = new ArrayList<>();
      for (String paymentId : new LinkedHashSet<>(paymentIds)) {
        findById(testRunId, paymentId).ifPresent(found::add);
      }
      return List.copyOf(found);
    }

    List<AutraPaymentEvent> everyEvent() {
      return List.copyOf(timeline);
    }

    @Override
    public LedgerClaim capture(AutraPayment payment, MonetaryOperation operation, Instant at) {
      return applied(payment, operation, AutraStatus.CAPTURED, at);
    }

    @Override
    public LedgerClaim reverse(
        AutraPayment payment, MonetaryOperation operation, String nativeStatus, Instant at) {
      return applied(payment, operation, nativeStatus, at);
    }

    private LedgerClaim applied(
        AutraPayment payment, MonetaryOperation operation, String nativeStatus, Instant at) {
      if (claimsToLose > 0) {
        claimsToLose--;
        return LedgerClaim.CLAIM_TAKEN;
      }
      Optional<AutraPayment> held = findById(payment.testRunId(), payment.paymentId());
      if (held.isEmpty()) {
        return LedgerClaim.OBJECT_GONE;
      }
      AutraPayment current = held.get();
      if (current.ledger().nextSequence() != operation.sequence()) {
        return LedgerClaim.CLAIM_TAKEN;
      }
      stored.set(
          stored.indexOf(current),
          rewritten(current, current.ledger().with(operation), nativeStatus, null, at));
      return LedgerClaim.APPLIED;
    }

    @Override
    public LedgerClaim releaseTheHold(AutraPayment payment, Instant at) {
      if (claimsToLose > 0) {
        claimsToLose--;
        return LedgerClaim.CLAIM_TAKEN;
      }
      Optional<AutraPayment> held = findById(payment.testRunId(), payment.paymentId());
      if (held.isEmpty()) {
        return LedgerClaim.OBJECT_GONE;
      }
      AutraPayment current = held.get();
      if (current.holdReleasedAt() != null) {
        return LedgerClaim.CLAIM_TAKEN;
      }
      stored.set(
          stored.indexOf(current),
          rewritten(current, current.ledger(), AutraStatus.VOIDED, at, at));
      return LedgerClaim.APPLIED;
    }

    private static AutraPayment rewritten(
        AutraPayment payment,
        Ledger ledger,
        String nativeStatus,
        @Nullable Instant released,
        Instant at) {
      return new AutraPayment(
          payment.testRunId(),
          payment.paymentId(),
          payment.accountId(),
          payment.documentId(),
          payment.orderId(),
          payment.type(),
          payment.transactionType(),
          payment.amount(),
          payment.currency(),
          payment.installments(),
          nativeStatus,
          payment.authorizationCode(),
          payment.rrn(),
          payment.trackingNumber(),
          payment.slugToken(),
          payment.slugStoredCard(),
          ledger,
          released == null ? payment.holdReleasedAt() : released,
          payment.origin(),
          payment.createdAt(),
          at);
    }

    @Override
    public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String paymentId) {
      Long arrival = arrivalOf.get(placeKey(testRunId, paymentId));
      return arrival == null
          ? Optional.empty()
          : Optional.of(new ProviderObjectCursor(arrival, paymentId));
    }

    @Override
    public List<ArrivalOf<AutraPayment>> newestFirst(
        TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
      long from = ProviderObjectCursor.walkingBackFrom(after);
      List<ArrivalOf<AutraPayment>> found = new ArrayList<>();
      for (AutraPayment payment : stored) {
        Long arrival = arrivalOf.get(placeKey(payment.testRunId(), payment.paymentId()));
        if (arrival != null && payment.testRunId().equals(testRunId) && arrival < from) {
          found.add(new ArrivalOf<>(arrival, payment));
        }
      }
      found.sort((left, right) -> Long.compare(right.arrival(), left.arrival()));
      return List.copyOf(found.size() <= limit ? found : found.subList(0, limit));
    }

    @Override
    public void observed(KeyedObservation observation) {
      exchanged.logOf(AutraKeyMechanisms.MECHANISMS).record(observation);
    }

    List<RecordedKeyedRequest> observedRequests() {
      return exchanged.recorded();
    }

    @Override
    public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
      return exchanged.exchanges(AutraKeyMechanisms.MECHANISMS, query);
    }

    @Override
    public int clear(TestRunId testRunId) {
      exchanged.clear(testRunId);
      orders.keySet().removeIf(key -> key.startsWith(testRunId.value() + '\u0000'));
      claimedPaymentIds.removeIf(claim -> claim.startsWith(testRunId.value() + '\u0000'));
      List<AutraPayment> doomed =
          stored.stream().filter(payment -> payment.testRunId().equals(testRunId)).toList();
      stored.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public int clearAll() {
      exchanged.clearAll();
      orders.clear();
      claimedPaymentIds.clear();
      int size = stored.size();
      stored.clear();
      return size;
    }

    private record Claim(@Nullable String paymentId) {}
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

    private static String pad(String value, int length, char with) {
      return String.valueOf(with).repeat(Math.max(0, length - value.length())) + value;
    }
  }

  static <T> T counting(Class<T> port, T store, Map<String, Integer> calls) {
    return port.cast(
        Proxy.newProxyInstance(
            port.getClassLoader(), new Class<?>[] {port}, new CountedCalls(store, calls)));
  }

  private record CountedCalls(Object store, Map<String, Integer> calls)
      implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method called, Object[] arguments) throws Throwable {
      calls.merge(called.getName(), 1, Integer::sum);
      try {
        return called.invoke(store, arguments);
      } catch (InvocationTargetException thrownInside) {
        throw thrownInside.getCause();
      }
    }
  }
}
