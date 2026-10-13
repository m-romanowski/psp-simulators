package dev.marcinromanowski.pspsim.kernel.idempotency;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record KeyedExchangePage(List<KeyedExchange> exchanges) {

  public KeyedExchangePage {
    for (int following = 1; following < exchanges.size(); following++) {
      KeyedExchange earlier = exchanges.get(following - 1);
      KeyedExchange later = exchanges.get(following);
      if (earlier.arrival() <= later.arrival()) {
        throw new IllegalArgumentException(
            "a page of keyed exchanges is newest first and every row strictly older than the one"
                + " above it, and the exchange under "
                + later.keyName()
                + " "
                + later.keyValue()
                + " did not arrive before the exchange under "
                + earlier.keyName()
                + " "
                + earlier.keyValue()
                + ", so a reader paging on the cursor of the last row it saw would either loop on"
                + " it or skip everything between the two");
      }
    }
    exchanges = List.copyOf(exchanges);
  }

  public static KeyedExchangePage empty() {
    return new KeyedExchangePage(List.of());
  }

  public @Nullable String cursorToCarryOnFrom(int pageSize) {
    if (exchanges.size() < pageSize) {
      return null;
    }
    return String.valueOf(exchanges.get(exchanges.size() - 1).arrival());
  }
}
