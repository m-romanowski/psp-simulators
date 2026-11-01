package dev.marcinromanowski.pspsim.journey;

import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.PAYMENTS;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.RUN;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SECRET;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.SETUPS;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.payment;
import static dev.marcinromanowski.pspsim.journey.StripeJourneyClient.with;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDecision;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeWriteAtomicityIntegrationTest extends PostgresBackedTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UnitOfWork units;
  @Autowired private ScenarioEngine scenarios;
  @LocalServerPort private int port;
  private StripeJourneyClient app;

  @BeforeEach
  void configure() {
    app = new StripeJourneyClient(port);
  }

  @AfterEach
  void clean() {
    for (String table :
        new String[] {
          "stripe_payment_intent", "stripe_setup_intent", "simulator_webhook_delivery"
        }) {
      jdbc.execute("alter table " + table + " drop constraint if exists stripe_r1_forced_failure");
    }
    app.close();
  }

  @ParameterizedTest
  @ValueSource(strings = {"payment-create", "payment-confirm", "setup-create", "setup-confirm"})
  void a_failed_write_rolls_back_the_method_story_charge_ledger_and_key(String variant) {
    boolean setup = variant.startsWith("setup");
    boolean confirm = variant.endsWith("confirm");
    String base = setup ? SETUPS : PAYMENTS;
    String path = base;
    String beforeId = "";
    if (confirm) {
      beforeId =
          app.ok(
                  "POST",
                  base,
                  SECRET,
                  setup ? Map.of("allowed_payment_method_types[]", "card") : payment("1000"))
              .at("id");
      path += "/" + beforeId + "/confirm";
    }
    String operation = base + (confirm ? "/{id}/confirm" : "");
    if (!setup || confirm) {
      story(
          "POST " + operation,
          Map.of("type", "TERMINAL_FAILURE", "providerCode", "insufficient_funds"));
    }
    String table = setup ? "stripe_setup_intent" : "stripe_payment_intent";
    String methodColumn = setup ? "payment_method_id" : "payment_method";
    jdbc.execute(
        "alter table "
            + table
            + " add constraint stripe_r1_forced_failure check (test_run_id <> 'stripe-acceptance-journey' or "
            + methodColumn
            + " is null) not valid");
    Map<String, String> form =
        with(
            card(),
            "amount",
            "1000",
            "currency",
            "usd",
            "confirm",
            "true",
            "allowed_payment_method_types[]",
            "card");
    var failed =
        app.request("POST", path, SECRET, form, Map.of("Idempotency-Key", "write-must-roll-back"));
    assertThat(failed.status()).isEqualTo(500);
    assertThat(count("stripe_payment_method")).isZero();
    assertThat(count("stripe_charge")).isZero();
    assertThat(count("stripe_payment_intent_operation")).isZero();
    assertThat(count("stripe_idempotency_key")).isZero();
    assertThat(count("simulator_scenario_story_state")).isZero();
    assertThat(count("simulator_webhook_delivery")).isZero();
    assertThat(app.endpoint.received()).isEmpty();
    if (confirm) {
      assertThat(app.ok("GET", base + "/" + beforeId, SECRET, Map.of()).at("status"))
          .isEqualTo("requires_payment_method");
    } else {
      assertThat(count(table)).isZero();
    }
    jdbc.execute("alter table " + table + " drop constraint stripe_r1_forced_failure");
    var corrected =
        app.request("POST", path, SECRET, form, Map.of("Idempotency-Key", "write-must-roll-back"));
    assertThat(corrected.status()).isEqualTo(setup && !confirm ? 200 : 402);
    if (!setup || confirm) {
      assertThat(corrected.at("error.decline_code")).isEqualTo("insufficient_funds");
    }
    assertThat(count("stripe_payment_method")).isEqualTo(1);
  }

  @Test
  void a_discarded_unit_rolls_back_a_preempts_story_while_a_delivered_preempt_advances_it() {
    story(
        "POST /v1/payment_intents",
        Map.of(
            "type",
            "TRANSPORT_FAULT_INSTEAD_OF_OPERATION",
            "fault",
            "SERVER_ERROR",
            "httpStatus",
            502));
    ScenarioDecision discarded =
        units.keepingOnly(
            _ -> false,
            () ->
                scenarios.decide(
                    new ScenarioRequest(
                        ProviderId.of("stripe"),
                        TestRunId.of(RUN),
                        "payment-intent:pi_discarded",
                        "merchant-1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "POST /v1/payment_intents",
                        Map.of())));
    assertThat(discarded).isInstanceOf(ScenarioDecision.Preempt.class);
    assertThat(count("simulator_scenario_story_state")).isZero();
    Map<String, String> form =
        with(
            card(),
            "amount",
            "1000",
            "currency",
            "usd",
            "confirm",
            "true",
            "allowed_payment_method_types[]",
            "card");
    assertThat(
            app.request(
                    "POST", PAYMENTS, SECRET, form, Map.of("Idempotency-Key", "discarded-story"))
                .status())
        .isEqualTo(502);
    assertThat(count("simulator_scenario_story_state")).isEqualTo(1);
    assertThat(count("stripe_payment_method")).isZero();
    assertThat(count("stripe_payment_intent")).isZero();
    assertThat(count("stripe_idempotency_key")).isZero();
    assertThat(
            app.ok("POST", PAYMENTS, SECRET, form, Map.of("Idempotency-Key", "discarded-story"))
                .at("status"))
        .isEqualTo("succeeded");
  }

  @Test
  void a_failure_while_scheduling_the_outcome_rolls_back_every_earlier_write() {
    story(
        "POST /v1/payment_intents",
        Map.of(
            "type",
            "PROCESSING_THEN_TERMINAL",
            "settlesAfterMs",
            1000,
            "providerCode",
            "insufficient_funds"));
    jdbc.execute(
        "alter table simulator_webhook_delivery add constraint stripe_r1_forced_failure check (test_run_id <> 'stripe-acceptance-journey') not valid");
    Map<String, String> form =
        with(
            card(),
            "amount",
            "1000",
            "currency",
            "usd",
            "confirm",
            "true",
            "allowed_payment_method_types[]",
            "card");
    assertThat(
            app.request(
                    "POST", PAYMENTS, SECRET, form, Map.of("Idempotency-Key", "schedule-failure"))
                .status())
        .isEqualTo(500);
    assertThat(count("stripe_payment_intent")).isZero();
    assertThat(count("stripe_payment_method")).isZero();
    assertThat(count("simulator_scenario_story_state")).isZero();
    assertThat(count("stripe_idempotency_key")).isZero();
    assertThat(count("simulator_webhook_delivery")).isZero();
    assertThat(app.endpoint.received()).isEmpty();
    jdbc.execute("alter table simulator_webhook_delivery drop constraint stripe_r1_forced_failure");
    assertThat(
            app.ok("POST", PAYMENTS, SECRET, form, Map.of("Idempotency-Key", "schedule-failure"))
                .at("status"))
        .isEqualTo("processing");
    app.advance("PT1S");
    app.control("POST", "/webhooks/deliver-due", Map.of("testRunId", RUN));
    assertThat(app.endpoint.received()).hasSize(1);
    var jobs =
        app.request(
            "GET", "/__simulator/stripe/webhooks", SECRET, Map.of("testRunId", RUN), Map.of());
    assertThat(JsonParser.parseString(jobs.body()).getAsJsonArray()).hasSize(1);
  }

  private void story(String operation, Map<String, ?> action) {
    app.control(
        "POST",
        "/scenarios",
        Map.of(
            "testRunId",
            RUN,
            "priority",
            100,
            "match",
            Map.of("operation", operation),
            "action",
            action,
            "story",
            "r1-write",
            "keyedOn",
            "TEST_RUN",
            "whenState",
            "Started",
            "thenState",
            "Consumed"));
  }

  private int count(String table) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where test_run_id = ?", Integer.class, RUN);
  }

  private static Map<String, String> card() {
    return Map.of(
        "payment_method_data[type]",
        "card",
        "payment_method_data[card][number]",
        "4242424242424242",
        "payment_method_data[card][exp_month]",
        "12",
        "payment_method_data[card][exp_year]",
        "2030",
        "payment_method_data[card][cvc]",
        "123");
  }
}
