package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

class ScreenSuccessNoticeIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "screen-success-notice-it";

  private static final String ALIASED_URL =
      "https://notice.provider.example/api/light/server/1.0/createPaymentProcess";

  private static final String GROW_CREATE = "/api/light/server/1.0/createPaymentProcess";

  private static final Pattern THE_RULE_IT_OPENED = Pattern.compile("[?&]open=([^&]+)");

  private static final Pattern A_FIXTURE_ID = Pattern.compile("fx_[0-9a-f]+");

  private static final Pattern A_DELIVERY_ID = Pattern.compile("whjob_[0-9a-f]+");

  private static final Pattern A_PROCESS_ID = Pattern.compile("\"processId\":\"?([^\",}]+)");

  @LocalServerPort private int port;

  private RestClient http;
  private HttpClient browserLike;
  private LocalMerchantServer merchant;

  @BeforeEach
  void aBrowserThatKeepsItsCookie() {
    http = RestClient.create("http://localhost:" + port);
    browserLike =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    merchant = new LocalMerchantServer();
  }

  @AfterEach
  void forgetTheRun() {
    merchant.close();
    for (String provider : List.of("grow", "stripe")) {
      http.post()
          .uri("/__simulator/" + provider + "/reset")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("testRunId", RUN, "scope", "TEST_RUN"))
          .retrieve()
          .onStatus(status -> true, (request, ignored) -> {})
          .toBodilessEntity();
    }
    http.delete()
        .uri("/__simulator/grow/config/accounts/merchant-1?testRunId=" + RUN)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity();
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN, "clock", Map.of(), "accounts", List.of(), "webhooks", List.of()))
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toBodilessEntity();
  }

  @Test
  void configuring_an_account_lands_on_a_page_that_says_so_once_and_not_again_on_reload() {
    Landed configured = aGrowAccountIsConfigured();

    assertThat(configured.page()).contains(notice(Done.THE_ACCOUNT_IS_CONFIGURED));
    assertThat(configured.location()).doesNotContainIgnoringCase("jsessionid");
    assertThat(configured.page()).doesNotContainIgnoringCase("jsessionid");
    assertThat(get(configured.location())).doesNotContain("class=\"done\"");
  }

  @Test
  void pinning_and_advancing_the_clock_each_say_so() {
    aGrowAccountIsConfigured();

    assertThat(postAndFollow("/ui/configuration/clock", Map.of("fixedNow", "2026-09-08T09:30:00Z")))
        .contains(notice(Done.THE_CLOCK_IS_PINNED));
    assertThat(postAndFollow("/ui/configuration/clock/advance", Map.of("advanceBy", "PT1H")))
        .contains(notice(Done.THE_CLOCK_HAS_MOVED_ON));
  }

  @Test
  void binding_and_releasing_a_url_each_say_so() {
    aGrowAccountIsConfigured();

    assertThat(
            postAndFollow(
                "/ui/configuration/route-alias", Map.of("from", ALIASED_URL, "to", GROW_CREATE)))
        .contains(notice(Done.THE_URL_IS_BOUND));

    assertThat(
            postAndFollow(
                "/ui/configuration/route-alias/release",
                Map.of("from", ALIASED_URL, "confirm", ConfigurationScreen.REMOVAL_IS_CONFIRMED)))
        .contains(notice(Done.THE_URL_IS_RELEASED));
  }

  @Test
  void setting_a_provider_setting_says_so() {
    aGrowAccountIsConfigured();

    assertThat(
            postAndFollow(
                "/ui/configuration/settings",
                Map.of(SettingsForm.SUBMITTED_SETTING_PREFIX + "webhook.retryPlan", "PT0S,PT3S")))
        .contains(notice(Done.THE_SETTINGS_ARE_SET));
  }

  @Test
  void writing_and_deleting_a_scenario_rule_each_say_so() {
    aGrowAccountIsConfigured();

    HttpResponse<String> written =
        post(
            "/ui/scenarios/write",
            Map.of(
                "operation", "createPaymentProcess",
                "actionType", "TERMINAL_FAILURE",
                "providerCode", "813"));
    assertThat(written.statusCode()).isEqualTo(302);
    String location = written.headers().firstValue("location").orElseThrow();
    assertThat(get(location)).contains(notice(Done.THE_RULES_ARE_WRITTEN));

    Matcher opened = THE_RULE_IT_OPENED.matcher(location);
    assertThat(opened.find()).isTrue();
    assertThat(postAndFollow("/ui/scenarios/delete", Map.of("scenarioId", opened.group(1))))
        .contains(notice(Done.THE_RULE_IS_DELETED));
  }

  @Test
  void attempting_every_due_delivery_and_resetting_the_run_each_say_so() {
    aGrowAccountIsConfigured();

    assertThat(postAndFollow("/ui/webhooks/deliver-due", Map.of()))
        .contains(notice(Done.EVERY_DUE_DELIVERY_WAS_ATTEMPTED));
    assertThat(postAndFollow("/ui/dashboard/reset", Map.of()))
        .contains("<div class=\"done\" role=\"status\">This test run is reset in every provider.");
  }

  @Test
  void sending_an_event_from_the_webhook_lab_says_so() {
    String process = aGrowProcessWhoseCallbacksReachTheMerchant();

    assertThat(postAndFollow("/ui/webhooks/send", Map.of("providerObjectId", process)))
        .contains(notice(Done.THE_CALLBACK_IS_SCHEDULED));
  }

  @Test
  void sending_a_callback_from_the_payments_own_screen_says_so() {
    String process = aGrowProcessWhoseCallbacksReachTheMerchant();

    assertThat(
            postAndFollow(
                "/ui/objects/webhook",
                Map.of("providerObjectId", process, "confirm", ObjectScreen.A_CHANGE_IS_CONFIRMED)))
        .contains(notice(Done.THE_CALLBACK_IS_SCHEDULED));
  }

  @Test
  void attempting_one_delivery_says_so_and_a_redirected_refusal_of_a_retry_does_not() {
    String process = aGrowProcessWhoseCallbacksReachTheMerchant();
    String lab = postAndFollow("/ui/webhooks/send", Map.of("providerObjectId", process));
    Matcher delivery = A_DELIVERY_ID.matcher(lab);
    assertThat(delivery.find()).isTrue();

    assertThat(postAndFollow("/ui/webhooks/retry", Map.of("deliveryId", delivery.group())))
        .contains(notice(Done.THE_DELIVERY_WAS_ATTEMPTED));

    String refused = postAndFollow("/ui/webhooks/retry", Map.of("deliveryId", "whjob_none"));
    assertThat(refused)
        .contains("class=\"refusal\"")
        .contains(HtmlUtils.htmlEscape(DeliveryForm.NO_DELIVERY_OF_THIS_RUN_ANSWERS_TO_THAT_ID))
        .doesNotContain("class=\"done\"");
  }

  @Test
  void copying_a_scenario_rule_from_the_comparison_says_so() {
    aGrowAccountIsConfigured();
    Map<String, String> rule =
        Map.of(
            "operation", "createPaymentProcess",
            "actionType", "TERMINAL_FAILURE",
            "providerCode", "813");
    HttpResponse<String> written = post("/ui/scenarios/write", rule);
    assertThat(written.statusCode()).isEqualTo(302);
    Matcher opened =
        THE_RULE_IT_OPENED.matcher(written.headers().firstValue("location").orElseThrow());
    assertThat(opened.find()).isTrue();

    Map<String, String> copy = new LinkedHashMap<>(rule);
    copy.put("copiedFrom", opened.group(1));
    assertThat(postAndFollow("/ui/scenarios/diff/copy", copy))
        .contains(notice(Done.THE_COPY_IS_WRITTEN));
  }

  @Test
  void seeding_and_removing_a_payment_method_each_say_so() {
    aStripeAccountIsConfigured();
    Map<String, String> card = new LinkedHashMap<>();
    card.put("provider", "stripe");
    card.put("merchantId", "merchant-1");
    card.put("ownerId", "customer-123");
    card.put("type", "card");
    card.put("alias", "visa-success");
    card.put("brand", "visa");
    card.put("last4", "1111");
    card.put("expMonth", "12");
    card.put("expYear", "2030");
    card.put("funding", "credit");
    card.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "paymentMethodId", "pm_notice_it");

    String seeded = postAndFollow("/ui/payment-methods/seed", card);
    assertThat(seeded).contains(notice(Done.THE_PAYMENT_METHOD_IS_SEEDED));

    Matcher fixture = A_FIXTURE_ID.matcher(seeded);
    assertThat(fixture.find()).isTrue();
    assertThat(
            postAndFollow(
                "/ui/payment-methods/remove",
                Map.of(
                    "provider",
                    "stripe",
                    "fixtureId",
                    fixture.group(),
                    "confirm",
                    PaymentMethodController.REMOVAL_IS_CONFIRMED)))
        .contains(notice(Done.THE_FIXTURE_IS_REMOVED));
  }

  private static String notice(String said) {
    return "<div class=\"done\" role=\"status\">" + HtmlUtils.htmlEscape(said) + "</div>";
  }

  private Landed aGrowAccountIsConfigured() {
    HttpResponse<String> configured =
        post(
            "/ui/configuration/account",
            Map.of(
                "accountId", "merchant-1",
                "field.userId", "notice-user",
                "field.pageCode", "notice-page"));
    assertThat(configured.statusCode()).isEqualTo(302);
    String location = configured.headers().firstValue("location").orElseThrow();
    return new Landed(location, get(location));
  }

  private String aGrowProcessWhoseCallbacksReachTheMerchant() {
    HttpResponse<String> configured =
        post(
            "/ui/configuration/account",
            Map.of(
                "accountId", "merchant-1",
                "field.userId", "notice-user",
                "field.pageCode", "notice-page",
                "callbackUrl", merchant.url()));
    assertThat(configured.statusCode()).isEqualTo(302);
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", "notice-page");
    form.put("userId", "notice-user");
    form.put("chargeType", "2");
    form.put("sum", "120.50");
    form.put("description", "A hold");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("notifyUrl", merchant.url());
    form.put("pageField[fullName]", "A Payer");
    form.put("pageField[phone]", "0505503144");
    HttpResponse<String> created = send(formPost(GROW_CREATE, form));
    Matcher process = A_PROCESS_ID.matcher(created.body());
    assertThat(process.find()).as("createPaymentProcess answered " + created.body()).isTrue();
    return process.group(1);
  }

  private void aStripeAccountIsConfigured() {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN,
                "clock",
                Map.of(),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        "merchant-1",
                        "configuration",
                        Map.of(
                            "secretKey", "sk_test_51NoticeFromAScreen", "captureWindow", "P3D"))),
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private String postAndFollow(String path, Map<String, String> fields) {
    HttpResponse<String> answered = post(path, fields);
    assertThat(answered.statusCode()).as(path + " answered " + answered.body()).isEqualTo(302);
    return get(answered.headers().firstValue("location").orElseThrow());
  }

  private HttpResponse<String> post(String path, Map<String, String> fields) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN);
    form.putAll(fields);
    return send(formPost(path, form));
  }

  private HttpRequest.Builder formPost(String path, Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .timeout(Duration.ofSeconds(20))
        .POST(HttpRequest.BodyPublishers.ofString(String.join("&", pairs)));
  }

  private String get(String location) {
    HttpResponse<String> answered =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port).resolve(location))
                .timeout(Duration.ofSeconds(20))
                .GET());
    assertThat(answered.statusCode()).isEqualTo(200);
    return answered.body();
  }

  private HttpResponse<String> send(HttpRequest.Builder request) {
    try {
      return browserLike.send(request.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private record Landed(String location, String page) {}
}
