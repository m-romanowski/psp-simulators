package dev.marcinromanowski.pspsim.provider.stripe;

import org.jspecify.annotations.Nullable;

final class StripeStoredParameters {
  private StripeStoredParameters() {}

  static @Nullable StripeError refusalOf(StripeForm form, String... parameters) {
    for (String parameter : parameters) {
      String value = form.first(parameter);
      if (value == null) {
        continue;
      }
      StripeError refusal = refusalOf(parameter, value);
      if (refusal != null) {
        return refusal;
      }
    }
    return null;
  }

  static @Nullable StripeError refusalOf(String parameter, String value) {
    if (value.indexOf(0) >= 0) {
      return StripeErrors.unreadableParameter(parameter, "text without a NUL character");
    }
    int longest =
        switch (parameter) {
          case StripeFields.CUSTOMER -> StripeCustomer.LONGEST_CUSTOMER_ID;
          case StripeFields.PAYMENT_METHOD -> StripePaymentMethod.LONGEST_PAYMENT_METHOD_ID;
          default -> Integer.MAX_VALUE;
        };
    return value.length() > longest
        ? StripeErrors.unreadableParameter(
            parameter, "an identifier of at most " + longest + " characters")
        : null;
  }
}
