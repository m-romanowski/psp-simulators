package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

final class StripeDeclineRendering {

  private StripeDeclineRendering() {}

  static StripeJson of(StripeJson value, Function<String, Optional<StripeJson>> methods) {
    if (value instanceof StripeJson.JsonArray array) {
      return StripeJson.array(
          array.elements().stream().map(element -> of(element, methods)).toList());
    }
    if (!(value instanceof StripeJson.JsonObject object)) {
      return value;
    }
    Map<String, StripeJson> members = new LinkedHashMap<>();
    boolean cardError = StripeJson.text("card_error").equals(object.members().get("type"));
    object
        .members()
        .forEach(
            (name, member) -> {
              if (cardError
                  && "payment_method".equals(name)
                  && member instanceof StripeJson.JsonObject method
                  && method.members().get("id") instanceof StripeJson.JsonText id) {
                members.put(name, methods.apply(id.value()).orElse(member));
              } else {
                members.put(name, of(member, methods));
              }
            });
    return new StripeJson.JsonObject(members);
  }
}
