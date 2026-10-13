package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record StripeError(
    int httpStatus,
    StripeErrorType type,
    @Nullable String code,
    @Nullable String declineCode,
    @Nullable String message,
    @Nullable String param,
    @Nullable StripeJson paymentIntent,
    @Nullable StripeJson setupIntent) {

  static final String DOC_URL_PREFIX = "https://docs.stripe.com/error-codes#";

  StripeJson body() {
    Map<String, StripeJson> error = new LinkedHashMap<>();
    error.put("type", StripeJson.text(type.wireValue()));
    put(error, "code", code);
    put(error, "decline_code", declineCode);
    put(error, "doc_url", theEntryItsCodeHas());
    put(error, "message", message);
    put(error, "param", param);
    StripeJson intent = paymentIntent;
    if (intent != null) {
      error.put("payment_intent", intent);
    }
    StripeJson setup = setupIntent;
    if (setup != null) {
      error.put("setup_intent", setup);
    }
    return StripeJson.object("error", new StripeJson.JsonObject(error));
  }

  StripeError about(StripeJson intent) {
    return new StripeError(httpStatus, type, code, declineCode, message, param, intent, null);
  }

  StripeError aboutTheSetup(StripeJson setup) {
    return new StripeError(httpStatus, type, code, declineCode, message, param, null, setup);
  }

  private @Nullable String theEntryItsCodeHas() {
    String errorCode = code;
    if (errorCode == null) {
      return null;
    }
    return DOC_URL_PREFIX + errorCode.replace('_', '-');
  }

  private static void put(Map<String, StripeJson> error, String name, @Nullable String value) {
    if (value != null) {
      error.put(name, StripeJson.text(value));
    }
  }
}
