package com.example.checkout.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class Control {

  private final Http http;
  private final String provider;
  private final String testRunId;

  Control(String baseUrl, String provider, String testRunId) {
    this.http = new Http(baseUrl);
    this.provider = provider;
    this.testRunId = testRunId;
  }

  String testRunId() {
    return testRunId;
  }

  void configure(
      Map<String, String> account, String accountId, String clockPinnedTo, String callbackUrl) {
    Object webhooks =
        callbackUrl == null
            ? List.of()
            : List.of(Http.fields("accountId", accountId, "url", callbackUrl));
    Http.Answer answered =
        http.putJson(
            control("/config"),
            Http.jsonOf(
                Http.fields(
                    "testRunId",
                    testRunId,
                    "clock",
                    Http.fields("fixedNow", clockPinnedTo),
                    "accounts",
                    List.of(Http.fields("accountId", accountId, "configuration", account)),
                    "webhooks",
                    webhooks)));
    expect(answered, 200, "configure the " + provider + " account " + accountId);
  }

  void applySettings(Map<String, String> settings) {
    expect(
        http.putJson(
            control("/config"),
            Http.jsonOf(Http.fields("testRunId", testRunId, "settings", settings))),
        200,
        "apply the settings " + settings);
  }

  String writeRule(Map<String, Object> match, Map<String, Object> action, int priority) {
    Http.Answer answered =
        http.postJson(
            control("/scenarios"),
            Http.jsonOf(
                Http.fields(
                    "testRunId", testRunId,
                    "priority", priority,
                    "match", match,
                    "action", action)));
    expect(answered, 201, "write a scenario rule");
    return answered.text("scenarioId");
  }

  List<String> expandPreset(String preset, int priority) {
    Http.Answer answered =
        http.postJson(
            control("/scenarios/presets"),
            Http.jsonOf(
                Http.fields("testRunId", testRunId, "preset", preset, "priority", priority)));
    expect(answered, 201, "expand the preset " + preset);
    JsonNode rules = answered.at("rules");
    return rules == null
        ? List.of()
        : rules.valueStream().map(rule -> rule.get("scenarioId").asText()).toList();
  }

  String advanceClockBy(String duration) {
    Http.Answer answered =
        http.postJson(
            control("/clock/advance"),
            Http.jsonOf(Http.fields("testRunId", testRunId, "advanceBy", duration)));
    expect(answered, 200, "advance the clock by " + duration);
    return answered.text("now");
  }

  Http.Answer completeHostedPage(String providerObjectId, String outcome, String card) {
    return http.postJson(
        control("/hosted/" + providerObjectId + "/complete"),
        Http.jsonOf(Http.fields("testRunId", testRunId, "outcome", outcome, "card", card)));
  }

  Http.Answer stateOf(String providerObjectId) {
    return http.get(control("/state/" + providerObjectId) + "?testRunId=" + testRunId);
  }

  String normalizedStatusOf(String providerObjectId) {
    Http.Answer answered = stateOf(providerObjectId);
    expect(answered, 200, "read the state of " + providerObjectId);
    return answered.text("normalizedStatus");
  }

  String capturedAmountOf(String providerObjectId) {
    Http.Answer answered = stateOf(providerObjectId);
    expect(answered, 200, "read the state of " + providerObjectId);
    return answered.text("derivedBalances.capturedAmount");
  }

  boolean aCaptureWouldBeTakenOn(String providerObjectId) {
    Http.Answer answered = stateOf(providerObjectId);
    expect(answered, 200, "read the state of " + providerObjectId);
    JsonNode said = answered.at("capturesAccepted");
    if (said == null || !said.isBoolean()) {
      throw new IllegalStateException(
          "the state of "
              + providerObjectId
              + " carries no capturesAccepted: "
              + answered.preview());
    }
    return said.booleanValue();
  }

  String capturableRemainderOf(String providerObjectId) {
    Http.Answer answered = stateOf(providerObjectId);
    expect(answered, 200, "read the state of " + providerObjectId);
    return answered.text("derivedBalances.capturableRemainder");
  }

  boolean aRefundWouldBeReturnedOn(String providerObjectId) {
    Http.Answer answered = stateOf(providerObjectId);
    expect(answered, 200, "read the state of " + providerObjectId);
    JsonNode said = answered.at("refundsAccepted");
    if (said == null || !said.isBoolean()) {
      throw new IllegalStateException(
          "the state of "
              + providerObjectId
              + " carries no refundsAccepted: "
              + answered.preview());
    }
    return said.booleanValue();
  }

  String refundableRemainderOf(String providerObjectId) {
    Http.Answer answered = stateOf(providerObjectId);
    expect(answered, 200, "read the state of " + providerObjectId);
    return answered.text("derivedBalances.refundableRemainder");
  }

  JsonNode deliveries() {
    Http.Answer answered = http.get(control("/webhooks") + "?testRunId=" + testRunId);
    expect(answered, 200, "read the callback deliveries");
    return answered.json();
  }

  JsonNode theOnlyDelivery() {
    JsonNode all = deliveries();
    if (all.size() != 1) {
      throw new AssertionError("expected one queued callback and the run holds " + all.size());
    }
    return all.get(0);
  }

  String sendCallbackAbout(String providerObjectId, String eventType, String reuseEventId) {
    Map<String, Object> body =
        Http.fields(
            "testRunId", testRunId,
            "providerObjectId", providerObjectId,
            "eventType", eventType);
    if (reuseEventId != null) {
      body.put("reuseEventId", reuseEventId);
    }
    Http.Answer answered = http.postJson(control("/webhooks"), Http.jsonOf(body));
    expect(answered, 201, "send a callback about " + providerObjectId);
    return answered.text("webhookJobId");
  }

  void retryDelivery(String webhookJobId) {
    expect(
        http.postJson(
            control("/webhooks"),
            Http.jsonOf(
                Http.fields(
                    "testRunId", testRunId, "operation", "RETRY", "webhookJobId", webhookJobId))),
        200,
        "retry the delivery " + webhookJobId);
  }

  int deliverWhateverIsDue() {
    Http.Answer answered =
        http.postJson(
            control("/webhooks/deliver-due"), Http.jsonOf(Http.fields("testRunId", testRunId)));
    expect(answered, 200, "deliver whatever has become due");
    return answered.at("attempted").asInt();
  }

  JsonNode trail() {
    Http.Answer answered = http.get(control("/audit") + "?testRunId=" + testRunId + "&limit=500");
    expect(answered, 200, "read the trail");
    return answered.json();
  }

  Optional<String> theRuleThatDecided(String path) {
    for (JsonNode row : trail()) {
      JsonNode scenario = row.get("scenarioId");
      if (scenario != null && !scenario.isNull() && row.get("path").asText().endsWith(path)) {
        return Optional.of(scenario.asText());
      }
    }
    return Optional.empty();
  }

  String seedCard(String accountId, String alias, Map<String, Object> providerIds) {
    Http.Answer answered =
        http.postJson(
            control("/fixtures/payment-methods"),
            Http.jsonOf(
                Http.fields(
                    "testRunId", testRunId,
                    "merchantId", accountId,
                    "ownerId", "shopper-" + alias,
                    "type", "card",
                    "alias", alias,
                    "card",
                        Http.fields(
                            "brand", "visa",
                            "last4", "4242",
                            "expMonth", 12,
                            "expYear", 2030,
                            "funding", "credit"),
                    "providerIds", providerIds)));
    expect(answered, 201, "seed the card " + alias);
    return answered.text("fixtureId");
  }

  void tidyUp(String accountId) {
    http.postJson(control("/reset"), Http.jsonOf(Http.fields("testRunId", testRunId)));
    http.delete(control("/config/accounts/" + accountId) + "?testRunId=" + testRunId);
  }

  private String control(String path) {
    return "/__simulator/" + provider + path;
  }

  private static void expect(Http.Answer answered, int status, String what) {
    if (answered.status() != status) {
      throw new IllegalStateException(
          "could not " + what + ": expected HTTP " + status + " and read " + answered.preview());
    }
  }
}
