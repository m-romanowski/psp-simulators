package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import java.math.BigDecimal;
import java.util.Optional;

final class AutraAmounts {

  private AutraAmounts() {}

  static Optional<String> lexicalIn(AutraJson.JsonObject body, String name) {
    Optional<String> number = body.number(name);
    return number.isPresent() ? number : body.text(name);
  }

  static Optional<BigDecimal> numberOf(String lexical) {
    return Amount.of(lexical).number();
  }

  static boolean isTypedAsANumber(String lexical) {
    return AutraJsonReader.isANumber(lexical);
  }

  static boolean isAboveZero(String lexical) {
    return isTypedAsANumber(lexical)
        && numberOf(lexical).filter(value -> value.signum() > 0).isPresent();
  }

  static int compare(String left, BigDecimal right) {
    return numberOf(left).orElse(BigDecimal.ZERO).compareTo(right);
  }
}
