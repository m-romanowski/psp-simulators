package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

enum TranzilaTxnType {
  DEBIT("debit", "A"),

  VERIFY("verify", null),

  FORCE("force", "F"),

  REVERSAL("reversal", null),

  CREDIT("credit", "C"),

  CANCEL("cancel", "D");

  static final String STO = "sto";

  static final int J2 = 2;

  static final int J5 = 5;

  static final int STANDING_ORDER_VERIFICATION = 6;

  static final String J2_TRANMODE = "N";

  static final String J5_TRANMODE = "V";

  private final String wire;
  private final @Nullable String tranmode;

  TranzilaTxnType(String wire, @Nullable String tranmode) {
    this.wire = wire;
    this.tranmode = tranmode;
  }

  String wire() {
    return wire;
  }

  boolean carriesACard() {
    return this != CANCEL && this != REVERSAL;
  }

  boolean carriesACardholder() {
    return this == DEBIT || this == VERIFY;
  }

  boolean carriesAnAmount() {
    return this == DEBIT || this == VERIFY || this == FORCE || this == CREDIT;
  }

  Optional<String> tranmodeOf(@Nullable Integer verifyMode) {
    if (this != VERIFY) {
      return Optional.ofNullable(tranmode);
    }
    return Optional.of(Integer.valueOf(J5).equals(verifyMode) ? J5_TRANMODE : J2_TRANMODE);
  }

  static Optional<TranzilaTxnType> of(String wire) {
    for (TranzilaTxnType served : values()) {
      if (served.wire.equals(wire)) {
        return Optional.of(served);
      }
    }
    return Optional.empty();
  }
}
