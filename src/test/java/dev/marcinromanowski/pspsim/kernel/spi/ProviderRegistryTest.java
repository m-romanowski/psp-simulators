package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderRegistryTest {

  @Test
  void the_registry_answers_for_every_module_it_was_built_with() {
    ProviderRegistry registry =
        new ProviderRegistry(List.of(new ProviderDouble("azericard"), new ProviderDouble("grow")));

    assertThat(registry.names()).containsExactly("azericard", "grow");
    assertThat(registry.find("grow")).isPresent();
    assertThat(registry.find("no-such-psp")).isEmpty();
  }

  @Test
  void a_screen_that_summarises_every_provider_is_handed_the_modules_in_the_order_it_serves_them() {
    ProviderRegistry registry =
        new ProviderRegistry(
            List.of(
                new ProviderDouble("azericard"),
                new ProviderDouble("grow"),
                new ProviderDouble("stripe")));

    assertThat(registry.modules())
        .extracting(module -> module.id().value())
        .containsExactly("azericard", "grow", "stripe");
    assertThat(registry.modules()).hasSameSizeAs(registry.names());
  }

  @Test
  void the_refusal_of_an_unknown_provider_names_every_provider_this_simulator_serves() {
    ProviderRegistry registry =
        new ProviderRegistry(List.of(new ProviderDouble("azericard"), new ProviderDouble("grow")));

    assertThat(registry.theRefusalOfAnUnknownProvider())
        .contains("unknown provider")
        .contains(registry.names());
  }

  @Test
  void a_provider_added_to_the_simulator_is_named_by_the_refusal_without_it_being_rewritten() {
    ProviderRegistry two =
        new ProviderRegistry(List.of(new ProviderDouble("azericard"), new ProviderDouble("grow")));
    ProviderRegistry three =
        new ProviderRegistry(
            List.of(
                new ProviderDouble("azericard"),
                new ProviderDouble("grow"),
                new ProviderDouble("no-such-psp")));

    assertThat(two.theRefusalOfAnUnknownProvider()).doesNotContain("no-such-psp");
    assertThat(three.theRefusalOfAnUnknownProvider()).contains(three.names());
  }

  @Test
  void two_modules_claiming_one_provider_id_stop_the_simulator_rather_than_one_winning() {
    List<SimulatorProvider> clashing =
        List.of(new ProviderDouble("grow"), new ProviderDouble("grow"));

    assertThatThrownBy(() -> new ProviderRegistry(clashing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("grow");
  }
}
