package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import java.math.BigDecimal;
import java.util.Optional;

final class WorldlineAmounts {

  static final long SMALLEST = 0L;

  static final long LARGEST = 999999999999L;

  static final int CURRENCY_CODE_LENGTH = 3;

  static final String MINOR_UNITS =
      "provider worldline types order.amountOfMoney.amount as integer/int64 and glosses it"
          + " \"Amount in the smallest currency unit\", with a published minimum of "
          + SMALLEST
          + " and a published maximum of "
          + LARGEST
          + ". Every amount of this contract is therefore a whole number of minor units and never"
          + " a decimal in units of the currency";

  private WorldlineAmounts() {}

  static Optional<Long> wholeMinorUnits(String lexical) {
    try {
      return Optional.of(Long.valueOf(lexical));
    } catch (NumberFormatException notAWholeNumber) {
      return Optional.empty();
    }
  }

  static boolean isWithinThePublishedBounds(long amount) {
    return amount >= SMALLEST && amount <= LARGEST;
  }

  static Amount asAnAmount(long minorUnits) {
    return Amount.of(Long.toString(minorUnits));
  }

  static long minorUnitsOf(Amount amount) {
    return amount.number().orElse(BigDecimal.ZERO).longValue();
  }

  static boolean isAThreeLetterCode(String currencyCode) {
    return currencyCode.length() == CURRENCY_CODE_LENGTH;
  }
}
