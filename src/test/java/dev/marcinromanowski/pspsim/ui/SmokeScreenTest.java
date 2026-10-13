package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.smoke.Evidence;
import dev.marcinromanowski.pspsim.kernel.smoke.EvidenceClass;
import dev.marcinromanowski.pspsim.kernel.smoke.FailureKind;
import dev.marcinromanowski.pspsim.kernel.smoke.Provenance;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRun;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeVerdict;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SmokeScreenTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final Provenance A_SUCCESS_IS_ONE =
      new Provenance(
          "grow/create-payment-process-approved",
          Evidence.of(EvidenceClass.PUBLISHED_EXAMPLE),
          "https://developers.grow.business/reference/delayed-payment-response",
          "grow.md, Create Hosted Payment Process");

  private static final Provenance NOTHING_SIGNS_A_GROW_CALLBACK =
      new Provenance(
          "grow/unsigned-callback",
          Evidence.of(EvidenceClass.PUBLISHED_EXAMPLE),
          "https://developers.grow.business/reference/server-to-server-callback",
          "grow.md, Merchant Callback Contract",
          "the published callback carries no signature");

  private static final String UNSIGNED = "Grow signs no server-to-server callback";

  private static final String THE_SHAPE_A_RUN_IS_NAMED_IN =
      "testRunId must match [A-Za-z0-9][A-Za-z0-9._-]{0,63}, which is at most 64 characters, the"
          + " first a letter or a digit and the rest a letter, a digit, a dot, a hyphen or an"
          + " underscore, for example checkout-smoke";

  private static final ProviderRegistry KNOWN =
      new ProviderRegistry(List.of(new ProviderModuleDouble("grow")));

  private static final TestRunId WHEN_NO_RUN_IS_NAMED = TestRunId.of("default");

  @Test
  void a_run_name_the_bar_carries_on_to_every_other_screen_is_refused_on_this_one_as_well() {
    Selection whatTheBarCarries = Selection.of("grow", "a run", KNOWN, WHEN_NO_RUN_IS_NAMED);

    assertThat(SmokeScreen.theRefusalThisScreenPrints(null, whatTheBarCarries))
        .as(
            "the checks this screen runs name no run of a tester's, and the bar it draws hands"
                + " what they typed to every screen they click to next, so a name this screen"
                + " took in silence came back refused somewhere else")
        .isEqualTo(THE_SHAPE_A_RUN_IS_NAMED_IN);
  }

  @Test
  void a_bar_naming_a_provider_and_a_run_both_known_leaves_this_screen_refusing_nothing() {
    assertThat(
            SmokeScreen.theRefusalThisScreenPrints(
                null, Selection.of("grow", "checkout-smoke", KNOWN, WHEN_NO_RUN_IS_NAMED)))
        .isNull();
    assertThat(
            SmokeScreen.theRefusalThisScreenPrints(
                null, Selection.of(null, null, KNOWN, WHEN_NO_RUN_IS_NAMED)))
        .as("a tester who has named no provider yet is choosing one, not being refused")
        .isNull();
  }

  @Test
  void what_pressing_run_the_checks_refused_is_the_refusal_printed_and_not_the_bars() {
    assertThat(
            SmokeScreen.theRefusalThisScreenPrints(
                "the checks are already running",
                Selection.of("grow", "a run", KNOWN, WHEN_NO_RUN_IS_NAMED)))
        .as("a tester who just pressed the button reads what the press answered")
        .isEqualTo("the checks are already running");
  }

  @Test
  void a_simulator_that_has_run_no_check_says_so_and_shows_no_row() {
    SmokeScreen.View view = SmokeScreen.of(Optional.empty(), List.of());

    assertThat(view.ran()).isFalse();
    assertThat(view.summary()).isEqualTo(SmokeScreen.NOTHING_HAS_BEEN_CHECKED_YET);
    assertThat(view.rows()).isEmpty();
    assertThat(view.passed()).isZero();
    assertThat(view.tookMillis()).isZero();
  }

  @Test
  void a_provider_no_check_covers_yet_is_named_even_before_anything_has_been_run() {
    SmokeScreen.View view = SmokeScreen.of(Optional.empty(), List.of("autra", "worldline"));

    assertThat(view.uncovered()).containsExactly("autra", "worldline");
  }

  @Test
  void a_run_in_which_everything_applicable_held_says_so_and_counts_what_it_did() {
    SmokeScreen.View view =
        SmokeScreen.of(
            Optional.of(
                aRunOf(
                    new SmokeVerdict.Passed(GROW, "POST /create", A_SUCCESS_IS_ONE, 2),
                    new SmokeVerdict.NotApplicable(
                        GROW, "POST notifyUrl", NOTHING_SIGNS_A_GROW_CALLBACK, UNSIGNED))),
            List.of());

    assertThat(view.ran()).isTrue();
    assertThat(view.summary()).isEqualTo(SmokeScreen.EVERY_APPLICABLE_CHECK_HELD);
    assertThat(view.passed()).isEqualTo(1);
    assertThat(view.notApplicable()).isEqualTo(1);
    assertThat(view.failed()).isZero();
    assertThat(view.tookMillis()).isEqualTo(1200);
  }

  @Test
  void a_held_check_shows_the_sample_its_expectations_were_read_from_and_names_no_field() {
    SmokeScreen.View view =
        SmokeScreen.of(
            Optional.of(aRunOf(new SmokeVerdict.Passed(GROW, "POST /create", A_SUCCESS_IS_ONE, 1))),
            List.of());

    assertThat(view.rows())
        .singleElement()
        .isEqualTo(
            new SmokeScreen.Row("grow", "POST /create", "held", "", A_SUCCESS_IS_ONE.rendered()));
  }

  @Test
  void a_check_that_does_not_apply_shows_why_beside_the_sample_that_shows_it() {
    SmokeScreen.View view =
        SmokeScreen.of(
            Optional.of(
                aRunOf(
                    new SmokeVerdict.NotApplicable(
                        GROW, "POST notifyUrl", NOTHING_SIGNS_A_GROW_CALLBACK, UNSIGNED))),
            List.of());

    assertThat(view.rows())
        .singleElement()
        .isEqualTo(
            new SmokeScreen.Row(
                "grow",
                "POST notifyUrl",
                "does not apply",
                "",
                "Grow signs no server-to-server callback (grow/unsigned-callback: published "
                    + "example, "
                    + "https://developers.grow.business/reference/server-to-server-callback "
                    + "(grow.md, Merchant Callback Contract); "
                    + "the published callback carries no signature)"));
  }

  @Test
  void a_failing_check_shows_the_provider_the_operation_the_field_and_the_vendor_sample() {
    SmokeScreen.View view =
        SmokeScreen.of(
            Optional.of(
                aRunOf(
                    new SmokeVerdict.Failed(
                        GROW,
                        "POST /create",
                        A_SUCCESS_IS_ONE,
                        "status",
                        FailureKind.NOT_AS_PUBLISHED))),
            List.of());

    assertThat(view.summary()).isEqualTo(SmokeScreen.A_CHECK_DID_NOT_HOLD);
    assertThat(view.rows())
        .singleElement()
        .isEqualTo(
            new SmokeScreen.Row(
                "grow",
                "POST /create",
                "not what the vendor sample shows",
                "status",
                A_SUCCESS_IS_ONE.rendered()));
  }

  @Test
  void a_failing_row_renders_no_value_the_answer_carried() {
    SmokeScreen.View view =
        SmokeScreen.of(
            Optional.of(
                aRunOf(
                    new SmokeVerdict.Failed(
                        GROW,
                        "POST /create",
                        A_SUCCESS_IS_ONE,
                        "status",
                        FailureKind.NOT_AS_PUBLISHED))),
            List.of());

    assertThat(view.rows().get(0).field()).isEqualTo("status");
    assertThat(view.rows().get(0).verdict()).doesNotContain("0").doesNotContain("observed value");
  }

  @Test
  void each_way_a_check_can_fail_reads_as_its_own_sentence() {
    assertThat(SmokeScreen.verdictOf(FailureKind.NOT_AS_PUBLISHED))
        .isEqualTo("not what the vendor sample shows");
    assertThat(SmokeScreen.verdictOf(FailureKind.NOT_OBSERVED))
        .isEqualTo("the answer carried no such field");
    assertThat(SmokeScreen.verdictOf(FailureKind.ASSERTS_NOTHING))
        .isEqualTo("the check asserts nothing");
    assertThat(SmokeScreen.verdictOf(FailureKind.UNREACHABLE))
        .isEqualTo("the route did not answer");
  }

  private static SmokeRun aRunOf(SmokeVerdict... verdicts) {
    return new SmokeRun(
        Instant.parse("2026-09-24T09:00:00Z"), Duration.ofMillis(1200), List.of(verdicts));
  }
}
