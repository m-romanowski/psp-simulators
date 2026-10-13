package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuditScreenTest {

  private static final Instant AT = Instant.parse("2026-09-07T12:00:00Z");
  private static final TestRunId RUN = TestRunId.of("ui-audit-screen");
  private static final String NARROWED_TO_ONE_EXCHANGE = "aud_1";
  private static final String EMAIL = "payer@example.test";

  @Test
  void a_relink_carries_no_cursor_at_all_when_none_was_ever_named() {
    assertThat(AuditScreen.theCursorARelinkShouldCarry(null)).isEmpty();
  }

  @Test
  void a_relink_carries_no_cursor_at_all_when_the_one_named_was_blank() {
    assertThat(AuditScreen.theCursorARelinkShouldCarry("  ")).isEmpty();
  }

  @Test
  void a_relink_carries_the_cursor_a_page_was_actually_reached_by() {
    assertThat(AuditScreen.theCursorARelinkShouldCarry("aud_7")).containsExactly("aud_7");
  }

  @Test
  void the_filter_offers_every_direction_the_trail_can_hold_and_not_a_list_written_beside_it() {
    Filters filters = new Filters(null, null, null, null, null, null, null, null, null, null, null);

    assertThat(filters.directions())
        .containsExactlyElementsOf(AuditQuery.theDirectionsThisTrailHolds())
        .contains("OUTBOUND_WEBHOOK_DROPPED");
    assertThat(filters.origins())
        .containsExactlyElementsOf(AuditQuery.theOriginsThisTrailHolds())
        .contains("UI", "CONTROL_API");
  }

  @Test
  void a_page_that_filled_its_page_size_offers_the_next_page_to_carry_on_from() {
    AuditScreen.Page page = AuditScreen.pageOf(aTrailOf(3), aTrail(3), null, false);

    assertThat(page.nextCursor()).isEqualTo("aud_3");
  }

  @Test
  void a_page_shorter_than_its_page_size_is_the_end_of_the_trail_and_offers_no_next_page() {
    AuditScreen.Page page = AuditScreen.pageOf(aTrailOf(2), aTrail(3), null, false);

    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void a_filter_that_matched_nothing_offers_no_next_page_to_walk_into() {
    AuditScreen.Page page = AuditScreen.pageOf(List.of(), aTrail(100), null, false);

    assertThat(page.nextCursor()).isNull();
    assertThat(page.rows()).isEmpty();
  }

  @Test
  void only_the_exchange_a_tester_opened_puts_the_fields_of_its_body_on_the_page() {
    AuditScreen.Page page = AuditScreen.pageOf(aTrailOf(3), aTrail(3), "aud_2", false);

    assertThat(fieldNamesOf(page, "aud_2")).isNotEmpty();
    assertThat(fieldNamesOf(page, "aud_1")).isEmpty();
    assertThat(fieldNamesOf(page, "aud_3")).isEmpty();
  }

  @Test
  void only_the_exchange_a_tester_opened_puts_the_headers_it_travelled_under_on_the_page() {
    AuditScreen.Page page = AuditScreen.pageOf(aTrailOf(3), aTrail(3), "aud_2", false);

    assertThat(headerNamesOf(page, "aud_2")).containsExactly("Authorization");
    assertThat(headerNamesOf(page, "aud_1")).isEmpty();
    assertThat(headerNamesOf(page, "aud_3")).isEmpty();
  }

  @Test
  void an_opened_exchange_keeps_the_payers_details_off_the_page_until_they_are_asked_for() {
    AuditScreen.Page unaskedFor = AuditScreen.pageOf(aTrailOf(1), aTrail(1), "aud_1", false);
    AuditScreen.Page askedFor = AuditScreen.pageOf(aTrailOf(1), aTrail(1), "aud_1", true);

    assertThat(valuesOf(unaskedFor)).doesNotContain(EMAIL);
    assertThat(valuesOf(askedFor)).contains(EMAIL);
    assertThat(unaskedFor.valuesShown()).isFalse();
    assertThat(askedFor.valuesShown()).isTrue();
  }

  @Test
  void a_damaged_delivery_reports_what_reached_the_client_and_what_was_promised() {
    AuditScreen.Page page =
        AuditScreen.pageOf(List.of(aTruncatedResponse()), aTrail(1), null, false);

    AuditScreen.Damage damage = page.rows().get(0).damage();
    assertThat(damage).isNotNull();
    assertThat(damage.transportFault()).isEqualTo(TransportFaultKind.TRUNCATED_BODY.name());
    assertThat(damage.faultPlacement()).isEqualTo(FaultPlacement.AFTER_THE_OPERATION.name());
    assertThat(damage.declaredContentLength()).isEqualTo(120L);
    assertThat(damage.writtenBodyBytes()).isEqualTo(40L);
  }

  @Test
  void a_delivery_that_was_only_held_back_reports_how_long_it_was_held_and_names_no_fault() {
    AuditScreen.Page page = AuditScreen.pageOf(List.of(aHeldResponse()), aTrail(1), null, false);

    AuditScreen.Damage damage = page.rows().get(0).damage();
    assertThat(damage).isNotNull();
    assertThat(damage.transportFault()).isNull();
    assertThat(damage.faultPlacement()).isNull();
    assertThat(damage.heldForMillis()).isEqualTo(1500L);
  }

  @Test
  void an_exchange_no_fault_touched_carries_no_damage_to_report() {
    AuditScreen.Page page = AuditScreen.pageOf(aTrailOf(1), aTrail(1), null, false);

    assertThat(page.rows().get(0).damage()).isNull();
  }

  @Test
  void a_row_carries_the_rule_that_routed_it_and_the_verdict_the_provider_reached() {
    AuditScreen.Page page = AuditScreen.pageOf(aTrailOf(1), aTrail(1), null, false);

    AuditScreen.Row row = page.rows().get(0);
    assertThat(row.resolutionRule()).isEqualTo(TestRunResolutionRule.TEST_HEADER.name());
    assertThat(row.outcome()).isEqualTo(new StatusBadge("SUCCESS", StatusBadge.Tone.SUCCESS));
    assertThat(row.direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE.name());
    assertThat(row.status()).isEqualTo(200);
  }

  @Test
  void a_narrowing_to_an_exchange_the_trail_no_longer_holds_says_which_audit_id_answered_nothing() {
    AuditScreen.Page page =
        AuditScreen.pageOf(List.of(), aTrailNarrowedToTheExchange("aud_gone", 100), null, false);

    assertThat(page.rows()).isEmpty();
    assertThat(page.theExchangeNoRowAnswersTo()).isEqualTo("aud_gone");
    assertThat(page.theAccountNoRowNames()).isNull();
  }

  @Test
  void a_narrowing_to_an_exchange_the_trail_holds_reports_that_row_and_says_nothing_is_missing() {
    AuditScreen.Page page =
        AuditScreen.pageOf(aTrailOf(1), aTrailNarrowedToTheExchange("aud_1", 1), "aud_1", false);

    assertThat(page.rows()).extracting(AuditScreen.Row::auditId).containsExactly("aud_1");
    assertThat(page.theExchangeNoRowAnswersTo()).isNull();
  }

  @Test
  void an_empty_trail_no_exchange_narrowed_stays_the_empty_trail_and_names_no_audit_id() {
    AuditScreen.Page page = AuditScreen.pageOf(List.of(), aTrail(100), null, false);

    assertThat(page.rows()).isEmpty();
    assertThat(page.theExchangeNoRowAnswersTo()).isNull();
  }

  @ParameterizedTest(name = "with {0} beside it")
  @MethodSource("everyNarrowingBesidesTheExchange")
  void an_empty_page_something_else_could_have_emptied_blames_the_exchange_for_none_of_it(
      String besidesTheExchange, AuditQuery narrowed) {
    AuditScreen.Page page = AuditScreen.pageOf(List.of(), narrowed, null, false);

    assertThat(page.rows()).isEmpty();
    assertThat(page.theExchangeNoRowAnswersTo()).isNull();
    assertThat(page.theAccountNoRowNames()).isNull();
    assertThat(besidesTheExchange).isNotBlank();
  }

  @Test
  void an_empty_page_something_else_could_have_emptied_blames_the_account_for_none_of_it_either() {
    AuditScreen.Page page =
        AuditScreen.pageOf(
            List.of(), besidesAnExchangeThat("merchant-9", null, null, "GET"), null, false);

    assertThat(page.theAccountNoRowNames()).isNull();
    assertThat(page.theExchangeNoRowAnswersTo()).isNull();
  }

  private static Stream<Arguments> everyNarrowingBesidesTheExchange() {
    return Stream.of(
        Arguments.of("an account", besidesAnExchangeThat("merchant-9", null, null, null)),
        Arguments.of("a surface", besidesAnExchangeThat(null, ObjectOrigin.UI, null, null)),
        Arguments.of(
            "a direction", besidesAnExchangeThat(null, null, AuditDirection.INBOUND_REQUEST, null)),
        Arguments.of("a method", besidesAnExchangeThat(null, null, null, "GET")),
        Arguments.of("an HTTP status", besidesAnExchangeNarrowedTo(404, List.of(), null)),
        Arguments.of(
            "a provider object", besidesAnExchangeNarrowedTo(null, List.of("pay_1"), null)),
        Arguments.of("a scenario", besidesAnExchangeNarrowedTo(null, List.of(), "scn_1")),
        Arguments.of("the start of a range", besidesAnExchangeInTime(AT, null, null)),
        Arguments.of("the end of a range", besidesAnExchangeInTime(null, AT, null)),
        Arguments.of("a cursor", besidesAnExchangeInTime(null, null, "aud_1")));
  }

  private static AuditQuery besidesAnExchangeThat(
      @Nullable String accountId,
      @Nullable ObjectOrigin origin,
      @Nullable AuditDirection direction,
      @Nullable String method) {
    return new AuditQuery(
        RUN,
        NARROWED_TO_ONE_EXCHANGE,
        accountId,
        origin,
        direction,
        method,
        null,
        List.of(),
        null,
        null,
        null,
        null,
        100);
  }

  private static AuditQuery besidesAnExchangeNarrowedTo(
      @Nullable Integer httpStatus, List<String> providerObjectIds, @Nullable String scenarioId) {
    return new AuditQuery(
        RUN,
        NARROWED_TO_ONE_EXCHANGE,
        null,
        null,
        null,
        null,
        httpStatus,
        providerObjectIds,
        scenarioId,
        null,
        null,
        null,
        100);
  }

  private static AuditQuery besidesAnExchangeInTime(
      @Nullable Instant from, @Nullable Instant until, @Nullable String after) {
    return new AuditQuery(
        RUN,
        NARROWED_TO_ONE_EXCHANGE,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        null,
        from,
        until,
        after,
        100);
  }

  @Test
  void a_narrowing_to_an_account_no_row_of_the_run_names_says_which_account_answered_nothing() {
    AuditScreen.Page page =
        AuditScreen.pageOf(List.of(), aTrailNarrowedToTheAccount("merchant-9", 100), null, false);

    assertThat(page.rows()).isEmpty();
    assertThat(page.theAccountNoRowNames()).isEqualTo("merchant-9");
    assertThat(page.theExchangeNoRowAnswersTo()).isNull();
  }

  @Test
  void
      a_narrowing_to_an_account_that_did_act_reports_its_rows_and_says_nothing_about_the_account() {
    AuditScreen.Page page =
        AuditScreen.pageOf(aTrailOf(1), aTrailNarrowedToTheAccount("merchant-1", 1), null, false);

    assertThat(page.rows()).hasSize(1);
    assertThat(page.theAccountNoRowNames()).isNull();
  }

  @Test
  void an_empty_trail_no_account_narrowed_stays_the_empty_trail_and_names_no_account() {
    AuditScreen.Page page = AuditScreen.pageOf(List.of(), aTrail(100), null, false);

    assertThat(page.rows()).isEmpty();
    assertThat(page.theAccountNoRowNames()).isNull();
  }

  @Test
  void
      a_row_reports_the_account_it_acted_for_while_the_field_that_authenticates_it_stays_withheld() {
    AuditScreen.Page page =
        AuditScreen.pageOf(List.of(anAccountsRow()), aTrail(1), "aud_account", true);

    AuditScreen.Row row = page.rows().get(0);
    assertThat(row.accountId()).isEqualTo("merchant-1");
    assertThat(row.body()).hasSize(1);
    assertThat(row.body().get(0).name()).isEqualTo("userId");
    assertThat(row.body().get(0).visibility()).isEqualTo(Disclosure.Visibility.REDACTED_ON_WRITE);
    assertThat(valuesOf(page)).isEmpty();
  }

  @Test
  void
      a_row_written_with_no_account_at_all_carries_none_rather_than_a_blank_no_filter_could_match() {
    AuditScreen.Page page =
        AuditScreen.pageOf(List.of(aBlankAccountsRow()), aTrail(1), null, false);

    assertThat(page.rows().get(0).accountId()).isNull();
  }

  private static AuditQuery aTrail(int limit) {
    return aTrail(null, null, null, null, limit);
  }

  private static AuditQuery aTrailNarrowedToTheAccount(String accountId, int limit) {
    return aTrail(null, accountId, null, null, limit);
  }

  private static AuditQuery aTrailNarrowedToTheExchange(String auditId, int limit) {
    return aTrail(auditId, null, null, null, limit);
  }

  private static AuditQuery aTrail(
      @Nullable String auditId,
      @Nullable String accountId,
      @Nullable String method,
      @Nullable String after,
      int limit) {
    return new AuditQuery(
        RUN, auditId, accountId, null, null, method, null, List.of(), null, null, null, after,
        limit);
  }

  private static AuditRecord anAccountsRow() {
    return aRecordOf("aud_account", "merchant-1", Map.of("userId", List.of(Redaction.REDACTED)));
  }

  private static AuditRecord aBlankAccountsRow() {
    return aRecordOf("aud_blank", "  ", Map.of());
  }

  private static List<AuditRecord> aTrailOf(int rows) {
    List<AuditRecord> trail = new ArrayList<>();
    for (int number = 1; number <= rows; number++) {
      trail.add(aRecord("aud_" + number, null));
    }
    return trail;
  }

  private static AuditRecord aHeldResponse() {
    return aRecord("aud_held", new DeliveredResponse(null, 120L, 120L, Duration.ofMillis(1500)));
  }

  private static AuditRecord aTruncatedResponse() {
    return aRecord(
        "aud_damaged",
        new DeliveredResponse(
            new AppliedFault(TransportFaultKind.TRUNCATED_BODY, FaultPlacement.AFTER_THE_OPERATION),
            120L,
            40L,
            Duration.ofMillis(250)));
  }

  private static AuditRecord aRecordOf(
      String auditId, @Nullable String accountId, Map<String, List<String>> body) {
    return new AuditRecord(
        auditId,
        TestRunId.of("ui-audit"),
        TestRunResolutionRule.TEST_HEADER,
        ProviderId.of("grow"),
        accountId,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.INBOUND_REQUEST,
        null,
        "POST",
        "/api/light/server/1.0/createPaymentProcess",
        null,
        null,
        null,
        null,
        1,
        AT,
        Map.of("Authorization", List.of(Redaction.REDACTED)),
        body,
        null);
  }

  private static AuditRecord aRecord(String auditId, @Nullable DeliveredResponse delivered) {
    return new AuditRecord(
        auditId,
        TestRunId.of("ui-audit"),
        TestRunResolutionRule.TEST_HEADER,
        ProviderId.of("grow"),
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.SUCCESS,
        "POST",
        "/api/light/server/1.0/createPaymentProcess",
        200,
        "proc_1",
        null,
        "scn_1",
        1,
        AT,
        Map.of("Authorization", List.of(Redaction.REDACTED)),
        Map.of("pageField[email]", List.of(EMAIL)),
        delivered);
  }

  private static List<String> fieldNamesOf(AuditScreen.Page page, String auditId) {
    return page.rows().stream()
        .filter(row -> row.auditId().equals(auditId))
        .flatMap(row -> row.body().stream())
        .map(Disclosure.Field::name)
        .toList();
  }

  private static List<String> headerNamesOf(AuditScreen.Page page, String auditId) {
    return page.rows().stream()
        .filter(row -> row.auditId().equals(auditId))
        .flatMap(row -> row.headers().stream())
        .map(Disclosure.Field::name)
        .toList();
  }

  private static List<String> valuesOf(AuditScreen.Page page) {
    return page.rows().stream()
        .flatMap(row -> row.body().stream())
        .flatMap(field -> field.values().stream())
        .toList();
  }
}
