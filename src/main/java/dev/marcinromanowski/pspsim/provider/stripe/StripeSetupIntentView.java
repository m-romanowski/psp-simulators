package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.List;

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
        StripeFields.CLIENT_SECRET,
        StripeJson.text(intent.clientSecret()),
        "created",
        StripeJson.number(Long.toString(intent.createdAt().getEpochSecond())),
        "customer",
        StripeJson.textOrNull(intent.customerId()),
        "last_setup_error",
        StripeDeclineView.of(intent.lastSetupError()),
        "livemode",
        StripeJson.bool(false),
        "payment_method",
        StripeJson.textOrNull(intent.paymentMethodId()),
        "payment_method_types",
        thePublishedTypesThisBuildSetsUp(),
        "status",
        StripeJson.text(intent.status().wireValue()),
        "usage",
        StripeJson.text(intent.usage().wireValue()));
  }

  private static StripeJson thePublishedTypesThisBuildSetsUp() {
    List<StripeJson> types = new ArrayList<>();
    for (StripePaymentMethodType type : StripePaymentMethodType.values()) {
      types.add(StripeJson.text(type.wireValue()));
    }
    return StripeJson.array(types);
  }
}
