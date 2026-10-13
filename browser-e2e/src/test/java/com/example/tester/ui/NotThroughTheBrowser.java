package com.example.tester.ui;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NotThroughTheBrowser {

  private static final String TEST_RUN_HEADER = "X-Simulator-Test-Run-Id";

  private static final String JSON = "application/json";

  private static final String CREATE_A_PAYMENT_PROCESS =
      "/api/light/server/1.0/createPaymentProcess";

  private static final String GET_PAYMENT_PROCESS_INFO =
      "/api/light/server/1.0/getPaymentProcessInfo";

  private static final Pattern THE_URL_GROW_HANDS_BACK =
      Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

  private static final String WORLDLINE_SIGNS_WITH = "GCS v1HMAC:";

  private static final Pattern A_WORLDLINE_FIELD =
      Pattern.compile("\"([A-Za-z]+)\"\\s*:\\s*\"([^\"]*)\"");

  public record HostedTokenization(String id, String url) {}

  public record GrowProcess(String id, String token, String url) {}

  private final String baseUrl;
  private final TheProviderApi providerApi;

  public NotThroughTheBrowser(String baseUrl, TheProviderApi providerApi) {
    this.baseUrl = baseUrl;
    this.providerApi = providerApi;
  }

  public String theHostedPageUrlGrowMints(String testRunId, Map<String, String> form) {
    return theHostedPaymentProcessGrowStarts(testRunId, form).url();
  }

  public GrowProcess theHostedPaymentProcessGrowStarts(String testRunId, Map<String, String> form) {
    HttpResponse<String> answer = growCall(testRunId, CREATE_A_PAYMENT_PROCESS, form);
    Matcher minted = THE_URL_GROW_HANDS_BACK.matcher(answer.body());
    if (answer.statusCode() != 200 || !minted.find()) {
      throw new IllegalStateException(
          "grow answered "
              + answer.statusCode()
              + " to createPaymentProcess and named no hosted page url: "
              + answer.body());
    }
    return new GrowProcess(
        theTextFieldIn(answer.body(), "processId"),
        theTextFieldIn(answer.body(), "processToken"),
        minted.group(1).replace("\\/", "/"));
  }

  public int whatGrowAnswersAnAppWhoseHeaderNamesARunNothingHolds(
      String theRunItNames, Map<String, String> form) {
    return growCall(theRunItNames, CREATE_A_PAYMENT_PROCESS, form).statusCode();
  }

  public String theProcessGrowReadsBack(String testRunId, String pageCode, GrowProcess process) {
    return growCall(
            testRunId,
            GET_PAYMENT_PROCESS_INFO,
            form(
                "pageCode", pageCode,
                "processId", process.id(),
                "processToken", process.token()))
        .body();
  }

  private HttpResponse<String> growCall(String testRunId, String route, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create(baseUrl + route))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header(TEST_RUN_HEADER, testRunId)
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form), StandardCharsets.UTF_8))
            .build());
  }

  public HostedTokenization theHostedTokenizationWorldlineOpens(
      String testRunId, String merchantId, String apiKey) {
    HttpResponse<String> answer =
        send(
            HttpRequest.newBuilder(
                    URI.create(baseUrl + "/v2/" + merchantId + "/hostedtokenizations"))
                .header("Content-Type", JSON)
                .header("Authorization", WORLDLINE_SIGNS_WITH + apiKey + ":unverified")
                .header(TEST_RUN_HEADER, testRunId)
                .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .build());
    Map<String, String> fields = theFieldsIn(answer.body());
    if (answer.statusCode() != 200 || !fields.containsKey("hostedTokenizationUrl")) {
      throw new IllegalStateException(
          "worldline answered "
              + answer.statusCode()
              + " to createHostedTokenization and named no hostedTokenizationUrl: "
              + answer.body());
    }
    return new HostedTokenization(
        fields.get("hostedTokenizationId"), fields.get("hostedTokenizationUrl"));
  }

  public String theHostedTokenizationWorldlineReadsBack(
      String testRunId, String merchantId, String apiKey, String hostedTokenizationId) {
    return send(HttpRequest.newBuilder(
                URI.create(
                    baseUrl + "/v2/" + merchantId + "/hostedtokenizations/" + hostedTokenizationId))
            .header("Authorization", WORLDLINE_SIGNS_WITH + apiKey + ":unverified")
            .header(TEST_RUN_HEADER, testRunId)
            .GET()
            .build())
        .body();
  }

  public String thePaymentWorldlineCreatesFrom(
      String testRunId, String merchantId, String apiKey, String hostedTokenizationId) {
    HttpResponse<String> answer =
        send(
            HttpRequest.newBuilder(URI.create(baseUrl + "/v2/" + merchantId + "/payments"))
                .header("Content-Type", JSON)
                .header("Authorization", WORLDLINE_SIGNS_WITH + apiKey + ":unverified")
                .header(TEST_RUN_HEADER, testRunId)
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"order\":{\"amountOfMoney\":{\"amount\":5000,"
                            + "\"currencyCode\":\"EUR\"}},\"hostedTokenizationId\":\""
                            + hostedTokenizationId
                            + "\",\"cardPaymentMethodSpecificInput\":"
                            + "{\"authorizationMode\":\"SALE\"}}",
                        StandardCharsets.UTF_8))
                .build());
    if (answer.statusCode() != 201) {
      throw new IllegalStateException(
          "worldline answered "
              + answer.statusCode()
              + " to a payment with the hosted tokenization: "
              + answer.body());
    }
    return answer.body();
  }

  private static Map<String, String> theFieldsIn(String json) {
    Map<String, String> fields = new LinkedHashMap<>();
    Matcher field = A_WORLDLINE_FIELD.matcher(json);
    while (field.find()) {
      fields.putIfAbsent(field.group(1), field.group(2).replace("\\/", "/"));
    }
    return fields;
  }

  public String anAutraAccessToken(String testRunId, String clientId, String clientSecret) {
    return theFieldAutraAnswered(
        "accessToken",
        HttpRequest.newBuilder(
                URI.create(baseUrl + "/v1/oauth/token?grant_type=client_credentials"))
            .header(TEST_RUN_HEADER, testRunId)
            .header(
                "Authorization",
                "Basic "
                    + Base64.getEncoder()
                        .encodeToString(
                            (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)))
            .POST(HttpRequest.BodyPublishers.noBody()));
  }

  public String theHostedCardUrlAutraMints(String testRunId, String accessToken, String document) {
    return autraCall(
        "hostedCardUrl",
        testRunId,
        accessToken,
        "/v1/acquiring/card-sessions",
        "{\"documentId\":\"" + document + "\"}");
  }

  public String theSaleAutraAnswers(
      String testRunId, String accessToken, String document, String slugToken) {
    HttpResponse<String> answer = anAutraSale(testRunId, accessToken, document, slugToken);
    if (answer.statusCode() != 200) {
      throw new IllegalStateException(
          "autra answered " + answer.statusCode() + " to a sale: " + answer.body());
    }
    return answer.body();
  }

  public String theCodeOfARefusedAutraSaleOf(
      String testRunId, String accessToken, String document, String slugToken) {
    HttpResponse<String> answer = anAutraSale(testRunId, accessToken, document, slugToken);
    return answer.statusCode() + " " + theTextFieldIn(answer.body(), "code");
  }

  private HttpResponse<String> anAutraSale(
      String testRunId, String accessToken, String document, String slugToken) {
    String transactionalToken =
        autraCall(
            "transactionalToken",
            testRunId,
            accessToken,
            "/v1/acquiring/payments/initialize",
            "{\"documentId\":\"" + document + "\"}");
    return send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/v1/acquiring/payments"))
            .header(TEST_RUN_HEADER, testRunId)
            .header("Content-Type", JSON)
            .header("Authorization", "Bearer " + accessToken)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"transactionalToken\":\""
                        + transactionalToken
                        + "\",\"documentId\":\""
                        + document
                        + "\",\"transactionType\":\"CREDIT\",\"amount\":150.00,"
                        + "\"currency\":\"BRL\",\"installments\":1,"
                        + "\"tokenData\":{\"slugToken\":\""
                        + slugToken
                        + "\"}}",
                    StandardCharsets.UTF_8))
            .build());
  }

  public static String theTextFieldIn(String json, String field) {
    Matcher named =
        Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
    if (!named.find()) {
      throw new IllegalStateException("no text field " + field + " in " + json);
    }
    return named.group(1).replace("\\/", "/");
  }

  private String autraCall(
      String field, String testRunId, String accessToken, String path, String body) {
    return theFieldAutraAnswered(
        field,
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header(TEST_RUN_HEADER, testRunId)
            .header("Content-Type", JSON)
            .header("Authorization", "Bearer " + accessToken)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
  }

  private String theFieldAutraAnswered(String field, HttpRequest.Builder request) {
    HttpResponse<String> answer = send(request.build());
    if (answer.statusCode() != 200) {
      throw new IllegalStateException(
          "autra answered "
              + answer.statusCode()
              + " to "
              + request.build().uri()
              + ": "
              + answer.body());
    }
    return theTextFieldIn(answer.body(), field);
  }

  public static Map<String, String> form(String... pairs) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (int at = 0; at < pairs.length; at += 2) {
      fields.put(pairs[at], pairs[at + 1]);
    }
    return fields;
  }

  private static String encoded(Map<String, String> form) {
    StringBuilder body = new StringBuilder();
    for (Map.Entry<String, String> field : form.entrySet()) {
      if (!body.isEmpty()) {
        body.append('&');
      }
      body.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
    }
    return body.toString();
  }

  private HttpResponse<String> send(HttpRequest request) {
    return providerApi.send(request);
  }
}
