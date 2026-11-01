package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import org.jspecify.annotations.Nullable;

record StripeApiVersion(String wireValue) {

  static final String SETTING = "apiVersion";

  static final String CODENAME_SEPARATOR = ".";

  static String mustBeOneThisSimulatorAnswers() {
    return SETTING
        + " must be one of the Stripe API versions this simulator answers, "
        + StripeApiVersions.theRangeAnswered()
        + ", written as Stripe writes it, such as "
        + StripeApiVersions.newest().wireValue()
        + ". An account's default is what a request sending no Stripe-Version is answered at,"
        + " so a version this simulator does not answer would be a default no request could"
        + " ever be answered at";
  }

  static StripeApiVersion ofTheAccount(String propertyPrefix, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return StripeApiVersions.newest();
    }
    return StripeApiVersions.answeredNamed(configured)
        .orElseThrow(
            () ->
                new ProviderConfigurationRejectedException(
                    propertyPrefix + mustBeOneThisSimulatorAnswers()));
  }

  static StripeApiVersion stored(String value) {
    return new StripeApiVersion(value);
  }

  String storedForm() {
    return wireValue;
  }
}
