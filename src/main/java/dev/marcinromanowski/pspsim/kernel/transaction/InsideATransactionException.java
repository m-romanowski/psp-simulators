package dev.marcinromanowski.pspsim.kernel.transaction;

public final class InsideATransactionException extends RuntimeException {

  public static final String WHAT_REPORTS_WHAT_IT_DID_RUNS_OUTSIDE_ANY_TRANSACTION =
      "this operation answers with what it did, and inside an open unit of work or transaction"
          + " what it does waits for a commit that has not happened, so its answer would describe"
          + " nothing; call it outside any unit of work or transaction";

  private static final long serialVersionUID = 1L;

  public InsideATransactionException(String message) {
    super(message);
  }
}
