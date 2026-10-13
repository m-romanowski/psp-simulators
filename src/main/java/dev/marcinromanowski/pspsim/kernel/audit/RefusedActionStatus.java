package dev.marcinromanowski.pspsim.kernel.audit;

public enum RefusedActionStatus {
  REJECTED(400),
  NOT_FOUND(404),
  CONFLICTING(409),
  REFUSED(422);

  private static final int THE_LOWEST_STATUS_THAT_REFUSES = 400;

  private static final int THE_LOWEST_STATUS_NOTHING_DECIDED = 500;

  private final int httpStatus;

  RefusedActionStatus(int httpStatus) {
    this.httpStatus = httpStatus;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public static boolean refuses(int httpStatus) {
    return httpStatus >= THE_LOWEST_STATUS_THAT_REFUSES
        && httpStatus < THE_LOWEST_STATUS_NOTHING_DECIDED;
  }

  public static boolean nothingDecided(int httpStatus) {
    return httpStatus >= THE_LOWEST_STATUS_NOTHING_DECIDED;
  }
}
