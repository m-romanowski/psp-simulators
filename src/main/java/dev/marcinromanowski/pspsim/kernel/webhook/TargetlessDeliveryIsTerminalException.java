package dev.marcinromanowski.pspsim.kernel.webhook;

public final class TargetlessDeliveryIsTerminalException extends RuntimeException {

  public static final String ONLY_AN_UNDELIVERABLE_DELIVERY_HAS_NO_TARGET =
      "a delivery with no target is undeliverable from the moment it is recorded and stays so;"
          + " no other state can hold one, because nothing could ever attempt it";

  private static final long serialVersionUID = 1L;

  public TargetlessDeliveryIsTerminalException() {
    super(ONLY_AN_UNDELIVERABLE_DELIVERY_HAS_NO_TARGET);
  }
}
