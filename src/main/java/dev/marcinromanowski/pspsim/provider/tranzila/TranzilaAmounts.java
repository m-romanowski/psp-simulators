package dev.marcinromanowski.pspsim.provider.tranzila;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class TranzilaAmounts {

  private TranzilaAmounts() {}

  static String lexicalOf(BigDecimal amount) {
    return amount.toPlainString();
  }

  static BigDecimal ofAnItem(String unitPrice, long unitsNumber) {
    return new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(unitsNumber));
  }

  static final String ISRAELI_VAT_PERCENT = "18";

  static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  static BigDecimal withVat(BigDecimal net, String vatPercent) {
    return net.multiply(BigDecimal.ONE.add(new BigDecimal(vatPercent).divide(HUNDRED)))
        .setScale(2, RoundingMode.HALF_UP);
  }

  static String inMinorUnits(String amount) {
    return new BigDecimal(amount)
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .toPlainString();
  }

  static boolean isAbove(String amount, String ceiling) {
    return new BigDecimal(amount).compareTo(new BigDecimal(ceiling)) > 0;
  }
}
