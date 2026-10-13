package dev.marcinromanowski.pspsim.kernel.money;

import java.math.BigDecimal;
import java.util.Optional;

public record Amount(String value) {

  public static Amount of(String value) {
    return new Amount(value);
  }

  public static Amount of(BigDecimal value) {
    return new Amount(value.toPlainString());
  }

  public Optional<BigDecimal> number() {
    try {
      return Optional.of(new BigDecimal(value));
    } catch (NumberFormatException notANumber) {
      return Optional.empty();
    }
  }
}
