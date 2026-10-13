package com.example.checkout.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class GrowShop implements Shop {

  private static final String API = "/api/light/server/1.0";

  private static final String ACCOUNT = "our-shop";

  private static final String THE_CARD_THE_PAYER_PICKS = "sandbox-4580";

  private final Http http;
  private final Control control;
  private final MerchantEndpoint endpoint;
  private final String userId;
  private final String pageCode;
  private final Map<String, String> tokenOf = new LinkedHashMap<>();
  private String processId = "";
  private String processToken = "";

  GrowShop(String baseUrl, String testRunId, MerchantEndpoint endpoint) {
    this.http = new Http(baseUrl);
    this.control = new Control(baseUrl, "grow", testRunId);
    this.endpoint = endpoint;
    this.userId = "user-" + testRunId;
    this.pageCode = "page-" + testRunId;
  }

  @Override
  public String providerName() {
    return "Grow";
  }

  @Override
  public Control control() {
    return control;
  }

  @Override
  public void opensForBusiness(String clockPinnedTo, String callbackUrl) {
    control.configure(
        Map.of("userId", userId, "pageCode", pageCode, "captureWindow", "P7D"),
        ACCOUNT,
        clockPinnedTo,
        callbackUrl);
  }

  void acknowledgesCallbacksWithASeparateCall(String clockPinnedTo, String callbackUrl) {
    control.configure(
        Map.of(
            "userId",
            userId,
            "pageCode",
            pageCode,
            "captureWindow",
            "P7D",
            "callbackAcknowledgement",
            "APPROVE_TRANSACTION"),
        ACCOUNT,
        clockPinnedTo,
        callbackUrl);
  }

  void answersInTheFormItsPublishedExampleShows(String clockPinnedTo, String callbackUrl) {
    control.configure(
        Map.of(
            "userId",
            userId,
            "pageCode",
            pageCode,
            "captureWindow",
            "P7D",
            "wireForm",
            "PUBLISHED_EXAMPLE"),
        ACCOUNT,
        clockPinnedTo,
        callbackUrl);
  }

  @Override
  public void tidiesUp() {
    control.tidyUp(ACCOUNT);
  }

  String theProcessThePayerWasSentTo() {
    return processId;
  }

  @Override
  public String aPayerAuthorisesAHoldOf(String amount) {
    aPayerPaysForAHostedPaymentOf(amount);
    return theTransactionTheCallbackNamed();
  }

  void aPayerPaysForAHostedPaymentOf(String amount) {
    String link = theShopStartsAHostedPaymentOf(amount);
    thePayerOpensTheirPage(link);
    thePayerPaysWith(link, THE_CARD_THE_PAYER_PICKS);
  }

  String theContentTypeOfItsAnswers(String amount) {
    Map<String, String> request = new LinkedHashMap<>();
    request.put("pageCode", pageCode);
    request.put("userId", userId);
    request.put("chargeType", "2");
    request.put("sum", amount);
    request.put("description", "One boxed set");
    request.put("successUrl", "https://our-shop.example/thanks");
    request.put("cancelUrl", "https://our-shop.example/basket");
    request.put("notifyUrl", endpoint.url());
    request.put("pageField[fullName]", "Dana Cohen");
    request.put("pageField[phone]", "0505503144");
    Http.Answer answered = http.postForm(API + "/createPaymentProcess", request, ourTestRun());
    envelopeMustSucceed(answered, "start a hosted payment");
    return answered.contentType();
  }

  Reconciled theShopLooksThePaymentProcessUp() {
    Http.Answer answered =
        http.postForm(
            API + "/getPaymentProcessInfo",
            Map.of("pageCode", pageCode, "processId", processId, "processToken", processToken),
            ourTestRun());
    envelopeMustSucceed(answered, "look the payment process up");
    return new Reconciled(
        answered.text("data.transactions.0.statusCode"), answered.text("data.transactions.0.sum"));
  }

  String theShopStartsAHostedPaymentOf(String amount) {
    Map<String, String> request = new LinkedHashMap<>();
    request.put("pageCode", pageCode);
    request.put("userId", userId);
    request.put("chargeType", "2");
    request.put("sum", amount);
    request.put("saveCardToken", "1");
    request.put("description", "One boxed set");
    request.put("successUrl", "https://our-shop.example/thanks");
    request.put("cancelUrl", "https://our-shop.example/basket");
    request.put("notifyUrl", endpoint.url());
    request.put("pageField[fullName]", "Dana Cohen");
    request.put("pageField[phone]", "0505503144");
    request.put("cField1", "basket-" + amount.replace('.', '-'));
    request.put("cField2", "order-" + amount.replace('.', '-'));
    Http.Answer answered = http.postForm(API + "/createPaymentProcess", request, ourTestRun());
    envelopeMustSucceed(answered, "start a hosted payment");
    processId = answered.text("data.processId");
    processToken = answered.text("data.processToken");
    return linkOf(answered.text("data.url"));
  }

  Http.Answer thePayerOpensTheirPage(String link) {
    Http.Answer page = http.get("/far?l=" + link);
    if (page.status() != 200) {
      throw new IllegalStateException("the payer's page answered " + page.preview());
    }
    return page;
  }

  Http.Answer thePayerPaysWith(String link, String card) {
    return http.postForm("/far", Map.of("l", link, "outcome", "PAID", "card", card), Map.of());
  }

  Http.Answer thePayerWalksAwayFrom(String link) {
    return http.postForm("/far", Map.of("l", link, "outcome", "ABANDONED"), Map.of());
  }

  String theTransactionTheCallbackNamed() {
    MerchantEndpoint.Received callback = endpoint.everythingReceived().getLast();
    String transactionId =
        callback
            .field("data[transactionId]")
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "the callback named no transaction: " + callback.body()));
    tokenOf.put(
        transactionId,
        callback
            .field("data[transactionToken]")
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "the callback carried no transaction token, which is the only thing that"
                            + " authorises a settlement: "
                            + callback.body())));
    return transactionId;
  }

  @Override
  public Answer captures(String paymentId, String amount) {
    Map<String, String> request =
        Map.of(
            "userId", userId,
            "transactionId", paymentId,
            "transactionToken", tokenOf.getOrDefault(paymentId, ""),
            "sum", amount);
    try {
      return envelopeOf(http.postForm(API + "/settleSuspendedTransaction", request, ourTestRun()));
    } catch (Http.TransportBroke nothingCameBack) {
      return Answer.noAnswerAtAll(nothingCameBack.getMessage());
    }
  }

  @Override
  public Answer refunds(String paymentId, String amount) {
    Map<String, String> request =
        Map.of(
            "userId", userId,
            "transactionId", paymentId,
            "transactionToken", tokenOf.getOrDefault(paymentId, ""),
            "refundSum", amount);
    try {
      return envelopeOf(http.postForm(API + "/refundTransaction", request, ourTestRun()));
    } catch (Http.TransportBroke nothingCameBack) {
      return Answer.noAnswerAtAll(nothingCameBack.getMessage());
    }
  }

  String theCodeForASecondRefund() {
    return "210";
  }

  String theCodeForAPartialRefundOnTheDayOfTheCharge() {
    return "130";
  }

  @Override
  public Reconciled reconciles(String paymentId) {
    Http.Answer answered =
        http.postForm(
            API + "/getTransactionInfo",
            Map.of(
                "pageCode", pageCode,
                "transactionId", paymentId,
                "transactionToken", tokenOf.getOrDefault(paymentId, "")),
            ourTestRun());
    envelopeMustSucceed(answered, "look the transaction up");
    return new Reconciled(answered.text("data.statusCode"), answered.text("data.sum"));
  }

  void acknowledgesTheCallbackOf(String paymentId) {
    Http.Answer answered =
        http.postForm(
            API + "/approveTransaction",
            Map.of(
                "pageCode", pageCode,
                "transactionId", paymentId,
                "transactionToken", tokenOf.getOrDefault(paymentId, "")),
            ourTestRun());
    envelopeMustSucceed(answered, "acknowledge the callback");
  }

  @Override
  public String theCodeFor(Refusal refusal) {
    return switch (refusal) {
      case AN_EXPIRED_CARD -> "103";
      case AN_AUTHORISATION_PAST_ITS_WINDOW -> "803";
    };
  }

  @Override
  public String theScenarioNameFor(Refusal refusal) {
    return switch (refusal) {
      case AN_EXPIRED_CARD -> "expiredCard";
      case AN_AUTHORISATION_PAST_ITS_WINDOW -> "expiredCard";
    };
  }

  @Override
  public String theSimulatorsWordFor(String amount) {
    return amount;
  }

  @Override
  public String theOperationThatCaptures() {
    return "settleSuspendedTransaction";
  }

  @Override
  public String theEventOfAFinishedPayment() {
    return "payment_process.completed";
  }

  private Map<String, String> ourTestRun() {
    return Map.of("X-Simulator-Test-Run-Id", control.testRunId());
  }

  private Answer envelopeOf(Http.Answer answered) {
    if (answered.status() != 200) {
      return Answer.serverError(answered.preview());
    }
    JsonNode status = answered.at("status");
    if (status != null && status.asInt() == 1) {
      return Answer.accepted(answered.contentType(), answered.body());
    }
    return Answer.refused(
        Optional.ofNullable(answered.at("err.id")).map(JsonNode::asText).orElse("<none>"),
        answered.contentType(),
        answered.body());
  }

  private void envelopeMustSucceed(Http.Answer answered, String what) {
    Answer envelope = envelopeOf(answered);
    if (envelope.outcome() != Outcome.ACCEPTED) {
      throw new IllegalStateException("Grow would not " + what + ": " + answered.preview());
    }
  }

  private static String linkOf(String url) {
    String query = URI.create(url).getQuery();
    for (String pair : query.split("&")) {
      if (pair.startsWith("l=")) {
        return pair.substring(2);
      }
    }
    throw new IllegalStateException("the hosted page url carried no link: " + url);
  }
}
