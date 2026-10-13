package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditQueryTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final Instant NOON = Instant.parse("2026-09-07T12:00:00Z");

  @Test
  void a_read_that_names_no_test_run_reads_the_default_namespace() {
    assertThat(queryOf(nothing()).testRunId()).isEqualTo(DEFAULT_RUN);
  }

  @Test
  void a_read_can_address_the_namespace_a_failed_resolution_was_recorded_under() {
    assertThat(queryOf(testRun("UNKNOWN")).testRunId()).isEqualTo(TestRunId.UNKNOWN);
  }

  @Test
  void a_test_run_id_that_is_not_a_test_run_id_is_refused_rather_than_read_as_a_namespace() {
    DeclaredAuditQuery declared = testRun("no spaces allowed");

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("testRunId");
  }

  @Test
  void a_direction_names_one_of_the_three_kinds_of_record_the_trail_holds() {
    assertThat(queryOf(direction("OUTBOUND_RESPONSE")).direction())
        .isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
  }

  @Test
  void a_direction_matches_however_the_harness_cased_it_just_as_a_method_does() {
    assertThat(queryOf(direction("inbound_request")).direction())
        .isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(queryOf(method("post")).method()).isEqualTo("POST");
  }

  @Test
  void a_direction_outside_the_published_vocabulary_is_refused() {
    DeclaredAuditQuery declared = direction("INBOUND");

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "direction must be one of INBOUND_REQUEST, OUTBOUND_RESPONSE,"
                + " OUTBOUND_WEBHOOK_ATTEMPT, OUTBOUND_WEBHOOK_DROPPED,"
                + " OUTBOUND_WEBHOOK_NOT_SENT");
  }

  @Test
  void the_directions_a_reader_is_offered_are_every_direction_this_trail_can_hold() {
    assertThat(AuditQuery.theDirectionsThisTrailHolds())
        .containsExactly(
            "INBOUND_REQUEST",
            "OUTBOUND_RESPONSE",
            "OUTBOUND_WEBHOOK_ATTEMPT",
            "OUTBOUND_WEBHOOK_DROPPED",
            "OUTBOUND_WEBHOOK_NOT_SENT");
  }

  @Test
  void a_callback_a_rule_dropped_is_a_direction_a_reader_may_filter_the_trail_by() {
    assertThat(queryOf(direction("OUTBOUND_WEBHOOK_DROPPED")).direction())
        .isEqualTo(AuditDirection.OUTBOUND_WEBHOOK_DROPPED);
  }

  @Test
  void
      a_harness_reading_back_only_what_it_asked_the_control_api_for_filters_the_trail_by_surface() {
    assertThat(queryOf(origin("CONTROL_API")).origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(queryOf(origin("ui")).origin()).isEqualTo(ObjectOrigin.UI);
  }

  @Test
  void a_surface_outside_the_published_vocabulary_is_refused() {
    DeclaredAuditQuery declared = origin("SCREEN");

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("origin must be one of PROVIDER_API, CONTROL_API, UI, SCHEDULED_JOB");
  }

  @Test
  void the_surfaces_a_reader_is_offered_are_every_surface_an_object_can_be_created_by() {
    assertThat(AuditQuery.theOriginsThisTrailHolds())
        .containsExactly("PROVIDER_API", "CONTROL_API", "UI", "SCHEDULED_JOB");
  }

  @Test
  void a_status_no_http_exchange_could_have_travelled_under_is_refused() {
    DeclaredAuditQuery declared = status("42");

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("status must be an HTTP status");
  }

  @Test
  void a_status_that_is_not_a_number_is_refused_without_repeating_what_was_sent() {
    DeclaredAuditQuery declared = status("two hundred");

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("status must be a whole number");
  }

  @Test
  void a_status_is_refused_only_where_no_http_exchange_could_have_travelled_under_it() {
    assertThat(queryOf(status("100")).httpStatus()).isEqualTo(100);
    assertThat(queryOf(status("599")).httpStatus()).isEqualTo(599);
    DeclaredAuditQuery belowTheFirst = status("99");
    DeclaredAuditQuery aboveTheLast = status("600");

    assertThatThrownBy(() -> queryOf(belowTheFirst))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("status must be an HTTP status");
    assertThatThrownBy(() -> queryOf(aboveTheLast))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("status must be an HTTP status");
  }

  @Test
  void a_time_range_is_read_as_the_two_instants_that_bound_it() {
    AuditQuery query = queryOf(range("2026-09-07T12:00:00Z", "2026-09-07T12:00:01Z"));

    assertThat(query.from()).isEqualTo(NOON);
    assertThat(query.until()).isEqualTo(NOON.plusSeconds(1));
  }

  @Test
  void a_range_that_ends_where_it_starts_is_refused_rather_than_answered_empty() {
    DeclaredAuditQuery declared = range("2026-09-07T12:00:00Z", "2026-09-07T12:00:00Z");

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("until must be later than from");
  }

  @Test
  void a_range_that_ends_before_it_starts_is_refused_rather_than_answered_empty() {
    DeclaredAuditQuery declared = range("2026-09-07T12:00:01Z", "2026-09-07T12:00:00Z");

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("until must be later than from");
  }

  @Test
  void a_time_that_is_not_an_instant_is_refused_without_repeating_what_was_sent() {
    DeclaredAuditQuery notAnInstantAsFrom = from("yesterday");
    DeclaredAuditQuery notAnInstantAsUntil = until("tomorrow");

    assertThatThrownBy(() -> queryOf(notAnInstantAsFrom))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("from must be an ISO-8601 instant such as 2026-09-07T12:00:00Z");
    assertThatThrownBy(() -> queryOf(notAnInstantAsUntil))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("until must be an ISO-8601 instant such as 2026-09-07T12:00:00Z");
  }

  @Test
  void a_read_that_names_no_page_size_reads_the_page_the_route_publishes_as_its_default() {
    assertThat(queryOf(nothing()).limit()).isEqualTo(AuditQuery.DEFAULT_LIMIT);
  }

  @Test
  void a_page_that_asks_for_no_rows_at_all_is_refused() {
    DeclaredAuditQuery declared = limit("0");

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be between 1 and " + AuditQuery.LARGEST_LIMIT);
  }

  @Test
  void a_page_beyond_the_published_ceiling_is_refused() {
    DeclaredAuditQuery declared = limit(Integer.toString(AuditQuery.LARGEST_LIMIT + 1));

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be between 1 and " + AuditQuery.LARGEST_LIMIT);
  }

  @Test
  void a_page_bound_is_honoured_at_both_ends_of_the_range_it_publishes() {
    assertThat(queryOf(limit("1")).limit()).isEqualTo(1);
    assertThat(queryOf(limit(Integer.toString(AuditQuery.LARGEST_LIMIT))).limit())
        .isEqualTo(AuditQuery.LARGEST_LIMIT);
  }

  @Test
  void a_page_size_that_is_not_a_number_is_refused_without_repeating_what_was_sent() {
    DeclaredAuditQuery declared = limit("all of them");

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be a whole number");
  }

  @Test
  void a_filter_is_carried_with_the_whitespace_a_harness_wrapped_it_in_taken_off() {
    AuditQuery query = queryOf(objectAndScenario(" 734754 ", " scn_capture "));

    assertThat(query.providerObjectIds()).containsExactly("734754");
    assertThat(query.scenarioId()).isEqualTo("scn_capture");
  }

  @Test
  void refuses_a_narrowing_by_an_identifier_no_row_could_carry() {
    List<String> oneBlankIdentifier = List.of("734754", " ");

    assertThatThrownBy(
            () ->
                new AuditQuery(
                    DEFAULT_RUN,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    oneBlankIdentifier,
                    null,
                    null,
                    null,
                    null,
                    AuditQuery.DEFAULT_LIMIT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("identifier 1 is blank");
  }

  @Test
  void a_filter_given_with_no_value_at_all_narrows_nothing_the_way_an_absent_one_does() {
    AuditQuery query =
        queryOf(new DeclaredAuditQuery("", "", "", "", "", "", "", "", "", "", "", null, ""));

    assertThat(query.testRunId()).isEqualTo(DEFAULT_RUN);
    assertThat(query.auditId()).isNull();
    assertThat(query.accountId()).isNull();
    assertThat(query.origin()).isNull();
    assertThat(query.direction()).isNull();
    assertThat(query.method()).isNull();
    assertThat(query.httpStatus()).isNull();
    assertThat(query.providerObjectIds()).isEmpty();
    assertThat(query.scenarioId()).isNull();
    assertThat(query.from()).isNull();
    assertThat(query.until()).isNull();
    assertThat(query.limit()).isEqualTo(AuditQuery.DEFAULT_LIMIT);
  }

  @Test
  void a_cursor_given_with_no_value_at_all_names_no_row_rather_than_silently_starting_again() {
    assertThat(queryOf(after("")).after()).isEmpty();
    assertThat(queryOf(after("aud_2")).after()).isEqualTo("aud_2");
    assertThat(queryOf(nothing()).after()).isNull();
  }

  @Test
  void a_read_can_narrow_the_trail_to_the_one_exchange_an_audit_id_was_recorded_under() {
    assertThat(queryOf(exchange("  aud_7 ")).auditId()).isEqualTo("aud_7");
    assertThat(queryOf(exchange("  ")).auditId()).isNull();
    assertThat(queryOf(nothing()).auditId()).isNull();
  }

  @Test
  void a_declared_blank_exchange_narrows_nothing_and_never_reaches_the_refusal_of_a_built_one() {
    assertThat(queryOf(exchange("   ")).auditId()).isNull();
    assertThatThrownBy(() -> theQueryNarrowedTo("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("auditId is blank");
  }

  @Test
  void a_narrowing_to_an_exchange_wider_than_a_row_holds_is_refused_by_the_read_that_declared_it() {
    DeclaredAuditQuery declared = exchange("a".repeat(65));

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("auditId must be at most 64 characters");
  }

  @Test
  void a_refusal_of_an_exchange_wider_than_a_row_holds_never_repeats_the_id_that_was_sent() {
    String tooWide = "z".repeat(4000);

    assertThatThrownBy(() -> queryOf(exchange(tooWide)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("zz")
        .hasMessageContaining("an exchange no row could carry");
  }

  @Test
  void an_exchange_as_wide_as_a_row_holds_is_carried_as_a_narrowing_rather_than_refused() {
    String asWideAsARowHolds = "a".repeat(64);

    assertThat(queryOf(exchange(asWideAsARowHolds)).auditId()).isEqualTo(asWideAsARowHolds);
  }

  private static AuditQuery theQueryNarrowedTo(String auditId) {
    return new AuditQuery(
        DEFAULT_RUN,
        auditId,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        null,
        null,
        null,
        AuditQuery.DEFAULT_LIMIT);
  }

  @Test
  void a_read_can_narrow_the_trail_to_the_rows_of_one_account_by_the_identity_a_tester_typed() {
    assertThat(queryOf(account("  merchant-1 ")).accountId()).isEqualTo("merchant-1");
    assertThat(queryOf(nothing()).accountId()).isNull();
  }

  @Test
  void a_narrowing_to_a_blank_account_is_refused_rather_than_kept_as_a_narrowing_to_every_row() {
    assertThatThrownBy(
            () ->
                new AuditQuery(
                    DEFAULT_RUN,
                    null,
                    " ",
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    AuditQuery.DEFAULT_LIMIT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountId is blank");
  }

  @Test
  void a_narrowing_to_an_account_wider_than_a_row_holds_is_refused_by_the_read_that_declared_it() {
    DeclaredAuditQuery declared = account("a".repeat(129));

    assertThatThrownBy(() -> queryOf(declared))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountId must be at most 128 characters");
  }

  private static AuditQuery queryOf(DeclaredAuditQuery declared) {
    return AuditQuery.of(declared, DEFAULT_RUN);
  }

  private static DeclaredAuditQuery nothing() {
    return new DeclaredAuditQuery(
        null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static DeclaredAuditQuery exchange(String value) {
    return new DeclaredAuditQuery(
        null, value, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static DeclaredAuditQuery account(String value) {
    return new DeclaredAuditQuery(
        null, null, value, null, null, null, null, null, null, null, null, null, null);
  }

  private static DeclaredAuditQuery testRun(String value) {
    return new DeclaredAuditQuery(
        value, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  private static DeclaredAuditQuery direction(String value) {
    return new DeclaredAuditQuery(
        null, null, null, null, value, null, null, null, null, null, null, null, null);
  }

  private static DeclaredAuditQuery origin(String value) {
    return new DeclaredAuditQuery(
        null, null, null, value, null, null, null, null, null, null, null, null, null);
  }

  private static DeclaredAuditQuery method(String value) {
    return new DeclaredAuditQuery(
        null, null, null, null, null, value, null, null, null, null, null, null, null);
  }

  private static DeclaredAuditQuery status(String value) {
    return new DeclaredAuditQuery(
        null, null, null, null, null, null, value, null, null, null, null, null, null);
  }

  private static DeclaredAuditQuery objectAndScenario(String object, String scenario) {
    return new DeclaredAuditQuery(
        null, null, null, null, null, null, null, object, scenario, null, null, null, null);
  }

  private static DeclaredAuditQuery from(String value) {
    return new DeclaredAuditQuery(
        null, null, null, null, null, null, null, null, null, value, null, null, null);
  }

  private static DeclaredAuditQuery until(String value) {
    return new DeclaredAuditQuery(
        null, null, null, null, null, null, null, null, null, null, value, null, null);
  }

  private static DeclaredAuditQuery range(String start, String end) {
    return new DeclaredAuditQuery(
        null, null, null, null, null, null, null, null, null, start, end, null, null);
  }

  private static DeclaredAuditQuery after(String value) {
    return new DeclaredAuditQuery(
        null, null, null, null, null, null, null, null, null, null, null, value, null);
  }

  private static DeclaredAuditQuery limit(String value) {
    return new DeclaredAuditQuery(
        null, null, null, null, null, null, null, null, null, null, null, null, value);
  }
}
