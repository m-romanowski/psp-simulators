package dev.marcinromanowski.pspsim.kernel.money;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public enum LedgerClaim {
  APPLIED,

  CLAIM_TAKEN,

  OBJECT_GONE;

  public static LedgerClaim of(
      BooleanSupplier theObjectIsStillStored, IntSupplier claim, Runnable noteTheChange) {
    if (!theObjectIsStillStored.getAsBoolean()) {
      return OBJECT_GONE;
    }
    if (claim.getAsInt() != 1) {
      return CLAIM_TAKEN;
    }
    noteTheChange.run();
    return APPLIED;
  }
}
