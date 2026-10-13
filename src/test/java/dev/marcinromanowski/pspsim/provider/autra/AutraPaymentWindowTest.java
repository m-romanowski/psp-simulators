package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraPaymentWindowTest {

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

  private static AutraPaymentWindow.Asked asked(String... query) {
    return AutraPaymentWindow.of(
        AutraWire.get(AutraApiController.PAYMENTS, Map.of(), AutraWire.query(query)), NOW);
  }

  private static AutraPaymentWindow windowOf(String... query) {
    return ((AutraPaymentWindow.Asked.Between) asked(query)).window();
  }

  private static AutraPaymentWindow.Asked.Refused refusalOf(String... query) {
    return (AutraPaymentWindow.Asked.Refused) asked(query);
  }

  @Test
  void a_call_that_names_neither_end_is_given_the_seven_days_up_to_the_present_moment() {
    AutraPaymentWindow window = windowOf();

    assertThat(window.to()).isEqualTo(Instant.parse("2026-09-22T10:00:01Z"));
    assertThat(window.from()).isEqualTo(Instant.parse("2026-09-15T10:00:01Z"));
  }

  @Test
  void the_end_a_call_that_names_none_gets_passes_the_present_moment_rather_than_stopping_on_it() {
    assertThat(AutraPaymentWindow.theEndACallThatNamesNoneGets(NOW)).isAfter(NOW);
    assertThat(windowOf().holds(NOW)).isTrue();
  }

  @Test
  void a_whole_moment_is_read_as_the_moment_it_names() {
    AutraPaymentWindow window =
        windowOf("from", "2026-09-01T08:30:00Z", "to", "2026-09-02T09:45:10Z");

    assertThat(window.from()).isEqualTo(Instant.parse("2026-09-01T08:30:00Z"));
    assertThat(window.to()).isEqualTo(Instant.parse("2026-09-02T09:45:10Z"));
  }

  @Test
  void a_bare_date_covers_the_whole_of_that_day() {
    AutraPaymentWindow window = windowOf("from", "2026-09-01", "to", "2026-09-01");

    assertThat(window.from()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    assertThat(window.to()).isEqualTo(Instant.parse("2026-09-02T00:00:00Z"));
    assertThat(window.holds(Instant.parse("2026-09-01T00:00:00Z"))).isTrue();
    assertThat(window.holds(Instant.parse("2026-09-01T23:59:59Z"))).isTrue();
    assertThat(window.holds(Instant.parse("2026-09-02T00:00:00Z"))).isFalse();
  }

  @Test
  void a_named_from_with_no_named_to_keeps_the_present_moment_as_the_end() {
    AutraPaymentWindow window = windowOf("from", "2026-09-20T00:00:00Z");

    assertThat(window.from()).isEqualTo(Instant.parse("2026-09-20T00:00:00Z"));
    assertThat(window.to()).isEqualTo(Instant.parse("2026-09-22T10:00:01Z"));
  }

  @Test
  void a_named_to_with_no_named_from_counts_the_seven_days_back_from_that_to() {
    AutraPaymentWindow window = windowOf("to", "2026-09-10T00:00:00Z");

    assertThat(window.to()).isEqualTo(Instant.parse("2026-09-10T00:00:00Z"));
    assertThat(window.from()).isEqualTo(Instant.parse("2026-09-03T00:00:00Z"));
  }

  @Test
  void the_window_holds_its_from_and_stops_before_its_to() {
    AutraPaymentWindow window =
        new AutraPaymentWindow(
            Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-09-02T00:00:00Z"));

    assertThat(window.holds(Instant.parse("2026-08-31T23:59:59Z"))).isFalse();
    assertThat(window.holds(Instant.parse("2026-09-01T00:00:00Z"))).isTrue();
    assertThat(window.holds(Instant.parse("2026-09-01T23:59:59Z"))).isTrue();
    assertThat(window.holds(Instant.parse("2026-09-02T00:00:00Z"))).isFalse();
  }

  @Test
  void a_from_that_is_no_moment_is_refused_as_invalid_from() {
    AutraPaymentWindow.Asked.Refused refused = refusalOf("from", "yesterday");

    assertThat(refused.code()).isEqualTo(AutraErrors.INVALID_FROM);
    assertThat(refused.msg()).isEqualTo(AutraPaymentWindow.A_MOMENT_IS_RFC3339_OR_A_BARE_DATE);
  }

  @Test
  void a_to_that_is_no_moment_is_refused_as_invalid_to_before_the_from_is_even_read() {
    AutraPaymentWindow.Asked.Refused refused = refusalOf("from", "yesterday", "to", "tomorrow");

    assertThat(refused.code()).isEqualTo(AutraErrors.INVALID_TO);
  }

  @Test
  void a_bare_date_naming_no_day_of_any_month_is_refused() {
    assertThat(refusalOf("from", "2026-02-31").code()).isEqualTo(AutraErrors.INVALID_FROM);
    assertThat(refusalOf("to", "2026-13-01").code()).isEqualTo(AutraErrors.INVALID_TO);
  }

  @Test
  void a_from_after_its_to_is_refused_as_invalid_range_and_one_equal_to_it_is_not() {
    AutraPaymentWindow.Asked.Refused refused =
        refusalOf("from", "2026-09-20T00:00:01Z", "to", "2026-09-20T00:00:00Z");

    assertThat(refused.code()).isEqualTo(AutraErrors.INVALID_RANGE);
    assertThat(refused.msg()).isEqualTo(AutraPaymentWindow.THE_WINDOW_RUNS_FORWARDS);
    AutraPaymentWindow empty =
        windowOf("from", "2026-09-20T00:00:00Z", "to", "2026-09-20T00:00:00Z");
    assertThat(empty.holds(Instant.parse("2026-09-20T00:00:00Z"))).isFalse();
  }

  @Test
  void thirty_one_days_is_taken_and_a_second_more_than_that_is_refused_as_range_too_large() {
    assertThat(asked("from", "2026-08-22T10:00:00Z", "to", "2026-09-22T10:00:00Z"))
        .isInstanceOf(AutraPaymentWindow.Asked.Between.class);

    AutraPaymentWindow.Asked.Refused refused =
        refusalOf("from", "2026-08-22T09:59:59Z", "to", "2026-09-22T10:00:00Z");

    assertThat(refused.code()).isEqualTo(AutraErrors.RANGE_TOO_LARGE);
    assertThat(refused.msg()).isEqualTo(AutraPaymentWindow.THE_WINDOW_IS_AT_MOST_A_MONTH);
  }

  @Test
  void the_bounds_this_route_enforces_are_the_numbers_the_vendor_publishes() {
    assertThat(AutraPaymentWindow.LONGEST_WINDOW_IN_DAYS).isEqualTo(31);
    assertThat(AutraPaymentWindow.THE_WINDOW_A_CALL_THAT_NAMES_NONE_GETS).isEqualTo(7);
  }

  @Test
  void a_window_writes_each_of_its_ends_the_way_provider_autra_writes_a_moment() {
    AutraPaymentWindow window =
        new AutraPaymentWindow(
            Instant.parse("2026-09-01T08:30:00Z"), Instant.parse("2026-09-02T09:45:10Z"));

    assertThat(window.fromAsAutraWritesIt()).isEqualTo("2026-09-01T08:30:00Z");
    assertThat(window.toAsAutraWritesIt()).isEqualTo("2026-09-02T09:45:10Z");
  }

  @Test
  void a_parameter_sent_blank_counts_as_one_the_call_did_not_name() {
    AutraPaymentWindow.Asked carried =
        AutraPaymentWindow.of(
            AutraWire.get(
                AutraApiController.PAYMENTS,
                Map.of(),
                Map.of(AutraFields.FROM, List.of(""), AutraFields.TO, List.of(""))),
            NOW);

    assertThat(((AutraPaymentWindow.Asked.Between) carried).window().to())
        .isEqualTo(Instant.parse("2026-09-22T10:00:01Z"));
  }
}
