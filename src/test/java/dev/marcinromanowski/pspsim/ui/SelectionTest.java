package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionTest {

  private static final TestRunId DEFAULT = TestRunId.of("default");

  private static final ProviderRegistry KNOWN = registryOf("grow", "stripe");

  @Test
  void a_screen_reached_with_no_provider_named_chooses_none_and_offers_the_ones_it_serves() {
    Selection selection = Selection.of(null, null, KNOWN, DEFAULT);

    assertThat(selection).isEqualTo(new Selection.Unnamed(List.of("grow", "stripe")));
  }

  @Test
  void a_provider_parameter_that_names_nothing_is_no_provider_rather_than_the_first_one() {
    assertThat(Selection.of("   ", null, KNOWN, DEFAULT))
        .isEqualTo(new Selection.Unnamed(List.of("grow", "stripe")));
  }

  @Test
  void the_provider_a_screen_shows_never_falls_out_of_the_order_the_modules_registered_in() {
    ProviderRegistry oneOrder = registryOf("grow", "stripe");
    ProviderRegistry theReverse = registryOf("stripe", "grow");

    assertThat(Selection.of(null, null, oneOrder, DEFAULT)).isNotInstanceOf(Selection.Chosen.class);
    assertThat(Selection.of(null, null, theReverse, DEFAULT))
        .isNotInstanceOf(Selection.Chosen.class);
  }

  @Test
  void a_tester_who_names_a_provider_and_a_test_run_reaches_that_provider_and_that_run() {
    Selection.Chosen chosen =
        chosen(Selection.of("stripe", "manual-mr-2026-09-10", KNOWN, DEFAULT));

    assertThat(chosen.provider()).isEqualTo("stripe");
    assertThat(chosen.testRunId()).isEqualTo(TestRunId.of("manual-mr-2026-09-10"));
  }

  @Test
  void a_tester_who_names_a_provider_but_no_test_run_reaches_the_default_run() {
    assertThat(chosen(Selection.of("grow", null, KNOWN, DEFAULT)).testRunId()).isEqualTo(DEFAULT);
  }

  @Test
  void the_screen_is_handed_the_module_itself_so_no_screen_looks_a_provider_up_a_second_time() {
    SimulatorProvider grow = new ProviderModuleDouble("grow");

    Selection.Chosen chosen =
        chosen(Selection.of("grow", null, new ProviderRegistry(List.of(grow)), DEFAULT));

    assertThat(chosen.module()).isSameAs(grow);
  }

  @Test
  void a_provider_this_simulator_does_not_serve_is_refused_and_the_refusal_names_what_it_serves() {
    Selection selection = Selection.of("no-such-psp", null, KNOWN, DEFAULT);

    assertThat(selection).isInstanceOf(Selection.Refused.class);
    assertThat(messageOf(selection)).contains(KNOWN.names());
    assertThat(messageOf(selection)).doesNotContain("no-such-psp");
  }

  @Test
  void a_refusal_names_the_property_that_was_wrong_and_never_repeats_the_value_that_was_sent() {
    Selection selection = Selection.of("grow", "a test run/with a slash", KNOWN, DEFAULT);

    assertThat(selection).isInstanceOf(Selection.Refused.class);
    assertThat(messageOf(selection)).contains("testRunId").doesNotContain("with a slash");
  }

  @Test
  void a_test_run_the_simulator_reserves_for_a_failed_resolution_is_no_screens_to_write_into() {
    Selection selection = Selection.of("grow", "UNKNOWN", KNOWN, DEFAULT);

    assertThat(selection)
        .as(
            "every screen whose subject is what one test run holds resolves its run this way, and"
                + " nothing of a run's own may be stored under the namespace this build keeps a"
                + " failed resolution in")
        .isInstanceOf(Selection.Refused.class);
    assertThat(messageOf(selection)).contains("UNKNOWN");
  }

  @Test
  void
      the_refusal_of_the_reserved_id_hands_a_tester_the_box_to_retype_rather_than_the_rule_alone() {
    Selection selection = Selection.of("grow", "UNKNOWN", KNOWN, DEFAULT);

    assertThat(messageOf(selection))
        .as(
            "this refusal is what a tester meets after following the link into the reserved"
                + " namespace and clicking the next screen the rail offers, so it carries the way"
                + " back to a run and not only the id that is refused")
        .isEqualTo(TheReservedRunOnAScreen.theRefusalOfAScreenThatShowsWhatARunHolds(DEFAULT))
        .contains("test run box")
        .contains(DEFAULT.value());
  }

  @Test
  void a_malformed_run_is_still_refused_by_its_shape_rather_than_as_the_reserved_namespace() {
    assertThat(messageOf(Selection.of("grow", "UNKNOWN!", KNOWN, DEFAULT)))
        .as("only the reserved id itself is refused as the reserved id")
        .doesNotContain("test run box")
        .contains("testRunId must match");
  }

  @Test
  void the_namespace_a_failed_resolution_is_kept_under_is_readable_by_a_screen_that_only_reads() {
    Selection selection =
        Selection.ofAScreenWhoseSubjectIsTheTrail("grow", "UNKNOWN", KNOWN, DEFAULT);

    assertThat(chosen(selection).testRunId()).isEqualTo(TestRunId.UNKNOWN);
  }

  @Test
  void
      a_test_run_outside_the_published_shape_is_refused_to_a_reading_screen_by_its_property_and_never_by_its_value() {
    Selection selection =
        Selection.ofAScreenWhoseSubjectIsTheTrail("grow", "a run/with a slash", KNOWN, DEFAULT);

    assertThat(selection).isInstanceOf(Selection.Refused.class);
    assertThat(messageOf(selection)).contains("testRunId").doesNotContain("with a slash");
  }

  @Test
  void a_namespace_nobody_named_is_the_default_one_rather_than_a_refusal() {
    assertThat(
            chosen(Selection.ofAScreenWhoseSubjectIsTheTrail("grow", "   ", KNOWN, DEFAULT))
                .testRunId())
        .isEqualTo(DEFAULT);
    assertThat(
            chosen(Selection.ofAScreenWhoseSubjectIsTheTrail("grow", null, KNOWN, DEFAULT))
                .testRunId())
        .isEqualTo(DEFAULT);
  }

  @Test
  void a_screen_that_only_reads_still_reaches_the_run_and_the_module_a_tester_named() {
    Selection.Chosen chosen =
        chosen(Selection.ofAScreenWhoseSubjectIsTheTrail("  stripe  ", " a-run ", KNOWN, DEFAULT));

    assertThat(chosen.provider()).isEqualTo("stripe");
    assertThat(chosen.testRunId()).isEqualTo(TestRunId.of("a-run"));
  }

  @Test
  void a_provider_a_reading_screen_does_not_serve_is_refused_before_any_run_is_resolved() {
    assertThat(Selection.ofAScreenWhoseSubjectIsTheTrail("no-such-psp", "UNKNOWN", KNOWN, DEFAULT))
        .isInstanceOf(Selection.Refused.class);
    assertThat(Selection.ofAScreenWhoseSubjectIsTheTrail(null, "UNKNOWN", KNOWN, DEFAULT))
        .isEqualTo(new Selection.Unnamed(List.of("grow", "stripe")));
  }

  @Test
  void the_reserved_namespace_a_reading_screen_reaches_is_no_run_an_action_could_be_filed_under() {
    Selection reading =
        Selection.ofAScreenWhoseSubjectIsTheTrail("grow", "UNKNOWN", KNOWN, DEFAULT);

    assertThatThrownBy(() -> TestRunId.storableNamespace(chosen(reading).testRunId()))
        .as(
            "a screen that only reads may name this namespace, and the store refuses it to"
                + " anything that would write into it")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_simulator_with_no_provider_module_says_so_rather_than_offering_an_empty_choice() {
    Selection selection = Selection.of(null, null, new ProviderRegistry(List.of()), DEFAULT);

    assertThat(selection).isInstanceOf(Selection.Refused.class);
    assertThat(messageOf(selection)).contains("no provider module");
  }

  @Test
  void a_provider_padded_with_whitespace_still_names_the_provider_it_spells() {
    assertThat(chosen(Selection.of("  grow  ", null, KNOWN, DEFAULT)).provider()).isEqualTo("grow");
  }

  @Test
  void only_a_chosen_provider_and_run_name_what_an_action_row_would_be_filed_under() {
    assertThat(Selection.of("grow", "a-run", KNOWN, DEFAULT).namesWhatARowIsFiledUnder()).isTrue();
    assertThat(Selection.of(null, "a-run", KNOWN, DEFAULT).namesWhatARowIsFiledUnder()).isFalse();
    assertThat(Selection.of("nosuch", "a-run", KNOWN, DEFAULT).namesWhatARowIsFiledUnder())
        .isFalse();
    assertThat(Selection.of("grow", "not a valid id", KNOWN, DEFAULT).namesWhatARowIsFiledUnder())
        .isFalse();
  }

  @Test
  void the_two_arms_that_name_nothing_carry_no_provider_module_and_no_test_run_to_file_one_by() {
    assertThat(Selection.Unnamed.class.getRecordComponents())
        .noneMatch(component -> component.getType() == SimulatorProvider.class)
        .noneMatch(component -> component.getType() == TestRunId.class);
    assertThat(Selection.Refused.class.getRecordComponents())
        .noneMatch(component -> component.getType() == SimulatorProvider.class)
        .noneMatch(component -> component.getType() == TestRunId.class);
  }

  private static ProviderRegistry registryOf(String... names) {
    List<SimulatorProvider> modules = new ArrayList<>();
    for (String name : names) {
      modules.add(new ProviderModuleDouble(name));
    }
    return new ProviderRegistry(List.copyOf(modules));
  }

  private static Selection.Chosen chosen(Selection selection) {
    assertThat(selection).isInstanceOf(Selection.Chosen.class);
    return (Selection.Chosen) selection;
  }

  private static String messageOf(Selection selection) {
    return ((Selection.Refused) selection).message();
  }
}
