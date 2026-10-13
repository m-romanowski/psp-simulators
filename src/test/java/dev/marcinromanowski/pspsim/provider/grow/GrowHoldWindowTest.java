package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.spi.AccountProperty;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurationRejectedException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GrowHoldWindowTest {

  private static final String PREFIX = "accounts[0].configuration.";

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "PT0.0S", "P0D"})
  void a_capture_window_of_zero_is_refused_rather_than_born_expired(String nothing) {
    assertThatCode(() -> GrowHoldWindow.of(PREFIX, nothing, "P10D"))
        .as(
            "a window of zero made every hold on the account uncapturable from the instant it was"
                + " created, and no answer anywhere said the account was the reason")
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("captureWindow must be longer than nothing");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "PT0.0S", "P0D"})
  void an_automatic_void_of_zero_is_refused_too(String nothing) {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, "P7D", nothing))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("automaticVoidAfter must be longer than nothing");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT-120H", "-P5D", "PT-1S"})
  void a_negative_capture_window_is_refused_because_a_desk_keyboard_can_type_a_minus(String back) {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, back, "P10D"))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("captureWindow must be longer than nothing");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT-120H", "-P5D", "PT-1S"})
  void a_negative_automatic_void_is_refused_too(String back) {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, "P7D", back))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("automaticVoidAfter must be longer than nothing");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0.000000001S", "PT0.0000005S", "PT1.0000015S", "PT0.00000999S"})
  void a_span_finer_than_the_store_keeps_is_refused_rather_than_stored_and_then_rejected(
      String finer) {
    assertThatCode(() -> GrowHoldWindow.of(PREFIX, finer, "P10D"))
        .as(
            "the provider took these and JdbcGrowAccounts wrote the same text, which a"
                + " microsecond-resolution interval truncates, so the column's own check then"
                + " refused the row and a control call answered 500 instead of a refusal")
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("captureWindow must be a whole number of microseconds");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0.000000001S", "PT0.0000005S", "PT1.0000015S"})
  void a_span_finer_than_the_store_keeps_is_refused_in_the_automatic_void_too(String finer) {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, "P7D", finer))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("automaticVoidAfter must be a whole number of microseconds");
  }

  @Test
  void one_microsecond_is_the_shortest_span_this_provider_takes() {
    GrowHoldWindow finest = GrowHoldWindow.of(PREFIX, "PT0.000001S", "PT0.000001S");

    assertThat(finest.captureWindow()).isEqualTo(Duration.ofNanos(1000));
    assertThat(finest.automaticVoidAfter()).isEqualTo(Duration.ofNanos(1000));
  }

  @Test
  void a_capture_window_beyond_a_year_is_refused() {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, "P366D", "P10D"))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("captureWindow must be at most 365 days");
  }

  @Test
  void an_automatic_void_beyond_a_year_is_refused() {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, "P7D", "P366D"))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("automaticVoidAfter must be at most 365 days");
  }

  @Test
  void a_span_of_exactly_a_year_is_taken_because_the_ceiling_is_a_span_a_test_may_still_state() {
    GrowHoldWindow ayear = GrowHoldWindow.of(PREFIX, "P365D", "P365D");

    assertThat(ayear.captureWindow()).isEqualTo(Duration.ofDays(365));
    assertThat(ayear.automaticVoidAfter()).isEqualTo(Duration.ofDays(365));
  }

  @Test
  void
      a_span_one_second_beyond_a_year_is_refused_so_the_ceiling_is_not_a_boundary_a_mutant_moves() {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, "PT8760H1S", "P10D"))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("captureWindow must be at most 365 days");
  }

  @Test
  void a_span_a_microsecond_beyond_a_year_is_refused_for_the_ceiling_and_not_for_its_fineness() {
    assertThatCode(() -> GrowHoldWindow.of(PREFIX, "PT8760H0.000001S", "P10D"))
        .as("the ceiling is judged before the resolution, so the larger mistake is the one named")
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("captureWindow must be at most 365 days");
  }

  @Test
  void a_span_so_long_that_adding_it_to_a_hold_would_throw_is_refused_before_it_is_ever_stored() {
    assertThatCode(() -> GrowHoldWindow.of(PREFIX, "PT2562047788015215H30M7S", "P10D"))
        .as(
            "Duration.parse reads this and Instant.plus throws ArithmeticException on it, so a"
                + " settle of any hold on the account left an unchecked exception to escape the"
                + " provider, which no handler in this build catches")
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("captureWindow must be at most 365 days");
  }

  @ParameterizedTest
  @ValueSource(strings = {"soon", "7 days", "P1W", "5", "PT", "P7DT", "PT9X"})
  void a_span_no_reader_can_count_is_still_refused_for_not_naming_a_span(String unreadable) {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, unreadable, "P10D"))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("captureWindow must name a span of time");
  }

  @Test
  void a_readable_positive_span_inside_the_ceiling_is_taken_as_it_was_written() {
    assertThat(GrowHoldWindow.of(PREFIX, "PT72H", "PT100H"))
        .isEqualTo(new GrowHoldWindow(Duration.ofHours(72), Duration.ofHours(100)));
    assertThat(GrowHoldWindow.of(PREFIX, "P20D", "P30D"))
        .isEqualTo(new GrowHoldWindow(Duration.ofDays(20), Duration.ofDays(30)));
    assertThat(GrowHoldWindow.of(PREFIX, "PT0.5S", "PT1S"))
        .isEqualTo(new GrowHoldWindow(Duration.ofMillis(500), Duration.ofSeconds(1)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"+PT1H", "pt1h", "PT+1H", "PT0,5S"})
  void a_form_only_a_backfilled_row_can_hold_is_still_taken_by_the_provider(String aBackfillShape) {
    GrowHoldWindow taken = GrowHoldWindow.of(PREFIX, aBackfillShape, "P10D");

    assertThat(taken.captureWindow())
        .as(
            "Duration.parse reads all four, so the provider takes them, and JdbcGrowAccounts"
                + " writes Duration.toString() rather than the text it was handed, which is why"
                + " the column's narrower predicate never meets one from this route")
        .isIn(Duration.ofHours(1), Duration.ofMillis(500));
    assertThat(taken.captureWindow().toString()).doesNotContain("+").doesNotContain(",");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void a_span_nobody_stated_still_falls_back_on_the_vendor_default_rather_than_being_refused(
      String blank) {
    GrowHoldWindow fellBack = GrowHoldWindow.of(PREFIX, blank, blank);

    assertThat(fellBack.captureWindow()).isEqualTo(Duration.ofDays(7));
    assertThat(fellBack.automaticVoidAfter()).isEqualTo(Duration.ofDays(10));
  }

  @Test
  void a_span_no_call_stated_at_all_falls_back_on_the_vendor_default() {
    GrowHoldWindow fellBack = GrowHoldWindow.of(PREFIX, null, null);

    assertThat(fellBack.captureWindow()).isEqualTo(Duration.ofDays(7));
    assertThat(fellBack.automaticVoidAfter()).isEqualTo(Duration.ofDays(10));
  }

  @ParameterizedTest
  @CsvSource({
    GrowHoldWindow.CAPTURE_WINDOW + ", P7D",
    GrowHoldWindow.AUTOMATIC_VOID_AFTER + ", P10D"
  })
  void the_rule_that_a_span_is_longer_than_nothing_names_both_readers_and_the_way_to_an_expiry(
      String setting, String anExampleOfIt) {
    String said = GrowHoldWindow.mustBeLongerThanNothing(setting, anExampleOfIt);

    assertThat(said)
        .startsWith(setting + " must be longer than nothing")
        .contains("a form states one as a number and a unit")
        .contains("an ISO-8601 duration such as " + anExampleOfIt)
        .contains("can never be captured")
        .contains("move the simulator's clock past it");
  }

  @ParameterizedTest
  @CsvSource({
    GrowHoldWindow.CAPTURE_WINDOW + ", P7D",
    GrowHoldWindow.AUTOMATIC_VOID_AFTER + ", P10D"
  })
  void the_ceiling_declares_itself_the_simulators_own_and_not_a_figure_grow_publishes(
      String setting, String anExampleOfIt) {
    String said = GrowHoldWindow.mustBeShortEnoughToTravelTo(setting, anExampleOfIt);

    assertThat(said)
        .as(
            "a published bound and an invented one are different facts, and the reason a tester"
                + " can act on is the only one that belongs in a refusal: grow's disagreeing"
                + " figures and where each is published are recorded in doc/grow.md instead")
        .startsWith(setting + " must be at most 365 days")
        .contains("this simulator's own bound and not a figure grow publishes")
        .contains("a form states one as a number and a unit")
        .contains("an ISO-8601 duration such as " + anExampleOfIt)
        .doesNotContain("Postman");
  }

  @ParameterizedTest
  @CsvSource({
    GrowHoldWindow.CAPTURE_WINDOW + ", P7D",
    GrowHoldWindow.AUTOMATIC_VOID_AFTER + ", P10D"
  })
  void the_resolution_rule_says_how_fine_the_store_keeps_a_span(
      String setting, String anExampleOfIt) {
    assertThat(GrowHoldWindow.mustBeStatedToTheMicrosecond(setting, anExampleOfIt))
        .startsWith(setting + " must be a whole number of microseconds")
        .contains("as fine as the store keeps a span")
        .contains("a form states one as a number and a unit")
        .contains("an ISO-8601 duration such as " + anExampleOfIt);
  }

  @ParameterizedTest
  @CsvSource({
    GrowHoldWindow.CAPTURE_WINDOW + ", P7D",
    GrowHoldWindow.AUTOMATIC_VOID_AFTER + ", P10D"
  })
  void the_rule_that_a_span_is_readable_names_both_readers(String setting, String anExampleOfIt) {
    assertThat(GrowHoldWindow.mustNameASpanOfTime(setting, anExampleOfIt))
        .startsWith(setting + " must name a span of time")
        .contains("a form states one as a number and a unit")
        .contains("an ISO-8601 duration such as " + anExampleOfIt);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "PT-120H", "P366D", "soon", "PT0.000000001S"})
  void every_refusal_of_a_capture_window_names_the_field_a_screen_draws_it_on(String refused) {
    assertThatCode(() -> GrowHoldWindow.of(PREFIX, refused, "P10D"))
        .as("a refusal a screen cannot attach to a box is one a clicking tester cannot act on")
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .satisfies(
            rejected ->
                assertThat(AccountProperty.theFieldNamedBy(String.valueOf(rejected.getMessage())))
                    .isEqualTo("captureWindow"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "PT-120H", "P366D", "soon", "PT0.000000001S"})
  void every_refusal_of_an_automatic_void_names_the_field_a_screen_draws_it_on(String refused) {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, "P7D", refused))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .satisfies(
            rejected ->
                assertThat(AccountProperty.theFieldNamedBy(String.valueOf(rejected.getMessage())))
                    .isEqualTo("automaticVoidAfter"));
  }

  @Test
  void the_capture_window_is_judged_before_the_automatic_void_so_one_refusal_names_one_field() {
    assertThatThrownBy(() -> GrowHoldWindow.of(PREFIX, "PT0S", "PT0S"))
        .isInstanceOf(ProviderConfigurationRejectedException.class)
        .hasMessageContaining("captureWindow")
        .hasMessageNotContaining("automaticVoidAfter");
  }

  @Test
  void the_published_default_is_a_span_the_floor_itself_admits() {
    assertThat(GrowHoldWindow.PUBLISHED_DEFAULT.captureWindow()).isEqualTo(Duration.ofDays(7));
    assertThat(GrowHoldWindow.PUBLISHED_DEFAULT.automaticVoidAfter())
        .isEqualTo(Duration.ofDays(10));
  }

  @Test
  void a_window_outside_the_floor_cannot_be_built_at_all_whichever_route_reaches_the_record() {
    assertThatCode(() -> new GrowHoldWindow(Duration.ZERO, Duration.ofDays(10)))
        .as(
            "JdbcGrowAccounts reads a stored row straight through the canonical constructor, so"
                + " of() was the only guarded door and a row written before the column was"
                + " checked walked past it")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("captureWindow is no span a hold can outlive");
    assertThatThrownBy(() -> new GrowHoldWindow(Duration.ofDays(7), Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("automaticVoidAfter is no span a hold can outlive");
  }

  @Test
  void a_negative_window_cannot_be_built_either() {
    assertThatThrownBy(() -> new GrowHoldWindow(Duration.ofDays(-5), Duration.ofDays(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("captureWindow is no span a hold can outlive");
    assertThatThrownBy(() -> new GrowHoldWindow(Duration.ofDays(7), Duration.ofDays(-5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("automaticVoidAfter is no span a hold can outlive");
  }

  @Test
  void a_window_beyond_the_ceiling_cannot_be_built_either() {
    assertThatThrownBy(() -> new GrowHoldWindow(Duration.ofDays(366), Duration.ofDays(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("captureWindow is no span a hold can outlive");
    assertThatThrownBy(() -> new GrowHoldWindow(Duration.ofDays(7), Duration.ofDays(366)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("automaticVoidAfter is no span a hold can outlive");
  }

  @Test
  void a_window_finer_than_the_store_keeps_cannot_be_built_either() {
    assertThatThrownBy(() -> new GrowHoldWindow(Duration.ofNanos(1), Duration.ofDays(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("captureWindow is finer than the store holds");
    assertThatThrownBy(() -> new GrowHoldWindow(Duration.ofDays(7), Duration.ofNanos(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("automaticVoidAfter is finer than the store holds");
  }

  @Test
  void the_span_at_each_end_of_the_floor_can_be_built() {
    assertThat(new GrowHoldWindow(Duration.ofNanos(1000), Duration.ofDays(365)))
        .isEqualTo(GrowHoldWindow.of(PREFIX, "PT0.000001S", "P365D"));
  }
}
