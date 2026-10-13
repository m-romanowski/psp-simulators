package dev.marcinromanowski.pspsim.kernel.transaction;

public final class NoUnitOwnsThisTransactionException extends RuntimeException {

  public static final String WORK_AFTER_A_COMMIT_NEEDS_A_UNIT_THAT_OWNS_IT =
      "work was handed on to run after a commit while a transaction no unit of work owns is open"
          + " on this thread; no unit can say when that transaction commits or whether it rolls"
          + " back, so the work would run before its effects are kept. Open the transaction as a"
          + " unit of work, or hand the work on after the transaction has ended";

  private static final long serialVersionUID = 1L;

  public NoUnitOwnsThisTransactionException(String message) {
    super(message);
  }
}
