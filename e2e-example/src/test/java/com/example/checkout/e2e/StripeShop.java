package com.example.checkout.e2e;

import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.net.StripeResponse;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import java.math.BigDecimal;
import java.util.Map;

final class StripeShop implements Shop {

  private static final String ACCOUNT = "our-shop";

  private static final String CURRENCY = "usd";

  private final Control control;
  private final StripeClient client;
  private final TranscriptOfTheVendorClient transcript;
  private final String secretKey;
  private final String savedCard;
  private String customerId = "";
  private StripeResponse lastResponse;

  StripeShop(String baseUrl, String testRunId) {
    this.control = new Control(baseUrl, "stripe", testRunId);
    this.secretKey = "sk_test_" + testRunId.replace("-", "");
    this.savedCard = "pm_" + testRunId.replace("-", "");
    this.transcript = new TranscriptOfTheVendorClient();
    this.client =
        StripeClient.builder()
            .setApiKey(secretKey)
            .setApiBase(baseUrl)
            .setHttpClient(transcript)
            .setMaxNetworkRetries(0)
            .build();
  }

  @Override
  public String providerName() {
    return "Stripe";
  }

  @Override
  public Control control() {
    return control;
  }

  StripeClient vendorClient() {
    return client;
  }

  TranscriptOfTheVendorClient transcript() {
    return transcript;
  }

  String theCardTheShopperSaved() {
    return savedCard;
  }

  String theShopper() {
    return customerId;
  }

  @Override
  public void opensForBusiness(String clockPinnedTo, String callbackUrl) {
    control.configure(
        Map.of(
            "secretKey",
            secretKey,
            "captureWindow",
            "P7D",
            "webhookSecret",
            "whsec_" + secretKey.substring(8)),
        ACCOUNT,
        clockPinnedTo,
        callbackUrl);
    control.seedCard(ACCOUNT, "the-shoppers-visa", Map.of("paymentMethodId", savedCard));
  }

  @Override
  public void tidiesUp() {
    control.tidyUp(ACCOUNT);
  }

  @Override
  public String aPayerAuthorisesAHoldOf(String amount) {
    try {
      Customer shopper =
          client
              .v1()
              .customers()
              .create(CustomerCreateParams.builder().setEmail("dana@example.test").build());
      customerId = shopper.getId();
      SetupIntent setup =
          client
              .v1()
              .setupIntents()
              .create(
                  SetupIntentCreateParams.builder()
                      .setCustomer(customerId)
                      .setPaymentMethod(savedCard)
                      .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                      .build());
      client.v1().setupIntents().confirm(setup.getId());
      PaymentIntent hold =
          client
              .v1()
              .paymentIntents()
              .create(
                  PaymentIntentCreateParams.builder()
                      .setAmount(minorUnits(amount))
                      .setCurrency(CURRENCY)
                      .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                      .setCustomer(customerId)
                      .setPaymentMethod(savedCard)
                      .setConfirm(true)
                      .setOffSession(true)
                      .build());
      return hold.getId();
    } catch (StripeException refused) {
      throw new IllegalStateException(
          "Stripe would not authorise the hold: " + refused.getMessage(), refused);
    }
  }

  @Override
  public Answer captures(String paymentId, String amount) {
    return captures(paymentId, amount, RequestOptions.builder().build());
  }

  Answer captures(String paymentId, String amount, RequestOptions options) {
    PaymentIntentCaptureParams params =
        PaymentIntentCaptureParams.builder().setAmountToCapture(minorUnits(amount)).build();
    try {
      PaymentIntent captured = client.v1().paymentIntents().capture(paymentId, params, options);
      lastResponse = captured.getLastResponse();
      return Answer.accepted(transcript.contentTypeOfTheLastAnswer(), lastResponse.body());
    } catch (ApiConnectionException nothingCameBack) {
      return Answer.noAnswerAtAll(nothingCameBack.getMessage());
    } catch (StripeException refused) {
      Integer status = refused.getStatusCode();
      if (status != null && status >= 500) {
        return Answer.serverError(refused.getMessage());
      }
      return Answer.refused(
          refused.getCode(), transcript.contentTypeOfTheLastAnswer(), refused.getMessage());
    }
  }

  @Override
  public Answer refunds(String paymentId, String amount) {
    RefundCreateParams params =
        RefundCreateParams.builder()
            .setPaymentIntent(paymentId)
            .setAmount(minorUnits(amount))
            .build();
    try {
      Refund returned = client.v1().refunds().create(params);
      return Answer.accepted(
          transcript.contentTypeOfTheLastAnswer(), returned.getLastResponse().body());
    } catch (ApiConnectionException nothingCameBack) {
      return Answer.noAnswerAtAll(nothingCameBack.getMessage());
    } catch (StripeException refused) {
      Integer status = refused.getStatusCode();
      if (status != null && status >= 500) {
        return Answer.serverError(refused.getMessage());
      }
      return Answer.refused(
          refused.getCode(), transcript.contentTypeOfTheLastAnswer(), refused.getMessage());
    }
  }

  java.util.Optional<String> theLastAnswersHeader(String name) {
    return lastResponse == null
        ? java.util.Optional.empty()
        : lastResponse.headers().firstValue(name);
  }

  @Override
  public Reconciled reconciles(String paymentId) {
    try {
      PaymentIntent intent = client.v1().paymentIntents().retrieve(paymentId);
      return new Reconciled(intent.getStatus(), majorUnits(intent.getAmountReceived()));
    } catch (StripeException unreadable) {
      throw new IllegalStateException(
          "Stripe would not answer a lookup of " + paymentId, unreadable);
    }
  }

  @Override
  public String theCodeFor(Refusal refusal) {
    return switch (refusal) {
      case AN_EXPIRED_CARD -> "expired_card";
      case AN_AUTHORISATION_PAST_ITS_WINDOW -> "charge_expired_for_capture";
    };
  }

  @Override
  public String theScenarioNameFor(Refusal refusal) {
    return switch (refusal) {
      case AN_EXPIRED_CARD -> "expired_card";
      case AN_AUTHORISATION_PAST_ITS_WINDOW -> "expired_card";
    };
  }

  @Override
  public String theSimulatorsWordFor(String amount) {
    return Long.toString(minorUnits(amount));
  }

  @Override
  public String theOperationThatCaptures() {
    return "POST /v1/payment_intents/{id}/capture";
  }

  @Override
  public String theEventOfAFinishedPayment() {
    return "payment_intent.succeeded";
  }

  private static long minorUnits(String amount) {
    return new BigDecimal(amount).movePointRight(2).longValueExact();
  }

  private static String majorUnits(Long minorUnits) {
    return BigDecimal.valueOf(minorUnits == null ? 0L : minorUnits)
        .movePointLeft(2)
        .toPlainString();
  }
}
