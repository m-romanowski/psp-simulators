package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

enum TranzilaTranmode {
  A("A", TranzilaTxnType.DEBIT, null),

  V("V", TranzilaTxnType.VERIFY, Integer.valueOf(TranzilaTxnType.J5)),

  N("N", TranzilaTxnType.VERIFY, Integer.valueOf(TranzilaTxnType.J2)),

  K("K", null, null),

  AK("AK", TranzilaTxnType.DEBIT, null),

  VK("VK", TranzilaTxnType.VERIFY, Integer.valueOf(TranzilaTxnType.J5)),

  NK("NK", TranzilaTxnType.VERIFY, Integer.valueOf(TranzilaTxnType.J2));

  static final String STANDING_ORDER = "J";

  private final String wire;
  private final @Nullable TranzilaTxnType type;
  private final @Nullable Integer verifyMode;

  TranzilaTranmode(String wire, @Nullable TranzilaTxnType type, @Nullable Integer verifyMode) {
    this.wire = wire;
    this.type = type;
    this.verifyMode = verifyMode;
  }

  String wire() {
    return wire;
  }

  Optional<TranzilaTxnType> type() {
    return Optional.ofNullable(type);
  }

  @Nullable Integer verifyMode() {
    return verifyMode;
  }

  boolean recordsATransaction() {
    return type != null;
  }

  static Optional<TranzilaTranmode> named(String sent) {
    for (TranzilaTranmode served : values()) {
      if (served.wire.equals(sent)) {
        return Optional.of(served);
      }
    }
    return Optional.empty();
  }
}
