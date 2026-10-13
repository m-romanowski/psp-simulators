package dev.marcinromanowski.pspsim.kernel.spi;

public enum PayerOutcome {
  PAID("paid"),
  DECLINED("met a decline"),
  ABANDONED("left without paying");

  private final String whatThePayerDid;

  PayerOutcome(String whatThePayerDid) {
    this.whatThePayerDid = whatThePayerDid;
  }

  public String whatThePayerDid() {
    return whatThePayerDid;
  }

  public boolean namesACard() {
    return this == PAID;
  }

  public static PayerOutcome declared(String outcome) {
    for (PayerOutcome known : values()) {
      if (known.name().equals(outcome)) {
        return known;
      }
    }
    throw new IllegalArgumentException(
        "outcome must be one of PAID, DECLINED, ABANDONED; those are what a payer step does to a"
            + " payment, and which of them an object admits is the provider's to declare and not"
            + " this simulator's, as is what a tester reads on each");
  }
}
