package dev.marcinromanowski.pspsim.ui;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.Nullable;

public record AMomentInWords(Instant now) {

  public static final String NO_MOMENT_THIS_SCREEN_COULD_READ =
      "this screen was handed no moment it could read here";

  static final String IT_IS_THAT_MOMENT_NOW = "now";

  private static final long SECONDS_IN_A_MINUTE = 60;

  private static final long SECONDS_IN_AN_HOUR = 60 * SECONDS_IN_A_MINUTE;

  private static final long SECONDS_IN_A_DAY = 24 * SECONDS_IN_AN_HOUR;

  public String howFarFromNow(@Nullable String moment) {
    Instant at = read(moment);
    if (at == null) {
      return NO_MOMENT_THIS_SCREEN_COULD_READ;
    }
    long seconds = Duration.between(now, at).toSeconds();
    if (seconds < 0) {
      return theSpanOf(-seconds) + " ago";
    }
    if (seconds == 0) {
      return IT_IS_THAT_MOMENT_NOW;
    }
    return "in " + theSpanOf(seconds);
  }

  static String theSpanOf(long seconds) {
    if (seconds < SECONDS_IN_A_MINUTE) {
      return howMany(seconds, "second");
    }
    if (seconds < SECONDS_IN_AN_HOUR) {
      return howMany(seconds / SECONDS_IN_A_MINUTE, "minute");
    }
    if (seconds < SECONDS_IN_A_DAY) {
      return howMany(seconds / SECONDS_IN_AN_HOUR, "hour");
    }
    return howMany(seconds / SECONDS_IN_A_DAY, "day");
  }

  private static String howMany(long count, String unit) {
    return count == 1 ? count + " " + unit : count + " " + unit + "s";
  }

  private static @Nullable Instant read(@Nullable String moment) {
    if (moment == null) {
      return null;
    }
    try {
      return Instant.parse(moment.strip());
    } catch (DateTimeParseException noMomentAnythingCanRead) {
      return null;
    }
  }
}
