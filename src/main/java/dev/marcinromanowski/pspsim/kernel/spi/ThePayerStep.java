package dev.marcinromanowski.pspsim.kernel.spi;

public sealed interface ThePayerStep {

  String asATesterReadsIt();

  record APageThisSimulatorServes(String route) implements ThePayerStep {

    public APageThisSimulatorServes {
      if (route.isBlank()) {
        throw new IllegalArgumentException(
            "a way of starting a payment that leaves a payer a page this simulator serves must name"
                + " the route it is served at, because that route is what a gate compares against"
                + " the ones this build answers on: a page named in prose alone can say this"
                + " simulator serves it while nothing does, or say it does not while it does");
      }
      if (!route.startsWith("/")) {
        throw new IllegalArgumentException(
            "the payer page route "
                + route
                + " is not a path this build can answer on, so nothing could ever match it against"
                + " the routes this build serves. A page the vendor hosts on its own origin is not"
                + " a route of this build and is declared as NoPageThisSimulatorServes instead,"
                + " which says in that provider's words what stands in its place");
      }
    }

    @Override
    public String asATesterReadsIt() {
      return "This simulator serves the payer's page itself, at "
          + route
          + ", so a payer reaches it in a browser here and never on the vendor's own host. The"
          + " link the provider answers with points at that page and is never printed on a"
          + " screen, so from this wizard you finish it with the payer step on the object's own"
          + " screen.";
    }
  }

  record NoPageThisSimulatorServes(String whatStandsInItsPlace) implements ThePayerStep {

    public NoPageThisSimulatorServes {
      if (whatStandsInItsPlace.isBlank()) {
        throw new IllegalArgumentException(
            "a way of starting a payment that leaves a payer no page this simulator serves must say"
                + " in this provider's own words what stands in its place, because a wizard"
                + " reporting that absence in this simulator's words would be reporting a decision"
                + " this simulator did not make");
      }
    }

    @Override
    public String asATesterReadsIt() {
      return whatStandsInItsPlace;
    }
  }
}
