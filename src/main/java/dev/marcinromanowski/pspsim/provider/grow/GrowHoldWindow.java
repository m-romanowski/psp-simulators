package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

record GrowHoldWindow(Duration captureWindow, Duration automaticVoidAfter) {

  static final String CAPTURE_WINDOW = "captureWindow";

  static final String AUTOMATIC_VOID_AFTER = "automaticVoidAfter";

  static final Duration LONGEST = Duration.ofDays(365);

  static final GrowHoldWindow PUBLISHED_DEFAULT =
      new GrowHoldWindow(Duration.ofDays(7), Duration.ofDays(10));

  static final String AN_EXAMPLE_CAPTURE_WINDOW = "P7D";

  static final String AN_EXAMPLE_AUTOMATIC_VOID = "P10D";

  private static final String STATED_BY_A_FORM_AND_BY_A_CALL =
      "a form states one as a number and a unit, and a call to the control API writes one as an"
          + " ISO-8601 duration such as ";

  GrowHoldWindow {
    guard(CAPTURE_WINDOW, captureWindow);
    guard(AUTOMATIC_VOID_AFTER, automaticVoidAfter);
  }

  private static void guard(String setting, Duration span) {
    if (span == null || span.isZero() || span.isNegative() || span.compareTo(LONGEST) > 0) {
      throw new IllegalArgumentException(setting + " is no span a hold can outlive: " + span);
    }
    if (!isWholeMicroseconds(span)) {
      throw new IllegalArgumentException(setting + " is finer than the store holds: " + span);
    }
  }

  private static boolean isWholeMicroseconds(Duration span) {
    return span.equals(span.truncatedTo(ChronoUnit.MICROS));
  }

  static String mustNameASpanOfTime(String setting, String anExampleOfIt) {
    return setting + " must name a span of time: " + STATED_BY_A_FORM_AND_BY_A_CALL + anExampleOfIt;
  }

  static String mustBeLongerThanNothing(String setting, String anExampleOfIt) {
    return setting
        + " must be longer than nothing: "
        + STATED_BY_A_FORM_AND_BY_A_CALL
        + anExampleOfIt
        + ". A hold whose span closes at or before the moment it is created can never be"
        + " captured, so a span of zero or less leaves every hold this account takes refusing a"
        + " settlement, with nothing in the answer to say the account was the reason. To watch a"
        + " settlement refused on a hold that has expired, state a span a test can outlive and"
        + " move the simulator's clock past it";
  }

  static String mustBeShortEnoughToTravelTo(String setting, String anExampleOfIt) {
    return setting
        + " must be at most "
        + LONGEST.toDays()
        + " days, which is this simulator's own bound and not a figure grow publishes: "
        + STATED_BY_A_FORM_AND_BY_A_CALL
        + anExampleOfIt
        + ". A span beyond a year fixes a deadline no test could reach by travelling to it";
  }

  static String mustBeStatedToTheMicrosecond(String setting, String anExampleOfIt) {
    return setting
        + " must be a whole number of microseconds, which is as fine as the store keeps a span: "
        + STATED_BY_A_FORM_AND_BY_A_CALL
        + anExampleOfIt
        + ". A span stated more finely than that is not the span that would be stored, and the"
        + " hold it governs would not expire when this account says";
  }

  static GrowHoldWindow of(
      String propertyPrefix, @Nullable String captureWindow, @Nullable String automaticVoidAfter) {
    return new GrowHoldWindow(
        duration(
            propertyPrefix,
            CAPTURE_WINDOW,
            AN_EXAMPLE_CAPTURE_WINDOW,
            captureWindow,
            PUBLISHED_DEFAULT::captureWindow),
        duration(
            propertyPrefix,
            AUTOMATIC_VOID_AFTER,
            AN_EXAMPLE_AUTOMATIC_VOID,
            automaticVoidAfter,
            PUBLISHED_DEFAULT::automaticVoidAfter));
  }

  private static Duration duration(
      String propertyPrefix,
      String setting,
      String anExampleOfIt,
      @Nullable String configured,
      Supplier<Duration> byDefault) {
    if (configured == null || configured.isBlank()) {
      return byDefault.get();
    }
    Duration parsed;
    try {
      parsed = Duration.parse(configured);
    } catch (DateTimeParseException unreadable) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + mustNameASpanOfTime(setting, anExampleOfIt));
    }
    if (parsed.isZero() || parsed.isNegative()) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + mustBeLongerThanNothing(setting, anExampleOfIt));
    }
    if (parsed.compareTo(LONGEST) > 0) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + mustBeShortEnoughToTravelTo(setting, anExampleOfIt));
    }
    if (!isWholeMicroseconds(parsed)) {
      throw new ProviderConfigurationRejectedException(
          propertyPrefix + mustBeStatedToTheMicrosecond(setting, anExampleOfIt));
    }
    return parsed;
  }
}
