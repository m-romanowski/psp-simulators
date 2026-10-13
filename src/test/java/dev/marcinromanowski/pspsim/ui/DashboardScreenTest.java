package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.audit.TestRunActivity;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DashboardScreenTest {

  private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
  private static final TestRunId RUN = TestRunId.of("ui-dashboard");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("ui-dashboard-other");
  private static final RetryPlan PLAN =
      new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5)));
  private static final int SHOWN = 20;
  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");

  private static final HostedPages.Split THE_FIVE_THIS_BUILD_SERVES =
      new HostedPages.Split(
          List.of("autra", "grow", "worldline"),
          List.of("azericard", "stripe"),
          4,
          "autra a card session and a Pix; grow a payment process;"
              + " worldline a hosted tokenization session");

  @Test
  void the_absent_hosted_session_count_names_every_provider_and_says_which_keep_such_an_object() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN, listing(RUN, RUN), List.of(), THE_FIVE_THIS_BUILD_SERVES, NOW, SHOWN);

    assertThat(view.whyItSumsNoHostedSession())
        .contains(
            "4 objects waiting for a payer on a page the provider hosts are kept by 3 of the 5"
                + " providers here")
        .contains("autra a card session and a Pix")
        .contains("grow a payment process")
        .contains("worldline a hosted tokenization session")
        .contains("The other 2, azericard and stripe, have no such page")
        .contains("read that opens an object of a run")
        .contains("is not built");
  }

  @Test
  void a_provider_that_moves_between_the_two_lists_moves_in_the_sentence_the_screen_prints() {
    DashboardScreen.View moved =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(),
            new HostedPages.Split(
                List.of("autra", "grow", "stripe", "worldline"),
                List.of("azericard"),
                5,
                "autra a card session and a Pix; grow a payment process;"
                    + " stripe a checkout session; worldline a hosted tokenization session"),
            NOW,
            SHOWN);

    assertThat(moved.whyItSumsNoHostedSession())
        .contains(
            "5 objects waiting for a payer on a page the provider hosts are kept by 4 of the 5"
                + " providers here")
        .contains("stripe a checkout session")
        .contains("The other 1, azericard, have no such page")
        .contains("so the Payments screen shows them and presses the payer's own step")
        .doesNotContain("so Objects shows them")
        .doesNotContain("The other 2, azericard and stripe");
  }

  @Test
  void the_absent_live_sessions_queue_says_where_an_object_waiting_for_a_payer_is_read_instead() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN, listing(RUN, RUN), List.of(), THE_FIVE_THIS_BUILD_SERVES, NOW, SHOWN);

    assertThat(view.whyItOpensNoLiveSessionsQueue())
        .contains("no queue of their own")
        .contains("listed beside every other object of this test run on the Payments screen")
        .doesNotContain("on Objects");
  }

  @Test
  void the_absent_payment_method_count_says_the_fixtures_exist_and_only_the_sum_does_not() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN, listing(RUN, RUN), List.of(), THE_FIVE_THIS_BUILD_SERVES, NOW, SHOWN);

    assertThat(view.whyItSumsNoPaymentMethod())
        .contains("A payment-method fixture is an ordinary object of this simulator")
        .contains("the Payment methods screen linked above seeds one and removes it")
        .contains("a persona seeds one in one press")
        .contains("a column summing them per provider")
        .doesNotContain("No payment-method fixture exists");
  }

  @Test
  void the_queue_of_every_provider_is_summed_rather_than_reported_from_one_of_them() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                aProvider("grow", deliveries("grow", 1, 2, 3)),
                aProvider("azericard", deliveries("azericard", 2, 3, 1)),
                aProvider("stripe", deliveries("stripe", 4, 1, 3))),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.totals().pending()).isEqualTo(13);
    assertThat(view.totals().due()).isEqualTo(6);
    assertThat(view.totals().exhausted()).isEqualTo(7);
    assertThat(view.totals().failing()).isEqualTo(20);
    assertThat(view.providers())
        .extracting(
            DashboardScreen.Provider::pending,
            DashboardScreen.Provider::due,
            DashboardScreen.Provider::exhausted,
            DashboardScreen.Provider::failing)
        .containsExactly(tuple(3, 2, 3, 6), tuple(5, 3, 1, 6), tuple(5, 1, 3, 8));
  }

  @Test
  void a_provider_no_account_is_configured_for_is_reported_as_answering_nothing() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                configured("grow", List.of("merchant-1", "merchant-2")),
                configured("azericard", List.of()),
                configured("stripe", List.of("merchant-1"))),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.providers())
        .extracting(
            DashboardScreen.Provider::provider,
            DashboardScreen.Provider::answersCallsInThisRun,
            DashboardScreen.Provider::accounts)
        .containsExactly(
            tuple("grow", true, 2), tuple("azericard", false, 0), tuple("stripe", true, 1));
    assertThat(view.totals().answering()).isEqualTo(2);
    assertThat(view.totals().providers()).isEqualTo(3);
  }

  @Test
  void the_exchanges_of_three_providers_are_interleaved_by_when_each_one_happened() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                trafficOf("grow", NOW.plusSeconds(30), NOW.plusSeconds(5)),
                trafficOf("azericard", NOW.plusSeconds(20)),
                trafficOf("stripe", NOW.plusSeconds(25), NOW.plusSeconds(10))),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.traffic())
        .extracting(DashboardScreen.Call::provider)
        .containsExactly("grow", "stripe", "azericard", "stripe", "grow");
  }

  @Test
  void a_callback_attempt_is_listed_beside_the_calls_a_client_made_to_the_provider() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                new DashboardScreen.Reading(
                    "grow",
                    List.of("merchant-1"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(aCall("aud_call", NOW.plusSeconds(1))),
                    List.of(aCallbackAttempt("aud_attempt", NOW.plusSeconds(2))))),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.traffic())
        .as(
            "this panel read the exchanges of one origin, so every callback attempt the simulator"
                + " made was missing from a panel whose own words promise both directions, and a"
                + " tester reading it after fourteen failed deliveries was told none had happened")
        .extracting(DashboardScreen.Call::auditId, DashboardScreen.Call::direction)
        .containsExactly(
            tuple("aud_attempt", AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT.name()),
            tuple("aud_call", AuditDirection.INBOUND_REQUEST.name()));
  }

  @Test
  void a_callback_attempt_that_reached_no_target_is_listed_with_no_path_rather_than_dropped() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                new DashboardScreen.Reading(
                    "grow",
                    List.of("merchant-1"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(aCallbackWithNoTarget("aud_nowhere", NOW)))),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.traffic())
        .extracting(DashboardScreen.Call::auditId, DashboardScreen.Call::path)
        .containsExactly(tuple("aud_nowhere", null));
  }

  @Test
  void the_last_exchange_a_provider_card_names_is_the_last_call_that_provider_answered() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                new DashboardScreen.Reading(
                    "grow",
                    List.of("merchant-1"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(aCall("aud_call", NOW.plusSeconds(1))),
                    List.of(aCallbackAttempt("aud_attempt", NOW.plusSeconds(900))))),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.providers())
        .extracting(DashboardScreen.Provider::lastCallAt)
        .as(
            "the column says whether this provider answered a call in this run, and a callback the"
                + " simulator sent out is not a call anyone made to it")
        .containsExactly(NOW.plusSeconds(1));
  }

  @Test
  void two_exchanges_of_one_provider_sharing_an_instant_keep_the_order_it_recorded_them_in() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                new DashboardScreen.Reading(
                    "grow",
                    List.of("merchant-1"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(aCall("aud_newer", NOW), aCall("aud_older", NOW)),
                    List.of())),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.traffic())
        .extracting(DashboardScreen.Call::path)
        .containsExactly("/aud_newer", "/aud_older");
  }

  @Test
  void every_traffic_row_carries_the_audit_id_of_the_one_exchange_it_is_so_it_can_link_to_itself() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                new DashboardScreen.Reading(
                    "grow",
                    List.of("merchant-1"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                        aCall("aud_newer", NOW.plusSeconds(2)),
                        aCall("aud_older", NOW.plusSeconds(1))),
                    List.of())),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.traffic())
        .extracting(DashboardScreen.Call::auditId, DashboardScreen.Call::path)
        .containsExactly(tuple("aud_newer", "/aud_newer"), tuple("aud_older", "/aud_older"));
  }

  @Test
  void an_overview_shows_no_more_exchanges_than_it_read_however_many_providers_answered() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                trafficOf("grow", NOW.plusSeconds(4), NOW.plusSeconds(1)),
                trafficOf("stripe", NOW.plusSeconds(3), NOW.plusSeconds(2))),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            3);

    assertThat(view.traffic()).hasSize(3);
    assertThat(view.traffic())
        .extracting(DashboardScreen.Call::createdAt)
        .containsExactly(NOW.plusSeconds(4), NOW.plusSeconds(3), NOW.plusSeconds(2));
  }

  @Test
  void the_newest_exchange_a_provider_recorded_is_the_one_the_overview_reports_for_it() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                trafficOf("grow", NOW.plusSeconds(9), NOW.plusSeconds(1)),
                aProvider("stripe", List.of())),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.providers().get(0).lastCallAt()).isEqualTo(NOW.plusSeconds(9));
    assertThat(view.providers().get(1).lastCallAt()).isNull();
  }

  @Test
  void a_test_run_a_tester_typed_that_nothing_registered_is_still_listed_as_the_active_one() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(RUN, DEFAULT_RUN, List.of(ANOTHER_RUN), List.of(), null);

    assertThat(everyRunListed(listed))
        .extracting(DashboardScreen.KnownRun::testRunId, DashboardScreen.KnownRun::active)
        .containsExactly(tuple(RUN.value(), true), tuple(ANOTHER_RUN.value(), false));
  }

  @Test
  void a_registered_test_run_is_listed_once_and_marked_active_when_it_is_the_one_being_read() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(RUN, DEFAULT_RUN, List.of(ANOTHER_RUN, RUN), List.of(), null);

    assertThat(everyRunListed(listed))
        .extracting(DashboardScreen.KnownRun::testRunId, DashboardScreen.KnownRun::active)
        .containsExactly(tuple(RUN.value(), true), tuple(ANOTHER_RUN.value(), false));
  }

  @Test
  void a_callback_a_merchant_refused_is_listed_under_the_provider_whose_merchant_refused_it() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                aProvider("grow", deliveries("grow", 0, 1, 0)),
                aProvider("stripe", deliveries("stripe", 0, 2, 0))),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.failing())
        .extracting(DashboardScreen.Failure::provider)
        .containsExactly("grow", "stripe", "stripe");
  }

  @Test
  void a_scenario_rule_is_listed_under_the_provider_it_was_written_for() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(withRules("grow", "scn_grow"), withRules("azericard", "scn_az_1", "scn_az_2")),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.scenarios())
        .extracting(rule -> rule.provider() + ":" + rule.scenario().scenarioId())
        .containsExactly("grow:scn_grow", "azericard:scn_az_1", "azericard:scn_az_2");
    assertThat(view.totals().scenarios()).isEqualTo(3);
  }

  @Test
  void an_exchange_the_simulator_recorded_no_outcome_for_reports_none_rather_than_inventing_one() {
    DashboardScreen.View view =
        DashboardScreen.of(
            RUN,
            listing(RUN, RUN),
            List.of(
                new DashboardScreen.Reading(
                    "grow",
                    List.of("merchant-1"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(
                        aCall("aud_decided", NOW.plusSeconds(2), ProviderOutcome.FAILURE),
                        aCall("aud_undecided", NOW.plusSeconds(1), null)),
                    List.of())),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.traffic())
        .extracting(DashboardScreen.Call::outcome)
        .containsExactly("FAILURE", null);
  }

  @Test
  void a_request_that_names_no_test_run_is_answered_about_the_one_the_simulator_defaults_to() {
    assertThat(DashboardScreen.Asked.of(null, RUN)).isEqualTo(new DashboardScreen.Asked.Run(RUN));
    assertThat(DashboardScreen.Asked.of("  ", RUN)).isEqualTo(new DashboardScreen.Asked.Run(RUN));
  }

  @Test
  void a_test_run_the_simulator_reserves_is_refused_by_name_rather_than_read() {
    DashboardScreen.Asked asked = DashboardScreen.Asked.of("UNKNOWN", RUN);

    assertThat(asked).isInstanceOf(DashboardScreen.Asked.Refused.class);
    assertThat(((DashboardScreen.Asked.Refused) asked).message()).contains("reserved");
  }

  @Test
  void a_test_run_shaped_like_nothing_the_simulator_stores_is_refused_rather_than_looked_up() {
    DashboardScreen.Asked asked = DashboardScreen.Asked.of("not a run!", RUN);

    assertThat(asked).isInstanceOf(DashboardScreen.Asked.Refused.class);
    assertThat(((DashboardScreen.Asked.Refused) asked).message()).contains("testRunId");
  }

  @Test
  void the_run_a_tester_asked_for_is_the_one_the_overview_names() {
    DashboardScreen.View view =
        DashboardScreen.of(
            ANOTHER_RUN,
            listing(ANOTHER_RUN, RUN),
            List.of(),
            THE_FIVE_THIS_BUILD_SERVES,
            NOW,
            SHOWN);

    assertThat(view.testRunId()).isEqualTo(ANOTHER_RUN.value());
    assertThat(view.now()).isEqualTo(NOW);
    assertThat(view.exchangesRead()).isEqualTo(SHOWN);
  }

  private static DashboardScreen.Reading aProvider(
      String provider, List<WebhookDelivery> deliveries) {
    return new DashboardScreen.Reading(
        provider,
        List.of("merchant-1"),
        List.of(new WebhookTarget("merchant-1", "http://localhost:9/callback")),
        List.of("anOperation"),
        List.of(),
        deliveries,
        List.of(),
        List.of());
  }

  private static DashboardScreen.Reading configured(String provider, List<String> accountIds) {
    return new DashboardScreen.Reading(
        provider, accountIds, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
  }

  private static DashboardScreen.Reading withRules(String provider, String... scenarioIds) {
    List<ScenarioRule> rules = new ArrayList<>();
    for (String scenarioId : scenarioIds) {
      rules.add(aRule(provider, scenarioId));
    }
    return new DashboardScreen.Reading(
        provider,
        List.of("merchant-1"),
        List.of(),
        List.of(),
        rules,
        List.of(),
        List.of(),
        List.of());
  }

  private static DashboardScreen.Reading trafficOf(String provider, Instant... newestFirst) {
    List<AuditRecord> records = new ArrayList<>();
    for (Instant at : newestFirst) {
      records.add(aCall("aud_" + provider + "_" + at.getEpochSecond(), at));
    }
    return new DashboardScreen.Reading(
        provider,
        List.of("merchant-1"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        records,
        List.of());
  }

  private static List<WebhookDelivery> deliveries(
      String provider, int waiting, int due, int exhausted) {
    List<WebhookDelivery> deliveries = new ArrayList<>();
    for (int number = 0; number < waiting; number++) {
      deliveries.add(
          aDelivery(
              provider,
              provider + "_waiting_" + number,
              DeliveryState.PENDING,
              1,
              NOW.plusSeconds(1)));
    }
    for (int number = 0; number < due; number++) {
      deliveries.add(
          aDelivery(
              provider,
              provider + "_due_" + number,
              DeliveryState.PENDING,
              1,
              NOW.minusSeconds(1)));
    }
    for (int number = 0; number < exhausted; number++) {
      deliveries.add(
          aDelivery(provider, provider + "_spent_" + number, DeliveryState.EXHAUSTED, 3, null));
    }
    return List.copyOf(deliveries);
  }

  private static ScenarioRule aRule(String provider, String scenarioId) {
    return new ScenarioRule(
        scenarioId,
        ProviderId.of(provider),
        RUN,
        0,
        1L,
        ScenarioMatch.anyRequest(),
        new ScenarioAction.FixedSuccess(),
        null,
        NOW);
  }

  private static AuditRecord aCallbackAttempt(String auditId, Instant at) {
    return aCallback(auditId, at, "/callbacks");
  }

  private static AuditRecord aCallbackWithNoTarget(String auditId, Instant at) {
    return aCallback(auditId, at, null);
  }

  private static AuditRecord aCallback(String auditId, Instant at, @Nullable String path) {
    return new AuditRecord(
        auditId,
        RUN,
        TestRunResolutionRule.TEST_HEADER,
        ProviderId.of("grow"),
        null,
        ObjectOrigin.SCHEDULED_JOB,
        path == null
            ? AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT
            : AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT,
        null,
        "POST",
        path,
        path == null ? null : 500,
        "obj_1",
        null,
        null,
        1,
        at,
        Map.of(),
        Map.of(),
        null);
  }

  private static AuditRecord aCall(String auditId, Instant at) {
    return aCall(auditId, at, ProviderOutcome.SUCCESS);
  }

  private static AuditRecord aCall(String auditId, Instant at, @Nullable ProviderOutcome outcome) {
    return new AuditRecord(
        auditId,
        RUN,
        TestRunResolutionRule.TEST_HEADER,
        ProviderId.of("grow"),
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.INBOUND_REQUEST,
        outcome,
        "POST",
        "/" + auditId,
        200,
        "obj_1",
        null,
        null,
        1,
        at,
        Map.of(),
        Map.of(),
        null);
  }

  private static WebhookDelivery aDelivery(
      String provider,
      String deliveryId,
      DeliveryState state,
      int attemptsMade,
      @Nullable Instant nextAttemptAt) {
    return new WebhookDelivery(
        deliveryId,
        ProviderId.of(provider),
        RUN,
        "merchant-1",
        "evt_" + deliveryId,
        "object.changed",
        "obj_" + deliveryId,
        "http://localhost:9/callback",
        aPayload(),
        PLAN,
        state,
        false,
        attemptsMade,
        nextAttemptAt,
        NOW,
        NOW,
        null,
        null);
  }

  private static WebhookPayload aPayload() {
    return new WebhookPayload(
        "application/json",
        Map.of(),
        "{}".getBytes(StandardCharsets.UTF_8),
        Map.of("id", List.of("obj_1")));
  }

  @Test
  void a_run_the_trail_has_recorded_is_listed_before_one_it_has_not_and_carries_its_stamp() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            DEFAULT_RUN,
            DEFAULT_RUN,
            List.of(DEFAULT_RUN, TestRunId.of("ghost-194902"), TestRunId.of("wizard-check")),
            List.of(
                new TestRunActivity(TestRunId.of("wizard-check"), NOW.minusSeconds(60), 14),
                new TestRunActivity(DEFAULT_RUN, NOW.minusSeconds(900), 3)),
            null);

    assertThat(listed.groups())
        .extracting(DashboardScreen.RunGroup::heading)
        .containsExactly(
            DashboardScreen.THE_RUNS_THE_TRAIL_HAS_RECORDED, DashboardScreen.THE_RUNS_IT_HAS_NOT);
    assertThat(listed.groups().get(0).runs())
        .extracting(
            DashboardScreen.KnownRun::testRunId,
            DashboardScreen.KnownRun::lastTouchedAt,
            DashboardScreen.KnownRun::auditRows)
        .containsExactly(
            tuple(DEFAULT_RUN.value(), NOW.minusSeconds(900), 3),
            tuple("wizard-check", NOW.minusSeconds(60), 14));
    assertThat(listed.groups().get(1).runs())
        .extracting(
            DashboardScreen.KnownRun::testRunId,
            DashboardScreen.KnownRun::lastTouchedAt,
            DashboardScreen.KnownRun::auditRows)
        .containsExactly(tuple("ghost-194902", null, 0));
  }

  @Test
  void the_runs_the_trail_has_recorded_are_ordered_by_the_order_the_trail_reports_them_in() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            DEFAULT_RUN,
            DEFAULT_RUN,
            List.of(
                DEFAULT_RUN,
                TestRunId.of("curl-check"),
                TestRunId.of("doc-walk-185254"),
                TestRunId.of("wizard-check")),
            List.of(
                new TestRunActivity(TestRunId.of("doc-walk-185254"), NOW.minusSeconds(5), 2),
                new TestRunActivity(TestRunId.of("wizard-check"), NOW.minusSeconds(9), 7),
                new TestRunActivity(TestRunId.of("curl-check"), NOW.minusSeconds(1), 1)),
            null);

    assertThat(listed.groups().get(0).runs())
        .extracting(DashboardScreen.KnownRun::testRunId)
        .containsExactly("doc-walk-185254", "wizard-check", "curl-check");
  }

  @Test
  void the_run_being_read_is_pinned_to_the_top_of_its_group_however_recently_it_exchanged() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            TestRunId.of("wizard-check"),
            DEFAULT_RUN,
            List.of(DEFAULT_RUN, TestRunId.of("curl-check"), TestRunId.of("wizard-check")),
            List.of(
                new TestRunActivity(TestRunId.of("curl-check"), NOW.minusSeconds(1), 1),
                new TestRunActivity(TestRunId.of("wizard-check"), NOW.minusSeconds(3600), 7)),
            null);

    assertThat(listed.groups().get(0).runs())
        .extracting(DashboardScreen.KnownRun::testRunId, DashboardScreen.KnownRun::active)
        .containsExactly(tuple("wizard-check", true), tuple("curl-check", false));
  }

  @Test
  void a_run_already_first_in_its_group_is_left_where_it_is() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            TestRunId.of("curl-check"),
            DEFAULT_RUN,
            List.of(DEFAULT_RUN, TestRunId.of("curl-check"), TestRunId.of("wizard-check")),
            List.of(
                new TestRunActivity(TestRunId.of("curl-check"), NOW.minusSeconds(1), 1),
                new TestRunActivity(TestRunId.of("wizard-check"), NOW.minusSeconds(3600), 7)),
            null);

    assertThat(listed.groups().get(0).runs())
        .extracting(DashboardScreen.KnownRun::testRunId)
        .containsExactly("curl-check", "wizard-check");
  }

  @Test
  void a_trail_row_of_a_run_this_simulator_no_longer_knows_puts_no_row_in_the_list() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            DEFAULT_RUN,
            DEFAULT_RUN,
            List.of(DEFAULT_RUN),
            List.of(new TestRunActivity(TestRunId.of("forgotten-one"), NOW.minusSeconds(1), 9)),
            null);

    assertThat(everyRunListed(listed))
        .extracting(DashboardScreen.KnownRun::testRunId)
        .containsExactly(DEFAULT_RUN.value());
    assertThat(listed.whatItHolds())
        .isEqualTo(
            "1 test run is known here: 0 have been touched and 1 has not. Every one of them is"
                + " listed below.");
  }

  @Test
  void the_runs_the_trail_recorded_stop_at_six_and_the_line_above_says_how_many_are_left_out() {
    List<TestRunId> known = new ArrayList<>();
    List<TestRunActivity> touched = new ArrayList<>();
    known.add(DEFAULT_RUN);
    for (int nth = 0; nth < 8; nth++) {
      TestRunId run = TestRunId.of("used-" + nth);
      known.add(run);
      touched.add(new TestRunActivity(run, NOW.minusSeconds(nth), 1 + nth));
    }
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            TestRunId.of("used-0"), DEFAULT_RUN, List.copyOf(known), List.copyOf(touched), null);

    assertThat(listed.groups()).hasSize(2);
    assertThat(listed.groups().get(0).runs()).hasSize(6);
    assertThat(listed.groups().get(0).runs())
        .extracting(DashboardScreen.KnownRun::testRunId)
        .containsExactly("used-0", "used-1", "used-2", "used-3", "used-4", "used-5");
    assertThat(listed.whatItHolds())
        .isEqualTo(
            "9 test runs are known here: 8 have been touched and 1 has not. This list holds the 6"
                + " touched most recently and 1 never touched; narrow the list to reach another.");
  }

  @Test
  void the_runs_never_touched_stop_at_three_even_when_every_touched_run_fits() {
    List<TestRunId> known = new ArrayList<>();
    for (int nth = 0; nth < 5; nth++) {
      known.add(TestRunId.of("idle-" + nth));
    }
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            TestRunId.of("idle-0"),
            DEFAULT_RUN,
            List.copyOf(known),
            List.of(new TestRunActivity(TestRunId.of("idle-0"), NOW, 4)),
            null);

    assertThat(listed.groups().get(0).runs()).hasSize(1);
    assertThat(listed.groups().get(1).runs()).hasSize(3);
    assertThat(listed.whatItHolds())
        .isEqualTo(
            "5 test runs are known here: 1 has been touched and 4 have not. This list holds the 1"
                + " touched most recently and 3 never touched; narrow the list to reach another.");
  }

  @Test
  void a_name_fragment_narrows_both_groups_whatever_case_it_is_typed_in() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            DEFAULT_RUN,
            DEFAULT_RUN,
            List.of(
                DEFAULT_RUN,
                TestRunId.of("Wizard-check"),
                TestRunId.of("wizard-probe"),
                TestRunId.of("curl-check")),
            List.of(new TestRunActivity(TestRunId.of("Wizard-check"), NOW.minusSeconds(4), 6)),
            "  WIZARD ");

    assertThat(listed.match()).isEqualTo("wizard");
    assertThat(everyRunListed(listed))
        .extracting(DashboardScreen.KnownRun::testRunId)
        .containsExactly("Wizard-check", "wizard-probe");
    assertThat(listed.whatItHolds())
        .isEqualTo(
            "2 of the 4 test runs known here carry wizard: 1 has been touched and 1 has not."
                + " Every one of them is listed below.");
  }

  @Test
  void one_of_a_thing_is_counted_in_the_singular_and_everything_else_in_the_plural() {
    assertThat(DashboardScreen.asMany(1, "has", "have"))
        .as(
            "the line read \"1 test runs are known here: 1 have been touched\", which is the"
                + " screen telling a tester with no technical knowledge that it does not read its"
                + " own numbers")
        .isEqualTo("1 has");
    assertThat(DashboardScreen.asMany(0, "has", "have")).isEqualTo("0 have");
    assertThat(DashboardScreen.asMany(2, "has", "have")).isEqualTo("2 have");
  }

  @Test
  void the_only_run_a_narrowed_list_matches_is_counted_in_the_singular_too() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            DEFAULT_RUN,
            DEFAULT_RUN,
            List.of(DEFAULT_RUN),
            List.of(new TestRunActivity(DEFAULT_RUN, NOW, 2)),
            "def");

    assertThat(listed.whatItHolds())
        .isEqualTo(
            "1 of the 1 test run known here carries def: 1 has been touched and 0 have not. Every"
                + " one of them is listed below.");
  }

  @Test
  void a_fragment_no_run_carries_leaves_no_group_to_list_and_the_line_says_none_carries_it() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            DEFAULT_RUN,
            DEFAULT_RUN,
            List.of(DEFAULT_RUN, TestRunId.of("curl-check")),
            List.of(new TestRunActivity(DEFAULT_RUN, NOW, 2)),
            "nothing-here");

    assertThat(listed.groups()).isEmpty();
    assertThat(listed.whatItHolds())
        .isEqualTo("No test run of the 2 known here carries nothing-here.");
  }

  @Test
  void a_blank_fragment_narrows_nothing_and_is_reported_as_no_fragment_at_all() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            DEFAULT_RUN,
            DEFAULT_RUN,
            List.of(DEFAULT_RUN, TestRunId.of("curl-check")),
            List.of(),
            "   ");

    assertThat(listed.match()).isNull();
    assertThat(everyRunListed(listed))
        .extracting(DashboardScreen.KnownRun::testRunId)
        .containsExactly(DEFAULT_RUN.value(), "curl-check");
  }

  @Test
  void the_run_being_read_names_itself_as_the_reason_it_is_not_forgotten() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(
            TestRunId.of("curl-check"),
            DEFAULT_RUN,
            List.of(DEFAULT_RUN, TestRunId.of("curl-check"), TestRunId.of("wizard-check")),
            List.of(),
            null);

    assertThat(everyRunListed(listed))
        .extracting(
            DashboardScreen.KnownRun::testRunId,
            DashboardScreen.KnownRun::forgettable,
            DashboardScreen.KnownRun::whyItCannotBeForgotten)
        .containsExactly(
            tuple("curl-check", false, DashboardScreen.A_REMOVAL_LEAVES_THE_RUN_BEING_READ),
            tuple(DEFAULT_RUN.value(), false, DashboardScreen.A_REMOVAL_LEAVES_THE_DEFAULT_RUN),
            tuple("wizard-check", true, null));
  }

  @Test
  void the_default_run_read_as_the_active_one_is_refused_as_the_run_being_read() {
    DashboardScreen.Listing listed =
        DashboardScreen.listingOf(DEFAULT_RUN, DEFAULT_RUN, List.of(DEFAULT_RUN), List.of(), null);

    assertThat(everyRunListed(listed))
        .extracting(DashboardScreen.KnownRun::whyItCannotBeForgotten)
        .containsExactly(DashboardScreen.A_REMOVAL_LEAVES_THE_RUN_BEING_READ);
  }

  @Test
  void the_run_being_read_is_never_built_as_one_a_click_could_forget() {
    assertThatThrownBy(() -> new DashboardScreen.KnownRun("ui-dashboard", true, null, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the run being read is never forgotten")
        .hasMessageContaining("ui-dashboard");
  }

  @Test
  void a_group_nothing_falls_into_is_never_built() {
    assertThatThrownBy(() -> new DashboardScreen.RunGroup("Used", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Used");
  }

  @Test
  void forgetting_nothing_is_declined_and_asks_for_a_run() {
    assertThat(DashboardScreen.Forgetting.of(null, "yes", RUN, DEFAULT_RUN))
        .isEqualTo(
            new DashboardScreen.Forgetting.Declined(DashboardScreen.A_FORGETTING_NAMES_A_RUN));
    assertThat(DashboardScreen.Forgetting.of("  ", "yes", RUN, DEFAULT_RUN))
        .isEqualTo(
            new DashboardScreen.Forgetting.Declined(DashboardScreen.A_FORGETTING_NAMES_A_RUN));
  }

  @Test
  void forgetting_a_name_no_test_run_could_carry_is_declined_in_the_words_of_the_shape() {
    assertThat(DashboardScreen.Forgetting.of("-not a run-", "yes", RUN, DEFAULT_RUN))
        .isInstanceOf(DashboardScreen.Forgetting.Declined.class)
        .extracting(declined -> ((DashboardScreen.Forgetting.Declined) declined).message())
        .asString()
        .contains("testRunId must match");
  }

  @Test
  void forgetting_a_run_nobody_confirmed_is_declined_and_says_what_forgetting_does() {
    assertThat(DashboardScreen.Forgetting.of("wizard-check", null, RUN, DEFAULT_RUN))
        .isEqualTo(
            new DashboardScreen.Forgetting.Declined(DashboardScreen.A_FORGETTING_IS_CONFIRMED));
    assertThat(DashboardScreen.Forgetting.of("wizard-check", "no", RUN, DEFAULT_RUN))
        .isEqualTo(
            new DashboardScreen.Forgetting.Declined(DashboardScreen.A_FORGETTING_IS_CONFIRMED));
  }

  @Test
  void forgetting_the_run_being_read_or_the_default_one_is_declined_even_when_confirmed() {
    assertThat(DashboardScreen.Forgetting.of(RUN.value(), "yes", RUN, DEFAULT_RUN))
        .isEqualTo(
            new DashboardScreen.Forgetting.Declined(
                DashboardScreen.A_REMOVAL_LEAVES_THE_RUN_BEING_READ));
    assertThat(DashboardScreen.Forgetting.of(DEFAULT_RUN.value(), "yes", RUN, DEFAULT_RUN))
        .isEqualTo(
            new DashboardScreen.Forgetting.Declined(
                DashboardScreen.A_REMOVAL_LEAVES_THE_DEFAULT_RUN));
  }

  @Test
  void forgetting_another_run_with_confirm_ticked_is_granted_under_the_name_it_named() {
    assertThat(DashboardScreen.Forgetting.of(" wizard-check ", "yes", RUN, DEFAULT_RUN))
        .isEqualTo(new DashboardScreen.Forgetting.Granted(TestRunId.of("wizard-check")));
  }

  private static DashboardScreen.Listing listing(TestRunId active, TestRunId... registered) {
    return DashboardScreen.listingOf(active, DEFAULT_RUN, List.of(registered), List.of(), null);
  }

  private static List<DashboardScreen.KnownRun> everyRunListed(DashboardScreen.Listing listed) {
    List<DashboardScreen.KnownRun> runs = new ArrayList<>();
    for (DashboardScreen.RunGroup group : listed.groups()) {
      runs.addAll(group.runs());
    }
    return List.copyOf(runs);
  }
}
