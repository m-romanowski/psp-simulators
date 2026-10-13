package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork.Ending;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PersonaAdmissionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId MINE = TestRunId.of("run-a");

  private static final TestRunId SOMEONE_ELSES = TestRunId.of("run-b");

  private static final String ACCOUNT = "acct-1";

  private static final String TOKEN = ProviderPaymentMethodFields.TOKEN;

  private final InMemoryPaymentMethodFixtures seeded = new InMemoryPaymentMethodFixtures();
  private final InMemoryScenarioRules scenarios = new InMemoryScenarioRules();
  private final BundleStores.RegisteredRuns testRuns = new BundleStores.RegisteredRuns();
  private final CountingIds ids = new CountingIds();
  private final RecordedUnitsOfWork unitsOfWork = new RecordedUnitsOfWork();

  private final ProviderDouble provider =
      new ProviderDouble()
          .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
          .publishing(aCatalogueNaming("a-card-that-works"));

  private final ScenarioAdmission rules =
      new ScenarioAdmission(
          new ScenarioGrammar(new FaultDelayCeiling(Duration.ofSeconds(30))),
          scenarios,
          testRuns,
          DEFAULT_RUN);

  private final PersonaAdmission admission =
      new PersonaAdmission(
          rules,
          new PaymentMethodFixtureAdmission(seeded, scenarios, testRuns, unitsOfWork, DEFAULT_RUN),
          seeded,
          ids,
          unitsOfWork,
          DEFAULT_RUN);

  @Test
  void a_persona_writes_its_presets_rules_and_seeds_an_instrument_that_names_the_first_of_them() {
    PersonaAdmission.Verdict verdict =
        admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Seeded.class,
            wasSeeded -> {
              SeededPersona created = wasSeeded.seeded();
              assertThat(created.rules()).hasSize(1);
              assertThat(created.fixtures()).hasSize(1);
              PaymentMethodFixture fixture = created.fixtures().get(0);
              assertThat(fixture.fixtureAlias()).isEqualTo("a-card-that-works");
              assertThat(fixture.testRunId()).isEqualTo(MINE);
              assertThat(fixture.merchantId()).isEqualTo(ACCOUNT);
              assertThat(fixture.providerIds().get(TOKEN)).isNotBlank();
              assertThat(fixture.defaultScenario()).isEqualTo(created.rules().get(0).scenarioId());
            });
  }

  @Test
  void a_persona_that_writes_a_rule_and_seeds_an_instrument_keeps_both_in_one_unit_of_work() {
    admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    assertThat(unitsOfWork.endings()).containsExactly(Ending.KEPT, Ending.KEPT);
  }

  @Test
  void
      a_persona_whose_second_instrument_the_fixture_store_already_holds_the_token_of_refuses_the_whole_seed() {
    ProviderDouble sharedToken =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueOfTwoInstrumentsSharingOneToken());

    PersonaAdmission.Verdict verdict =
        admission.seed(sharedToken, asking("two-cards", MINE), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Conflicting.class,
            conflicting -> assertThat(conflicting.message()).contains("providerIds." + TOKEN));
    assertThat(unitsOfWork.endings())
        .containsExactly(Ending.DISCARDED, Ending.DISCARDED, Ending.DISCARDED);
  }

  @Test
  void
      a_scenario_rule_the_database_finds_claimed_meanwhile_refuses_the_persona_seed_as_a_conflict_and_seeds_no_instrument() {
    RecordedUnitsOfWork racedUnitOfWork = new RecordedUnitsOfWork();
    ClaimingScenarioRules claiming =
        new ClaimingScenarioRules("a-card-that-works taken while this persona wrote it");
    ScenarioAdmission racedScenarios =
        new ScenarioAdmission(
            new ScenarioGrammar(new FaultDelayCeiling(Duration.ofSeconds(30))),
            claiming,
            testRuns,
            DEFAULT_RUN);
    PersonaAdmission raced =
        new PersonaAdmission(
            racedScenarios,
            new PaymentMethodFixtureAdmission(
                seeded, claiming, testRuns, racedUnitOfWork, DEFAULT_RUN),
            seeded,
            ids,
            racedUnitOfWork,
            DEFAULT_RUN);

    PersonaAdmission.Verdict verdict =
        raced.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.message())
                    .isEqualTo("a-card-that-works taken while this persona wrote it"));
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void every_verdict_reports_the_refusal_the_trail_files_it_under_and_a_seeded_one_reports_none() {
    RefusalSubject thePersona =
        RefusalSubject.noObjectWasNamed(
            PersonaAdmission.THE_PERSONA_IT_ASKED_THIS_PROVIDER_TO_SEED);

    assertThat(new PersonaAdmission.Verdict.Rejected("unreadable").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(thePersona));
    assertThat(new PersonaAdmission.Verdict.Unknown("no such persona").theRefusalItReports())
        .isEqualTo(
            ActionVerdict.notFound(RefusalSubject.noObjectAnsweredTo(PersonaAdmission.PERSONA)));
    assertThat(new PersonaAdmission.Verdict.Conflicting("already held").theRefusalItReports())
        .isEqualTo(ActionVerdict.conflicting(thePersona));
    assertThat(new PersonaAdmission.Verdict.Refused(List.of()).theRefusalItReports())
        .isEqualTo(ActionVerdict.refused(thePersona));

    assertThat(
            new PersonaAdmission.Verdict.Rejected("unreadable").theRefusalItReports().httpStatus())
        .isEqualTo(400);
    assertThat(
            new PersonaAdmission.Verdict.Unknown("no such persona")
                .theRefusalItReports()
                .httpStatus())
        .isEqualTo(404);
    assertThat(
            new PersonaAdmission.Verdict.Conflicting("already held")
                .theRefusalItReports()
                .httpStatus())
        .isEqualTo(409);
    assertThat(new PersonaAdmission.Verdict.Refused(List.of()).theRefusalItReports().httpStatus())
        .isEqualTo(422);

    PersonaAdmission.Verdict seededByTheRealPath =
        admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);
    assertThat(seededByTheRealPath).isInstanceOf(PersonaAdmission.Verdict.Seeded.class);
    assertThat(seededByTheRealPath.theRefusalItReports()).isNull();
  }

  @Test
  void the_instrument_carries_the_id_the_provider_minted_for_it_and_not_one_the_core_chose() {
    admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    assertThat(seeded.of(ProviderDouble.ID, MINE))
        .singleElement()
        .satisfies(
            fixture ->
                assertThat(fixture.providerIds()).containsEntry(TOKEN, "minted-000000000001"));
  }

  @Test
  void a_persona_seeded_into_one_run_is_invisible_to_another_run() {
    admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    assertThat(seeded.of(ProviderDouble.ID, SOMEONE_ELSES)).isEmpty();
    assertThat(scenarios.of(ProviderDouble.ID, SOMEONE_ELSES)).isEmpty();
    assertThat(scenarios.of(ProviderDouble.ID, MINE)).hasSize(1);
  }

  @Test
  void two_runs_seeding_the_same_persona_each_get_their_own_objects() {
    admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);
    admission.seed(provider, asking("a-card-that-works", SOMEONE_ELSES), ObjectOrigin.CONTROL_API);

    assertThat(seeded.of(ProviderDouble.ID, MINE)).hasSize(1);
    assertThat(seeded.of(ProviderDouble.ID, SOMEONE_ELSES)).hasSize(1);
    assertThat(seeded.of(ProviderDouble.ID, MINE).get(0).fixtureId())
        .isNotEqualTo(seeded.of(ProviderDouble.ID, SOMEONE_ELSES).get(0).fixtureId());
  }

  @Test
  void a_name_this_provider_does_not_publish_is_answered_as_a_missing_persona_and_writes_nothing() {
    PersonaAdmission.Verdict verdict =
        admission.seed(provider, asking("a-card-that-does-not-exist", MINE), ObjectOrigin.UI);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Unknown.class,
            unknown -> {
              assertThat(unknown.message()).contains("a-card-that-works");
              assertThat(unknown.theRefusalItReports())
                  .isEqualTo(ActionVerdict.notFound(RefusalSubject.noObjectAnsweredTo("persona")));
            });
    assertThat(scenarios.of(ProviderDouble.ID, MINE)).isEmpty();
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void a_run_that_already_holds_the_instrument_is_refused_and_no_second_rule_is_written() {
    admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    PersonaAdmission.Verdict again =
        admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    assertThat(again)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Conflicting.class,
            conflicting -> {
              assertThat(conflicting.message()).contains("a-card-that-works");
              assertThat(conflicting.theRefusalItReports().httpStatus()).isEqualTo(409);
            });
    assertThat(scenarios.of(ProviderDouble.ID, MINE)).hasSize(1);
    assertThat(seeded.of(ProviderDouble.ID, MINE)).hasSize(1);
  }

  @Test
  void a_provider_native_id_another_instrument_of_the_run_holds_is_refused_before_any_rule() {
    ProviderDouble fixedToken =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueMintingTheSameTokenTwice());

    assertThat(admission.seed(fixedToken, asking("first-card", MINE), ObjectOrigin.CONTROL_API))
        .isInstanceOf(PersonaAdmission.Verdict.Seeded.class);

    PersonaAdmission.Verdict second =
        admission.seed(fixedToken, asking("second-card", MINE), ObjectOrigin.CONTROL_API);

    assertThat(second)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Conflicting.class,
            conflicting -> assertThat(conflicting.message()).contains("providerIds." + TOKEN));
    assertThat(scenarios.of(ProviderDouble.ID, MINE)).hasSize(1);
  }

  @Test
  void a_seeding_that_names_no_account_is_rejected_and_writes_nothing() {
    PersonaAdmission.Verdict verdict =
        admission.seed(
            provider,
            new DeclaredPersonaSeeding(MINE.value(), "a-card-that-works", " "),
            ObjectOrigin.UI);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .isEqualTo(PersonaAdmission.A_PERSONA_IS_SEEDED_INTO_AN_ACCOUNT));
    assertThat(scenarios.of(ProviderDouble.ID, MINE)).isEmpty();
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void a_request_naming_a_test_run_no_namespace_could_hold_is_rejected_before_the_catalogue() {
    PersonaAdmission.Verdict verdict =
        admission.seed(
            provider,
            new DeclaredPersonaSeeding("a run with spaces", "a-card-that-works", ACCOUNT),
            ObjectOrigin.UI);

    assertThat(verdict).isInstanceOf(PersonaAdmission.Verdict.Rejected.class);
    assertThat(
            admission.whatItWouldCreate(
                provider,
                new DeclaredPersonaSeeding("a run with spaces", "a-card-that-works", ACCOUNT)))
        .isInstanceOf(PersonaAdmission.Plan.Rejected.class);
  }

  @Test
  void a_request_naming_no_persona_at_all_is_rejected() {
    assertThat(
            admission.seed(
                provider, new DeclaredPersonaSeeding(MINE.value(), null, ACCOUNT), ObjectOrigin.UI))
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Rejected.class,
            rejected ->
                assertThat(rejected.message())
                    .isEqualTo(PersonaAdmission.A_PERSONA_IS_NAMED_BY_THE_REQUEST));
  }

  @Test
  void a_preset_this_provider_cannot_express_refuses_the_persona_and_writes_nothing() {
    ProviderDouble cannot =
        new ProviderDouble()
            .expanding(new PresetExpansion.NotExpressible("this provider holds no money"))
            .publishing(aCatalogueNaming("a-card-that-works"));

    PersonaAdmission.Verdict verdict =
        admission.seed(cannot, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.refusals())
                  .singleElement()
                  .satisfies(
                      one -> {
                        assertThat(one.property()).isEqualTo("preset");
                        assertThat(one.reason()).isEqualTo("this provider holds no money");
                      });
              assertThat(refused.theRefusalItReports().httpStatus()).isEqualTo(422);
            });
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void an_instrument_naming_a_rule_its_presets_do_not_reach_is_refused_naming_that_place() {
    ProviderDouble reachingTooFar =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueNamingTheRuleAt(3));

    PersonaAdmission.Verdict verdict =
        admission.seed(reachingTooFar, asking("a-card-that-works", MINE), ObjectOrigin.UI);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .singleElement()
                    .satisfies(
                        one -> {
                          assertThat(one.property()).isEqualTo("fixtures[0].triggersTheRuleAt");
                          assertThat(one.reason()).contains("place 3");
                        }));
    assertThat(scenarios.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void a_plan_shows_the_rules_and_writes_none_of_them() {
    PersonaAdmission.Plan plan =
        admission.whatItWouldCreate(provider, asking("a-card-that-works", MINE));

    assertThat(plan)
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned -> {
              assertThat(planned.rules()).hasSize(1);
              assertThat(planned.testRunId()).isEqualTo(MINE);
              assertThat(planned.theRunAlreadyHoldsIt()).isFalse();
            });
    assertThat(scenarios.of(ProviderDouble.ID, MINE)).isEmpty();
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void a_plan_states_the_refusal_it_cannot_foresee_for_every_id_a_creation_compares() {
    PersonaAdmission.Plan plan =
        admission.whatItWouldCreate(provider, asking("a-card-that-works", MINE));

    assertThat(
            PersonaAdmission.theProviderNativeKeysAPlanCannotCompare(
                thePersonaNamed(provider, "a-card-that-works"), provider.paymentMethodFields()))
        .containsExactly(TOKEN);
    assertThat(plan)
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned ->
                assertThat(planned.theRefusalThisPlanCannotForesee())
                    .contains("the provider mints them at the moment the persona is created")
                    .contains("cannot foresee that refusal")
                    .contains("providerIds." + TOKEN));
  }

  @Test
  void a_plan_whose_ids_all_name_an_owner_states_no_refusal_it_cannot_foresee() {
    ProviderDouble sharingItsOwner =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .seeding(aProviderWhoseCustomerIdNamesAnOwner())
            .publishing(aCatalogueMintingACustomerId());

    PersonaAdmission.Plan plan =
        admission.whatItWouldCreate(sharingItsOwner, asking("a-card-that-works", MINE));

    assertThat(
            PersonaAdmission.theProviderNativeKeysAPlanCannotCompare(
                thePersonaNamed(sharingItsOwner, "a-card-that-works"),
                sharingItsOwner.paymentMethodFields()))
        .isEmpty();
    assertThat(plan)
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned -> assertThat(planned.theRefusalThisPlanCannotForesee()).isNull());
  }

  @Test
  void a_plan_states_no_refusal_it_cannot_foresee_for_an_id_whose_value_the_persona_publishes() {
    ProviderDouble publishingItsId =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueCarryingAPublishedToken());

    PersonaAdmission.Plan plan =
        admission.whatItWouldCreate(publishingItsId, asking("a-card-that-works", MINE));

    assertThat(
            PersonaAdmission.theProviderNativeKeysAPlanCannotCompare(
                thePersonaNamed(publishingItsId, "a-card-that-works"),
                publishingItsId.paymentMethodFields()))
        .isEmpty();
    assertThat(plan)
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned -> assertThat(planned.theRefusalThisPlanCannotForesee()).isNull());
  }

  @Test
  void a_plan_foresees_the_refusal_of_a_published_id_another_instrument_of_the_run_already_holds() {
    ProviderDouble publishingItsId =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueOfTwoPersonasSharingOnePublishedToken());

    assertThat(
            admission.seed(publishingItsId, asking("first-card", MINE), ObjectOrigin.CONTROL_API))
        .isInstanceOf(PersonaAdmission.Verdict.Seeded.class);

    assertThat(admission.whatItWouldCreate(publishingItsId, asking("second-card", MINE)))
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned -> {
              assertThat(planned.theRunAlreadyHoldsIt()).isTrue();
              assertThat(planned.theInstrumentsThisRunAlreadyHolds())
                  .singleElement()
                  .satisfies(
                      held -> {
                        assertThat(held.theAliasTheRunHolds()).isEqualTo("first-card");
                        assertThat(held.theAliasThisPersonaWouldSeed()).isEqualTo("second-card");
                        assertThat(held.underTheKey()).isEqualTo(TOKEN);
                        assertThat(held.andTheValue()).isEqualTo("the-published-token");
                      });
              assertThat(planned.theRefusalThisPlanCannotForesee()).isNull();
            });

    assertThat(
            admission.seed(publishingItsId, asking("second-card", MINE), ObjectOrigin.CONTROL_API))
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Conflicting.class,
            refused ->
                assertThat(refused.message())
                    .contains("providerIds." + TOKEN + " the-published-token")
                    .contains("already holds the instrument first-card"));
  }

  @Test
  void a_plan_does_not_foresee_a_published_id_no_other_instrument_of_the_run_holds() {
    ProviderDouble publishingItsId =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueOfTwoPersonasSharingOnePublishedToken());

    assertThat(admission.whatItWouldCreate(publishingItsId, asking("second-card", MINE)))
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned -> {
              assertThat(planned.theRunAlreadyHoldsIt()).isFalse();
              assertThat(planned.theInstrumentsThisRunAlreadyHolds()).isEmpty();
            });
  }

  @Test
  void a_published_id_that_names_an_owner_is_not_foreseen_because_many_fixtures_may_share_it() {
    ProviderDouble sharingItsOwner =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .seeding(aProviderWhoseCustomerIdNamesAnOwner())
            .publishing(aCatalogueOfTwoPersonasSharingOnePublishedCustomerId());

    assertThat(
            admission.seed(sharingItsOwner, asking("first-card", MINE), ObjectOrigin.CONTROL_API))
        .isInstanceOf(PersonaAdmission.Verdict.Seeded.class);

    assertThat(admission.whatItWouldCreate(sharingItsOwner, asking("second-card", MINE)))
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned -> {
              assertThat(planned.theRunAlreadyHoldsIt()).isFalse();
              assertThat(planned.theInstrumentsThisRunAlreadyHolds()).isEmpty();
            });
  }

  @Test
  void
      the_persona_seeded_before_is_reported_once_though_its_alias_and_its_published_id_both_match() {
    ProviderDouble publishingItsId =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueOfTwoPersonasSharingOnePublishedToken());

    assertThat(
            admission.seed(publishingItsId, asking("first-card", MINE), ObjectOrigin.CONTROL_API))
        .isInstanceOf(PersonaAdmission.Verdict.Seeded.class);

    assertThat(admission.whatItWouldCreate(publishingItsId, asking("first-card", MINE)))
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned ->
                assertThat(planned.theInstrumentsThisRunAlreadyHolds())
                    .singleElement()
                    .satisfies(
                        held -> {
                          assertThat(held.theAliasTheRunHolds()).isEqualTo("first-card");
                          assertThat(held.underTheKey()).isNull();
                          assertThat(held.andTheValue()).isNull();
                        }));

    assertThat(
            admission.seed(publishingItsId, asking("first-card", MINE), ObjectOrigin.CONTROL_API))
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Conflicting.class,
            refused ->
                assertThat(refused.message())
                    .contains("already holds the instrument first-card that persona first-card")
                    .doesNotContain("providerIds."));
  }

  @Test
  void a_plan_names_only_the_ids_it_cannot_compare_when_one_of_two_is_published() {
    ProviderDouble publishingOneOfTwo =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .seeding(aProviderThatReadsATokenAndAPaymentMethodId())
            .publishing(aCatalogueCarryingAPublishedTokenAndAMintedPaymentMethodId());

    assertThat(
            PersonaAdmission.theProviderNativeKeysAPlanCannotCompare(
                thePersonaNamed(publishingOneOfTwo, "a-card-that-works"),
                publishingOneOfTwo.paymentMethodFields()))
        .containsExactly(ProviderPaymentMethodFields.PAYMENT_METHOD_ID);
    assertThat(admission.whatItWouldCreate(publishingOneOfTwo, asking("a-card-that-works", MINE)))
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned ->
                assertThat(planned.theRefusalThisPlanCannotForesee())
                    .contains("providerIds." + ProviderPaymentMethodFields.PAYMENT_METHOD_ID)
                    .doesNotContain("providerIds." + TOKEN));
  }

  @Test
  void a_plan_says_the_run_already_holds_the_persona_once_it_has_been_seeded() {
    admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    assertThat(admission.whatItWouldCreate(provider, asking("a-card-that-works", MINE)))
        .isInstanceOfSatisfying(
            PersonaAdmission.Plan.Planned.class,
            planned -> {
              assertThat(planned.theRunAlreadyHoldsIt()).isTrue();
              assertThat(planned.theAliasesThisRunAlreadyHolds())
                  .containsExactly("a-card-that-works");
            });
  }

  @Test
  void an_instrument_that_mints_an_id_under_a_key_it_never_published_is_refused_to_its_author() {
    ProviderDouble lying =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueThatMintsAKeyItNeverPublished());

    DeclaredPersonaSeeding asked = asking("a-card-that-works", MINE);

    assertThatThrownBy(() -> admission.seed(lying, asked, ObjectOrigin.UI))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("described something other than what it created");
  }

  @Test
  void the_rules_a_persona_writes_are_the_rules_the_run_holds_afterwards() {
    admission.seed(provider, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    List<ScenarioRule> held = scenarios.of(ProviderDouble.ID, MINE);
    assertThat(held)
        .singleElement()
        .satisfies(
            rule -> {
              assertThat(rule.match().operation()).isEqualTo("capture");
              assertThat(rule.action()).isInstanceOf(ScenarioAction.FixedSuccess.class);
            });
  }

  @Test
  void a_provider_native_id_this_provider_reads_nothing_of_is_refused_before_any_rule_is_written() {
    ProviderDouble readsNoCustomer =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueMintingACustomerId());

    PersonaAdmission.Verdict verdict =
        admission.seed(readsNoCustomer, asking("a-card-that-works", MINE), ObjectOrigin.UI);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .singleElement()
                    .satisfies(
                        one -> {
                          assertThat(one.property()).isEqualTo("providerIds.customerId");
                          assertThat(one.reason())
                              .isEqualTo("this provider names no customer anywhere");
                        }));
    assertThat(scenarios.of(ProviderDouble.ID, MINE)).isEmpty();
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void
      a_provider_that_stops_expressing_its_preset_after_the_plan_refuses_and_seeds_no_instrument() {
    ProviderDouble stops =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .stoppingAfterTheFirstExpansion("this provider withdrew the operation")
            .publishing(aCatalogueNaming("a-card-that-works"));

    PersonaAdmission.Verdict verdict =
        admission.seed(stops, asking("a-card-that-works", MINE), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .singleElement()
                    .satisfies(
                        one ->
                            assertThat(one.reason())
                                .isEqualTo("this provider withdrew the operation")));
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void a_provider_that_refuses_the_seed_itself_is_reported_as_a_refusal_about_the_persona() {
    ProviderDouble refusesTheSeed =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .refusingTheSeed("providerIds.token", "this provider saved no such card")
            .publishing(aCatalogueNaming("a-card-that-works"));

    PersonaAdmission.Verdict verdict =
        admission.seed(refusesTheSeed, asking("a-card-that-works", MINE), ObjectOrigin.UI);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .singleElement()
                    .satisfies(
                        one -> {
                          assertThat(one.property()).isEqualTo("providerIds.token");
                          assertThat(one.reason()).isEqualTo("this provider saved no such card");
                        }));
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void an_instrument_naming_the_rule_one_past_the_last_one_written_is_refused() {
    ProviderDouble onePastTheEnd =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueNamingTheRuleAt(1));

    assertThat(admission.seed(onePastTheEnd, asking("a-card-that-works", MINE), ObjectOrigin.UI))
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .singleElement()
                    .satisfies(one -> assertThat(one.reason()).contains("place 1")));
  }

  @Test
  void an_instrument_naming_the_last_rule_written_is_seeded_against_it() {
    ProviderDouble theLastRule =
        new ProviderDouble()
            .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
            .publishing(aCatalogueNamingTheRuleAt(0));

    assertThat(admission.seed(theLastRule, asking("a-card-that-works", MINE), ObjectOrigin.UI))
        .isInstanceOf(PersonaAdmission.Verdict.Seeded.class);
  }

  private ProviderPersonas aCatalogueMintingACustomerId() {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(
            new DeclaredPersona(
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "a-card-that-works",
                "a persona naming an id this provider reads nothing of",
                List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
                List.of(
                    new PersonaFixture(
                        "a-card-that-works",
                        "an-owner",
                        PaymentMethodKind.CARD,
                        aCard(),
                        List.of(ProviderPaymentMethodFields.CUSTOMER_ID),
                        "the double says it mints a customer",
                        () -> Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, "cus_1"),
                        0)))),
        theIntentsItPublishesNoneFor());
  }

  private ProviderPersonas aCatalogueCarryingAPublishedToken() {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(
            new DeclaredPersona(
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "a-card-that-works",
                "a persona whose token is a value this provider publishes",
                List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
                List.of(
                    new PersonaFixture(
                        "a-card-that-works",
                        "an-owner",
                        PaymentMethodKind.CARD,
                        aCard(),
                        List.of(TOKEN),
                        "the double says the token is the one value it publishes",
                        () -> Map.of(TOKEN, "the-published-token"),
                        0,
                        Map.of(TOKEN, "the-published-token"))))),
        theIntentsItPublishesNoneFor());
  }

  private ProviderPersonas aCatalogueCarryingAPublishedTokenAndAMintedPaymentMethodId() {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(
            new DeclaredPersona(
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "a-card-that-works",
                "a persona that publishes one of its two ids and mints the other",
                List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
                List.of(
                    new PersonaFixture(
                        "a-card-that-works",
                        "an-owner",
                        PaymentMethodKind.CARD,
                        aCard(),
                        List.of(TOKEN, ProviderPaymentMethodFields.PAYMENT_METHOD_ID),
                        "the double publishes the token and mints the payment method id",
                        () ->
                            Map.of(
                                TOKEN,
                                "the-published-token",
                                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                                "minted-" + ids.hex(12)),
                        0,
                        Map.of(TOKEN, "the-published-token"))))),
        theIntentsItPublishesNoneFor());
  }

  private ProviderPersonas aCatalogueOfTwoPersonasSharingOnePublishedToken() {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(
            aPersonaPublishingTheToken(
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "first-card",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
            aPersonaPublishingTheToken(
                PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
                "second-card",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE)),
        Map.of(
            PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
            "this double drops no connection"));
  }

  private static DeclaredPersona aPersonaPublishingTheToken(
      PersonaIntent intent, String name, ScenarioPreset preset) {
    return new DeclaredPersona(
        intent,
        name,
        "a persona whose token is the one value this provider publishes",
        List.of(preset),
        List.of(
            new PersonaFixture(
                name,
                "an-owner",
                PaymentMethodKind.CARD,
                aCard(),
                List.of(TOKEN),
                "the double says the token is the one value it publishes",
                () -> Map.of(TOKEN, "the-published-token"),
                0,
                Map.of(TOKEN, "the-published-token"))));
  }

  private ProviderPersonas aCatalogueOfTwoPersonasSharingOnePublishedCustomerId() {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(
            aPersonaPublishingTheCustomerId(
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "first-card",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
            aPersonaPublishingTheCustomerId(
                PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
                "second-card",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE)),
        Map.of(
            PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
            "this double drops no connection"));
  }

  private DeclaredPersona aPersonaPublishingTheCustomerId(
      PersonaIntent intent, String name, ScenarioPreset preset) {
    return new DeclaredPersona(
        intent,
        name,
        "a persona whose customer is the owner its card belongs to",
        List.of(preset),
        List.of(
            new PersonaFixture(
                name,
                "an-owner",
                PaymentMethodKind.CARD,
                aCard(),
                List.of(TOKEN, ProviderPaymentMethodFields.CUSTOMER_ID),
                "the double mints a token and publishes the customer",
                () ->
                    Map.of(
                        TOKEN,
                        "minted-" + ids.hex(12),
                        ProviderPaymentMethodFields.CUSTOMER_ID,
                        "the-published-customer"),
                0,
                Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, "the-published-customer"))));
  }

  private DeclaredPersona thePersonaNamed(ProviderDouble module, String name) {
    return admission.publishedBy(module).named(name).orElseThrow();
  }

  private static ProviderPaymentMethodFields aProviderWhoseCustomerIdNamesAnOwner() {
    return new ProviderPaymentMethodFields(
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
                "the customer the card belongs to",
                "a sentence a test does not read",
                false,
                false,
                List.of(),
                "a value a test does not read")),
        Map.of(
            ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
            "this provider calls a saved card a token and nothing else"),
        Set.of(ProviderPaymentMethodFields.CUSTOMER_ID));
  }

  private static ProviderPaymentMethodFields aProviderThatReadsATokenAndAPaymentMethodId() {
    return new ProviderPaymentMethodFields(
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
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                "the card id a payment names",
                "a sentence a test does not read",
                true,
                false,
                List.of(),
                "a value a test does not read")),
        Map.of(
            ProviderPaymentMethodFields.CUSTOMER_ID, "this provider names no customer anywhere"));
  }

  private static DeclaredPersonaSeeding asking(String persona, TestRunId run) {
    return new DeclaredPersonaSeeding(run.value(), persona, ACCOUNT);
  }

  private ProviderPersonas aCatalogueNaming(String name) {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(personaOf(name, 0, () -> Map.of(TOKEN, "minted-" + ids.hex(12)))),
        theIntentsItPublishesNoneFor());
  }

  private ProviderPersonas aCatalogueNamingTheRuleAt(int place) {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(
            personaOf("a-card-that-works", place, () -> Map.of(TOKEN, "minted-" + ids.hex(12)))),
        theIntentsItPublishesNoneFor());
  }

  private ProviderPersonas aCatalogueMintingTheSameTokenTwice() {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(
            personaOf("first-card", 0, () -> Map.of(TOKEN, "one-token")),
            new DeclaredPersona(
                PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
                "second-card",
                "the same token under another name",
                List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE),
                List.of(instrumentOf("second-card", 0, () -> Map.of(TOKEN, "one-token"))))),
        Map.of(
            PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
            "this double drops no connection"));
  }

  private ProviderPersonas aCatalogueThatMintsAKeyItNeverPublished() {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(
            new DeclaredPersona(
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "a-card-that-works",
                "a persona whose declaration and whose minting disagree",
                List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
                List.of(
                    new PersonaFixture(
                        "a-card-that-works",
                        "an-owner",
                        PaymentMethodKind.CARD,
                        aCard(),
                        List.of(TOKEN),
                        "the double says it mints a token",
                        () -> Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, "cus_1"),
                        0)))),
        theIntentsItPublishesNoneFor());
  }

  private static DeclaredPersona personaOf(String name, int place, PersonaFixture.Minting minting) {
    return new DeclaredPersona(
        PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
        name,
        "a stored card this double will capture",
        List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
        List.of(instrumentOf(name, place, minting)));
  }

  private static PersonaFixture instrumentOf(
      String name, int place, PersonaFixture.Minting minting) {
    return new PersonaFixture(
        name,
        "an-owner",
        PaymentMethodKind.CARD,
        aCard(),
        List.of(TOKEN),
        "the double mints a token of its own",
        minting,
        place);
  }

  private static FixtureCard aCard() {
    return new FixtureCard("VISA", "1111", 12, 2030, "credit");
  }

  private static Map<PersonaIntent, String> theIntentsItPublishesNoneFor() {
    return Map.of(
        PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
        "this double declines nothing",
        PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
        "this double drops no connection");
  }

  private ProviderPersonas aCatalogueOfTwoInstrumentsSharingOneToken() {
    return new ProviderPersonas(
        ProviderDouble.ID,
        List.of(
            new DeclaredPersona(
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "two-cards",
                "a persona whose two instruments mint the same provider-native token",
                List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
                List.of(
                    new PersonaFixture(
                        "first-of-two",
                        "an-owner",
                        PaymentMethodKind.CARD,
                        aCard(),
                        List.of(TOKEN),
                        "the double mints a token of its own",
                        () -> Map.of(TOKEN, "shared-token"),
                        null),
                    new PersonaFixture(
                        "second-of-two",
                        "an-owner",
                        PaymentMethodKind.CARD,
                        aCard(),
                        List.of(TOKEN),
                        "the double mints a token of its own",
                        () -> Map.of(TOKEN, "shared-token"),
                        null)))),
        theIntentsItPublishesNoneFor());
  }

  private static final class ClaimingScenarioRules implements ScenarioRules {

    private final InMemoryScenarioRules delegate = new InMemoryScenarioRules();
    private final String message;

    ClaimingScenarioRules(String message) {
      this.message = message;
    }

    @Override
    public ScenarioRule add(NewScenarioRule rule) {
      throw new ClaimedMeanwhileException(message);
    }

    @Override
    public List<ScenarioRule> addAll(List<NewScenarioRule> rules) {
      throw new ClaimedMeanwhileException(message);
    }

    @Override
    public Optional<ScenarioRule> find(
        ProviderId provider, TestRunId testRunId, String scenarioId) {
      return delegate.find(provider, testRunId, scenarioId);
    }

    @Override
    public List<ScenarioRule> of(ProviderId provider, TestRunId testRunId) {
      return delegate.of(provider, testRunId);
    }

    @Override
    public boolean remove(ProviderId provider, TestRunId testRunId, String scenarioId) {
      return delegate.remove(provider, testRunId, scenarioId);
    }

    @Override
    public <T> T decidingOneRequestAtATimeAbout(
        ProviderId provider, TestRunId testRunId, List<StoryKey> keys, Supplier<T> decision) {
      return delegate.decidingOneRequestAtATimeAbout(provider, testRunId, keys, decision);
    }

    @Override
    public SequencedMap<String, String> storyStatesOf(
        ProviderId provider, TestRunId testRunId, StoryKey key) {
      return delegate.storyStatesOf(provider, testRunId, key);
    }

    @Override
    public boolean moveStory(
        ProviderId provider,
        TestRunId testRunId,
        StoryKey key,
        String story,
        String from,
        String to) {
      return delegate.moveStory(provider, testRunId, key, story, from, to);
    }

    @Override
    public int clear(ProviderId provider, TestRunId testRunId) {
      return delegate.clear(provider, testRunId);
    }

    @Override
    public int clearAll(ProviderId provider) {
      return delegate.clearAll(provider);
    }
  }
}
