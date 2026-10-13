package com.example.shop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
class SimulatorControlOverHttp implements SimulatorControl {

  private static final String CONTROL = "/__simulator/stripe";

  private static final String THE_CAPTURE_OPERATION = "POST /v1/payment_intents/{id}/capture";

  private final RestClient http;

  SimulatorControlOverHttp(ShopSettings settings) {
    SimpleClientHttpRequestFactory withTimeouts = new SimpleClientHttpRequestFactory();
    withTimeouts.setConnectTimeout(settings.simulatorConnectTimeout());
    withTimeouts.setReadTimeout(settings.simulatorReadTimeout());
    this.http =
        RestClient.builder()
            .baseUrl(settings.simulatorApiUrl())
            .requestFactory(withTimeouts)
            .build();
  }

  @Override
  public void openTheRun(StoryRun run, String callbackUrl) {
    Map<String, Object> configuration = new LinkedHashMap<>();
    configuration.put("testRunId", run.testRunId());
    configuration.put("clock", Map.of("fixedNow", run.clockPinnedTo().toString()));
    configuration.put(
        "accounts",
        List.of(
            Map.of(
                "accountId",
                StoryRun.ACCOUNT,
                "configuration",
                Map.of(
                    "secretKey", run.secretKey(),
                    "captureWindow", "P7D",
                    "webhookSecret", run.webhookSecret()))));
    configuration.put(
        "webhooks", List.of(Map.of("accountId", StoryRun.ACCOUNT, "url", callbackUrl)));
    expect(
        http.put()
            .uri(CONTROL + "/config")
            .contentType(MediaType.APPLICATION_JSON)
            .body(configuration),
        Set.of(200),
        "configure the Stripe account of " + run.testRunId());
  }

  @Override
  public void seedTheSavedCard(StoryRun run) {
    Map<String, Object> fixture = new LinkedHashMap<>();
    fixture.put("testRunId", run.testRunId());
    fixture.put("merchantId", StoryRun.ACCOUNT);
    fixture.put("ownerId", "the-shopper");
    fixture.put("type", "card");
    fixture.put("alias", "the-shoppers-visa");
    fixture.put(
        "card",
        Map.of(
            "brand", "visa",
            "last4", "4242",
            "expMonth", 12,
            "expYear", 2030,
            "funding", "credit"));
    fixture.put("providerIds", Map.of("paymentMethodId", run.savedCard()));
    expect(
        http.post()
            .uri(CONTROL + "/fixtures/payment-methods")
            .contentType(MediaType.APPLICATION_JSON)
            .body(fixture),
        Set.of(201),
        "seed the saved card of " + run.testRunId());
  }

  @Override
  public String refuseEveryCapture(String testRunId, String providerCode) {
    Map<String, Object> rule = new LinkedHashMap<>();
    rule.put("testRunId", testRunId);
    rule.put("priority", 100);
    rule.put("match", Map.of("operation", THE_CAPTURE_OPERATION));
    rule.put("action", Map.of("type", "TERMINAL_FAILURE", "providerCode", providerCode));
    String what = "write the rule refusing every capture of " + testRunId;
    try {
      return http.post()
          .uri(CONTROL + "/scenarios")
          .contentType(MediaType.APPLICATION_JSON)
          .body(rule)
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                if (status != 201) {
                  throw new TheSimulatorRefused(what, status, textOf(response));
                }
                ScenarioWritten written = response.bodyTo(ScenarioWritten.class);
                if (written == null || written.scenarioId() == null) {
                  throw new TheSimulatorRefused(what, status, "an answer naming no scenarioId");
                }
                return written.scenarioId();
              });
    } catch (ResourceAccessException unreachable) {
      throw new TheSimulatorDidNotAnswer(what, unreachable);
    }
  }

  @Override
  public void sendTheEvent(String testRunId, String paymentIntentId, String eventType) {
    expect(
        http.post()
            .uri(CONTROL + "/webhooks")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "testRunId", testRunId,
                    "providerObjectId", paymentIntentId,
                    "eventType", eventType)),
        Set.of(201),
        "send " + eventType + " about " + paymentIntentId);
  }

  @Override
  public void reset(String testRunId) {
    expect(
        http.post()
            .uri(CONTROL + "/reset")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", testRunId)),
        Set.of(200),
        "reset " + testRunId);
  }

  @Override
  public void removeTheAccount(String testRunId, String accountId) {
    expect(
        http.delete()
            .uri(
                uri ->
                    uri.path(CONTROL + "/config/accounts/{accountId}")
                        .queryParam("testRunId", testRunId)
                        .build(accountId)),
        Set.of(200),
        "remove the account of " + testRunId);
  }

  private static void expect(
      RestClient.RequestHeadersSpec<?> call, Set<Integer> accepted, String what) {
    try {
      call.exchange(
          (request, response) -> {
            int status = response.getStatusCode().value();
            if (!accepted.contains(status)) {
              throw new TheSimulatorRefused(what, status, textOf(response));
            }
            return status;
          });
    } catch (ResourceAccessException unreachable) {
      throw new TheSimulatorDidNotAnswer(what, unreachable);
    }
  }

  private static String textOf(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
      throws IOException {
    return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ScenarioWritten(String scenarioId) {}
}
