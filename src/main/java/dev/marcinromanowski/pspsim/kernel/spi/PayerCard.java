package dev.marcinromanowski.pspsim.kernel.spi;

public record PayerCard(String alias, String label) {

  public PayerCard {
    if (alias.isBlank()) {
      throw new IllegalArgumentException(
          "a card a payer may name with no alias names nothing, because the alias is what a"
              + " completion sends back when a tester picks the card");
    }
    if (label.isBlank()) {
      throw new IllegalArgumentException(
          "card "
              + alias
              + " must carry a label, because a tester picks the card a payer used by what that"
              + " card is and not by the alias a provider happens to spell it with");
    }
  }
}
