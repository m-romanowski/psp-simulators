package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.Set;
import org.jspecify.annotations.Nullable;

final class StripeReturnUrl {

  static final Set<String> THE_OFF_SESSION_VALUES = Set.of(StripeFlag.TRUE, "one_off", "recurring");

  private StripeReturnUrl() {}

  static @Nullable StripeError theRefusalAPaymentConfirmationEarns(
      StripeForm form, StripeSelection selection) {
    String offSession = form.first(StripeFields.OFF_SESSION);
    if (offSession != null && THE_OFF_SESSION_VALUES.contains(offSession)) {
      return null;
    }
    return mayRedirectWithNowhereToReturn(form, selection)
        ? StripeErrors.returnUrlRequiredForThePayment()
        : null;
  }

  static @Nullable StripeError theRefusalASetupConfirmationEarns(
      StripeForm form, StripeSelection selection) {
    return mayRedirectWithNowhereToReturn(form, selection)
        ? StripeErrors.returnUrlRequiredForTheSetup()
        : null;
  }

  private static boolean mayRedirectWithNowhereToReturn(
      StripeForm form, StripeSelection selection) {
    return selection.dynamic()
        && selection.allowRedirects() != StripeRedirects.NEVER
        && !form.has(StripeFields.RETURN_URL);
  }
}
