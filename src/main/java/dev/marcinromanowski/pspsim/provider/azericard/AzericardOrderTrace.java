package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

final class AzericardOrderTrace {

  static final int TRACE_LENGTH = 6;

  static final int SHORTEST_ORDER = 6;

  static final int LONGEST_ORDER = 32;

  private AzericardOrderTrace() {}

  static boolean isWellFormed(String order) {
    if (order.length() < SHORTEST_ORDER || order.length() > LONGEST_ORDER) {
      return false;
    }
    for (int index = 0; index < order.length(); index++) {
      if (!Character.isDigit(order.charAt(index))) {
        return false;
      }
    }
    return true;
  }

  static String of(String order) {
    return order.substring(order.length() - TRACE_LENGTH);
  }

  static String exchangeKeyOf(String order, LocalDate day) {
    return day + KeyedExchange.SCOPE_THEN_VALUE + of(order);
  }

  static LocalDate dayOf(Instant at) {
    return at.atOffset(ZoneOffset.UTC).toLocalDate();
  }
}
