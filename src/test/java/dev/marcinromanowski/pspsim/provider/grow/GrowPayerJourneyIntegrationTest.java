package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class GrowPayerJourneyIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-payer-journey-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "grow-journey-user";
  private static final String PAGE_CODE = "grow-journey-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String PROCESS_INFO = "/api/light/server/1.0/getPaymentProcessInfo";
  private static final String CHARGE_TOKEN = "/api/light/server/1.0/createTransactionWithToken";
  private static final String TRANSACTION_INFO = "/api/light/server/1.0/getTransactionInfo";
  private static final String REFUND = "/api/light/server/1.0/refundTransaction";
  private static final String FIXTURES = "/__simulator/grow/fixtures/payment-methods";
  private static final String HELD = "120.50";
  private static final String SANDBOX_CARD = "sandbox-4580";
  private static final String SECOND_CARD = "sandbox-1121";
  private static final String SECOND_SUFFIX = "1121";

  @LocalServerPort private int port;

  private RestClient http;
  private HttpClient payer;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    payer = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
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
  void a_tester_takes_a_hold_to_a_settled_payment_using_nothing_but_published_routes() {
    Map<String, Object> created = create(hold());
    String processId = (String) created.get("processId");
    String processToken = (String) created.get("processToken");

    HttpResponse<String> page = open((String) created.get("url"));
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains(SANDBOX_CARD).contains("A hold");
    assertThat(page.headers().allValues("Content-Security-Policy"))
        .containsExactly("default-src 'none'; style-src 'unsafe-inline'");
    assertThat(page.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(page.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(page.headers().firstValue("Referrer-Policy")).contains("no-referrer");

    HttpResponse<String> completed = pay((String) created.get("url"), "PAID", SANDBOX_CARD);
    assertThat(completed.statusCode()).isEqualTo(303);
    assertThat(completed.headers().firstValue("Location").orElseThrow())
        .startsWith("https://merchant.example/thanks")
        .contains("cField1=session-abc");
    assertThat(completed.headers().allValues("Content-Security-Policy"))
        .as("a redirect has no document, so it declares nothing a browser could apply")
        .isEmpty();
    assertThat(completed.headers().allValues("Cache-Control")).isEmpty();

    assertThat(merchant.received()).hasSize(1);
    Map<String, String> callback = merchant.received().get(0).fields();
    assertThat(callback)
        .containsEntry("status", "1")
        .containsEntry("data[statusCode]", "11")
        .containsEntry("data[processId]", processId)
        .containsEntry("data[sum]", HELD)
        .containsEntry("data[customFields][cField2]", "intent-99")
        .containsEntry("data[cardSuffix]", "4580");

    String transactionId = callback.get("data[transactionId]");
    String transactionToken = callback.get("data[transactionToken]");
    assertThat(transactionId).isNotBlank();
    assertThat(transactionToken).isNotBlank();

    Map<String, Object> settled = dataOf(settle(transactionId, transactionToken, HELD));
    assertThat(settled).containsEntry("statusCode", "2").containsEntry("sum", HELD);

    Map<String, Object> reconciled = dataOf(processInfo(processId, processToken));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) reconciled.get("transactions");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("statusCode", "2");

    assertThat(state(transactionId)).containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void a_token_only_process_the_payer_completed_mints_a_card_token_a_stored_charge_accepts() {
    Map<String, Object> created = create(tokenOnly());

    pay((String) created.get("url"), "PAID", SANDBOX_CARD);

    Map<String, String> callback = merchant.received().get(0).fields();
    assertThat(callback).containsEntry("data[statusCode]", "11");
    assertThat(callback).doesNotContainKey("data[customFields][cField2]");
    String cardToken = callback.get("data[cardToken]");
    assertThat(cardToken).isNotBlank();

    Map<String, Object> charged = dataOf(chargeStoredCard(cardToken));
    assertThat(String.valueOf(charged.get("statusCode"))).isEqualTo("2");
    assertThat(state(String.valueOf(charged.get("transactionId"))))
        .containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void a_payer_who_abandons_the_page_leaves_a_process_the_merchant_can_reconcile_and_no_callback() {
    Map<String, Object> created = create(hold());

    HttpResponse<String> left = pay((String) created.get("url"), "ABANDONED", null);

    assertThat(left.statusCode()).isEqualTo(303);
    assertThat(left.headers().firstValue("Location").orElseThrow())
        .startsWith("https://merchant.example/cancel");
    assertThat(merchant.received()).isEmpty();

    Map<String, Object> reconciled =
        dataOf(
            processInfo((String) created.get("processId"), (String) created.get("processToken")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows = (List<Map<String, Object>>) reconciled.get("transactions");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("statusCode", "0").containsEntry("transactionId", "0");
  }

  @Test
  void a_payer_who_meets_a_decline_causes_the_failure_callback_the_contract_publishes() {
    Map<String, Object> created = create(hold());

    HttpResponse<String> declined = pay((String) created.get("url"), "DECLINED", null);

    assertThat(declined.statusCode()).isEqualTo(303);
    Map<String, String> callback = merchant.received().get(0).fields();
    assertThat(callback).containsEntry("status", "0").containsEntry("data", "");
    assertThat(callback).doesNotContainKey("data[statusCode]");
    assertThat(callback.get("err[id]")).isNotBlank();
  }

  @Test
  void the_control_route_completes_a_process_into_the_same_hold_the_page_would_have() {
    Map<String, Object> created = create(hold());
    String processId = (String) created.get("processId");

    Map<String, Object> answered =
        http.post()
            .uri("/__simulator/grow/hosted/" + processId + "/complete")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("testRunId", RUN.value(), "outcome", "PAID", "card", SANDBOX_CARD))
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(answered)
        .containsEntry("providerObjectId", processId)
        .containsEntry("outcome", "PAID");
    String transactionId = (String) answered.get("createdObjectId");
    assertThat(transactionId).isNotBlank();

    Map<String, String> callback = merchant.received().get(0).fields();
    assertThat(callback).containsEntry("data[transactionId]", transactionId);

    assertThat(dataOf(settle(transactionId, callback.get("data[transactionToken]"), HELD)))
        .containsEntry("statusCode", "2");
  }

  @Test
  void a_run_with_no_callback_target_refuses_the_payer_and_pays_once_the_tester_configures_one() {
    configureWithoutACallbackTarget();
    Map<String, Object> created = create(hold());

    HttpResponse<String> refused = pay((String) created.get("url"), "PAID", SANDBOX_CARD);

    assertThat(refused.statusCode()).isEqualTo(422);
    assertThat(refused.body())
        .contains(GrowCallbacks.NOTIFY_URL_IS_NOT_A_CONFIGURED_TARGET)
        .contains(CompleteHostedProcess.STILL_COMPLETABLE);
    assertThat(merchant.received()).isEmpty();

    configure();
    HttpResponse<String> paid = pay((String) created.get("url"), "PAID", SANDBOX_CARD);

    assertThat(paid.statusCode()).isEqualTo(303);
    assertThat(merchant.received()).hasSize(1);
    Map<String, String> callback = merchant.received().get(0).fields();
    assertThat(
            dataOf(
                settle(
                    callback.get("data[transactionId]"),
                    callback.get("data[transactionToken]"),
                    HELD)))
        .containsEntry("statusCode", "2");
  }

  @Test
  void a_payer_who_abandons_a_page_of_a_run_with_no_callback_target_still_leaves_it_reconciled() {
    configureWithoutACallbackTarget();
    Map<String, Object> created = create(hold());

    HttpResponse<String> left = pay((String) created.get("url"), "ABANDONED", null);

    assertThat(left.statusCode()).isEqualTo(303);
    assertThat(
            theRowOf(
                processInfo(
                    (String) created.get("processId"), (String) created.get("processToken"))))
        .containsEntry("statusCode", "0");
  }

  @Test
  void a_second_completion_of_a_process_the_payer_already_finished_is_refused() {
    Map<String, Object> created = create(hold());

    pay((String) created.get("url"), "PAID", SANDBOX_CARD);
    HttpResponse<String> again = pay((String) created.get("url"), "PAID", SANDBOX_CARD);

    assertThat(again.statusCode()).isEqualTo(422);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void a_link_no_process_was_issued_under_opens_nothing() {
    HttpResponse<String> nothing = open("http://localhost:" + port + "/far?l=" + "0".repeat(32));

    assertThat(nothing.statusCode()).isEqualTo(404);
  }

  @Test
  void every_surface_that_names_a_card_names_the_one_the_payer_chose_and_not_the_first_offered() {
    Map<String, Object> created = create(hold());
    String processId = (String) created.get("processId");
    String processToken = (String) created.get("processToken");

    pay((String) created.get("url"), "PAID", SECOND_CARD);

    Map<String, String> callback = merchant.received().get(0).fields();
    assertThat(callback).containsEntry("data[cardSuffix]", SECOND_SUFFIX);
    String transactionId = callback.get("data[transactionId]");
    String transactionToken = callback.get("data[transactionToken]");

    assertThat(dataOf(transactionInfo(transactionId, transactionToken)))
        .containsEntry("cardSuffix", SECOND_SUFFIX);
    assertThat(dataOf(settle(transactionId, transactionToken, HELD)))
        .containsEntry("cardSuffix", SECOND_SUFFIX);
    assertThat(theRowOf(processInfo(processId, processToken)))
        .containsEntry("cardSuffix", SECOND_SUFFIX);
    assertThat(dataOf(refund(transactionId, transactionToken, HELD)))
        .containsEntry("cardSuffix", SECOND_SUFFIX);
  }

  @Test
  void a_process_completed_through_the_control_callback_route_charges_the_published_sandbox_card() {
    Map<String, Object> created = create(hold());
    String processId = (String) created.get("processId");

    completeThroughTheCallbackRoute(processId);

    Map<String, String> callback = merchant.received().get(0).fields();
    assertThat(callback).containsEntry("data[cardSuffix]", GrowSandboxCard.SUFFIX);
    String transactionId = callback.get("data[transactionId]");
    String transactionToken = callback.get("data[transactionToken]");

    assertThat(dataOf(transactionInfo(transactionId, transactionToken)))
        .containsEntry("cardSuffix", GrowSandboxCard.SUFFIX)
        .containsEntry("cardBrand", GrowSandboxCard.BRAND)
        .containsEntry("cardExp", GrowSandboxCard.EXPIRY);
    assertThat(dataOf(settle(transactionId, transactionToken, HELD)))
        .containsEntry("cardSuffix", GrowSandboxCard.SUFFIX);
  }

  @Test
  void a_stored_card_charge_describes_the_card_the_payer_registered_and_not_the_first_offered() {
    Map<String, Object> created = create(tokenOnly());

    pay((String) created.get("url"), "PAID", SECOND_CARD);

    String cardToken = merchant.received().get(0).fields().get("data[cardToken]");
    Map<String, Object> charged = dataOf(chargeStoredCard(cardToken));

    assertThat(charged).containsEntry("cardSuffix", SECOND_SUFFIX);
    assertThat(
            dataOf(
                transactionInfo(
                    String.valueOf(charged.get("transactionId")),
                    String.valueOf(charged.get("transactionToken")))))
        .containsEntry("cardSuffix", SECOND_SUFFIX);
  }

  @Test
  void a_payer_who_pays_with_a_seeded_fixture_is_described_by_its_brand_and_expiry_too() {
    seed("mastercard-debit", "mastercard", "4444", 3, 2029, "debit");
    Map<String, Object> created = create(hold());
    String transactionId;
    String transactionToken;

    pay((String) created.get("url"), "PAID", "mastercard-debit");

    Map<String, String> callback = merchant.received().get(0).fields();
    transactionId = callback.get("data[transactionId]");
    transactionToken = callback.get("data[transactionToken]");
    assertThat(callback)
        .containsEntry("data[cardSuffix]", "4444")
        .containsEntry("data[cardBrand]", "mastercard")
        .containsEntry("data[cardBrandCode]", "2")
        .containsEntry("data[cardType]", "debit")
        .containsEntry("data[cardTypeCode]", "4")
        .containsEntry("data[cardExp]", "0329");

    assertThat(dataOf(settle(transactionId, transactionToken, HELD)))
        .containsEntry("cardSuffix", "4444")
        .containsEntry("cardBrand", "mastercard")
        .containsEntry("cardBrandCode", "2")
        .containsEntry("cardType", "debit")
        .containsEntry("cardTypeCode", "4")
        .containsEntry("cardExp", "0329");
  }

  @Test
  void a_fixture_whose_brand_grow_publishes_no_code_for_is_looked_up_with_an_empty_code() {
    seed("amex-credit", "amex", "0005", 3, 2029, "credit");
    Map<String, Object> created = create(hold());

    pay((String) created.get("url"), "PAID", "amex-credit");

    Map<String, String> callback = merchant.received().get(0).fields();
    assertThat(callback)
        .containsEntry("data[cardBrandCode]", "")
        .containsEntry("data[cardTypeCode]", "");

    assertThat(
            dataOf(
                transactionInfo(
                    callback.get("data[transactionId]"), callback.get("data[transactionToken]"))))
        .containsEntry("cardSuffix", "0005")
        .containsEntry("cardBrandCode", "")
        .containsEntry("cardTypeCode", "");
  }

  private HttpResponse<String> open(String publishedUrl) {
    return send(HttpRequest.newBuilder(URI.create(payerUrlOf(publishedUrl))).GET().build());
  }

  private HttpResponse<String> pay(String publishedUrl, String outcome, String card) {
    String body =
        "l=" + linkOf(publishedUrl) + "&outcome=" + outcome + (card == null ? "" : "&card=" + card);
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/far"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build());
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return payer.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException unreachable) {
      throw new AssertionError(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AssertionError(interrupted);
    }
  }

  private String payerUrlOf(String publishedUrl) {
    return "http://localhost:" + port + "/far?l=" + linkOf(publishedUrl);
  }

  private static String linkOf(String publishedUrl) {
    return publishedUrl.substring(publishedUrl.indexOf("?l=") + "?l=".length());
  }

  private Map<String, Object> create(MultiValueMap<String, Object> form) {
    return dataOf(
        http.post()
            .uri(CREATE)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(form)
            .retrieve()
            .toEntity(byte[].class)
            .getBody());
  }

  private byte[] settle(String transactionId, String transactionToken, String sum) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("userId", USER_ID);
    form.add("transactionId", transactionId);
    form.add("transactionToken", transactionToken);
    form.add("sum", sum);
    return http.post()
        .uri(SETTLE)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class)
        .getBody();
  }

  private byte[] processInfo(String processId, String processToken) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("processId", processId);
    form.add("processToken", processToken);
    return http.post()
        .uri(PROCESS_INFO)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class)
        .getBody();
  }

  private byte[] chargeStoredCard(String cardToken) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("userId", USER_ID);
    form.add("cardToken", cardToken);
    form.add("sum", "42.00");
    form.add("description", "A stored card charge");
    form.add("paymentType", "2");
    form.add("paymentNum", "1");
    form.add("transactionUniqueIdentifier", "778899");
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    return http.post()
        .uri(CHARGE_TOKEN)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class)
        .getBody();
  }

  private Map<String, Object> state(String providerObjectId) {
    return http.get()
        .uri("/__simulator/grow/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private byte[] transactionInfo(String transactionId, String transactionToken) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("transactionId", transactionId);
    form.add("transactionToken", transactionToken);
    return http.post()
        .uri(TRANSACTION_INFO)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class)
        .getBody();
  }

  private byte[] refund(String transactionId, String transactionToken, String refundSum) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("userId", USER_ID);
    form.add("transactionId", transactionId);
    form.add("transactionToken", transactionToken);
    form.add("refundSum", refundSum);
    return http.post()
        .uri(REFUND)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class)
        .getBody();
  }

  private void completeThroughTheCallbackRoute(String processId) {
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", processId))
        .retrieve()
        .toBodilessEntity();
  }

  private void seed(
      String alias, String brand, String last4, int expMonth, int expYear, String funding) {
    http.post()
        .uri(FIXTURES)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "merchantId",
                MERCHANT,
                "ownerId",
                "payer-1",
                "type",
                "card",
                "alias",
                alias,
                "card",
                Map.of(
                    "brand",
                    brand,
                    "last4",
                    last4,
                    "expMonth",
                    expMonth,
                    "expYear",
                    expYear,
                    "funding",
                    funding)))
        .retrieve()
        .toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> theRowOf(byte[] response) {
    List<Map<String, Object>> rows =
        (List<Map<String, Object>>) dataOf(response).get("transactions");
    return rows.get(0);
  }

  private static Map<String, Object> dataOf(byte[] response) {
    Map<String, Object> body = GrowWireText.asJson(response);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    return data;
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

  private void configureWithoutACallbackTarget() {
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
                List.of()))
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

  private MultiValueMap<String, Object> hold() {
    MultiValueMap<String, Object> form = base();
    form.add("chargeType", "2");
    form.add("sum", HELD);
    form.add("description", "A hold");
    form.add("cField2", "intent-99");
    return form;
  }

  private MultiValueMap<String, Object> tokenOnly() {
    MultiValueMap<String, Object> form = base();
    form.add("chargeType", "3");
    form.add("sum", "1");
    form.add("description", "A card registration");
    return form;
  }

  private MultiValueMap<String, Object> base() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("saveCardToken", "1");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("notifyUrl", merchant.url());
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    form.add("pageField[email]", "payer@merchant.example");
    form.add("cField1", "session-abc");
    return form;
  }
}
