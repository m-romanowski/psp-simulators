package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleCompatibility;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleEntry;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleReport;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableAccount;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableCard;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableFixture;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.repro.ProviderNativeIds;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedCallback;
import dev.marcinromanowski.pspsim.kernel.repro.RecordedExchange;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionEvidence;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionManifest;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionReport;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReproductionAdmissionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId INTO = TestRunId.of("run-b");

  private static final String CARRIED_TOKEN = "tok_carried";

  private static final String A_FREE_TOKEN = "tok_free";

  private static final String AT = "2026-09-24T12:00:00Z";

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
                  "capture.window",
                  "The capture window",
                  "a sentence a test does not read",
                  false,
                  false,
                  List.of(),
                  "a value a test does not read"))
          .reading("capture.window");

  @Test
  void an_import_naming_no_bundle_is_rejected_rather_than_reported_as_an_import_of_nothing() {
    assertThat(
            admission()
                .takeBack(
                    provider,
                    new DeclaredReproductionImport(INTO.value(), false, null, null, null)))
        .isInstanceOfSatisfying(
            ReproductionAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .isEqualTo(ReproductionAdmission.AN_IMPORT_TAKES_BACK_A_BUNDLE));
  }

  @Test
  void a_bundle_carrying_evidence_and_no_setup_is_rejected_rather_than_read_as_an_empty_import() {
    assertThat(
            admission()
                .takeBack(
                    provider,
                    new DeclaredReproductionImport(
                        INTO.value(),
                        false,
                        new ReproductionBundle("run-a", "twokeys", null, null, null, null),
                        null,
                        null)))
        .isInstanceOfSatisfying(
            ReproductionAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .isEqualTo(ReproductionAdmission.A_BUNDLE_CARRIES_THE_SETUP_IT_REPRODUCES));
  }

  @Test
  void a_test_run_id_no_namespace_can_hold_is_rejected_rather_than_imported_into() {
    assertThat(
            admission()
                .takeBack(
                    provider,
                    new DeclaredReproductionImport(
                        "a run with spaces", false, aBundle(CARRIED_TOKEN), null, null)))
        .isInstanceOf(ReproductionAdmission.Verdict.Rejected.class);
  }

  @Test
  void an_import_naming_no_run_of_its_own_takes_the_setup_back_into_the_run_the_setup_names() {
    assertThat(
            theReportOf(
                    new DeclaredReproductionImport(null, true, aBundle(CARRIED_TOKEN), null, null))
                .setup()
                .testRunId())
        .isEqualTo("run-a");
  }

  @Test
  void an_import_asking_for_no_remapping_seeds_the_instrument_under_the_id_the_bundle_carried() {
    ReproductionReport report =
        theReportOf(
            new DeclaredReproductionImport(
                INTO.value(), false, aBundle(CARRIED_TOKEN), null, null));

    assertThat(report.providerNativeIdsPreserved()).isTrue();
    assertThat(report.theProviderNativeIdKeysItRemapped()).isEmpty();
    assertThat(fixtures.of(provider.id(), INTO))
        .singleElement()
        .satisfies(
            seeded ->
                assertThat(seeded.providerIds())
                    .containsExactlyInAnyOrderEntriesOf(Map.of("token", CARRIED_TOKEN)));
  }

  @Test
  void an_import_asking_for_a_remapping_seeds_the_instrument_under_the_id_it_asked_for() {
    ReproductionReport report =
        theReportOf(
            new DeclaredReproductionImport(
                INTO.value(),
                false,
                aBundle(CARRIED_TOKEN),
                null,
                Map.of(CARRIED_TOKEN, A_FREE_TOKEN)));

    assertThat(report.providerNativeIdsPreserved()).isFalse();
    assertThat(report.theProviderNativeIdKeysItRemapped()).containsExactly("token");
    assertThat(fixtures.of(provider.id(), INTO))
        .singleElement()
        .satisfies(
            seeded -> {
              assertThat(seeded.providerIds())
                  .containsExactlyInAnyOrderEntriesOf(Map.of("token", A_FREE_TOKEN));
              assertThat(seeded.providerIds()).doesNotContainValue(CARRIED_TOKEN);
            });
  }

  @Test
  void an_id_the_target_run_already_gave_another_instrument_is_a_conflict_and_nothing_is_written() {
    ReproductionAdmission admission = admission();
    theReportOf(
        admission,
        new DeclaredReproductionImport(INTO.value(), false, aBundle(CARRIED_TOKEN), null, null));

    assertThat(
            admission.takeBack(
                provider,
                new DeclaredReproductionImport(
                    INTO.value(), false, anotherBundleCarrying(CARRIED_TOKEN), null, null)))
        .isInstanceOfSatisfying(
            ReproductionAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("setup.fixtures[0].providerIds.token"));
    assertThat(fixtures.of(provider.id(), INTO)).hasSize(1);
  }

  @Test
  void a_setup_the_database_finds_claimed_meanwhile_is_a_conflict_that_keeps_nothing() {
    ProviderDouble claimedMeanwhile =
        new ProviderDouble()
            .asking(
                new ConfigurableField(
                    "capture.window",
                    "The capture window",
                    "a sentence a test does not read",
                    false,
                    false,
                    List.of(),
                    "a value a test does not read"))
            .reading("capture.window")
            .findingTheSeedClaimedMeanwhile("taken while this setup was written");

    assertThat(
            admission()
                .takeBack(
                    claimedMeanwhile,
                    new DeclaredReproductionImport(
                        INTO.value(), false, aBundle(CARRIED_TOKEN), null, null)))
        .isInstanceOfSatisfying(
            ReproductionAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .containsExactly(
                        new BundleRefusal("fixtures[0]", "taken while this setup was written")));
    assertThat(unitsOfWork.endings()).containsOnly(RecordedUnitsOfWork.Ending.DISCARDED);
  }

  @Test
  void a_conflict_over_an_id_names_the_instrument_holding_it_and_never_the_id_itself() {
    ReproductionAdmission admission = admission();
    theReportOf(
        admission,
        new DeclaredReproductionImport(INTO.value(), false, aBundle(CARRIED_TOKEN), null, null));

    assertThat(
            admission.takeBack(
                provider,
                new DeclaredReproductionImport(
                    INTO.value(), false, anotherBundleCarrying(CARRIED_TOKEN), null, null)))
        .isInstanceOfSatisfying(
            ReproductionAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.refusals())
                    .singleElement()
                    .satisfies(
                        refusal -> {
                          assertThat(refusal.reason()).contains("visa-success");
                          assertThat(refusal.reason()).doesNotContain(CARRIED_TOKEN);
                        }));
  }

  @Test
  void an_id_the_target_run_holds_is_taken_back_under_a_remapping_that_frees_it() {
    ReproductionAdmission admission = admission();
    theReportOf(
        admission,
        new DeclaredReproductionImport(INTO.value(), false, aBundle(CARRIED_TOKEN), null, null));

    theReportOf(
        admission,
        new DeclaredReproductionImport(
            INTO.value(),
            false,
            anotherBundleCarrying(CARRIED_TOKEN),
            null,
            Map.of(CARRIED_TOKEN, A_FREE_TOKEN)));

    assertThat(fixtures.of(provider.id(), INTO))
        .extracting(seeded -> seeded.providerIds().get("token"))
        .containsExactlyInAnyOrder(CARRIED_TOKEN, A_FREE_TOKEN);
  }

  @Test
  void
      a_remapping_of_an_id_no_instrument_of_this_bundle_carries_is_refused_and_nothing_is_written() {
    assertThat(
            admission()
                .takeBack(
                    provider,
                    new DeclaredReproductionImport(
                        INTO.value(),
                        false,
                        aBundle(CARRIED_TOKEN),
                        null,
                        Map.of("tok_nothing", A_FREE_TOKEN))))
        .isInstanceOfSatisfying(
            ReproductionAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly(ProviderNativeIds.REMAP + ".tok_nothing"));
    assertThat(fixtures.of(provider.id(), INTO)).isEmpty();
    assertThat(accounts.identitiesOf(provider.id(), INTO)).isEmpty();
    assertThat(runs.registered()).isEmpty();
  }

  @Test
  void a_refusal_the_setup_itself_reports_is_reported_by_this_import_and_nothing_is_written() {
    RunBundle ofAnotherProvider =
        new RunBundle(
            new BundleCompatibility(List.of(), List.of("token"), List.of(), List.of()),
            "run-a",
            "someone-else",
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            List.of(),
            null);

    assertThat(
            admission()
                .takeBack(
                    provider,
                    new DeclaredReproductionImport(
                        INTO.value(),
                        false,
                        new ReproductionBundle(
                            "run-a", "someone-else", null, ofAnotherProvider, null, null),
                        null,
                        null)))
        .isInstanceOfSatisfying(
            ReproductionAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .contains(RunBundle.PROVIDER));
    assertThat(runs.registered()).isEmpty();
  }

  @Test
  void a_dry_run_reports_what_it_would_create_and_writes_none_of_it() {
    ReproductionReport report =
        theReportOf(
            new DeclaredReproductionImport(INTO.value(), true, aBundle(CARRIED_TOKEN), null, null));

    assertThat(report.applied()).isFalse();
    assertThat(report.setup().created()).isEqualTo(3);
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
    ReproductionAdmission admission = admission();
    ReproductionReport dry =
        theReportOf(
            admission,
            new DeclaredReproductionImport(INTO.value(), true, aBundle(CARRIED_TOKEN), null, null));

    ReproductionReport applied =
        theReportOf(
            admission,
            new DeclaredReproductionImport(
                INTO.value(), false, aBundle(CARRIED_TOKEN), null, null));

    assertThat(applied.applied()).isTrue();
    assertThat(applied.setup().entries()).isEqualTo(dry.setup().entries());
    assertThat(applied.theContentsItWrote()).isEqualTo(dry.theContentsItWrote());
    assertThat(applied.theContentsItOnlyRead()).isEqualTo(dry.theContentsItOnlyRead());
  }

  @Test
  void a_dry_run_that_would_conflict_over_a_preserved_id_says_so_and_still_writes_nothing() {
    ReproductionAdmission admission = admission();
    theReportOf(
        admission,
        new DeclaredReproductionImport(INTO.value(), false, aBundle(CARRIED_TOKEN), null, null));

    assertThat(
            admission.takeBack(
                provider,
                new DeclaredReproductionImport(
                    INTO.value(), true, anotherBundleCarrying(CARRIED_TOKEN), null, null)))
        .isInstanceOf(ReproductionAdmission.Verdict.Conflicting.class);
    assertThat(fixtures.of(provider.id(), INTO)).hasSize(1);
  }

  @Test
  void a_report_names_the_contents_the_import_wrote_and_the_contents_it_only_read() {
    ReproductionReport report =
        theReportOf(
            new DeclaredReproductionImport(
                INTO.value(), false, aBundle(CARRIED_TOKEN), null, null));

    assertThat(report.theContentsItWrote())
        .contains(ReproductionManifest.FIXTURES, ReproductionManifest.ACCOUNTS);
    assertThat(report.theContentsItOnlyRead())
        .contains(ReproductionManifest.TRAIL, ReproductionManifest.CALLBACKS);
    assertThat(report.theContentsItWrote())
        .doesNotContainAnyElementsOf(report.theContentsItOnlyRead());
  }

  @Test
  void a_bundle_whose_document_listed_no_contents_is_reported_against_the_contents_it_carries() {
    ReproductionBundle listingNothing =
        new ReproductionBundle(
            "run-a", ProviderDouble.ID.value(), null, aSetup(CARRIED_TOKEN), null, null);

    ReproductionReport report =
        ReproductionAdmission.theReportOf(
            listingNothing,
            BundleReport.of("run-b", "twokeys", true, List.of(), List.of()),
            List.of());

    assertThat(report.theContentsItWrote()).contains(ReproductionManifest.FIXTURES);
    assertThat(report.theContentsItOnlyRead()).isEmpty();
  }

  @Test
  void
      a_rejected_a_refused_and_a_conflicting_import_each_report_the_refusal_the_trail_files_them_under() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            ReproductionAdmission.THE_BUNDLE_IT_ASKED_THIS_PROVIDER_TO_TAKE_BACK);

    assertThat(new ReproductionAdmission.Verdict.Rejected("no").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(subject));
    assertThat(new ReproductionAdmission.Verdict.Refused(List.of()).theRefusalItReports())
        .isEqualTo(ActionVerdict.refused(subject));
    assertThat(new ReproductionAdmission.Verdict.Conflicting(List.of()).theRefusalItReports())
        .isEqualTo(ActionVerdict.conflicting(subject));
    assertThat(
            new ReproductionAdmission.Verdict.Taken(
                    ReproductionReport.of(
                        BundleReport.of("run-a", "twokeys", true, List.of(), List.of()),
                        ReproductionManifest.of(null, null),
                        List.of()))
                .theRefusalItReports())
        .isNull();

    assertThat(new ReproductionAdmission.Verdict.Rejected("no").theRefusalItReports().httpStatus())
        .isEqualTo(400);
    assertThat(
            new ReproductionAdmission.Verdict.Refused(List.of()).theRefusalItReports().httpStatus())
        .isEqualTo(422);
    assertThat(
            new ReproductionAdmission.Verdict.Conflicting(List.of())
                .theRefusalItReports()
                .httpStatus())
        .isEqualTo(409);
  }

  @Test
  void the_refusals_a_refused_or_conflicting_import_was_built_from_do_not_change_it_afterwards() {
    List<BundleRefusal> refusals =
        new ArrayList<>(List.of(new BundleRefusal("setup.provider", "no")));
    ReproductionAdmission.Verdict.Refused refused =
        new ReproductionAdmission.Verdict.Refused(refusals);
    ReproductionAdmission.Verdict.Conflicting conflicting =
        new ReproductionAdmission.Verdict.Conflicting(refusals);
    refusals.add(new BundleRefusal("setup.compatibility", "no"));

    assertThat(refused.refusals()).hasSize(1);
    assertThat(conflicting.refusals()).hasSize(1);
  }

  @Test
  void only_the_keys_this_provider_says_name_an_owner_are_the_keys_many_instruments_may_share() {
    ProviderDouble namingAnOwner =
        new ProviderDouble()
            .seeding(
                new ProviderPaymentMethodFields(
                    ProviderDouble.ID,
                    List.of(
                        new ConfigurableField(
                            ProviderPaymentMethodFields.TOKEN,
                            "the saved card token",
                            "a sentence a test does not read",
                            true,
                            false,
                            List.of(),
                            "a value a test does not read"),
                        new ConfigurableField(
                            ProviderPaymentMethodFields.CUSTOMER_ID,
                            "the customer",
                            "a sentence a test does not read",
                            false,
                            false,
                            List.of(),
                            "a value a test does not read")),
                    Map.of(
                        ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                        "this provider calls a saved card a token and nothing else"),
                    Set.of(ProviderPaymentMethodFields.CUSTOMER_ID)));

    assertThat(
            ReproductionAdmission.theKeysManyFixturesMayShare(
                namingAnOwner,
                List.of(
                    fixture(
                        "visa-success",
                        Map.of(
                            ProviderPaymentMethodFields.TOKEN,
                            CARRIED_TOKEN,
                            ProviderPaymentMethodFields.CUSTOMER_ID,
                            "cus_1")))))
        .containsExactly(ProviderPaymentMethodFields.CUSTOMER_ID);
    assertThat(
            ReproductionAdmission.theKeysManyFixturesMayShare(
                provider, List.of(fixture("visa-success", Map.of("token", CARRIED_TOKEN)))))
        .isEmpty();
  }

  private ReproductionAdmission admission() {
    BundleAdmission setups =
        new BundleAdmission(
            new ProviderConfigurations(accounts, aliases, declaredSettings(), settings),
            new ScenarioAdmission(
                new ScenarioGrammar(new FaultDelayCeiling(Duration.ofSeconds(30))),
                rules,
                runs,
                DEFAULT_RUN),
            rules,
            new PaymentMethodFixtureAdmission(fixtures, rules, runs, unitsOfWork, DEFAULT_RUN),
            accounts,
            aliases,
            settings,
            declaredSettings(),
            fixtures,
            pins,
            runs,
            unitsOfWork,
            DEFAULT_RUN);
    return new ReproductionAdmission(setups, fixtures, DEFAULT_RUN);
  }

  private DeclaredProviderSettings declaredSettings() {
    return new DeclaredProviderSettings(
        List.of(
            new ProviderSettings(
                provider.id(),
                List.of(new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S")))));
  }

  private ReproductionReport theReportOf(DeclaredReproductionImport declared) {
    return theReportOf(admission(), declared);
  }

  private ReproductionReport theReportOf(
      ReproductionAdmission admission, DeclaredReproductionImport declared) {
    return ((ReproductionAdmission.Verdict.Taken) admission.takeBack(provider, declared)).report();
  }

  private ReproductionBundle aBundle(String token) {
    return ReproductionBundle.of(
        "run-a", ProviderDouble.ID.value(), AT, aSetup(token), someEvidence());
  }

  private ReproductionBundle anotherBundleCarrying(String token) {
    return ReproductionBundle.of(
        "run-a",
        ProviderDouble.ID.value(),
        AT,
        aSetup(token)
            .carryingInstead(
                List.of(fixture("visa-decline", Map.of(ProviderPaymentMethodFields.TOKEN, token)))),
        someEvidence());
  }

  private static ReproductionEvidence someEvidence() {
    return new ReproductionEvidence(
        "ch_1",
        RecordedEvidence.ADDRESSED_BY_ITS_PROVIDER_OBJECT_ID,
        AT,
        null,
        List.of(
            new RecordedExchange(
                0,
                "aud_1",
                "PROVIDER_API",
                "INBOUND_REQUEST",
                "POST",
                "/v1/charges",
                null,
                null,
                null,
                null,
                null,
                1,
                AT,
                Map.of(),
                Map.of("amount", List.of("1000")))),
        List.of(
            new RecordedCallback(
                "whd_1",
                "evt_1",
                null,
                "ch_1",
                "DELIVERED",
                "https://merchant.test/hook",
                1,
                3,
                null,
                null,
                null,
                "application/json",
                Map.of(),
                List.of())),
        false);
  }

  private RunBundle aSetup(String token) {
    return new RunBundle(
        BundleVocabulary.of(provider, declaredSettings().of(provider.id())),
        "run-a",
        "twokeys",
        null,
        null,
        List.of(new PortableAccount("merchant-1", Map.of("capture.window", "P3D"), List.of())),
        List.of(),
        List.of(),
        Map.of(SettingKey.WEBHOOK_RETRY_PLAN.key(), "PT0S"),
        List.of(fixture("visa-success", Map.of("token", token))),
        null);
  }

  private static PortableFixture fixture(String alias, Map<String, String> providerIds) {
    return new PortableFixture(
        "merchant-1",
        "owner-1",
        "card",
        alias,
        new PortableCard("visa", "4242", 12, 2031, "credit"),
        providerIds,
        null);
  }

  @Test
  void an_origin_a_seeded_fixture_records_is_the_control_api_whichever_surface_asked() {
    theReportOf(
        new DeclaredReproductionImport(INTO.value(), false, aBundle(CARRIED_TOKEN), null, null));

    assertThat(fixtures.of(provider.id(), INTO)).hasSize(1);
    assertThat(runs.registered()).contains(INTO);
    assertThat(ObjectOrigin.CONTROL_API).isNotNull();
  }

  @Test
  void an_entry_a_report_carries_names_the_kind_of_thing_it_reports_on() {
    ReproductionReport report =
        theReportOf(
            new DeclaredReproductionImport(INTO.value(), true, aBundle(CARRIED_TOKEN), null, null));

    assertThat(report.setup().entries())
        .extracting(BundleEntry::kind)
        .contains(BundleEntry.ACCOUNT, BundleEntry.SETTING, BundleEntry.FIXTURE);
  }
}
