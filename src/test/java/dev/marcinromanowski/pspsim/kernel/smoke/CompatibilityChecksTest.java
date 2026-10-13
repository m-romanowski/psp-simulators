package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompatibilityChecksTest {

  private static final Provenance PROVENANCE = Samples.A_PUBLISHED_EXAMPLE;

  @Test
  void the_checks_of_every_module_are_collected_in_the_order_the_modules_were_given_in() {
    CompatibilityChecks collected =
        new CompatibilityChecks(
            List.of(
                publishing("onekey", aCheck("onekey")), publishing("twokeys", aCheck("twokeys"))));

    assertThat(collected.all())
        .extracting(check -> check.provider().value())
        .containsExactly("onekey", "twokeys");
  }

  @Test
  void a_provider_that_publishes_no_check_is_named_rather_than_counted_as_passing() {
    CompatibilityChecks collected =
        new CompatibilityChecks(
            List.of(publishing("onekey", aCheck("onekey")), publishingNothing("twokeys")));

    assertThat(collected.all()).hasSize(1);
    assertThat(collected.providersWithNoCheckYet()).containsExactly("twokeys");
  }

  @Test
  void a_simulator_whose_modules_all_publish_checks_names_none_as_uncovered() {
    CompatibilityChecks collected =
        new CompatibilityChecks(List.of(publishing("onekey", aCheck("onekey"))));

    assertThat(collected.providersWithNoCheckYet()).isEmpty();
  }

  @Test
  void a_simulator_with_no_module_at_all_collects_nothing_and_names_nothing() {
    CompatibilityChecks collected = new CompatibilityChecks(List.of());

    assertThat(collected.all()).isEmpty();
    assertThat(collected.providersWithNoCheckYet()).isEmpty();
  }

  @Test
  void a_published_set_that_names_no_module_is_refused_so_an_uncovered_row_always_has_a_name() {
    assertThatThrownBy(() -> new CompatibilityChecks.ProviderChecks(" ", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("provider must name the module");
  }

  private static CompatibilityChecks.ProviderChecks publishing(String name, ContractCheck check) {
    return new CompatibilityChecks.ProviderChecks(name, List.of(check));
  }

  private static CompatibilityChecks.ProviderChecks publishingNothing(String name) {
    return new CompatibilityChecks.ProviderChecks(name, List.of());
  }

  private static ContractCheck aCheck(String provider) {
    return DeclaredCheck.applying(
        ProviderId.of(provider),
        "POST /somewhere",
        PROVENANCE,
        List.of(new ContractExpectation("status", Samples.published("1", PROVENANCE))),
        transport -> Map.of("status", "1"));
  }
}
