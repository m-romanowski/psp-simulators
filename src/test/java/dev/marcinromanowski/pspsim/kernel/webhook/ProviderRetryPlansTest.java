package dev.marcinromanowski.pspsim.kernel.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.InMemoryTestRunSettings;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderRetryPlansTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final ProviderId AZERICARD = ProviderId.of("azericard");

  private static final TestRunId MINE = TestRunId.of("my-run");

  private static final TestRunId SOMEBODY_ELSES = TestRunId.of("another-run");

  private static final RetryPlan WHAT_THE_SIMULATOR_SHIPS =
      new RetryPlan(
          List.of(
              Duration.ZERO,
              Duration.ofSeconds(1),
              Duration.ofSeconds(5),
              Duration.ofSeconds(30),
              Duration.ofMinutes(2)));

  private static final String GROWS_SIX = "PT0S,PT1S,PT5S,PT30S,PT2M,PT5M";

  private final InMemoryTestRunSettings configured = new InMemoryTestRunSettings();

  private final ProviderRetryPlans plans =
      new ProviderRetryPlans(
          new DeclaredProviderSettings(
              List.of(
                  new ProviderSettings(
                      GROW,
                      List.of(new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, GROWS_SIX))))),
          configured,
          WHAT_THE_SIMULATOR_SHIPS);

  @Test
  void a_provider_that_declares_no_retry_setting_keeps_the_plan_the_simulator_ships() {
    assertThat(plans.of(AZERICARD, MINE)).isEqualTo(WHAT_THE_SIMULATOR_SHIPS);
    assertThat(plans.of(AZERICARD, MINE).attemptCap()).isEqualTo(5);
  }

  @Test
  void a_provider_that_declares_a_retry_plan_answers_with_its_own_before_anybody_configures_one() {
    assertThat(plans.of(GROW, MINE).attemptCap()).isEqualTo(6);
    assertThat(plans.of(GROW, MINE).delays()).endsWith(Duration.ofMinutes(5));
  }

  @Test
  void a_harness_that_sets_the_plan_for_its_run_leaves_every_other_run_on_the_declared_one() {
    configured.merge(GROW, MINE, Map.of(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1S"));

    assertThat(plans.of(GROW, MINE).delays()).containsExactly(Duration.ZERO, Duration.ofSeconds(1));
    assertThat(plans.of(GROW, SOMEBODY_ELSES).attemptCap()).isEqualTo(6);
  }

  @Test
  void a_setting_written_for_one_provider_does_not_reach_another_provider_of_the_same_run() {
    configured.merge(GROW, MINE, Map.of(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1S"));

    assertThat(plans.of(AZERICARD, MINE)).isEqualTo(WHAT_THE_SIMULATOR_SHIPS);
  }
}
