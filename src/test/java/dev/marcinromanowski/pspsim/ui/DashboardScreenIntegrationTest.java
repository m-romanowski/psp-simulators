package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.TestRunActivity;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.io.IOException;
import java.net.CookieManager;
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
import org.jspecify.annotations.Nullable;
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

class DashboardScreenIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-dashboard-it");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("ui-dashboard-it-other");
  private static final TestRunId A_RUN_ABANDONED = TestRunId.of("ui-dashboard-it-abandoned");
  private static final Instant FIXED_NOW = Instant.parse("2026-09-11T12:00:00Z");
  private static final TestRunId A_RUN_PINNED_EARLY = TestRunId.of("ui-dashboard-it-clockearly");
  private static final TestRunId A_RUN_PINNED_LATE = TestRunId.of("ui-dashboard-it-clocklate");
  private static final TestRunId A_RUN_REPINNED = TestRunId.of("ui-dashboard-it-repinned");
  private static final Instant EARLY = Instant.parse("2020-06-01T10:00:00Z");
  private static final Instant LATE = Instant.parse("2030-06-01T10:00:00Z");
  private static final Instant BETWEEN = Instant.parse("2025-06-01T10:00:00Z");

  private static final String MERCHANT = "merchant-1";
  private static final String GROW_USER = "ui-dashboard-user";
  private static final String GROW_PAGE = "ui-dashboard-page";
  private static final String STRIPE_SECRET_KEY = "sk_test_ui_dashboard";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String PROCESS_INFO = "/api/light/server/1.0/getPaymentProcessInfo";
  private static final String PAYMENT_INTENTS = "/v1/payment_intents";
  private static final String DASHBOARD = "/ui/dashboard";
  private static final String HELD = "120.50";

  private static final int LOOKUPS_THAT_OUTNUMBER_THE_EXCHANGES_SHOWN = 12;

  @LocalServerPort private int port;

  @Autowired private ObjectMapper json;
  @Autowired private TestRuns testRuns;
  @Autowired private AuditReads auditTrail;
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
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    merchant = new LocalMerchantServer();
    resetEveryProvider(RUN);
    resetEveryProvider(ANOTHER_RUN);
    configureGrow(RUN);
    configureStripe(RUN);
  }

  @AfterEach
  void unconfigureTheTestRun() {
    unconfigure("grow", RUN);
    unconfigure("stripe", RUN);
    unconfigure("grow", ANOTHER_RUN);
    resetEveryProvider(RUN);
    resetEveryProvider(ANOTHER_RUN);
    for (TestRunId aside : List.of(A_RUN_PINNED_EARLY, A_RUN_PINNED_LATE, A_RUN_REPINNED)) {
      resetEveryProvider(aside);
      testRuns.forget(aside);
    }
    merchant.close();
  }

  @Test
  void one_screen_counts_the_work_of_every_provider_rather_than_one_provider_at_a_time() {
    String processId = aGrowHold();
    aStripeHold();

    String overview = page(DASHBOARD + "?testRunId=" + RUN.value());

    assertThat(overview)
        .contains("autra")
        .contains("grow")
        .contains("azericard")
        .contains("stripe");
    assertThat(overview).contains(CREATE).contains(PAYMENT_INTENTS);
    assertThat(overview).contains(processId);
    assertThat(overview).doesNotContain("Choose a provider");
  }

  @Test
  void a_provider_this_test_run_configured_no_account_for_is_named_as_answering_nothing() {
    String overview = page(DASHBOARD + "?testRunId=" + RUN.value());

    assertThat(overview).contains("no account is configured");
    assertThat(overview).contains("2 of 6 answer calls in this test run");
  }

  @Test
  void the_link_from_a_provider_row_to_its_own_screen_reads_the_name_the_rail_gives_that_screen() {
    String overview = page(DASHBOARD + "?testRunId=" + RUN.value());

    assertThat(overview).contains(">This test run</a");
    assertThat(overview).doesNotContain(">Workbench</a");
  }

  @Test
  void the_newest_exchanges_are_on_the_dashboard_without_paging_to_the_end_of_the_audit_trail() {
    Hold hold = aGrowProcess();
    for (int lookup = 0; lookup < LOOKUPS_THAT_OUTNUMBER_THE_EXCHANGES_SHOWN; lookup++) {
      post(PROCESS_INFO, lookupOf(hold), RUN);
    }

    String overview = page(DASHBOARD + "?testRunId=" + RUN.value());
    String trail = page("/ui/audit?provider=grow&testRunId=" + RUN.value() + "&limit=4");

    assertThat(overview).contains(PROCESS_INFO).doesNotContain(CREATE);
    assertThat(trail).contains(CREATE);
  }

  @Test
  void a_traffic_row_links_to_the_one_exchange_it_is_and_not_to_the_others_of_the_same_object() {
    Hold hold = aGrowProcess();
    post(PROCESS_INFO, lookupOf(hold), RUN);
    post(PROCESS_INFO, lookupOf(hold), RUN);

    String overview = page(DASHBOARD + "?testRunId=" + RUN.value());
    String linkToOneExchange = theFirstLinkTo(overview, "This exchange");
    String linkToTheObject = theFirstLinkTo(overview, "Exchanges");
    String narrowed = page(linkToOneExchange);
    String everyExchangeOfTheObject = page(linkToTheObject);

    assertThat(theAuditIdIn(linkToOneExchange)).isEqualTo(theNewestAuditIdOfTheTrail());
    assertThat(howManyExchangesAreOn(everyExchangeOfTheObject)).isGreaterThan(1);
    assertThat(howManyExchangesAreOn(narrowed)).isEqualTo(1);
    assertThat(narrowed).contains(theAuditIdIn(linkToOneExchange));
    assertThat(narrowed).contains("Show the values of this exchange");
    assertThat(narrowed).doesNotContain(CREATE);
    assertThat(everyExchangeOfTheObject).contains(CREATE);
  }

  @Test
  void a_link_to_an_exchange_the_trail_no_longer_holds_says_so_rather_than_reading_as_no_traffic() {
    Hold hold = aGrowProcess();
    post(PROCESS_INFO, lookupOf(hold), RUN);

    String linkToOneExchange =
        theFirstLinkTo(page(DASHBOARD + "?testRunId=" + RUN.value()), "This exchange");
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();

    String narrowed = page(linkToOneExchange);

    assertThat(narrowed).contains("was recorded under the audit id");
    assertThat(narrowed).contains(theAuditIdIn(linkToOneExchange));
    assertThat(narrowed).doesNotContain("No exchange of this test run matches.");
  }

  private String theNewestAuditIdOfTheTrail() {
    List<Map<String, Object>> trail =
        http.get()
            .uri("/__simulator/grow/audit?testRunId=" + RUN.value())
            .retrieve()
            .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    assertThat(trail).isNotNull();
    return String.valueOf(trail.get(trail.size() - 1).get("auditId"));
  }

  private static String theAuditIdIn(String link) {
    Matcher named = Pattern.compile("auditId=([^&]+)").matcher(link);
    assertThat(named.find()).isTrue();
    return named.group(1);
  }

  private static String theFirstLinkTo(String body, String text) {
    Matcher link =
        Pattern.compile("href=\"([^\"]+)\"\\s*>\\s*" + Pattern.quote(text)).matcher(body);
    assertThat(link.find()).isTrue();
    return link.group(1).replace("&amp;", "&");
  }

  private static int howManyExchangesAreOn(String trail) {
    return trail.split("<td class=\"stamp\"", -1).length - 1;
  }

  @Test
  void a_callback_the_merchant_refused_is_named_on_the_dashboard_with_the_provider_it_belongs_to() {
    merchant.alwaysAnswering(new LocalMerchantServer.Answer.Respond(500, "no"));
    String processId = aGrowHold();

    String overview = page(DASHBOARD + "?testRunId=" + RUN.value());

    assertThat(overview).contains("Callbacks and webhooks a merchant has already refused");
    assertThat(overview).contains(processId);
    assertThat(overview).doesNotContain("No callback of this test run has been refused");
  }

  @Test
  void naming_a_test_run_on_the_dashboard_carries_the_tester_to_the_account_form() {
    Answer created = createTestRun(ANOTHER_RUN.value());

    String form = page(created.location());

    assertThat(created.status()).isEqualTo(302);
    assertThat(created.location()).contains("/ui/configuration/account");
    assertThat(created.location()).contains(ANOTHER_RUN.value());
    assertThat(form).contains("Configure an account for this provider and test run");
    assertThat(form).contains("Grow user id");
  }

  @Test
  void a_test_run_a_tester_named_and_then_abandoned_the_form_of_was_never_registered() {
    createTestRun(A_RUN_ABANDONED.value());

    assertThat(page(DASHBOARD)).doesNotContain(">" + A_RUN_ABANDONED.value() + "<");
    assertThat(testRuns.exists(A_RUN_ABANDONED)).isFalse();
  }

  @Test
  void a_test_run_the_simulator_reserves_is_refused_on_the_dashboard_and_no_run_is_created() {
    Answer refused = createTestRun("UNKNOWN");

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("reserved");
    assertThat(page(DASHBOARD)).doesNotContain(">UNKNOWN<");
  }

  @Test
  void resetting_from_the_dashboard_clears_every_provider_of_one_run_and_leaves_another_alone() {
    aGrowHold();
    aStripeHold();
    configureGrow(ANOTHER_RUN);
    String elsewhere = aGrowHold(ANOTHER_RUN);

    Answer reset = resetFromTheDashboard(RUN.value());
    String cleared = page(reset.location());
    String reopened = page(DASHBOARD + "?testRunId=" + RUN.value());
    String untouched = page(DASHBOARD + "?testRunId=" + ANOTHER_RUN.value());

    assertThat(reset.status()).isEqualTo(302);
    assertThat(reset.location()).doesNotContain("cleared");
    assertThat(cleared)
        .contains("<div class=\"done\" role=\"status\">This test run is reset in every provider.")
        .contains("across " + providers.modules().size() + " provider(s).");
    assertThat(reopened).doesNotContain("This test run is reset").doesNotContain("It cleared");
    assertThat(cleared)
        .contains("No provider of this test run has answered a call or attempted a callback yet")
        .doesNotContain(PAYMENT_INTENTS);
    assertThat(untouched).contains(CREATE).contains(elsewhere);
  }

  @Test
  void the_dashboard_names_what_nothing_behind_it_can_answer_instead_of_showing_a_zero() {
    String overview = page(DASHBOARD + "?testRunId=" + RUN.value());

    assertThat(overview).contains("Not on this screen");
    assertThat(overview).contains("pending hosted sessions");
    assertThat(overview).contains("pending captures and refunds");
    assertThat(overview).contains("open the live sessions queue");
    assertThat(overview).contains("a count of the payment methods a run holds");
  }

  @Test
  void the_providers_the_absent_hosted_session_count_splits_are_the_providers_this_build_serves() {
    HostedPages.Split declared = HostedPages.of(providers.modules());
    List<String> split = new ArrayList<>(declared.thatKeepAnObjectAPayerFinishes());
    split.addAll(declared.withNoPageAPayerFinishes());

    assertThat(split).containsExactlyInAnyOrderElementsOf(providers.names());
    assertThat(page(DASHBOARD + "?testRunId=" + RUN.value()))
        .contains(
            "7 objects waiting for a payer on a page the provider hosts are kept by 5 of the 6"
                + " providers here")
        .contains("autra a card session and a Pix")
        .contains("azericard a payment page")
        .contains("tranzila a payment page and a 3-D Secure challenge")
        .doesNotContain("Grow is the only provider here that keeps a hosted session");
  }

  @Test
  void the_grow_hosted_process_the_dashboard_names_is_opened_by_the_object_state_read() {
    String processId = aGrowHold();
    SimulatorProvider grow = providers.find("grow").orElseThrow();

    assertThat(HostedPages.of(providers.modules()).thatKeepAnObjectAPayerFinishes())
        .contains("grow");
    assertThat(grow.state(RUN, processId)).isPresent();
    assertThat(grow.hostedCompletion(RUN, processId))
        .isInstanceOfSatisfying(
            HostedCompletionOffer.NotOffered.class,
            noStepIsLeft ->
                assertThat(noStepIsLeft.whatThisProviderHasInstead())
                    .contains("this hosted process has already reached an outcome"));
  }

  @Test
  void the_stripe_intent_the_dashboard_says_has_no_page_a_payer_finishes_offers_no_payer_step() {
    String intentId = aStripeHold();
    SimulatorProvider stripe = providers.find("stripe").orElseThrow();

    assertThat(HostedPages.of(providers.modules()).withNoPageAPayerFinishes()).contains("stripe");
    assertThat(stripe.state(RUN, intentId)).isPresent();
    assertThat(stripe.hostedCompletion(RUN, intentId))
        .isInstanceOf(HostedCompletionOffer.NotOffered.class);
  }

  @Test
  void the_payment_method_fixture_the_dashboard_says_exists_is_written_read_and_removed() {
    Map<String, Object> card = new LinkedHashMap<>();
    card.put("testRunId", RUN.value());
    card.put("merchantId", MERCHANT);
    card.put("ownerId", "cus_ui_dashboard_owner");
    card.put("type", "card");
    card.put("alias", "a-card-the-dashboard-speaks-of");
    card.put(
        "card",
        Map.of(
            "brand",
            "visa",
            "last4",
            "4242",
            "expMonth",
            12,
            "expYear",
            2030,
            "funding",
            "credit"));
    card.put(
        "providerIds",
        Map.of("paymentMethodId", "pm_card_visa", "customerId", "cus_ui_dashboard_owner"));

    Map<String, Object> seeded =
        http.post()
            .uri("/__simulator/stripe/fixtures/payment-methods")
            .contentType(MediaType.APPLICATION_JSON)
            .body(card)
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    String fixtureId = String.valueOf(seeded.get("fixtureId"));

    assertThat(theFixturesOfStripe()).hasSize(1);
    assertThat(page("/ui/payment-methods?provider=stripe&testRunId=" + RUN.value()))
        .contains(fixtureId);

    http.delete()
        .uri(
            "/__simulator/stripe/fixtures/payment-methods/"
                + fixtureId
                + "?testRunId="
                + RUN.value())
        .retrieve()
        .toBodilessEntity();

    assertThat(theFixturesOfStripe()).isEmpty();
  }

  private List<Map<String, Object>> theFixturesOfStripe() {
    return http.get()
        .uri("/__simulator/stripe/fixtures/payment-methods?testRunId=" + RUN.value())
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  @Test
  void a_test_run_a_tester_typed_that_nothing_registered_is_still_the_run_the_dashboard_reads() {
    String overview = page(DASHBOARD + "?testRunId=ui-dashboard-it-never-registered");

    assertThat(overview).contains("ui-dashboard-it-never-registered");
    assertThat(overview).contains("class=\"tag fact\">active");
    assertThat(overview)
        .contains("No provider of this test run has answered a call or attempted a callback yet");
  }

  @Test
  void a_test_run_shaped_like_nothing_the_simulator_stores_is_refused_on_the_dashboard() {
    Answer refused = answer(DASHBOARD + "?testRunId=" + URLEncoder.encode("not a run!", UTF_8));

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("testRunId");
    assertThat(refused.body()).doesNotContain("Provider availability in this test run");
  }

  @Test
  void every_screen_offers_the_dashboard_so_a_reader_can_leave_one_provider_for_all_of_them() {
    for (String screen : List.of("/ui", "/ui/audit", "/ui/webhooks", "/ui/scenarios")) {
      String body = page(screen + "?provider=grow&testRunId=" + RUN.value());

      assertThat(body).contains(DASHBOARD);
    }
  }

  @Test
  void the_test_runs_are_ordered_by_the_run_the_trail_recorded_an_exchange_in_last() {
    configureGrow(ANOTHER_RUN);
    aGrowHold(ANOTHER_RUN);
    aGrowHold(RUN);

    String overview = page(DASHBOARD + "?testRunId=" + RUN.value());

    assertThat(overview).contains("test run name contains");
    assertThat(overview).contains(DashboardScreen.THE_RUNS_THE_TRAIL_HAS_RECORDED);
    assertThat(overview.indexOf(">" + RUN.value() + "<"))
        .isLessThan(overview.indexOf(">" + ANOTHER_RUN.value() + "<"));
  }

  @Test
  void a_name_fragment_narrows_the_list_to_the_runs_that_carry_it() {
    configureGrow(ANOTHER_RUN);

    String narrowed =
        page(DASHBOARD + "?testRunId=" + RUN.value() + "&runMatch=dashboard-it-other");

    assertThat(narrowed).contains(">" + ANOTHER_RUN.value() + "<");
    assertThat(narrowed).contains("test runs known here carry dashboard-it-other");
    assertThat(narrowed).doesNotContain(">default<");
  }

  @Test
  void forgetting_a_run_from_the_dashboard_empties_it_and_drops_its_registration() {
    configureGrow(ANOTHER_RUN);
    String elsewhere = aGrowHold(ANOTHER_RUN);
    String here = aGrowHold(RUN);

    Answer forgotten = forgetFromTheDashboard(RUN.value(), ANOTHER_RUN.value(), "yes");
    String reopened = page(forgotten.location());
    String stillHere = page(DASHBOARD + "?testRunId=" + RUN.value());

    assertThat(forgotten.status()).isEqualTo(302);
    assertThat(testRuns.exists(ANOTHER_RUN)).isFalse();
    assertThat(reopened).contains("Test run " + ANOTHER_RUN.value() + " is forgotten");
    assertThat(reopened).contains("Its registration is dropped");
    assertThat(stillHere).contains(here).doesNotContain(elsewhere);
  }

  @Test
  void forgetting_a_run_nobody_confirmed_leaves_it_registered_and_says_what_forgetting_does() {
    configureGrow(ANOTHER_RUN);

    Answer refused = forgetFromTheDashboard(RUN.value(), ANOTHER_RUN.value(), null);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains(DashboardScreen.A_FORGETTING_IS_CONFIRMED);
    assertThat(testRuns.exists(ANOTHER_RUN)).isTrue();
  }

  @Test
  void forgetting_the_run_being_read_or_the_default_one_is_refused_and_neither_is_dropped() {
    Answer itself = forgetFromTheDashboard(RUN.value(), RUN.value(), "yes");
    Answer theDefault = forgetFromTheDashboard(RUN.value(), "default", "yes");

    assertThat(itself.body()).contains(DashboardScreen.A_REMOVAL_LEAVES_THE_RUN_BEING_READ);
    assertThat(theDefault.body()).contains(DashboardScreen.A_REMOVAL_LEAVES_THE_DEFAULT_RUN);
    assertThat(testRuns.exists(RUN)).isTrue();
    assertThat(testRuns.exists(TestRunId.of("default"))).isTrue();
  }

  @Test
  void the_run_being_read_offers_no_button_that_would_forget_it_from_under_the_reader() {
    configureGrow(ANOTHER_RUN);

    String overview = page(DASHBOARD + "?testRunId=" + RUN.value());

    assertThat(overview).contains(DashboardScreen.A_REMOVAL_LEAVES_THE_RUN_BEING_READ);
    assertThat(overview).contains("Forget this test run for good");
    assertThat(overview)
        .doesNotContain("name=\"forgetTestRunId\" value=\"" + RUN.value() + "\"")
        .contains("name=\"forgetTestRunId\" value=\"" + ANOTHER_RUN.value() + "\"");
  }

  @Test
  void the_runs_are_ordered_by_the_trail_and_not_by_the_clock_each_of_them_pinned() {
    pinTheClockOf(A_RUN_PINNED_LATE, LATE);
    writeAScenarioIn(A_RUN_PINNED_LATE);
    pinTheClockOf(A_RUN_PINNED_EARLY, EARLY);
    writeAScenarioIn(A_RUN_PINNED_EARLY);

    String overview = page(DASHBOARD + "?testRunId=" + RUN.value() + "&runMatch=it-clock");

    assertThat(theRunsListedOn(overview))
        .as(
            "the run touched last is listed first even though its clock is pinned ten years"
                + " before the other's, so the order is the trail's own and not the stamps'")
        .containsSubsequence(A_RUN_PINNED_EARLY.value(), A_RUN_PINNED_LATE.value());
    assertThat(theStampBesideTheRunNamed(overview, A_RUN_PINNED_EARLY.value()))
        .isEqualTo(EARLY.toString());
    assertThat(theStampBesideTheRunNamed(overview, A_RUN_PINNED_LATE.value()))
        .isEqualTo(LATE.toString());
  }

  @Test
  void the_stamp_is_the_newest_rows_time_and_not_the_newest_or_oldest_time_the_trail_holds() {
    pinTheClockOf(A_RUN_REPINNED, EARLY);
    writeAScenarioIn(A_RUN_REPINNED);
    pinTheClockOf(A_RUN_REPINNED, LATE);
    writeAScenarioIn(A_RUN_REPINNED);
    pinTheClockOf(A_RUN_REPINNED, BETWEEN);
    writeAScenarioIn(A_RUN_REPINNED);

    String overview = page(DASHBOARD + "?testRunId=" + RUN.value() + "&runMatch=repinned");

    assertThat(theStampBesideTheRunNamed(overview, A_RUN_REPINNED.value()))
        .as(
            "the run was pinned to "
                + EARLY
                + ", then to "
                + LATE
                + ", then to "
                + BETWEEN
                + " before its newest row, so neither the newest nor the oldest time the trail"
                + " holds of it is the time of its newest row")
        .isEqualTo(BETWEEN.toString());
  }

  @Test
  void a_forgotten_run_leaves_no_row_of_its_own_behind_in_the_trail() {
    configureGrow(ANOTHER_RUN);
    aGrowHold(ANOTHER_RUN);

    Answer forgotten = forgetFromTheDashboard(RUN.value(), ANOTHER_RUN.value(), "yes");

    assertThat(forgotten.status()).isEqualTo(302);
    assertThat(auditTrail.theRunsNewestTouchFirst())
        .as(
            "emptying the run records what it did against the run being read, so forgetting"
                + " leaves nothing the list could ever show and nothing a reset could ever clear")
        .extracting(TestRunActivity::testRunId)
        .doesNotContain(ANOTHER_RUN);
    assertThat(auditTrail.theRunsNewestTouchFirst())
        .extracting(TestRunActivity::testRunId)
        .contains(RUN);
  }

  @Test
  void forgetting_from_a_narrowed_list_comes_back_to_the_list_narrowed_the_same_way() {
    configureGrow(ANOTHER_RUN);

    Answer forgotten =
        forgetFromTheDashboard(RUN.value(), ANOTHER_RUN.value(), "yes", "dashboard-it-other");
    String reopened = page(forgotten.location());

    assertThat(forgotten.location()).contains("runMatch=dashboard-it-other");
    assertThat(reopened).contains("known here carries dashboard-it-other");
    assertThat(reopened).doesNotContain(">" + ANOTHER_RUN.value() + "<");
  }

  @Test
  void resetting_from_a_narrowed_list_comes_back_to_the_list_narrowed_the_same_way() {
    Answer reset = resetFromTheDashboard(RUN.value(), "dashboard-it-other");

    assertThat(reset.location()).contains("runMatch=dashboard-it-other");
  }

  private String aGrowHold() {
    return aGrowHold(RUN);
  }

  private String aGrowHold(TestRunId run) {
    String processId = aGrowProcess(run).processId();
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", run.value(), "providerObjectId", processId))
        .retrieve()
        .toBodilessEntity();
    return processId;
  }

  private Hold aGrowProcess() {
    return aGrowProcess(RUN);
  }

  private Hold aGrowProcess(TestRunId run) {
    Map<String, Object> created = parse(post(CREATE, holdForm(), run));
    assertThat(created).containsEntry("status", 1);
    Map<String, Object> data = dataOf(created);
    return new Hold(
        String.valueOf(data.get("processId")), String.valueOf(data.get("processToken")));
  }

  private String aStripeHold() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1099");
    form.put("currency", "pln");
    form.put("capture_method", "manual");
    form.put("payment_method", "pm_card_visa");
    form.put("confirm", "true");
    HttpResponse<byte[]> created =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + PAYMENT_INTENTS))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Bearer " + STRIPE_SECRET_KEY)
                .header(TestRunHeader.NAME, RUN.value())
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    assertThat(created.statusCode()).isEqualTo(200);
    return String.valueOf(
        json.readValue(
                new String(created.body(), UTF_8), new TypeReference<Map<String, Object>>() {})
            .get("id"));
  }

  private Answer createTestRun(String newTestRunId) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("newTestRunId", newTestRunId);
    return submit(DASHBOARD + "/test-run", form);
  }

  private Answer resetFromTheDashboard(String testRunId) {
    return resetFromTheDashboard(testRunId, null);
  }

  private Answer resetFromTheDashboard(String testRunId, @Nullable String runMatch) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("testRunId", testRunId);
    if (runMatch != null) {
      form.put("runMatch", runMatch);
    }
    return submit(DASHBOARD + "/reset", form);
  }

  private Answer forgetFromTheDashboard(
      String testRunId, String forgetTestRunId, @Nullable String confirm) {
    return forgetFromTheDashboard(testRunId, forgetTestRunId, confirm, null);
  }

  private Answer forgetFromTheDashboard(
      String testRunId,
      String forgetTestRunId,
      @Nullable String confirm,
      @Nullable String runMatch) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("testRunId", testRunId);
    form.put("forgetTestRunId", forgetTestRunId);
    if (confirm != null) {
      form.put("confirm", confirm);
    }
    if (runMatch != null) {
      form.put("runMatch", runMatch);
    }
    return submit(DASHBOARD + "/forget", form);
  }

  private void pinTheClockOf(TestRunId run, Instant fixedNow) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", run.value(),
                "clock", Map.of("fixedNow", fixedNow.toString()),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void writeAScenarioIn(TestRunId run) {
    http.post()
        .uri("/__simulator/grow/scenarios")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", run.value(),
                "match", Map.of("operation", "createPaymentProcess"),
                "action", Map.of("type", "FIXED_SUCCESS")))
        .retrieve()
        .toBodilessEntity();
  }

  private static List<String> theRunsListedOn(String page) {
    int from = page.indexOf("<h3>Test runs this simulator has registered</h3>");
    int until = page.indexOf("<h3>Start another test run</h3>", from);
    Matcher named = Pattern.compile(">([^<>\\s]+)</a>").matcher(page.substring(from, until));
    List<String> listed = new ArrayList<>();
    while (named.find()) {
      listed.add(named.group(1));
    }
    return List.copyOf(listed);
  }

  private static String theStampBesideTheRunNamed(String page, String testRunId) {
    int at = page.indexOf(">" + testRunId + "</a>");
    assertThat(at).as(testRunId + " is listed on this page").isNotNegative();
    String opening = "<td class=\"stamp\">";
    int stamp = page.indexOf(opening, at);
    assertThat(stamp).as(testRunId + " carries a stamp").isNotNegative();
    return page.substring(stamp + opening.length(), page.indexOf("</td>", stamp));
  }

  private Answer submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
  }

  private static Map<String, String> lookupOf(Hold hold) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", GROW_PAGE);
    form.put("processId", hold.processId());
    form.put("processToken", hold.processToken());
    return form;
  }

  private Map<String, String> holdForm() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("pageCode", GROW_PAGE);
    form.put("userId", GROW_USER);
    form.put("chargeType", "2");
    form.put("sum", HELD);
    form.put("saveCardToken", "0");
    form.put("description", "A hold");
    form.put("successUrl", "https://merchant.example/thanks");
    form.put("cancelUrl", "https://merchant.example/cancel");
    form.put("notifyUrl", merchant.url());
    form.put("pageField[fullName]", "A Payer");
    form.put("pageField[phone]", "0505503144");
    return form;
  }

  private String page(String uri) {
    Answer answer = answer(uri);
    assertThat(answer.status()).isEqualTo(200);
    return answer.body();
  }

  private Answer answer(String uri) {
    HttpResponse<byte[]> response =
        send(HttpRequest.newBuilder(at(uri)).timeout(Duration.ofSeconds(20)).GET());
    return new Answer(response.statusCode(), new String(response.body(), UTF_8), "");
  }

  private URI at(String uri) {
    return URI.create(uri.startsWith("http") ? uri : "http://localhost:" + port + uri);
  }

  private Map<String, Object> parse(HttpResponse<byte[]> response) {
    return json.readValue(
        new String(response.body(), UTF_8), new TypeReference<Map<String, Object>>() {});
  }

  private HttpResponse<byte[]> post(String path, Map<String, String> form, TestRunId run) {
    return send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header(TestRunHeader.NAME, run.value())
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

  private void configureGrow(TestRunId run) {
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                run.value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("userId", GROW_USER, "pageCode", GROW_PAGE))),
                "webhooks",
                List.of(Map.of("accountId", MERCHANT, "url", merchant.url()))))
        .retrieve()
        .toBodilessEntity();
  }

  private void configureStripe(TestRunId run) {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                run.value(),
                "clock",
                Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                List.of(
                    Map.of(
                        "accountId",
                        MERCHANT,
                        "configuration",
                        Map.of("secretKey", STRIPE_SECRET_KEY, "captureWindow", "P3D"))),
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void unconfigure(String provider, TestRunId run) {
    http.put()
        .uri("/__simulator/" + provider + "/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", run.value(),
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void resetEveryProvider(TestRunId run) {
    for (String provider : providers.names()) {
      http.post()
          .uri("/__simulator/" + provider + "/reset")
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("testRunId", run.value()))
          .retrieve()
          .toBodilessEntity();
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
  }

  private record Hold(String processId, String processToken) {}

  private record Answer(int status, String body, String location) {}
}
