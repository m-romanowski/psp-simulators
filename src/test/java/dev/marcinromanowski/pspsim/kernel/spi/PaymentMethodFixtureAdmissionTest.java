package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork.Ending;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PaymentMethodFixtureAdmissionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId MINE = TestRunId.of("run-a");

  private static final TestRunId SOMEONE_ELSES = TestRunId.of("run-b");

  private static final String TOKEN = ProviderPaymentMethodFields.TOKEN;

  private final InMemoryPaymentMethodFixtures seeded = new InMemoryPaymentMethodFixtures();
  private final InMemoryScenarioRules scenarios = new InMemoryScenarioRules();
  private final RegisteredRuns testRuns = new RegisteredRuns();
  private final ProviderDouble provider = new ProviderDouble();
  private final RecordedUnitsOfWork unitsOfWork = new RecordedUnitsOfWork();
  private final PaymentMethodFixtureAdmission admission =
      new PaymentMethodFixtureAdmission(seeded, scenarios, testRuns, unitsOfWork, DEFAULT_RUN);

  @Test
  void a_harness_seeds_a_card_and_is_handed_back_the_provider_native_id_it_will_drive_it_with() {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Seeded.class,
            seededCard -> {
              PaymentMethodFixture fixture = seededCard.fixture();
              assertThat(fixture.providerIds()).containsExactly(Map.entry(TOKEN, "tok_sim_123"));
              assertThat(fixture.fixtureAlias()).isEqualTo("visa-success");
              assertThat(fixture.type()).isEqualTo(PaymentMethodKind.CARD);
              assertThat(fixture.card()).isNotNull();
              assertThat(fixture.card().last4()).isEqualTo("1111");
              assertThat(fixture.ownerId()).isEqualTo("customer-123");
            });
  }

  @Test
  void the_provider_is_handed_the_seeded_fixture_so_it_can_keep_its_own_row_for_it() {
    admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    assertThat(provider.itSeeded())
        .singleElement()
        .satisfies(
            fixture -> {
              assertThat(fixture.fixtureId()).isNotBlank();
              assertThat(fixture.providerIds()).containsEntry(TOKEN, "tok_sim_123");
            });
  }

  @Test
  void a_seed_whose_alias_another_call_claimed_first_is_answered_as_a_conflict_and_names_no_run() {
    RegisteredRuns theOtherCallsRuns = new RegisteredRuns();
    new PaymentMethodFixtureAdmission(
            seeded, scenarios, theOtherCallsRuns, new RecordedUnitsOfWork(), DEFAULT_RUN)
        .seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    assertThat(verdict).isInstanceOf(PaymentMethodFixtureAdmission.Verdict.Conflicting.class);
    assertThat(testRuns.exists(MINE)).isFalse();
    assertThat(unitsOfWork.endings()).containsExactly(Ending.DISCARDED);
  }

  @Test
  void a_seed_the_provider_refuses_at_the_store_is_refused_and_names_no_run() {
    ProviderDouble refusingAtTheStore =
        new ProviderDouble().refusingToStoreTheSeed("card.last4", "this double stores no card");

    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(refusingAtTheStore, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.property()).isEqualTo("card.last4");
              assertThat(refused.message()).isEqualTo("this double stores no card");
            });
    assertThat(testRuns.exists(MINE)).isFalse();
    assertThat(unitsOfWork.endings()).containsExactly(Ending.THREW);
  }

  @Test
  void a_seed_whose_value_the_database_finds_claimed_meanwhile_is_a_conflict_and_names_no_run() {
    ProviderDouble claimedMeanwhile =
        new ProviderDouble().findingTheSeedClaimedMeanwhile("taken while this seed was written");

    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(claimedMeanwhile, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.message()).isEqualTo("taken while this seed was written"));
    assertThat(testRuns.exists(MINE)).isFalse();
    assertThat(unitsOfWork.endings()).containsExactly(Ending.THREW);
  }

  @Test
  void a_seeded_fixture_and_the_run_it_names_are_kept_by_one_unit_of_work() {
    admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    assertThat(unitsOfWork.endings()).containsExactly(Ending.KEPT);
    assertThat(testRuns.exists(MINE)).isTrue();
  }

  @Test
  void seeding_a_card_registers_the_test_run_so_a_harness_can_find_it_afterwards() {
    admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    assertThat(testRuns.exists(MINE)).isTrue();
  }

  @Test
  void seeding_the_same_fixture_twice_under_one_alias_is_refused_as_a_conflict() {
    admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    PaymentMethodFixtureAdmission.Verdict again =
        admission.seed(
            provider, card(MINE, "visa-success", "tok_sim_999", null), ObjectOrigin.CONTROL_API);

    assertThat(again)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Conflicting.class,
            conflicting -> assertThat(conflicting.message()).contains("alias", "visa-success"));
    assertThat(seeded.of(ProviderDouble.ID, MINE)).hasSize(1);
  }

  @Test
  void two_fixtures_of_one_test_run_cannot_claim_one_provider_native_token() {
    admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    PaymentMethodFixtureAdmission.Verdict again =
        admission.seed(
            provider, card(MINE, "visa-decline", "tok_sim_123", null), ObjectOrigin.CONTROL_API);

    assertThat(again)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Conflicting.class,
            conflicting ->
                assertThat(conflicting.message()).contains("providerIds.token", "tok_sim_123"));
  }

  @Test
  void a_fixture_seeded_in_one_test_run_is_invisible_to_another() {
    admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    assertThat(seeded.of(ProviderDouble.ID, MINE)).hasSize(1);
    assertThat(seeded.of(ProviderDouble.ID, SOMEONE_ELSES)).isEmpty();
  }

  @Test
  void one_alias_is_free_again_in_a_second_test_run_because_the_runs_are_isolated() {
    admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API);

    assertThat(admission.seed(provider, aVisaCard(SOMEONE_ELSES), ObjectOrigin.CONTROL_API))
        .isInstanceOf(PaymentMethodFixtureAdmission.Verdict.Seeded.class);
  }

  @Test
  void a_fixture_naming_a_scenario_this_test_run_has_not_written_is_refused() {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(
            provider,
            card(MINE, "visa-success", "tok_sim_123", "scn_nothing"),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.property())
                  .isEqualTo(PaymentMethodFixtureAdmission.DEFAULT_SCENARIO);
              assertThat(refused.message()).contains("match.paymentMethodId");
            });
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void a_fixture_naming_a_rule_this_test_run_holds_is_seeded_and_reports_the_rule_it_names() {
    ScenarioRule declining = aRuleNarrowedOnTheToken();

    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(
            provider,
            card(MINE, "visa-decline", "tok_sim_123", declining.scenarioId()),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Seeded.class,
            seededCard ->
                assertThat(seededCard.fixture().defaultScenario())
                    .isEqualTo(declining.scenarioId()));
  }

  @Test
  void a_rule_written_for_another_test_run_is_not_a_scenario_this_fixture_may_name() {
    ScenarioRule elsewhere =
        scenarios.add(
            new NewScenarioRule(
                ProviderDouble.ID,
                SOMEONE_ELSES,
                0,
                ScenarioMatch.anyRequest(),
                new ScenarioAction.FixedSuccess(),
                null));

    assertThat(
            admission.seed(
                provider,
                card(MINE, "visa", "tok_sim_1", elsewhere.scenarioId()),
                ObjectOrigin.CONTROL_API))
        .isInstanceOf(PaymentMethodFixtureAdmission.Verdict.Refused.class);
  }

  @Test
  void a_provider_native_id_this_provider_reads_nothing_of_is_refused_in_its_own_words() {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(
            provider,
            new DeclaredPaymentMethodFixture(
                MINE.value(),
                "merchant-1",
                "customer-123",
                "card",
                "visa-success",
                aVisaDescriptor(),
                Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, "cus_sim_123", TOKEN, "tok_1"),
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.property()).isEqualTo("providerIds.customerId");
              assertThat(refused.message()).isEqualTo("this provider names no customer anywhere");
            });
  }

  @Test
  void a_key_of_no_published_schema_is_refused_by_naming_the_keys_the_schema_publishes() {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(
            provider,
            new DeclaredPaymentMethodFixture(
                MINE.value(),
                "merchant-1",
                "customer-123",
                "card",
                "visa-success",
                aVisaDescriptor(),
                Map.of("pan", "4111111111111111"),
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.message())
                    .contains("no key of the published payment-method fixture schema"));
  }

  @Test
  void a_fixture_that_leaves_out_the_id_this_provider_drives_it_by_is_refused_by_name() {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(provider, card(MINE, "visa-success", null, null), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.property()).isEqualTo("providerIds.token");
              assertThat(refused.message()).contains("could never be used");
            });
  }

  @Test
  void a_provider_that_cannot_hold_this_fixture_refuses_it_in_its_own_words() {
    ProviderDouble refusing =
        new ProviderDouble().refusingTheSeed("merchantId", "this account was never configured");

    assertThat(admission.seed(refusing, aVisaCard(MINE), ObjectOrigin.CONTROL_API))
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Refused.class,
            refused -> {
              assertThat(refused.property()).isEqualTo("merchantId");
              assertThat(refused.message()).isEqualTo("this account was never configured");
            });
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void a_card_fixture_that_describes_no_card_is_rejected_as_malformed() {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(
            provider,
            new DeclaredPaymentMethodFixture(
                MINE.value(),
                "merchant-1",
                "customer-123",
                "card",
                "visa-success",
                null,
                Map.of(TOKEN, "tok_1"),
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict).isInstanceOf(PaymentMethodFixtureAdmission.Verdict.Rejected.class);
  }

  @Test
  void a_wallet_fixture_that_describes_a_card_is_rejected_as_malformed() {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(
            provider,
            new DeclaredPaymentMethodFixture(
                MINE.value(),
                "merchant-1",
                "customer-123",
                "wallet",
                "a-wallet",
                aVisaDescriptor(),
                Map.of(TOKEN, "tok_1"),
                null),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict).isInstanceOf(PaymentMethodFixtureAdmission.Verdict.Rejected.class);
  }

  @Test
  void a_fixture_with_no_alias_is_rejected_because_nothing_would_name_it_afterwards() {
    PaymentMethodFixtureAdmission.Verdict verdict =
        admission.seed(provider, card(MINE, null, "tok_sim_123", null), ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Verdict.Rejected.class,
            rejected -> assertThat(rejected.message()).contains("alias"));
  }

  @Test
  void a_seed_naming_no_test_run_lands_in_the_default_namespace() {
    admission.seed(provider, aVisaCard(null), ObjectOrigin.CONTROL_API);

    assertThat(seeded.of(ProviderDouble.ID, DEFAULT_RUN)).hasSize(1);
  }

  @Test
  void a_tester_removes_a_seeded_method_and_the_provider_drops_its_own_row_for_it() {
    PaymentMethodFixture fixture =
        theFixtureSeededBy(admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API));

    PaymentMethodFixtureAdmission.Removal removal =
        admission.forget(provider, MINE.value(), fixture.fixtureId());

    assertThat(removal)
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Removal.Removed.class,
            removed -> assertThat(removed.fixture().fixtureId()).isEqualTo(fixture.fixtureId()));
    assertThat(provider.theFixturesItForgot()).containsExactly(fixture.fixtureId());
    assertThat(seeded.of(ProviderDouble.ID, MINE)).isEmpty();
  }

  @Test
  void removing_a_fixture_of_another_test_run_finds_nothing_and_leaves_it_where_it_is() {
    PaymentMethodFixture fixture =
        theFixtureSeededBy(admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API));

    assertThat(admission.forget(provider, SOMEONE_ELSES.value(), fixture.fixtureId()))
        .isInstanceOf(PaymentMethodFixtureAdmission.Removal.NotSeeded.class);
    assertThat(seeded.of(ProviderDouble.ID, MINE)).hasSize(1);
    assertThat(provider.theFixturesItForgot()).isEmpty();
  }

  @Test
  void a_fixture_another_call_removed_between_the_read_and_the_write_is_not_there_either() {
    InMemoryPaymentMethodFixtures racing = new InMemoryPaymentMethodFixtures();
    PaymentMethodFixtureAdmission racy =
        new PaymentMethodFixtureAdmission(
            racing, scenarios, testRuns, new RecordedUnitsOfWork(), DEFAULT_RUN);
    PaymentMethodFixture fixture =
        theFixtureSeededBy(racy.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API));
    racing.asIfAnotherCallHadJustRemovedIt();

    assertThat(racy.forget(provider, MINE.value(), fixture.fixtureId()))
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Removal.NotSeeded.class,
            absent ->
                assertThat(absent.message())
                    .isEqualTo(PaymentMethodFixtureAdmission.A_FIXTURE_IS_REMOVED_BY_ITS_IDENTITY));
  }

  @Test
  void removing_a_fixture_no_run_ever_seeded_is_answered_as_a_thing_that_is_not_there() {
    assertThat(admission.forget(provider, MINE.value(), "fx_nothing"))
        .isInstanceOfSatisfying(
            PaymentMethodFixtureAdmission.Removal.NotSeeded.class,
            absent ->
                assertThat(absent.message())
                    .isEqualTo(PaymentMethodFixtureAdmission.A_FIXTURE_IS_REMOVED_BY_ITS_IDENTITY));
  }

  @Test
  void removing_a_fixture_with_no_identity_is_rejected_rather_than_removing_something_else() {
    assertThat(admission.forget(provider, MINE.value(), "  "))
        .isInstanceOf(PaymentMethodFixtureAdmission.Removal.Rejected.class);
  }

  @Test
  void a_removal_naming_an_unusable_test_run_is_rejected_by_the_rule_that_names_a_run() {
    assertThat(admission.forget(provider, "UNKNOWN", "fx_1"))
        .isInstanceOf(PaymentMethodFixtureAdmission.Removal.Rejected.class);
  }

  @Test
  void a_seed_naming_an_unusable_test_run_is_rejected_before_anything_is_written() {
    assertThat(admission.seed(provider, aVisaCard(TestRunId.UNKNOWN), ObjectOrigin.CONTROL_API))
        .isInstanceOf(PaymentMethodFixtureAdmission.Verdict.Rejected.class);
    assertThat(provider.itSeeded()).isEmpty();
  }

  private ScenarioRule aRuleNarrowedOnTheToken() {
    return scenarios.add(
        new NewScenarioRule(
            ProviderDouble.ID,
            MINE,
            0,
            new ScenarioMatch(
                null, null, null, null, "tok_sim_123", null, null, null, null, Map.of()),
            new ScenarioAction.TerminalFailure("813"),
            null));
  }

  private static PaymentMethodFixture theFixtureSeededBy(
      PaymentMethodFixtureAdmission.Verdict verdict) {
    return ((PaymentMethodFixtureAdmission.Verdict.Seeded) verdict).fixture();
  }

  private static DeclaredPaymentMethodFixture aVisaCard(@Nullable TestRunId run) {
    return card(run, "visa-success", "tok_sim_123", null);
  }

  private static DeclaredPaymentMethodFixture card(
      @Nullable TestRunId run,
      @Nullable String alias,
      @Nullable String token,
      @Nullable String defaultScenario) {
    return new DeclaredPaymentMethodFixture(
        run == null ? null : run.value(),
        "merchant-1",
        "customer-123",
        "card",
        alias,
        aVisaDescriptor(),
        token == null ? Map.of() : Map.of(TOKEN, token),
        defaultScenario);
  }

  private static DeclaredCard aVisaDescriptor() {
    return new DeclaredCard("visa", "1111", 12, 2030, "credit");
  }

  private static final class RegisteredRuns implements TestRuns {

    private final Set<TestRunId> registered = new LinkedHashSet<>();

    @Override
    public void register(TestRunId testRunId) {
      registered.add(testRunId);
    }

    @Override
    public int forget(TestRunId testRunId) {
      return registered.remove(testRunId) ? 1 : 0;
    }

    @Override
    public boolean exists(TestRunId testRunId) {
      return registered.contains(testRunId);
    }

    @Override
    public List<TestRunId> all() {
      return List.copyOf(registered);
    }
  }

  @Test
  void every_arm_of_a_fixture_verdict_says_whether_it_refused_and_under_which_status() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_SEED);

    assertThat(
            new PaymentMethodFixtureAdmission.Verdict.Rejected("unreadable").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(subject));
    assertThat(new PaymentMethodFixtureAdmission.Verdict.Refused(TOKEN, "no").theRefusalItReports())
        .isEqualTo(ActionVerdict.refused(subject));
    assertThat(
            new PaymentMethodFixtureAdmission.Verdict.Conflicting(TOKEN, "taken")
                .theRefusalItReports())
        .isEqualTo(ActionVerdict.conflicting(subject));
    assertThat(
            admission
                .seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API)
                .theRefusalItReports())
        .isNull();
  }

  @Test
  void every_arm_of_a_fixture_removal_says_whether_it_refused_and_under_which_status() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            PaymentMethodFixtureAdmission.THE_FIXTURE_IT_ASKED_THIS_PROVIDER_TO_FORGET);
    PaymentMethodFixture fixture =
        theFixtureSeededBy(admission.seed(provider, aVisaCard(MINE), ObjectOrigin.CONTROL_API));

    assertThat(
            new PaymentMethodFixtureAdmission.Removal.Rejected("unreadable").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(subject));
    assertThat(new PaymentMethodFixtureAdmission.Removal.NotSeeded("absent").theRefusalItReports())
        .isEqualTo(ActionVerdict.notFound(subject));
    assertThat(new PaymentMethodFixtureAdmission.Removal.Removed(fixture).theRefusalItReports())
        .isNull();
  }
}
