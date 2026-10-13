package dev.marcinromanowski.pspsim.kernel.transaction;

public final class KeptAfterADiscardedUnitException extends RuntimeException {

  public static final String A_DISCARDED_UNIT_CANNOT_BE_KEPT_BY_THE_UNIT_AROUND_IT =
      "a unit of work answered a verdict it keeps after a unit of work nested in it was discarded;"
          + " the nested unit joined its transaction and marked it to roll back, so nothing it"
          + " wrote can be kept, and its verdict must discard what it wrote as well";

  private static final long serialVersionUID = 1L;

  public KeptAfterADiscardedUnitException(String message) {
    super(message);
  }
}
