package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import java.util.Optional;

final class TranzilaIdentifiers {

  static final int TRANSACTION_ID_DIGITS = 8;

  static final int AUTH_NUMBER_DIGITS = 7;

  static final int TOKEN_HEX = 14;

  static final String TOKEN_LEAD = "t";

  static final int LONGEST_TRANSACTION_ID = 18;

  private TranzilaIdentifiers() {}

  static long aTransactionId(IdGenerator ids) {
    return Long.parseLong(ids.digits(TRANSACTION_ID_DIGITS));
  }

  static String anAuthNumber(IdGenerator ids) {
    return ids.digits(AUTH_NUMBER_DIGITS);
  }

  static String aTokenFor(IdGenerator ids, String last4) {
    return TOKEN_LEAD + ids.hex(TOKEN_HEX) + last4;
  }

  static Optional<Long> transactionIdIn(String named) {
    if (!named.matches("[1-9][0-9]{0," + (LONGEST_TRANSACTION_ID - 1) + "}")) {
      return Optional.empty();
    }
    return Optional.of(Long.valueOf(named));
  }
}
