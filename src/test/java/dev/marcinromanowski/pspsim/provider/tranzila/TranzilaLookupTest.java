package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TranzilaLookupTest {

  private static final String DATES =
      ",\"transaction_start_date\":\"2026-10-01\",\"transaction_end_date\":\"2026-10-31\"";

  @Test
  void the_api_reads_an_index_a_thousand_rows_a_page_oldest_first_and_no_items() {
    TranzilaLookup read = api(",\"transaction_index\":1696");

    assertThat(read)
        .isEqualTo(new TranzilaLookup("myterminal", 1696L, null, null, 1, 1000, false, false));
    assertThat(read.offset()).isZero();
  }

  @Test
  void the_api_reads_a_range_of_two_calendar_dates_and_a_page_a_thousand_rows_on() {
    TranzilaLookup read = api(DATES + ",\"page\":3");

    assertThat(read.start()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(read.end()).isEqualTo(LocalDate.of(2026, 10, 31));
    assertThat(read.page()).isEqualTo(3);
    assertThat(read.offset()).isEqualTo(2000);
    assertThat(read.window())
        .isEqualTo(
            new TranzilaLookup.Window(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1, 2000, 1000));
  }

  @Test
  void the_report_reads_newest_first_a_thousand_rows_and_no_items_unless_asked() {
    TranzilaLookup read = report(DATES);

    assertThat(read)
        .isEqualTo(
            new TranzilaLookup(
                "myterminal",
                null,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                1,
                1000,
                true,
                false));
    assertThat(read.window().direction()).isEqualTo(-1);
  }

  @Test
  void the_report_reads_its_page_size_its_direction_and_whether_items_are_wanted() {
    TranzilaLookup read =
        report(
            DATES
                + ",\"detailed\":\"Y\",\"page\":2,\"page_results\":25,"
                + "\"order_direction\":\"asc\",\"ufields\":[],\"dfields\":[]");

    assertThat(read.detailed()).isTrue();
    assertThat(read.newestFirst()).isFalse();
    assertThat(read.window())
        .isEqualTo(
            new TranzilaLookup.Window(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 1, 25, 25));
    assertThat(report(DATES + ",\"detailed\":\"N\"").detailed()).isFalse();
    assertThat(report(DATES + ",\"order_direction\":\"desc\"").newestFirst()).isTrue();
    assertThat(report(DATES + ",\"page_results\":1").pageResults()).isEqualTo(1);
    assertThat(report(DATES + ",\"page_results\":1000").pageResults()).isEqualTo(1000);
  }

  @Test
  void a_field_outside_each_schema_is_refused_naming_the_schema() {
    assertThatThrownBy(() -> api(DATES + ",\"detailed\":\"Y\""))
        .isInstanceOf(TranzilaSchemaMismatch.class)
        .hasMessage("detailed is not a field of the TransactionsBody schema.");
    assertThatThrownBy(() -> report(DATES + ",\"filters\":[]"))
        .isInstanceOf(TranzilaSchemaMismatch.class)
        .hasMessage("filters is not a field of the TransactionRequest schema.");
  }

  @Test
  void a_terminal_name_missing_blank_or_not_text_is_required() {
    assertThatThrownBy(() -> TranzilaLookup.ofTheApi(body("{\"transaction_index\":1}")))
        .isInstanceOf(TranzilaSchemaMismatch.class)
        .hasMessage("terminal_name is required.");
    assertThatThrownBy(
            () ->
                TranzilaLookup.ofTheReport(
                    body("{\"terminal_name\":\" \",\"transaction_index\":1}")))
        .hasMessage("terminal_name is required.");
    assertThatThrownBy(
            () -> TranzilaLookup.ofTheApi(body("{\"terminal_name\":7,\"transaction_index\":1}")))
        .hasMessage("terminal_name is required.");
  }

  @Test
  void an_index_and_a_range_exclude_each_other_and_one_of_them_is_required() {
    assertThatThrownBy(() -> api(DATES + ",\"transaction_index\":1"))
        .hasMessage(
            "Provide either transaction_index or both transaction_start_date and"
                + " transaction_end_date, not both.");
    assertThatThrownBy(
            () -> report(",\"transaction_index\":1,\"transaction_end_date\":\"2026-10-01\""))
        .hasMessage(TranzilaLookup.NOT_BOTH);
    assertThatThrownBy(() -> api(""))
        .hasMessage(
            "transaction_index, or both transaction_start_date and transaction_end_date, is"
                + " required.");
    assertThatThrownBy(() -> api(",\"transaction_start_date\":\"2026-10-01\""))
        .hasMessage(TranzilaLookup.ONE_OF_THEM);
    assertThatThrownBy(() -> report(",\"transaction_end_date\":\"2026-10-01\""))
        .hasMessage(TranzilaLookup.ONE_OF_THEM);
  }

  @Test
  void an_index_is_a_whole_number_of_at_most_eighteen_digits() {
    assertThat(api(",\"transaction_index\":-3").index()).isEqualTo(-3L);
    assertThat(api(",\"transaction_index\":999999999999999999").index())
        .isEqualTo(999999999999999999L);
    assertThatThrownBy(() -> api(",\"transaction_index\":1.5"))
        .hasMessage("transaction_index must be an integer.");
    assertThatThrownBy(() -> api(",\"transaction_index\":\"1\""))
        .hasMessage("transaction_index must be an integer.");
    assertThatThrownBy(() -> api(",\"transaction_index\":1000000000000000000"))
        .hasMessage("transaction_index must be an integer.");
  }

  @Test
  void a_date_is_a_calendar_day_written_with_four_two_and_two_digits_from_year_one() {
    assertThat(
            api(",\"transaction_start_date\":\"0001-01-01\",\"transaction_end_date\":\"9999-12-31\"")
                .end())
        .isEqualTo(LocalDate.of(9999, 12, 31));
    assertThat(
            api(",\"transaction_start_date\":\"2028-02-29\",\"transaction_end_date\":\"2028-02-29\"")
                .start())
        .isEqualTo(LocalDate.of(2028, 2, 29));
    for (String refused :
        List.of(
            "\"2026-02-30\"",
            "\"0000-01-01\"",
            "\"2026-1-01\"",
            "\"2026-10-01T00:00\"",
            "20261001")) {
      assertThatThrownBy(
              () ->
                  api(
                      ",\"transaction_start_date\":"
                          + refused
                          + ",\"transaction_end_date\":\"2026-10-01\""))
          .as(refused)
          .hasMessage(
              "transaction_start_date must be a calendar date written YYYY-MM-DD, from"
                  + " 0001-01-01.");
    }
    assertThatThrownBy(
            () ->
                api(
                    ",\"transaction_start_date\":\"2026-10-01\","
                        + "\"transaction_end_date\":\"2026-13-01\""))
        .hasMessage(
            "transaction_end_date must be a calendar date written YYYY-MM-DD, from 0001-01-01.");
  }

  @Test
  void a_page_is_a_whole_number_from_one_to_the_largest_integer() {
    assertThat(api(DATES + ",\"page\":1").page()).isEqualTo(1);
    assertThat(api(DATES + ",\"page\":2147483647").offset()).isEqualTo(2147483646000L);
    assertThatThrownBy(() -> api(DATES + ",\"page\":0"))
        .hasMessage("page must be a whole number from 1 to 2147483647.");
    assertThatThrownBy(() -> api(DATES + ",\"page\":2147483648"))
        .hasMessage("page must be a whole number from 1 to 2147483647.");
    assertThatThrownBy(() -> api(DATES + ",\"page\":\"2\"")).hasMessage("page must be an integer.");
  }

  @Test
  void the_report_takes_one_to_a_thousand_rows_a_page_and_only_its_published_words() {
    assertThatThrownBy(() -> report(DATES + ",\"page_results\":0"))
        .hasMessage("page_results must be a whole number from 1 to 1000.");
    assertThatThrownBy(() -> report(DATES + ",\"page_results\":1001"))
        .hasMessage("page_results must be a whole number from 1 to 1000.");
    assertThatThrownBy(() -> report(DATES + ",\"detailed\":\"y\""))
        .hasMessage("detailed must be Y or N.");
    assertThatThrownBy(() -> report(DATES + ",\"detailed\":true"))
        .hasMessage("detailed must be Y or N.");
    assertThatThrownBy(() -> report(DATES + ",\"order_direction\":\"DESC\""))
        .hasMessage("order_direction must be desc or asc.");
  }

  @Test
  void a_filter_is_refused_rather_than_ignored_and_an_empty_one_filters_nothing() {
    assertThatThrownBy(
            () ->
                report(
                    DATES
                        + ",\"ufields\":[{\"name\":\"a\",\"operator\":\"equals\",\"value\":\"b\"}]"))
        .hasMessage(
            "ufields filters are not served by this simulator, so they are refused rather than"
                + " ignored: an ignored filter would answer rows the caller excluded.");
    assertThatThrownBy(
            () ->
                report(
                    DATES
                        + ",\"dfields\":[{\"name\":\"amount\",\"operator\":\">\",\"value\":\"1\"}]"))
        .hasMessage("dfields" + TranzilaLookup.NOT_SERVED);
    assertThatThrownBy(() -> report(DATES + ",\"ufields\":{}"))
        .hasMessage("ufields must be an array.");
    assertThatThrownBy(() -> report(DATES + ",\"dfields\":\"amount\""))
        .hasMessage("dfields must be an array.");
  }

  @Test
  void a_lookup_by_index_has_no_window_and_pages_what_it_found() {
    TranzilaLookup first = report(",\"transaction_index\":5,\"page_results\":2");
    TranzilaLookup second = report(",\"transaction_index\":5,\"page_results\":2,\"page\":2");
    TranzilaLookup third = report(",\"transaction_index\":5,\"page_results\":2,\"page\":3");

    assertThatThrownBy(first::window)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("a lookup by transaction_index has no window of dates");
    assertThat(first.thePageOf(List.of("a", "b", "c"))).containsExactly("a", "b");
    assertThat(second.thePageOf(List.of("a", "b", "c"))).containsExactly("c");
    assertThat(third.thePageOf(List.of("a", "b", "c"))).isEmpty();
    assertThat(api(",\"transaction_index\":5,\"page\":2147483647").thePageOf(List.of("a")))
        .isEmpty();
  }

  private static TranzilaLookup api(String criteria) {
    return TranzilaLookup.ofTheApi(body("{\"terminal_name\":\"myterminal\"" + criteria + "}"));
  }

  private static TranzilaLookup report(String criteria) {
    return TranzilaLookup.ofTheReport(body("{\"terminal_name\":\"myterminal\"" + criteria + "}"));
  }

  private static TranzilaJson.JsonObject body(String json) {
    return TranzilaJsonReader.objectIn(json).orElseThrow();
  }
}
