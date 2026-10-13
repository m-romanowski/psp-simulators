package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.BoundRouteAliases;
import dev.marcinromanowski.pspsim.MergedIdentities;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork;
import dev.marcinromanowski.pspsim.kernel.transaction.RecordedUnitsOfWork.Ending;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigurationAdmissionTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId RUN = TestRunId.of("named-run");

  private static final Instant PINNED = Instant.parse("2026-09-12T10:00:00Z");

  private static final String PAYMENTS = "https://api.provider.example/v2/merchant-1/payments";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  private final Accounts accounts = new Accounts();
  private final BoundRouteAliases aliases = new BoundRouteAliases();
  private final Pins pins = new Pins();
  private final RegisteredRuns runs = new RegisteredRuns();
  private final RecordedUnitsOfWork unitsOfWork = new RecordedUnitsOfWork();

  @Test
  void a_configuration_names_the_run_it_configures_and_the_clock_it_pins() {
    ProviderDouble provider = new ProviderDouble().reading("userId");

    ConfigurationAdmission.Verdict verdict =
        admission()
            .apply(
                provider,
                new DeclaredConfiguration(
                    RUN.value(),
                    new DeclaredClock(PINNED.toString(), null, null),
                    List.of(new DeclaredAccount("account-1", Map.of("userId", "u"))),
                    null,
                    null,
                    null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Applied.class,
            applied -> {
              assertThat(applied.testRunId()).isEqualTo(RUN);
              assertThat(applied.accountIds()).containsExactly("account-1");
              assertThat(applied.now()).isEqualTo(PINNED);
            });
    assertThat(runs.registered()).containsExactly(RUN);
    assertThat(pins.of(RUN)).contains(PINNED);
  }

  @Test
  void a_configuration_that_names_no_test_run_configures_the_one_the_simulator_defaults_to() {
    ConfigurationAdmission.Verdict verdict =
        admission()
            .apply(
                new ProviderDouble().reading("userId"),
                new DeclaredConfiguration(null, null, null, null, null, null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Applied.class,
            applied -> assertThat(applied.testRunId()).isEqualTo(DEFAULT_RUN));
  }

  @Test
  void a_configuration_the_provider_refuses_names_no_test_run_and_pins_no_clock() {
    ProviderDouble provider = new ProviderDouble().reading("userId");

    ConfigurationAdmission.Verdict verdict =
        admission()
            .apply(
                provider,
                new DeclaredConfiguration(
                    RUN.value(),
                    new DeclaredClock(PINNED.toString(), null, null),
                    List.of(new DeclaredAccount("account-1", Map.of("userId", "u"))),
                    null,
                    null,
                    Map.of("webhook.retryPlan", "PT1S")));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Refused.class,
            refused ->
                assertThat(refused.refusal().message())
                    .startsWith("settings.webhook.retryPlan is declared by no setting"));
    assertThat(runs.registered()).isEmpty();
    assertThat(pins.of(RUN)).isEmpty();
    assertThat(provider.itStored()).isEmpty();
    assertThat(accounts.identitiesOf(ProviderDouble.ID, RUN)).isEmpty();
  }

  @Test
  void a_configuration_the_provider_refuses_leaves_a_clock_pinned_before_it_where_it_was() {
    Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
    pins.pin(RUN, earlier);
    ProviderDouble provider = new ProviderDouble().reading("userId");

    ConfigurationAdmission.Verdict repinning =
        admission()
            .apply(
                provider,
                new DeclaredConfiguration(
                    RUN.value(),
                    new DeclaredClock(PINNED.toString(), null, null),
                    List.of(new DeclaredAccount("account-1", Map.of("nobodyReadsThis", "x"))),
                    null,
                    null,
                    null));
    ConfigurationAdmission.Verdict releasing =
        admission()
            .apply(
                provider,
                new DeclaredConfiguration(
                    RUN.value(),
                    new DeclaredClock(null, null, null),
                    List.of(new DeclaredAccount("account-1", Map.of("nobodyReadsThis", "x"))),
                    null,
                    null,
                    null));

    assertThat(repinning)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Refused.class,
            refused -> assertThat(refused.refusal().field()).isEqualTo("nobodyReadsThis"));
    assertThat(releasing).isInstanceOf(ConfigurationAdmission.Verdict.Refused.class);
    assertThat(pins.of(RUN)).contains(earlier);
    assertThat(runs.registered()).isEmpty();
  }

  @Test
  void a_request_the_grammar_cannot_read_registers_nothing_and_moves_no_clock() {
    ConfigurationAdmission.Verdict verdict =
        admission()
            .apply(
                new ProviderDouble().reading("userId"),
                new DeclaredConfiguration(
                    "not a test run id",
                    new DeclaredClock(PINNED.toString(), null, null),
                    null,
                    null,
                    null,
                    null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Rejected.class,
            rejected -> assertThat(rejected.message()).contains("testRunId"));
    assertThat(runs.registered()).isEmpty();
    assertThat(pins.of(RUN)).isEmpty();
  }

  @Test
  void a_malformed_clock_is_rejected_before_any_account_of_the_same_call_is_stored() {
    ProviderDouble provider = new ProviderDouble().reading("userId");

    ConfigurationAdmission.Verdict verdict =
        admission()
            .apply(
                provider,
                new DeclaredConfiguration(
                    RUN.value(),
                    new DeclaredClock("yesterday", null, null),
                    List.of(new DeclaredAccount("account-1", Map.of("userId", "u"))),
                    null,
                    null,
                    null));

    assertThat(verdict).isInstanceOf(ConfigurationAdmission.Verdict.Rejected.class);
    assertThat(provider.itStored()).isEmpty();
    assertThat(runs.registered()).isEmpty();
  }

  @Test
  void the_clock_a_configuration_pins_is_answered_as_the_run_reads_it_and_not_as_the_host_does() {
    ConfigurationAdmission.Verdict verdict =
        admission()
            .apply(
                new ProviderDouble().reading("userId"),
                new DeclaredConfiguration(
                    RUN.value(), new DeclaredClock(null, null, null), null, null, null, null));

    assertThat(pins.of(RUN)).isEmpty();
    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Applied.class,
            applied -> assertThat(applied.now()).isEqualTo(Instant.EPOCH));
  }

  @Test
  void a_configuration_that_names_a_callback_target_counts_the_targets_it_carried() {
    ConfigurationAdmission.Verdict verdict =
        admission()
            .apply(
                new ProviderDouble().reading("userId"),
                new DeclaredConfiguration(
                    RUN.value(),
                    null,
                    List.of(new DeclaredAccount("account-1", Map.of("userId", "u"))),
                    List.of(new DeclaredWebhookTarget("account-1", "https://merchant.test/hook")),
                    null,
                    null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Applied.class,
            applied -> assertThat(applied.webhookTargets()).isEqualTo(1));
  }

  @Test
  void a_configuration_that_binds_a_url_counts_the_urls_it_carried() {
    ConfigurationAdmission.Verdict verdict =
        admission()
            .apply(
                new ProviderDouble().reading("userId"),
                new DeclaredConfiguration(
                    RUN.value(),
                    null,
                    null,
                    null,
                    List.of(
                        new DeclaredRouteAlias(
                            "https://api.provider.example/v2/merchant-1/payments",
                            "/api/light/server/1.0/createPaymentProcess")),
                    null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Applied.class,
            applied -> {
              assertThat(applied.routeAliases()).isEqualTo(1);
              assertThat(applied.webhookTargets()).isZero();
            });
  }

  @Test
  void a_configuration_that_binds_no_url_counts_none() {
    ConfigurationAdmission.Verdict verdict =
        admission()
            .apply(
                new ProviderDouble().reading("userId"),
                new DeclaredConfiguration(RUN.value(), null, null, null, null, null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Applied.class,
            applied -> assertThat(applied.routeAliases()).isZero());
  }

  @Test
  void a_url_a_harness_releases_is_free_for_the_next_test_run_to_bind() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission().apply(provider, binding(PAYMENTS));

    ConfigurationAdmission.Release release =
        admission().releaseRouteAlias(provider, RUN.value(), PAYMENTS);

    assertThat(release)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Release.Released.class,
            released -> {
              assertThat(released.testRunId()).isEqualTo(RUN);
              assertThat(released.from()).isEqualTo(PAYMENTS);
              assertThat(released.routeAliases()).isEmpty();
            });
    assertThat(aliases.of(ProviderDouble.ID, RUN)).isEmpty();
  }

  @Test
  void releasing_one_url_answers_the_urls_the_test_run_still_answers_on() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission().apply(provider, binding(PAYMENTS));
    admission().apply(provider, binding("https://api.provider.example/v2/merchant-2/payments"));

    ConfigurationAdmission.Release release =
        admission().releaseRouteAlias(provider, RUN.value(), PAYMENTS);

    assertThat(release)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Release.Released.class,
            released ->
                assertThat(released.routeAliases())
                    .containsExactly("https://api.provider.example/v2/merchant-2/payments"));
  }

  @Test
  void no_test_run_releases_a_url_another_one_holds() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission().apply(provider, binding(PAYMENTS));

    ConfigurationAdmission.Release release =
        admission().releaseRouteAlias(provider, "another-run", PAYMENTS);

    assertThat(release).isInstanceOf(ConfigurationAdmission.Release.NotBound.class);
    assertThat(aliases.of(ProviderDouble.ID, RUN)).hasSize(1);
  }

  @Test
  void a_url_this_test_run_never_bound_is_not_found_and_nothing_is_released() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission().apply(provider, binding(PAYMENTS));

    assertThat(
            admission()
                .releaseRouteAlias(provider, RUN.value(), "https://api.provider.example/elsewhere"))
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Release.NotBound.class,
            absent ->
                assertThat(absent.message())
                    .isEqualTo(
                        ConfigurationAdmission.A_ROUTE_ALIAS_IS_RELEASED_BY_THE_URL_IT_BINDS));
    assertThat(aliases.of(ProviderDouble.ID, RUN)).hasSize(1);
  }

  @Test
  void a_release_that_names_no_url_is_rejected_rather_than_freeing_every_url_of_the_run() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission().apply(provider, binding(PAYMENTS));

    assertThat(admission().releaseRouteAlias(provider, RUN.value(), null))
        .isInstanceOf(ConfigurationAdmission.Release.Rejected.class);
    assertThat(admission().releaseRouteAlias(provider, RUN.value(), "   "))
        .isInstanceOf(ConfigurationAdmission.Release.Rejected.class);
    assertThat(aliases.of(ProviderDouble.ID, RUN)).hasSize(1);
  }

  @Test
  void a_release_naming_something_that_is_not_a_url_is_rejected_by_the_field_that_named_it() {
    assertThat(admission().releaseRouteAlias(new ProviderDouble(), RUN.value(), "/payments"))
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Release.Rejected.class,
            rejected -> assertThat(rejected.message()).startsWith("from must be an absolute URL"));
  }

  @Test
  void a_release_naming_a_test_run_the_simulator_could_not_store_is_rejected_by_name() {
    assertThat(admission().releaseRouteAlias(new ProviderDouble(), "not a test run id", PAYMENTS))
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Release.Rejected.class,
            rejected -> assertThat(rejected.message()).contains("testRunId"));
  }

  @Test
  void releasing_a_url_registers_no_test_run_of_its_own() {
    assertThat(admission().releaseRouteAlias(new ProviderDouble(), RUN.value(), PAYMENTS))
        .isInstanceOf(ConfigurationAdmission.Release.NotBound.class);
    assertThat(runs.registered()).isEmpty();
  }

  private static DeclaredConfiguration binding(String from) {
    return new DeclaredConfiguration(
        RUN.value(), null, null, null, List.of(new DeclaredRouteAlias(from, CREATE)), null);
  }

  @Test
  void an_account_a_harness_removes_is_gone_from_the_run_and_the_provider_is_told_to_forget_it() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission().apply(provider, configuring("account-1"));
    admission().apply(provider, configuring("account-2"));

    ConfigurationAdmission.Removal removal = admission().forget(provider, RUN.value(), "account-1");

    assertThat(removal)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Removal.Removed.class,
            removed -> {
              assertThat(removed.accountId()).isEqualTo("account-1");
              assertThat(removed.accountIds()).containsExactly("account-2");
            });
    assertThat(provider.itForgot()).containsExactly(RUN.value() + "/account-1");
  }

  @Test
  void an_account_no_configuration_of_this_run_named_is_not_found_and_the_provider_is_not_told() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission().apply(provider, configuring("account-1"));

    ConfigurationAdmission.Removal removal = admission().forget(provider, RUN.value(), "account-9");

    assertThat(removal).isInstanceOf(ConfigurationAdmission.Removal.NotConfigured.class);
    assertThat(provider.itForgot()).isEmpty();
    assertThat(accounts.identitiesOf(ProviderDouble.ID, RUN)).containsExactly("account-1");
  }

  @Test
  void a_removal_that_names_no_account_is_rejected_rather_than_emptying_the_run() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission().apply(provider, configuring("account-1"));

    assertThat(admission().forget(provider, RUN.value(), "   "))
        .isInstanceOf(ConfigurationAdmission.Removal.Rejected.class);
    assertThat(admission().forget(provider, RUN.value(), null))
        .isInstanceOf(ConfigurationAdmission.Removal.Rejected.class);
    assertThat(accounts.identitiesOf(ProviderDouble.ID, RUN)).containsExactly("account-1");
  }

  @Test
  void a_removal_naming_a_test_run_the_simulator_could_not_store_is_rejected_by_name() {
    ConfigurationAdmission.Removal removal =
        admission().forget(new ProviderDouble(), "not a test run id", "account-1");

    assertThat(removal)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Removal.Rejected.class,
            rejected -> assertThat(rejected.message()).contains("testRunId"));
  }

  @Test
  void removing_the_last_account_of_a_run_leaves_the_run_registered() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission().apply(provider, configuring("account-1"));

    admission().forget(provider, RUN.value(), "account-1");

    assertThat(accounts.identitiesOf(ProviderDouble.ID, RUN)).isEmpty();
    assertThat(runs.exists(RUN)).isTrue();
  }

  @Test
  void removing_an_account_registers_no_test_run_of_its_own() {
    ConfigurationAdmission.Removal removal =
        admission().forget(new ProviderDouble(), RUN.value(), "account-1");

    assertThat(removal).isInstanceOf(ConfigurationAdmission.Removal.NotConfigured.class);
    assertThat(runs.registered()).isEmpty();
  }

  @Test
  void an_account_configured_under_a_padded_identity_is_removed_by_the_identity_a_caller_types() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    ConfigurationAdmission.Verdict verdict = admission().apply(provider, configuring("account-1 "));

    ConfigurationAdmission.Removal removal = admission().forget(provider, RUN.value(), "account-1");

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Applied.class,
            applied -> assertThat(applied.accountIds()).containsExactly("account-1"));
    assertThat(removal)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Removal.Removed.class,
            removed -> assertThat(removed.accountIds()).isEmpty());
    assertThat(provider.itForgot()).containsExactly(RUN.value() + "/account-1");
    assertThat(accounts.identitiesOf(ProviderDouble.ID, RUN)).isEmpty();
  }

  @Test
  void a_callback_target_padded_the_same_way_as_its_account_goes_when_that_account_goes() {
    ProviderDouble provider = new ProviderDouble().reading("userId");
    admission()
        .apply(
            provider,
            new DeclaredConfiguration(
                RUN.value(),
                null,
                List.of(new DeclaredAccount("account-1", Map.of("userId", "u"))),
                List.of(new DeclaredWebhookTarget("account-1 ", "https://merchant.example/hook")),
                null,
                null));

    assertThat(accounts.webhookTargetsOf(ProviderDouble.ID, RUN))
        .containsExactly(new WebhookTarget("account-1", "https://merchant.example/hook"));

    admission().forget(provider, RUN.value(), "account-1");

    assertThat(accounts.webhookTargetsOf(ProviderDouble.ID, RUN)).isEmpty();
  }

  @Test
  void a_configuration_writes_its_clock_run_and_accounts_in_one_unit_of_work_it_keeps() {
    admission().apply(new ProviderDouble().reading("userId"), configuring("account-1"));

    assertThat(unitsOfWork.endings()).containsExactly(Ending.KEPT);
    assertThat(runs.registered()).containsExactly(RUN);
  }

  @Test
  void a_route_alias_the_database_finds_claimed_meanwhile_is_answered_as_a_conflict() {
    ConfigurationAdmission.Verdict verdict =
        admissionBinding(new ClaimedAtTheStore())
            .apply(
                new ProviderDouble().reading("userId"),
                new DeclaredConfiguration(
                    RUN.value(),
                    new DeclaredClock(PINNED.toString(), null, null),
                    List.of(new DeclaredAccount("account-1", Map.of("userId", "u"))),
                    null,
                    List.of(new DeclaredRouteAlias(PAYMENTS, CREATE)),
                    null));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ConfigurationAdmission.Verdict.Conflicting.class,
            conflicting -> {
              assertThat(conflicting.refusal().message())
                  .isEqualTo("routeAliases[0].from names a URL another run bound meanwhile");
              assertThat(conflicting.theRefusalItReports().httpStatus()).isEqualTo(409);
            });
    assertThat(unitsOfWork.endings()).containsExactly(Ending.THREW);
  }

  private static DeclaredConfiguration configuring(String accountId) {
    return new DeclaredConfiguration(
        RUN.value(),
        null,
        List.of(new DeclaredAccount(accountId, Map.of("userId", "u-" + accountId))),
        null,
        null,
        null);
  }

  private ConfigurationAdmission admission() {
    return admissionBinding(aliases);
  }

  private ConfigurationAdmission admissionBinding(RouteAliases routeAliases) {
    return new ConfigurationAdmission(
        new ProviderConfigurations(
            accounts,
            routeAliases,
            new DeclaredProviderSettings(List.of()),
            new InMemoryTestRunSettings()),
        pins,
        pins,
        runs,
        unitsOfWork,
        DEFAULT_RUN);
  }

  private static final class ClaimedAtTheStore implements RouteAliases {

    @Override
    public void merge(ProviderId provider, TestRunId testRunId, List<RouteAlias> aliases) {
      throw new ClaimedMeanwhileException(
          "routeAliases[0].from names a URL another run bound meanwhile");
    }

    @Override
    public boolean release(ProviderId provider, TestRunId testRunId, RouteAliasKey key) {
      return false;
    }

    @Override
    public List<RouteAlias> of(ProviderId provider, TestRunId testRunId) {
      return List.of();
    }

    @Override
    public Optional<RouteAlias> boundTo(RouteAliasKey key) {
      return Optional.empty();
    }
  }

  private static final class Pins implements FixedTimes, SimulatorClock {

    private final Map<TestRunId, Instant> pinned = new LinkedHashMap<>();

    @Override
    public Optional<Instant> of(TestRunId testRunId) {
      return Optional.ofNullable(pinned.get(testRunId));
    }

    @Override
    public void pin(TestRunId testRunId, Instant fixedNow) {
      pinned.put(testRunId, fixedNow);
    }

    @Override
    public void release(TestRunId testRunId) {
      pinned.remove(testRunId);
    }

    @Override
    public Instant now(TestRunId testRunId) {
      return pinned.getOrDefault(testRunId, Instant.EPOCH);
    }
  }

  private static final class RegisteredRuns implements TestRuns {

    private final Set<TestRunId> registered = new LinkedHashSet<>();

    Set<TestRunId> registered() {
      return Set.copyOf(registered);
    }

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

  private static final class Accounts implements ProviderAccounts {

    private final Map<TestRunId, List<String>> identities = new LinkedHashMap<>();
    private final Map<TestRunId, List<WebhookTarget>> targets = new LinkedHashMap<>();

    @Override
    public boolean remove(
        ProviderId provider, TestRunId testRunId, String accountId, AccountRemovalPlan plan) {
      if (!MergedIdentities.outOf(identities, targets, testRunId, accountId)) {
        return false;
      }
      plan.remove();
      return true;
    }

    @Override
    public void merge(
        ProviderId provider,
        TestRunId testRunId,
        List<String> accountIds,
        List<WebhookTarget> webhookTargets,
        ProviderConfigurationPlan plan) {
      plan.store();
      MergedIdentities.into(identities, targets, testRunId, accountIds, webhookTargets);
    }

    @Override
    public List<String> identitiesOf(ProviderId provider, TestRunId testRunId) {
      return List.copyOf(identities.getOrDefault(testRunId, new ArrayList<>()));
    }

    @Override
    public List<WebhookTarget> webhookTargetsOf(ProviderId provider, TestRunId testRunId) {
      return List.copyOf(targets.getOrDefault(testRunId, new ArrayList<>()));
    }
  }

  @Test
  void every_arm_of_a_configuration_verdict_says_whether_it_refused_and_under_which_status() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            ConfigurationAdmission.THE_CONFIGURATION_IT_ASKED_THIS_PROVIDER_TO_HOLD);

    assertThat(new ConfigurationAdmission.Verdict.Rejected("unreadable").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(subject));
    assertThat(
            new ConfigurationAdmission.Verdict.Refused(ConfigurationRefusal.of("no"))
                .theRefusalItReports())
        .isEqualTo(ActionVerdict.refused(subject));
    assertThat(
            new ConfigurationAdmission.Verdict.Conflicting(ConfigurationRefusal.of("taken"))
                .theRefusalItReports())
        .isEqualTo(ActionVerdict.conflicting(subject));
    assertThat(
            new ConfigurationAdmission.Verdict.Applied(RUN, List.of(), 0, 0, PINNED)
                .theRefusalItReports())
        .isNull();
  }

  @Test
  void every_arm_of_an_account_removal_says_whether_it_refused_and_under_which_status() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            ConfigurationAdmission.THE_ACCOUNT_IT_ASKED_THIS_PROVIDER_TO_FORGET);

    assertThat(new ConfigurationAdmission.Removal.Rejected("unreadable").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(subject));
    assertThat(new ConfigurationAdmission.Removal.NotConfigured("absent").theRefusalItReports())
        .isEqualTo(ActionVerdict.notFound(subject));
    assertThat(
            new ConfigurationAdmission.Removal.Removed(RUN, "merchant-1", List.of())
                .theRefusalItReports())
        .isNull();
  }

  @Test
  void every_arm_of_a_route_alias_release_says_whether_it_refused_and_under_which_status() {
    RefusalSubject subject =
        RefusalSubject.noObjectWasNamed(
            ConfigurationAdmission.THE_ROUTE_ALIAS_IT_ASKED_THIS_PROVIDER_TO_RELEASE);

    assertThat(new ConfigurationAdmission.Release.Rejected("unreadable").theRefusalItReports())
        .isEqualTo(ActionVerdict.rejected(subject));
    assertThat(new ConfigurationAdmission.Release.NotBound("absent").theRefusalItReports())
        .isEqualTo(ActionVerdict.notFound(subject));
    assertThat(
            new ConfigurationAdmission.Release.Released(RUN, PAYMENTS, List.of())
                .theRefusalItReports())
        .isNull();
  }
}
