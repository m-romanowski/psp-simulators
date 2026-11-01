package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class StripeDeclineView {

  private StripeDeclineView() {}

  static StripeJson of(@Nullable StripeDecline decline) {
    return of(decline, null);
  }

  static StripeJson of(@Nullable StripeDecline decline, @Nullable String paymentMethod) {
    return of(decline, paymentMethod, null);
  }

  static StripeJson of(
      @Nullable StripeDecline decline, @Nullable String paymentMethod, @Nullable String charge) {
    if (decline == null) {
      return StripeJson.nothing();
    }
    StripeJson.JsonObject body = (StripeJson.JsonObject) StripeErrors.declined(decline).body();
    StripeJson.JsonObject error =
        (StripeJson.JsonObject) Objects.requireNonNull(body.members().get("error"));
    Map<String, StripeJson> members = new LinkedHashMap<>(error.members());
    if (paymentMethod != null) {
      members.put(
          "payment_method",
          StripeJson.object(
              "id", StripeJson.text(paymentMethod), "object", StripeJson.text("payment_method")));
    }
    if (charge != null) {
      members.put("charge", StripeJson.text(charge));
    }
    return new StripeJson.JsonObject(members);
  }
}
