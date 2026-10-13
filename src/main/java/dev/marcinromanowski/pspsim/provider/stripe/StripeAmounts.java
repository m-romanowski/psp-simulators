package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import java.math.BigDecimal;
import java.util.regex.Pattern;

final class StripeAmounts {

  static final int LONGEST_MINOR_UNITS_A_JSON_READER_HOLDS = 18;

  private static final Pattern POSITIVE_MINOR_UNITS =
      Pattern.compile("[1-9][0-9]{0," + (LONGEST_MINOR_UNITS_A_JSON_READER_HOLDS - 1) + "}");

  private static final Pattern DIGITS = Pattern.compile("[0-9]+");

  private StripeAmounts() {}

  static boolean isPositiveMinorUnits(String value) {
    return POSITIVE_MINOR_UNITS.matcher(value).matches();
  }

  static boolean isTooLongForAJsonReader(String value) {
    return DIGITS.matcher(value).matches()
        && value.length() > LONGEST_MINOR_UNITS_A_JSON_READER_HOLDS;
  }

  static Amount of(BigDecimal value) {
    return Amount.of(value.toBigInteger().toString());
  }
}
