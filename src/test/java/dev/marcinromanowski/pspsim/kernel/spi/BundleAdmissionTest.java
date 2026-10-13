package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleCompatibility;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleEntry;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleReport;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleVerdict;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableAccount;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableCard;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableFixture;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableRouteAlias;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableWebhookTarget;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredAction;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.PortableScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork.Ending;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class BundleAdmissionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId INTO = TestRunId.of("run-b");

  private static final Instant PINNED = Instant.parse("2026-09-24T12:00:00Z");

  private static final String A_SECRET_A_CALLER_SUPPLIED = "sk-live-must-not-be-echoed";

  private static final String A_RETRY_PLAN = "PT0S,PT1M";

  private final BundleStores.Accounts accounts = new BundleStores.Accounts();
  private final BoundRouteAliases aliases = new BoundRouteAliases();
  private final InMemoryTestRunSettings settings = new InMemoryTestRunSettings();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final BundleStores.Pins pins = new BundleStores.Pins();
  private final BundleStores.RegisteredRuns runs = new BundleStores.RegisteredRuns();
  private final RecordedUnitsOfWork unitsOfWork = new RecordedUnitsOfWork();

  private final ProviderDouble provider =
      new ProviderDouble()
          .asking(
              new ConfigurableField(
                  "secretKey",
                  "The secret key",
                  "a sentence a test does not read",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "capture.window",
                  "The capture window",
                  "a sentence a test does not read",
                  false,
                  false,
                  List.of(),
                  "a value a test does not read"))
          .reading("secretKey", "capture.window");

  @Test
  void an_import_naming_no_bundle_is_rejected_rather_than_reported_as_an_import_of_nothing() {
    assertThat(
            admission()
                .takeBack(provider, new DeclaredBundleImport(INTO.value(), false, null, null)))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .isEqualTo(BundleAdmission.AN_IMPORT_TAKES_BACK_A_BUNDLE));
  }

  @Test
  void a_test_run_id_no_namespace_can_hold_is_rejected_rather_than_imported_into() {
    assertThat(
            admission()
                .takeBack(
                    provider,
                    new DeclaredBundleImport("a run with spaces", false, aBundle(), null)))
        .isInstanceOf(BundleAdmission.Verdict.Rejected.class);
  }

  @Test
  void an_import_naming_no_run_of_its_own_takes_the_bundle_back_into_the_run_the_bundle_names() {
    assertThat(theReportOf(new DeclaredBundleImport(null, true, aBundle(), null)).testRunId())
        .isEqualTo("run-a");
  }

  @Test
  void a_declared_secret_in_the_bundle_is_refused_by_its_property_and_never_by_its_value() {
    List<BundleRefusal> refused =
        BundleAdmission.theSecretsThisBundleMayNotCarry(
            provider.accountFields(),
            aBundleWhoseAccountCarries(Map.of("secretKey", A_SECRET_A_CALLER_SUPPLIED)));

    assertThat(refused)
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("accounts[0].configuration.secretKey");
              assertThat(refusal.reason()).isEqualTo(BundleAdmission.A_BUNDLE_CARRIES_NO_SECRET);
              assertThat(refusal.reason()).doesNotContain(A_SECRET_A_CALLER_SUPPLIED);
            });
  }

  @Test
  void a_key_this_provider_declares_nothing_of_is_refused_under_a_reason_of_its_own() {
    assertThat(
            BundleAdmission.theSecretsThisBundleMayNotCarry(
                provider.accountFields(),
                aBundleWhoseAccountCarries(Map.of("smuggled", A_SECRET_A_CALLER_SUPPLIED))))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("accounts[0].configuration.smuggled");
              assertThat(refusal.reason())
                  .isEqualTo(BundleAdmission.A_KEY_THIS_PROVIDER_READS_NOTHING_OF);
            });
  }

  @Test
  void a_field_this_provider_declares_plainly_is_carried_by_a_bundle_and_refused_over_nothing() {
    assertThat(
            BundleAdmission.theSecretsThisBundleMayNotCarry(
                provider.accountFields(),
                aBundleWhoseAccountCarries(Map.of("capture.window", "P3D"))))
        .isEmpty();
  }

  @Test
  void an_account_entry_naming_no_label_is_refused_because_no_secret_could_be_addressed_to_it() {
    RunBundle bundle = aBundleOfAccounts(List.of(new PortableAccount(" ", Map.of(), List.of())));

    assertThat(BundleAdmission.theSecretsThisBundleMayNotCarry(provider.accountFields(), bundle))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("accounts[0].accountId");
              assertThat(refusal.reason())
                  .isEqualTo(BundleAdmission.AN_ACCOUNT_IS_ADDRESSED_BY_ITS_LABEL);
            });
  }

  @Test
  void a_secret_supplied_for_an_account_this_bundle_configures_is_refused_over_nothing() {
    assertThat(
            BundleAdmission.theSecretsThisImportMayNotSupply(
                provider.accountFields(),
                aBundle(),
                Map.of("merchant-1", Map.of("secretKey", A_SECRET_A_CALLER_SUPPLIED))))
        .isEmpty();
  }

  @Test
  void a_secret_supplied_for_an_account_no_bundle_entry_names_is_refused_by_that_label() {
    assertThat(
            BundleAdmission.theSecretsThisImportMayNotSupply(
                provider.accountFields(),
                aBundle(),
                Map.of("merchant-9", Map.of("secretKey", A_SECRET_A_CALLER_SUPPLIED))))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("secrets.merchant-9");
              assertThat(refusal.reason())
                  .isEqualTo(BundleAdmission.A_SECRET_IS_SUPPLIED_TO_AN_ACCOUNT_THE_BUNDLE_CARRIES);
              assertThat(refusal.reason()).doesNotContain(A_SECRET_A_CALLER_SUPPLIED);
            });
  }

  @Test
  void a_plainly_declared_field_supplied_as_a_secret_is_refused_by_the_field_it_named() {
    assertThat(
            BundleAdmission.theSecretsThisImportMayNotSupply(
                provider.accountFields(),
                aBundle(),
                Map.of("merchant-1", Map.of("capture.window", "P1D"))))
        .singleElement()
        .satisfies(
            refusal -> {
              assertThat(refusal.property()).isEqualTo("secrets.merchant-1.capture.window");
              assertThat(refusal.reason())
                  .isEqualTo(BundleAdmission.ONLY_A_DECLARED_SECRET_IS_SUPPLIED_APART);
            });
  }

  @Test
  void an_import_supplying_nothing_apart_is_refused_over_nothing() {
    assertThat(
            BundleAdmission.theSecretsThisImportMayNotSupply(
                provider.accountFields(), aBundle(), Map.of()))
        .isEmpty();
  }

  @Test
  void a_clock_this_simulator_cannot_read_is_rejected_rather_than_read_as_a_release() {
    RunBundle bundle = aBundle();
    RunBundle unreadable =
        new RunBundle(
            bundle.compatibility(),
            bundle.testRunId(),
            bundle.provider(),
            bundle.exportedAt(),
            new DeclaredClock("the middle of next week", null, null),
            bundle.accounts(),
            bundle.webhooks(),
            bundle.routeAliases(),
            bundle.settings(),
            bundle.fixtures(),
            bundle.scenarios());

    assertThat(
            admission()
                .takeBack(
                    provider, new DeclaredBundleImport(INTO.value(), true, unreadable, aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Rejected.class,
            rejected -> assertThat(rejected.message()).contains("clock.fixedNow"));
    assertThat(pins.of(INTO)).isEmpty();
  }

  @Test
  void a_bundle_that_says_nothing_about_a_clock_leaves_the_clock_the_target_run_stands_on() {
    pins.pin(INTO, PINNED.minusSeconds(60));
    RunBundle bundle = aBundle();
    RunBundle sayingNothing =
        new RunBundle(
            bundle.compatibility(),
            bundle.testRunId(),
            bundle.provider(),
            bundle.exportedAt(),
            null,
            bundle.accounts(),
            bundle.webhooks(),
            bundle.routeAliases(),
            bundle.settings(),
            bundle.fixtures(),
            bundle.scenarios());

    BundleReport report =
        theReportOf(new DeclaredBundleImport(INTO.value(), false, sayingNothing, aSecret()));

    assertThat(report.entries())
        .extracting(BundleEntry::kind)
        .contains(BundleEntry.ACCOUNT, BundleEntry.FIXTURE)
        .doesNotContain(BundleEntry.CLOCK);
    assertThat(pins.of(INTO)).contains(PINNED.minusSeconds(60));
  }

  @Test
  void a_bundle_of_a_run_that_reads_the_host_clock_releases_the_clock_of_the_run_it_lands_in() {
    pins.pin(INTO, PINNED.minusSeconds(60));
    RunBundle bundle = aBundle();
    RunBundle released =
        new RunBundle(
            bundle.compatibility(),
            bundle.testRunId(),
            bundle.provider(),
            bundle.exportedAt(),
            new DeclaredClock(null, null, null),
            bundle.accounts(),
            bundle.webhooks(),
            bundle.routeAliases(),
            bundle.settings(),
            bundle.fixtures(),
            bundle.scenarios());

    BundleReport report =
        theReportOf(new DeclaredBundleImport(INTO.value(), false, released, aSecret()));

    assertThat(report.entries())
        .filteredOn(entry -> entry.kind().equals("clock"))
        .extracting(BundleEntry::verdict)
        .containsExactly(BundleVerdict.UPDATED);
    assertThat(pins.of(INTO)).isEmpty();
  }

  @Test
  void the_secrets_an_import_was_given_are_named_by_account_and_field_and_by_nothing_else() {
    Map<String, Map<String, String>> supplied = new LinkedHashMap<>();
    supplied.put("merchant-1", Map.of("secretKey", A_SECRET_A_CALLER_SUPPLIED));
    supplied.put("merchant-2", Map.of("secretKey", A_SECRET_A_CALLER_SUPPLIED));

    List<String> named = BundleAdmission.theNamesOfTheSecretsSupplied(supplied);

    assertThat(named).containsExactly("merchant-1.secretKey", "merchant-2.secretKey");
    assertThat(named).noneMatch(name -> name.contains(A_SECRET_A_CALLER_SUPPLIED));
  }

  @Test
  void an_import_given_no_secret_names_none() {
    assertThat(BundleAdmission.theNamesOfTheSecretsSupplied(Map.of())).isEmpty();
  }

  @Test
  void the_configuration_an_import_applies_names_the_target_run_and_carries_the_supplied_secret() {
    DeclaredConfiguration configuration =
        BundleAdmission.theConfigurationItWouldApply(
            INTO, aBundle(), Map.of("merchant-1", Map.of("secretKey", A_SECRET_A_CALLER_SUPPLIED)));

    assertThat(configuration.testRunId()).isEqualTo("run-b");
    assertThat(configuration.clock()).isEqualTo(new DeclaredClock(PINNED.toString(), null, null));
    assertThat(configuration.accounts())
        .containsExactly(
            new DeclaredAccount(
                "merchant-1",
                Map.of("capture.window", "P3D", "secretKey", A_SECRET_A_CALLER_SUPPLIED)));
    assertThat(configuration.webhooks())
        .containsExactly(new DeclaredWebhookTarget("merchant-1", "https://merchant.example/hook"));
    assertThat(configuration.routeAliases())
        .containsExactly(new DeclaredRouteAlias("https://api.example/v1", "/v1"));
    assertThat(configuration.settings())
        .containsExactly(Map.entry("webhook.retryPlan", A_RETRY_PLAN));
  }

  @Test
  void a_fixture_an_import_seeds_names_the_rule_this_import_has_just_written() {
    PortableFixture carried =
        new PortableFixture(
            "merchant-1",
            "owner-1",
            "card",
            "visa-success",
            new PortableCard("visa", "4242", 12, 2031, "credit"),
            Map.of("token", "tok_1"),
            1);

    DeclaredPaymentMethodFixture declared =
        BundleAdmission.theFixtureItWouldSeed(INTO, carried, List.of("scn_first", "scn_second"));

    assertThat(declared.testRunId()).isEqualTo("run-b");
    assertThat(declared.merchantId()).isEqualTo("merchant-1");
    assertThat(declared.ownerId()).isEqualTo("owner-1");
    assertThat(declared.type()).isEqualTo("card");
    assertThat(declared.alias()).isEqualTo("visa-success");
    assertThat(declared.card()).isEqualTo(new DeclaredCard("visa", "4242", 12, 2031, "credit"));
    assertThat(declared.providerIds()).containsExactly(Map.entry("token", "tok_1"));
    assertThat(declared.defaultScenario()).isEqualTo("scn_second");
  }

  @Test
  void a_fixture_naming_no_rule_and_a_fixture_naming_one_no_import_wrote_both_seed_without_one() {
    PortableFixture namingNone = new PortableFixture("m", "o", "card", "a", null, Map.of(), null);
    PortableFixture namingOneNotWritten =
        new PortableFixture("m", "o", "card", "b", null, Map.of(), 3);

    assertThat(
            BundleAdmission.theFixtureItWouldSeed(INTO, namingNone, List.of("scn_1"))
                .defaultScenario())
        .isNull();
    assertThat(
            BundleAdmission.theFixtureItWouldSeed(INTO, namingOneNotWritten, List.of("scn_1"))
                .defaultScenario())
        .isNull();
    assertThat(BundleAdmission.theFixtureItWouldSeed(INTO, namingNone, List.of("scn_1")).card())
        .isNull();
  }

  @Test
  void a_dry_run_reports_what_it_would_create_and_writes_none_of_it() {
    BundleReport report =
        theReportOf(new DeclaredBundleImport(INTO.value(), true, aBundle(), aSecret()));

    assertThat(report.applied()).isFalse();
    assertThat(report.created()).isEqualTo(7);
    assertThat(accounts.identitiesOf(provider.id(), INTO)).isEmpty();
    assertThat(rules.of(provider.id(), INTO)).isEmpty();
    assertThat(fixtures.of(provider.id(), INTO)).isEmpty();
    assertThat(settings.of(provider.id(), INTO)).isEmpty();
    assertThat(pins.of(INTO)).isEmpty();
    assertThat(runs.registered()).isEmpty();
    assertThat(provider.itStored()).isEmpty();
  }

  @Test
  void a_dry_run_and_the_import_that_follows_it_report_the_same_entries_in_the_same_order() {
    BundleAdmission admission = admission();
    BundleReport dry =
        theReportOf(admission, new DeclaredBundleImport(INTO.value(), true, aBundle(), aSecret()));

    BundleReport applied =
        theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret()));

    assertThat(applied.applied()).isTrue();
    assertThat(applied.entries()).isEqualTo(dry.entries());
    assertThat(applied.created()).isEqualTo(dry.created());
    assertThat(applied.updated()).isEqualTo(dry.updated());
    assertThat(applied.skipped()).isEqualTo(dry.skipped());
  }

  @Test
  void
      an_import_creates_the_clock_the_account_the_target_the_alias_the_setting_the_rule_and_the_fixture() {
    BundleReport report =
        theReportOf(new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret()));

    assertThat(report.entries())
        .extracting(BundleEntry::kind, BundleEntry::name, BundleEntry::verdict)
        .containsExactly(
            Tuple.tuple("clock", "fixedNow", BundleVerdict.CREATED),
            Tuple.tuple("account", "merchant-1", BundleVerdict.CREATED),
            Tuple.tuple(
                "webhookTarget", "merchant-1 https://merchant.example/hook", BundleVerdict.CREATED),
            Tuple.tuple("routeAlias", "https://api.example/v1", BundleVerdict.CREATED),
            Tuple.tuple("setting", "webhook.retryPlan", BundleVerdict.CREATED),
            Tuple.tuple("scenarioRule", "rules[0]", BundleVerdict.CREATED),
            Tuple.tuple("fixture", "visa-success", BundleVerdict.CREATED));
    assertThat(pins.of(INTO)).contains(PINNED);
    assertThat(runs.registered()).containsExactly(INTO);
    assertThat(accounts.identitiesOf(provider.id(), INTO)).containsExactly("merchant-1");
    assertThat(rules.of(provider.id(), INTO)).hasSize(1);
    assertThat(fixtures.of(provider.id(), INTO)).hasSize(1);
  }

  @Test
  void
      the_second_import_of_one_bundle_skips_what_the_run_already_holds_and_creates_the_rules_again() {
    BundleAdmission admission = admission();
    theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret()));
    provider.disclosing("merchant-1", Map.of("capture.window", List.of("P3D")));

    BundleReport again =
        theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, aBundle(), null));

    assertThat(again.entries())
        .extracting(BundleEntry::kind, BundleEntry::verdict)
        .contains(
            Tuple.tuple("clock", BundleVerdict.SKIPPED),
            Tuple.tuple("account", BundleVerdict.SKIPPED),
            Tuple.tuple("webhookTarget", BundleVerdict.SKIPPED),
            Tuple.tuple("routeAlias", BundleVerdict.SKIPPED),
            Tuple.tuple("setting", BundleVerdict.SKIPPED),
            Tuple.tuple("fixture", BundleVerdict.SKIPPED),
            Tuple.tuple("scenarioRule", BundleVerdict.CREATED));
    assertThat(rules.of(provider.id(), INTO)).hasSize(2);
    assertThat(fixtures.of(provider.id(), INTO)).hasSize(1);
  }

  @Test
  void an_account_a_secret_is_supplied_for_is_updated_rather_than_skipped() {
    BundleAdmission admission = admission();
    theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret()));
    provider.disclosing("merchant-1", Map.of("capture.window", List.of("P3D")));

    BundleReport again =
        theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret()));

    assertThat(again.entries())
        .filteredOn(entry -> entry.kind().equals("account"))
        .extracting(BundleEntry::verdict)
        .containsExactly(BundleVerdict.UPDATED);
    assertThat(again.secretsSupplied()).containsExactly("merchant-1.secretKey");
  }

  @Test
  void an_entry_whose_value_the_target_run_holds_differently_is_updated_rather_than_created() {
    settings.merge(provider.id(), INTO, Map.of(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S"));
    aliases.merge(
        provider.id(),
        INTO,
        List.of(
            new RouteAlias(
                provider.id(),
                INTO,
                new RouteAliasKey("api.example", "/v1"),
                "https://api.example/v1",
                "/v2")));
    pins.pin(INTO, PINNED.minusSeconds(60));

    BundleReport report =
        theReportOf(new DeclaredBundleImport(INTO.value(), true, aBundle(), aSecret()));

    assertThat(report.entries())
        .extracting(BundleEntry::kind, BundleEntry::verdict)
        .contains(
            Tuple.tuple("clock", BundleVerdict.UPDATED),
            Tuple.tuple("routeAlias", BundleVerdict.UPDATED),
            Tuple.tuple("setting", BundleVerdict.UPDATED));
  }

  @Test
  void
      a_setting_this_provider_declares_nothing_of_is_refused_by_a_dry_run_and_not_only_by_an_import() {
    RunBundle bundle = aBundle();
    RunBundle unreadable =
        new RunBundle(
            bundle.compatibility(),
            bundle.testRunId(),
            bundle.provider(),
            bundle.exportedAt(),
            bundle.clock(),
            bundle.accounts(),
            bundle.webhooks(),
            bundle.routeAliases(),
            Map.of("webhook.retryPlan", "not a duration at all"),
            bundle.fixtures(),
            bundle.scenarios());

    BundleAdmission admission = admission();

    assertThat(
            admission.takeBack(
                provider, new DeclaredBundleImport(INTO.value(), true, unreadable, aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("configuration"));
    assertThat(
            admission.takeBack(
                provider, new DeclaredBundleImport(INTO.value(), false, unreadable, aSecret())))
        .isInstanceOf(BundleAdmission.Verdict.Refused.class);
    assertThat(pins.of(INTO)).isEmpty();
    assertThat(accounts.identitiesOf(provider.id(), INTO)).isEmpty();
  }

  @Test
  void an_account_field_this_provider_reads_nothing_of_is_refused_by_a_dry_run_naming_that_field() {
    ProviderDouble readingOnlyTheSecret =
        new ProviderDouble()
            .asking(
                new ConfigurableField(
                    "secretKey",
                    "The secret key",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read"),
                new ConfigurableField(
                    "capture.window",
                    "The capture window",
                    "a sentence a test does not read",
                    false,
                    false,
                    List.of(),
                    "a value a test does not read"))
            .reading("secretKey");

    assertThat(
            admission()
                .takeBack(
                    readingOnlyTheSecret,
                    new DeclaredBundleImport(INTO.value(), true, aBundle(), aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("configuration.capture.window"));
    assertThat(accounts.identitiesOf(readingOnlyTheSecret.id(), INTO)).isEmpty();
  }

  @Test
  void a_route_alias_another_test_run_holds_is_refused_by_a_dry_run_and_bound_to_neither_run() {
    TestRunId anotherRun = TestRunId.of("run-c");
    aliases.merge(
        provider.id(),
        anotherRun,
        List.of(
            new RouteAlias(
                provider.id(),
                anotherRun,
                new RouteAliasKey("api.example", "/v1"),
                "https://api.example/v1",
                "/v1")));

    assertThat(
            admission()
                .takeBack(
                    provider, new DeclaredBundleImport(INTO.value(), true, aBundle(), aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("routeAliases[0].from"));
    assertThat(aliases.of(provider.id(), INTO)).isEmpty();
    assertThat(aliases.of(provider.id(), anotherRun)).hasSize(1);
  }

  @Test
  void a_route_alias_the_target_run_already_holds_is_refused_over_nothing() {
    aliases.merge(
        provider.id(),
        INTO,
        List.of(
            new RouteAlias(
                provider.id(),
                INTO,
                new RouteAliasKey("api.example", "/v1"),
                "https://api.example/v1",
                "/v1")));

    assertThat(theReportOf(new DeclaredBundleImport(INTO.value(), true, aBundle(), aSecret())))
        .isNotNull();
  }

  @Test
  void an_account_whose_plain_fields_differ_from_the_bundle_is_updated_though_no_secret_is_given() {
    BundleAdmission admission = admission();
    theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret()));
    provider.disclosing("merchant-1", Map.of("capture.window", List.of("P7D")));

    BundleReport again =
        theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, aBundle(), null));

    assertThat(again.entries())
        .filteredOn(entry -> entry.kind().equals("account"))
        .extracting(BundleEntry::verdict)
        .containsExactly(BundleVerdict.UPDATED);
  }

  @Test
  void an_account_this_provider_discloses_nothing_of_is_updated_rather_than_skipped() {
    BundleAdmission admission = admission();
    theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret()));

    BundleReport again =
        theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, aBundle(), null));

    assertThat(again.entries())
        .filteredOn(entry -> entry.kind().equals("account"))
        .extracting(BundleEntry::verdict)
        .containsExactly(BundleVerdict.UPDATED);
  }

  @Test
  void a_fixture_the_run_holds_is_skipped_while_one_beside_it_is_seeded() {
    BundleAdmission admission = admission();
    RunBundle one = aBundle();
    theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, one, aSecret()));
    provider.disclosing("merchant-1", Map.of("capture.window", List.of("P3D")));
    RunBundle two = aBundleOfTwoFixtures();

    BundleReport again =
        theReportOf(admission, new DeclaredBundleImport(INTO.value(), false, two, aSecret()));

    assertThat(again.entries())
        .filteredOn(entry -> entry.kind().equals("fixture"))
        .extracting(BundleEntry::name, BundleEntry::verdict)
        .containsExactly(
            Tuple.tuple("visa-success", BundleVerdict.SKIPPED),
            Tuple.tuple("visa-decline", BundleVerdict.CREATED));
    assertThat(fixtures.of(provider.id(), INTO))
        .extracting(PaymentMethodFixture::fixtureAlias)
        .containsExactlyInAnyOrder("visa-success", "visa-decline");
  }

  @Test
  void a_fixture_the_grammar_cannot_read_refuses_the_import_before_a_dry_run_reports_anything() {
    RunBundle bundle = aBundle();
    RunBundle unreadable =
        rewritten(
            bundle,
            List.of(
                new PortableFixture(
                    "merchant-1",
                    "owner-1",
                    "a kind this simulator never had",
                    "visa-success",
                    null,
                    Map.of("token", "tok_1"),
                    null)));

    assertThat(
            admission()
                .takeBack(provider, new DeclaredBundleImport(INTO.value(), true, unreadable, null)))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("fixtures[0]"));
  }

  @Test
  void a_rejected_and_a_refused_import_each_report_the_refusal_the_trail_files_them_under() {
    assertThat(new BundleAdmission.Verdict.Rejected("no").theRefusalItReports())
        .isEqualTo(
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(
                    BundleAdmission.THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK)));
    assertThat(new BundleAdmission.Verdict.Refused(List.of()).theRefusalItReports())
        .isEqualTo(
            ActionVerdict.refused(
                RefusalSubject.noObjectWasNamed(
                    BundleAdmission.THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK)));
    assertThat(
            new BundleAdmission.Verdict.Taken(
                    BundleReport.of("run-a", "twokeys", true, List.of(), List.of()))
                .theRefusalItReports())
        .isNull();
    assertThat(new BundleAdmission.Verdict.Rejected("no").theRefusalItReports().httpStatus())
        .isEqualTo(400);
    assertThat(new BundleAdmission.Verdict.Refused(List.of()).theRefusalItReports().httpStatus())
        .isEqualTo(422);
    assertThat(new BundleAdmission.Verdict.Conflicting(List.of()).theRefusalItReports())
        .isEqualTo(
            ActionVerdict.conflicting(
                RefusalSubject.noObjectWasNamed(
                    BundleAdmission.THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK)));
  }

  @Test
  void the_refusals_a_refused_import_was_built_from_do_not_change_it_afterwards() {
    List<BundleRefusal> refusals = new ArrayList<>(List.of(new BundleRefusal("provider", "no")));
    BundleAdmission.Verdict.Refused refused = new BundleAdmission.Verdict.Refused(refusals);
    refusals.add(new BundleRefusal("compatibility", "no"));

    assertThat(refused.refusals()).hasSize(1);
  }

  @Test
  void a_fixture_this_provider_itself_refuses_to_seed_is_reported_by_its_place_and_its_property() {
    ProviderDouble refusingTheSeed =
        new ProviderDouble()
            .asking(
                new ConfigurableField(
                    "secretKey",
                    "The secret key",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read"),
                new ConfigurableField(
                    "capture.window",
                    "The capture window",
                    "a sentence a test does not read",
                    false,
                    false,
                    List.of(),
                    "a value a test does not read"))
            .reading("secretKey", "capture.window")
            .refusingTheSeed("card.last4", "this double seeds no card");

    assertThat(
            admission()
                .takeBack(
                    refusingTheSeed,
                    new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("fixtures[0].card.last4"));
    assertThat(fixtures.of(refusingTheSeed.id(), INTO)).isEmpty();
    assertThat(unitsOfWork.endings()).containsExactly(Ending.DISCARDED);
  }

  @Test
  void an_import_the_whole_of_which_was_written_is_kept_by_one_unit_of_work() {
    theReportOf(new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret()));

    assertThat(unitsOfWork.endings()).containsExactly(Ending.KEPT, Ending.KEPT);
  }

  @Test
  void an_account_the_database_finds_claimed_meanwhile_is_a_conflict_under_the_configuration() {
    ProviderDouble claimedMeanwhile =
        new ProviderDouble()
            .asking(
                new ConfigurableField(
                    "secretKey",
                    "The secret key",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read"),
                new ConfigurableField(
                    "capture.window",
                    "The capture window",
                    "a sentence a test does not read",
                    false,
                    false,
                    List.of(),
                    "a value a test does not read"))
            .reading("secretKey", "capture.window")
            .findingTheAccountClaimedMeanwhile("taken while this import was written");

    assertThat(
            admission()
                .takeBack(
                    claimedMeanwhile,
                    new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .containsExactly(
                        new BundleRefusal("configuration", "taken while this import was written")));
    assertThat(unitsOfWork.endings()).containsExactly(Ending.THREW);
  }

  @Test
  void two_fixtures_of_one_bundle_claiming_one_provider_native_id_conflict_at_the_second_id() {
    RunBundle bundle = aBundle();
    List<PortableFixture> both = new ArrayList<>(bundle.carriedFixtures());
    both.add(
        new PortableFixture(
            "merchant-1",
            "owner-1",
            "card",
            "visa-decline",
            new PortableCard("visa", "0002", 12, 2031, "credit"),
            Map.of("token", "tok_1"),
            0));

    assertThat(
            admission()
                .takeBack(
                    provider,
                    new DeclaredBundleImport(
                        INTO.value(), false, rewritten(bundle, List.copyOf(both)), aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("fixtures[1].providerIds.token"));
    assertThat(unitsOfWork.endings())
        .containsExactly(Ending.DISCARDED, Ending.DISCARDED, Ending.DISCARDED);
  }

  @Test
  void a_fixture_the_database_finds_claimed_meanwhile_is_a_conflict_under_that_fixture() {
    ProviderDouble claimedMeanwhile =
        new ProviderDouble()
            .asking(
                new ConfigurableField(
                    "secretKey",
                    "The secret key",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read"),
                new ConfigurableField(
                    "capture.window",
                    "The capture window",
                    "a sentence a test does not read",
                    false,
                    false,
                    List.of(),
                    "a value a test does not read"))
            .reading("secretKey", "capture.window")
            .findingTheSeedClaimedMeanwhile("taken while this fixture was written");

    assertThat(
            admission()
                .takeBack(
                    claimedMeanwhile,
                    new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .containsExactly(
                        new BundleRefusal("fixtures[0]", "taken while this fixture was written")));
    assertThat(unitsOfWork.endings()).containsExactly(Ending.DISCARDED, Ending.DISCARDED);
  }

  @Test
  void a_route_alias_another_run_binds_meanwhile_is_a_conflict_under_the_alias_it_names() {
    TestRunId anotherRun = TestRunId.of("run-c");
    RouteAliases boundMeanwhile =
        new BindingFirst(
            aliases,
            () ->
                aliases.merge(
                    provider.id(),
                    anotherRun,
                    List.of(
                        new RouteAlias(
                            provider.id(),
                            anotherRun,
                            new RouteAliasKey("api.example", "/v1"),
                            "https://api.example/v1",
                            "/v1"))));

    assertThat(
            admissionBinding(boundMeanwhile)
                .takeBack(
                    provider, new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Conflicting.class,
            conflicting -> {
              assertThat(conflicting.refusals())
                  .extracting(BundleRefusal::property)
                  .containsExactly("routeAliases[0].from");
              assertThat(conflicting.theRefusalItReports().httpStatus()).isEqualTo(409);
            });
    assertThat(unitsOfWork.endings()).containsExactly(Ending.THREW);
  }

  @Test
  void a_route_alias_claimed_meanwhile_is_reported_under_the_property_the_claim_carries() {
    RouteAliases claimedMeanwhile =
        new BindingFirst(
            aliases,
            () -> {
              throw new ClaimedMeanwhileException(
                  "routeAliases[0].from", "worded without naming the alias it is about");
            });

    assertThat(
            admissionBinding(claimedMeanwhile)
                .takeBack(
                    provider, new DeclaredBundleImport(INTO.value(), false, aBundle(), aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .containsExactly(
                        new BundleRefusal(
                            "routeAliases[0].from",
                            "worded without naming the alias it is about")));
  }

  @Test
  void a_dry_run_refusal_is_reported_under_the_property_it_carries_however_it_is_worded() {
    ProviderDouble refusingTheAlias =
        new ProviderDouble()
            .asking(
                new ConfigurableField(
                    "secretKey",
                    "The secret key",
                    "a sentence a test does not read",
                    true,
                    true,
                    List.of(),
                    "a value a test does not read"),
                new ConfigurableField(
                    "capture.window",
                    "The capture window",
                    "a sentence a test does not read",
                    false,
                    false,
                    List.of(),
                    "a value a test does not read"))
            .reading("secretKey", "capture.window")
            .refusingTheConfiguration(
                "routeAliases[0].to", "worded without naming the alias it is about");

    assertThat(
            admission()
                .takeBack(
                    refusingTheAlias,
                    new DeclaredBundleImport(INTO.value(), true, aBundle(), aSecret())))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .containsExactly(
                        new BundleRefusal(
                            "routeAliases[0].to", "worded without naming the alias it is about")));
  }

  @Test
  void
      a_bundle_of_configuration_alone_registers_the_run_it_lands_in_before_anything_addresses_it() {
    RunBundle bundle = aBundle();

    theReportOf(
        new DeclaredBundleImport(INTO.value(), false, rewritten(bundle, List.of()), aSecret()));

    assertThat(runs.registered()).containsExactly(INTO);
    assertThat(fixtures.of(provider.id(), INTO)).isEmpty();
  }

  @Test
  void a_bundle_carrying_a_secret_is_refused_and_nothing_of_it_is_written() {
    BundleAdmission.Verdict verdict =
        admission()
            .takeBack(
                provider,
                new DeclaredBundleImport(
                    INTO.value(),
                    false,
                    aBundleWhoseAccountCarries(
                        Map.of("secretKey", A_SECRET_A_CALLER_SUPPLIED, "capture.window", "P3D")),
                    null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("accounts[0].configuration.secretKey"));
    assertThat(accounts.identitiesOf(provider.id(), INTO)).isEmpty();
    assertThat(rules.of(provider.id(), INTO)).isEmpty();
  }

  @Test
  void a_bundle_written_against_a_term_this_build_no_longer_declares_is_refused_by_that_term() {
    RunBundle bundle = aBundle();
    RunBundle written =
        new RunBundle(
            new BundleCompatibility(List.of("apiSecret"), null, null, null),
            bundle.testRunId(),
            bundle.provider(),
            bundle.exportedAt(),
            bundle.clock(),
            bundle.accounts(),
            bundle.webhooks(),
            bundle.routeAliases(),
            bundle.settings(),
            bundle.fixtures(),
            bundle.scenarios());

    assertThat(
            admission()
                .takeBack(provider, new DeclaredBundleImport(INTO.value(), true, written, null)))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("compatibility.accountFields.apiSecret"));
  }

  @Test
  void a_rule_keyed_on_a_flow_this_grammar_cannot_read_is_rejected_and_writes_nothing() {
    RunBundle bundle = aBundle();
    RunBundle unreadable =
        new RunBundle(
            bundle.compatibility(),
            bundle.testRunId(),
            bundle.provider(),
            bundle.exportedAt(),
            bundle.clock(),
            bundle.accounts(),
            bundle.webhooks(),
            bundle.routeAliases(),
            bundle.settings(),
            List.of(),
            new ScenarioDocument(
                "run-a",
                ProviderDouble.ID.value(),
                null,
                List.of(
                    new PortableScenario(
                        0,
                        "a-story",
                        "A_KEYING_THIS_SIMULATOR_NEVER_HAD",
                        null,
                        null,
                        null,
                        new DeclaredAction(
                            "FIXED_SUCCESS", null, null, null, null, null, null, null)))));

    assertThat(
            admission()
                .takeBack(
                    provider, new DeclaredBundleImport(INTO.value(), true, unreadable, aSecret())))
        .isInstanceOf(BundleAdmission.Verdict.Rejected.class);
    assertThat(rules.of(provider.id(), INTO)).isEmpty();
  }

  @Test
  void a_rule_this_simulator_cannot_read_refuses_the_import_under_the_scenarios_it_came_from() {
    RunBundle bundle = aBundle();
    RunBundle unreadable =
        new RunBundle(
            bundle.compatibility(),
            bundle.testRunId(),
            bundle.provider(),
            bundle.exportedAt(),
            bundle.clock(),
            bundle.accounts(),
            bundle.webhooks(),
            bundle.routeAliases(),
            bundle.settings(),
            List.of(),
            new ScenarioDocument(
                "run-a",
                ProviderDouble.ID.value(),
                null,
                List.of(
                    new PortableScenario(
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new DeclaredAction(
                            "A_TYPE_THIS_BUILD_NEVER_HAD",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null)))));

    assertThat(
            admission()
                .takeBack(provider, new DeclaredBundleImport(INTO.value(), true, unreadable, null)))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("scenarios.action.type"));
  }

  @Test
  void a_fixture_naming_an_id_this_provider_reads_nothing_of_refuses_the_import_by_its_own_place() {
    RunBundle bundle = aBundle();
    RunBundle unseedable =
        new RunBundle(
            bundle.compatibility(),
            bundle.testRunId(),
            bundle.provider(),
            bundle.exportedAt(),
            bundle.clock(),
            bundle.accounts(),
            bundle.webhooks(),
            bundle.routeAliases(),
            bundle.settings(),
            List.of(
                new PortableFixture(
                    "merchant-1",
                    "owner-1",
                    "card",
                    "visa-success",
                    new PortableCard("visa", "4242", 12, 2031, "credit"),
                    Map.of("aKeyThisProviderReadsNothingOf", "x"),
                    null)),
            bundle.scenarios());

    assertThat(
            admission()
                .takeBack(provider, new DeclaredBundleImport(INTO.value(), true, unseedable, null)))
        .isInstanceOfSatisfying(
            BundleAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("fixtures[0].providerIds.aKeyThisProviderReadsNothingOf"));
    assertThat(fixtures.of(provider.id(), INTO)).isEmpty();
  }

  private BundleReport theReportOf(DeclaredBundleImport declared) {
    return theReportOf(admission(), declared);
  }

  private BundleReport theReportOf(BundleAdmission admission, DeclaredBundleImport declared) {
    BundleAdmission.Verdict verdict = admission.takeBack(provider, declared);
    assertThat(verdict).isInstanceOf(BundleAdmission.Verdict.Taken.class);
    return ((BundleAdmission.Verdict.Taken) verdict).report();
  }

  private DeclaredProviderSettings theSettingsThisBuildDeclares() {
    return new DeclaredProviderSettings(
        List.of(
            new ProviderSettings(
                provider.id(),
                List.of(new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S")))));
  }

  private BundleAdmission admission() {
    return admissionBinding(aliases);
  }

  private BundleAdmission admissionBinding(RouteAliases routeAliases) {
    ScenarioAdmission scenarios =
        new ScenarioAdmission(
            new ScenarioGrammar(new FaultDelayCeiling(Duration.ofSeconds(30))),
            rules,
            runs,
            DEFAULT_RUN);
    return new BundleAdmission(
        new ProviderConfigurations(
            accounts, routeAliases, theSettingsThisBuildDeclares(), settings),
        scenarios,
        rules,
        new PaymentMethodFixtureAdmission(fixtures, rules, runs, unitsOfWork, DEFAULT_RUN),
        accounts,
        aliases,
        settings,
        theSettingsThisBuildDeclares(),
        fixtures,
        pins,
        runs,
        unitsOfWork,
        DEFAULT_RUN);
  }

  private record BindingFirst(RouteAliases delegate, Runnable claim) implements RouteAliases {

    @Override
    public void merge(ProviderId provider, TestRunId testRunId, List<RouteAlias> bound) {
      claim.run();
      delegate.merge(provider, testRunId, bound);
    }

    @Override
    public boolean release(ProviderId provider, TestRunId testRunId, RouteAliasKey key) {
      return delegate.release(provider, testRunId, key);
    }

    @Override
    public List<RouteAlias> of(ProviderId provider, TestRunId testRunId) {
      return delegate.of(provider, testRunId);
    }

    @Override
    public Optional<RouteAlias> boundTo(RouteAliasKey key) {
      return delegate.boundTo(key);
    }
  }

  private Map<String, Map<String, String>> aSecret() {
    return Map.of("merchant-1", Map.of("secretKey", A_SECRET_A_CALLER_SUPPLIED));
  }

  private RunBundle aBundle() {
    return aBundleWhoseAccountCarries(Map.of("capture.window", "P3D"));
  }

  private RunBundle aBundleWhoseAccountCarries(Map<String, String> configuration) {
    return aBundleOfAccounts(
        List.of(new PortableAccount("merchant-1", configuration, List.of("secretKey"))));
  }

  private RunBundle aBundleOfTwoFixtures() {
    RunBundle bundle = aBundle();
    List<PortableFixture> both = new ArrayList<>(bundle.carriedFixtures());
    both.add(
        new PortableFixture(
            "merchant-1",
            "owner-1",
            "card",
            "visa-decline",
            new PortableCard("visa", "0002", 12, 2031, "credit"),
            Map.of("token", "tok_2"),
            0));
    return rewritten(bundle, List.copyOf(both));
  }

  private static RunBundle rewritten(RunBundle bundle, List<PortableFixture> carried) {
    return new RunBundle(
        bundle.compatibility(),
        bundle.testRunId(),
        bundle.provider(),
        bundle.exportedAt(),
        bundle.clock(),
        bundle.accounts(),
        bundle.webhooks(),
        bundle.routeAliases(),
        bundle.settings(),
        carried,
        bundle.scenarios());
  }

  private RunBundle aBundleOfAccounts(List<PortableAccount> carried) {
    return new RunBundle(
        BundleVocabulary.of(provider, theSettingsThisBuildDeclares().of(provider.id())),
        "run-a",
        ProviderDouble.ID.value(),
        PINNED.toString(),
        new DeclaredClock(PINNED.toString(), null, null),
        carried,
        List.of(new PortableWebhookTarget("merchant-1", "https://merchant.example/hook")),
        List.of(new PortableRouteAlias("https://api.example/v1", "/v1")),
        Map.of("webhook.retryPlan", A_RETRY_PLAN),
        List.of(
            new PortableFixture(
                "merchant-1",
                "owner-1",
                "card",
                "visa-success",
                new PortableCard("visa", "4242", 12, 2031, "credit"),
                Map.of("token", "tok_1"),
                0)),
        new ScenarioDocument(
            "run-a",
            ProviderDouble.ID.value(),
            null,
            List.of(
                new PortableScenario(
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new DeclaredAction(
                        "DELIVERY_DELAYED", null, null, null, null, null, 900000L, null)))));
  }
}
