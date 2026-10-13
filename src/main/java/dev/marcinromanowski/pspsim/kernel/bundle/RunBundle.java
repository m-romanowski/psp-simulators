package dev.marcinromanowski.pspsim.kernel.bundle;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.scenario.PortableScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record RunBundle(
    @Nullable BundleCompatibility compatibility,
    @Nullable String testRunId,
    @Nullable String provider,
    @Nullable String exportedAt,
    @Nullable DeclaredClock clock,
    @Nullable List<PortableAccount> accounts,
    @Nullable List<PortableWebhookTarget> webhooks,
    @Nullable List<PortableRouteAlias> routeAliases,
    @Nullable Map<String, String> settings,
    @Nullable List<PortableFixture> fixtures,
    @Nullable ScenarioDocument scenarios) {

  public static final String PROVIDER = "provider";

  public static final String COMPATIBILITY = "compatibility";

  public static final String SCENARIOS_PROVIDER = "scenarios.provider";

  public static final String A_BUNDLE_OF_ONE_PROVIDER_DESCRIBES_NOTHING_OF_ANOTHER =
      "an account field, a payment-method field and a preset mean different things to different"
          + " providers, so a bundle of one provider describes nothing of another";

  public static final String THE_DOCUMENT_NAMES_NO_PROVIDER = "names no provider at all";

  public static String theProviderThatWroteIt(ProviderId addressed, @Nullable String carried) {
    String wroteIt =
        carried == null || carried.isBlank()
            ? THE_DOCUMENT_NAMES_NO_PROVIDER
            : "was exported from " + carried;
    return "provider must name the provider this request addresses: this document "
        + wroteIt
        + " and this request addresses "
        + addressed.value()
        + ", and "
        + A_BUNDLE_OF_ONE_PROVIDER_DESCRIBES_NOTHING_OF_ANOTHER;
  }

  public static final String ONE_BUNDLE_NAMES_ONE_PROVIDER =
      "scenarios.provider must name the same provider the bundle does, because the rules a bundle"
          + " carries are configured for the provider it was exported from, and a bundle whose two"
          + " halves name different providers would write one half into a run the other cannot use";

  public RunBundle {
    accounts = accounts == null ? null : List.copyOf(accounts);
    webhooks = webhooks == null ? null : List.copyOf(webhooks);
    routeAliases = routeAliases == null ? null : List.copyOf(routeAliases);
    settings = settings == null ? null : theSettingsInTheOrderTheBundleCarriesThem(settings);
    fixtures = fixtures == null ? null : List.copyOf(fixtures);
  }

  public List<PortableAccount> carriedAccounts() {
    List<PortableAccount> carried = accounts;
    return carried == null ? List.of() : carried;
  }

  public List<PortableWebhookTarget> carriedWebhooks() {
    List<PortableWebhookTarget> carried = webhooks;
    return carried == null ? List.of() : carried;
  }

  public List<PortableRouteAlias> carriedRouteAliases() {
    List<PortableRouteAlias> carried = routeAliases;
    return carried == null ? List.of() : carried;
  }

  public Map<String, String> carriedSettings() {
    Map<String, String> carried = settings;
    return carried == null ? Map.of() : carried;
  }

  public List<PortableFixture> carriedFixtures() {
    List<PortableFixture> carried = fixtures;
    return carried == null ? List.of() : carried;
  }

  public RunBundle carryingInstead(List<PortableFixture> instead) {
    return new RunBundle(
        compatibility,
        testRunId,
        provider,
        exportedAt,
        clock,
        accounts,
        webhooks,
        routeAliases,
        settings,
        List.copyOf(instead),
        scenarios);
  }

  public List<PortableScenario> carriedRules() {
    ScenarioDocument document = scenarios;
    return document == null ? List.of() : document.carriedRules();
  }

  public List<BundleRefusal> theReasonsItCannotBeTakenBackBy(
      ProviderId addressed, BundleCompatibility declaredHere) {
    List<BundleRefusal> refused = new ArrayList<>();
    if (!addressed.value().equals(provider)) {
      refused.add(new BundleRefusal(PROVIDER, theProviderThatWroteIt(addressed, provider)));
    }
    ScenarioDocument document = scenarios;
    if (document != null && document.provider() != null && !document.provider().equals(provider)) {
      refused.add(new BundleRefusal(SCENARIOS_PROVIDER, ONE_BUNDLE_NAMES_ONE_PROVIDER));
    }
    BundleCompatibility written = compatibility;
    if (written == null) {
      refused.add(
          new BundleRefusal(
              COMPATIBILITY, BundleCompatibility.A_BUNDLE_NAMES_WHAT_IT_WAS_WRITTEN_AGAINST));
    } else {
      refused.addAll(written.theTermsThisBuildNoLongerDeclares(declaredHere));
    }
    int rulesCarried = carriedRules().size();
    List<PortableFixture> carried = carriedFixtures();
    for (int i = 0; i < carried.size(); i++) {
      refused.addAll(
          carried.get(i).theRuleItNamesThatThisBundleHasNot("fixtures[" + i + "]", rulesCarried));
    }
    return List.copyOf(refused);
  }

  private static SequencedMap<String, String> theSettingsInTheOrderTheBundleCarriesThem(
      Map<String, String> carried) {
    LinkedHashMap<String, String> inTheOrderTheBundleCarriesThem = new LinkedHashMap<>(carried);
    return Collections.unmodifiableSequencedMap(inTheOrderTheBundleCarriesThem);
  }
}
