package dev.marcinromanowski.pspsim.control;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class AuditReadIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("audit-read-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "audit-read-user";
  private static final String PAGE_CODE = "audit-read-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String PROCESS_INFO = "/api/light/server/1.0/getPaymentProcessInfo";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String HELD = "120.50";
  private static final String SESSION = "session-abc-must-not-be-readable";
  private static final String BEARER = "Bearer must-not-be-readable";
  private static final String REDACTED = "[redacted]";
  private static final String AUDIT = "/__simulator/grow/audit";
  private static final String NEVER_CONFIGURED = "audit-read-it-never-configured";
  private static final String CURSOR_NAMES_NO_ROW =
      "after must name an audit record of this provider and test run";

  @LocalServerPort private int port;

  @Autowired private ObjectMapper json;
  @Autowired private JdbcClient jdbc;
  @Autowired private ProviderRegistry providers;

  private RestClient http;
  private HttpClient wire;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    merchant = new LocalMerchantServer();
    reset();
    configure();
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure();
    reset();
    merchant.close();
  }

  @Test
  void a_harness_can_tell_a_call_a_rule_stood_aside_for_from_one_no_rule_touched() {
    String standingAside = aFixedSuccessOn("createPaymentProcess");
    Hold hold = aHold();

    Map<String, Object> lookup = parse(post(PROCESS_INFO, lookupOf(hold)));

    assertThat(lookup).containsEntry("status", 1);
    List<Map<String, Object>> answered = auditRead("?direction=OUTBOUND_RESPONSE");
    assertThat(rowOf(answered, CREATE)).containsEntry("scenarioId", standingAside);
    assertThat(rowOf(answered, PROCESS_INFO)).containsEntry("scenarioId", null);
    assertThat(rowOf(answered, CREATE)).containsEntry("outcome", "SUCCESS");
    assertThat(rowOf(answered, PROCESS_INFO)).containsEntry("outcome", "SUCCESS");
  }

  @Test
  void a_decline_a_rule_chose_is_told_from_a_refusal_the_provider_owns() {
    String declining = aDeclineOn("settleSuspendedTransaction", "813");
    Hold hold = aHold();

    Map<String, Object> declined = parse(post(SETTLE, settlementOf(hold)));
    Map<String, Object> unknownTransaction =
        parse(post(SETTLE, settlementOf(new Hold("nope", "nope", "404404", "no-such-token"))));

    assertThat(declined).containsEntry("status", 0);
    assertThat(unknownTransaction).containsEntry("status", 0);
    List<Map<String, Object>> captures = rowsOf(auditRead("?direction=OUTBOUND_RESPONSE"), SETTLE);
    assertThat(captures.get(0))
        .containsEntry("outcome", "FAILURE")
        .containsEntry("scenarioId", declining);
    assertThat(captures.get(1))
        .containsEntry("outcome", "FAILURE")
        .containsEntry("scenarioId", null);
    assertThat(captures).hasSize(2);
  }

  @Test
  void
      a_harness_recovers_from_audit_what_a_damaged_delivery_lost_and_sees_that_a_preempted_call_did_nothing() {
    Hold hold = aHold();
    aTransportFaultOn("createPaymentProcess", Map.of("fault", "TRUNCATED_BODY"));
    aFaultInsteadOf("getPaymentProcessInfo");

    HttpResponse<byte[]> damaged = post(CREATE, holdForm());
    HttpResponse<byte[]> preempted = post(PROCESS_INFO, lookupOf(hold));

    assertThat(damaged.statusCode()).isEqualTo(200);
    assertThat(new String(damaged.body(), UTF_8)).doesNotContain("}");
    assertThat(preempted.statusCode()).isEqualTo(502);
    assertThat(preempted.headers().firstValue("Content-Type")).contains("text/plain;charset=utf-8");
    assertThat(new String(preempted.body(), UTF_8))
        .startsWith("502 was answered in front of the provider.");

    List<Map<String, Object>> answered = auditRead("?direction=OUTBOUND_RESPONSE");
    Map<String, Object> damagedRow = lastRowOf(answered, CREATE);
    Map<String, Object> preemptedRow = lastRowOf(answered, PROCESS_INFO);

    assertThat(bodyOf(damagedRow).get("data.processId")).isNotNull();
    assertThat(bodyOf(preemptedRow)).isEmpty();
    assertThat(deliveredOf(damagedRow))
        .containsEntry("transportFault", "TRUNCATED_BODY")
        .containsEntry("faultPlacement", "AFTER_THE_OPERATION");
    assertThat(deliveredOf(preemptedRow))
        .containsEntry("transportFault", "SERVER_ERROR")
        .containsEntry("faultPlacement", "INSTEAD_OF_THE_OPERATION");
    assertThat(damagedRow).containsEntry("outcome", "SUCCESS").containsEntry("status", 200);
    assertThat(preemptedRow).containsEntry("outcome", "UNKNOWN").containsEntry("status", 502);
  }

  @Test
  void a_body_the_simulator_could_not_parse_is_audited_with_the_answer_it_was_given() {
    String cursor = lastAuditIdOf("default");

    HttpResponse<byte[]> answer = postRaw(CREATE, encoded(holdForm()) + "&cField1=%zz");

    assertThat(answer.statusCode()).isEqualTo(200);
    assertThat(new String(answer.body(), UTF_8)).contains("\"id\":707");
    List<Map<String, Object>> written = auditRead(after("?testRunId=default", cursor));
    assertThat(bodyOf(written.get(0))).isEmpty();
    assertThat(bodyOf(written.get(1))).containsEntry("err.id", List.of("707"));
    assertThat(written.get(0))
        .containsEntry("direction", "INBOUND_REQUEST")
        .containsEntry("path", CREATE)
        .containsEntry("method", "POST")
        .containsEntry("resolutionRule", "DEFAULT");
    assertThat(written).hasSize(2);
  }

  @Test
  void a_request_that_resolved_to_no_test_run_is_readable_under_the_reserved_namespace() {
    String cursor = lastAuditIdOf("UNKNOWN");

    Map<String, Object> answer = parse(postNamingTestRun(CREATE, holdForm(), "audit-read-it-typo"));

    assertThat(answer).containsEntry("status", 0);
    List<Map<String, Object>> written = auditRead(after("?testRunId=UNKNOWN", cursor));
    assertThat(written.get(0))
        .containsEntry("testRunId", "UNKNOWN")
        .containsEntry("resolutionRule", "TEST_HEADER_UNMATCHED")
        .containsEntry("direction", "INBOUND_REQUEST");
    assertThat(written.get(1)).containsEntry("outcome", "FAILURE");
    assertThat(written).hasSize(2);
  }

  @Test
  void what_the_route_returns_was_already_redacted_where_it_is_stored() {
    Hold hold = aHold();

    Map<String, Object> inbound = rowOf(auditRead("?direction=INBOUND_REQUEST"), CREATE);
    Map<String, Object> outbound = rowOf(auditRead("?direction=OUTBOUND_RESPONSE"), CREATE);

    assertThat(bodyOf(inbound)).containsEntry("cField1", List.of(REDACTED));
    assertThat(bodyOf(inbound)).containsEntry("userId", List.of(REDACTED));
    assertThat(bodyOf(outbound)).containsEntry("data.processToken", List.of(REDACTED));
    assertThat(headersOf(inbound)).containsEntry("Authorization", List.of(REDACTED));
    assertThat(hold.processToken()).isNotEqualTo(REDACTED);

    String stored =
        jdbc.sql(
                """
                select sanitized_body || sanitized_headers from simulator_audit_record
                where test_run_id = ? and path = ? order by seq
                """)
            .params(RUN.value(), CREATE)
            .query(String.class)
            .list()
            .toString();
    assertThat(stored).contains(REDACTED).doesNotContain(SESSION, BEARER, hold.processToken());
  }

  @Test
  void an_outbound_callback_attempt_is_listed_beside_the_exchanges_that_caused_it() {
    aHold();

    List<Map<String, Object>> attempts =
        auditRead("?origin=SCHEDULED_JOB&direction=OUTBOUND_WEBHOOK_ATTEMPT");

    assertThat(attempts.get(0))
        .containsEntry("resolutionRule", null)
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("attempt", 1)
        .containsEntry("path", merchant.url());
    assertThat(bodyOf(attempts.get(0))).containsEntry("data[processToken]", List.of(REDACTED));
    assertThat(attempts).hasSize(1);
    assertThat(auditRead("")).hasSize(4);
    assertThat(auditIdsOf(theWholeTrail()))
        .containsAll(auditIdsOf(auditRead("")))
        .containsAll(auditIdsOf(attempts));
  }

  @Test
  void a_page_hands_back_a_cursor_the_next_page_carries_on_from() {
    aHold();

    int exchanged = auditRead("").size();
    List<Map<String, Object>> first = auditRead("?limit=2");
    List<Map<String, Object>> rest = auditRead(after("?limit=10", auditIdOf(first.get(1))));

    assertThat(first).hasSize(2);
    assertThat(rest).hasSize(exchanged - 2);
    assertThat(auditIdsOf(rest)).doesNotContainAnyElementsOf(auditIdsOf(first));
  }

  @Test
  void the_route_answers_a_request_it_cannot_serve_by_naming_what_was_wrong() {
    assertThat(refusalOf(AUDIT + "?direction=SIDEWAYS"))
        .isEqualTo(
            new Refusal(
                400,
                "direction must be one of INBOUND_REQUEST, OUTBOUND_RESPONSE,"
                    + " OUTBOUND_WEBHOOK_ATTEMPT, OUTBOUND_WEBHOOK_DROPPED,"
                    + " OUTBOUND_WEBHOOK_NOT_SENT"));
    assertThat(refusalOf(AUDIT + "?limit=0"))
        .isEqualTo(new Refusal(400, "limit must be between 1 and 500"));
    assertThat(refusalOf(AUDIT + "?limit=501"))
        .isEqualTo(new Refusal(400, "limit must be between 1 and 500"));
    assertThat(refusalOf(AUDIT + "?limit=all"))
        .isEqualTo(new Refusal(400, "limit must be a whole number"));
    assertThat(refusalOf(AUDIT + "?status=teapot"))
        .isEqualTo(new Refusal(400, "status must be a whole number"));
    assertThat(refusalOf(AUDIT + "?status=42"))
        .isEqualTo(new Refusal(400, "status must be an HTTP status between 100 and 599"));
    assertThat(refusalOf(AUDIT + "?from=yesterday"))
        .isEqualTo(
            new Refusal(400, "from must be an ISO-8601 instant such as 2026-09-07T12:00:00Z"));
    assertThat(refusalOf(AUDIT + "?until=tomorrow"))
        .isEqualTo(
            new Refusal(400, "until must be an ISO-8601 instant such as 2026-09-07T12:00:00Z"));
    assertThat(refusalOf(AUDIT + "?from=" + FIXED_NOW + "&until=" + FIXED_NOW))
        .isEqualTo(new Refusal(400, "until must be later than from"));
    assertThat(refusalOf(AUDIT + "?testRunId=a%20b").status()).isEqualTo(400);
    assertThat(refusalOf(AUDIT + "?testRunId=a%20b").message())
        .startsWith("testRunId")
        .contains("must match");
    assertThat(refusalOf(AUDIT + "?after=aud_never_written"))
        .isEqualTo(new Refusal(404, CURSOR_NAMES_NO_ROW));
    assertThat(refusalOf(AUDIT + "?after=")).isEqualTo(new Refusal(404, CURSOR_NAMES_NO_ROW));
    assertThat(statusOf(AUDIT + "?testRunId=" + RUN.value())).isEqualTo(200);
  }

  @Test
  void the_trail_narrows_to_the_account_whose_credentials_the_exchange_authenticated_with() {
    Hold hold = aHold();

    List<Map<String, Object>> theAccounts = auditRead("?accountId=" + MERCHANT);

    assertThat(theAccounts).isNotEmpty();
    assertThat(theAccounts).allSatisfy(row -> assertThat(row).containsEntry("accountId", MERCHANT));
    assertThat(auditIdsOf(theAccounts)).containsExactlyElementsOf(auditIdsOf(auditRead("")));
    assertThat(hold.processId()).isNotEqualTo(MERCHANT);
  }

  @Test
  void a_callback_the_queue_posted_to_an_accounts_target_names_that_account_too() {
    aHold();

    List<Map<String, Object>> attempts = auditRead("?origin=SCHEDULED_JOB&accountId=" + MERCHANT);

    assertThat(attempts).isNotEmpty();
    assertThat(attempts).allSatisfy(row -> assertThat(row).containsEntry("accountId", MERCHANT));
  }

  @Test
  void the_trail_narrows_to_the_one_exchange_an_audit_id_names_and_not_to_its_object_or_its_run() {
    aHold();
    List<Map<String, Object>> whole = auditRead("");
    String named = auditIdOf(whole.get(0));

    List<Map<String, Object>> oneExchange = auditRead("?auditId=" + named);

    assertThat(whole).hasSizeGreaterThan(1);
    assertThat(auditIdsOf(oneExchange)).containsExactly(named);
  }

  @Test
  void an_audit_id_given_with_no_value_at_all_narrows_nothing_the_way_an_absent_one_does() {
    aHold();

    assertThat(auditRead("?auditId=")).isNotEmpty();
    assertThat(auditIdsOf(auditRead("?auditId=")))
        .containsExactlyElementsOf(auditIdsOf(auditRead("")));
  }

  @Test
  void a_narrowing_to_an_audit_id_wider_than_a_row_holds_is_refused_rather_than_read_as_empty() {
    String tooWide = "z".repeat(4000);

    Refusal refused = refusalOf(AUDIT + "?auditId=" + tooWide);

    assertThat(refused.status()).isEqualTo(400);
    assertThat(refused.message()).contains("auditId must be at most 64 characters");
    assertThat(refused.message()).doesNotContain("zz");
  }

  @Test
  void a_narrowing_to_an_audit_id_no_row_was_recorded_under_answers_an_empty_trail() {
    aHold();

    assertThat(auditRead("?auditId=aud_nothing_was_recorded_under_this")).isEmpty();
    assertThat(auditRead("")).isNotEmpty();
  }

  @Test
  void a_narrowing_to_an_account_that_wrote_nothing_answers_an_empty_trail_and_not_the_whole_one() {
    aHold();

    List<Map<String, Object>> nothing = auditRead("?accountId=" + NEVER_CONFIGURED);

    assertThat(nothing).isEmpty();
    assertThat(auditRead("")).isNotEmpty();
  }

  @Test
  void the_column_that_names_the_account_carries_no_field_that_authenticates_it() {
    aHold();

    List<Map<String, Object>> exchanges = auditRead("");

    assertThat(exchanges).allSatisfy(row -> assertThat(row).containsEntry("accountId", MERCHANT));
    assertThat(String.valueOf(exchanges)).doesNotContain(USER_ID, PAGE_CODE);
    String stored =
        jdbc.sql(
                "select account_id || sanitized_body || sanitized_headers from"
                    + " simulator_audit_record where test_run_id = ? order by seq")
            .params(RUN.value())
            .query(String.class)
            .list()
            .toString();
    assertThat(stored).contains(MERCHANT, REDACTED).doesNotContain(USER_ID, PAGE_CODE);
  }

  @Test
  void a_narrowing_to_an_account_no_row_could_carry_is_refused_in_this_simulators_own_words() {
    assertThat(refusalOf(AUDIT + "?accountId=" + "a".repeat(129)))
        .isEqualTo(
            new Refusal(
                400,
                "accountId must be at most 128 characters, which is the widest identity a row"
                    + " holds, so a longer one names an account no row could carry"));
    assertThat(statusOf(AUDIT + "?accountId=" + "a".repeat(128))).isEqualTo(200);
  }

  @Test
  void an_account_given_with_no_value_at_all_narrows_nothing_the_way_an_absent_one_does() {
    aHold();

    assertThat(auditIdsOf(auditRead("?accountId=")))
        .containsExactlyElementsOf(auditIdsOf(auditRead("")));
  }

  @Test
  void a_route_of_a_provider_this_simulator_does_not_serve_names_every_provider_it_does_serve() {
    Refusal refusal = refusalOf("/__simulator/nosuchprovider/audit");

    assertThat(providers.names()).contains("grow", "azericard");
    assertThat(refusal.status()).isEqualTo(404);
    assertThat(refusal.message()).contains(providers.names());
    assertThat(refusal.message()).doesNotContain("nosuchprovider");
  }

  @Test
  void a_refusal_names_the_property_that_was_wrong_and_never_repeats_the_value_that_was_sent() {
    assertThat(refusalOf(AUDIT + "?direction=SIDEWAYS").message()).doesNotContain("SIDEWAYS");
    assertThat(refusalOf(AUDIT + "?status=teapot").message()).doesNotContain("teapot");
    assertThat(refusalOf(AUDIT + "?from=yesterday").message()).doesNotContain("yesterday");
    assertThat(refusalOf(AUDIT + "?limit=all").message()).doesNotContain("all");
    assertThat(refusalOf(AUDIT + "?after=aud_never_written").message())
        .doesNotContain("aud_never_written");
  }

  @Test
  void a_row_carries_every_field_the_published_record_names_under_its_published_name() {
    Hold hold = aHold();

    Map<String, Object> outbound = rowOf(auditRead("?direction=OUTBOUND_RESPONSE"), CREATE);

    assertThat(outbound)
        .containsEntry("provider", "grow")
        .containsEntry("accountId", MERCHANT)
        .containsEntry("testRunId", RUN.value())
        .containsEntry("resolutionRule", "ACCOUNT_CREDENTIALS")
        .containsEntry("direction", "OUTBOUND_RESPONSE")
        .containsEntry("outcome", "SUCCESS")
        .containsEntry("method", "POST")
        .containsEntry("path", CREATE)
        .containsEntry("status", 200)
        .containsEntry("providerObjectId", hold.processId())
        .containsEntry("scenarioId", null)
        .containsEntry("attempt", 1)
        .containsEntry("createdAt", FIXED_NOW.toString());
    assertThat(auditIdOf(outbound)).startsWith("aud_").hasSizeGreaterThan("aud_".length());
  }

  @Test
  void a_filter_on_the_object_a_call_was_about_answers_that_objects_exchanges_and_no_others() {
    Hold first = aHold();
    Hold second = aHold();

    List<Map<String, Object>> about = auditRead("?providerObjectId=" + first.processId());

    assertThat(about).isNotEmpty();
    assertThat(about)
        .extracting(row -> row.get("providerObjectId"))
        .containsOnly(first.processId());
    assertThat(auditIdsOf(about))
        .doesNotContainAnyElementsOf(
            auditIdsOf(auditRead("?providerObjectId=" + second.processId())));
    assertThat(about).hasSizeLessThan(auditRead("").size());
  }

  @Test
  void a_filter_on_the_rule_that_decided_a_call_answers_only_the_calls_it_decided() {
    String deciding = aFixedSuccessOn("createPaymentProcess");
    aHold();

    List<Map<String, Object>> decided = auditRead("?scenarioId=" + deciding);

    assertThat(decided).isNotEmpty();
    assertThat(decided).extracting(row -> row.get("scenarioId")).containsOnly(deciding);
    assertThat(decided).extracting(row -> row.get("path")).containsOnly(CREATE);
    assertThat(decided).hasSizeLessThan(auditRead("").size());
  }

  @Test
  void a_filter_matches_however_the_harness_cased_the_value_it_named() {
    aHold();

    assertThat(auditIdsOf(auditRead("?method=post"))).isEqualTo(auditIdsOf(auditRead("")));
    assertThat(auditRead("?method=GET")).isEmpty();
    assertThat(auditIdsOf(auditRead("?direction=outbound_response")))
        .isNotEmpty()
        .isEqualTo(auditIdsOf(auditRead("?direction=OUTBOUND_RESPONSE")));
  }

  @Test
  void a_status_filter_answers_what_travelled_under_it_and_leaves_out_the_row_that_carried_none() {
    aHold();

    List<Map<String, Object>> answered = auditRead("?status=200");

    assertThat(answered).isNotEmpty();
    assertThat(answered).extracting(row -> row.get("status")).containsOnly(200);
    assertThat(answered).extracting(row -> row.get("direction")).doesNotContain("INBOUND_REQUEST");
    assertThat(answered).hasSizeLessThan(auditRead("").size());
    assertThat(auditRead("?status=404")).isEmpty();
  }

  @Test
  void a_time_range_takes_in_the_exchange_that_arrived_at_its_start_and_not_the_one_at_its_end() {
    aHold();

    List<String> whole = auditIdsOf(auditRead(""));

    assertThat(auditIdsOf(auditRead("?from=" + FIXED_NOW))).isEqualTo(whole);
    assertThat(auditIdsOf(auditRead("?until=" + FIXED_NOW.plusMillis(1)))).isEqualTo(whole);
    assertThat(auditRead("?until=" + FIXED_NOW)).isEmpty();
    assertThat(auditRead("?from=" + FIXED_NOW.plusMillis(1))).isEmpty();
  }

  @Test
  void a_test_run_that_never_wrote_a_row_is_answered_as_an_empty_trail_and_not_as_a_missing_one() {
    aHold();

    assertThat(auditRead("?testRunId=" + NEVER_CONFIGURED)).isEmpty();
    assertThat(statusOf(AUDIT + "?testRunId=" + NEVER_CONFIGURED)).isEqualTo(200);
  }

  @Test
  void a_cursor_names_a_row_of_the_trail_rather_than_of_the_page_a_filter_narrowed_it_to() {
    aHold();

    List<Map<String, Object>> whole = auditRead("");
    List<Map<String, Object>> onwards =
        auditRead(after("?direction=OUTBOUND_RESPONSE", auditIdOf(whole.get(0))));

    assertThat(whole.get(0)).containsEntry("direction", "INBOUND_REQUEST");
    assertThat(onwards).isNotEmpty();
    assertThat(onwards).extracting(row -> row.get("direction")).containsOnly("OUTBOUND_RESPONSE");
  }

  private String aFixedSuccessOn(String operation) {
    return createScenario(
        Map.of(
            "testRunId", RUN.value(),
            "priority", 100,
            "match", Map.of("operation", operation),
            "action", Map.of("type", "FIXED_SUCCESS")));
  }

  private String aDeclineOn(String operation, String providerCode) {
    return createScenario(
        Map.of(
            "testRunId", RUN.value(),
            "priority", 100,
            "match", Map.of("operation", operation),
            "action", Map.of("type", "TERMINAL_FAILURE", "providerCode", providerCode)));
  }

  private void aTransportFaultOn(String operation, Map<String, Object> fault) {
    Map<String, Object> action = new LinkedHashMap<>(fault);
    action.put("type", "TRANSPORT_FAULT");
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "priority",
            100,
            "match",
            Map.of("operation", operation),
            "action",
            action));
  }

  private void aFaultInsteadOf(String operation) {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "priority",
            100,
            "match",
            Map.of("operation", operation),
            "action",
            Map.of(
                "type", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION",
                "fault", "SERVER_ERROR",
                "httpStatus", 502)));
  }

  private String createScenario(Map<String, Object> body) {
    ResponseEntity<Map<String, Object>> created =
        http.post()
            .uri("/__simulator/grow/scenarios")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    return String.valueOf(created.getBody().get("scenarioId"));
  }

  private Hold aHold() {
    Map<String, Object> created = parse(post(CREATE, holdForm()));
    assertThat(created).containsEntry("status", 1);
    Map<String, Object> data = dataOf(created);
    String processId = String.valueOf(data.get("processId"));
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
        .retrieve()
        .toBodilessEntity();
    Map<String, Object> outcome = parse(post(PROCESS_INFO, lookupOf(processId, data)));
    Map<String, Object> transaction = firstTransactionOf(outcome);
    return new Hold(
        processId,
        String.valueOf(data.get("processToken")),
        String.valueOf(transaction.get("transactionId")),
        String.valueOf(transaction.get("transactionToken")));
  }

  private static Map<String, String> lookupOf(String processId, Map<String, Object> data) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", PAGE_CODE);
    form.put("processId", processId);
    form.put("processToken", String.valueOf(data.get("processToken")));
    return form;
  }

  private static Map<String, String> lookupOf(Hold hold) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", PAGE_CODE);
    form.put("processId", hold.processId());
    form.put("processToken", hold.processToken());
    return form;
  }

  private static Map<String, String> settlementOf(Hold hold) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("userId", USER_ID);
    form.put("transactionId", hold.transactionId());
    form.put("transactionToken", hold.transactionToken());
    form.put("sum", HELD);
    return form;
  }

  private Map<String, String> holdForm() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", PAGE_CODE);
    form.put("userId", USER_ID);
    form.put("chargeType", "2");
    form.put("sum", HELD);
    form.put("saveCardToken", "1");
    form.put("description", "A hold");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("notifyUrl", merchant.url());
    form.put("pageField[fullName]", "Israel Israeli");
    form.put("pageField[phone]", "0505503144");
    form.put("cField1", SESSION);
    form.put("cField2", "intent-99");
    return form;
  }

  private List<Map<String, Object>> theWholeTrail() {
    return http.get()
        .uri(AUDIT + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private List<Map<String, Object>> auditRead(String query) {
    return http.get()
        .uri(AUDIT + addressed(query))
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private static String addressed(String query) {
    String addressed = query;
    if (!addressed.contains("testRunId=")) {
      addressed = addressed + (addressed.isEmpty() ? "?" : "&") + "testRunId=" + RUN.value();
    }
    return addressed.contains("origin=") ? addressed : addressed + "&origin=PROVIDER_API";
  }

  private String lastAuditIdOf(String testRunId) {
    List<Map<String, Object>> trail =
        http.get()
            .uri(AUDIT + "?limit=" + 500 + "&testRunId=" + testRunId)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return trail.isEmpty() ? "" : auditIdOf(trail.get(trail.size() - 1));
  }

  private static String after(String query, String cursor) {
    return cursor.isEmpty() ? query : query + "&after=" + cursor;
  }

  private Refusal refusalOf(String uri) {
    ResponseEntity<Map<String, Object>> refused =
        http.get()
            .uri(uri)
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    Map<String, Object> body = refused.getBody();
    return new Refusal(
        refused.getStatusCode().value(), body == null ? "" : String.valueOf(body.get("message")));
  }

  private int statusOf(String uri) {
    return http.get()
        .uri(uri)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity()
        .getStatusCode()
        .value();
  }

  private static String auditIdOf(Map<String, Object> row) {
    return String.valueOf(row.get("auditId"));
  }

  private static List<String> auditIdsOf(List<Map<String, Object>> rows) {
    List<String> ids = new ArrayList<>();
    rows.forEach(row -> ids.add(auditIdOf(row)));
    return ids;
  }

  private static Map<String, Object> rowOf(List<Map<String, Object>> rows, String path) {
    return rowsOf(rows, path).get(0);
  }

  private static Map<String, Object> lastRowOf(List<Map<String, Object>> rows, String path) {
    List<Map<String, Object>> matched = rowsOf(rows, path);
    return matched.get(matched.size() - 1);
  }

  private static List<Map<String, Object>> rowsOf(List<Map<String, Object>> rows, String path) {
    List<Map<String, Object>> matched = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      if (path.equals(row.get("path"))) {
        matched.add(row);
      }
    }
    assertThat(matched).isNotEmpty();
    return matched;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> bodyOf(Map<String, Object> row) {
    return (Map<String, Object>) row.get("sanitizedBody");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> headersOf(Map<String, Object> row) {
    return (Map<String, Object>) row.get("sanitizedHeaders");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> deliveredOf(Map<String, Object> row) {
    return (Map<String, Object>) row.get("delivered");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstTransactionOf(Map<String, Object> body) {
    return ((List<Map<String, Object>>) dataOf(body).get("transactions")).get(0);
  }

  private Map<String, Object> parse(HttpResponse<byte[]> response) {
    return json.readValue(
        new String(response.body(), UTF_8), new TypeReference<Map<String, Object>>() {});
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return postRaw(path, encoded(form));
  }

  private HttpResponse<byte[]> postRaw(String path, String body) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", BEARER)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(body)));
  }

  private HttpResponse<byte[]> postNamingTestRun(
      String path, Map<String, String> form, String testRunId) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-Simulator-Test-Run-Id", testRunId)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private void configure() {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("userId", USER_ID, "pageCode", PAGE_CODE))),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", merchant.url()))))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure() {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }

  private record Refusal(int status, String message) {}

  private record Hold(
      String processId, String processToken, String transactionId, String transactionToken) {}
}
