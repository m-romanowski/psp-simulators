package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AzericardCardStorageTest {

  private static final TestRunId DEFAULT_RUN = TestRunId.of("default");
  private static final TestRunId E2E_RUN = TestRunId.of("azericard-token");

  private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

  private static final String TERMINAL_ID = "77777777";
  private static final String MERCHANT = "merchant-1";
  private static final String ORDER = "20220628084800";
  private static final String ANOTHER_ORDER = "20220628084801";
  private static final String NONCE = "F2B2DD7E603A7ADA";
  private static final String TOKEN_ROUTE = "/token/cgi_link";
  private static final String CGI_ROUTE = "/cgi-bin/cgi_link";
  private static final String MINIMAL = "0.01";

  private static final String FIRST_TOKEN = "0000000000000000000000000001";

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarioEngine = new ScenarioEngine(scenarioRules);
  private final AzericardDoubles.Accounts accounts = new AzericardDoubles.Accounts();
  private final AzericardDoubles.Runs testRuns = new AzericardDoubles.Runs(DEFAULT_RUN);
  private final AzericardDoubles.Audit audit = new AzericardDoubles.Audit();
  private final AzericardDoubles.Payments payments = new AzericardDoubles.Payments();
  private final AzericardDoubles.StoredCards cards = new AzericardDoubles.StoredCards();

  @Test
  void stores_a_card_by_taking_the_minimal_payment_the_registration_is_made_of() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response = gateway().tokenLink(tokenCall(signed(cardStorage("1"))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(bodyOf(response)).contains("<action>0</action>").contains("<rc>00</rc>");
    assertThat(cards.all())
        .singleElement()
        .satisfies(
            card -> {
              assertThat(card.token()).isEqualTo(FIRST_TOKEN);
              assertThat(card.terminalId()).isEqualTo(TERMINAL_ID);
              assertThat(card.merchantId()).isEqualTo(MERCHANT);
              assertThat(card.order()).isEqualTo(ORDER);
              assertThat(card.storedByIntRef()).isEqualTo(onlyPayment().intRef());
            });
  }

  @Test
  void names_the_stored_card_as_the_object_the_registration_payment_brought_into_existence() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    gateway().tokenLink(tokenCall(signed(cardStorage("1"))));

    assertThat(onlyPayment().ledger().operations())
        .singleElement()
        .satisfies(
            operation -> {
              assertThat(operation.kind()).isEqualTo(OperationKind.CAPTURE);
              assertThat(operation.createdObjectId()).isEqualTo(FIRST_TOKEN);
              assertThat(operation.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
            });
    assertThat(onlyPayment().token()).isEqualTo(FIRST_TOKEN);
  }

  @Test
  void answers_a_card_storage_with_the_ordinary_gateway_xml_and_puts_no_token_in_it() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response = gateway().tokenLink(tokenCall(signed(cardStorage("1"))));

    assertThat(response.contentType()).isEqualTo("text/html");
    assertThat(bodyOf(response)).startsWith("<response>").doesNotContain(FIRST_TOKEN);
  }

  @Test
  void pays_with_a_card_this_terminal_stored_and_records_the_token_the_payment_was_made_on() {
    String token = aStoredCard();

    AzericardApiResponse response =
        gateway().tokenLink(tokenCall(signed(storedCardPayment(token, ANOTHER_ORDER))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(payments.all()).hasSize(2);
    assertThat(payments.all().get(1).token()).isEqualTo(token);
    assertThat(payments.all().get(1).ledger().operations())
        .singleElement()
        .satisfies(operation -> assertThat(operation.createdObjectId()).isNull());
    assertThat(cards.all()).hasSize(1);
  }

  @Test
  void refuses_a_payment_on_a_token_no_card_of_this_run_was_ever_stored_under() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response =
        gateway().tokenLink(tokenCall(signed(storedCardPayment(FIRST_TOKEN, ORDER))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(bodyOf(response))
        .contains("<action>3</action>")
        .contains("<rc>" + AzericardOutcomes.RC_NO_SUCH_TOKEN + "</rc>");
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void refuses_a_payment_on_a_card_another_terminal_of_the_same_run_stored() {
    String token = aStoredCard();
    String otherTerminal = "70000002";
    accounts.with(azericardAccount(E2E_RUN, "merchant-2", otherTerminal));

    Map<String, String> form = storedCardPayment(token, ANOTHER_ORDER);
    form.put(AzericardFields.TERMINAL, otherTerminal);

    AzericardApiResponse response = gateway().tokenLink(tokenCall(signed(form)));

    assertThat(bodyOf(response)).contains("<rc>" + AzericardOutcomes.RC_NO_SUCH_TOKEN + "</rc>");
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void pays_with_a_card_another_configured_source_terminal_stored() {
    String token = aStoredCard();
    String paymentTerminal = "70000002";
    accounts.with(
        azericardAccount(
            E2E_RUN,
            "merchant-2",
            paymentTerminal,
            Map.of(AzericardTerminal.STORED_CARD_SOURCE_TERMINALS_PROPERTY, TERMINAL_ID)));

    Map<String, String> form = storedCardPayment(token, ANOTHER_ORDER);
    form.put(AzericardFields.TERMINAL, paymentTerminal);

    AzericardApiResponse response = gateway().tokenLink(tokenCall(signed(form)));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(payments.all()).hasSize(2);
    assertThat(payments.all().get(1).token()).isEqualTo(token);
  }

  @Test
  void stores_no_card_for_a_registration_the_issuer_declined() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    scenarioRules.add(
        new NewScenarioRule(
            AzericardGateway.AZERICARD,
            E2E_RUN,
            0,
            ScenarioMatch.anyRequest().naming("TRTYPE=1 TOKEN_ACTION=REGISTER"),
            new ScenarioAction.TerminalFailure("05"),
            null));

    AzericardApiResponse response = gateway().tokenLink(tokenCall(signed(cardStorage("1"))));

    assertThat(bodyOf(response)).contains("<action>2</action>").contains("<rc>05</rc>");
    assertThat(cards.all()).isEmpty();
    assertThat(onlyPayment().token()).isNull();
  }

  @Test
  void draws_another_token_when_the_one_it_drew_is_already_claimed_in_this_run() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    cards.withTheTokensAlreadyClaimed(FIRST_TOKEN);

    gateway().tokenLink(tokenCall(signed(cardStorage("1"))));

    assertThat(cards.all())
        .singleElement()
        .satisfies(card -> assertThat(card.token()).isEqualTo("0000000000000000000000000002"));
  }

  @Test
  void gives_up_rather_than_storing_a_card_under_a_token_this_run_already_holds() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    cards.withTheTokensAlreadyClaimed(theFirstDrawnTokens(Authorize.ID_ATTEMPTS));
    AzericardGateway gateway = gateway();
    AzericardApiCall storing = tokenCall(signed(cardStorage("1")));

    assertThatThrownBy(() -> gateway.tokenLink(storing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TOKEN");
    assertThat(cards.all()).isEmpty();
  }

  private static String[] theFirstDrawnTokens(int count) {
    String[] drawn = new String[count];
    for (int index = 0; index < count; index++) {
      drawn[index] = "0".repeat(AzericardStoredCard.TOKEN_LENGTH - 1) + (index + 1);
    }
    return drawn;
  }

  @ParameterizedTest(name = "refuses MERCH_TRAN_STATE {0}, an initiation this simulator cannot run")
  @ValueSource(strings = {"C", "S"})
  void refuses_an_authorization_initiation_the_token_route_of_this_simulator_does_not_run(
      String initiation) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = storedCardPayment(FIRST_TOKEN, ORDER);
    form.put(AzericardFields.MERCH_TRAN_STATE, initiation);

    AzericardApiResponse response = gateway().tokenLink(tokenCall(signed(form)));

    assertThat(response.contentType()).isEqualTo("text/html");
    assertThat(bodyOf(response)).contains("Error in authorization initiation indicator field");
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void refuses_a_registration_that_does_not_declare_the_card_storage_initiation() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = cardStorage("1");
    form.remove(AzericardFields.MERCH_TRAN_STATE);

    assertThat(bodyOf(gateway().tokenLink(tokenCall(signed(form)))))
        .contains("Error in authorization initiation indicator field");
  }

  @Test
  void refuses_a_token_action_other_than_the_one_value_the_vendor_publishes() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = cardStorage("1");
    form.put(AzericardFields.TOKEN_ACTION, "DELETE");

    assertThat(bodyOf(gateway().tokenLink(tokenCall(signed(form)))))
        .contains("Error in token action field");
  }

  @Test
  void refuses_a_token_route_request_that_neither_registers_a_card_nor_names_one() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = storedCardPayment(FIRST_TOKEN, ORDER);
    form.remove(AzericardFields.TOKEN);

    assertThat(bodyOf(gateway().tokenLink(tokenCall(signed(form)))))
        .contains("Error in saved card token field");
  }

  @ParameterizedTest(name = "refuses TRTYPE {0} on the token route, which answers no such call")
  @ValueSource(strings = {"21", "22", "24", "90"})
  void refuses_a_transaction_type_the_token_route_does_not_answer(String trtype) {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = cardStorage("1");
    form.put(AzericardFields.TRTYPE, trtype);
    form.put(AzericardFields.P_SIGN, "00");

    assertThat(bodyOf(gateway().tokenLink(tokenCall(form))))
        .contains("Error in transaction type field");
  }

  @Test
  void signs_a_token_route_request_in_the_authorization_order_with_no_token_field_in_the_mac() {
    Map<String, String> plain = authorization("1");
    Map<String, String> storing = cardStorage("1");
    storing.put(AzericardFields.TOKEN, FIRST_TOKEN);

    assertThat(new String(macSourceOf(AzericardRoute.TOKEN_CGI_LINK, storing), UTF_8))
        .isEqualTo(new String(macSourceOf(AzericardRoute.CGI_LINK, plain), UTF_8));
  }

  @Test
  void stores_no_card_for_a_registration_posted_to_the_route_that_does_not_carry_tokens() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    AzericardApiResponse response = gateway().cgiLink(call(signed(cardStorage("1"))));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(cards.all()).isEmpty();
    assertThat(onlyPayment().token()).isNull();
  }

  @Test
  void holds_the_funds_of_a_registration_sent_as_a_pre_authorization() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));

    gateway().tokenLink(tokenCall(signed(cardStorage("0"))));

    assertThat(onlyPayment().normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(cards.all()).hasSize(1);
  }

  @Test
  void refuses_a_card_saved_for_merchant_initiated_payments_that_carries_no_recurring_nonce() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = cardStorage("1");
    form.put(AzericardFields.MIT_AGREEMENT, "N");

    assertThat(bodyOf(gateway().tokenLink(tokenCall(signed(form)))))
        .contains("Error in merchant recurring nonce field");
    assertThat(cards.all()).isEmpty();
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void refuses_a_payment_agreement_indicator_other_than_the_two_the_vendor_publishes() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = cardStorage("1");
    form.put(AzericardFields.MIT_AGREEMENT, "X");
    form.put(AzericardFields.MERCH_RN_ID, "0123456789ABCDEF");

    assertThat(bodyOf(gateway().tokenLink(tokenCall(signed(form)))))
        .contains("Error in payment agreement indicator field");
    assertThat(cards.all()).isEmpty();
  }

  @Test
  void refuses_a_merchant_initiated_payment_sent_as_anything_but_an_authorization() {
    String token = aStoredCard();
    Map<String, String> form = storedCardPayment(token, ANOTHER_ORDER);
    form.put(AzericardFields.TRTYPE, "0");
    form.put(AzericardFields.MERCH_TRAN_STATE, AzericardTokenFlow.MERCHANT_INITIATED);
    form.put(AzericardFields.MERCH_RN_ID, "0123456789ABCDEF");

    assertThat(bodyOf(gateway().tokenLink(tokenCall(signed(form)))))
        .contains("Error in transaction type field");
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void refuses_a_card_whose_cvc2_reason_code_is_none_the_vendor_publishes() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> form = cardStorage("1");
    form.put(AzericardFields.CVC2_RC, "7");

    assertThat(bodyOf(gateway().tokenLink(tokenCall(signed(form)))))
        .contains("Error in CVC2 reason code field");
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void
      pays_a_customer_initiated_payment_on_a_card_saved_for_merchant_initiated_ones_without_its_ext_net_ref() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    Map<String, String> save = cardStorage("1");
    save.put(AzericardFields.MIT_AGREEMENT, "N");
    save.put(AzericardFields.MERCH_RN_ID, "0123456789ABCDEF");
    gateway().tokenLink(tokenCall(signed(save)));
    assertThat(cards.all())
        .singleElement()
        .satisfies(card -> assertThat(card.extNetRef()).isNotNull());

    AzericardApiResponse paid =
        gateway().tokenLink(tokenCall(signed(storedCardPayment(FIRST_TOKEN, ANOTHER_ORDER))));

    assertThat(paid.outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(bodyOf(paid)).contains("<action>0</action>");
  }

  @Test
  void keeps_no_card_digits_for_a_payment_made_on_a_stored_card_since_it_carried_no_card() {
    String token = aStoredCard();

    gateway().tokenLink(tokenCall(signed(storedCardPayment(token, ANOTHER_ORDER))));

    assertThat(payments.all())
        .extracting(AzericardPayment::cardLast4)
        .containsExactly("2601", null);
  }

  private String aStoredCard() {
    accounts.with(azericardAccount(E2E_RUN, MERCHANT, TERMINAL_ID));
    gateway().tokenLink(tokenCall(signed(cardStorage("1"))));
    assertThat(cards.all()).hasSize(1);
    return cards.all().get(0).token();
  }

  private AzericardPayment onlyPayment() {
    assertThat(payments.all()).hasSize(1);
    return payments.all().get(0);
  }

  private static Map<String, String> authorization(String trtype) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.TERMINAL, TERMINAL_ID);
    form.put(AzericardFields.TRTYPE, trtype);
    form.put(AzericardFields.ORDER, ORDER);
    form.put(AzericardFields.AMOUNT, MINIMAL);
    form.put(AzericardFields.CURRENCY, "AZN");
    form.put(AzericardFields.TIMESTAMP, AzericardTimestamp.write(NOW));
    form.put(AzericardFields.NONCE, NONCE);
    form.putAll(AzericardKeyFixture.THE_PUBLISHED_TEST_CARD_SENT_S2S);
    return form;
  }

  private static Map<String, String> cardStorage(String trtype) {
    Map<String, String> form = authorization(trtype);
    form.put(AzericardFields.TOKEN_ACTION, AzericardTokenFlow.REGISTER);
    form.put(AzericardFields.MERCH_TRAN_STATE, AzericardTokenFlow.STORING_A_CARD);
    return form;
  }

  private static Map<String, String> storedCardPayment(String token, String order) {
    Map<String, String> form = authorization("1");
    form.put(AzericardFields.ORDER, order);
    form.put(AzericardFields.TOKEN, token);
    form.keySet().removeAll(AzericardFields.CARD_DATA);
    return form;
  }

  private static Map<String, String> signed(Map<String, String> form) {
    Map<String, String> signed = new LinkedHashMap<>(form);
    signed.put(
        AzericardFields.P_SIGN,
        AzericardKeyFixture.signedByTheMerchant(
            macSourceOf(AzericardRoute.TOKEN_CGI_LINK, signed)));
    return signed;
  }

  private static byte[] macSourceOf(AzericardRoute route, Map<String, String> form) {
    return AzericardMessage.of(route, fieldsOf(form)).macSource();
  }

  private static AzericardApiCall tokenCall(Map<String, String> form) {
    return new AzericardApiCall("POST", TOKEN_ROUTE, Map.of(), fieldsOf(form));
  }

  private static AzericardApiCall call(Map<String, String> form) {
    return new AzericardApiCall("POST", CGI_ROUTE, Map.of(), fieldsOf(form));
  }

  private static Map<String, List<String>> fieldsOf(Map<String, String> form) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    form.forEach((name, value) -> fields.put(name, List.of(value)));
    return fields;
  }

  private static String bodyOf(AzericardApiResponse response) {
    return new String(response.body(), UTF_8);
  }

  private static AzericardTerminal azericardAccount(
      TestRunId testRunId, String accountId, String terminalId) {
    return AzericardDoubles.terminal(testRunId, accountId, terminalId);
  }

  private static AzericardTerminal azericardAccount(
      TestRunId testRunId, String accountId, String terminalId, Map<String, String> configuration) {
    return AzericardDoubles.terminal(testRunId, accountId, terminalId, configuration);
  }

  private AzericardGateway gateway() {
    return gatewayAt(NOW, new AzericardDoubles.CountingIds());
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
