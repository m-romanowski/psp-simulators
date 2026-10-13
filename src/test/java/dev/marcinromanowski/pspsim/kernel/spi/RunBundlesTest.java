package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableAccount;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableRouteAlias;
import dev.marcinromanowski.pspsim.kernel.bundle.PortableWebhookTarget;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.fixture.Seeding;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.PortableScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RunBundlesTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId RUN = TestRunId.of("run-a");

  private static final Instant PINNED = Instant.parse("2026-09-24T12:00:00Z");

  private static final String A_SECRET_A_CALLER_SUPPLIED = "sk-live-must-not-travel";

  private final BundleStores.Accounts accounts = new BundleStores.Accounts();
  private final BoundRouteAliases aliases = new BoundRouteAliases();
  private final InMemoryTestRunSettings settings = new InMemoryTestRunSettings();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final BundleStores.Pins pins = new BundleStores.Pins();

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
                  "a value a test does not read"));

  @Test
  void a_run_holding_nothing_exports_a_bundle_naming_the_run_the_provider_and_this_build() {
    RunBundle bundle = exported();

    assertThat(bundle.testRunId()).isEqualTo("run-a");
    assertThat(bundle.provider()).isEqualTo(ProviderDouble.ID.value());
    assertThat(bundle.compatibility())
        .isEqualTo(
            BundleVocabulary.of(
                provider, new DeclaredProviderSettings(List.of()).of(provider.id())));
    assertThat(bundle.carriedAccounts()).isEmpty();
    assertThat(bundle.carriedFixtures()).isEmpty();
    assertThat(bundle.scenarios()).isNull();
  }

  @Test
  void a_run_naming_no_test_run_at_all_exports_the_run_this_simulator_uses_when_none_is_named() {
    assertThat(theBundleOf(null).testRunId()).isEqualTo("default");
  }

  @Test
  void a_test_run_id_no_namespace_can_hold_is_rejected_rather_than_exported() {
    assertThat(bundles().of(provider, "a run with spaces"))
        .isInstanceOf(RunBundles.Export.Rejected.class);
  }

  @Test
  void an_unpinned_run_exports_a_clock_that_names_no_instant_and_a_pinned_run_names_its_own() {
    assertThat(exported().clock()).isEqualTo(new DeclaredClock(null, null, null));

    pins.pin(RUN, PINNED);

    assertThat(exported().clock()).isEqualTo(new DeclaredClock("2026-09-24T12:00:00Z", null, null));
  }

  @Test
  void
      an_account_exports_the_fields_this_provider_declares_plainly_and_names_the_secrets_it_left() {
    configureAnAccount();

    RunBundle bundle = exported();

    assertThat(bundle.carriedAccounts())
        .singleElement()
        .satisfies(
            account -> {
              assertThat(account.accountId()).isEqualTo("merchant-1");
              assertThat(account.carriedConfiguration())
                  .containsExactly(Map.entry("capture.window", "P3D"));
              assertThat(account.theSecretsItNames()).containsExactly("secretKey");
            });
  }

  @Test
  void a_secret_a_provider_disclosed_without_redacting_it_is_still_left_out_of_the_bundle() {
    accounts.merge(provider.id(), RUN, List.of("merchant-1"), List.of(), plan());
    provider.disclosing(
        "merchant-1",
        Map.of("secretKey", List.of(A_SECRET_A_CALLER_SUPPLIED), "capture.window", List.of("P3D")));

    RunBundle bundle = exported();

    assertThat(bundle.toString()).doesNotContain(A_SECRET_A_CALLER_SUPPLIED);
    assertThat(bundle.carriedAccounts().getFirst().carriedConfiguration())
        .containsExactly(Map.entry("capture.window", "P3D"));
    assertThat(bundle.carriedAccounts().getFirst().theSecretsItNames())
        .containsExactly("secretKey");
  }

  @Test
  void the_marker_a_disclosure_puts_where_a_secret_stood_is_not_carried_into_the_bundle_either() {
    configureAnAccount();

    RunBundle bundle = exported();

    assertThat(bundle.carriedAccounts().getFirst().carriedConfiguration().values())
        .doesNotContain(Redaction.REDACTED);
    assertThat(bundle.toString()).doesNotContain(Redaction.REDACTED);
  }

  @Test
  void
      an_exported_account_carries_its_configuration_in_the_order_it_was_disclosed_and_twelve_cannot_land_that_way_by_luck() {
    ConfigurableField[] asked = new ConfigurableField[12];
    LinkedHashMap<String, List<String>> disclosed = new LinkedHashMap<>();
    for (int at = 1; at <= 12; at++) {
      String field = "field.number." + at;
      asked[at - 1] =
          new ConfigurableField(
              field,
              "Field " + at,
              "a sentence a test does not read",
              false,
              false,
              List.of(),
              "a value a test does not read");
      disclosed.put(field, List.of("value-" + at));
    }

    PortableAccount carried =
        RunBundles.theAccountItCanCarry(
            new ProviderDouble().asking(asked).accountFields(),
            new DisclosedAccount("merchant-1", disclosed));

    assertThat(carried.carriedConfiguration().keySet())
        .as(
            "a downloaded bundle is read, diffed and handed to someone else, and the import answer"
                + " names one refusal per field in this order, so the fields come out in the order"
                + " the account disclosed them; twelve names land in one declared order by chance"
                + " once in 479001600")
        .containsExactlyElementsOf(disclosed.keySet());
    assertThat(carried.carriedConfiguration())
        .as(
            "a map with no order of its own would answer the sequence above from the keys' hashes"
                + " and a per-JVM salt, and the type says which of the two this is in every run")
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void a_key_this_provider_declares_nothing_of_is_named_as_left_out_rather_than_carried() {
    PortableAccount carried =
        RunBundles.theAccountItCanCarry(
            provider.accountFields(),
            new DisclosedAccount(
                "merchant-1",
                Map.of("smuggled", List.of("anything"), "capture.window", List.of("P3D"))));

    assertThat(carried.carriedConfiguration()).containsOnlyKeys("capture.window");
    assertThat(carried.theSecretsItNames()).containsExactly("smuggled");
  }

  @Test
  void a_plainly_declared_field_disclosed_twice_over_is_named_as_left_out_rather_than_halved() {
    PortableAccount carried =
        RunBundles.theAccountItCanCarry(
            provider.accountFields(),
            new DisclosedAccount("merchant-1", Map.of("capture.window", List.of("P3D", "P7D"))));

    assertThat(carried.carriedConfiguration()).isEmpty();
    assertThat(carried.theSecretsItNames()).containsExactly("capture.window");
  }

  @Test
  void a_plainly_declared_field_disclosed_with_no_value_is_neither_carried_nor_named() {
    PortableAccount carried =
        RunBundles.theAccountItCanCarry(
            provider.accountFields(),
            new DisclosedAccount("merchant-1", Map.of("capture.window", List.of())));

    assertThat(carried.carriedConfiguration()).isEmpty();
    assertThat(carried.theSecretsItNames()).isEmpty();
  }

  @Test
  void an_account_this_provider_discloses_nothing_of_exports_its_label_and_no_field() {
    accounts.merge(provider.id(), RUN, List.of("merchant-2"), List.of(), plan());

    assertThat(exported().carriedAccounts())
        .containsExactly(new PortableAccount("merchant-2", Map.of(), List.of()));
  }

  @Test
  void the_webhook_targets_the_route_aliases_and_the_settings_of_a_run_travel_with_it() {
    accounts.merge(
        provider.id(),
        RUN,
        List.of("merchant-1"),
        List.of(new WebhookTarget("merchant-1", "https://merchant.example/hook")),
        plan());
    aliases.merge(
        provider.id(),
        RUN,
        List.of(
            new RouteAlias(
                provider.id(),
                RUN,
                new RouteAliasKey("api.example", "/v1"),
                "https://api.example/v1",
                "/v1")));
    settings.merge(provider.id(), RUN, Map.of(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1M"));

    RunBundle bundle = exported();

    assertThat(bundle.carriedWebhooks())
        .containsExactly(new PortableWebhookTarget("merchant-1", "https://merchant.example/hook"));
    assertThat(bundle.carriedRouteAliases())
        .containsExactly(new PortableRouteAlias("https://api.example/v1", "/v1"));
    assertThat(bundle.carriedSettings())
        .containsExactly(Map.entry("webhook.retryPlan", "PT0S,PT1M"));
  }

  @Test
  void a_fixture_travels_naming_the_place_of_the_rule_it_triggers_rather_than_that_rule_s_id() {
    ScenarioRule first = rules.add(aRule());
    ScenarioRule second = rules.add(aRule());
    seedAFixture("visa-success", second.scenarioId());

    RunBundle bundle = exported();

    assertThat(bundle.carriedRules()).hasSize(2);
    assertThat(bundle.carriedFixtures())
        .singleElement()
        .satisfies(
            fixture -> {
              assertThat(fixture.alias()).isEqualTo("visa-success");
              assertThat(bundle.carriedRules().get(fixture.triggersRuleAt()))
                  .isEqualTo(PortableScenario.of(second.asWrittenAgain()));
            });
    assertThat(RunBundles.thePlaceOf(first.scenarioId(), rules.of(provider.id(), RUN)))
        .isEqualTo(rules.of(provider.id(), RUN).indexOf(first));
  }

  @Test
  void a_fixture_naming_a_rule_this_run_no_longer_holds_refuses_the_export_by_its_own_property() {
    seedAFixture("visa-success", "scn_thatWasDeleted");

    assertThat(bundles().of(provider, RUN.value()))
        .isInstanceOfSatisfying(
            RunBundles.Export.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::property)
                    .containsExactly("fixtures[0].defaultScenario"));
  }

  @Test
  void
      writing_the_deleted_rule_again_leaves_the_export_refused_which_is_why_the_refusal_names_one_repair() {
    seedAFixture("visa-success", "scn_thatWasDeleted");
    rules.add(aRule());

    assertThat(bundles().of(provider, RUN.value()))
        .isInstanceOfSatisfying(
            RunBundles.Export.Refused.class,
            refused ->
                assertThat(refused.refusals())
                    .extracting(BundleRefusal::reason)
                    .containsExactly(RunBundles.A_RULE_THIS_RUN_NO_LONGER_HOLDS));
  }

  @Test
  void forgetting_the_fixture_is_the_repair_the_refusal_names_and_it_leaves_the_run_exportable() {
    configureAnAccount();
    seedAFixture("visa-success", "scn_thatWasDeleted");
    PaymentMethodFixture doomed = fixtures.of(provider.id(), RUN).getFirst();

    assertThat(
            fixtures.remove(provider.id(), RUN, doomed.fixtureId(), provider.forgetFixture(doomed)))
        .isTrue();

    assertThat(bundles().of(provider, RUN.value())).isInstanceOf(RunBundles.Export.Exported.class);
  }

  @Test
  void a_rule_narrowing_on_a_request_header_refuses_the_export_by_the_header_it_named() {
    rules.add(
        new NewScenarioRule(
            provider.id(),
            RUN,
            0,
            new ScenarioMatch(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("Authorization", "Bearer " + A_SECRET_A_CALLER_SUPPLIED)),
            new ScenarioAction.FixedSuccess(),
            null));

    assertThat(bundles().of(provider, RUN.value()))
        .isInstanceOfSatisfying(
            RunBundles.Export.Refused.class,
            refused -> {
              assertThat(refused.refusals())
                  .extracting(BundleRefusal::property)
                  .containsExactly("scenarios.match.header.Authorization");
              assertThat(refused.refusals().getFirst().reason())
                  .doesNotContain(A_SECRET_A_CALLER_SUPPLIED);
            });
  }

  @Test
  void a_rule_this_run_holds_travels_as_what_it_is_and_never_as_the_id_it_was_given() {
    ScenarioRule held = rules.add(aRule());

    RunBundle bundle = exported();

    assertThat(bundle.scenarios()).isNotNull();
    assertThat(bundle.scenarios().testRunId()).isEqualTo("run-a");
    assertThat(bundle.scenarios().preset()).isNull();
    assertThat(bundle.carriedRules()).hasSize(1);
    assertThat(bundle.toString()).doesNotContain(held.scenarioId());
  }

  @Test
  void the_place_of_a_rule_no_run_holds_is_no_place_at_all() {
    assertThat(RunBundles.thePlaceOf(null, List.of())).isNull();
    assertThat(RunBundles.thePlaceOf("scn_absent", List.of())).isNull();
  }

  private RunBundle exported() {
    return theBundleOf(RUN.value());
  }

  private RunBundle theBundleOf(String declaredTestRunId) {
    RunBundles.Export export = bundles().of(provider, declaredTestRunId);
    assertThat(export).isInstanceOf(RunBundles.Export.Exported.class);
    return ((RunBundles.Export.Exported) export).bundle();
  }

  private RunBundles bundles() {
    return new RunBundles(
        accounts,
        aliases,
        settings,
        new DeclaredProviderSettings(List.of()),
        fixtures,
        rules,
        pins,
        pins,
        DEFAULT_RUN);
  }

  private void configureAnAccount() {
    accounts.merge(provider.id(), RUN, List.of("merchant-1"), List.of(), plan());
    provider.disclosing(
        "merchant-1",
        Map.of("secretKey", List.of(Redaction.REDACTED), "capture.window", List.of("P3D")));
  }

  private void seedAFixture(String alias, String defaultScenario) {
    Seeding seeding =
        fixtures.seed(
            new NewPaymentMethodFixture(
                provider.id(),
                RUN,
                "merchant-1",
                "owner-1",
                PaymentMethodKind.CARD,
                alias,
                new FixtureCard("visa", "4242", 12, 2031, "credit"),
                Map.of("token", "tok_1"),
                defaultScenario,
                ObjectOrigin.CONTROL_API),
            new PaymentMethodFixturePlan(held -> {}),
            Set.of());
    assertThat(seeding).isInstanceOf(Seeding.Seeded.class);
  }

  private static ProviderConfigurationPlan plan() {
    return new ProviderConfigurationPlan(Set.of(), List.of(), () -> {});
  }

  private NewScenarioRule aRule() {
    return new NewScenarioRule(
        provider.id(),
        RUN,
        0,
        ScenarioMatch.anyRequest(),
        new ScenarioAction.DeliveryDelayed(Duration.ofMinutes(15)),
        null);
  }
}
