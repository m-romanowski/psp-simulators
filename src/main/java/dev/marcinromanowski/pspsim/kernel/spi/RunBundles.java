package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableAccount;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableFixture;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableRouteAlias;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableWebhookTarget;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.settings.TestRunSettings;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class RunBundles {

  public static final String SCENARIOS = "scenarios.";

  public static final String A_RULE_THIS_RUN_NO_LONGER_HOLDS =
      "defaultScenario names a scenario rule this run no longer holds, so a bundle carrying this"
          + " fixture would name a rule nothing could write back. The one repair is to forget the"
          + " fixture on the Payment methods screen and seed it again against a rule this run"
          + " holds: writing the deleted rule again does not repair it, because a rule written"
          + " again is given a new identity and this build publishes no route that changes the"
          + " rule a seeded fixture names.";

  public sealed interface Export {

    record Rejected(String message) implements Export {}

    record Refused(List<BundleRefusal> refusals) implements Export {

      public Refused {
        refusals = List.copyOf(refusals);
      }
    }

    record Exported(RunBundle bundle) implements Export {}
  }

  private final ProviderAccounts accounts;
  private final RouteAliases routeAliases;
  private final TestRunSettings settings;
  private final DeclaredProviderSettings declaredSettings;
  private final PaymentMethodFixtures fixtures;
  private final ScenarioRules rules;
  private final FixedTimes pinned;
  private final SimulatorClock clock;
  private final TestRunId whenNoTestRunIsNamed;

  public RunBundles(
      ProviderAccounts accounts,
      RouteAliases routeAliases,
      TestRunSettings settings,
      DeclaredProviderSettings declaredSettings,
      PaymentMethodFixtures fixtures,
      ScenarioRules rules,
      FixedTimes pinned,
      SimulatorClock clock,
      TestRunId whenNoTestRunIsNamed) {
    this.accounts = accounts;
    this.routeAliases = routeAliases;
    this.settings = settings;
    this.declaredSettings = declaredSettings;
    this.fixtures = fixtures;
    this.rules = rules;
    this.pinned = pinned;
    this.clock = clock;
    this.whenNoTestRunIsNamed = whenNoTestRunIsNamed;
  }

  public Export of(SimulatorProvider module, @Nullable String declaredTestRunId) {
    TestRunId run;
    try {
      run = TestRunId.declaredOr(declaredTestRunId, whenNoTestRunIsNamed);
    } catch (IllegalArgumentException rejected) {
      return new Export.Rejected(String.valueOf(rejected.getMessage()));
    }

    List<ScenarioRule> held = rules.of(module.id(), run);
    List<NewScenarioRule> again = new ArrayList<>();
    for (ScenarioRule rule : held) {
      again.add(rule.asWrittenAgain());
    }

    List<BundleRefusal> refused = new ArrayList<>();
    for (ScenarioRefusal narrowing : ScenarioDocument.theNarrowingsNoDocumentCarries(again)) {
      refused.add(new BundleRefusal(SCENARIOS + narrowing.property(), narrowing.reason()));
    }

    List<PortableFixture> carriedFixtures = new ArrayList<>();
    List<PaymentMethodFixture> seeded = fixtures.of(module.id(), run);
    for (int i = 0; i < seeded.size(); i++) {
      PaymentMethodFixture fixture = seeded.get(i);
      Integer place = thePlaceOf(fixture.defaultScenario(), held);
      if (fixture.defaultScenario() != null && place == null) {
        refused.add(
            new BundleRefusal(
                "fixtures[" + i + "].defaultScenario", A_RULE_THIS_RUN_NO_LONGER_HOLDS));
      }
      carriedFixtures.add(PortableFixture.of(fixture, place));
    }

    if (!refused.isEmpty()) {
      return new Export.Refused(List.copyOf(refused));
    }

    return new Export.Exported(
        new RunBundle(
            BundleVocabulary.of(module, declaredSettings.of(module.id())),
            run.value(),
            module.id().value(),
            clock.now(run).toString(),
            theClockOf(run),
            theAccountsOf(module, run),
            theWebhookTargetsOf(module, run),
            theRouteAliasesOf(module, run),
            theSettingsOf(module, run),
            List.copyOf(carriedFixtures),
            held.isEmpty() ? null : ScenarioDocument.of(run, module.id(), again)));
  }

  public static PortableAccount theAccountItCanCarry(
      ProviderAccountFields fields, DisclosedAccount disclosed) {
    Set<String> withheld =
        ConfigurationFacts.everythingButTheDeclaredPlainFields(
            fields, disclosed.disclosed().keySet());
    LinkedHashMap<String, String> carried = new LinkedHashMap<>();
    List<String> notCarried = new ArrayList<>();
    for (Map.Entry<String, List<String>> field : disclosed.disclosed().entrySet()) {
      List<String> values = field.getValue();
      if (withheld.contains(field.getKey()) || values.size() > 1) {
        notCarried.add(field.getKey());
        continue;
      }
      if (values.isEmpty()) {
        continue;
      }
      carried.put(field.getKey(), values.getFirst());
    }
    return new PortableAccount(
        disclosed.accountId(),
        Collections.unmodifiableSequencedMap(carried),
        List.copyOf(notCarried));
  }

  public static @Nullable Integer thePlaceOf(@Nullable String scenarioId, List<ScenarioRule> held) {
    if (scenarioId == null) {
      return null;
    }
    for (int i = 0; i < held.size(); i++) {
      if (held.get(i).scenarioId().equals(scenarioId)) {
        return i;
      }
    }
    return null;
  }

  private DeclaredClock theClockOf(TestRunId run) {
    Optional<Instant> fixedNow = pinned.of(run);
    return new DeclaredClock(fixedNow.map(Instant::toString).orElse(null), null, null);
  }

  private List<PortableAccount> theAccountsOf(SimulatorProvider module, TestRunId run) {
    List<PortableAccount> carried = new ArrayList<>();
    for (String accountId : accounts.identitiesOf(module.id(), run)) {
      Optional<DisclosedAccount> disclosed = module.disclosureOf(run, accountId);
      carried.add(
          disclosed
              .map(account -> theAccountItCanCarry(module.accountFields(), account))
              .orElseGet(() -> new PortableAccount(accountId, Map.of(), List.of())));
    }
    return List.copyOf(carried);
  }

  private List<PortableWebhookTarget> theWebhookTargetsOf(SimulatorProvider module, TestRunId run) {
    List<PortableWebhookTarget> carried = new ArrayList<>();
    for (WebhookTarget target : accounts.webhookTargetsOf(module.id(), run)) {
      carried.add(new PortableWebhookTarget(target.accountId(), target.url()));
    }
    return List.copyOf(carried);
  }

  private List<PortableRouteAlias> theRouteAliasesOf(SimulatorProvider module, TestRunId run) {
    List<PortableRouteAlias> carried = new ArrayList<>();
    for (RouteAlias alias : routeAliases.of(module.id(), run)) {
      carried.add(new PortableRouteAlias(alias.from(), alias.to()));
    }
    return List.copyOf(carried);
  }

  private SequencedMap<String, String> theSettingsOf(SimulatorProvider module, TestRunId run) {
    LinkedHashMap<String, String> carried = new LinkedHashMap<>();
    for (Map.Entry<SettingKey, String> setting : settings.of(module.id(), run).entrySet()) {
      carried.put(setting.getKey().key(), setting.getValue());
    }
    return Collections.unmodifiableSequencedMap(carried);
  }
}
