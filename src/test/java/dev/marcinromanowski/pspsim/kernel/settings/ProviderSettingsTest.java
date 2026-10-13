package dev.marcinromanowski.pspsim.kernel.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderSettingsTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final ProviderId AZERICARD = ProviderId.of("azericard");

  @Test
  void a_provider_that_declares_nothing_declares_no_key_a_harness_could_write() {
    ProviderSettings none = ProviderSettings.none(AZERICARD);

    assertThat(none.keys()).isEmpty();
    assertThat(none.declares(SettingKey.WEBHOOK_RETRY_PLAN)).isFalse();
    assertThat(none.setting(SettingKey.WEBHOOK_RETRY_PLAN)).isEmpty();
  }

  @Test
  void a_provider_that_declares_a_setting_carries_the_value_it_falls_back_to() {
    ProviderSettings declared =
        new ProviderSettings(
            GROW, List.of(new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1S")));

    assertThat(declared.declares(SettingKey.WEBHOOK_RETRY_PLAN)).isTrue();
    assertThat(declared.keys()).containsExactly("webhook.retryPlan");
    assertThat(declared.setting(SettingKey.WEBHOOK_RETRY_PLAN))
        .hasValueSatisfying(
            setting -> assertThat(setting.valueItDefaultsTo()).isEqualTo("PT0S,PT1S"));
  }

  @Test
  void a_provider_declaring_one_setting_twice_is_refused_rather_than_given_two_defaults() {
    List<ConfigurableSetting> oneKeyTwice =
        List.of(
            new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S"),
            new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,PT1S"));

    assertThatThrownBy(() -> new ProviderSettings(GROW, oneKeyTwice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("webhook.retryPlan")
        .hasMessageContaining("declared twice");
  }

  @Test
  void a_default_the_core_could_not_read_is_refused_where_the_provider_declares_it() {
    assertThatThrownBy(
            () -> new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S,ten minutes"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("webhook.retryPlan[1] must be an ISO-8601 duration");
  }

  @Test
  void the_registry_answers_a_provider_that_declared_nothing_with_a_declaration_of_nothing() {
    DeclaredProviderSettings registry =
        new DeclaredProviderSettings(
            List.of(
                new ProviderSettings(
                    GROW,
                    List.of(new ConfigurableSetting(SettingKey.WEBHOOK_RETRY_PLAN, "PT0S")))));

    assertThat(registry.of(AZERICARD).keys()).isEmpty();
    assertThat(registry.of(AZERICARD).provider()).isEqualTo(AZERICARD);
    assertThat(registry.of(GROW).keys()).containsExactly("webhook.retryPlan");
  }

  @Test
  void two_declarations_claiming_one_provider_are_refused_at_startup() {
    List<ProviderSettings> oneProviderTwice =
        List.of(ProviderSettings.none(GROW), ProviderSettings.none(GROW));

    assertThatThrownBy(() -> new DeclaredProviderSettings(oneProviderTwice))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("grow");
  }
}
