package dev.marcinromanowski.pspsim.kernel.smoke;

public final class ChecksAlreadyRunningException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public static final String THE_CHECKS_ARE_ALREADY_RUNNING =
      "the compatibility checks are already running; this press started nothing, and the run"
          + " in progress shows here once it finishes";

  public ChecksAlreadyRunningException() {
    super(THE_CHECKS_ARE_ALREADY_RUNNING);
  }
}
