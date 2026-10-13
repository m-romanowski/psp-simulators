package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record StripeWebhookSigning(
    @Nullable String secret, StripeSignatureEmission emission, Duration timestampAge) {

  static final String SECRET_SETTING = "webhookSecret";

  static final String EMISSION_SETTING = "webhookSignature";

  static final String AGE_SETTING = "webhookTimestampAge";

  static final String PUBLISHED_PREFIX = "whsec_";

  static final int LONGEST_SECRET_THE_STORE_HOLDS = 255;

  static final Duration OLDEST_TIMESTAMP_THE_STORE_HOLDS = Duration.ofDays(365);

  static final String AN_EXAMPLE_AGE = "PT6M";

  static final String A_SECRET_STRIPE_WOULD_TAKE = PUBLISHED_PREFIX + "theEndpointSecret";

  static final Set<String> SETTINGS = Set.of(SECRET_SETTING, EMISSION_SETTING, AGE_SETTING);

  static final String SECRET_IS_A_SIGNING_SECRET =
      SECRET_SETTING
          + " must begin with "
          + PUBLISHED_PREFIX
          + ", the prefix Stripe publishes for an endpoint signing secret; a value of any other"
          + " shape would sign a delivery with something no merchant was given";

  static final String SECRET_IS_KEPT_WITH_EVERY_ACCOUNT =
      SECRET_SETTING
          + " must be at most "
          + LONGEST_SECRET_THE_STORE_HOLDS
          + " characters, which is the simulator's bound on the column the account keeps it in and"
          + " not a length the vendor publishes";

  static final String EMISSION_IS_ONE_OF_TWO =
      EMISSION_SETTING
          + " must be VALID or CORRUPTED. Stripe publishes no wrong signature, so CORRUPTED is the"
          + " simulator's own name for a delivery whose v1 element cannot verify, which is what"
          + " exercises a merchant's own verification";

  static final String AGE_IS_A_DURATION =
      AGE_SETTING
          + " must name a span of time: a form states one as a number and a unit, and a call to"
          + " the control API writes one as an ISO-8601 duration such as PT6M or PT0S";

  static final String AGE_IS_NOT_A_FUTURE_TIMESTAMP =
      AGE_SETTING
          + " must not be negative. It is how far in the past the t this account emits is dated,"
          + " so that a merchant's own tolerance has something to reject; a negative age would"
          + " date a delivery ahead of the run's own clock, which Stripe never does";

  static final String AGE_IS_STORABLE =
      AGE_SETTING
          + " must be at most "
          + OLDEST_TIMESTAMP_THE_STORE_HOLDS.toDays()
          + " days, which is the simulator's own bound on the age it keeps beside the account and"
          + " not a window the vendor publishes: Stripe publishes no server-side staleness rule at"
          + " all, and this simulator enforces none";

  static final String NOTHING_TO_SIGN =
      " names how this account signs a delivery, and the account has no "
          + SECRET_SETTING
          + " to sign one with. Stripe sends Stripe-Signature only where a signing secret is"
          + " configured, so a delivery of this account carries no signature for either setting to"
          + " describe";

  static StripeWebhookSigning of(String propertyPrefix, Map<String, String> configuration) {
    String secret = theSecret(propertyPrefix, configuration.get(SECRET_SETTING));
    StripeSignatureEmission emission =
        theEmission(propertyPrefix, secret, configuration.get(EMISSION_SETTING));
    Duration age = theAge(propertyPrefix, secret, configuration.get(AGE_SETTING));
    return new StripeWebhookSigning(secret, emission, age);
  }

  static StripeWebhookSigning stored(
      @Nullable String secret, String emission, String timestampAge) {
    return new StripeWebhookSigning(
        secret, StripeSignatureEmission.stored(emission), Duration.parse(timestampAge));
  }

  private static @Nullable String theSecret(String propertyPrefix, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return null;
    }
    if (!configured.startsWith(PUBLISHED_PREFIX)) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + SECRET_IS_A_SIGNING_SECRET);
    }
    if (configured.codePointCount(0, configured.length()) > LONGEST_SECRET_THE_STORE_HOLDS) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + SECRET_IS_KEPT_WITH_EVERY_ACCOUNT);
    }
    return configured;
  }

  private static StripeSignatureEmission theEmission(
      String propertyPrefix, @Nullable String secret, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return StripeSignatureEmission.VALID;
    }
    if (secret == null) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + EMISSION_SETTING + NOTHING_TO_SIGN);
    }
    return StripeSignatureEmission.of(configured)
        .orElseThrow(
            () ->
                new ProviderConfigurationRejectedException(
                    propertyPrefix + EMISSION_IS_ONE_OF_TWO));
  }

  private static Duration theAge(
      String propertyPrefix, @Nullable String secret, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return Duration.ZERO;
    }
    if (secret == null) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + AGE_SETTING + NOTHING_TO_SIGN);
    }
    Duration parsed;
    try {
      parsed = Duration.parse(configured);
    } catch (DateTimeParseException unreadable) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + AGE_IS_A_DURATION);
    }
    if (parsed.isNegative()) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + AGE_IS_NOT_A_FUTURE_TIMESTAMP);
    }
    if (parsed.compareTo(OLDEST_TIMESTAMP_THE_STORE_HOLDS) > 0) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + AGE_IS_STORABLE);
    }
    return parsed;
  }

  Instant timestampFor(Instant attemptedAt) {
    return attemptedAt.minus(timestampAge);
  }

  String storedAge() {
    return timestampAge.toString();
  }
}
