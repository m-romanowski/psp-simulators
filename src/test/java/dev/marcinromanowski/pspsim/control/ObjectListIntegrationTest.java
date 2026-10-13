package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import dev.marcinromanowski.pspsim.provider.grow.GrowWireText;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class ObjectListIntegrationTest extends PostgresBackedTest {

  private static final Run MINE =
      new Run(TestRunId.of("object-list-it"), "object-list-user", "object-list-page");

  private static final Run THEIRS =
      new Run(
          TestRunId.of("object-list-it-elsewhere"),
          "object-list-other-user",
          "object-list-other-page");

  private static final String NEVER_USED = "object-list-it-never-used";
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String MERCHANT = "merchant-1";
  private static final int OBJECTS_A_COMPLETED_GROW_PAYMENT_IS = 2;

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String PROCESS_INFO = "/api/light/server/1.0/getPaymentProcessInfo";
  private static final String CHARGE = "/api/light/server/1.0/createTransactionWithToken";
  private static final String OBJECTS = "/__simulator/grow/objects";
  private static final String HELD = "89.90";
  private static final String CURSOR_NAMES_NO_OBJECT =
      "after must name an object of this provider and test run";

  @LocalServerPort private int port;

  private RestClient http;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRuns() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset(MINE);
    reset(THEIRS);
    configure(MINE);
    configure(THEIRS);
  }

  @AfterEach
  void unconfigureTheTestRuns() {
    unconfigure(MINE);
    unconfigure(THEIRS);
    reset(MINE);
    reset(THEIRS);
    merchant.close();
  }

  @Test
  void a_harness_lists_the_payments_of_its_own_test_run_and_none_of_another_runs() {
    String mine = aCompletedPayment(MINE);
    String theirs = aCompletedPayment(THEIRS);

    List<Map<String, Object>> listed = objectsOf(MINE.id().value(), "");

    assertThat(idsOf(listed)).contains(mine).doesNotContain(theirs);
    assertThat(idsOf(objectsOf(THEIRS.id().value(), ""))).contains(theirs).doesNotContain(mine);
    assertThat(listed).extracting(row -> row.get("testRunId")).containsOnly(MINE.id().value());
  }

  @Test
  void a_page_that_filled_its_page_size_hands_back_a_cursor_the_next_page_carries_on_from() {
    aCompletedPayment(MINE);
    aCompletedPayment(MINE);
    aCompletedPayment(MINE);

    List<Map<String, Object>> whole = objectsOf(MINE.id().value(), "&limit=10");
    List<Map<String, Object>> firstPage = objectsOf(MINE.id().value(), "&limit=4");
    List<Map<String, Object>> secondPage =
        objectsOf(MINE.id().value(), "&limit=4&after=" + lastIdOf(firstPage));

    assertThat(whole).hasSize(3 * OBJECTS_A_COMPLETED_GROW_PAYMENT_IS);
    assertThat(firstPage).hasSize(4);
    assertThat(secondPage).hasSize(2);
    assertThat(idsOf(firstPage)).doesNotContainAnyElementsOf(idsOf(secondPage));
    List<String> walked = new ArrayList<>(idsOf(firstPage));
    walked.addAll(idsOf(secondPage));
    assertThat(walked).isEqualTo(idsOf(whole));
  }

  @Test
  void a_page_shorter_than_its_page_size_is_the_end_of_the_list_and_carries_on_into_nothing() {
    aCompletedPayment(MINE);

    List<Map<String, Object>> shortPage = objectsOf(MINE.id().value(), "&limit=10");

    assertThat(shortPage).hasSizeLessThan(10);
    assertThat(objectsOf(MINE.id().value(), "&limit=10&after=" + lastIdOf(shortPage))).isEmpty();
  }

  @Test
  void a_test_run_that_holds_no_object_lists_nothing_rather_than_refusing() {
    aCompletedPayment(MINE);

    assertThat(objectsOf(NEVER_USED, "")).isEmpty();
    assertThat(statusOf(OBJECTS + "?testRunId=" + NEVER_USED)).isEqualTo(200);
  }

  @Test
  void a_cursor_naming_no_object_of_this_run_is_refused_rather_than_answered_as_a_first_page() {
    aCompletedPayment(MINE);
    String elsewhere = aCompletedPayment(THEIRS);
    String run = "?testRunId=" + MINE.id().value();

    assertThat(refusalOf(OBJECTS + run + "&after=" + elsewhere))
        .isEqualTo(new Refusal(404, CURSOR_NAMES_NO_OBJECT));
    assertThat(refusalOf(OBJECTS + run + "&after="))
        .isEqualTo(new Refusal(404, CURSOR_NAMES_NO_OBJECT));
    assertThat(refusalOf(OBJECTS + run + "&limit=all").status()).isEqualTo(400);
    assertThat(refusalOf(OBJECTS + run + "&limit=0").status()).isEqualTo(400);
    assertThat(refusalOf("/__simulator/nosuchprovider/objects").status()).isEqualTo(404);
  }

  @Test
  void a_refusal_of_a_list_names_the_property_and_never_repeats_the_value_that_was_sent() {
    String run = "?testRunId=" + MINE.id().value();

    assertThat(refusalOf(OBJECTS + run + "&limit=all").message()).doesNotContain("all");
    assertThat(refusalOf(OBJECTS + run + "&after=000000").message()).doesNotContain("000000");
  }

  @Test
  void every_object_the_list_hands_back_is_opened_by_the_read_that_takes_one_id() {
    aCompletedPayment(MINE);
    aStoredCardCharge(MINE);

    List<Map<String, Object>> listed = objectsOf(MINE.id().value(), "");

    assertThat(listed).hasSizeGreaterThanOrEqualTo(2);
    for (String id : idsOf(listed)) {
      assertThat(statusOf("/__simulator/grow/state/" + id + "?testRunId=" + MINE.id().value()))
          .isEqualTo(200);
    }
  }

  @Test
  void an_object_the_control_api_seeded_is_told_from_one_provider_traffic_created() {
    String seeded = aCompletedPayment(MINE);
    String charged = aStoredCardCharge(MINE);

    List<Map<String, Object>> listed = objectsOf(MINE.id().value(), "");

    assertThat(byIdOf(listed, seeded)).containsEntry("origin", "CONTROL_API");
    assertThat(byIdOf(listed, charged)).containsEntry("origin", "PROVIDER_API");
    assertThat(state(MINE, seeded)).containsEntry("origin", "CONTROL_API");
  }

  @Test
  void a_listed_object_is_the_same_answer_the_read_that_takes_one_id_gives() {
    String payment = aCompletedPayment(MINE);

    Map<String, Object> listed = byIdOf(objectsOf(MINE.id().value(), ""), payment);

    assertThat(listed).isEqualTo(state(MINE, payment));
  }

  private String aCompletedPayment(Run run) {
    HostedProcess process = aProcess(aHoldForm(run));
    complete(run, process);
    return String.valueOf(theOutcomeOf(run, process).get("transactionId"));
  }

  private String aStoredCardCharge(Run run) {
    HostedProcess process = aProcess(aRegistrationForm(run));
    complete(run, process);
    String cardToken = theCardTokenTheCallbackCarried();
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("cardToken", cardToken);
    form.add("userId", run.userId());
    form.add("sum", HELD);
    form.add("description", "A stored-card charge");
    form.add("paymentType", "2");
    form.add("paymentNum", "1");
    form.add("transactionUniqueIdentifier", process.processId());
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    Map<String, Object> charged =
        GrowWireText.asJson(
            http.post()
                .uri(CHARGE)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(byte[].class)
                .getBody());
    assertThat(charged).containsEntry("status", 1);
    return String.valueOf(dataOf(charged).get("transactionId"));
  }

  private String theCardTokenTheCallbackCarried() {
    List<LocalMerchantServer.ReceivedCallback> received = merchant.received();
    assertThat(received).isNotEmpty();
    String token = received.get(received.size() - 1).fields().get("data[cardToken]");
    assertThat(token).isNotNull().isNotEmpty();
    return token;
  }

  private HostedProcess aProcess(MultiValueMap<String, Object> form) {
    Map<String, Object> created =
        GrowWireText.asJson(
            http.post()
                .uri(CREATE)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(byte[].class)
                .getBody());
    assertThat(created).containsEntry("status", 1);
    Map<String, Object> data = dataOf(created);
    return new HostedProcess(
        String.valueOf(data.get("processId")), String.valueOf(data.get("processToken")));
  }

  private void complete(Run run, HostedProcess process) {
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", run.id().value(), "providerObjectId", process.processId()))
        .retrieve()
        .toBodilessEntity();
  }

  private Map<String, Object> theOutcomeOf(Run run, HostedProcess process) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", run.pageCode());
    form.add("processId", process.processId());
    form.add("processToken", process.processToken());
    Map<String, Object> info =
        GrowWireText.asJson(
            http.post()
                .uri(PROCESS_INFO)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toEntity(byte[].class)
                .getBody());
    assertThat(info).containsEntry("status", 1);
    return firstTransactionOf(info);
  }

  private MultiValueMap<String, Object> aHoldForm(Run run) {
    MultiValueMap<String, Object> form = commonForm(run);
    form.add("chargeType", "2");
    form.add("sum", HELD);
    form.add("description", "A payment");
    return form;
  }

  private MultiValueMap<String, Object> aRegistrationForm(Run run) {
    MultiValueMap<String, Object> form = commonForm(run);
    form.add("chargeType", "3");
    form.add("sum", "1");
    form.add("saveCardToken", "1");
    form.add("description", "A card registration");
    return form;
  }

  private MultiValueMap<String, Object> commonForm(Run run) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", run.pageCode());
    form.add("userId", run.userId());
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("notifyUrl", merchant.url());
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    return form;
  }

  private List<Map<String, Object>> objectsOf(String testRunId, String query) {
    return Objects.requireNonNull(
        http.get()
            .uri(OBJECTS + "?testRunId=" + testRunId + query)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {}));
  }

  private Map<String, Object> state(Run run, String providerObjectId) {
    return Objects.requireNonNull(
        http.get()
            .uri("/__simulator/grow/state/" + providerObjectId + "?testRunId=" + run.id().value())
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
  }

  private static List<String> idsOf(List<Map<String, Object>> rows) {
    List<String> ids = new ArrayList<>();
    rows.forEach(row -> ids.add(String.valueOf(row.get("providerObjectId"))));
    return ids;
  }

  private static String lastIdOf(List<Map<String, Object>> rows) {
    return idsOf(rows).get(rows.size() - 1);
  }

  private static Map<String, Object> byIdOf(List<Map<String, Object>> rows, String id) {
    for (Map<String, Object> row : rows) {
      if (id.equals(row.get("providerObjectId"))) {
        return row;
      }
    }
    throw new AssertionError("the list holds no object under that id");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) Objects.requireNonNull(body).get("data");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstTransactionOf(Map<String, Object> body) {
    return ((List<Map<String, Object>>) dataOf(body).get("transactions")).get(0);
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

  private void configure(Run run) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                run.id().value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("userId", run.userId(), "pageCode", run.pageCode()))),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", merchant.url()))))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure(Run run) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", run.id().value(),
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset(Run run) {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", run.id().value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }

  private record Run(TestRunId id, String userId, String pageCode) {}

  private record HostedProcess(String processId, String processToken) {}

  private record Refusal(int status, String message) {}
}
