package dev.marcinromanowski.pspsim.kernel.audit;

public final class AuditRowNamesNoPathException extends RuntimeException {

  public static final String ONLY_A_CALLBACK_WITH_NO_TARGET_NAMES_NO_PATH =
      "an audit row names the URL a call went to, or would have gone to; only a callback the"
          + " provider found no target for, recorded as not sent, has none";

  private static final long serialVersionUID = 1L;

  public AuditRowNamesNoPathException(String message) {
    super(message);
  }
}
