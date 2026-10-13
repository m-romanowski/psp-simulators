package com.example.shop;

import java.math.BigDecimal;
import java.util.Locale;

public record Product(String name, String description, long amountInMinorUnits, String currency) {

  public static final Product THE_ONE_WE_SELL =
      new Product(
          "Espresso machine",
          "A lever espresso machine with a steam wand, boxed and ready to ship.",
          12_050,
          "usd");

  public String price() {
    return BigDecimal.valueOf(amountInMinorUnits).movePointLeft(2).toPlainString()
        + " "
        + currency.toUpperCase(Locale.ROOT);
  }
}
