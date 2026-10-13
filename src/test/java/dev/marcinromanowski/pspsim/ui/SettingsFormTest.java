package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.settings.ConfigurableSetting;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingGrammar;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationGrammar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsFormTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final String GROW_NAME = "grow";

  private static final ProviderId STRIPE = ProviderId.of("stripe");

  private static final String STRIPE_NAME = "stripe";

  private static final String RUN = "settings-form-test";

  private static final String THE_DEFAULT = "PT0S,PT1S,PT5S";

  private static final String THE_RUN_SET_THIS = "PT0S,PT30S";

  private static final String THE_RETRY_PLAN = "webhook.retryPlan";

  private static final ProviderSettings DECLARING_THE_RETRY_PLAN =
      new ProviderSettings(
          GROW, List.of(new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, THE_DEFAULT)));

  private static final ProviderSettings DECLARING_NOTHING = ProviderSettings.none(STRIPE);

  @Test
  void a_provider_that_declares_a_setting_offers_one_field_per_declared_setting_and_no_other() {
    SettingsForm.View offered = panel(Map.of(), Map.of());

    assertThat(offered.settings()).hasSize(1);
    assertThat(offered.settings().getFirst().key()).isEqualTo(SettingKey.WEBHOOK_RETRY_PLAN.key());
    assertThat(offered.provider()).isEqualTo(GROW_NAME);
    assertThat(offered.testRunId()).isEqualTo(RUN);
  }

  @Test
  void every_setting_this_simulator_knows_is_offered_by_a_label_a_tester_reads_and_not_its_key() {
    for (SettingKey key : SettingKey.values()) {
      SettingsForm.Setting offered =
          SettingsForm.of(
                  GROW_NAME,
                  RUN,
                  new ProviderSettings(
                      GROW, List.of(new ConfigurableSetting(key, defaultValueOf(key)))),
                  Map.of(),
                  Map.of(),
                  null)
              .settings()
              .getFirst();

      assertThat(offered.label())
          .as("a tester should not have to know how " + key.key() + " is spelled to set it")
          .isNotBlank()
          .doesNotContain(key.key());
      assertThat(offered.shape())
          .as("the field of " + key.key() + " says what a value of it looks like")
          .isNotBlank();
    }
  }

  @Test
  void a_provider_that_declares_no_setting_offers_no_field_at_all() {
    SettingsForm.View offered =
        SettingsForm.of(STRIPE_NAME, RUN, DECLARING_NOTHING, Map.of(), Map.of(), null);

    assertThat(offered.settings()).isEmpty();
  }

  @Test
  void a_setting_this_test_run_has_not_set_carries_the_default_the_provider_declares() {
    SettingsForm.Setting offered = panel(Map.of(), Map.of()).settings().getFirst();

    assertThat(offered.theValueTheTestRunUses()).isEqualTo(THE_DEFAULT);
    assertThat(offered.theValueTheFieldCarries()).isEqualTo(THE_DEFAULT);
    assertThat(offered.theValueTheProviderDefaultsTo()).isEqualTo(THE_DEFAULT);
    assertThat(offered.setByThisTestRun()).isFalse();
  }

  @Test
  void a_setting_this_test_run_has_set_carries_that_value_and_still_names_the_default() {
    SettingsForm.Setting offered = panel(theRunSetThePlan(), Map.of()).settings().getFirst();

    assertThat(offered.theValueTheTestRunUses()).isEqualTo(THE_RUN_SET_THIS);
    assertThat(offered.theValueTheFieldCarries()).isEqualTo(THE_RUN_SET_THIS);
    assertThat(offered.theValueTheProviderDefaultsTo()).isEqualTo(THE_DEFAULT);
    assertThat(offered.setByThisTestRun()).isTrue();
  }

  @Test
  void a_refused_value_is_handed_back_to_its_field_and_is_not_reported_as_one_in_use() {
    SettingsForm.Setting offered =
        panel(theRunSetThePlan(), Map.of(THE_RETRY_PLAN, "PT9S,PT1S")).settings().getFirst();

    assertThat(offered.theValueTheFieldCarries()).isEqualTo("PT9S,PT1S");
    assertThat(offered.theValueTheTestRunUses())
        .as("a value the simulator has not accepted is no value this test run uses")
        .isEqualTo(THE_RUN_SET_THIS);
    assertThat(offered.setByThisTestRun()).isTrue();
  }

  @Test
  void a_field_emptied_by_the_tester_is_handed_back_empty_and_not_refilled_from_anywhere() {
    SettingsForm.Setting offered =
        panel(theRunSetThePlan(), Map.of(THE_RETRY_PLAN, "")).settings().getFirst();

    assertThat(offered.theValueTheFieldCarries()).isEmpty();
    assertThat(offered.theValueTheTestRunUses()).isEqualTo(THE_RUN_SET_THIS);
  }

  @Test
  void the_field_a_refusal_names_is_marked_as_the_one_that_was_refused() {
    String aboutTheRetryPlan =
        ProviderConfigurationGrammar.settingProperty(THE_RETRY_PLAN) + "[1] must not be shorter";

    assertThat(theOnlyFieldOf(panel(Map.of(), Map.of(), aboutTheRetryPlan)).refused()).isTrue();
    assertThat(theOnlyFieldOf(panel(Map.of(), Map.of(), null)).refused()).isFalse();
  }

  @Test
  void a_refusal_about_another_part_of_the_configuration_marks_no_field_of_this_form() {
    assertThat(theOnlyFieldOf(panel(Map.of(), Map.of(), "fixedNow must be an instant")).refused())
        .isFalse();
    assertThat(
            theOnlyFieldOf(panel(Map.of(), Map.of(), "settings.webhook.somethingElse is declared"))
                .refused())
        .isFalse();
  }

  @Test
  void only_the_fields_this_form_submits_as_settings_are_read_as_settings() {
    Map<String, String> posted = new LinkedHashMap<>();
    posted.put("provider", GROW_NAME);
    posted.put("testRunId", RUN);
    posted.put("confirm", "yes");
    posted.put(SettingsForm.SUBMITTED_SETTING_PREFIX + THE_RETRY_PLAN, THE_RUN_SET_THIS);

    assertThat(SettingsForm.submittedSettingsOf(posted))
        .containsExactly(Map.entry(THE_RETRY_PLAN, THE_RUN_SET_THIS));
  }

  @Test
  void a_field_named_after_a_setting_without_the_prefix_is_no_setting_of_this_form() {
    assertThat(SettingsForm.submittedSettingsOf(Map.of(THE_RETRY_PLAN, THE_RUN_SET_THIS)))
        .isEmpty();
  }

  @Test
  void a_typed_value_this_test_run_does_not_hold_yet_is_written() {
    DeclaredConfiguration asked = asked(Map.of(), Map.of(THE_RETRY_PLAN, THE_RUN_SET_THIS));

    assertThat(asked.testRunId()).isEqualTo(RUN);
    assertThat(asked.settings()).containsExactly(Map.entry(THE_RETRY_PLAN, THE_RUN_SET_THIS));
    assertThat(asked.accounts()).isNull();
    assertThat(asked.webhooks()).isNull();
    assertThat(asked.routeAliases()).isNull();
    assertThat(asked.clock()).isNull();
  }

  @Test
  void a_field_submitted_untouched_leaves_a_setting_this_test_run_never_took_over_alone() {
    DeclaredConfiguration asked = asked(Map.of(), Map.of(THE_RETRY_PLAN, THE_DEFAULT));

    assertThat(asked.settings())
        .as(
            "one click on a form of many fields must set the one that was typed and not pin every"
                + " default as a value this test run chose")
        .isEmpty();
  }

  @Test
  void a_setting_this_test_run_has_taken_over_is_written_even_when_it_is_handed_back_unchanged() {
    assertThat(asked(theRunSetThePlan(), Map.of(THE_RETRY_PLAN, THE_RUN_SET_THIS)).settings())
        .containsExactly(Map.entry(THE_RETRY_PLAN, THE_RUN_SET_THIS));
    assertThat(asked(theRunSetThePlan(), Map.of(THE_RETRY_PLAN, THE_DEFAULT)).settings())
        .as("typing the provider's default over a value this run set is how it goes back")
        .containsExactly(Map.entry(THE_RETRY_PLAN, THE_DEFAULT));
  }

  @Test
  void an_emptied_field_is_written_as_the_empty_value_the_control_api_refuses() {
    assertThat(asked(Map.of(), Map.of(THE_RETRY_PLAN, "")).settings())
        .as("no route returns a setting to the default, so blank is no way back to one")
        .containsExactly(Map.entry(THE_RETRY_PLAN, ""));
  }

  @Test
  void a_key_this_provider_declares_nothing_for_reaches_the_admission_that_refuses_it() {
    assertThat(asked(Map.of(), Map.of("webhook.nothing", THE_DEFAULT)).settings())
        .as(
            "the form filters no key it does not know, so the screen is refused in the words the"
                + " control API refuses it in")
        .containsExactly(Map.entry("webhook.nothing", THE_DEFAULT));
    assertThat(
            SettingsForm.settingsOf(
                    RUN, DECLARING_NOTHING, Map.of(), Map.of(THE_RETRY_PLAN, THE_DEFAULT))
                .settings())
        .containsExactly(Map.entry(THE_RETRY_PLAN, THE_DEFAULT));
  }

  @Test
  void the_shape_of_the_retry_plan_field_names_the_attempts_one_delivery_may_make() {
    SettingsForm.Setting offered = panel(Map.of(), Map.of()).settings().getFirst();

    assertThat(offered.shape())
        .contains(String.valueOf(SettingGrammar.MOST_ATTEMPTS_A_RETRY_PLAN_MAY_HOLD));
  }

  private static DeclaredConfiguration asked(
      Map<SettingKey, String> setByThisTestRun, Map<String, String> submitted) {
    return SettingsForm.settingsOf(RUN, DECLARING_THE_RETRY_PLAN, setByThisTestRun, submitted);
  }

  private static SettingsForm.Setting theOnlyFieldOf(SettingsForm.View panel) {
    return panel.settings().getFirst();
  }

  private static Map<SettingKey, String> theRunSetThePlan() {
    return Map.of(SettingKey.WEBHOOK_RETRY_PLAN, THE_RUN_SET_THIS);
  }

  private static SettingsForm.View panel(
      Map<SettingKey, String> setByThisTestRun, Map<String, String> submitted) {
    return panel(setByThisTestRun, submitted, null);
  }

  private static SettingsForm.View panel(
      Map<SettingKey, String> setByThisTestRun, Map<String, String> submitted, String refusal) {
    return SettingsForm.of(
        GROW_NAME, RUN, DECLARING_THE_RETRY_PLAN, setByThisTestRun, submitted, refusal);
  }

  private static String defaultValueOf(SettingKey key) {
    return switch (key) {
      case WEBHOOK_RETRY_PLAN -> THE_DEFAULT;
    };
  }
}
