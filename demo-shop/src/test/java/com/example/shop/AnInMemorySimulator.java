package com.example.shop;

import com.stripe.net.Webhook;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class AnInMemorySimulator implements SimulatorControl, Payments {

  record OpenedRun(String secretKey, String webhookSecret, String callbackUrl, String clock) {}

  record SentEvent(String testRunId, String paymentIntentId, String eventType) {}

  record RemovedAccount(String testRunId, String accountId) {}

  enum SetUpStep {
    OPENING_THE_RUN,
    SEEDING_THE_SAVED_CARD,
    WRITING_THE_CAPTURE_RULE
  }

  static final class Pause {

    private final AtomicInteger entrants = new AtomicInteger();
    private final CountDownLatch firstEntered = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    void awaitTheFirstEntrant() throws InterruptedException {
      if (!firstEntered.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("nobody called the provider");
      }
    }

    int entrants() {
      return entrants.get();
    }

    void release() {
      released.countDown();
    }

    private void passThrough() {
      entrants.incrementAndGet();
      firstEntered.countDown();
      try {
        if (!released.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("the provider was never released");
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(interrupted);
      }
    }
  }

  private final Map<String, OpenedRun> opened = new ConcurrentHashMap<>();
  private final Map<String, String> savedCards = new ConcurrentHashMap<>();
  private final Map<String, String> captureRefusals = new ConcurrentHashMap<>();
  private final Map<String, String> holds = new ConcurrentHashMap<>();
  private final Map<String, Long> captured = new ConcurrentHashMap<>();
  private final AtomicInteger holdsPlaced = new AtomicInteger();
  private final AtomicInteger capturesAsked = new AtomicInteger();
  private volatile Pause pause;
  private volatile boolean refusesTheNextEvent;
  private volatile SetUpStep writesButDoesNotAnswer;
  private volatile boolean refusesEveryReset;
  private volatile boolean refusesToRemoveAnyAccount;
  private final List<String> refusesToRemoveTheAccountOf = new ArrayList<>();
  private final List<SentEvent> sent = new ArrayList<>();
  private final List<SentEvent> undelivered = new ArrayList<>();
  private final List<String> resets = new ArrayList<>();
  private final List<RemovedAccount> removedAccounts = new ArrayList<>();
  private int delivered;
  private volatile StripeWebhooks theShopsEndpoint;

  void deliversWebhooksTo(StripeWebhooks endpoint) {
    this.theShopsEndpoint = endpoint;
  }

  @Override
  public void openTheRun(StoryRun run, String callbackUrl) {
    opened.put(
        run.testRunId(),
        new OpenedRun(
            run.secretKey(), run.webhookSecret(), callbackUrl, run.clockPinnedTo().toString()));
    answersUnlessItWasToldNotTo(SetUpStep.OPENING_THE_RUN, run.testRunId());
  }

  @Override
  public void seedTheSavedCard(StoryRun run) {
    passThroughAnyPause();
    savedCards.put(run.testRunId(), run.savedCard());
    answersUnlessItWasToldNotTo(SetUpStep.SEEDING_THE_SAVED_CARD, run.testRunId());
  }

  @Override
  public String refuseEveryCapture(String testRunId, String providerCode) {
    captureRefusals.put(testRunId, providerCode);
    answersUnlessItWasToldNotTo(SetUpStep.WRITING_THE_CAPTURE_RULE, testRunId);
    return "scn_" + testRunId;
  }

  private void answersUnlessItWasToldNotTo(SetUpStep step, String testRunId) {
    if (writesButDoesNotAnswer == step) {
      writesButDoesNotAnswer = null;
      throw new TheSimulatorDidNotAnswer(
          step + " of " + testRunId, new IllegalStateException("the answer never came"));
    }
  }

  @Override
  public void sendTheEvent(String testRunId, String paymentIntentId, String eventType) {
    passThroughAnyPause();
    synchronized (this) {
      if (refusesTheNextEvent) {
        refusesTheNextEvent = false;
        throw new TheSimulatorRefused("send " + eventType, 500, "refused on purpose");
      }
      SentEvent event = new SentEvent(testRunId, paymentIntentId, eventType);
      sent.add(event);
      undelivered.add(event);
    }
  }

  synchronized void deliverWhatIsDue() {
    List<SentEvent> due = List.copyOf(undelivered);
    undelivered.clear();
    for (SentEvent event : due) {
      delivered++;
      deliver(event, delivered);
    }
  }

  private void deliver(SentEvent event, int number) {
    String payload =
        "{\"id\":\"evt_"
            + number
            + "\",\"object\":\"event\",\"type\":\""
            + event.eventType()
            + "\",\"data\":{\"object\":{\"id\":\""
            + event.paymentIntentId()
            + "\",\"object\":\"payment_intent\",\"status\":\"succeeded\"}}}";
    OpenedRun openedRun = opened.get(event.testRunId());
    theShopsEndpoint.receive(
        event.testRunId(),
        payload,
        signed(
            payload, openedRun.webhookSecret(), Instant.parse(openedRun.clock()).getEpochSecond()));
  }

  @Override
  public synchronized void reset(String testRunId) {
    if (refusesEveryReset) {
      throw new TheSimulatorRefused("reset " + testRunId, 500, "refused on purpose");
    }
    resets.add(testRunId);
    savedCards.remove(testRunId);
    captureRefusals.remove(testRunId);
    undelivered.removeIf(event -> event.testRunId().equals(testRunId));
  }

  @Override
  public synchronized void removeTheAccount(String testRunId, String accountId) {
    if (refusesToRemoveAnyAccount || refusesToRemoveTheAccountOf.contains(testRunId)) {
      throw new TheSimulatorRefused("remove the account of " + testRunId, 404, "not configured");
    }
    removedAccounts.add(new RemovedAccount(testRunId, accountId));
    opened.remove(testRunId);
  }

  void refusesTheNextEvent() {
    refusesTheNextEvent = true;
  }

  synchronized void refusesToRemoveTheAccountOf(String testRunId) {
    refusesToRemoveTheAccountOf.add(testRunId);
  }

  synchronized void agreesToRemoveEveryAccount() {
    refusesToRemoveTheAccountOf.clear();
  }

  void writesButDoesNotAnswer(SetUpStep step) {
    writesButDoesNotAnswer = step;
  }

  void refusesEveryReset() {
    refusesEveryReset = true;
  }

  void refusesToRemoveAnyAccount() {
    refusesToRemoveAnyAccount = true;
  }

  Pause pauseInsideTheProvider() {
    Pause armed = new Pause();
    pause = armed;
    return armed;
  }

  private void passThroughAnyPause() {
    Pause armed = pause;
    if (armed != null) {
      armed.passThrough();
    }
  }

  @Override
  public String placeAHold(StoryRun run, String orderId, Product product) {
    passThroughAnyPause();
    if (!run.savedCard().equals(savedCards.get(run.testRunId()))) {
      throw new ThePaymentProviderFailed(
          "place a hold", new IllegalStateException("no such payment method"));
    }
    String paymentIntentId = "pi_" + holdsPlaced.incrementAndGet();
    holds.put(paymentIntentId, run.testRunId());
    return paymentIntentId;
  }

  @Override
  public CaptureOutcome capture(StoryRun run, String paymentIntentId, long amountInMinorUnits) {
    passThroughAnyPause();
    capturesAsked.incrementAndGet();
    String refusal = captureRefusals.get(run.testRunId());
    if (refusal != null) {
      return new CaptureOutcome.Refused(refusal);
    }
    captured.put(paymentIntentId, amountInMinorUnits);
    return new CaptureOutcome.Captured();
  }

  static String signed(String payload, String secret, long timestamp) {
    try {
      return Webhook.Signature.generateSignatureHeader(payload, secret, timestamp);
    } catch (GeneralSecurityException unsignable) {
      throw new IllegalStateException(unsignable);
    }
  }

  Map<String, OpenedRun> opened() {
    return opened;
  }

  Map<String, String> savedCards() {
    return savedCards;
  }

  Map<String, String> captureRefusals() {
    return captureRefusals;
  }

  Map<String, String> holds() {
    return holds;
  }

  Map<String, Long> captured() {
    return captured;
  }

  int capturesAsked() {
    return capturesAsked.get();
  }

  synchronized List<SentEvent> sent() {
    return List.copyOf(sent);
  }

  synchronized List<String> resets() {
    return List.copyOf(resets);
  }

  synchronized List<RemovedAccount> removedAccounts() {
    return List.copyOf(removedAccounts);
  }
}
