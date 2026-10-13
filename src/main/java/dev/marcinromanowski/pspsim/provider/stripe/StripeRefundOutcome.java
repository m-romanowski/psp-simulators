package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import org.jspecify.annotations.Nullable;

record StripeRefundOutcome(StripeRefundStatus status) {

  static final String SETTING = "refundStatus";

  static final StripeRefundStatus THE_ONE_A_CARD_REFUND_REACHES = StripeRefundStatus.SUCCEEDED;

  static final String MUST_BE_A_PUBLISHED_REFUND_STATUS =
      SETTING
          + " must be one of "
          + StripeRefundStatus.theStatusesTheVendorPublishes()
          + ", the five values Stripe publishes on the Refund object. It names the status a refund"
          + " created against this account comes to rest in, because a card refund in this"
          + " simulator settles synchronously and the other four are reached by payment methods no"
          + " endpoint of this contract exposes; without this setting a test could only ever see"
          + " one of the five";

  static final String IS_THE_ONLY_ONE_A_CARD_REACHES_UNASKED =
      SETTING
          + " defaults to "
          + THE_ONE_A_CARD_REFUND_REACHES.name()
          + ", which is where Stripe's own published refund example comes to rest and the only"
          + " status a card refund reaches without a payment method this simulator does not"
          + " expose; it is the vendor's ordinary path for the one payment-method type in scope"
          + " rather than a number picked to stand in for an unpublished one";

  static StripeRefundOutcome of(String propertyPrefix, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return new StripeRefundOutcome(THE_ONE_A_CARD_REFUND_REACHES);
    }
    return new StripeRefundOutcome(
        StripeRefundStatus.named(configured)
            .orElseThrow(
                () ->
                    new ProviderConfigurationRejectedException(
                        propertyPrefix + MUST_BE_A_PUBLISHED_REFUND_STATUS)));
  }

  static StripeRefundOutcome stored(String value) {
    return new StripeRefundOutcome(StripeRefundStatus.valueOf(value));
  }

  String storedForm() {
    return status.name();
  }
}
