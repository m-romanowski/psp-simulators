package dev.marcinromanowski.pspsim.kernel.webhook;

public enum Acknowledgement {
  ACKNOWLEDGED(Scheduling.FINISHED),
  REFUSED(Scheduling.NEXT_ATTEMPT),
  AWAITING_SEPARATE_CALL(Scheduling.NEXT_ATTEMPT);

  public enum Scheduling {
    FINISHED,
    NEXT_ATTEMPT
  }

  private final Scheduling scheduling;

  Acknowledgement(Scheduling scheduling) {
    this.scheduling = scheduling;
  }

  public Scheduling scheduling() {
    return scheduling;
  }
}
