package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleEntry;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleReport;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableAccount;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableCard;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableFixture;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableRouteAlias;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableWebhookTarget;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.clock.ClockChange;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
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
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class BundleAdmission {

  public static final String THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK =
      "the run bundle this request asked this provider to take back";

  public static final String AN_IMPORT_TAKES_BACK_A_BUNDLE =
      "bundle must carry the run bundle this import takes back, because an import naming none has"
          + " nothing to create, update or skip and would be reported as an import that did"
          + " nothing rather than as a request that named nothing";

  public static final String AN_ACCOUNT_IS_ADDRESSED_BY_ITS_LABEL =
      "accountId must name the account this entry configures, because a secret supplied at import"
          + " time is addressed to an account by that label and an entry carrying none could never"
          + " be given one";

  public static final String A_BUNDLE_CARRIES_NO_SECRET =
      "a reusable bundle never carries a value this provider declares secret: supply it for this"
          + " import under secrets, or configure the account directly, because a bundle is copied"
          + " into files, pasted into screens and posted back";

  public static final String A_KEY_THIS_PROVIDER_READS_NOTHING_OF =
      "a bundle carries only the account fields this provider declares plainly, and this key is"
          + " not one of them, so nothing here could tell whether the value beside it is a"
          + " credential";

  public static final String A_SECRET_IS_SUPPLIED_TO_AN_ACCOUNT_THE_BUNDLE_CARRIES =
      "secrets are addressed to an account this bundle configures, and this bundle configures no"
          + " account by that label, so the value supplied here would be written nowhere";

  public static final String ONLY_A_DECLARED_SECRET_IS_SUPPLIED_APART =
      "secrets carry only the fields this provider declares secret, because a field the bundle"
          + " itself carries would then have two values in one import and nothing here says which"
          + " of them the run should end up holding";

  public sealed interface Verdict {

    ActionVerdict.@Nullable Refused theRefusalItReports();

    record Rejected(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK));
      }
    }

    record Refused(List<BundleRefusal> refusals) implements Verdict {

      public Refused {
        refusals = List.copyOf(refusals);
      }

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.refused(
            RefusalSubject.noObjectWasNamed(THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK));
      }
    }

    record Conflicting(List<BundleRefusal> refusals) implements Verdict {

      public Conflicting {
        refusals = List.copyOf(refusals);
      }

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.conflicting(
            RefusalSubject.noObjectWasNamed(THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK));
      }
    }

    record Taken(BundleReport report) implements Verdict {

      @Override
      public ActionVerdict.@Nullable Refused theRefusalItReports() {
        return null;
      }
    }
  }

  private final ProviderConfigurations configurations;
  private final ScenarioAdmission scenarios;
  private final ScenarioRules rules;
  private final PaymentMethodFixtureAdmission fixtureAdmission;
  private final ProviderAccounts accounts;
  private final RouteAliases routeAliases;
  private final TestRunSettings settings;
  private final DeclaredProviderSettings declaredSettings;
  private final PaymentMethodFixtures fixtures;
  private final FixedTimes pinned;
  private final TestRuns testRuns;
  private final UnitOfWork unitOfWork;
  private final TestRunId whenNoTestRunIsNamed;

  public BundleAdmission(
      ProviderConfigurations configurations,
      ScenarioAdmission scenarios,
      ScenarioRules rules,
      PaymentMethodFixtureAdmission fixtureAdmission,
      ProviderAccounts accounts,
      RouteAliases routeAliases,
      TestRunSettings settings,
      DeclaredProviderSettings declaredSettings,
      PaymentMethodFixtures fixtures,
      FixedTimes pinned,
      TestRuns testRuns,
      UnitOfWork unitOfWork,
      TestRunId whenNoTestRunIsNamed) {
    this.configurations = configurations;
    this.scenarios = scenarios;
    this.rules = rules;
    this.fixtureAdmission = fixtureAdmission;
    this.accounts = accounts;
    this.routeAliases = routeAliases;
    this.settings = settings;
    this.declaredSettings = declaredSettings;
    this.fixtures = fixtures;
    this.pinned = pinned;
    this.testRuns = testRuns;
    this.unitOfWork = unitOfWork;
    this.whenNoTestRunIsNamed = whenNoTestRunIsNamed;
  }

  public Verdict takeBack(SimulatorProvider module, DeclaredBundleImport declared) {
    RunBundle bundle = declared.bundle();
    if (bundle == null) {
      return new Verdict.Rejected(AN_IMPORT_TAKES_BACK_A_BUNDLE);
    }

    TestRunId run;
    try {
      run =
          TestRunId.declaredOr(
              declared.testRunId() == null ? bundle.testRunId() : declared.testRunId(),
              whenNoTestRunIsNamed);
    } catch (IllegalArgumentException rejected) {
      return new Verdict.Rejected(String.valueOf(rejected.getMessage()));
    }

    Map<String, Map<String, String>> supplied = declared.theSecretsItSupplies();
    List<BundleRefusal> refused =
        new ArrayList<>(
            bundle.theReasonsItCannotBeTakenBackBy(
                module.id(), BundleVocabulary.of(module, declaredSettings.of(module.id()))));
    refused.addAll(theSecretsThisBundleMayNotCarry(module.accountFields(), bundle));
    refused.addAll(theSecretsThisImportMayNotSupply(module.accountFields(), bundle, supplied));
    if (!refused.isEmpty()) {
      return new Verdict.Refused(List.copyOf(refused));
    }

    ClockChange clockChange;
    ProviderConfiguration configuration;
    try {
      clockChange = ClockChange.of(bundle.clock());
      configuration =
          ProviderConfigurationGrammar.configurationOf(
              run, theConfigurationItWouldApply(run, bundle, supplied));
    } catch (IllegalArgumentException rejected) {
      return new Verdict.Rejected(String.valueOf(rejected.getMessage()));
    }

    ProviderConfigurations.Storable storable;
    try {
      storable = configurations.theWriteItWouldMake(module, configuration);
    } catch (ProviderConfigurationRejectedException refusedByProvider) {
      ConfigurationRefusal named =
          ConfigurationRefusal.of(String.valueOf(refusedByProvider.getMessage()));
      return new Verdict.Refused(
          List.of(
              new BundleRefusal(
                  theConfigurationPropertyOf(named, refusedByProvider.property()),
                  named.message())));
    }

    List<NewScenarioRule> incoming = new ArrayList<>();
    ScenarioDocument document = theDocumentItWouldWrite(run, bundle);
    if (document != null) {
      ScenarioAdmission.Reception reception = scenarios.theRulesItWouldWrite(module, document);
      if (reception instanceof ScenarioAdmission.Reception.Rejected rejected) {
        return new Verdict.Rejected(rejected.message());
      }
      if (reception instanceof ScenarioAdmission.Reception.Refused refusedRules) {
        return new Verdict.Refused(prefixed(refusedRules.refusals()));
      }
      incoming.addAll(((ScenarioAdmission.Reception.Receivable) reception).rules());
    }

    List<BundleRefusal> unseedable = theFixturesThisProviderWouldNotSeed(module, run, bundle);
    if (!unseedable.isEmpty()) {
      return new Verdict.Refused(unseedable);
    }

    Planned planned = thePlanFor(module, run, bundle, supplied);
    List<String> namesOfSecretsSupplied = theNamesOfTheSecretsSupplied(supplied);
    if (declared.writesNothing()) {
      return new Verdict.Taken(
          BundleReport.of(
              run.value(), module.id().value(), false, planned.entries(), namesOfSecretsSupplied));
    }
    return theBundleItWrites(
        module,
        run,
        bundle,
        configuration,
        clockChange,
        storable,
        List.copyOf(incoming),
        planned,
        namesOfSecretsSupplied);
  }

  public static List<BundleRefusal> theSecretsThisBundleMayNotCarry(
      ProviderAccountFields fields, RunBundle bundle) {
    List<BundleRefusal> refused = new ArrayList<>();
    List<PortableAccount> carried = bundle.carriedAccounts();
    for (int i = 0; i < carried.size(); i++) {
      PortableAccount account = carried.get(i);
      String prefix = "accounts[" + i + "]";
      String accountId = account.accountId();
      if (accountId == null || accountId.isBlank()) {
        refused.add(new BundleRefusal(prefix + ".accountId", AN_ACCOUNT_IS_ADDRESSED_BY_ITS_LABEL));
      }
      Set<String> secret = lowercased(fields.secretKeys());
      Set<String> withheld =
          ConfigurationFacts.everythingButTheDeclaredPlainFields(
              fields, account.carriedConfiguration().keySet());
      for (String key : account.carriedConfiguration().keySet()) {
        if (!withheld.contains(key)) {
          continue;
        }
        refused.add(
            new BundleRefusal(
                prefix + ".configuration." + key,
                secret.contains(key.toLowerCase(Locale.ROOT))
                    ? A_BUNDLE_CARRIES_NO_SECRET
                    : A_KEY_THIS_PROVIDER_READS_NOTHING_OF));
      }
    }
    return List.copyOf(refused);
  }

  public static List<BundleRefusal> theSecretsThisImportMayNotSupply(
      ProviderAccountFields fields, RunBundle bundle, Map<String, Map<String, String>> supplied) {
    Set<String> configured = new LinkedHashSet<>();
    for (PortableAccount account : bundle.carriedAccounts()) {
      String accountId = account.accountId();
      if (accountId != null) {
        configured.add(accountId);
      }
    }
    Set<String> secret = lowercased(fields.secretKeys());
    List<BundleRefusal> refused = new ArrayList<>();
    for (Map.Entry<String, Map<String, String>> account : supplied.entrySet()) {
      if (!configured.contains(account.getKey())) {
        refused.add(
            new BundleRefusal(
                "secrets." + account.getKey(),
                A_SECRET_IS_SUPPLIED_TO_AN_ACCOUNT_THE_BUNDLE_CARRIES));
        continue;
      }
      for (String key : account.getValue().keySet()) {
        if (!secret.contains(key.toLowerCase(Locale.ROOT))) {
          refused.add(
              new BundleRefusal(
                  "secrets." + account.getKey() + "." + key,
                  ONLY_A_DECLARED_SECRET_IS_SUPPLIED_APART));
        }
      }
    }
    return List.copyOf(refused);
  }

  public static List<String> theNamesOfTheSecretsSupplied(
      Map<String, Map<String, String>> supplied) {
    List<String> named = new ArrayList<>();
    for (Map.Entry<String, Map<String, String>> account : supplied.entrySet()) {
      for (String key : account.getValue().keySet()) {
        named.add(account.getKey() + "." + key);
      }
    }
    return List.copyOf(named);
  }

  public static DeclaredConfiguration theConfigurationItWouldApply(
      TestRunId run, RunBundle bundle, Map<String, Map<String, String>> supplied) {
    List<DeclaredAccount> declaredAccounts = new ArrayList<>();
    for (PortableAccount account : bundle.carriedAccounts()) {
      String accountId = account.accountId();
      Map<String, String> secrets =
          accountId == null ? Map.of() : supplied.getOrDefault(accountId, Map.of());
      declaredAccounts.add(new DeclaredAccount(accountId, account.alsoCarrying(secrets)));
    }
    List<DeclaredWebhookTarget> declaredWebhooks = new ArrayList<>();
    for (PortableWebhookTarget target : bundle.carriedWebhooks()) {
      declaredWebhooks.add(new DeclaredWebhookTarget(target.accountId(), target.url()));
    }
    List<DeclaredRouteAlias> declaredAliases = new ArrayList<>();
    for (PortableRouteAlias alias : bundle.carriedRouteAliases()) {
      declaredAliases.add(new DeclaredRouteAlias(alias.from(), alias.to()));
    }
    return new DeclaredConfiguration(
        run.value(),
        bundle.clock(),
        List.copyOf(declaredAccounts),
        List.copyOf(declaredWebhooks),
        List.copyOf(declaredAliases),
        bundle.carriedSettings());
  }

  public static DeclaredPaymentMethodFixture theFixtureItWouldSeed(
      TestRunId run, PortableFixture fixture, List<String> theRulesJustWritten) {
    PortableCard card = fixture.card();
    Integer place = fixture.triggersRuleAt();
    return new DeclaredPaymentMethodFixture(
        run.value(),
        fixture.merchantId(),
        fixture.ownerId(),
        fixture.type(),
        fixture.alias(),
        card == null
            ? null
            : new DeclaredCard(
                card.brand(), card.last4(), card.expMonth(), card.expYear(), card.funding()),
        fixture.carriedProviderIds(),
        place == null || place >= theRulesJustWritten.size()
            ? null
            : theRulesJustWritten.get(place));
  }

  private List<BundleRefusal> theFixturesThisProviderWouldNotSeed(
      SimulatorProvider module, TestRunId run, RunBundle bundle) {
    List<BundleRefusal> refused = new ArrayList<>();
    List<PortableFixture> carried = bundle.carriedFixtures();
    for (int i = 0; i < carried.size(); i++) {
      PaymentMethodFixtureAdmission.Verdict would =
          fixtureAdmission.theReasonsItWouldNotSeed(
              module,
              theFixtureItWouldSeed(run, carried.get(i), List.of()),
              ObjectOrigin.CONTROL_API);
      if (would == null) {
        continue;
      }
      BundleRefusal refusal = theFixtureRefusalOf("fixtures[" + i + "]", would);
      if (refusal != null) {
        refused.add(refusal);
      }
    }
    return List.copyOf(refused);
  }

  private @Nullable ScenarioDocument theDocumentItWouldWrite(TestRunId run, RunBundle bundle) {
    ScenarioDocument carried = bundle.scenarios();
    return carried == null || carried.carriedRules().isEmpty() ? null : carried.rewrittenFor(run);
  }

  private record Planned(List<BundleEntry> entries, List<Integer> theFixturesItWouldSeed) {

    Planned {
      entries = List.copyOf(entries);
      theFixturesItWouldSeed = List.copyOf(theFixturesItWouldSeed);
    }
  }

  private Planned thePlanFor(
      SimulatorProvider module,
      TestRunId run,
      RunBundle bundle,
      Map<String, Map<String, String>> supplied) {
    List<BundleEntry> planned = new ArrayList<>();
    theClockItWouldSet(run, bundle.clock(), planned);
    theAccountsItWouldConfigure(module, run, bundle, supplied, planned);
    theWebhookTargetsItWouldBind(module, run, bundle, planned);
    theRouteAliasesItWouldBind(module, run, bundle, planned);
    theSettingsItWouldMerge(module, run, bundle, planned);
    theRulesItWouldAdd(bundle, planned);
    return new Planned(planned, theFixturesItWouldSeed(module, run, bundle, planned));
  }

  private void theClockItWouldSet(
      TestRunId run, @Nullable DeclaredClock clock, List<BundleEntry> planned) {
    if (clock == null) {
      return;
    }
    Instant here = pinned.of(run).orElse(null);
    Instant asked =
        ClockChange.of(clock) instanceof ClockChange.PinnedTo pinning ? pinning.fixedNow() : null;
    if (Objects.equals(here, asked)) {
      planned.add(BundleEntry.skipped(BundleEntry.CLOCK, "fixedNow"));
      return;
    }
    planned.add(
        here == null
            ? BundleEntry.created(BundleEntry.CLOCK, "fixedNow")
            : BundleEntry.updated(BundleEntry.CLOCK, "fixedNow"));
  }

  private void theAccountsItWouldConfigure(
      SimulatorProvider module,
      TestRunId run,
      RunBundle bundle,
      Map<String, Map<String, String>> supplied,
      List<BundleEntry> planned) {
    List<String> configured = accounts.identitiesOf(module.id(), run);
    for (PortableAccount account : bundle.carriedAccounts()) {
      String accountId = String.valueOf(account.accountId());
      if (!configured.contains(accountId)) {
        planned.add(BundleEntry.created(BundleEntry.ACCOUNT, accountId));
        continue;
      }
      planned.add(
          theSameAccountAsHere(module, run, account, supplied)
              ? BundleEntry.skipped(BundleEntry.ACCOUNT, accountId)
              : BundleEntry.updated(BundleEntry.ACCOUNT, accountId));
    }
  }

  private boolean theSameAccountAsHere(
      SimulatorProvider module,
      TestRunId run,
      PortableAccount account,
      Map<String, Map<String, String>> supplied) {
    String accountId = String.valueOf(account.accountId());
    if (!supplied.getOrDefault(accountId, Map.of()).isEmpty()) {
      return false;
    }
    Optional<DisclosedAccount> here = module.disclosureOf(run, accountId);
    return here.isPresent()
        && RunBundles.theAccountItCanCarry(module.accountFields(), here.get())
            .carriedConfiguration()
            .equals(account.carriedConfiguration());
  }

  private void theWebhookTargetsItWouldBind(
      SimulatorProvider module, TestRunId run, RunBundle bundle, List<BundleEntry> planned) {
    Set<String> bound = new LinkedHashSet<>();
    for (WebhookTarget target : accounts.webhookTargetsOf(module.id(), run)) {
      bound.add(target.accountId() + " " + target.url());
    }
    for (PortableWebhookTarget target : bundle.carriedWebhooks()) {
      String name = target.accountId() + " " + target.url();
      planned.add(
          bound.contains(name)
              ? BundleEntry.skipped(BundleEntry.WEBHOOK_TARGET, name)
              : BundleEntry.created(BundleEntry.WEBHOOK_TARGET, name));
    }
  }

  private void theRouteAliasesItWouldBind(
      SimulatorProvider module, TestRunId run, RunBundle bundle, List<BundleEntry> planned) {
    Map<String, String> bound = new LinkedHashMap<>();
    for (RouteAlias alias : routeAliases.of(module.id(), run)) {
      bound.put(alias.from(), alias.to());
    }
    for (PortableRouteAlias alias : bundle.carriedRouteAliases()) {
      String from = String.valueOf(alias.from());
      if (!bound.containsKey(from)) {
        planned.add(BundleEntry.created(BundleEntry.ROUTE_ALIAS, from));
        continue;
      }
      planned.add(
          Objects.equals(bound.get(from), alias.to())
              ? BundleEntry.skipped(BundleEntry.ROUTE_ALIAS, from)
              : BundleEntry.updated(BundleEntry.ROUTE_ALIAS, from));
    }
  }

  private void theSettingsItWouldMerge(
      SimulatorProvider module, TestRunId run, RunBundle bundle, List<BundleEntry> planned) {
    Map<String, String> here = new LinkedHashMap<>();
    for (Map.Entry<SettingKey, String> setting : settings.of(module.id(), run).entrySet()) {
      here.put(setting.getKey().key(), setting.getValue());
    }
    for (Map.Entry<String, String> setting : bundle.carriedSettings().entrySet()) {
      if (!here.containsKey(setting.getKey())) {
        planned.add(BundleEntry.created(BundleEntry.SETTING, setting.getKey()));
        continue;
      }
      planned.add(
          Objects.equals(here.get(setting.getKey()), setting.getValue())
              ? BundleEntry.skipped(BundleEntry.SETTING, setting.getKey())
              : BundleEntry.updated(BundleEntry.SETTING, setting.getKey()));
    }
  }

  private void theRulesItWouldAdd(RunBundle bundle, List<BundleEntry> planned) {
    int carried = bundle.carriedRules().size();
    for (int i = 0; i < carried; i++) {
      planned.add(BundleEntry.created(BundleEntry.SCENARIO_RULE, "rules[" + i + "]"));
    }
  }

  private List<Integer> theFixturesItWouldSeed(
      SimulatorProvider module, TestRunId run, RunBundle bundle, List<BundleEntry> planned) {
    Set<String> held = new LinkedHashSet<>();
    for (PaymentMethodFixture fixture : fixtures.of(module.id(), run)) {
      held.add(fixture.fixtureAlias());
    }
    List<Integer> seeding = new ArrayList<>();
    List<PortableFixture> carried = bundle.carriedFixtures();
    for (int i = 0; i < carried.size(); i++) {
      String alias = String.valueOf(carried.get(i).alias());
      if (held.contains(alias)) {
        planned.add(BundleEntry.skipped(BundleEntry.FIXTURE, alias));
        continue;
      }
      planned.add(BundleEntry.created(BundleEntry.FIXTURE, alias));
      seeding.add(i);
    }
    return List.copyOf(seeding);
  }

  private Verdict theBundleItWrites(
      SimulatorProvider module,
      TestRunId run,
      RunBundle bundle,
      ProviderConfiguration configuration,
      ClockChange clockChange,
      ProviderConfigurations.Storable storable,
      List<NewScenarioRule> incoming,
      Planned planned,
      List<String> namesOfSecretsSupplied) {
    try {
      return unitOfWork.keepingOnly(
          Verdict.Taken.class::isInstance,
          () ->
              everythingTheBundleWrites(
                  module,
                  run,
                  bundle,
                  configuration,
                  clockChange,
                  storable,
                  incoming,
                  planned,
                  namesOfSecretsSupplied));
    } catch (ClaimedMeanwhileException claimed) {
      ConfigurationRefusal named = ConfigurationRefusal.of(String.valueOf(claimed.getMessage()));
      return new Verdict.Conflicting(
          List.of(
              new BundleRefusal(
                  theConfigurationPropertyOf(named, claimed.property()), named.message())));
    }
  }

  private Verdict everythingTheBundleWrites(
      SimulatorProvider module,
      TestRunId run,
      RunBundle bundle,
      ProviderConfiguration configuration,
      ClockChange clockChange,
      ProviderConfigurations.Storable storable,
      List<NewScenarioRule> incoming,
      Planned planned,
      List<String> namesOfSecretsSupplied) {
    clockChange.applyTo(pinned, run);
    testRuns.register(run);
    configurations.store(module, configuration, storable);

    List<String> theRulesJustWritten = new ArrayList<>();
    for (ScenarioRule rule : rules.addAll(incoming)) {
      theRulesJustWritten.add(rule.scenarioId());
    }

    List<PortableFixture> carried = bundle.carriedFixtures();
    for (int place : planned.theFixturesItWouldSeed()) {
      PaymentMethodFixtureAdmission.Verdict seeded =
          fixtureAdmission.seed(
              module,
              theFixtureItWouldSeed(run, carried.get(place), List.copyOf(theRulesJustWritten)),
              ObjectOrigin.CONTROL_API);
      String prefix = "fixtures[" + place + "]";
      if (seeded instanceof PaymentMethodFixtureAdmission.Verdict.Conflicting conflicting) {
        return new Verdict.Conflicting(List.of(theClaimOf(prefix, conflicting)));
      }
      BundleRefusal refusal = theFixtureRefusalOf(prefix, seeded);
      if (refusal != null) {
        return new Verdict.Refused(List.of(refusal));
      }
    }

    return new Verdict.Taken(
        BundleReport.of(
            run.value(), module.id().value(), true, planned.entries(), namesOfSecretsSupplied));
  }

  private static @Nullable BundleRefusal theFixtureRefusalOf(
      String prefix, PaymentMethodFixtureAdmission.Verdict seeded) {
    return switch (seeded) {
      case PaymentMethodFixtureAdmission.Verdict.Rejected rejected ->
          new BundleRefusal(prefix, rejected.message());
      case PaymentMethodFixtureAdmission.Verdict.Refused refused ->
          new BundleRefusal(prefix + "." + refused.property(), refused.message());
      case PaymentMethodFixtureAdmission.Verdict.Conflicting conflicting ->
          theClaimOf(prefix, conflicting);
      case PaymentMethodFixtureAdmission.Verdict.Seeded _ -> null;
    };
  }

  private static BundleRefusal theClaimOf(
      String prefix, PaymentMethodFixtureAdmission.Verdict.Conflicting conflicting) {
    String property = conflicting.property();
    return new BundleRefusal(
        property == null ? prefix : prefix + "." + property, conflicting.message());
  }

  private static String theConfigurationPropertyOf(
      ConfigurationRefusal refusal, @Nullable String property) {
    if (property != null) {
      return property;
    }
    String field = refusal.field();
    return field == null ? "configuration" : "configuration." + field;
  }

  private static List<BundleRefusal> prefixed(List<ScenarioRefusal> refusals) {
    List<BundleRefusal> named = new ArrayList<>();
    for (ScenarioRefusal refusal : refusals) {
      named.add(new BundleRefusal(RunBundles.SCENARIOS + refusal.property(), refusal.reason()));
    }
    return List.copyOf(named);
  }

  private static Set<String> lowercased(Set<String> keys) {
    Set<String> lower = new LinkedHashSet<>();
    for (String key : keys) {
      lower.add(key.toLowerCase(Locale.ROOT));
    }
    return Set.copyOf(lower);
  }
}
