package com.example.tester.ui;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

public final class TheTestSetup {

  private final String baseUrl;

  public TheTestSetup(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public void configureAGrowAccount(
      String testRunId,
      String accountId,
      String userId,
      String pageCode,
      List<String> callbackTargets) {
    configure(
        "grow",
        "{\"testRunId\":\""
            + testRunId
            + "\",\"accounts\":[{\"accountId\":\""
            + accountId
            + "\",\"configuration\":{\"userId\":\""
            + userId
            + "\",\"pageCode\":\""
            + pageCode
            + "\"}}],\"webhooks\":"
            + targets(accountId, callbackTargets)
            + "}");
  }

  public void configureAWorldlineAccount(
      String testRunId, String accountId, String merchantId, String apiKey) {
    configure(
        "worldline",
        "{\"testRunId\":\""
            + testRunId
            + "\",\"accounts\":[{\"accountId\":\""
            + accountId
            + "\",\"configuration\":{\"merchantId\":\""
            + merchantId
            + "\",\"apiKey\":\""
            + apiKey
            + "\",\"apiSecret\":\"a-secret-the-shop-never-shows\","
            + "\"worldline.authentication\":\"THE_SIGNATURE_IS_NOT_VERIFIED\"}}],"
            + "\"webhooks\":[]}");
  }

  public void configureAnAutraMerchant(
      String testRunId, String accountId, String clientId, String clientSecret, String document) {
    configure(
        "autra",
        "{\"testRunId\":\""
            + testRunId
            + "\",\"accounts\":[{\"accountId\":\""
            + accountId
            + "\",\"configuration\":{\"clientId\":\""
            + clientId
            + "\",\"clientSecret\":\""
            + clientSecret
            + "\",\"documentId\":\""
            + document
            + "\"}}]}");
  }

  public void configureAnAzericardTerminal(
      String testRunId,
      String accountId,
      String terminal,
      String theShopsPublicKey,
      String theGatewaysPrivateKey,
      List<String> callbackTargets) {
    configure(
        "azericard",
        "{\"testRunId\":\""
            + testRunId
            + "\",\"accounts\":[{\"accountId\":\""
            + accountId
            + "\",\"configuration\":{\"terminalId\":\""
            + terminal
            + "\",\"apiKey\":\""
            + json(theShopsPublicKey)
            + "\",\"apiSecret\":\""
            + json(theGatewaysPrivateKey)
            + "\"}}],\"webhooks\":"
            + targets(accountId, callbackTargets)
            + "}");
  }

  public void forgetTheTestRunAndItsAccount(String provider, String testRunId, String accountId) {
    send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/__simulator/" + provider + "/reset"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"testRunId\":\"" + testRunId + "\"}", StandardCharsets.UTF_8))
            .build());
    send(
        HttpRequest.newBuilder(
                URI.create(
                    baseUrl
                        + "/__simulator/"
                        + provider
                        + "/config/accounts/"
                        + accountId
                        + "?testRunId="
                        + URLEncoder.encode(testRunId, StandardCharsets.UTF_8)))
            .DELETE()
            .build());
  }

  private void configure(String provider, String body) {
    HttpResponse<String> answer =
        send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/__simulator/" + provider + "/config"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build());
    if (answer.statusCode() / 100 != 2) {
      throw new IllegalStateException(
          "the simulator answered "
              + answer.statusCode()
              + " to configuring a "
              + provider
              + " account: "
              + answer.body());
    }
  }

  private static String targets(String accountId, List<String> callbackTargets) {
    return callbackTargets.stream()
        .map(url -> "{\"accountId\":\"" + accountId + "\",\"url\":\"" + url + "\"}")
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static String json(String pem) {
    return pem.replace("\n", "\\n");
  }

  private static HttpResponse<String> send(HttpRequest request) {
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException unreachable) {
      throw new IllegalStateException(
          "the simulator did not answer " + request.method() + " " + request.uri(), unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while calling " + request.uri());
    }
  }
}
