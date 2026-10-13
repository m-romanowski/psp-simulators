package com.example.shop;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StoryRun {

  public static final String ACCOUNT = "demo-shop";

  private static final SecureRandom RANDOM = new SecureRandom();

  private static final int SECRET_BYTES = 24;

  private final String testRunId;
  private final Story story;
  private final Instant clockPinnedTo;
  private final String secretKey = "sk_test_" + randomHex();
  private final String webhookSecret = "whsec_" + randomHex();
  private final Map<String, ReceivedEvent> received =
      Collections.synchronizedMap(new LinkedHashMap<>());
  private volatile Order order;
  private volatile String captureRefusalRule;
  private volatile boolean stripeAskedToReport;

  StoryRun(String testRunId, Story story, Instant clockPinnedTo) {
    this.testRunId = testRunId;
    this.story = story;
    this.clockPinnedTo = clockPinnedTo;
  }

  public String testRunId() {
    return testRunId;
  }

  public Story story() {
    return story;
  }

  public Instant clockPinnedTo() {
    return clockPinnedTo;
  }

  public Clock theClockTheSimulatorKeepsForIt() {
    return Clock.fixed(clockPinnedTo, ZoneOffset.UTC);
  }

  public String secretKey() {
    return secretKey;
  }

  public String webhookSecret() {
    return webhookSecret;
  }

  public String savedCard() {
    return "pm_" + compact();
  }

  public Optional<Order> order() {
    return Optional.ofNullable(order);
  }

  public Optional<String> captureRefusalRule() {
    return Optional.ofNullable(captureRefusalRule);
  }

  public List<ReceivedEvent> received() {
    synchronized (received) {
      return List.copyOf(received.values());
    }
  }

  public boolean bought() {
    return order != null;
  }

  public boolean captureAnswered() {
    Order placed = order;
    return placed != null && !placed.awaitsCapture();
  }

  public boolean stripeAskedToReport() {
    return stripeAskedToReport;
  }

  public boolean awaitsStripe() {
    return stripeAskedToReport && !toldByStripe();
  }

  public boolean toldByStripe() {
    return !received.isEmpty();
  }

  public boolean ended() {
    return captureAnswered() && (!story.endsWithAWebhook() || toldByStripe());
  }

  synchronized Order ordered(Order placed) {
    if (order == null) {
      order = placed;
    }
    return order;
  }

  void captureRefusalRuleWritten(String scenarioId) {
    captureRefusalRule = scenarioId;
  }

  void stripeWasAskedToReport() {
    stripeAskedToReport = true;
  }

  void receivedAn(ReceivedEvent event) {
    received.putIfAbsent(event.eventId(), event);
  }

  private static String randomHex() {
    byte[] secret = new byte[SECRET_BYTES];
    RANDOM.nextBytes(secret);
    return HexFormat.of().formatHex(secret);
  }

  private String compact() {
    return testRunId.replaceAll("[^A-Za-z0-9]", "");
  }
}
