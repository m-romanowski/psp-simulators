package dev.marcinromanowski.pspsim.kernel.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BundleReportTest {

  private static final List<BundleEntry> THREE_KINDS_OF_VERDICT =
      List.of(
          BundleEntry.created("account", "merchant-1"),
          BundleEntry.created("scenarioRule", "rules[0]"),
          BundleEntry.updated("setting", "webhook.retryPlan"),
          BundleEntry.skipped("fixture", "visa-success"));

  @Test
  void a_report_counts_each_verdict_from_the_entries_it_carries() {
    BundleReport report =
        BundleReport.of("run-a", "stripe", true, THREE_KINDS_OF_VERDICT, List.of());

    assertThat(report.created()).isEqualTo(2);
    assertThat(report.updated()).isEqualTo(1);
    assertThat(report.skipped()).isEqualTo(1);
  }

  @Test
  void a_dry_run_and_an_applied_import_over_one_plan_differ_only_in_whether_it_was_applied() {
    BundleReport dry = BundleReport.of("run-a", "stripe", false, THREE_KINDS_OF_VERDICT, List.of());
    BundleReport applied =
        BundleReport.of("run-a", "stripe", true, THREE_KINDS_OF_VERDICT, List.of());

    assertThat(dry.applied()).isFalse();
    assertThat(applied.applied()).isTrue();
    assertThat(dry.entries()).isEqualTo(applied.entries());
    assertThat(dry.created()).isEqualTo(applied.created());
    assertThat(dry.updated()).isEqualTo(applied.updated());
    assertThat(dry.skipped()).isEqualTo(applied.skipped());
  }

  @Test
  void a_report_over_no_entry_counts_nothing_under_any_verdict() {
    BundleReport report = BundleReport.of("run-a", "stripe", true, List.of(), List.of());

    assertThat(report.created()).isZero();
    assertThat(report.updated()).isZero();
    assertThat(report.skipped()).isZero();
    assertThat(report.entries()).isEmpty();
  }

  @Test
  void a_report_names_the_run_the_provider_and_the_secret_fields_it_was_given() {
    BundleReport report =
        BundleReport.of("run-a", "stripe", true, List.of(), List.of("merchant-1.secretKey"));

    assertThat(report.testRunId()).isEqualTo("run-a");
    assertThat(report.provider()).isEqualTo("stripe");
    assertThat(report.secretsSupplied()).containsExactly("merchant-1.secretKey");
  }

  @Test
  void the_entries_a_report_was_built_from_do_not_change_it_afterwards() {
    List<BundleEntry> entries =
        new ArrayList<>(List.of(BundleEntry.created("account", "merchant-1")));
    BundleReport report = BundleReport.of("run-a", "stripe", true, entries, List.of());
    entries.add(BundleEntry.created("account", "merchant-2"));

    assertThat(report.entries()).hasSize(1);
    assertThat(report.created()).isEqualTo(1);
  }
}
