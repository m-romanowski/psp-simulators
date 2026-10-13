package dev.marcinromanowski.pspsim.kernel.spi;

public record AValueAFieldOffers(String value, String label) {

  public AValueAFieldOffers {
    if (value.isBlank()) {
      throw new IllegalArgumentException(
          "a choice with no value sends nothing to the provider, so a tester who picked it would"
              + " have started a payment the provider was never told the shape of");
    }
    if (label.isBlank()) {
      throw new IllegalArgumentException(
          "choice "
              + value
              + " must carry a label, because a wizard offers a tester the label and never the"
              + " value, and a provider's own wire value means nothing to someone who has not read"
              + " that provider's contract");
    }
  }
}
