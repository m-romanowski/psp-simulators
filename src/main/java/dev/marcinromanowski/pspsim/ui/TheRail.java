package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public final class TheRail {

  private static final boolean THE_SELECTION_TRAVELS_WITH_IT = true;

  private static final boolean IT_IS_THE_SAME_PAGE_FOR_EVERY_PROVIDER = false;

  private static final Comparator<RailDestination> WHERE_ITS_GROUP_PUTS_IT =
      Comparator.comparingInt(RailDestination::order).thenComparing(RailDestination::label);

  private final ContributedScreens contributed;

  public TheRail(ContributedScreens contributed, ProviderRegistry providers) {
    this.contributed = contributed;
    refuseAScreenContributedForAProviderThisBuildDoesNotCarry(contributed, providers);
    refuseATabATesterCouldNotTellFromAnother(contributed);
  }

  public List<RailGroupOnScreen> of(@Nullable String provider) {
    return grouped(everyTab(contributed.all(), provider));
  }

  public String theScreenToReturnTo(@Nullable String returnTo, String whenItNamesNoScreen) {
    if (returnTo != null && everyScreenAFormMayComeBackTo(contributed).contains(returnTo)) {
      return returnTo;
    }
    return whenItNamesNoScreen;
  }

  static List<String> everyScreenAFormMayComeBackTo(ContributedScreens contributed) {
    List<String> paths = new ArrayList<>();
    for (RailDestination destination : theScreensThisSimulatorCarries()) {
      if (destination.theSelectionTravelsWithIt()) {
        paths.add(destination.path());
      }
    }
    for (ContributedScreen screen : contributed.all()) {
      paths.add(screen.path());
    }
    return List.copyOf(paths);
  }

  public static List<String> everyDestinationOnOffer(ContributedScreens contributed) {
    List<String> paths = new ArrayList<>();
    for (RailDestination destination : theScreensThisSimulatorCarries()) {
      paths.add(destination.path());
    }
    for (ContributedScreen screen : contributed.all()) {
      paths.add(screen.path());
    }
    return List.copyOf(paths);
  }

  static List<RailDestination> everyTab(
      List<ContributedScreen> contributed, @Nullable String provider) {
    List<RailDestination> tabs = new ArrayList<>(theScreensThisSimulatorCarries());
    for (ContributedScreen screen : contributed) {
      if (screen.provider().equals(provider)) {
        tabs.add(screen.asATab());
      }
    }
    return List.copyOf(tabs);
  }

  private static void refuseAScreenContributedForAProviderThisBuildDoesNotCarry(
      ContributedScreens contributed, ProviderRegistry providers) {
    for (ContributedScreen screen : contributed.all()) {
      if (providers.find(screen.provider()).isEmpty()) {
        throw new IllegalStateException(
            screen.file()
                + " is contributed as a screen of the provider "
                + screen.provider()
                + ", and this build carries no provider of that name, so the tab it asks for would"
                + " be offered to nobody and the route that serves it would answer a provider that"
                + " is not here. This build carries "
                + providers.names()
                + ".");
      }
    }
  }

  private static void refuseATabATesterCouldNotTellFromAnother(ContributedScreens contributed) {
    Map<String, RailDestination> carried = new LinkedHashMap<>();
    for (RailDestination destination : theScreensThisSimulatorCarries()) {
      carried.put(asARailIsRead(destination.label()), destination);
    }
    Map<String, ContributedScreen> offered = new LinkedHashMap<>();
    for (ContributedScreen screen : contributed.all()) {
      RailDestination itAlreadyCarries = carried.get(asARailIsRead(screen.label()));
      if (itAlreadyCarries != null) {
        throw new IllegalStateException(
            theRefusalOfATabThatReadsLike(
                screen,
                itAlreadyCarries.label()
                    + "\" under "
                    + itAlreadyCarries.group().label()
                    + ", which opens "
                    + itAlreadyCarries.path()));
      }
      ContributedScreen anotherOfTheSameProvider =
          offered.put(screen.provider() + "/" + asARailIsRead(screen.label()), screen);
      if (anotherOfTheSameProvider != null) {
        throw new IllegalStateException(
            theRefusalOfATabThatReadsLike(
                screen,
                anotherOfTheSameProvider.label()
                    + "\", declared by "
                    + anotherOfTheSameProvider.file()));
      }
    }
  }

  private static String theRefusalOfATabThatReadsLike(ContributedScreen screen, String theOther) {
    return screen.file()
        + " declares <meta name=\""
        + ContributedScreens.TAB
        + "\" content=\""
        + screen.label()
        + "\">, and the navigation of "
        + screen.provider()
        + " already offers a tab reading \""
        + theOther
        + ". A tester reading two tabs of one name cannot tell which screen either one opens, and"
        + " the contributed tab is drawn first, so the name a tester already knows would lead"
        + " somewhere else. Name this tab for what this screen is and for nothing that is already"
        + " there.";
  }

  private static String asARailIsRead(String label) {
    return label.strip().toLowerCase(Locale.ROOT);
  }

  static List<RailGroupOnScreen> grouped(List<RailDestination> destinations) {
    List<RailGroupOnScreen> groups = new ArrayList<>();
    for (RailGroup group : RailGroup.values()) {
      List<RailDestination> items = new ArrayList<>();
      for (RailDestination destination : destinations) {
        if (destination.group() == group) {
          items.add(destination);
        }
      }
      items.sort(WHERE_ITS_GROUP_PUTS_IT);
      if (!items.isEmpty()) {
        groups.add(new RailGroupOnScreen(group.label(), items));
      }
    }
    return List.copyOf(groups);
  }

  public static List<RailDestination> theScreensThisSimulatorCarries() {
    return List.of(
        daily(10, "Payments", "/ui/objects", "objects"),
        daily(20, "Start a payment", "/ui/objects/new", "origination"),
        daily(30, "Look a payment up", ReadPage.PATH, ReadPage.VIEW),
        daily(40, "Webhooks", "/ui/webhooks", "webhooks"),
        daily(50, "Retries", "/ui/idempotency", "inspector"),
        setUp(10, "Configuration", "/ui/configuration", "configuration"),
        setUp(20, "Payment methods", "/ui/payment-methods", "payment-methods"),
        setUp(30, "Scenarios", "/ui/scenarios", "scenarios"),
        setUp(40, "Compare scenarios", "/ui/scenarios/diff", "scenario-diff"),
        setUp(50, "Personas", "/ui/personas", "personas"),
        evidence(10, "This test run", "/ui", "workbench"),
        evidence(20, "Dashboard", "/ui/dashboard", "dashboard"),
        evidence(30, "Audit", "/ui/audit", "audit"),
        evidence(40, "Timeline", "/ui/timeline", "timeline"),
        evidence(50, "Correlation", "/ui/correlation", "correlation"),
        elsewhere(10, "Run bundle", "/ui/run-bundle", "run-bundle"),
        elsewhere(20, "Reproduction bundle", "/ui/repro-bundle", "repro-bundle"),
        elsewhere(30, "Checks against the real PSP", "/ui/smoke", "smoke"),
        elsewhere(40, "Connect my app", "/ui/pairing", "pairing"),
        new RailDestination(
            RailGroup.TAKE_IT_ELSEWHERE,
            "API",
            "/openapi/index.html",
            "",
            50,
            IT_IS_THE_SAME_PAGE_FOR_EVERY_PROVIDER));
  }

  private static RailDestination daily(int order, String label, String path, String screen) {
    return new RailDestination(
        RailGroup.DAILY_WORK, label, path, screen, order, THE_SELECTION_TRAVELS_WITH_IT);
  }

  private static RailDestination setUp(int order, String label, String path, String screen) {
    return new RailDestination(
        RailGroup.SET_UP, label, path, screen, order, THE_SELECTION_TRAVELS_WITH_IT);
  }

  private static RailDestination evidence(int order, String label, String path, String screen) {
    return new RailDestination(
        RailGroup.EVIDENCE, label, path, screen, order, THE_SELECTION_TRAVELS_WITH_IT);
  }

  private static RailDestination elsewhere(int order, String label, String path, String screen) {
    return new RailDestination(
        RailGroup.TAKE_IT_ELSEWHERE, label, path, screen, order, THE_SELECTION_TRAVELS_WITH_IT);
  }
}
