package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;

record TranzilaDcDisableKey(
    TestRunId testRunId,
    String accountId,
    String dcDisable,
    TranzilaTxnType type,
    @Nullable Integer verifyMode) {

  static final int LOCK_SPACE = "tranzila DCdisable".hashCode();

  String exchangeKeyName() {
    return keyNameOf(type, verifyMode);
  }

  static String keyNameOf(TranzilaTxnType type, @Nullable Integer verifyMode) {
    return verifyMode == null
        ? TranzilaFields.DC_DISABLE + " " + type.wire()
        : TranzilaFields.DC_DISABLE + " " + type.wire() + " " + verifyMode;
  }

  int lock() {
    return (testRunId.value()
            + '\u0000'
            + accountId
            + '\u0000'
            + type.wire()
            + '\u0000'
            + verifyMode
            + '\u0000'
            + dcDisable)
        .hashCode();
  }
}
