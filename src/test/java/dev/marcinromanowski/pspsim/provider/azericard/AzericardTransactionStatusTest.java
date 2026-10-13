package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
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

class AzericardTransactionStatusTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("azericard-status");

  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
  private static final String NOW_ON_THE_WIRE = "20260910120000";

  private static final String TERMINAL_ID = "77777777";
  private static final String MERCHANT = "merchant-1";
  private static final String ORDER = "20220628084800";
  private static final String NONCE = "F2B2DD7E603A7ADA";
  private static final String ROUTE = "/cgi-bin/cgi_link";
  private static final String AMOUNT = "11.48";

  private static final String INT_REF = "0000000000000001";
  private static final String RRN = "100000000001";
  private static final String APPROVAL = "100001";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Runs testRuns = new AzericardDoubles.Runs(DEFAULT_RUN);
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();

  @Test
  void builds_the_status_mac_from_the_five_fields_the_vendor_publishes_in_their_order() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.ORDER, "20211112075614");
    form.put(AzericardFields.TERMINAL, "17202191");
    form.put(AzericardFields.TRTYPE, "90");
    form.put(AzericardFields.TIMESTAMP, "20211112075714");
    form.put(AzericardFields.NONCE, "7cfb4c2512eeec72");
    form.put(AzericardFields.TRAN_TRTYPE, "1");

    assertThat(
            new String(
                AzericardMessage.of(AzericardRoute.CGI_LINK, fieldsOf(form)).macSource(), UTF_8))
        .isEqualTo("14202111120756148172021912901420211112075714167cfb4c2512eeec72");
  }

  @Test
  void reports_the_payment_the_merchant_addresses_by_its_terminal_and_its_order() {
    anApprovedHold();

    AzericardApiResponse response = gatewayAt(NOW).cgiLink(call(signed(statusInquiry())));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(response.contentType()).isEqualTo("application/json; charset=UTF-8");
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(response.providerObjectId()).isEqualTo(INT_REF);
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"terminal\":\"77777777\","
                + "\"actionCode\":\"0\","
                + "\"responseCode\":\"00\","
                + "\"statusMsg\":\"Approved\","
                + "\"amount\":\"11.48\","
                + "\"currency\":\"AZN\","
                + "\"transactionDate\":\""
                + NOW_ON_THE_WIRE
                + "\","
                + "\"order\":\""
                + ORDER
                + "\","
                + "\"approval\":\""
                + APPROVAL
                + "\","
                + "\"rrn\":\""
                + RRN
                + "\","
                + "\"intRef\":\""
                + INT_REF
                + "\","
                + "\"tr_type\":\"0\","
                + "\"nonce\":\""
                + NONCE
                + "\","
                + "\"signature\":\""
                + theExpectedSignatureOver(AMOUNT, TERMINAL_ID, APPROVAL, RRN, INT_REF)
                + "\","
                + "\"timestamp\":\""
                + NOW_ON_THE_WIRE
                + "\"}");
  }

  @Test
  void reports_a_declined_payment_with_the_action_and_response_code_the_gateway_answered_it_with() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=1"),
            new ScenarioAction.TerminalFailure("05"),
            null));
    gatewayAt(NOW).cgiLink(call(signed(authorization("1"))));

    assertThat(bodyOf(gatewayAt(NOW).cgiLink(call(signed(statusInquiry())))))
        .contains("\"actionCode\":\"2\"")
        .contains("\"responseCode\":\"05\"")
        .contains("\"statusMsg\":\"\"")
        .contains("\"approval\":\"\"");
  }

  @Test
  void answers_an_inquiry_about_an_order_it_does_not_hold_as_the_sandbox_answered_one() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response = gatewayAt(NOW).cgiLink(call(signed(statusInquiry())));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .contains("\"actionCode\":\"3\"")
        .contains("\"responseCode\":\"-24\"")
        .contains("\"statusMsg\":\"Transaction context mismatch\"")
        .doesNotContain("\"order\"");
  }

  @Test
  void refuses_an_inquiry_made_after_the_published_twenty_four_hour_window_has_passed() {
    anApprovedHold();
    Instant tooLate = NOW.plus(AzericardTimestamp.STATUS_WINDOW).plusSeconds(1);

    AzericardApiResponse response =
        gatewayAt(tooLate).cgiLink(call(signed(statusInquiryAt(tooLate))));

    assertThat(bodyOf(response))
        .contains("\"responseCode\":\"" + AzericardOutcomes.RC_OUTSIDE_THE_STATUS_WINDOW + "\"")
        .contains("\"statusMsg\":\"\"");
  }

  @Test
  void answers_an_inquiry_made_on_the_last_second_of_the_published_window() {
    anApprovedHold();
    Instant lastMoment = NOW.plus(AzericardTimestamp.STATUS_WINDOW);

    AzericardApiResponse response =
        gatewayAt(lastMoment).cgiLink(call(signed(statusInquiryAt(lastMoment))));

    assertThat(bodyOf(response)).contains("\"intRef\":\"" + INT_REF + "\"");
  }

  @Test
  void refuses_to_choose_between_two_payments_one_order_names_inside_the_published_window() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Instant lateOnTheFirstDay = Instant.parse("2026-09-10T23:30:00Z");
    Instant earlyOnTheSecond = Instant.parse("2026-09-11T00:30:00Z");
    gatewayAt(lateOnTheFirstDay).cgiLink(call(signed(authorizationAt(lateOnTheFirstDay, "0"))));
    gatewayAt(earlyOnTheSecond).cgiLink(call(signed(authorizationAt(earlyOnTheSecond, "0"))));
    assertThat(payments.all()).hasSize(2);

    Instant asking = Instant.parse("2026-09-11T01:00:00Z");
    AzericardApiResponse response =
        gatewayAt(asking).cgiLink(call(signed(statusInquiryAt(asking))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(response.providerObjectId()).isNull();
    assertThat(bodyOf(response))
        .contains("\"responseCode\":\"" + AzericardOutcomes.RC_MORE_THAN_ONE_TRANSACTION + "\"")
        .contains("\"statusMsg\":\"\"");
  }

  @Test
  void answers_a_refused_signature_in_the_json_shape_the_sandbox_answers_it_in() {
    anApprovedHold();
    Map<String, String> form = statusInquiry();
    form.put(AzericardFields.P_SIGN, "00");

    AzericardApiResponse response = gatewayAt(NOW).cgiLink(call(form));

    assertThat(response.contentType()).isEqualTo("application/json; charset=UTF-8");
    assertThat(bodyOf(response))
        .isEqualTo(
            "{\"terminal\":\"77777777\","
                + "\"actionCode\":\"3\","
                + "\"responseCode\":\"-17\","
                + "\"statusMsg\":\"Access denied\","
                + "\"tr_type\":\"0\","
                + "\"nonce\":\""
                + NONCE
                + "\","
                + "\"signature\":\""
                + theExpectedSignatureOver(null, TERMINAL_ID, null, null, null)
                + "\","
                + "\"timestamp\":\""
                + NOW_ON_THE_WIRE
                + "\"}");
  }

  @Test
  void answers_a_status_inquiry_the_merchant_signed_in_the_authorization_field_order_as_refused() {
    anApprovedHold();
    Map<String, String> form = statusInquiry();
    form.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            AzericardMac.sourceOf(
                form.get(AzericardFields.AMOUNT),
                form.get(AzericardFields.CURRENCY),
                form.get(AzericardFields.TERMINAL),
                form.get(AzericardFields.TRTYPE),
                form.get(AzericardFields.TIMESTAMP),
                form.get(AzericardFields.NONCE),
                null)));

    assertThat(bodyOf(gatewayAt(NOW).cgiLink(call(form)))).contains("\"responseCode\":\"-17\"");
  }

  @Test
  void refuses_an_inquiry_that_names_no_original_transaction_type_on_the_human_error_page() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = signed(statusInquiry());
    form.remove(AzericardFields.TRAN_TRTYPE);

    AzericardApiResponse response = gatewayAt(NOW).cgiLink(call(form));

    assertThat(response.contentType()).isEqualTo("text/html");
    assertThat(bodyOf(response)).contains("Error in original transaction type field");
  }

  @Test
  void answers_an_inquiry_that_posts_neither_a_retrieval_reference_nor_a_gateway_reference() {
    anApprovedHold();

    assertThat(statusInquiry()).doesNotContainKeys(AzericardFields.RRN, AzericardFields.INT_REF);
    assertThat(bodyOf(gatewayAt(NOW).cgiLink(call(signed(statusInquiry())))))
        .contains("\"rrn\":\"" + RRN + "\"")
        .contains("\"intRef\":\"" + INT_REF + "\"");
  }

  @Test
  void escapes_a_merchant_nonce_the_answer_echoes_so_the_body_stays_readable_as_json() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = statusInquiry();
    form.put(AzericardFields.NONCE, "a\"b\\c\td");
    form.put(AzericardFields.P_SIGN, "00");

    assertThat(bodyOf(gatewayAt(NOW).cgiLink(call(form))))
        .contains("\"nonce\":\"a\\\"b\\\\c\\u0009d\"");
  }

  @Test
  void lets_a_rule_decline_the_lookup_itself_without_touching_the_payment_it_asks_about() {
    anApprovedHold();
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=90"),
            new ScenarioAction.TerminalFailure("96"),
            null));

    AzericardApiResponse response = gatewayAt(NOW).cgiLink(call(signed(statusInquiry())));

    assertThat(bodyOf(response))
        .contains("\"actionCode\":\"2\"")
        .contains("\"responseCode\":\"96\"");
    assertThat(payments.all())
        .singleElement()
        .satisfies(p -> assertThat(p.action()).isEqualTo("0"));
  }

  @Test
  void a_status_inquiry_moves_no_money_and_says_so_rather_than_answering_an_operation_kind() {
    assertThatThrownBy(AzericardTransactionType.TRANSACTION_STATUS::movement)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(AzericardTransactionType.MOVES_NOTHING);
  }

  private void anApprovedHold() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    gatewayAt(NOW).cgiLink(call(signed(authorization("0"))));
    assertThat(payments.all()).hasSize(1);
  }

  private static Map<String, String> authorization(String trtype) {
    return authorizationAt(NOW, trtype);
  }

  private static Map<String, String> authorizationAt(Instant at, String trtype) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, trtype);
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, AMOUNT);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(at));
    form.put(AzericardFields.NONCE, NONCE);
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> statusInquiry() {
    return statusInquiryAt(NOW);
  }

  private static Map<String, String> statusInquiryAt(Instant at) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, "90");
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.TRAN_TRTYPE, "0");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(at));
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

  private static AzericardApiCall call(Map<String, String> form) {
    return new AzericardApiCall("POST", ROUTE, Map.of(), fieldsOf(form));
  }

  private static Map<String, List<String>> fieldsOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private static String bodyOf(AzericardApiResponse response) {
    return new String(response.body(), UTF_8);
  }

  private static String theExpectedSignatureOver(String... values) {
    return AzericardKeyFixture.signedWith(
        AzericardMac.sourceOf(values),
        AzericardKeyFixture.privateKeyOf(AzericardKeyFixture.GATEWAY_PRIVATE_PKCS8));
  }

  private static AzericardTerminal azericardAccount(
      TestRunId testRunId, String accountId, String terminalId) {
    return AzericardDoubles.terminal(testRunId, accountId, terminalId);
  }

  private AzericardGateway gatewayAt(Instant now) {
    return gatewayAt(now, new AzericardDoubles.CountingIds());
  }

  private AzericardGateway gatewayAt(Instant now, IdGenerator ids) {
    accounts.all().forEach(terminal -> testRuns.register(terminal.testRunId()));
    return new AzericardGateway(
        accounts,
        testRuns,
        audit,
        AzericardDoubles.clockAt(now),
        ids,
        DEFAULT_RUN,
        new Authorize(payments, cards, ids, scenarioEngine),
        new CompleteOrReverse(payments, scenarioEngine),
        new LookUpStatus(payments, scenarioEngine),
        AzericardDoubles.checkoutOf(
            payments, cards, accounts, scenarioEngine, audit, AzericardDoubles.clockAt(now), ids));
  }
}
