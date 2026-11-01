package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class StripePublishableView {

  static final Set<String> WHAT_A_PAYMENT_INTENT_SHOWS =
      Set.of(
          "allowed_payment_method_types",
          "amount",
          "amount_details",
          "amount_subtotal",
          "automatic_payment_methods",
          "canceled_at",
          "cancellation_reason",
          "capture_method",
          "client_secret",
          "confirmation_method",
          "created",
          "currency",
          "description",
          "excluded_payment_method_types",
          "id",
          "last_payment_error",
          "livemode",
          "next_action",
          "object",
          "payment_method",
          "payment_method_configuration_details",
          "payment_method_options",
          "payment_method_types",
          "processing",
          "receipt_email",
          "setup_future_usage",
          "shipping",
          "source",
          "status",
          "total_details");

  static final Set<String> WHAT_A_SETUP_INTENT_SHOWS =
      Set.of(
          "allowed_payment_method_types",
          "automatic_payment_methods",
          "cancellation_reason",
          "client_secret",
          "created",
          "description",
          "excluded_payment_method_types",
          "id",
          "last_setup_error",
          "livemode",
          "next_action",
          "object",
          "payment_method",
          "payment_method_configuration_details",
          "payment_method_options",
          "payment_method_types",
          "status",
          "usage");

  static final Set<String> WHAT_A_PAYMENT_METHOD_NEVER_SHOWS =
      Set.of(StripePaymentMethodView.METADATA);

  private StripePublishableView() {}

  static StripeJson of(StripeJson answered) {
    return reduced(answered);
  }

  private static StripeJson reduced(StripeJson member) {
    if (member instanceof StripeJson.JsonArray array) {
      return StripeJson.array(
          array.elements().stream().map(StripePublishableView::reduced).toList());
    }
    if (!(member instanceof StripeJson.JsonObject object)) {
      return member;
    }
    StripeJson kind = object.members().get("object");
    if (StripeJson.text(StripeIntentView.OBJECT).equals(kind)) {
      return keeping(object, WHAT_A_PAYMENT_INTENT_SHOWS);
    }
    if (StripeJson.text(StripeSetupIntentView.OBJECT).equals(kind)) {
      return keeping(object, WHAT_A_SETUP_INTENT_SHOWS);
    }
    if (StripeJson.text(StripePaymentMethodView.OBJECT).equals(kind)) {
      Map<String, StripeJson> kept = new LinkedHashMap<>(object.members());
      kept.keySet().removeAll(WHAT_A_PAYMENT_METHOD_NEVER_SHOWS);
      kept.replaceAll((name, value) -> reduced(value));
      return new StripeJson.JsonObject(kept);
    }
    if (StripeJson.text(StripeCustomerView.OBJECT).equals(kind)
        || StripeJson.text(StripeChargeView.OBJECT).equals(kind)
        || StripeJson.text(StripeRefundView.OBJECT).equals(kind)) {
      return object.members().getOrDefault("id", StripeJson.nothing());
    }
    Map<String, StripeJson> nested = new LinkedHashMap<>(object.members());
    nested.replaceAll((name, value) -> reduced(value));
    return new StripeJson.JsonObject(nested);
  }

  private static StripeJson keeping(StripeJson.JsonObject object, Set<String> shown) {
    Map<String, StripeJson> kept = new LinkedHashMap<>();
    object
        .members()
        .forEach(
            (name, member) -> {
              if (shown.contains(name)) {
                kept.put(name, reduced(member));
              }
            });
    return new StripeJson.JsonObject(kept);
  }
}
