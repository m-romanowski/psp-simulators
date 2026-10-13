package dev.marcinromanowski.pspsim.kernel.spi;

public final class AKindOfObjectATestNames {

  public static final String WHAT_A_PROVIDER_WOULD_SAY_ONE_IS =
      "what a provider module says one of these is, in the sentence this test stands in for";

  public static final ProviderObjectKind ONE = named("a hold on a card");

  private AKindOfObjectATestNames() {}

  public static ProviderObjectKind named(String asThisProviderNamesIt) {
    return new ProviderObjectKind(asThisProviderNamesIt, WHAT_A_PROVIDER_WOULD_SAY_ONE_IS);
  }
}
