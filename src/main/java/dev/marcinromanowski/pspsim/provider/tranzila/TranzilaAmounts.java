package dev.marcinromanowski.pspsim.provider.tranzila;

import java.math.BigDecimal;

final class TranzilaAmounts {

  private TranzilaAmounts() {}

  static String lexicalOf(BigDecimal amount) {
    return amount.toPlainString();
  }

  static BigDecimal ofAnItem(String unitPrice, long unitsNumber) {
    return new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(unitsNumber));
  }

  static boolean isAbove(String amount, String ceiling) {
    return new BigDecimal(amount).compareTo(new BigDecimal(ceiling)) > 0;
  }
}
