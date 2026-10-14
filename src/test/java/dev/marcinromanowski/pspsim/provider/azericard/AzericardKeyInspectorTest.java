package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AzericardKeyInspectorTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("azericard-run");

  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String NOW_ON_THE_WIRE = "20260910120000";

  private static final String TERMINAL_ID = "77777777";
  private static final String MERCHANT = "merchant-1";
  private static final String ORDER = "20220628084800";
  private static final String ANOTHER_ORDER_ENDING_THE_SAME = "20230101084800";
  private static final String TRACE = "084800";
  private static final String DECLINE_CODE = "05";
  private static final String NONCE = "F2B2DD7E603A7ADA";
  private static final String ROUTE = "/cgi-bin/cgi_link";
  private static final String HELD = "11.48";

  private static final String INT_REF = "0000000000000001";
  private static final String RRN = "100000000001";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Runs testRuns = new AzericardDoubles.Runs(DEFAULT_RUN);
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();

  @Test
  void an_authorization_answered_once_is_one_first_request_under_the_order_it_carried() {
    accounts.with(AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID));

    gateway().cgiLink(call(signed(authorization(ORDER))));

    KeyedExchange exchange = theExchangeUnder(AzericardKeyMechanisms.ORDER, TRACE);
    assertThat(exchange.role()).isEqualTo(KeyRole.DUPLICATE_DETECTION_KEY);
    assertThat(exchange.accountId()).isEqualTo(MERCHANT);
    assertThat(exchange.operation()).isEqualTo("TRTYPE=0");
    assertThat(exchange.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST);
    assertThat(exchange.firstRequest().providerObjectId()).isEqualTo(INT_REF);
    assertThat(exchange.firstRequest().stateChanged()).isTrue();
  }

  @Test
  void a_declined_authorization_is_recorded_under_its_order_as_the_first_request_of_that_trace() {
    accounts.with(AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure(DECLINE_CODE),
            null));

    AzericardApiResponse declined = gateway().cgiLink(call(signed(authorization(ORDER))));

    assertThat(declined.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    KeyedExchange exchange = theExchangeUnder(AzericardKeyMechanisms.ORDER, TRACE);
    assertThat(exchange.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST);
    KeyedRequest recorded = exchange.firstRequest();
    assertThat(recorded.body())
        .contains("\"action\":[\"2\"]")
        .contains("\"rc\":[\"" + DECLINE_CODE);
    assertThat(recorded.providerObjectId()).isEqualTo(INT_REF);
    assertThat(recorded.stateChanged()).isTrue();
  }

  @Test
  void a_declined_authorization_that_repeats_a_trace_is_recorded_as_the_duplicate_refusal() {
    accounts.with(AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest(),
            new ScenarioAction.TerminalFailure(DECLINE_CODE),
            null));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization(ORDER))));

    gateway.cgiLink(call(signed(authorization(ANOTHER_ORDER_ENDING_THE_SAME))));

    assertThat(theExchangeUnder(AzericardKeyMechanisms.ORDER, TRACE).requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
  }

  @Test
  void a_second_order_ending_in_the_digits_of_the_first_is_recorded_as_a_duplicate_refusal() {
    accounts.with(AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization(ORDER))));

    AzericardApiResponse repeated =
        gateway.cgiLink(call(signed(authorization(ANOTHER_ORDER_ENDING_THE_SAME))));

    assertThat(repeated.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    KeyedExchange exchange = theExchangeUnder(AzericardKeyMechanisms.ORDER, TRACE);
    assertThat(exchange.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
    KeyedRequest refusal = exchange.repeats().get(0);
    assertThat(refusal.body()).contains("\"action\":[\"1\"]");
    assertThat(refusal.providerObjectId()).isNull();
    assertThat(refusal.stateChanged()).isFalse();
  }

  @Test
  void two_orders_the_duplicate_check_compares_are_one_row_and_never_two_first_requests() {
    accounts.with(AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization(ORDER))));

    gateway.cgiLink(call(signed(authorization(ANOTHER_ORDER_ENDING_THE_SAME))));

    assertThat(exchanges())
        .extracting(KeyedExchange::keyName, KeyedExchange::keyValue)
        .containsExactly(tuple(AzericardKeyMechanisms.ORDER, TRACE));
  }

  @Test
  void a_repeat_of_an_order_replays_nothing_and_the_row_carries_no_replay_timestamp() {
    accounts.with(AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization(ORDER))));

    gateway.cgiLink(call(signed(authorization(ANOTHER_ORDER_ENDING_THE_SAME))));

    KeyedExchange exchange = theExchangeUnder(AzericardKeyMechanisms.ORDER, TRACE);
    assertThat(exchange.replayTimestamps()).isEmpty();
    assertThat(exchange.repeats().get(0).body()).isNotEqualTo(exchange.firstRequest().body());
  }

  @Test
  void completing_and_then_reversing_one_payment_reads_as_a_correlated_retry_under_the_rrn() {
    AzericardGateway gateway = aGatewayHoldingAnAuthorization();

    gateway.cgiLink(call(signed(settlement("21", HELD))));
    gateway.cgiLink(call(signed(settlement("22", HELD))));

    KeyedExchange exchange = theExchangeUnder(AzericardKeyMechanisms.RRN, RRN);
    assertThat(exchange.role()).isEqualTo(KeyRole.CORRELATION_KEY);
    assertThat(exchange.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.CORRELATED_RETRY);
    assertThat(exchange.replayTimestamps()).isEmpty();
    assertThat(exchange.firstRequest().providerObjectId()).isEqualTo(INT_REF);
  }

  @Test
  void completing_and_then_reversing_one_payment_reads_as_a_correlated_retry_under_the_int_ref() {
    AzericardGateway gateway = aGatewayHoldingAnAuthorization();

    gateway.cgiLink(call(signed(settlement("21", HELD))));
    gateway.cgiLink(call(signed(settlement("22", HELD))));

    KeyedExchange exchange = theExchangeUnder(AzericardKeyMechanisms.INT_REF, INT_REF);
    assertThat(exchange.role()).isEqualTo(KeyRole.CORRELATION_KEY);
    assertThat(exchange.requests())
        .extracting(KeyedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.CORRELATED_RETRY);
    assertThat(exchange.operation()).isEqualTo("TRTYPE=21");
  }

  @Test
  void a_completion_this_payment_refuses_is_recorded_as_having_changed_nothing() {
    AzericardGateway gateway = aGatewayHoldingAnAuthorization();

    gateway.cgiLink(call(signed(settlement("21", "99.99"))));

    KeyedExchange exchange = theExchangeUnder(AzericardKeyMechanisms.INT_REF, INT_REF);
    assertThat(exchange.firstRequest().stateChanged()).isFalse();
    assertThat(exchange.firstRequest().body()).contains("\"action\":[\"3\"]");
  }

  @Test
  void a_completion_a_fault_answered_instead_of_running_is_recorded_as_answering_nothing() {
    AzericardGateway gateway = aGatewayHoldingAnAuthorization();
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=21"),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    AzericardApiResponse response = gateway.cgiLink(call(signed(settlement("21", HELD))));

    assertThat(response.httpStatus()).isEqualTo(502);
    KeyedRequest recorded =
        theExchangeUnder(AzericardKeyMechanisms.INT_REF, INT_REF).firstRequest();
    assertThat(recorded.carriesAResponse()).isFalse();
    assertThat(recorded.httpStatus()).isNull();
    assertThat(recorded.contentType()).isNull();
    assertThat(recorded.body()).isNull();
    assertThat(recorded.stateChanged()).isFalse();
  }

  @Test
  void a_settlement_that_addresses_no_payment_of_this_run_is_recorded_under_no_key_at_all() {
    accounts.with(AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID));

    gateway().cgiLink(call(signed(settlement("21", HELD))));

    assertThat(exchanges()).isEmpty();
  }

  @Test
  void a_status_inquiry_is_recorded_under_no_key_because_it_carries_none_of_the_three() {
    AzericardGateway gateway = aGatewayHoldingAnAuthorization();

    gateway.cgiLink(call(signed(statusInquiry())));

    assertThat(exchanges())
        .extracting(KeyedExchange::keyName)
        .containsExactly(AzericardKeyMechanisms.ORDER);
  }

  @Test
  void clearing_the_test_run_leaves_the_inspector_with_no_exchange_of_it_to_show() {
    accounts.with(AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID));
    gateway().cgiLink(call(signed(authorization(ORDER))));

    payments.clear(E2E_RUN);

    assertThat(exchanges()).isEmpty();
  }

  private AzericardGateway aGatewayHoldingAnAuthorization() {
    accounts.with(AzericardDoubles.terminal(E2E_RUN, MERCHANT, TERMINAL_ID));
    AzericardGateway gateway = gateway();
    gateway.cgiLink(call(signed(authorization(ORDER))));
    return gateway;
  }

  private KeyedExchange theExchangeUnder(String keyName, String keyValue) {
    return exchanges().stream()
        .filter(found -> found.keyName().equals(keyName) && found.keyValue().equals(keyValue))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no exchange under " + keyName + " " + keyValue));
  }

  private List<KeyedExchange> exchanges() {
    return payments
        .exchanges(
            new KeyedExchangeQuery(
                E2E_RUN, KeyedExchangeQuery.FROM_THE_NEWEST, KeyedExchangeQuery.DEFAULT_LIMIT))
        .exchanges();
  }

  private AzericardGateway gateway() {
    accounts.all().forEach(terminal -> testRuns.register(terminal.testRunId()));
    AzericardDoubles.CountingIds ids = new AzericardDoubles.CountingIds();
    return new AzericardGateway(
        accounts,
        testRuns,
        audit,
        AzericardDoubles.clockAt(NOW),
        ids,
        DEFAULT_RUN,
        new Authorize(payments, cards, ids, scenarioEngine),
        new CompleteOrReverse(payments, scenarioEngine),
        new LookUpStatus(payments, scenarioEngine),
        AzericardDoubles.checkoutOf(
            payments, cards, accounts, scenarioEngine, audit, AzericardDoubles.clockAt(NOW), ids));
  }

  private static Map<String, String> authorization(String order) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "0");
    form.put(AzericardFields.ORDER, order);
    form.put(AzericardFields.AMOUNT, HELD);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, NOW_ON_THE_WIRE);
    form.put(AzericardFields.NONCE, NONCE);
    form.put(AzericardFields.MERCH_URL, "https://merchant.example");
    form.put(AzericardFields.DESC, "IT Books. Qty: 2");
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> settlement(String trtype, String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, trtype);
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, amount);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.RRN, RRN);
    form.put(AzericardFields.INT_REF, INT_REF);
    form.put(AzericardFields.TIMESTAMP, NOW_ON_THE_WIRE);
    form.put(AzericardFields.NONCE, NONCE);
    return form;
  }

  private static Map<String, String> statusInquiry() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "90");
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.TRAN_TRTYPE, "0");
    form.put(AzericardFields.TIMESTAMP, NOW_ON_THE_WIRE);
    form.put(AzericardFields.NONCE, NONCE);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMessage.of(AzericardRoute.CGI_LINK, fieldsOf(signed)).macSource()));
    return signed;
  }

  private static Map<String, List<String>> fieldsOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private static AzericardApiCall call(Map<String, String> form) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Content-Type", List.of("application/x-www-form-urlencoded"));
    return new AzericardApiCall("POST", ROUTE, headers, fieldsOf(form));
  }

  @Test
  void provider_azericard_declares_every_key_it_has_as_one_no_request_can_ever_find_held() {
    assertThat(AzericardKeyMechanisms.MECHANISMS.claimOf(AzericardKeyMechanisms.ORDER).canBeHeld())
        .isFalse();
    assertThat(AzericardKeyMechanisms.MECHANISMS.claimOf(AzericardKeyMechanisms.RRN).canBeHeld())
        .isFalse();
    assertThat(
            AzericardKeyMechanisms.MECHANISMS.claimOf(AzericardKeyMechanisms.INT_REF).canBeHeld())
        .isFalse();
  }
}
