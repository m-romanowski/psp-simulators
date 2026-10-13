package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryHasNoTargetException;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

class AzericardPayerPageIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("azericard-page-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-29T12:00:00Z");
  private static final String TERMINAL_ID = "70000029";
  private static final String MERCHANT = "merchant-1";
  private static final String MERCH_URL = "https://merchant.example/shop";
  private static final String A_PUBLISHED_PAN = "4127208104942601";
  private static final String M_INFO = "eyJtb2JpbGVQaG9uZSI6Ijk5NDUwNzMwNzMwNyJ9";
  private static final long PAUSE = 73073L;
  private static final String FAILING_ENQUEUE = "azericard_page_it_refuses_the_enqueue";
  private static final String PAUSED_ENQUEUE = "azericard_page_it_pauses_the_enqueue";
  private static final String PAUSED_OPENING = "azericard_page_it_pauses_the_opening";
  private static final String PAUSED_RESET = "azericard_page_it_pauses_the_reset";

  private static final List<String> THE_TABLES_A_PAYMENT_WRITES =
      List.of(
          "azericard_payment",
          "azericard_stored_card",
          "azericard_payer_session",
          "azericard_keyed_exchange",
          "simulator_audit_record",
          "simulator_webhook_delivery");

  @LocalServerPort private int port;

  @Autowired private JdbcClient jdbc;

  @Autowired private DataSource dataSource;

  @Autowired private JdbcConnectionDetails database;

  private RestClient http;
  private LocalMerchantServer merchant;
  private final HttpClient wire = HttpClient.newHttpClient();

  @BeforeEach
  void configureTheTerminal() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    configure();
  }

  @AfterEach
  void unconfigureTheTerminal() {
    for (String trigger : List.of(FAILING_ENQUEUE, PAUSED_ENQUEUE, PAUSED_OPENING, PAUSED_RESET)) {
      jdbc.sql("drop trigger if exists " + trigger + " on simulator_webhook_delivery").update();
      jdbc.sql("drop trigger if exists " + trigger + " on azericard_payer_session").update();
      jdbc.sql("drop function if exists " + trigger + "()").update();
    }
    reset();
    unconfigure();
    merchant.close();
  }

  @Test
  void the_page_this_gateway_serves_is_the_one_the_dashboard_says_this_module_keeps_waiting() {
    String session =
        theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120144"))).body());

    assertThat(stateOf(session))
        .as("the object the page left open publishes the facts of one a payer finishes")
        .extractingByKey("hostedSession")
        .isNotNull();
    assertThat(page("/ui/dashboard?testRunId=" + RUN.value()))
        .as("and the declaration every screen reads names that page among what this module keeps")
        .contains("azericard a payment page");
  }

  @Test
  void a_card_save_is_handed_over_to_a_page_that_saves_the_card_and_calls_the_merchant_back() {
    Map<String, String> withMInfo = cardSave("20260929120101");
    withMInfo.put(AzericardFields.M_INFO, M_INFO);
    HttpResponse<String> handedOver = post("/token/cgi_link", signed(withMInfo));

    assertThat(handedOver.statusCode()).isEqualTo(200);
    HttpResponse<String> page = handedOver;
    String session = theSessionIn(page.body());
    assertThat(page.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
    assertThat(page.body()).contains("name=\"cardNumber\"").contains("0.01 AZN");
    assertThat(stateOf(session)).containsEntry("normalizedStatus", "NOT_INITIALIZED");

    HttpResponse<String> result =
        post(
            "/azericard/payer",
            Map.of(
                "session", session,
                "cardNumber", A_PUBLISHED_PAN,
                "expiry", "05/31",
                "cvc", "796",
                "action", "pay"));

    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(result.body()).contains("Payment successful").contains(MERCH_URL);
    deliverDue();
    Map<String, String> callback = merchant.received().get(0).fields();
    assertThat(callback)
        .containsEntry(AzericardFields.CARD, "************2601")
        .containsKey(AzericardFields.TOKEN)
        .containsEntry(AzericardFields.ACTION, "0");
    assertThat(stateOf(callback.get(AzericardFields.TOKEN)))
        .containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(stateOf(session)).containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(result.body() + page.body() + audit() + callback).doesNotContain(A_PUBLISHED_PAN);
    assertThat(result.headers().firstValue("Content-Security-Policy"))
        .hasValueSatisfying(csp -> assertThat(csp).contains("frame-ancestors 'none'"));
    for (String table : THE_TABLES_A_PAYMENT_WRITES) {
      assertThat(
              jdbc.sql("select t::text from " + table + " t where t::text like ?")
                  .param("%" + RUN.value() + "%")
                  .query(String.class)
                  .list())
          .as(table + " holds rows of this run and no card number, CVC field or M_INFO")
          .isNotEmpty()
          .allSatisfy(
              row ->
                  assertThat(row)
                      .doesNotContain(A_PUBLISHED_PAN)
                      .doesNotContain(M_INFO)
                      .doesNotContain("\"cvc\": [\"796\"]")
                      .doesNotContain("\"CVC2\""));
    }
  }

  @Test
  void a_second_page_for_the_order_an_open_page_holds_is_refused_as_a_duplicate() {
    assertThat(post("/token/cgi_link", signed(cardSave("20260929120103"))).body())
        .contains("name=\"cardNumber\"");

    Map<String, String> another = cardSave("20260929120103");
    another.put(AzericardFields.NONCE, "0A0B0C0D0E0F1011");

    HttpResponse<String> second = post("/token/cgi_link", signed(another));

    assertThat(second.body()).contains("<action>1</action>").doesNotContain("cardNumber");
  }

  @Test
  void a_repost_of_the_signed_form_while_its_page_is_open_is_served_the_same_page() {
    Map<String, String> form = signed(cardSave("20260929120105"));
    String session = theSessionIn(post("/token/cgi_link", form).body());

    HttpResponse<String> again = post("/token/cgi_link", form);

    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(again.body()).contains("name=\"cardNumber\"").doesNotContain("<action>");
    assertThat(theSessionIn(again.body())).isEqualTo(session);
    assertThat(again.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(rowsOf("azericard_payer_session")).isEqualTo(1);
    assertThat(pay(session).body()).contains("Payment successful");
  }

  @Test
  void a_callback_that_cannot_be_queued_leaves_no_payment_and_the_page_payable_again() {
    String session =
        theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120106"))).body());
    onInsertInto("simulator_webhook_delivery", FAILING_ENQUEUE, "raise exception 'refused'");

    HttpResponse<String> failed = pay(session);

    assertThat(failed.statusCode()).isEqualTo(500);
    assertThat(failed.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(failed.headers().firstValue("Content-Security-Policy"))
        .hasValueSatisfying(csp -> assertThat(csp).contains("frame-ancestors 'none'"));
    for (String table :
        List.of(
            "azericard_payment",
            "azericard_stored_card",
            "azericard_keyed_exchange",
            "azericard_int_ref_claim",
            "simulator_webhook_delivery")) {
      assertThat(rowsOf(table)).as(table).isZero();
    }
    assertThat(
            jdbc.sql("select outcome from azericard_payer_session where session_id = ?")
                .param(session)
                .query(String.class)
                .optional())
        .isEmpty();
    assertThat(
            jdbc.sql("select held_by_session from azericard_order_trace where test_run_id = ?")
                .param(RUN.value())
                .query(String.class)
                .single())
        .isEqualTo(session);
    assertThat(lockableNow(session)).isTrue();
    jdbc.sql("drop trigger " + FAILING_ENQUEUE + " on simulator_webhook_delivery").update();

    HttpResponse<String> paid = pay(session);

    assertThat(paid.body()).contains("Payment successful");
    assertThat(rowsOf("azericard_payment")).isEqualTo(1);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void a_page_held_by_a_submission_is_busy_and_is_released_by_the_database_when_that_holder_dies()
      throws SQLException {
    String session =
        theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120107"))).body());
    try (Connection holder = aConnectionOfItsOwn()) {
      holder.setAutoCommit(false);
      int pid = backendOf(holder);
      try (PreparedStatement lock =
          holder.prepareStatement(
              "select 1 from azericard_payer_session where session_id = ? for update")) {
        lock.setString(1, session);
        lock.executeQuery().close();
      }

      HttpResponse<String> busy = pay(session);

      assertThat(busy.statusCode()).isEqualTo(409);
      assertThat(busy.body()).contains("Payment in progress");
      assertThat(rowsOf("azericard_payment")).isZero();
      assertThat(
              jdbc.sql("select pg_terminate_backend(?)").param(pid).query(Boolean.class).single())
          .isTrue();
    }

    assertThat(pay(session).body()).contains("Payment successful");
  }

  @Test
  void an_s2s_authorization_for_the_order_an_open_page_holds_is_refused_as_a_duplicate() {
    String session =
        theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120108"))).body());
    Map<String, String> s2s = cardSave("20260929120108");
    s2s.put(AzericardFields.NONCE, "0A0B0C0D0E0F1011");
    s2s.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);

    HttpResponse<String> refused = post("/token/cgi_link", signed(s2s));

    assertThat(refused.body()).contains("<action>1</action>");
    assertThat(rowsOf("azericard_payment")).isZero();
    assertThat(pay(session).body()).contains("Payment successful");
    assertThat(merchant.received())
        .singleElement()
        .satisfies(callback -> assertThat(callback.fields()).containsEntry("ACTION", "0"));
  }

  @Test
  void an_order_paid_while_its_second_handoff_was_opening_a_page_opens_no_second_page()
      throws Exception {
    String session =
        theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120109"))).body());
    onInsertInto(
        "azericard_payer_session", PAUSED_OPENING, "perform pg_advisory_xact_lock(" + PAUSE + ")");
    Map<String, String> second = cardSave("20260929120109");
    second.put(AzericardFields.NONCE, "0A0B0C0D0E0F1011");
    ExecutorService browser = Executors.newSingleThreadExecutor();
    try (Connection pauser = aConnectionOfItsOwn()) {
      pause(pauser);
      Future<HttpResponse<String>> handedOver =
          browser.submit(() -> post("/token/cgi_link", signed(second)));
      awaitWaiting(
          handedOver,
          "select count(*) from pg_locks where locktype = 'advisory' and objid = "
              + PAUSE
              + " and not granted");

      assertThat(pay(session).body()).contains("Payment successful");
      resume(pauser);

      assertThat(handedOver.get(30, TimeUnit.SECONDS).body())
          .contains("<action>1</action>")
          .doesNotContain("cardNumber");
    } finally {
      browser.shutdownNow();
    }
    assertThat(rowsOf("azericard_payer_session")).isEqualTo(1);
  }

  @Test
  void a_reset_while_a_page_is_being_settled_leaves_nothing_of_the_run_behind() throws Exception {
    String session =
        theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120110"))).body());
    onInsertInto(
        "simulator_webhook_delivery",
        PAUSED_ENQUEUE,
        "perform pg_advisory_xact_lock(" + PAUSE + ")");
    ExecutorService browsers = Executors.newFixedThreadPool(2);
    try (Connection pauser = aConnectionOfItsOwn()) {
      pause(pauser);
      Future<HttpResponse<String>> paying = browsers.submit(() -> pay(session));
      awaitWaiting(
          paying,
          "select count(*) from pg_locks where locktype = 'advisory' and objid = "
              + PAUSE
              + " and not granted");
      Future<?> resetting = browsers.submit(this::reset);
      awaitWaiting(
          resetting,
          "select count(*) from pg_stat_activity where wait_event_type = 'Lock'"
              + " and query like '%delete from azericard%'");
      resume(pauser);

      paying.get(30, TimeUnit.SECONDS);
      resetting.get(30, TimeUnit.SECONDS);
    } finally {
      browsers.shutdownNow();
    }
    for (String table :
        List.of(
            "azericard_payment",
            "azericard_payer_session",
            "azericard_order_trace",
            "azericard_keyed_exchange",
            "azericard_int_ref_claim",
            "azericard_stored_card")) {
      assertThat(rowsOf(table)).as(table).isZero();
    }
  }

  @Test
  void a_story_that_faults_the_first_payment_and_passes_the_second_moves_on_the_payer_page() {
    aFaultThenSuccessStory("page-story");
    String session =
        theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120111"))).body());

    HttpResponse<String> unreached = pay(session);
    HttpResponse<String> paid = pay(session);

    assertThat(unreached.statusCode()).isEqualTo(502);
    assertThat(paid.statusCode()).isEqualTo(200);
    assertThat(paid.body()).contains("Payment successful");
    assertThat(rowsOf("azericard_payment")).isEqualTo(1);
  }

  @Test
  void a_story_that_faults_the_first_completion_and_passes_the_second_moves_on_the_control_api() {
    aFaultThenSuccessStory("complete-story");
    String session =
        theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120112"))).body());

    HttpResponse<String> unreached = complete(session);
    HttpResponse<String> completed = complete(session);

    assertThat(unreached.statusCode()).isEqualTo(422);
    assertThat(unreached.body()).contains("a scenario rule of this test run answered");
    assertThat(completed.statusCode()).isEqualTo(200);
    assertThat(completed.body()).contains("\"outcome\":\"PAID\"");
    assertThat(rowsOf("azericard_payment")).isEqualTo(1);
  }

  @Test
  void an_s2s_card_save_whose_callback_cannot_be_queued_leaves_no_payment_and_can_be_sent_again() {
    Map<String, String> s2s = cardSave("20260929120113");
    s2s.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    Map<String, String> form = signed(s2s);
    onInsertInto("simulator_webhook_delivery", FAILING_ENQUEUE, "raise exception 'refused'");

    HttpResponse<String> failed = post("/token/cgi_link", form);

    assertThat(failed.statusCode()).isEqualTo(500);
    for (String table :
        List.of(
            "azericard_payment",
            "azericard_stored_card",
            "azericard_order_trace",
            "azericard_keyed_exchange",
            "azericard_int_ref_claim",
            "simulator_webhook_delivery")) {
      assertThat(rowsOf(table)).as(table).isZero();
    }
    jdbc.sql("drop trigger " + FAILING_ENQUEUE + " on simulator_webhook_delivery").update();

    HttpResponse<String> saved = post("/token/cgi_link", form);

    assertThat(saved.body()).contains("<action>0</action>");
    assertThat(rowsOf("azericard_payment")).isEqualTo(1);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void a_page_handed_off_while_a_reset_runs_is_cleared_with_its_trace_or_survives_with_it()
      throws Exception {
    post("/token/cgi_link", signed(cardSave("20260929120114")));
    jdbc.sql(
            "create function "
                + PAUSED_RESET
                + "() returns trigger language plpgsql as $$ begin perform"
                + " pg_advisory_xact_lock("
                + PAUSE
                + "); return old; end $$")
        .update();
    jdbc.sql(
            "create trigger "
                + PAUSED_RESET
                + " before delete on azericard_payer_session for each row when (old.test_run_id"
                + " = '"
                + RUN.value()
                + "') execute function "
                + PAUSED_RESET
                + "()")
        .update();
    ExecutorService resetter = Executors.newSingleThreadExecutor();
    String session;
    try (Connection pauser = aConnectionOfItsOwn()) {
      pause(pauser);
      Future<?> resetting = resetter.submit(this::reset);
      awaitWaiting(
          resetting,
          "select count(*) from pg_locks where locktype = 'advisory' and objid = "
              + PAUSE
              + " and not granted");
      session = theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120115"))).body());
      resume(pauser);
      resetting.get(30, TimeUnit.SECONDS);
    } finally {
      resetter.shutdownNow();
    }

    assertThat(
            jdbc.sql(
                    "select count(*) from azericard_payer_session s where s.test_run_id = ? and"
                        + " s.outcome is null and not exists (select 1 from azericard_order_trace"
                        + " t where t.held_by_session = s.session_id)")
                .param(RUN.value())
                .query(Integer.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql(
                    "select count(*) from azericard_order_trace t where t.test_run_id = ? and"
                        + " t.held_by_session is not null and not exists (select 1 from"
                        + " azericard_payer_session s where s.session_id = t.held_by_session)")
                .param(RUN.value())
                .query(Integer.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql("select trace from azericard_order_trace where test_run_id = ?")
                .param(RUN.value())
                .query(String.class)
                .list())
        .containsExactly("120115");
    assertThat(rowsOf("azericard_payer_session")).isEqualTo(1);
    assertThat(pay(session).body()).contains("Payment successful");
  }

  @Test
  void an_s2s_card_save_whose_backref_is_no_target_is_answered_and_its_callback_is_undeliverable() {
    Map<String, String> s2s = cardSave("20260929120116");
    s2s.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    s2s.put(AzericardFields.BACKREF, "https://nobody.example/callback");

    HttpResponse<String> saved = post("/token/cgi_link", signed(s2s));

    assertThat(saved.body()).startsWith("<response>").contains("<action>0</action>");
    assertThat(merchant.received()).isEmpty();
    List<Map<String, Object>> deliveries =
        http.get()
            .uri("/__simulator/azericard/webhooks?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    assertThat(deliveries)
        .singleElement()
        .satisfies(
            delivery ->
                assertThat(delivery)
                    .containsEntry("status", "UNDELIVERABLE")
                    .containsEntry("targetUrl", "https://nobody.example/callback")
                    .containsEntry(
                        "notSentBecause", AzericardCallbacks.NOT_A_TARGET_OF_THE_ACCOUNT));
    assertThat(
            jdbc.sql(
                    "select direction || ' ' || origin || ' ' || coalesce(refusal_subject, 'none')"
                        + " from simulator_audit_record where test_run_id = ? and direction ="
                        + " 'OUTBOUND_WEBHOOK_NOT_SENT'")
                .param(RUN.value())
                .query(String.class)
                .list())
        .containsExactly("OUTBOUND_WEBHOOK_NOT_SENT SCHEDULED_JOB none");
  }

  @Test
  void an_s2s_card_save_without_backref_for_an_account_with_no_single_target_is_undeliverable() {
    configureTargets(List.of(merchant.url(), "https://merchant.example/another-callback"));
    Map<String, String> s2s = cardSave("20260929120117");
    s2s.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    s2s.remove(AzericardFields.BACKREF);

    HttpResponse<String> saved = post("/token/cgi_link", signed(s2s));

    assertThat(saved.body()).startsWith("<response>").contains("<action>0</action>");
    assertThat(merchant.received()).isEmpty();
    List<Map<String, Object>> deliveries =
        http.get()
            .uri("/__simulator/azericard/webhooks?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    assertThat(deliveries)
        .singleElement()
        .satisfies(
            delivery -> {
              assertThat(delivery)
                  .containsEntry("status", "UNDELIVERABLE")
                  .containsEntry("notSentBecause", AzericardCallbacks.NO_SINGLE_TARGET)
                  .containsEntry("attemptsMade", 0);
              assertThat(delivery.get("targetUrl")).isNull();
            });
    assertThat(
            jdbc.sql(
                    "select count(*) from simulator_webhook_delivery where test_run_id = ? and"
                        + " target_url is null")
                .param(RUN.value())
                .query(Integer.class)
                .single())
        .isEqualTo(1);

    assertThat(
            jdbc.sql(
                    "select direction || ' ' || origin || ' ' || coalesce(path, 'no path') || ' '"
                        + " || coalesce(refusal_subject, 'none') from simulator_audit_record where"
                        + " test_run_id = ? and direction = 'OUTBOUND_WEBHOOK_NOT_SENT'")
                .param(RUN.value())
                .query(String.class)
                .list())
        .containsExactly("OUTBOUND_WEBHOOK_NOT_SENT SCHEDULED_JOB no path none");
    String job = String.valueOf(deliveries.get(0).get("webhookJobId"));

    HttpResponse<String> retried =
        send(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/__simulator/azericard/webhooks"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        "{\"testRunId\":\""
                            + RUN.value()
                            + "\",\"operation\":\"RETRY\",\"webhookJobId\":\""
                            + job
                            + "\"}"))
                .build());
    HttpResponse<String> clicked =
        post(
            "/ui/webhooks/retry",
            Map.of("provider", "azericard", "testRunId", RUN.value(), "deliveryId", job));
    HttpResponse<String> screen =
        send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:"
                            + port
                            + "/ui/webhooks?provider=azericard&testRunId="
                            + RUN.value()
                            + "&open="
                            + job))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build());

    assertThat(retried.statusCode()).isEqualTo(409);
    assertThat(retried.body()).contains("this delivery has no target");
    assertThat(clicked.statusCode()).isEqualTo(302);
    assertThat(clicked.headers().firstValue("Location"))
        .hasValueSatisfying(location -> assertThat(location).contains("refusal="));
    assertThat(screen.body())
        .contains(DeliveryHasNoTargetException.IT_CANNOT_BE_ATTEMPTED.substring(0, 40))
        .contains("no target: the provider found none to post this callback to")
        .doesNotContain("Attempt this delivery now")
        .doesNotContain("An attempt made here reuses this event id");
    assertThat(merchant.received()).isEmpty();
    String intRef = String.valueOf(deliveries.get(0).get("providerObjectId"));
    assertThat(theRefusalsOfARetry())
        .containsExactlyInAnyOrder("CONTROL_API 409 " + intRef, "UI 409 " + intRef);
  }

  @Test
  void a_screen_retry_to_another_accounts_target_is_refused_and_the_callback_stays_delivered() {
    String anotherTarget = "https://merchant-2.example/callback";
    configureTwoAccounts(anotherTarget);
    String session =
        theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120120"))).body());
    assertThat(pay(session).body()).contains("Payment successful");
    List<Map<String, Object>> deliveries =
        http.get()
            .uri("/__simulator/azericard/webhooks?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    String job = String.valueOf(deliveries.get(0).get("webhookJobId"));
    String intRef = String.valueOf(deliveries.get(0).get("providerObjectId"));

    HttpResponse<String> clicked =
        post(
            "/ui/webhooks/retry",
            Map.of(
                "provider",
                "azericard",
                "testRunId",
                RUN.value(),
                "deliveryId",
                job,
                "targetUrl",
                anotherTarget));

    assertThat(clicked.statusCode()).isEqualTo(302);
    assertThat(clicked.headers().firstValue("Location"))
        .hasValueSatisfying(location -> assertThat(location).contains("refusal="));
    assertThat(merchant.received()).hasSize(1);
    assertThat(
            http.get()
                .uri("/__simulator/azericard/webhooks?testRunId=" + RUN.value())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {}))
        .singleElement()
        .satisfies(
            delivery ->
                assertThat(delivery)
                    .containsEntry("status", "DELIVERED")
                    .containsEntry("attemptsMade", 1));
    assertThat(theRefusalsOfARetry()).containsExactly("UI 409 " + intRef);
    assertThat(
            jdbc.sql(
                    "select path from simulator_audit_record where test_run_id = ? and"
                        + " direction = 'OUTBOUND_WEBHOOK_NOT_SENT'")
                .param(RUN.value())
                .query(String.class)
                .list())
        .containsExactly(anotherTarget);
  }

  private List<String> theRefusalsOfARetry() {
    return jdbc.sql(
            "select origin || ' ' || http_status || ' ' || provider_object_id from"
                + " simulator_audit_record where test_run_id = ? and http_status = 409 and"
                + " origin in ('CONTROL_API', 'UI') and refusal_subject like ?")
        .params(RUN.value(), "%TheObject%")
        .query(String.class)
        .list();
  }

  private void configureTwoAccounts(String anotherTarget) {
    http.put()
        .uri("/__simulator/azericard/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                List.of(anAccount(MERCHANT, TERMINAL_ID), anAccount("merchant-2", "70000030")),
                "webhooks",
                List.of(
                    Map.of("accountId", MERCHANT, "url", merchant.url()),
                    Map.of("accountId", "merchant-2", "url", anotherTarget))))
        .retrieve()
        .toBodilessEntity();
  }

  private static Map<String, Object> anAccount(String accountId, String terminalId) {
    return Map.of(
        "accountId",
        accountId,
        "configuration",
        Map.of(
            AzericardTerminal.TERMINAL_PROPERTY,
            terminalId,
            AzericardTerminal.MERCHANT_KEY_PROPERTY,
            AzericardKeyFixture.MERCHANT_PUBLIC,
            AzericardTerminal.GATEWAY_KEY_PROPERTY,
            AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8));
  }

  @Test
  void
      the_database_refuses_a_delivery_with_no_target_that_is_not_undeliverable_and_a_pathless_call() {
    post("/token/cgi_link", signed(cardSave("20260929120118")));
    assertThat(
            pay(theSessionIn(post("/token/cgi_link", signed(cardSave("20260929120119"))).body()))
                .body())
        .contains("Payment successful");
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "update simulator_webhook_delivery set target_url = null where"
                            + " test_run_id = ?")
                    .param(RUN.value())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("simulator_webhook_delivery_with_no_target_is_undeliverable");
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "update simulator_audit_record set path = null where test_run_id = ? and"
                            + " direction = 'INBOUND_REQUEST'")
                    .param(RUN.value())
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("simulator_audit_record_with_no_path_is_a_callback_not_sent");
  }

  private void configureTargets(List<String> urls) {
    http.put()
        .uri("/__simulator/azericard/config")
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
                        Map.of(
                            AzericardTerminal.TERMINAL_PROPERTY,
                            TERMINAL_ID,
                            AzericardTerminal.MERCHANT_KEY_PROPERTY,
                            AzericardKeyFixture.MERCHANT_PUBLIC,
                            AzericardTerminal.GATEWAY_KEY_PROPERTY,
                            AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8))),
                "webhooks",
                urls.stream().map(url -> Map.of("accountId", MERCHANT, "url", url)).toList()))
        .retrieve()
        .toBodilessEntity();
  }

  private void aFaultThenSuccessStory(String story) {
    scenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            story,
            "whenState",
            "Started",
            "thenState",
            "unreached-once",
            "match",
            Map.of("operation", "TRTYPE=1 TOKEN_ACTION=REGISTER"),
            "action",
            Map.of(
                "type", "TRANSPORT_FAULT_INSTEAD_OF_OPERATION",
                "fault", "SERVER_ERROR",
                "httpStatus", 502)));
    scenario(
        Map.of(
            "testRunId", RUN.value(),
            "story", story,
            "whenState", "unreached-once",
            "match", Map.of("operation", "TRTYPE=1 TOKEN_ACTION=REGISTER"),
            "action", Map.of("type", "FIXED_SUCCESS")));
  }

  private void scenario(Map<String, Object> body) {
    assertThat(
            http.post()
                .uri("/__simulator/azericard/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (request, ignored) -> {})
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(201);
  }

  private HttpResponse<String> complete(String session) {
    return send(
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/__simulator/azericard/hosted/"
                        + session
                        + "/complete"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"testRunId\":\""
                        + RUN.value()
                        + "\",\"outcome\":\"PAID\",\"card\":\"azericard-2601\"}"))
            .build());
  }

  private HttpResponse<String> pay(String session) {
    return post(
        "/azericard/payer",
        Map.of(
            "session", session,
            "cardNumber", A_PUBLISHED_PAN,
            "expiry", "05/31",
            "cvc", "796",
            "action", "pay"));
  }

  private int rowsOf(String table) {
    return jdbc.sql("select count(*) from " + table + " where test_run_id = ?")
        .param(RUN.value())
        .query(Integer.class)
        .single();
  }

  private void onInsertInto(String table, String name, String statement) {
    jdbc.sql(
            "create function "
                + name
                + "() returns trigger language plpgsql as $$ begin "
                + statement
                + "; return new; end $$")
        .update();
    jdbc.sql(
            "create trigger "
                + name
                + " before insert on "
                + table
                + " for each row when (new.test_run_id = '"
                + RUN.value()
                + "') execute function "
                + name
                + "()")
        .update();
  }

  private boolean lockableNow(String session) {
    try (Connection probe = dataSource.getConnection()) {
      probe.setAutoCommit(false);
      try (PreparedStatement lock =
          probe.prepareStatement(
              "select session_id from azericard_payer_session where session_id = ?"
                  + " for update skip locked")) {
        lock.setString(1, session);
        try (ResultSet found = lock.executeQuery()) {
          return found.next();
        }
      } finally {
        probe.rollback();
      }
    } catch (SQLException unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private Connection aConnectionOfItsOwn() throws SQLException {
    return DriverManager.getConnection(
        database.getJdbcUrl(), database.getUsername(), database.getPassword());
  }

  private static int backendOf(Connection connection) throws SQLException {
    try (PreparedStatement pid = connection.prepareStatement("select pg_backend_pid()");
        ResultSet found = pid.executeQuery()) {
      found.next();
      return found.getInt(1);
    }
  }

  private static void pause(Connection pauser) throws SQLException {
    try (PreparedStatement lock = pauser.prepareStatement("select pg_advisory_lock(?)")) {
      lock.setLong(1, PAUSE);
      lock.executeQuery().close();
    }
  }

  private static void resume(Connection pauser) throws SQLException {
    try (PreparedStatement unlock = pauser.prepareStatement("select pg_advisory_unlock(?)")) {
      unlock.setLong(1, PAUSE);
      unlock.executeQuery().close();
    }
  }

  private void awaitWaiting(Future<?> waiter, String waitersQuery) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (jdbc.sql(waitersQuery).query(Integer.class).single() == 0) {
      assertThat(waiter).as("the request expected to wait finished first").isNotDone();
      assertThat(System.nanoTime()).as("the request never waited").isLessThan(deadline);
      Thread.onSpinWait();
    }
  }

  @Test
  void a_signature_no_key_produced_is_refused_as_today_and_opens_no_page() {
    Map<String, String> forged = signed(cardSave("20260929120102"));
    forged.put(AzericardFields.P_SIGN, "00");

    HttpResponse<String> refused = post("/token/cgi_link", forged);

    assertThat(refused.body()).contains("<rc>-17</rc>").doesNotContain("cardNumber");
  }

  @Test
  void a_page_this_simulator_never_opened_is_answered_404() {
    assertThat(post("/azericard/payer", Map.of("session", "nosuchsession")).statusCode())
        .isEqualTo(404);
  }

  private static String theSessionIn(String page) {
    String marker = "name=\"session\" value=\"";
    int start = page.indexOf(marker) + marker.length();
    return page.substring(start, page.indexOf('"', start));
  }

  private Map<String, String> cardSave(String order) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "1");
    form.put(AzericardFields.ORDER, order);
    form.put(AzericardFields.AMOUNT, "0.01");
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.MERCH_NAME, "The Shop");
    form.put(AzericardFields.MERCH_URL, MERCH_URL);
    form.put(AzericardFields.BACKREF, merchant.url());
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(FIXED_NOW));
    form.put(AzericardFields.NONCE, "F2B2DD7E603A7ADA");
    form.put(AzericardFields.TOKEN_ACTION, AzericardTokenFlow.REGISTER);
    form.put(AzericardFields.MERCH_TRAN_STATE, AzericardTokenFlow.STORING_A_CARD);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    Map<String, List<String>> fields = new LinkedHashMap<>();
    signed.forEach((name, value) -> fields.put(name, List.of(value)));
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.TOKEN_CGI_LINK, fields).macSource()));
    return signed;
  }

  private HttpResponse<String> post(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<String> send(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private String page(String path) {
    return http.get().uri(path).retrieve().body(String.class);
  }

  private Map<String, Object> stateOf(String providerObjectId) {
    return http.get()
        .uri("/__simulator/azericard/state/" + providerObjectId + "?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private String audit() {
    return http.get()
        .uri("/__simulator/azericard/audit?testRunId=" + RUN.value())
        .retrieve()
        .body(String.class);
  }

  private void deliverDue() {
    http.post()
        .uri("/__simulator/azericard/webhooks/deliver-due")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private void configure() {
    configureTargets(List.of(merchant.url()));
  }

  private void unconfigure() {
    http.put()
        .uri("/__simulator/azericard/config")
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
        .uri("/__simulator/azericard/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }
}
