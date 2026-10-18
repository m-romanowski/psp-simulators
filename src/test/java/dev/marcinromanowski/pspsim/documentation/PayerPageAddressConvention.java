package dev.marcinromanowski.pspsim.documentation;

import dev.marcinromanowski.pspsim.documentation.PayerPageAddresses.Declared;
import dev.marcinromanowski.pspsim.documentation.PayerPageAddresses.Sighting;
import dev.marcinromanowski.pspsim.documentation.PayerPageAddresses.TheBuild;
import dev.marcinromanowski.pspsim.documentation.RoutesInTheBuild.Route;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class PayerPageAddressConvention {

  public static final String THE_RULE =
      "No screen source and no template of this UI hardcodes the address of a payer page. A screen"
          + " finishes a hosted page by the id it printed, and the page itself is a provider route"
          + " a payer opens in a browser. This is narrower than the sentence it enforces: core's"
          + " own ThePayerStep.APageThisSimulatorServes.asATesterReadsIt names the route in the"
          + " wizard and the correlation table prints the route of a recorded hit, both of which"
          + " are the product working, and neither is a link a payer could follow, because the"
          + " one-time token that makes a payer URL usable is withheld everywhere a tester reads.";

  private PayerPageAddressConvention() {}

  public record Verdict(List<String> offences, List<String> read) {

    public Verdict {
      offences = List.copyOf(offences);
      read = List.copyOf(read);
    }
  }

  public static void main(String[] args) {
    if (args.length != 2) {
      throw new IllegalArgumentException(
          "this gate takes the project root and the directory of compiled main classes, in that"
              + " order, and was given "
              + List.of(args));
    }
    Path projectRoot = Path.of(args[0]);
    Path classes = Path.of(args[1]);
    Verdict verdict = verdictOn(projectRoot, classes);
    verdict.read().forEach(line -> System.out.println("verifyNoPayerUrlOnAScreen: " + line));
    if (verdict.offences().isEmpty()) {
      return;
    }
    verdict.offences().forEach(offence -> System.out.println("  - " + offence));
    throw new IllegalStateException(
        THE_RULE
            + " "
            + verdict.offences().size()
            + " place(s) break it, each named above. A provider route answering its own payer URL"
            + " to an API client is the product working and is not read here; a screen source or a"
            + " template of this UI writing that address down is what this gate refuses. The"
            + " addresses it sweeps for are the ones each provider declares as the payer page it"
            + " hosts, so core holds no list of any provider's vocabulary and a provider added later"
            + " widens this gate by declaring its own page. Two shapes are missed on purpose: an"
            + " address split across string literals and one percent-encoded, both of which are"
            + " evasion rather than the mistake a contributor makes.");
  }

  public static Verdict verdictOn(Path projectRoot, Path classes) {
    TheBuild build = PayerPageAddresses.inTheBuild(classes);
    List<Route> routes = RoutesInTheBuild.under(classes);
    List<String> offences = new ArrayList<>();
    List<String> read = new ArrayList<>();

    if (build.declared().isEmpty()) {
      throw new IllegalStateException(
          "no provider in "
              + classes
              + " declares a payer page at all, so this gate would sweep for nothing and report"
              + " that it found nothing. Every provider that serves a payer a page declares the"
              + " route it is served at, so a run that reads no declaration has a broken"
              + " classpath, not a clean tree.");
    }

    offences.addAll(theDeclarationsNoRouteAnswers(build, routes));
    offences.addAll(theProvidersThatServeAPageAndDeclareNone(build));

    List<String> addresses = build.routes();
    List<Path> swept = PayerPageAddresses.theFilesAScreenIsMadeOf(projectRoot);
    List<Sighting> seen = new ArrayList<>();
    for (Path file : swept) {
      String relative = projectRoot.relativize(file).toString();
      seen.addAll(PayerPageAddresses.sightingsIn(relative, textOf(file), addresses));
    }
    for (Sighting sighting : seen) {
      offences.add(
          sighting.file()
              + ":"
              + sighting.line()
              + ": the address of the payer page at "
              + sighting.route()
              + " is written into a screen source: "
              + sighting.asItReads());
    }

    read.add(
        "swept "
            + swept.size()
            + " screen sources and templates for "
            + addresses.size()
            + " payer page addresses "
            + addresses
            + " declared by "
            + build.providersThatDeclareAPage());
    read.add(
        "the providers that serve a page as "
            + PayerPageAddresses.A_PAGE_IS_SERVED_AS
            + " are "
            + new TreeSet<>(build.providersThatServeAPage())
            + " and the providers that declare a payer page are "
            + build.providersThatDeclareAPage()
            + "; every provider in the first list must stand in the second, and the offences"
            + " below, if any, name those that do not");
    return new Verdict(offences, read);
  }

  static List<String> theDeclarationsNoRouteAnswers(TheBuild build, List<Route> routes) {
    Set<String> answered = new TreeSet<>();
    for (Route route : routes) {
      if (route.family().startsWith(RoutesInTheBuild.PROVIDER)) {
        answered.add(route.path());
      }
    }
    List<String> offences = new ArrayList<>();
    for (Declared page : build.declared()) {
      if (!answered.contains(page.route())) {
        offences.add(
            page.declaredIn()
                + " declares the payer page of provider "
                + page.provider()
                + " at "
                + page.route()
                + ", and no provider route of this build answers there, so this gate would sweep"
                + " for an address no payer is ever sent to while the real one went unswept. The"
                + " provider routes this build answers on are "
                + answered);
      }
    }
    return List.copyOf(offences);
  }

  static List<String> theProvidersThatServeAPageAndDeclareNone(TheBuild build) {
    List<String> declaring = build.providersThatDeclareAPage();
    List<String> offences = new ArrayList<>();
    for (String provider : new TreeSet<>(build.providersThatServeAPage())) {
      if (!declaring.contains(provider)) {
        offences.add(
            "provider "
                + provider
                + " serves a page as "
                + PayerPageAddresses.A_PAGE_IS_SERVED_AS
                + " and declares no payer page of its own, so this gate does not know the address"
                + " that page is served at and could not keep it off a screen. A provider that"
                + " hands a payer a page says so with "
                + PayerPageAddresses.THE_DECLARATION
                + ", which is also what tells a tester the page is served here");
      }
    }
    return List.copyOf(offences);
  }

  private static String textOf(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
