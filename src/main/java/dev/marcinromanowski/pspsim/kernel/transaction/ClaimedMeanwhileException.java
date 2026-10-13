package dev.marcinromanowski.pspsim.kernel.transaction;

import org.jspecify.annotations.Nullable;

public final class ClaimedMeanwhileException extends RuntimeException {

  public static final String A_VALUE_THIS_REQUEST_WRITES_WAS_CLAIMED_MEANWHILE =
      "a value this request writes was claimed by another request after it was checked and before"
          + " it was written, so nothing this request asked for was kept; sending it again names"
          + " the value that is taken";

  private static final long serialVersionUID = 1L;

  private final @Nullable String property;

  public ClaimedMeanwhileException(String message) {
    this(null, message);
  }

  public ClaimedMeanwhileException(@Nullable String property, String message) {
    super(message);
    this.property = property;
  }

  public @Nullable String property() {
    return property;
  }
}
