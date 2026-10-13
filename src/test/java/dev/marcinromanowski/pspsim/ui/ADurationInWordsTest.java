package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ADurationInWordsTest {

  @ParameterizedTest
  @CsvSource({
    "PT168H, 7, DAYS, 7 days",
    "P7D, 7, DAYS, 7 days",
    "P1D, 1, DAYS, 1 day",
    "PT1H, 1, HOURS, 1 hour",
    "PT36H, 36, HOURS, 36 hours",
    "PT5400S, 90, MINUTES, 90 minutes",
    "PT6M, 6, MINUTES, 6 minutes",
    "PT90S, 90, SECONDS, 90 seconds",
    "PT1S, 1, SECONDS, 1 second",
    "PT0S, 0, SECONDS, 0 seconds"
  })
  void a_span_is_read_as_the_largest_unit_that_counts_it_without_a_remainder(
      String written, long amount, ADurationInWords.Unit unit, String inWords) {
    Optional<ADurationInWords> read = ADurationInWords.read(written);

    assertThat(read).isPresent();
    assertThat(read.get().amount()).isEqualTo(amount);
    assertThat(read.get().unit()).isEqualTo(unit);
    assertThat(read.get().inWords()).isEqualTo(inWords);
  }

  @Test
  void a_span_read_into_a_number_and_a_unit_and_stated_back_is_the_string_it_was_read_from() {
    List<String> stored = new ArrayList<>();
    for (long seconds : theSecondsWorthProbing()) {
      stored.add(Duration.ofSeconds(seconds).toString());
    }

    List<String> statedBack = new ArrayList<>();
    for (String value : stored) {
      ADurationInWords read =
          ADurationInWords.read(value)
              .orElseThrow(() -> new AssertionError(value + " is a span the form cannot state"));
      statedBack.add(
          ADurationInWords.theIsoFormOf(Long.toString(read.amount()), read.unit().name()));
    }

    assertThat(statedBack)
        .as(
            "a form that comes back after a refusal, and a screen that reads an account back,"
                + " both state a stored span as a number and a unit; a span that came back"
                + " rounded would change a value nobody touched. This is a sample of %s spans"
                + " and not a proof over all of them: every whole day to a year, every hour to"
                + " two days, every minute to two hours, and a stride of 997 seconds",
            stored.size())
        .isEqualTo(stored);
  }

  @Test
  void
      the_largest_unit_is_chosen_so_a_week_reads_as_days_rather_than_as_the_hours_it_is_stored_as() {
    String stored = Duration.ofDays(7).toString();

    assertThat(stored)
        .as("java normalises a span of days into hours, which is what the column holds")
        .isEqualTo("PT168H");
    assertThat(ADurationInWords.read(stored).map(ADurationInWords::inWords)).contains("7 days");
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0.5S", "PT1.000000001S"})
  void a_span_with_a_fraction_of_a_second_is_not_stated_in_a_whole_number_of_any_unit(
      String written) {
    assertThat(ADurationInWords.read(written))
        .as(
            "a control the simulator cannot state this span in exactly would read it back as"
                + " something else, and a form that rounded what it was handed would change a"
                + " value a tester never touched")
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT-72H", "-PT1S"})
  void a_negative_span_is_not_stated_in_a_unit_a_form_counts_upwards_in(String written) {
    assertThat(ADurationInWords.read(written)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"seven days", "", "   ", "P7"})
  void anything_that_is_no_span_at_all_is_read_as_no_span(String written) {
    assertThat(ADurationInWords.read(written)).isEmpty();
  }

  @Test
  void nothing_at_all_is_read_as_no_span() {
    assertThat(ADurationInWords.read(null)).isEmpty();
  }

  @Test
  void the_units_a_tester_picks_from_run_from_the_shortest_to_the_longest() {
    assertThat(ADurationInWords.theUnitsATesterPicksFrom())
        .containsExactly(
            ADurationInWords.Unit.SECONDS,
            ADurationInWords.Unit.MINUTES,
            ADurationInWords.Unit.HOURS,
            ADurationInWords.Unit.DAYS);
    assertThat(ADurationInWords.theUnitsATesterPicksFrom())
        .extracting(ADurationInWords.Unit::inThePlural)
        .containsExactly("seconds", "minutes", "hours", "days");
  }

  @ParameterizedTest
  @CsvSource({"SECONDS, 1", "MINUTES, 60", "HOURS, 3600", "DAYS, 86400"})
  void a_unit_counts_the_seconds_a_form_multiplies_the_number_a_tester_states_by(
      ADurationInWords.Unit unit, long secondsEach) {
    assertThat(unit.secondsEach()).isEqualTo(secondsEach);
    assertThat(ADurationInWords.theUnitNamed(unit.name())).contains(unit);
  }

  @ParameterizedTest
  @ValueSource(strings = {"fortnights", "days", "DAY"})
  void a_unit_the_form_never_offered_names_no_unit(String named) {
    assertThat(ADurationInWords.theUnitNamed(named)).isEmpty();
  }

  @Test
  void no_unit_at_all_names_no_unit() {
    assertThat(ADurationInWords.theUnitNamed(null)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "7, DAYS, PT168H",
    "1, DAYS, PT24H",
    "90, MINUTES, PT1H30M",
    "6, MINUTES, PT6M",
    "0, SECONDS, PT0S",
    "365, DAYS, PT8760H"
  })
  void a_number_and_a_unit_are_sent_as_the_span_the_two_of_them_name(
      String amount, String unit, String sent) {
    assertThat(ADurationInWords.theIsoFormOf(amount, unit)).isEqualTo(sent);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void a_number_nobody_stated_sends_nothing_so_the_provider_decides_what_an_empty_field_means(
      String amount) {
    assertThat(ADurationInWords.theIsoFormOf(amount, "DAYS")).isEmpty();
  }

  @Test
  void a_form_that_sent_no_number_at_all_names_no_span_rather_than_an_empty_one() {
    assertThat(ADurationInWords.theIsoFormOf(null, "DAYS"))
        .as(
            "a caller that names no value at all is not a caller that emptied the box, and what"
                + " refuses it says so by naming nothing")
        .isNull();
  }

  @Test
  void the_units_a_form_offers_mark_the_one_it_opens_on_and_no_other() {
    List<ADurationInWords.Offered> offered =
        ADurationInWords.theUnitsOffered(ADurationInWords.Unit.HOURS);

    assertThat(offered)
        .extracting(ADurationInWords.Offered::value)
        .containsExactly("SECONDS", "MINUTES", "HOURS", "DAYS");
    assertThat(offered)
        .extracting(ADurationInWords.Offered::whatItMeans)
        .containsExactly("seconds", "minutes", "hours", "days");
    assertThat(offered)
        .filteredOn(ADurationInWords.Offered::chosen)
        .extracting(ADurationInWords.Offered::value)
        .containsExactly("HOURS");
  }

  @ParameterizedTest
  @CsvSource({
    "P7D, , P7D",
    "P7D, fortnights, P7D",
    "P7D, DAYS, P7D",
    "seven, DAYS, seven",
    "PT0.0000009S, SECONDS, PT0.0000009S"
  })
  void a_value_this_control_cannot_count_is_sent_on_as_it_was_written_for_the_provider_to_judge(
      String amount, String unit, String sent) {
    assertThat(ADurationInWords.theIsoFormOf(amount, unit))
        .as(
            "the provider is the only validator of what a span may be, so a value no number and"
                + " unit produced travels to it unchanged rather than being refused here; a"
                + " tester who pastes the ISO form they used yesterday has it taken as it is,"
                + " and a word reaches the refusal written for whoever wrote it")
        .isEqualTo(sent);
  }

  @Test
  void a_number_longer_than_any_span_is_sent_on_as_it_was_typed_rather_than_overflowing_into_one() {
    assertThat(ADurationInWords.theIsoFormOf(Long.toString(Long.MAX_VALUE), "DAYS"))
        .isEqualTo(Long.toString(Long.MAX_VALUE));
  }

  @Test
  void
      a_span_read_from_a_duration_the_simulator_holds_is_the_same_span_as_one_read_from_its_text() {
    assertThat(ADurationInWords.of(Duration.ofMinutes(2)))
        .contains(new ADurationInWords(2, ADurationInWords.Unit.MINUTES));
    assertThat(ADurationInWords.of(Duration.ofMillis(500))).isEmpty();
  }

  private static List<Long> theSecondsWorthProbing() {
    List<Long> seconds = new ArrayList<>();
    seconds.add(0L);
    for (long each = 1; each <= 100000; each += 997) {
      seconds.add(each);
    }
    for (long days = 1; days <= 365; days++) {
      seconds.add(days * 24 * 60 * 60);
    }
    for (long hours = 1; hours <= 48; hours++) {
      seconds.add(hours * 60 * 60);
    }
    for (long minutes = 1; minutes <= 120; minutes++) {
      seconds.add(minutes * 60);
    }
    return List.copyOf(seconds);
  }
}
