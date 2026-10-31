package dev.marcinromanowski.pspsim.provider.stripe;

final class StripeSetupIntentView {

  static final String OBJECT = "setup_intent";

  private StripeSetupIntentView() {}

  static StripeJson theTruncatedFormAnErrorCarries(StripeSetupIntent intent) {
    return StripeJson.object(
        "id",
        StripeJson.text(intent.setupIntentId()),
        "object",
        StripeJson.text(OBJECT),
        "customer",
        StripeJson.textOrNull(intent.customerId()),
        "last_setup_error",
        StripeDeclineView.of(intent.lastSetupError()),
        "payment_method",
        StripeJson.textOrNull(intent.paymentMethodId()),
        "status",
        StripeJson.text(intent.status().wireValue()));
  }

  static StripeJson of(StripeSetupIntent intent) {
    return StripeJson.object(
        "id",
        StripeJson.text(intent.setupIntentId()),
        "object",
        StripeJson.text(OBJECT),
        StripeFields.ALLOWED_PAYMENT_METHOD_TYPES,
        StripeSelectionView.allowed(intent.selection()),
        StripeFields.AUTOMATIC_PAYMENT_METHODS,
        StripeSelectionView.automatic(intent.selection()),
        StripeFields.CLIENT_SECRET,
        StripeJson.text(intent.clientSecret()),
        "created",
        StripeJson.number(Long.toString(intent.createdAt().getEpochSecond())),
        "customer",
        StripeJson.textOrNull(intent.customerId()),
        StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES,
        StripeSelectionView.excluded(intent.selection()),
        "last_setup_error",
        StripeDeclineView.of(intent.lastSetupError()),
        "livemode",
        StripeJson.bool(false),
        "payment_method",
        StripeJson.textOrNull(intent.paymentMethodId()),
        StripeFields.PAYMENT_METHOD_CONFIGURATION_DETAILS,
        StripeSelectionView.configurationDetails(intent.selection()),
        StripeFields.PAYMENT_METHOD_TYPES,
        StripeSelectionView.offered(intent.selection()),
        "status",
        StripeJson.text(intent.status().wireValue()),
        "usage",
        StripeJson.text(intent.usage().wireValue()));
  }
}
