package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.ProviderTraffic;
import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GrowHostedProcessIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-hosted-process-it");

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final TestRunId SHARED_CREDENTIALS_RUN =
      TestRunId.of("grow-hosted-process-it-shared");

  private static final TestRunId CLOCK_ONLY_RUN = TestRunId.of("grow-hosted-process-it-clock-only");

  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-user";
  private static final String PAGE_CODE = "grow-page";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  @LocalServerPort private int port;

  @Value("${simulator.public-base-url}")
  private String publicBaseUrl;

  @Autowired private GrowPaymentProcesses processes;
  @Autowired private AuditTrail audit;
  @Autowired private ObjectMapper json;

  private RestClient http;

  @BeforeEach
  void configureTheMerchantAccount() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "clock", Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                    List.of(
                        Map.of(
                            "accountId",
                            "merchant-1",
                            "configuration",
                            Map.of("userId", USER_ID, "pageCode", PAGE_CODE))),
                "webhooks",
                    List.of(
                        Map.of(
                            "accountId",
                            "merchant-1",
                            "url",
                            "https://merchant.example/callbacks/grow"))))
        .retrieve()
        .toBodilessEntity();
  }

  @AfterEach
  void unconfigureTheMerchantAccount() {
    unconfigure(RUN);
    unconfigure(SHARED_CREDENTIALS_RUN);
    resetEverything();
  }

  private void unconfigure(TestRunId testRunId) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", testRunId.value(),
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  @Test
  void pinning_the_clock_of_a_test_run_moves_the_time_the_control_api_reports() {
    Map<String, Object> configured =
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
                            "merchant-1",
                            "configuration",
                            Map.of("userId", USER_ID, "pageCode", PAGE_CODE)))))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(configured).containsEntry("updatedAt", FIXED_NOW.toString());
  }

  @Test
  void creates_a_hold_over_http_and_answers_with_grows_own_envelope() {
    ResponseEntity<byte[]> response = post(hold());

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.valueOf("text/html; charset=UTF-8"));

    Map<String, Object> body = parse(response);
    assertThat(body).containsEntry("status", 1).containsEntry("err", "");

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    assertThat(data).containsOnlyKeys("processId", "processToken", "url");
    assertThat((String) data.get("processId")).matches("[1-9][0-9]{5}");
    assertThat((String) data.get("processToken")).matches("[0-9a-f]{32}");

    String publishedUrl = (String) data.get("url");
    assertThat(publishedUrl).matches(Pattern.quote(publicBaseUrl) + "/far\\?l=[0-9a-f]{32}");

    String publishedLink = publishedUrl.substring((publicBaseUrl + "/far?l=").length());
    assertThat(publishedLink)
        .as("the link the published url carries is not the process token of the same shape")
        .isNotEqualTo(data.get("processToken"));
    assertThat(processes.findByHostedLink(publishedLink))
        .as("the link the published url carries is the one this process's payer page is found by")
        .get()
        .extracting(GrowPaymentProcess::processId)
        .isEqualTo(data.get("processId"));
    assertThat(processes.findByHostedLink((String) data.get("processToken")))
        .as("no payer page is found by the process token")
        .isEmpty();
  }

  @Test
  void stores_the_process_under_its_id_and_token_together() {
    Map<String, Object> data = dataOf(post(hold()));
    String processId = (String) data.get("processId");
    String processToken = (String) data.get("processToken");

    assertThat(processes.find(RUN, processId, processToken)).isPresent();
    assertThat(processes.find(RUN, processId, "0".repeat(32))).isEmpty();
    assertThat(processes.find(TestRunId.of("another-run"), processId, processToken)).isEmpty();
  }

  @Test
  void a_stored_process_survives_the_round_trip_through_the_database() {
    Map<String, Object> data = dataOf(post(hold()));

    GrowPaymentProcess process =
        processes
            .find(RUN, (String) data.get("processId"), (String) data.get("processToken"))
            .orElseThrow();

    assertThat(process.merchantId()).isEqualTo("merchant-1");
    assertThat(process.chargeType()).isEqualTo("2");
    assertThat(process.chargeSum()).isEqualTo("120.50");
    assertThat(process.description()).isEqualTo("A hold");
    assertThat(process.saveCardToken()).isTrue();
    assertThat(process.notifyUrl()).isEqualTo("https://merchant.example/callbacks/grow");
    assertThat(process.payerFullName()).isEqualTo("Israel Israeli");
    assertThat(process.payerEmail()).isEqualTo("payer@merchant.example");
    assertThat(process.customFields())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("cField1", "session-abc", "cField2", "intent-99"));
    assertThat(process.nativeStatusCode()).isEqualTo("0");
    assertThat(process.normalizedStatus()).isEqualTo(NormalizedStatus.NOT_INITIALIZED);
    assertThat(process.createdAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void keeps_an_optional_field_the_merchant_omitted_absent() {
    MultiValueMap<String, Object> form = hold();
    form.remove("pageField[email]");
    form.remove("notifyUrl");

    Map<String, Object> data = dataOf(post(form));

    GrowPaymentProcess process =
        processes
            .find(RUN, (String) data.get("processId"), (String) data.get("processToken"))
            .orElseThrow();
    assertThat(process.payerEmail()).isNull();
    assertThat(process.notifyUrl()).isNull();
  }

  @Test
  void answers_bad_credentials_with_http_200_and_grows_hebrew_message() {
    MultiValueMap<String, Object> form = hold();
    form.set("userId", "not-configured");

    ResponseEntity<byte[]> response = post(form);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(GrowWireText.readable(new String(response.getBody(), UTF_8)))
        .isEqualTo(
            "{\"status\":0,\"err\":{\"id\":701,"
                + "\"message\":\"פרמטר קוד זיהוי אינו תקין userId\"},\"data\":\"\"}");
  }

  @Test
  void reads_a_form_urlencoded_body_the_same_way_as_a_multipart_one() {
    ResponseEntity<byte[]> response = postUrlencoded(urlencodedHold());

    Map<String, Object> body = parse(response);
    assertThat(body).containsEntry("status", 1);

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    GrowPaymentProcess process =
        processes
            .find(RUN, (String) data.get("processId"), (String) data.get("processToken"))
            .orElseThrow();
    assertThat(process.chargeSum()).isEqualTo("120.50");
    assertThat(process.payerFullName()).isEqualTo("Israel Israeli");
  }

  @Test
  void reads_a_form_urlencoded_body_that_repeats_a_key_and_omits_a_value() {
    ResponseEntity<byte[]> response = postUrlencoded(urlencodedHold() + "&sum=1.00&&description");

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN)).get(0).sanitizedBody())
        .containsEntry("sum", List.of("120.50", "1.00"))
        .containsEntry("description", List.of("A hold", ""));
  }

  @Test
  void ignores_a_file_part_and_reads_the_fields_beside_it() {
    MultiValueMap<String, Object> form = hold();
    form.add(
        "receipt",
        new ByteArrayResource("not a field".getBytes(UTF_8)) {
          @Override
          public String getFilename() {
            return "receipt.txt";
          }
        });

    ResponseEntity<byte[]> response = post(form);

    assertThat(parse(response)).containsEntry("status", 1);
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN)).get(0).sanitizedBody())
        .doesNotContainKey("receipt")
        .containsEntry("sum", List.of("120.50"));
  }

  @Test
  void answers_a_body_that_is_not_form_data_with_the_error_grow_answers_an_empty_request() {
    ResponseEntity<byte[]> response =
        http.post()
            .uri(CREATE)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"pageCode\":\"grow-page\"}")
            .retrieve()
            .toEntity(byte[].class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(GrowWireText.readable(new String(response.getBody(), UTF_8))).contains("\"id\":707");
  }

  @Test
  void answers_a_form_urlencoded_body_whose_escaping_is_malformed_as_a_request_that_said_nothing() {
    ResponseEntity<byte[]> response = postUrlencoded(urlencodedHold() + "&cField1=%zz");

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.valueOf("text/html; charset=UTF-8"));
    assertThat(GrowWireText.readable(new String(response.getBody(), UTF_8))).contains("\"id\":707");
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN))).isEmpty();
  }

  @Test
  void reads_a_multipart_part_whose_bytes_are_a_malformed_escape_as_the_bytes_they_are() {
    MultiValueMap<String, Object> form = hold();
    form.set("cField1", "%zz");

    Map<String, Object> data = dataOf(post(form));

    GrowPaymentProcess process =
        processes
            .find(RUN, (String) data.get("processId"), (String) data.get("processToken"))
            .orElseThrow();
    assertThat(process.customFields()).containsEntry("cField1", "%zz");
  }

  @Test
  void answers_a_multipart_part_larger_than_the_container_accepts_as_a_request_that_said_nothing() {
    MultiValueMap<String, Object> form = hold();
    form.set("description", "A".repeat(1_049_000));

    ResponseEntity<byte[]> response = post(form);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.valueOf("text/html; charset=UTF-8"));
    assertThat(GrowWireText.readable(new String(response.getBody(), UTF_8))).contains("\"id\":707");
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN))).isEmpty();
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, DEFAULT_RUN)))
        .filteredOn(record -> record.direction() == AuditDirection.INBOUND_REQUEST)
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.path()).isEqualTo(CREATE);
              assertThat(record.sanitizedBody()).isEmpty();
            });
  }

  @Test
  void answers_a_multipart_body_the_container_cannot_parse_as_a_request_that_said_nothing() {
    ResponseEntity<byte[]> response =
        http.post()
            .uri(CREATE)
            .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data")
            .body(urlencodedHold())
            .retrieve()
            .toEntity(byte[].class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.valueOf("text/html; charset=UTF-8"));
    assertThat(GrowWireText.readable(new String(response.getBody(), UTF_8))).contains("\"id\":707");
  }

  @Test
  void audits_both_directions_with_credentials_and_tokens_redacted() {
    Map<String, Object> data = dataOf(post(hold()));

    List<AuditRecord> records = ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN));
    assertThat(records)
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
    assertThat(records).allSatisfy(record -> assertThat(record.createdAt()).isEqualTo(FIXED_NOW));

    AuditRecord inbound = records.get(0);
    assertThat(inbound.sanitizedBody())
        .containsEntry("userId", List.of(Redaction.REDACTED))
        .containsEntry("pageCode", List.of(Redaction.REDACTED))
        .containsEntry("cField1", List.of(Redaction.REDACTED))
        .containsEntry("sum", List.of("120.50"));

    AuditRecord outbound = records.get(1);
    assertThat(outbound.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(outbound.httpStatus()).isEqualTo(200);
    assertThat(outbound.providerObjectId()).isEqualTo(data.get("processId"));
    assertThat(outbound.sanitizedBody())
        .containsEntry("data.processToken", List.of(Redaction.REDACTED));

    assertThat(records.toString())
        .doesNotContain(USER_ID, PAGE_CODE, "session-abc", (String) data.get("processToken"));
  }

  @Test
  void the_header_tells_two_test_runs_sharing_one_set_of_credentials_apart_over_http() {
    configureSharedCredentialsRun();

    Map<String, Object> data = dataOf(postNaming(SHARED_CREDENTIALS_RUN.value(), hold()));
    String processId = (String) data.get("processId");
    String processToken = (String) data.get("processToken");

    assertThat(processes.find(SHARED_CREDENTIALS_RUN, processId, processToken))
        .get()
        .extracting(GrowPaymentProcess::merchantId)
        .isEqualTo("merchant-2");
    assertThat(processes.find(RUN, processId, processToken)).isEmpty();
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, SHARED_CREDENTIALS_RUN)))
        .hasSize(2)
        .allSatisfy(
            record ->
                assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.TEST_HEADER));
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN))).isEmpty();
  }

  @Test
  void a_header_naming_an_unknown_test_run_is_refused_and_audited_as_the_header_matching_nothing() {
    ResponseEntity<byte[]> response = postNaming("grow-hosted-process-it-typo", hold());

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(GrowWireText.readable(new String(response.getBody(), UTF_8))).contains("\"id\":701");
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN))).isEmpty();
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, TestRunId.UNKNOWN)))
        .hasSize(2)
        .allSatisfy(
            record ->
                assertThat(record.resolutionRule())
                    .isEqualTo(TestRunResolutionRule.TEST_HEADER_UNMATCHED));
  }

  @Test
  void a_header_naming_a_test_run_configured_without_a_grow_account_routes_to_it() {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", CLOCK_ONLY_RUN.value(),
                "clock", Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts", List.of()))
        .retrieve()
        .toBodilessEntity();

    ResponseEntity<byte[]> response = postNaming(CLOCK_ONLY_RUN.value(), hold());

    assertThat(GrowWireText.readable(new String(response.getBody(), UTF_8))).contains("\"id\":701");
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, CLOCK_ONLY_RUN)))
        .hasSize(2)
        .allSatisfy(
            record ->
                assertThat(record.resolutionRule()).isEqualTo(TestRunResolutionRule.TEST_HEADER));
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN))).isEmpty();
  }

  private void configureSharedCredentialsRun() {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", SHARED_CREDENTIALS_RUN.value(),
                "clock", Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                    List.of(
                        Map.of(
                            "accountId",
                            "merchant-2",
                            "configuration",
                            Map.of("userId", USER_ID, "pageCode", PAGE_CODE)))))
        .retrieve()
        .toBodilessEntity();
  }

  @Test
  void a_process_id_already_taken_in_the_test_run_reports_itself_as_taken() {
    Map<String, Object> data = dataOf(post(hold()));
    String processId = (String) data.get("processId");

    GrowPaymentProcess sameIdDifferentToken =
        processes
            .find(RUN, processId, (String) data.get("processToken"))
            .map(
                stored ->
                    new GrowPaymentProcess(
                        stored.testRunId(),
                        stored.processId(),
                        "f".repeat(32),
                        stored.merchantId(),
                        stored.chargeType(),
                        stored.chargeSum(),
                        stored.description(),
                        stored.saveCardToken(),
                        stored.successUrl(),
                        stored.cancelUrl(),
                        stored.notifyUrl(),
                        stored.payerFullName(),
                        stored.payerPhone(),
                        stored.payerEmail(),
                        stored.customFields(),
                        "f".repeat(32),
                        stored.nativeStatusCode(),
                        stored.nativeStatus(),
                        stored.normalizedStatus(),
                        stored.outcome(),
                        stored.origin(),
                        stored.createdAt(),
                        stored.updatedAt()))
            .orElseThrow();

    assertThat(processes.insertIfAbsent(sameIdDifferentToken)).isFalse();
    assertThat(processes.find(RUN, processId, "f".repeat(32))).isEmpty();
    assertThat(processes.find(RUN, processId, (String) data.get("processToken"))).isPresent();
  }

  @Test
  void the_same_process_id_in_another_test_run_is_not_a_collision() {
    Map<String, Object> data = dataOf(post(hold()));
    String processId = (String) data.get("processId");
    TestRunId elsewhere = TestRunId.of("grow-hosted-process-it-elsewhere");

    GrowPaymentProcess inAnotherRun = GrowAuditFixtures.process(elsewhere, processId);

    assertThat(processes.insertIfAbsent(inAnotherRun)).isTrue();
    assertThat(processes.clear(elsewhere)).isEqualTo(1);
  }

  @Test
  void reset_clears_the_processes_and_the_audit_of_one_test_run_and_reports_the_counts() {
    post(hold());
    post(hold());
    int held = audit.of(GrowLightApi.GROW, RUN).size();
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN))).hasSize(4);

    Map<String, Object> outcome = reset();

    assertThat(outcome)
        .containsEntry("testRunId", RUN.value())
        .containsEntry("provider", "grow")
        .containsEntry("clearedObjects", 2)
        .containsEntry("clearedAuditRecords", held);
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN))).isEmpty();
  }

  @Test
  void reset_leaves_the_configuration_the_credentials_are_matched_against_in_place() {
    post(hold());

    reset();

    assertThat(new String(post(hold()).getBody(), UTF_8)).contains("\"status\":1");
  }

  @Test
  void credentials_in_the_query_string_do_not_authenticate_a_request() {
    ResponseEntity<byte[]> response =
        http.post()
            .uri(
                CREATE
                    + "?pageCode={pageCode}&userId={userId}&chargeType=2&sum=120.50"
                    + "&description=A+hold&successUrl=https://merchant.example/thanks"
                    + "&cancelUrl=https://merchant.example/cancel",
                PAGE_CODE,
                USER_ID)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(new LinkedMultiValueMap<String, Object>())
            .retrieve()
            .toEntity(byte[].class);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(GrowWireText.readable(new String(response.getBody(), UTF_8))).contains("\"id\":707");
    assertThat(ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN))).isEmpty();
  }

  @Test
  void a_form_field_sent_twice_survives_the_wire_and_the_audit_table() {
    MultiValueMap<String, Object> form = hold();
    form.add("sum", "1.00");

    post(form);

    List<AuditRecord> records = ProviderTraffic.in(audit.of(GrowLightApi.GROW, RUN));
    assertThat(records.get(0).sanitizedBody())
        .containsEntry("sum", List.of("120.50", "1.00"))
        .containsEntry("pageCode", List.of(Redaction.REDACTED));
  }

  private Map<String, Object> reset() {
    return resetOf(RUN);
  }

  private void resetEverything() {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("scope", "ALL"))
        .retrieve()
        .toBodilessEntity();
  }

  private Map<String, Object> resetOf(TestRunId testRunId) {
    return http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", testRunId.value(), "scope", "TEST_RUN"))
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private ResponseEntity<byte[]> postUrlencoded(String body) {
    return http.post()
        .uri(CREATE)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(body)
        .retrieve()
        .toEntity(byte[].class);
  }

  private static String urlencodedHold() {
    return "pageCode="
        + PAGE_CODE
        + "&userId="
        + USER_ID
        + "&chargeType=2&sum=120.50&description=A+hold"
        + "&successUrl=https%3A%2F%2Fmerchant.example%2Fthanks"
        + "&cancelUrl=https%3A%2F%2Fmerchant.example%2Fcancel"
        + "&pageField%5BfullName%5D=Israel+Israeli"
        + "&pageField%5Bphone%5D=0505503144";
  }

  private ResponseEntity<byte[]> post(MultiValueMap<String, Object> form) {
    return http.post()
        .uri(CREATE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .header(HttpHeaders.AUTHORIZATION, "Bearer squirrel")
        .body(form)
        .retrieve()
        .toEntity(byte[].class);
  }

  private ResponseEntity<byte[]> postNaming(String testRunId, MultiValueMap<String, Object> form) {
    return http.post()
        .uri(CREATE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .header(HttpHeaders.AUTHORIZATION, "Bearer squirrel")
        .header(TestRunHeader.NAME, testRunId)
        .body(form)
        .retrieve()
        .toEntity(byte[].class);
  }

  private Map<String, Object> parse(ResponseEntity<byte[]> response) {
    return json.readValue(
        GrowWireText.readable(new String(response.getBody(), UTF_8)),
        new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> dataOf(ResponseEntity<byte[]> response) {
    return (Map<String, Object>) parse(response).get("data");
  }

  private static MultiValueMap<String, Object> hold() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "2");
    form.add("sum", "120.50");
    form.add("saveCardToken", "1");
    form.add("description", "A hold");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("notifyUrl", "https://merchant.example/callbacks/grow");
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    form.add("pageField[email]", "payer@merchant.example");
    form.add("cField1", "session-abc");
    form.add("cField2", "intent-99");
    return form;
  }
}
