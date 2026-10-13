package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

final class TranzilaJ5Validity {

  static final String SETTING = "tranzila.j5-validity";

  static final Duration BY_DEFAULT = Duration.ofDays(7);

  static final Duration LONGEST = Duration.ofDays(365);

  static final String AN_EXAMPLE = "P7D";

  static final String WHY_IT_IS_A_SETTING =
      "provider tranzila publishes no validity for a J5 hold, and its written answer to a"
          + " merchant calls it \"acquirer-defined\", so this simulator keeps a J5 open for a span"
          + " an account chooses, on the run clock";

  private TranzilaJ5Validity() {}

  static Duration of(String property, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return BY_DEFAULT;
    }
    Duration parsed;
    try {
      parsed = Duration.parse(configured);
    } catch (DateTimeParseException unreadable) {
      throw new ProviderConfigurationRejectedException(
          property
              + " must name a span of time as an ISO-8601 duration such as "
              + AN_EXAMPLE
              + ". "
              + WHY_IT_IS_A_SETTING);
    }
    if (parsed.isZero() || parsed.isNegative()) {
      throw new ProviderConfigurationRejectedException(
          property
              + " must be longer than nothing: a J5 that closes the moment it is approved can"
              + " never be forced, so every hold this account takes would answer 20112 with"
              + " nothing in the answer to say the account was the reason");
    }
    if (parsed.compareTo(LONGEST) > 0) {
      throw new ProviderConfigurationRejectedException(
          property
              + " must be at most "
              + LONGEST.toDays()
              + " days, which is this simulator's own bound: a span beyond a year fixes a"
              + " deadline no test could reach by moving the clock to it");
    }
    if (!parsed.equals(parsed.truncatedTo(ChronoUnit.MICROS))) {
      throw new ProviderConfigurationRejectedException(
          property
              + " must be a whole number of microseconds, which is as fine as the store keeps a"
              + " span, so a finer one would not be the span that was stored");
    }
    return parsed;
  }
}
