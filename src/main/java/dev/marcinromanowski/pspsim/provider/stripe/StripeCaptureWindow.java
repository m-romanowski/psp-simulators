package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

record StripeCaptureWindow(Duration window) {

  static final String SETTING = "captureWindow";

  static final Duration THE_VENDORS_PUBLISHED_DEFAULT = Duration.ofDays(7);

  static final Duration LONGEST = Duration.ofDays(365);

  static final Duration FINEST = Duration.of(1, ChronoUnit.MICROS);

  static final String AN_EXAMPLE_WINDOW = "P3D";

  static final String NO_DEFAULT_IS_CHOSEN_FOR_YOU =
      SETTING
          + " is required by provider stripe. Stripe publishes that uncaptured PaymentIntents are"
          + " cancelled a set number of days after their creation, names seven as the default and"
          + " never names the set, so the simulator has no maximum to enforce and will not adopt"
          + " the vendor's default on a fixture's behalf: a window nobody wrote agrees with seven"
          + " by coincidence and proves nothing about a value the vendor never fixed. Name the"
          + " window this fixture means, on whichever side of day seven it means to sit: a form"
          + " states it as a number and a unit, and a call to the control API writes it as an"
          + " ISO-8601 duration such as P3D or P14D";

  static final String MUST_BE_A_DURATION =
      SETTING
          + " must name a span of time: a form states one as a number and a unit, and a call to"
          + " the control API writes one as an ISO-8601 duration such as P3D or P14D";

  static final String MUST_BE_POSITIVE =
      SETTING
          + " must be a positive duration, because an authorization that expires at or before the"
          + " moment it is created can never be captured";

  static final String MUST_BE_STORABLE =
      SETTING
          + " must be at most "
          + LONGEST.toDays()
          + " days, which is the simulator's own bound and not the vendor's: the deadline it fixes"
          + " is kept with every PaymentIntent as a timestamp, and a window beyond this leaves a"
          + " deadline no test could reach by travelling to it";

  static final String MUST_BE_KEEPABLE =
      SETTING
          + " must be at least "
          + FINEST
          + ", one microsecond, which is as fine as the deadline it fixes is kept: that deadline"
          + " is a timestamp stored beside every PaymentIntent, a window finer than a microsecond"
          + " fixes the very moment the intent was created, and an authorization that expires at"
          + " the moment it is created can never be captured. A form states a span as a number"
          + " and a unit of a second or more, and a call to the control API writes one as an"
          + " ISO-8601 duration no finer than "
          + FINEST;

  static final String NO_WINDOW_A_HOLD_CAN_OUTLIVE = SETTING + " is no window a hold can outlive: ";

  StripeCaptureWindow {
    if (window == null || window.compareTo(FINEST) < 0) {
      throw new IllegalArgumentException(NO_WINDOW_A_HOLD_CAN_OUTLIVE + window);
    }
  }

  static StripeCaptureWindow of(String propertyPrefix, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + NO_DEFAULT_IS_CHOSEN_FOR_YOU);
    }
    Duration parsed;
    try {
      parsed = Duration.parse(configured);
    } catch (DateTimeParseException unreadable) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + MUST_BE_A_DURATION);
    }
    if (parsed.isZero() || parsed.isNegative()) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + MUST_BE_POSITIVE);
    }
    if (parsed.compareTo(FINEST) < 0) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + MUST_BE_KEEPABLE);
    }
    if (parsed.compareTo(LONGEST) > 0) {
      throw new ProviderConfigurationRejectedException(propertyPrefix + MUST_BE_STORABLE);
    }
    return new StripeCaptureWindow(parsed);
  }

  static StripeCaptureWindow stored(String value) {
    return new StripeCaptureWindow(Duration.parse(value));
  }

  String storedForm() {
    return window.toString();
  }

  Instant deadlineFrom(Instant createdAt) {
    return createdAt.plus(window);
  }
}
