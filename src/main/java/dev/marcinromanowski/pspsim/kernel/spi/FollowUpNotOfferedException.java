package dev.marcinromanowski.pspsim.kernel.spi;

public class FollowUpNotOfferedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public FollowUpNotOfferedException(String inTheProvidersWords) {
    super(inTheProvidersWords);
  }
}
