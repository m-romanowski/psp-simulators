package dev.marcinromanowski.pspsim.kernel.bundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class BundleEntryTest {

  @Test
  void an_entry_created_updated_or_skipped_carries_the_verdict_its_factory_names() {
    assertThat(BundleEntry.created("account", "merchant-1").verdict())
        .isEqualTo(BundleVerdict.CREATED);
    assertThat(BundleEntry.updated("account", "merchant-1").verdict())
        .isEqualTo(BundleVerdict.UPDATED);
    assertThat(BundleEntry.skipped("account", "merchant-1").verdict())
        .isEqualTo(BundleVerdict.SKIPPED);
  }

  @Test
  void an_entry_keeps_the_kind_and_the_name_it_was_given() {
    BundleEntry entry = BundleEntry.created("fixture", "visa-success");

    assertThat(entry.kind()).isEqualTo("fixture");
    assertThat(entry.name()).isEqualTo("visa-success");
  }

  @Test
  void an_entry_naming_no_kind_is_refused_rather_than_counted() {
    assertThatThrownBy(() -> BundleEntry.created(" ", "merchant-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("kind");
  }

  @Test
  void an_entry_naming_no_object_is_refused_rather_than_counted() {
    assertThatThrownBy(() -> BundleEntry.skipped("account", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void the_kinds_an_import_reports_on_are_the_seven_this_bundle_carries() {
    assertThat(
            List.of(
                BundleEntry.ACCOUNT,
                BundleEntry.WEBHOOK_TARGET,
                BundleEntry.ROUTE_ALIAS,
                BundleEntry.SETTING,
                BundleEntry.CLOCK,
                BundleEntry.FIXTURE,
                BundleEntry.SCENARIO_RULE))
        .containsExactly(
            "account",
            "webhookTarget",
            "routeAlias",
            "setting",
            "clock",
            "fixture",
            "scenarioRule");
  }
}
