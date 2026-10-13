package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ThePagesADeclarationPromises {

  public sealed interface Agreement {

    record TheDeclarationAndTheRoutesAgree() implements Agreement {}

    record TheyDisagree(String why) implements Agreement {}
  }

  private ThePagesADeclarationPromises() {}

  public static Agreement of(
      ProviderOriginationFields declared, Set<String> theRoutesThisBuildAnswersOn) {
    List<String> promised = new ArrayList<>();
    for (DeclaredOrigination way : declared.waysToStartAPayment()) {
      if (way.thePayerStep() instanceof ThePayerStep.APageThisSimulatorServes page
          && !theRoutesThisBuildAnswersOn.contains(page.route())) {
        promised.add(way.name() + " at " + page.route());
      }
    }
    if (promised.isEmpty()) {
      return new Agreement.TheDeclarationAndTheRoutesAgree();
    }
    return new Agreement.TheyDisagree(
        "provider "
            + declared.provider().value()
            + " tells a tester that this simulator serves the payer's page of "
            + promised
            + ", and this build answers on no such route: it answers on "
            + theRoutesThisBuildAnswersOn
            + ", so a tester is sent to a page that is not there");
  }
}
