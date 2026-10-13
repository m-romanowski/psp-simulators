package dev.marcinromanowski.pspsim.kernel.spi;

public enum ObjectAction {
  CAPTURE("capture or complete the hold"),
  CANCEL("cancel or reverse the hold"),
  REFUND("refund what was captured");

  private final String asked;

  ObjectAction(String asked) {
    this.asked = asked;
  }

  public String asked() {
    return asked;
  }

  public boolean takesAnAmount() {
    return this != CANCEL;
  }

  public boolean undoesMoneyAlreadyTakenOrHeld() {
    return this == REFUND || this == CANCEL;
  }

  public static ObjectAction declared(String action) {
    for (ObjectAction known : values()) {
      if (known.name().equals(action)) {
        return known;
      }
    }
    throw new IllegalArgumentException(
        "action must be one of CAPTURE, CANCEL, REFUND; nothing else is offered on an object of"
            + " this simulator through any surface");
  }
}
