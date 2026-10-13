package com.example.shop;

import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.stereotype.Component;

@Component
class StripePayments implements Payments {

  private final ShopSettings settings;

  StripePayments(ShopSettings settings) {
    this.settings = settings;
  }

  @Override
  public String placeAHold(StoryRun run, String orderId, Product product) {
    PaymentIntentCreateParams hold =
        PaymentIntentCreateParams.builder()
            .setAmount(product.amountInMinorUnits())
            .setCurrency(product.currency())
            .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
            .setPaymentMethod(run.savedCard())
            .setConfirm(true)
            .setDescription(product.name())
            .build();
    try {
      return clientFor(run)
          .v1()
          .paymentIntents()
          .create(hold, RequestOptions.builder().setIdempotencyKey("hold-" + orderId).build())
          .getId();
    } catch (StripeException refused) {
      throw new ThePaymentProviderFailed("place a hold of " + product.price(), refused);
    }
  }

  @Override
  public CaptureOutcome capture(StoryRun run, String paymentIntentId, long amountInMinorUnits) {
    PaymentIntentCaptureParams capture =
        PaymentIntentCaptureParams.builder().setAmountToCapture(amountInMinorUnits).build();
    try {
      clientFor(run).v1().paymentIntents().capture(paymentIntentId, capture);
      return new CaptureOutcome.Captured();
    } catch (CardException declined) {
      return new CaptureOutcome.Refused(declined.getCode());
    } catch (StripeException failed) {
      throw new ThePaymentProviderFailed("capture " + paymentIntentId, failed);
    }
  }

  private StripeClient clientFor(StoryRun run) {
    return StripeClient.builder()
        .setApiKey(run.secretKey())
        .setApiBase(settings.simulatorApiUrl())
        .setMaxNetworkRetries(0)
        .setConnectTimeout(Math.toIntExact(settings.simulatorConnectTimeout().toMillis()))
        .setReadTimeout(Math.toIntExact(settings.simulatorReadTimeout().toMillis()))
        .build();
  }
}
