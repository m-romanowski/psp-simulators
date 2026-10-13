package dev.marcinromanowski.pspsim.ui;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class UiScreensIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-screens-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final String USER_ID = "ui-screens-user";
  private static final String PAGE_CODE = "ui-screens-page";
  private static final String MERCHANT = "merchant-1";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String PROCESS_INFO = "/api/light/server/1.0/getPaymentProcessInfo";
  private static final String HELD = "120.50";

  private static final Pattern AN_AMOUNT_BOX =
      Pattern.compile("<input[^>]*\\sname=\"amount\"[^>]*\\svalue=\"([^\"]*)\"");
  private static final String PAYER = "Israel Israeli";
  private static final String PAYER_PHONE = "0505503144";
  private static final String SESSION = "session-must-not-reach-a-monitor";
  private static final String BEARER = "Bearer must-not-reach-a-monitor";
  private static final String UI_AUDIT = "/ui/audit";
  private static final String UI_WEBHOOKS = "/ui/webhooks";
  private static final String UI_OBJECTS = "/ui/objects";
  private static final String UI_SCENARIOS = "/ui/scenarios";
  private static final String UI_TIMELINE = "/ui/timeline";
  private static final String CARD_TOKEN = "pm_must_not_be_read_off_a_monitor";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String FORCED = "X-Force";
  private static final String DECLINE = "decline";

  @LocalServerPort private int port;

  @Autowired private ObjectMapper json;
  @Autowired private ProviderRegistry providers;

  private RestClient http;
  private HttpClient wire;
  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheTestRun() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
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
  void a_tester_who_opens_the_simulator_lands_on_the_test_run_and_not_on_a_landing_page() {
    aHold();

    String workbench = page("/ui?testRunId=" + RUN.value());

    assertThat(workbench).contains(RUN.value()).contains("grow");
    assertThat(workbench).contains("Callback and webhook queue");
    assertThat(workbench).contains(UI_AUDIT).contains(UI_WEBHOOKS).contains(UI_OBJECTS);
    assertThat(workbench).contains(UI_SCENARIOS);
  }

  @Test
  void the_workbench_names_a_callback_the_merchant_refused_rather_than_leaving_it_to_be_found() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(500, "no"));
    Hold hold = aHold();

    String workbench = page("/ui?testRunId=" + RUN.value());

    assertThat(workbench).contains("Callbacks a merchant has already refused");
    assertThat(workbench).contains(hold.processId());
  }

  @Test
  void a_workbench_panel_it_does_not_show_names_the_screen_the_objects_behind_it_are_read_on() {
    String workbench = page("/ui?testRunId=" + RUN.value());

    assertThat(workbench).contains("Not on this screen yet");
    assertThat(workbench).contains("recent provider API traffic");
    assertThat(workbench).contains("pending captures and refunds");
    assertThat(workbench).contains("seeded payment methods");
    assertThat(workbench)
        .contains("the Payment methods screen lists what this provider holds")
        .doesNotContain("No payment-method fixture exists")
        .doesNotContain("noun rather than a word the other two providers have");
  }

  @Test
  void the_audit_screen_lists_the_call_a_client_made_and_the_object_the_simulator_made_of_it() {
    Hold hold = aHold();

    String trail = page(UI_AUDIT + "?testRunId=" + RUN.value());

    assertThat(trail).contains(CREATE).contains(hold.processId());
    assertThat(trail).contains("INBOUND_REQUEST").contains("OUTBOUND_RESPONSE");
  }

  @Test
  void a_payers_details_stay_off_the_audit_screen_until_a_tester_asks_for_them() {
    aHold();
    String inbound = auditIdOfTheFirstInboundRow();

    String closed = page(UI_AUDIT + "?testRunId=" + RUN.value());
    String opened = page(UI_AUDIT + "?testRunId=" + RUN.value() + "&open=" + inbound);
    String asked =
        page(UI_AUDIT + "?testRunId=" + RUN.value() + "&open=" + inbound + "&values=show");

    assertThat(closed).doesNotContain(PAYER).doesNotContain(PAYER_PHONE);
    assertThat(opened).doesNotContain(PAYER).doesNotContain(PAYER_PHONE);
    assertThat(opened).contains("pageField[fullName]").contains("hidden until asked for");
    assertThat(asked).contains(PAYER).contains(PAYER_PHONE);
  }

  @Test
  void a_secret_the_simulator_hid_when_it_stored_the_row_is_never_handed_back_by_the_screen() {
    aHold();
    String inbound = auditIdOfTheFirstInboundRow();

    String asked =
        page(UI_AUDIT + "?testRunId=" + RUN.value() + "&open=" + inbound + "&values=show");

    assertThat(asked).doesNotContain(SESSION).doesNotContain("must-not-reach-a-monitor");
    assertThat(asked).contains("redacted before it was stored");
    assertThat(asked).contains("cField1").contains("Authorization");
  }

  @Test
  void the_audit_screen_offers_no_filter_the_stored_row_could_not_answer() {
    String trail = page(UI_AUDIT + "?testRunId=" + RUN.value());

    assertThat(trail).doesNotContain("name=\"orderId\"");
    assertThat(trail).doesNotContain("name=\"customerId\"");
    assertThat(trail).contains("name=\"accountId\"");
    assertThat(trail).contains("Order id and customer are still not offered");
  }

  @Test
  void a_filter_on_the_account_an_exchange_acted_for_narrows_the_screen_to_that_accounts_rows() {
    Hold hold = aHold();

    String theAccounts = page(UI_AUDIT + "?testRunId=" + RUN.value() + "&accountId=" + MERCHANT);

    assertThat(theAccounts).contains(hold.processId());
    assertThat(theAccounts).contains(MERCHANT);
    assertThat(theAccounts).doesNotContain(USER_ID).doesNotContain(PAGE_CODE);
  }

  @Test
  void a_filter_on_an_account_no_row_names_is_answered_in_words_and_not_as_an_empty_table() {
    aHold();

    String nothing =
        page(UI_AUDIT + "?testRunId=" + RUN.value() + "&accountId=ui-screens-never-configured");

    assertThat(nothing).contains("No row of this test run names the account");
    assertThat(nothing).contains("ui-screens-never-configured");
    assertThat(nothing).doesNotContain("No exchange of this test run matches");
  }

  @Test
  void a_filter_on_the_object_a_call_was_about_narrows_the_screen_to_that_objects_exchanges() {
    Hold first = aHold();
    Hold second = aHold();

    String about =
        page(UI_AUDIT + "?testRunId=" + RUN.value() + "&providerObjectId=" + first.processId());

    assertThat(about).contains(first.processId()).doesNotContain(second.processId());
  }

  @Test
  void a_filter_the_simulator_refuses_is_reported_on_the_screen_rather_than_breaking_the_page() {
    Answer refused = answer(UI_AUDIT + "?testRunId=" + RUN.value() + "&status=teapot");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refusalOn(refused.body())).isEqualTo("status must be a whole number");
    assertThat(refused.body()).doesNotContain("Exchanges, oldest first");
    assertThat(refused.body()).contains("name=\"status\" value=\"teapot\"");
  }

  @Test
  void a_test_run_the_simulator_reserves_is_read_on_the_screen_and_shows_no_other_runs_rows() {
    Hold mine = aHold();

    Answer read = answer(UI_AUDIT + "?testRunId=UNKNOWN");

    assertThat(read.status()).isEqualTo(200);
    assertThat(read.body())
        .as(
            "this screen refused the reserved id until the rows of a failed resolution were made"
                + " readable here, so the assertion that pinned the refusal now pins the read")
        .contains("The namespace of a failed resolution")
        .doesNotContain("that id is reserved for a failed resolution");
    assertThat(read.body())
        .as("a row of the run this test configured is no row of the namespace it did not name")
        .doesNotContain(mine.processId());
  }

  @Test
  void a_screen_that_writes_still_refuses_the_test_run_the_simulator_reserves() {
    Answer refused = answer(UI_SCENARIOS + "?testRunId=UNKNOWN");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refusalOn(refused.body())).contains("reserved for a failed resolution");
  }

  @Test
  void a_screen_that_writes_nothing_and_shows_what_a_run_holds_refuses_it_for_the_same_reason() {
    Answer refused = answer(UI_TIMELINE + "?provider=grow&testRunId=UNKNOWN");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refusalOn(refused.body()))
        .as(
            "this screen declares no write mapping at all and sits in the rail group the two"
                + " screens that read the namespace sit in, and what it shows is one payment of"
                + " one test run, of which the reserved id holds none")
        .contains("reserved for a failed resolution")
        .contains("This screen shows what one test run holds");
  }

  @Test
  void the_refusal_of_the_reserved_id_sends_a_tester_to_the_box_and_to_where_it_is_read() {
    Answer refused = answer(UI_SCENARIOS + "?provider=grow&testRunId=UNKNOWN");

    assertThat(refusalOn(refused.body()))
        .as(
            "every rail link on the screen reading the namespace carries the reserved id, so the"
                + " refusal a tester clicks into is the one place to hand them the way out")
        .contains("Type a run id in the test run box at the top of this page, for example")
        .contains("the Audit screen and the Correlation screen");
  }

  @Test
  void emptying_the_test_run_box_on_the_namespace_of_a_failed_resolution_leaves_it() {
    Hold mine = aHold();

    Answer left = answer(UI_AUDIT + "?provider=grow&testRunId=");

    assertThat(left.status()).isEqualTo(200);
    assertThat(left.body())
        .as(
            "the way out the panel names is one a tester can take: an empty box reads the run this"
                + " simulator uses when a request names none, and this build's default run is the"
                + " one the rows of this test are filed under")
        .doesNotContain("The namespace of a failed resolution")
        .doesNotContain("reserved for a failed resolution")
        .contains("Requests whose assignment failed");
    assertThat(left.body())
        .as("nothing of the reserved namespace is shown once the box is empty")
        .doesNotContain(mine.processId());
  }

  @Test
  void a_page_that_filled_its_page_size_hands_the_tester_the_next_page_to_walk_into() {
    aHold();

    String firstPage = page(UI_AUDIT + "?testRunId=" + RUN.value() + "&limit=2");

    assertThat(firstPage).contains("Next page").contains("after=");
  }

  @Test
  void opening_an_exchange_on_a_later_page_keeps_the_tester_on_that_page() {
    aHold();
    String secondPage = page(UI_AUDIT + "?testRunId=" + RUN.value() + "&limit=2");
    String cursor = cursorOn(secondPage);

    String opened = page(UI_AUDIT + "?testRunId=" + RUN.value() + "&limit=2&after=" + cursor);
    String openedRow = openLinkOn(opened);

    assertThat(openedRow).contains("after=" + cursor);
    assertThat(page(openedRow)).contains("after=" + cursor);
  }

  @Test
  void a_tester_can_attempt_a_refused_callback_again_and_see_the_attempt_it_made() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(500, "no"));
    aHold();
    String deliveryId = firstDeliveryId();

    String beforeRetry = page(UI_WEBHOOKS + "?testRunId=" + RUN.value() + "&open=" + deliveryId);
    Answer redirect = retry(deliveryId);
    String afterRetry = page(redirect.location());

    assertThat(beforeRetry).contains("1 of 6").contains("REFUSED");
    assertThat(redirect.status()).isEqualTo(302);
    assertThat(afterRetry).contains("Attempt ledger").contains("2 of 6");
    assertThat(merchant.received()).hasSize(2);
  }

  @Test
  void a_tester_runs_the_deliveries_the_clock_made_due_and_the_queue_reports_them_delivered() {
    Hold hold = aHold();
    aCallbackDueIn(hold.processId(), 60_000);
    pinTheClockTo(FIXED_NOW.plusSeconds(120));

    String waiting = page(UI_WEBHOOKS + "?testRunId=" + RUN.value());
    Answer ran = deliverDue();
    String afterwards = page(ran.location());

    assertThat(waiting).contains(">PENDING</span>").contains(">due</span>");
    assertThat(ran.status()).isEqualTo(302);
    assertThat(afterwards).contains(hold.processId()).doesNotContain(">PENDING</span>");
    assertThat(merchant.received()).hasSize(2);
  }

  @Test
  void a_callback_payload_keeps_its_values_off_the_screen_until_a_tester_asks_for_them() {
    aHold();
    String deliveryId = firstDeliveryId();

    String opened = page(UI_WEBHOOKS + "?testRunId=" + RUN.value() + "&open=" + deliveryId);
    String asked =
        page(UI_WEBHOOKS + "?testRunId=" + RUN.value() + "&open=" + deliveryId + "&values=show");

    assertThat(opened).contains("hidden until asked for");
    assertThat(opened).contains("Attempt ledger");
    assertThat(asked).doesNotContain(SESSION);
    assertThat(asked).contains("redacted before it was stored");
  }

  @Test
  void
      the_payment_screen_shows_the_hold_the_provider_is_holding_and_what_can_still_be_done_to_it() {
    Hold hold = aHold();

    String payment =
        page(
            UI_OBJECTS + "?testRunId=" + RUN.value() + "&providerObjectId=" + hold.transactionId());

    assertThat(payment).contains(hold.transactionId()).contains(HELD);
    assertThat(payment).contains("READY_FOR_CAPTURE");
    assertThat(payment).contains("capturable:").contains("AUTHORIZATION");
    assertThat(payment).contains(hold.processId());
  }

  @Test
  void a_tester_opens_the_payment_list_and_reaches_one_payments_detail_from_it() {
    Hold hold = aHold();

    String list = page(UI_OBJECTS + "?testRunId=" + RUN.value());
    String reached = page(theFirstPaymentLinkOn(list));

    assertThat(list).contains("What this test run holds, newest first");
    assertThat(list).contains(hold.transactionId());
    assertThat(reached).contains("What this object is");
    assertThat(reached).contains(hold.transactionId()).contains(HELD);
    assertThat(reached).contains("AUTHORIZATION");
  }

  @Test
  void a_tester_reading_the_list_is_told_which_payment_the_control_api_seeded() {
    aHold();

    String list = page(UI_OBJECTS + "?testRunId=" + RUN.value());

    assertThat(list).contains("created by");
    assertThat(list).contains("CONTROL_API");
    assertThat(page(theFirstPaymentLinkOn(list))).contains("CONTROL_API");
  }

  @Test
  void the_object_page_says_a_capture_would_be_taken_on_the_remainder_it_reports_as_capturable() {
    aHold();

    String object = page(theFirstPaymentLinkOn(page(UI_OBJECTS + "?testRunId=" + RUN.value())));

    assertThat(object).contains("still capturable");
    assertThat(object).contains("a capture would be taken");
    assertThat(object).doesNotContain("is not money the next call can take");
  }

  @Test
  void the_object_page_says_a_refund_would_be_returned_on_the_remainder_it_reports_as_refundable() {
    Hold hold = aHold();
    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    String object = thePaymentPageOf(hold.transactionId());

    assertThat(object).contains("still refundable");
    assertThat(object).contains("a refund would be returned");
    assertThat(object).doesNotContain("is not money the next call can return");
  }

  @Test
  void the_object_page_says_plainly_when_a_remainder_it_reports_is_money_no_refund_can_return() {
    Hold hold = aHold();
    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");
    pinTheClockTo(FIXED_NOW.plus(Duration.ofDays(1)));
    actOn(hold.transactionId(), "REFUND", "40.00", "yes");

    String object = thePaymentPageOf(hold.transactionId());

    assertThat(object).contains("80.50");
    assertThat(object).contains("is not money the next call can return:");
    assertThat(object).doesNotContain("is not money the next call can take");
  }

  @Test
  void a_test_run_that_holds_no_payment_says_so_rather_than_showing_an_empty_table() {
    String list = page(UI_OBJECTS + "?testRunId=" + RUN.value());

    assertThat(list).contains("holds no object of this provider");
    assertThat(list).doesNotContain("id the provider answered with</th>");
    assertThat(list).contains("end of the list as it stands");
  }

  @Test
  void the_table_head_the_empty_run_is_proved_to_omit_is_the_one_a_held_payment_renders() {
    aHold();

    String list = page(UI_OBJECTS + "?testRunId=" + RUN.value());

    assertThat(list).contains("id the provider answered with</th>");
    assertThat(list).contains("created by</th>");
  }

  @Test
  void a_payment_this_test_run_does_not_hold_is_reported_as_such_and_not_as_an_empty_payment() {
    Answer missing =
        answer(UI_OBJECTS + "?testRunId=" + RUN.value() + "&providerObjectId=trx_never_made");

    assertThat(missing.status()).isEqualTo(200);
    assertThat(missing.body()).contains("holds no object of this provider");
    assertThat(missing.body()).doesNotContain("Balances derived from the operation list");
  }

  @Test
  void a_rule_a_tester_wrote_on_the_screen_decides_the_next_call_and_the_screen_names_that_call() {
    Answer written =
        submit(
            UI_SCENARIOS + "/write",
            Map.of(
                "provider", "grow",
                "testRunId", RUN.value(),
                "operation", "createPaymentProcess",
                "actionType", "TERMINAL_FAILURE",
                "providerCode", "813"));

    String wroteIt = page(written.location());
    assertThat(parse(post(CREATE, holdForm()))).containsEntry("status", 0);
    String decided = page(written.location());

    assertThat(written.status()).isEqualTo(302);
    assertThat(wroteIt).contains("TERMINAL_FAILURE").contains("createPaymentProcess");
    assertThat(wroteIt).contains("The calls this rule decided");
    assertThat(wroteIt).contains("This rule has decided no call of this test run");
    assertThat(decided).contains("The calls this rule decided").contains(CREATE);
  }

  @Test
  void a_story_a_tester_keyed_on_the_test_run_shows_the_state_the_run_has_reached() {
    Map<String, String> step = new LinkedHashMap<>();
    step.put("provider", "grow");
    step.put("testRunId", RUN.value());
    step.put("operation", "createPaymentProcess");
    step.put("story", "declines-the-first-hold");
    step.put("keyedOn", "TEST_RUN");
    step.put("whenState", "Started");
    step.put("thenState", "AlreadyAsked");
    step.put("actionType", "TERMINAL_FAILURE");
    step.put("providerCode", "813");

    Answer wrote = submit(UI_SCENARIOS + "/write", step);
    String before = page(wrote.location());
    assertThat(parse(post(CREATE, holdForm()))).containsEntry("status", 0);
    String after = page(wrote.location());

    assertThat(wrote.status()).isEqualTo(302);
    assertThat(before).contains("No story of this test run stands on the test run itself");
    assertThat(after).contains("declines-the-first-hold").contains("AlreadyAsked");
  }

  @Test
  void a_fact_this_provider_never_carries_is_refused_on_the_screen_and_no_rule_is_written() {
    Answer refused =
        submit(
            UI_SCENARIOS + "/write",
            Map.of(
                "provider", "grow",
                "testRunId", RUN.value(),
                "customerId", "cus_1",
                "actionType", "FIXED_SUCCESS"));

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("This rule was not written").contains("match.customerId");
    assertThat(refused.body()).contains("provider grow publishes no customer");
    assertThat(refused.body()).doesNotContain("name=\"customerId\"");
    assertThat(page(UI_SCENARIOS + "?testRunId=" + RUN.value()))
        .contains("No scenario rule is written");
  }

  @Test
  void a_priority_that_is_not_a_number_is_refused_by_name_and_the_screen_keeps_what_was_typed() {
    Answer refused =
        submit(
            UI_SCENARIOS + "/write",
            Map.of(
                "provider", "grow",
                "testRunId", RUN.value(),
                "priority", "urgent",
                "actionType", "FIXED_SUCCESS"));

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("must be a whole number");
    assertThat(refused.body()).contains("value=\"urgent\"");
  }

  @Test
  void the_value_a_rule_matches_on_stays_off_the_scenarios_screen_until_a_tester_asks_for_it() {
    Answer written =
        submit(
            UI_SCENARIOS + "/write",
            Map.of(
                "provider",
                "grow",
                "testRunId",
                RUN.value(),
                "paymentMethodId",
                CARD_TOKEN,
                "actionType",
                "FIXED_SUCCESS"));

    String opened = page(written.location());
    String listed = page(theLinkOn(opened, "close"));
    String asked = page(theLinkOn(opened, "Show the values this rule matches on"));

    assertThat(listed).doesNotContain(CARD_TOKEN).contains("match.paymentMethodId");
    assertThat(opened).doesNotContain(CARD_TOKEN).contains("hidden until asked for");
    assertThat(asked).contains(CARD_TOKEN);
  }

  @Test
  void a_preset_this_provider_cannot_express_is_refused_on_the_screen_in_its_own_words() {
    Answer refused =
        submit(
            UI_SCENARIOS + "/preset",
            Map.of(
                "provider", "grow",
                "testRunId", RUN.value(),
                "preset", "webhook-invalid-signature",
                "priority", "0"));

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("no scenario action corrupts the signature of a callback");
    assertThat(page(UI_SCENARIOS + "?testRunId=" + RUN.value()))
        .contains("No scenario rule is written");
  }

  @Test
  void a_confirmed_preset_writes_the_steps_of_a_story_and_a_deleted_rule_leaves_the_screen() {
    Answer expanded =
        submit(
            UI_SCENARIOS + "/preset",
            Map.of(
                "provider", "grow",
                "testRunId", RUN.value(),
                "preset", "psp-5xx-then-success",
                "priority", "0",
                "confirm", "yes"));
    String listed = page(expanded.location());
    List<String> steps = scenarioIdsOn(listed);

    Answer deleted =
        submit(
            UI_SCENARIOS + "/delete",
            Map.of("provider", "grow", "testRunId", RUN.value(), "scenarioId", steps.getFirst()));
    String remaining = page(deleted.location());

    assertThat(expanded.status()).isEqualTo(302);
    assertThat(listed).contains("psp-5xx-then-success").contains("answered-without-the-capture");
    assertThat(listed).contains("TRANSPORT_FAULT_INSTEAD_OF_OPERATION");
    assertThat(steps).hasSizeGreaterThan(1);
    assertThat(deleted.status()).isEqualTo(302);
    assertThat(remaining).contains(steps.get(1)).doesNotContain(steps.getFirst());
  }

  @Test
  void the_screens_are_served_with_the_stylesheet_and_the_script_they_reference() {
    String workbench = page("/ui?testRunId=" + RUN.value());

    assertThat(answer("/ui.css").status()).isEqualTo(200);
    assertThat(answer("/webjars/htmx.org/4.0.0/dist/htmx.min.js").status()).isEqualTo(200);
    assertThat(workbench).contains("/ui.css").contains("htmx.min.js");
  }

  @Test
  void a_provider_this_simulator_does_not_serve_is_refused_on_every_screen_naming_what_it_serves() {
    assertThat(providers.names()).contains("grow", "azericard");
    for (String screen : theScreens()) {
      String refused = answer(screen + "?provider=no-such-psp").body();

      assertThat(refused).contains("unknown provider");
      assertThat(refused).contains(providers.names());
    }
  }

  @Test
  void a_screen_reached_with_no_provider_named_asks_the_reader_to_pick_one_of_the_known_ones() {
    for (String screen : theScreens()) {
      Answer unnamed = namingNoProvider(screen + "?testRunId=" + RUN.value());

      assertThat(unnamed.status()).isEqualTo(200);
      assertThat(unnamed.body()).contains("Choose a provider");
      assertThat(unnamed.body()).contains("choose a provider");
      for (String known : providers.names()) {
        assertThat(unnamed.body()).contains("provider=" + known);
      }
    }
  }

  @Test
  void a_screen_reached_with_no_provider_named_shows_no_provider_rather_than_the_first_one() {
    Hold hold = aHold();

    Answer workbench = namingNoProvider("/ui?testRunId=" + RUN.value());
    Answer trail = namingNoProvider(UI_AUDIT + "?testRunId=" + RUN.value());

    assertThat(workbench.body()).doesNotContain("Callback and webhook queue");
    assertThat(trail.body()).doesNotContain(hold.processId()).doesNotContain(CREATE);
  }

  private static List<String> theScreens() {
    return List.of("/ui", UI_AUDIT, UI_WEBHOOKS, UI_OBJECTS, UI_SCENARIOS);
  }

  private static String cursorOn(String page) {
    Matcher cursor = Pattern.compile("after=(aud_[a-z0-9]+)").matcher(page);
    assertThat(cursor.find()).isTrue();
    return cursor.group(1);
  }

  private static String theFirstPaymentLinkOn(String page) {
    Matcher link =
        Pattern.compile("href=\"(/ui/objects\\?[^\"]*providerObjectId=[^\"&]+)\"").matcher(page);
    assertThat(link.find()).isTrue();
    return link.group(1).replace("&amp;", "&");
  }

  private static String openLinkOn(String page) {
    Matcher link = Pattern.compile("href=\"(/ui/audit\\?[^\"]*open=aud_[a-z0-9]+)\"").matcher(page);
    assertThat(link.find()).isTrue();
    return link.group(1).replace("&amp;", "&");
  }

  private static String refusalOn(String page) {
    String opening = "<div class=\"refusal\">";
    int from = page.indexOf(opening);
    assertThat(from).isNotNegative();
    return page.substring(from + opening.length(), page.indexOf("</div>", from));
  }

  private String auditIdOfTheFirstInboundRow() {
    List<Map<String, Object>> rows =
        http.get()
            .uri(
                "/__simulator/grow/audit?origin=PROVIDER_API&testRunId="
                    + RUN.value()
                    + "&direction=INBOUND_REQUEST")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return String.valueOf(rows.get(0).get("auditId"));
  }

  private String firstDeliveryId() {
    List<Map<String, Object>> deliveries =
        http.get()
            .uri("/__simulator/grow/webhooks?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return String.valueOf(deliveries.get(0).get("webhookJobId"));
  }

  private static List<String> scenarioIdsOn(String page) {
    Matcher scenario = Pattern.compile("scn_[a-z0-9]+").matcher(page);
    List<String> found = new ArrayList<>();
    while (scenario.find()) {
      if (!found.contains(scenario.group())) {
        found.add(scenario.group());
      }
    }
    return List.copyOf(found);
  }

  private Answer submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return answerOf(response);
  }

  private Answer deliverDue() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    return submit("/ui/webhooks/deliver-due", form);
  }

  @Test
  void a_tester_captures_a_hold_from_the_screen_and_the_payment_reports_the_money_it_released() {
    Hold hold = aHold();

    String before = thePaymentPageOf(hold.transactionId());
    Answer captured = actOn(hold.transactionId(), "CAPTURE", HELD, "yes");
    String after = page(captured.location());

    assertThat(before).contains("capture or complete the hold").contains("value=\"" + HELD + "\"");
    assertThat(captured.status()).isEqualTo(302);
    assertThat(after).contains(">SUCCEEDED<").contains("refund what was captured");
    assertThat(theState(hold.transactionId())).containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void a_tester_refunds_from_the_screen_and_the_trail_says_the_tester_did_it() {
    Hold hold = aHold();
    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    Answer refunded = actOn(hold.transactionId(), "REFUND", HELD, "yes");

    assertThat(refunded.status()).isEqualTo(302);
    Map<String, Object> row = theOnlyActionNaming("/ui/objects/act", "REFUND");
    assertThat(row).containsEntry("origin", "UI").containsEntry("method", "POST");
    assertThat(row).containsEntry("providerObjectId", hold.transactionId());
    String refundId = String.valueOf(bodyOfRow(row).get("createdObjectId").get(0));
    assertThat(theState(refundId)).containsEntry("origin", "UI");
    assertThat(theState(hold.transactionId())).containsEntry("origin", "CONTROL_API");
  }

  @Test
  void an_action_grow_refuses_is_refused_on_the_screen_in_grows_own_words() {
    Hold hold = aHold();
    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    Answer again = actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    assertThat(again.status()).isEqualTo(200);
    assertThat(again.body()).contains("808").doesNotContain("Exception");
  }

  @Test
  void an_action_grow_refuses_leaves_a_row_carrying_the_refusing_status_and_the_object() {
    Hold hold = aHold();
    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    Map<String, Object> row = theOnlyActionNaming("/ui/objects/act", "808");
    assertThat(row).containsEntry("status", 422);
    assertThat(row).containsEntry("providerObjectId", hold.transactionId());
    assertThat(theRefusalOf(row))
        .containsEntry("subject", "THE_OBJECT")
        .containsEntry("providerObjectId", hold.transactionId());
  }

  @Test
  void a_rule_narrowing_on_a_header_declines_the_wire_call_and_the_screen_says_it_cannot_fire() {
    String rule = aRuleDecliningASettlementCarrying(FORCED, DECLINE);
    Hold overTheWire = aHold();
    Hold fromTheScreen = aHold();

    Map<String, Object> declined = parse(postCarrying(SETTLE, settlementOf(overTheWire)));
    Answer captured = actOn(fromTheScreen.transactionId(), "CAPTURE", HELD, "yes");

    assertThat(declined).containsEntry("status", 0);
    assertThat(theState(overTheWire.transactionId()))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
    assertThat(captured.status()).isEqualTo(302);
    assertThat(theState(fromTheScreen.transactionId()))
        .containsEntry("normalizedStatus", "SUCCEEDED");
    assertThat(thePaymentPageOf(fromTheScreen.transactionId()))
        .contains("Rules no action on this screen can fire")
        .contains(rule)
        .contains(FORCED + ": " + DECLINE);
  }

  @Test
  void a_rule_narrowing_on_nothing_but_the_operation_is_not_called_unfireable_by_the_screen() {
    String narrowedOnAHeader = aRuleDecliningASettlementCarrying(FORCED, DECLINE);
    String narrowedOnTheOperationAlone = aRuleDecidingEverySettlement("FIXED_SUCCESS", null);
    Hold hold = aHold();

    String payment = thePaymentPageOf(hold.transactionId());

    assertThat(theUnfireableRulesOn(payment)).containsExactly(narrowedOnAHeader);
    assertThat(theUnfireableRulesOn(payment)).doesNotContain(narrowedOnTheOperationAlone);
  }

  @Test
  void a_capture_a_scenario_refused_is_findable_in_the_trail_afterwards() {
    String rule = aRuleDecidingEverySettlement("TERMINAL_FAILURE", "813");
    Hold hold = aHold();

    Answer refused = actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theState(hold.transactionId()))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
    Map<String, Object> row = theOnlyActionNaming("/ui/objects/act", "CAPTURE");
    assertThat(row).containsEntry("scenarioId", rule);
    assertThat(row).containsEntry("providerObjectId", hold.transactionId());
    assertThat(row).containsEntry("origin", "UI");
    assertThat(bodyOfRow(row).get("action")).containsExactly("CAPTURE");
  }

  @Test
  void a_capture_a_scenario_refused_carries_the_refusing_status_beside_the_rule_that_decided_it() {
    String rule = aRuleDecidingEverySettlement("TERMINAL_FAILURE", "813");
    Hold hold = aHold();

    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    Map<String, Object> row = theOnlyActionNaming("/ui/objects/act", "CAPTURE");
    assertThat(row).containsEntry("status", 422);
    assertThat(row).containsEntry("scenarioId", rule);
    assertThat(theRefusalOf(row))
        .containsEntry("subject", "THE_OBJECT")
        .containsEntry("providerObjectId", hold.transactionId());
  }

  @Test
  void the_audit_screen_prints_what_a_refused_row_named_beside_the_status_that_refused_it() {
    Hold hold = aHold();
    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");
    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    String screen =
        page(UI_AUDIT + "?provider=grow&testRunId=" + RUN.value() + "&origin=UI&status=422");

    assertThat(screen).contains("what it refused");
    assertThat(screen).contains("the object " + hold.transactionId());
  }

  @Test
  void a_harness_filtering_the_trail_by_a_rules_id_finds_the_screen_driven_call_it_refused() {
    String rule = aRuleDecidingEverySettlement("TERMINAL_FAILURE", "813");
    Hold hold = aHold();

    actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    List<Map<String, Object>> decided = theCallsTheRuleDecided(rule);
    assertThat(decided).isNotEmpty();
    assertThat(decided).allSatisfy(row -> assertThat(row).containsEntry("scenarioId", rule));
    assertThat(decided)
        .anySatisfy(
            row ->
                assertThat(row)
                    .containsEntry("origin", "UI")
                    .containsEntry("path", "/ui/objects/act")
                    .containsEntry("providerObjectId", hold.transactionId()));
  }

  @Test
  void a_harness_filtering_the_trail_by_a_rules_id_finds_the_screen_driven_call_it_let_through() {
    String rule = aRuleDecidingEverySettlement("FIXED_SUCCESS", null);
    Hold hold = aHold();

    Answer captured = actOn(hold.transactionId(), "CAPTURE", HELD, "yes");

    assertThat(captured.status()).isEqualTo(302);
    List<Map<String, Object>> decided = theCallsTheRuleDecided(rule);
    assertThat(decided).allSatisfy(row -> assertThat(row).containsEntry("scenarioId", rule));
    assertThat(decided)
        .anySatisfy(
            row ->
                assertThat(row)
                    .containsEntry("origin", "UI")
                    .containsEntry("path", "/ui/objects/act")
                    .containsEntry("status", 302));
  }

  @Test
  void an_action_a_tester_did_not_confirm_moves_no_money_and_says_what_it_wants() {
    Hold hold = aHold();

    Answer unconfirmed = actOn(hold.transactionId(), "CAPTURE", HELD, null);

    assertThat(unconfirmed.status()).isEqualTo(200);
    assertThat(unconfirmed.body()).contains("tick confirm before moving money");
    assertThat(theState(hold.transactionId()))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
  }

  @Test
  void an_action_a_tester_did_not_confirm_comes_back_with_the_amount_they_had_typed() {
    Hold hold = aHold();

    Answer unconfirmed = actOn(hold.transactionId(), "CAPTURE", "12.34", null);

    assertThat(unconfirmed.body())
        .as(
            "the refusal used to come back over a box refilled from the balance, so a tester who"
                + " had changed the figure read why it was refused and had to type it again")
        .contains("value=\"12.34\"");
  }

  @Test
  void an_amount_typed_against_one_action_is_drawn_into_no_other_action_s_box() {
    Hold hold = aHold();

    Answer unconfirmed = actOn(hold.transactionId(), "REFUND", "12.34", null);

    assertThat(theAmountBoxesOn(unconfirmed.body()))
        .as(
            "a held grow payment offers capture and nothing else, so a refused refund has no box"
                + " of its own to come back into, and one carried onto every box named amount"
                + " would have shown a tester a figure they typed against a different action")
        .containsExactly(HELD);
  }

  @Test
  void a_callback_a_tester_did_not_confirm_comes_back_with_the_event_type_they_had_typed() {
    Hold hold = aHold();

    Answer unconfirmed = sendACallbackAbout(hold.transactionId(), "charge.settled", null);

    assertThat(unconfirmed.status()).isEqualTo(200);
    assertThat(unconfirmed.body()).contains("tick confirm before sending a callback");
    assertThat(unconfirmed.body())
        .as("the event type box carried no value at all, so every refusal emptied it")
        .contains("value=\"charge.settled\"");
  }

  @Test
  void the_screen_offers_no_cancellation_for_grow_and_says_what_grow_documents_instead() {
    Hold hold = aHold();

    String payment = thePaymentPageOf(hold.transactionId());

    assertThat(payment).contains("cancel or reverse the hold");
    assertThat(payment).contains("settleSuspendedTransaction for the full held amount");
    assertThat(payment).doesNotContain("value=\"CANCEL\"");
  }

  @Test
  void a_tester_sends_a_callback_about_a_payment_from_its_own_screen_and_the_merchant_gets_it() {
    Hold hold = aHold();
    int alreadySent = merchant.received().size();

    Answer sent = sendACallbackAbout(hold.processId(), "yes");

    assertThat(sent.status()).isEqualTo(302);
    assertThat(merchant.received()).hasSize(alreadySent + 1);
    Map<String, Object> row = theOnlyActionNaming("/ui/objects/webhook", "SEND");
    assertThat(row).containsEntry("origin", "UI");
    assertThat(bodyOfRow(row)).containsKey("webhookJobIds");
  }

  @Test
  void a_callback_a_tester_did_not_confirm_is_never_sent_to_the_merchant() {
    Hold hold = aHold();
    int alreadySent = merchant.received().size();

    Answer unconfirmed = sendACallbackAbout(hold.processId(), null);

    assertThat(unconfirmed.status()).isEqualTo(200);
    assertThat(unconfirmed.body()).contains("tick confirm before sending a callback");
    assertThat(merchant.received()).hasSize(alreadySent);
  }

  private String aRuleDecliningASettlementCarrying(String header, String value) {
    return aRuleWritten(
        Map.of(
            "testRunId",
            RUN.value(),
            "match",
            Map.of("operation", "settleSuspendedTransaction", "header", Map.of(header, value)),
            "action",
            Map.of("type", "TERMINAL_FAILURE", "providerCode", "813")));
  }

  private String aRuleDecidingEverySettlement(String type, String providerCode) {
    Map<String, Object> action = new LinkedHashMap<>();
    action.put("type", type);
    if (providerCode != null) {
      action.put("providerCode", providerCode);
    }
    return aRuleWritten(
        Map.of(
            "testRunId",
            RUN.value(),
            "match",
            Map.of("operation", "settleSuspendedTransaction"),
            "action",
            action));
  }

  private String aRuleWritten(Map<String, Object> declared) {
    Map<String, Object> written =
        http.post()
            .uri("/__simulator/grow/scenarios")
            .contentType(MediaType.APPLICATION_JSON)
            .body(declared)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    assertThat(written).isNotNull();
    return String.valueOf(written.get("scenarioId"));
  }

  private List<String> theUnfireableRulesOn(String page) {
    String opening = "<h3>Rules no action on this screen can fire</h3>";
    int from = page.indexOf(opening);
    if (from < 0) {
      return List.of();
    }
    return scenarioIdsOn(page.substring(from, page.indexOf("</table>", from)));
  }

  private List<Map<String, Object>> theCallsTheRuleDecided(String scenarioId) {
    List<Map<String, Object>> rows =
        http.get()
            .uri("/__simulator/grow/audit?testRunId=" + RUN.value() + "&scenarioId=" + scenarioId)
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return rows == null ? List.of() : rows;
  }

  private static Map<String, String> settlementOf(Hold hold) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("userId", USER_ID);
    form.put("transactionId", hold.transactionId());
    form.put("transactionToken", hold.transactionToken());
    form.put("sum", HELD);
    return form;
  }

  private HttpResponse<byte[]> postCarrying(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", BEARER)
            .header(FORCED, DECLINE)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
  }

  private Map<String, Object> theOnlyActionNaming(String path, String fact) {
    for (Map<String, Object> row : theActionsATesterTook()) {
      if (path.equals(row.get("path")) && String.valueOf(bodyOfRow(row)).contains(fact)) {
        return row;
      }
    }
    throw new AssertionError("no action of the trail names " + path + " and " + fact);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> theRefusalOf(Map<String, Object> row) {
    return (Map<String, Object>) row.get("refusal");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, List<String>> bodyOfRow(Map<String, Object> row) {
    return (Map<String, List<String>>) row.get("sanitizedBody");
  }

  private Map<String, Object> theState(String providerObjectId) {
    Map<String, Object> state =
        http.get()
            .uri("/__simulator/grow/state/" + providerObjectId + "?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    return state == null ? Map.of() : state;
  }

  private Answer actOn(String providerObjectId, String action, String amount, String confirm) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("providerObjectId", providerObjectId);
    form.put("action", action);
    if (amount != null) {
      form.put("amount", amount);
    }
    if (confirm != null) {
      form.put("confirm", confirm);
    }
    return submitTo("/ui/objects/act", form);
  }

  private Answer sendACallbackAbout(String providerObjectId, String confirm) {
    return sendACallbackAbout(providerObjectId, null, confirm);
  }

  private Answer sendACallbackAbout(String providerObjectId, String eventType, String confirm) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("providerObjectId", providerObjectId);
    if (eventType != null) {
      form.put("eventType", eventType);
    }
    if (confirm != null) {
      form.put("confirm", confirm);
    }
    return submitTo("/ui/objects/webhook", form);
  }

  private static List<String> theAmountBoxesOn(String markup) {
    List<String> drawn = new ArrayList<>();
    Matcher found = AN_AMOUNT_BOX.matcher(markup);
    while (found.find()) {
      drawn.add(found.group(1));
    }
    return List.copyOf(drawn);
  }

  private Answer submitTo(String path, Map<String, String> form) {
    return answerOf(
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))));
  }

  private String thePaymentPageOf(String transactionId) {
    return page(UI_OBJECTS + "?testRunId=" + RUN.value() + "&providerObjectId=" + transactionId);
  }

  private List<Map<String, Object>> theActionsATesterTook() {
    List<Map<String, Object>> rows =
        http.get()
            .uri("/__simulator/grow/audit?testRunId=" + RUN.value() + "&origin=UI")
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    return rows == null ? List.of() : rows;
  }

  private void aCallbackDueIn(String providerObjectId, long delayMs) {
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(), "providerObjectId", providerObjectId, "delayMs", delayMs))
        .retrieve()
        .toBodilessEntity();
  }

  private Answer retry(String deliveryId) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("deliveryId", deliveryId);
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ui/webhooks/retry"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return answerOf(response);
  }

  private String page(String uri) {
    Answer answer = answer(uri);
    assertThat(answer.status()).isEqualTo(200);
    return answer.body();
  }

  private Answer answer(String uri) {
    return answerAt(namingTheProvider(uri));
  }

  private Answer namingNoProvider(String uri) {
    return answerAt(uri);
  }

  private Answer answerAt(String uri) {
    HttpResponse<byte[]> response =
        send(HttpRequest.newBuilder(at(uri)).timeout(Duration.ofSeconds(20)).GET());
    return answerOf(response);
  }

  private URI at(String uri) {
    return URI.create(uri.startsWith("http") ? uri : "http://localhost:" + port + uri);
  }

  private static Answer answerOf(HttpResponse<byte[]> response) {
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
  }

  private static String theLinkOn(String page, String text) {
    Matcher link =
        Pattern.compile("href=\"([^\"]+)\"\\s*>\\s*" + Pattern.quote(text)).matcher(page);
    assertThat(link.find()).isTrue();
    return link.group(1).replace("&amp;", "&");
  }

  private static String namingTheProvider(String uri) {
    if (!aScreen(uri) || uri.contains("provider=")) {
      return uri;
    }
    return uri + (uri.contains("?") ? "&" : "?") + "provider=grow";
  }

  private static boolean aScreen(String uri) {
    return uri.equals("/ui") || uri.startsWith("/ui?") || uri.startsWith("/ui/");
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
    form.put("pageField[fullName]", PAYER);
    form.put("pageField[phone]", PAYER_PHONE);
    form.put("cField1", SESSION);
    form.put("cField2", "intent-99");
    return form;
  }

  private Map<String, Object> parse(HttpResponse<byte[]> response) {
    return json.readValue(
        new String(response.body(), UTF_8), new TypeReference<Map<String, Object>>() {});
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", BEARER)
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

  private void pinTheClockTo(Instant now) {
    configure(now);
  }

  private void configure() {
    configure(FIXED_NOW);
  }

  private void configure(Instant now) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                Map.of("fixedNow", now.toString()),
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
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstTransactionOf(Map<String, Object> body) {
    return ((List<Map<String, Object>>) dataOf(body).get("transactions")).get(0);
  }

  private record Hold(String processId, String transactionId, String transactionToken) {}

  private record Answer(int status, String body, String location) {}
}
