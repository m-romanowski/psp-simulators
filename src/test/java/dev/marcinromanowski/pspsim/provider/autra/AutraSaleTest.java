package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraSaleTest {

  private static final TestRunId RUN = TestRunId.of("autra-sale");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String ORDER = "pedido-abc-123";
  private static final String SLUG_TOKEN = "slug-token-abc";
  private static final String SLUG_STORED_CARD = "slug-stored-card-xyz";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.PixPayments pixPayments = new AutraDoubles.PixPayments();
  private final AutraDoubles.CardSessions cardSessions = new AutraDoubles.CardSessions();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);

  private AutraApi apiFor(Map<String, String> configuration) {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, configuration));
    return AutraWiring.api(
        merchants,
        sessions,
        payments,
        pixPayments,
        cardSessions,
        audit,
        RUN,
        NOW,
        scenarios,
        new AutraDoubles.Runs(RUN));
  }

  @Test
  void an_approved_sale_answers_the_schemas_fields_in_its_order_and_spells_payment_id_up() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response = sell(api, body("150.00", null));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response).members().keySet())
        .containsExactly(
            AutraFields.PAYMENT_ID_UPPERCASE_D,
            AutraFields.STATUS,
            AutraFields.AMOUNT,
            AutraFields.CURRENCY,
            AutraFields.INSTALLMENTS,
            "trackingNumber",
            "nsu",
            AutraFields.RRN,
            AutraFields.AUTHORIZATION_CODE);
    assertThat(bodyOf(response).members()).doesNotContainKey("brandReferenceId");
    assertThat(bodyOf(response).text(AutraFields.STATUS)).contains(AutraStatus.ACCEPTED);
    assertThat(bodyOf(response).number(AutraFields.AMOUNT)).contains("150.00");
  }

  @Test
  void the_sale_spells_the_identifier_with_a_capital_d_where_the_void_spells_it_with_a_small_one() {
    AutraApi api = apiFor(Map.of());

    AutraJson.JsonObject body = bodyOf(sell(api, body("150.00", null)));

    assertThat(body.text(AutraFields.PAYMENT_ID_UPPERCASE_D)).isPresent();
    assertThat(body.text(AutraFields.PAYMENT_ID_LOWERCASE_D)).isEmpty();
  }

  @Test
  void an_approved_sale_is_stored_accepted_and_reads_back_as_succeeded() {
    AutraApi api = apiFor(Map.of());

    sell(api, body("150.00", null));

    AutraPayment stored = payments.all().get(0);
    assertThat(stored.nativeStatus()).isEqualTo(AutraStatus.ACCEPTED);
    assertThat(stored.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void an_approved_sale_records_the_money_it_took_so_a_void_of_it_is_a_refund() {
    AutraApi api = apiFor(Map.of());

    sell(api, body("150.00", null));

    AutraPayment stored = payments.all().get(0);
    assertThat(stored.ledger().holds(OperationKind.CAPTURE)).isTrue();
    assertThat(stored.reversalsAccepted()).isTrue();
    assertThat(stored.authorizationOpen()).isFalse();
  }

  @Test
  void a_zero_dollar_validation_is_accepted_and_records_no_money_because_nothing_was_charged() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response = sell(api, body("0", null));

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraPayment stored = payments.all().get(0);
    assertThat(stored.nativeStatus()).isEqualTo(AutraStatus.ACCEPTED);
    assertThat(stored.ledger().operations()).isEmpty();
    assertThat(stored.reversalsAccepted()).isFalse();
  }

  @Test
  void an_amount_below_zero_is_refused_invalid_amount_although_zero_is_not() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response = sell(api, body("-1.00", null));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void an_amount_that_is_not_a_number_at_all_is_refused_invalid_amount() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace("\"amount\":150.00", "\"amount\":\"lots\""));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void a_sale_on_debit_is_accepted_because_the_sale_is_the_one_endpoint_that_publishes_it() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(
            api,
            body("150.00", null)
                .replace("\"transactionType\":\"CREDIT\"", "\"transactionType\":\"DEBIT\""));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(payments.all().get(0).transactionType()).isEqualTo(AutraFields.DEBIT);
  }

  @Test
  void a_transaction_type_of_neither_published_value_is_refused() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(
            api,
            body("150.00", null)
                .replace("\"transactionType\":\"CREDIT\"", "\"transactionType\":\"VOUCHER\""));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.ONLY_CREDIT_OR_DEBIT_IS_SOLD);
  }

  @Test
  void a_sale_naming_no_transaction_type_at_all_is_refused() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace("\"transactionType\":\"CREDIT\",", ""));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.ONLY_CREDIT_OR_DEBIT_IS_SOLD);
  }

  @Test
  void a_sale_whose_body_is_not_a_json_object_is_refused_invalid_json() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response = sell(api, "not json at all");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_JSON);
  }

  @Test
  void a_sale_naming_no_document_is_refused_missing_document_id() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace("\"documentId\":\"" + DOCUMENT + "\",", ""));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
  }

  @Test
  void a_sale_for_a_document_no_merchant_of_this_run_holds_is_refused_as_not_found() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(
            api,
            body("150.00", null)
                .replace("\"documentId\":\"" + DOCUMENT + "\"", "\"documentId\":\"11111111111\""));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void a_sale_naming_no_session_token_is_refused_missing_transactional_token() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace("\"transactionalToken\":\"", "\"unused\":\""));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MISSING_TRANSACTIONAL_TOKEN);
  }

  @Test
  void a_session_token_naming_no_session_is_refused_the_way_the_pre_authorization_refuses_one() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace(aSession(), "a-token-that-names-no-session"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.BAD_REQUEST);
    assertThat(theMessageIn(response))
        .isEqualTo(ChargeStoredCard.THE_SESSION_TOKEN_NAMES_NO_SESSION);
  }

  @Test
  void a_sale_naming_no_currency_is_refused() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace(",\"currency\":\"BRL\"", ""));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.THE_CURRENCY_IS_REQUIRED);
  }

  @Test
  void a_sale_naming_no_instalment_plan_is_refused_because_the_schema_requires_one() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response = sell(api, body("150.00", null).replace(",\"installments\":1", ""));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.INSTALLMENTS_ARE_ONE_TO_TWELVE);
  }

  @Test
  void a_sale_over_thirteen_instalments_is_refused_because_autra_publishes_two_to_twelve() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace(",\"installments\":1", ",\"installments\":13"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.INSTALLMENTS_ARE_ONE_TO_TWELVE);
  }

  @Test
  void a_sale_over_twelve_instalments_is_the_longest_plan_autra_publishes_and_is_accepted() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace(",\"installments\":1", ",\"installments\":12"));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(payments.all().get(0).installments()).isEqualTo(12);
  }

  @Test
  void a_declined_sale_is_recorded_as_a_request_that_changed_state_because_it_left_a_payment() {
    AutraApi api = apiFor(Map.of());
    aRuleThatDeclinesTheSale(AutraErrors.INSUFFICIENT_FUNDS);

    sell(api, body("150.00", ORDER));

    AutraObservedRequest recorded = payments.observedRequests().get(0);
    assertThat(recorded.stateChanged()).isTrue();
    assertThat(recorded.providerObjectId()).isEqualTo(payments.all().get(0).paymentId());
  }

  @Test
  void a_sale_under_one_instalment_is_refused_for_the_same_published_reason() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace(",\"installments\":1", ",\"installments\":0"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.INSTALLMENTS_ARE_ONE_TO_TWELVE);
  }

  @Test
  void
      a_sale_sending_both_a_card_and_a_token_is_refused_because_autra_publishes_one_or_the_other() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace(",\"tokenData\":", ",\"card\":{},\"tokenData\":"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(PreAuthorize.ONE_INSTRUMENT_AND_ONLY_ONE);
  }

  @Test
  void a_sale_sending_neither_a_card_nor_a_token_is_refused_for_the_same_published_reason() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace(theTokenData(), "\"unused\":{}"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(PreAuthorize.ONE_INSTRUMENT_AND_ONLY_ONE);
  }

  @Test
  void a_token_object_naming_neither_half_of_the_pair_names_no_card_and_is_refused() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, body("150.00", null).replace(theTokenData(), "\"tokenData\":{}"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.THE_TOKEN_DATA_NAMES_NO_CARD);
  }

  @Test
  void a_token_object_naming_the_stored_card_half_alone_is_the_card_the_sale_charges() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(
            api,
            body("150.00", null)
                .replace(
                    theTokenData(),
                    "\"tokenData\":{\"slugStoredCard\":\"" + SLUG_STORED_CARD + "\"}"));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(payments.all().get(0).slugToken()).isNull();
    assertThat(payments.all().get(0).slugStoredCard()).isEqualTo(SLUG_STORED_CARD);
  }

  @Test
  void a_token_object_whose_only_half_is_blank_names_no_card_and_is_refused() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(
            api,
            body("150.00", null).replace(theTokenData(), "\"tokenData\":{\"slugToken\":\"   \"}"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.THE_TOKEN_DATA_NAMES_NO_CARD);
  }

  @Test
  void a_sale_charged_on_a_card_records_both_halves_of_the_pair_under_the_names_autra_gives_them() {
    AutraApi api = apiFor(Map.of());

    sell(api, body("150.00", null));

    AutraPayment stored = payments.all().get(0);
    assertThat(stored.slugToken()).isEqualTo(SLUG_TOKEN);
    assertThat(stored.slugStoredCard()).isEqualTo(SLUG_STORED_CARD);
  }

  @Test
  void a_decline_is_answered_two_hundred_carrying_the_payment_and_the_error_array() {
    AutraApi api = apiFor(Map.of());
    aRuleThatDeclinesTheSale(AutraErrors.INSUFFICIENT_FUNDS);

    AutraApiResponse response = sell(api, body("150.00", null));

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraJson.JsonObject body = bodyOf(response);
    assertThat(body.members().keySet())
        .containsExactly(AutraFields.PAYMENT_ID_UPPERCASE_D, AutraFields.ERRORS);
    assertThat(body.text(AutraFields.STATUS)).isEmpty();
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INSUFFICIENT_FUNDS);
    assertThat(theMessageIn(response))
        .isEqualTo(AutraDeclineMessages.THE_PUBLISHED_INSUFFICIENT_FUNDS_MESSAGE);
  }

  @Test
  void a_decline_answered_two_hundred_is_reported_as_a_failure_and_never_as_a_success() {
    AutraApi api = apiFor(Map.of());
    aRuleThatDeclinesTheSale(AutraErrors.INSUFFICIENT_FUNDS);

    AutraApiResponse response = sell(api, body("150.00", null));

    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void a_decline_answered_two_hundred_names_the_payment_it_declined_on_the_wire() {
    AutraApi api = apiFor(Map.of());
    aRuleThatDeclinesTheSale(AutraErrors.CARD_DECLINED);

    AutraApiResponse response = sell(api, body("150.00", null));

    String named = bodyOf(response).text(AutraFields.PAYMENT_ID_UPPERCASE_D).orElseThrow();
    assertThat(response.providerObjectId()).isEqualTo(named);
    assertThat(payments.all().get(0).paymentId()).isEqualTo(named);
  }

  @Test
  void the_account_that_reads_the_prose_side_answers_the_same_decline_four_twenty_two() {
    AutraApi api = apiFor(Map.of(AutraSaleDecline.SETTING, "UNPROCESSABLE_WITH_ERRORS"));
    aRuleThatDeclinesTheSale(AutraErrors.INSUFFICIENT_FUNDS);

    AutraApiResponse response = sell(api, body("150.00", null));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INSUFFICIENT_FUNDS);
  }

  @Test
  void the_prose_side_carries_the_bare_envelope_so_the_decline_names_no_payment_at_all() {
    AutraApi api = apiFor(Map.of(AutraSaleDecline.SETTING, "UNPROCESSABLE_WITH_ERRORS"));
    aRuleThatDeclinesTheSale(AutraErrors.INSUFFICIENT_FUNDS);

    AutraApiResponse response = sell(api, body("150.00", null));

    assertThat(bodyOf(response).members().keySet()).containsExactly(AutraFields.ERRORS);
    assertThat(response.providerObjectId()).isNull();
  }

  @Test
  void a_decline_is_stored_as_a_payment_either_way_so_a_tester_can_see_the_attempt() {
    AutraApi api = apiFor(Map.of(AutraSaleDecline.SETTING, "UNPROCESSABLE_WITH_ERRORS"));
    aRuleThatDeclinesTheSale(AutraErrors.INSUFFICIENT_FUNDS);

    sell(api, body("150.00", null));

    AutraPayment stored = payments.all().get(0);
    assertThat(stored.nativeStatus()).isEqualTo(AutraStatus.DECLINED);
    assertThat(stored.normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
    assertThat(stored.ledger().operations()).isEmpty();
  }

  @Test
  void
      a_repeat_under_an_order_that_already_holds_an_approved_payment_is_refused_the_published_code() {
    AutraApi api = apiFor(Map.of());
    sell(api, body("150.00", ORDER));

    AutraApiResponse repeat = sell(api, body("150.00", ORDER));

    assertThat(repeat.httpStatus()).isEqualTo(409);
    assertThat(theCodeIn(repeat)).isEqualTo(AutraErrors.ORDER_ALREADY_APPROVED);
    assertThat(theMessageIn(repeat))
        .isEqualTo(ChargeStoredCard.THE_ORDER_ALREADY_HAS_AN_APPROVED_PAYMENT);
  }

  @Test
  void the_published_conflict_body_names_no_payment_at_all_and_neither_does_the_response() {
    AutraApi api = apiFor(Map.of());
    sell(api, body("150.00", ORDER));

    AutraApiResponse repeat = sell(api, body("150.00", ORDER));

    assertThat(bodyOf(repeat).members().keySet()).containsExactly(AutraFields.ERRORS);
    assertThat(repeat.providerObjectId()).isNull();
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void the_original_payment_is_named_only_on_this_simulators_own_record_of_the_refusal() {
    AutraApi api = apiFor(Map.of());
    String original =
        bodyOf(sell(api, body("150.00", ORDER)))
            .text(AutraFields.PAYMENT_ID_UPPERCASE_D)
            .orElseThrow();

    sell(api, body("150.00", ORDER));

    List<AutraObservedRequest> recorded = payments.observedRequests();
    assertThat(recorded).hasSize(2);
    assertThat(recorded.get(1).verdict()).isEqualTo(ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
    assertThat(recorded.get(1).providerObjectId()).isEqualTo(original);
    assertThat(recorded.get(1).stateChanged()).isFalse();
  }

  @Test
  void a_repeat_under_an_order_whose_only_attempt_was_declined_is_admitted_and_charged() {
    AutraApi api = apiFor(Map.of());
    String declining = aRuleThatDeclinesTheSale(AutraErrors.INSUFFICIENT_FUNDS);
    sell(api, body("150.00", ORDER));
    rules.remove(AutraApi.AUTRA, RUN, declining);

    AutraApiResponse repeat = sell(api, body("150.00", ORDER));

    assertThat(repeat.httpStatus()).isEqualTo(200);
    assertThat(payments.all()).hasSize(2);
    assertThat(payments.observedRequests().get(1).verdict())
        .isEqualTo(ExchangeVerdict.REPEAT_ADMITTED);
  }

  @Test
  void a_sale_under_an_order_a_live_reservation_holds_is_refused_without_a_second_debit() {
    AutraApi api = apiFor(Map.of());
    AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, "500.00", ORDER);

    AutraApiResponse response = sell(api, body("150.00", ORDER));

    assertThat(response.httpStatus())
        .as(
            "a reservation answers ACCEPTED, and this route publishes the rule on exactly that"
                + " word: \"Se um pagamento com o mesmo orderId ja estiver ACCEPTED ou CAPTURED,"
                + " retorna 409 sem criar um novo debito\". An app that pre-authorizes and falls"
                + " back to an immediate sale on a retry path is protected in production, so"
                + " charging it here would be a second debit autra never takes")
        .isEqualTo(409);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.ORDER_ALREADY_APPROVED);
    assertThat(payments.all())
        .as("nothing was charged: the reservation is the only payment under this order")
        .hasSize(1);
  }

  @Test
  void a_sale_with_no_order_is_recorded_under_no_key_and_never_refused_as_a_duplicate() {
    AutraApi api = apiFor(Map.of());
    sell(api, body("150.00", null));

    AutraApiResponse second = sell(api, body("150.00", null));

    assertThat(second.httpStatus()).isEqualTo(200);
    assertThat(payments.all()).hasSize(2);
    assertThat(payments.observedRequests()).isEmpty();
  }

  @Test
  void the_first_sale_under_an_order_is_recorded_as_a_first_request_that_changed_state() {
    AutraApi api = apiFor(Map.of());

    sell(api, body("150.00", ORDER));

    AutraObservedRequest recorded = payments.observedRequests().get(0);
    assertThat(recorded.verdict()).isEqualTo(ExchangeVerdict.FIRST_REQUEST);
    assertThat(recorded.operation()).isEqualTo(AutraOperations.SALE);
    assertThat(recorded.stateChanged()).isTrue();
  }

  @Test
  void a_rule_that_replaced_the_sale_with_a_transport_fault_reports_the_rule_that_answered() {
    AutraApi api = apiFor(Map.of());
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.SALE),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    AutraApiResponse response = sell(api, body("150.00", ORDER));

    assertThat(response.httpStatus()).isEqualTo(502);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.UNKNOWN);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void a_payment_identifier_the_store_will_not_take_is_drawn_again_until_one_is_claimed() {
    payments.refusingTheNextPaymentIds(PreAuthorize.PAYMENT_ID_ATTEMPTS - 1);

    assertThat(sell(apiFor(Map.of()), body("150.00", null)).httpStatus()).isEqualTo(200);
  }

  @Test
  void a_payment_identifier_no_attempt_could_claim_is_a_call_this_simulator_cannot_answer() {
    AutraApi api = apiFor(Map.of());
    String body = body("150.00", null);
    payments.refusingTheNextPaymentIds(PreAuthorize.PAYMENT_ID_ATTEMPTS);

    assertThatThrownBy(() -> sell(api, body))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("paymentID");
  }

  @Test
  void a_payment_another_request_stored_under_the_same_identifier_is_a_call_it_cannot_answer() {
    AutraApi api = apiFor(Map.of());
    String body = body("150.00", null);
    payments.refusingTheNextInsert();

    assertThatThrownBy(() -> sell(api, body))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("claimed by another request");
  }

  @Test
  void a_slug_no_card_of_this_run_holds_is_refused_as_a_token_no_longer_available() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(
            api,
            body("150.00", null)
                .replace(theTokenData(), "\"tokenData\":{\"slugToken\":\"slug-nobody-minted\"}"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.TOKEN_NOT_AVAILABLE);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void two_halves_of_two_different_cards_name_no_card_and_are_refused() {
    AutraApi api = apiFor(Map.of());
    cardSessions.holding(aTokenizedSession("slug-of-the-page", "stored-of-the-page"));

    AutraApiResponse response =
        sell(
            api,
            body("150.00", null)
                .replace(
                    theTokenData(),
                    "\"tokenData\":{\"slugToken\":\"slug-of-the-page\",\"slugStoredCard\":\""
                        + SLUG_STORED_CARD
                        + "\"}"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.TOKEN_NOT_AVAILABLE);
  }

  @Test
  void a_slug_a_payer_tokenized_on_the_card_page_is_a_card_the_sale_charges() {
    AutraApi api = apiFor(Map.of());
    cardSessions.holding(aTokenizedSession("slug-of-the-page", "stored-of-the-page"));

    AutraApiResponse response =
        sell(
            api,
            body("150.00", null)
                .replace(theTokenData(), "\"tokenData\":{\"slugToken\":\"slug-of-the-page\"}"));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(payments.all().get(0).slugToken()).isEqualTo("slug-of-the-page");
  }

  @Test
  void a_slug_tokenized_in_another_run_is_no_card_of_this_one() {
    AutraApi api = apiFor(Map.of());
    cardSessions.holding(
        aTokenizedSession("slug-of-the-page", "stored-of-the-page", TestRunId.of("elsewhere")));

    AutraApiResponse response =
        sell(
            api,
            body("150.00", null)
                .replace(theTokenData(), "\"tokenData\":{\"slugToken\":\"slug-of-the-page\"}"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.TOKEN_NOT_AVAILABLE);
  }

  private static AutraCardSession aTokenizedSession(String slugToken, String slugStoredCard) {
    return aTokenizedSession(slugToken, slugStoredCard, RUN);
  }

  private static AutraCardSession aTokenizedSession(
      String slugToken, String slugStoredCard, TestRunId run) {
    return AutraDoubles.aTokenizedSession(run, ACCOUNT, DOCUMENT, slugToken, slugStoredCard, NOW);
  }

  private String aRuleThatDeclinesTheSale(String code) {
    return rules
        .add(
            new NewScenarioRule(
                AutraApi.AUTRA,
                RUN,
                10,
                ScenarioMatch.anyRequest().naming(AutraOperations.SALE),
                new ScenarioAction.TerminalFailure(code),
                null))
        .scenarioId();
  }

  private String aSession() {
    sessions.open(new AutraSession(RUN, "a-sale-session-token", ACCOUNT, DOCUMENT, NOW));
    return "a-sale-session-token";
  }

  private static String theTokenData() {
    return "\"tokenData\":{\"slugToken\":\""
        + SLUG_TOKEN
        + "\",\"slugStoredCard\":\""
        + SLUG_STORED_CARD
        + "\"}";
  }

  private String body(String amount, String orderId) {
    return "{\"transactionalToken\":\""
        + aSession()
        + "\",\"documentId\":\""
        + DOCUMENT
        + "\",\"transactionType\":\"CREDIT\",\"amount\":"
        + amount
        + ",\"currency\":\"BRL\",\"installments\":1"
        + (orderId == null ? "" : ",\"orderId\":\"" + orderId + "\"")
        + ","
        + theTokenData()
        + "}";
  }

  private static AutraApiResponse sell(AutraApi api, String body) {
    return api.sale(AutraWire.call(AutraApiController.PAYMENTS, bearerOf(api), body));
  }

  private static Map<String, List<String>> bearerOf(AutraApi api) {
    return AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT));
  }

  private static String theCodeIn(AutraApiResponse response) {
    return theErrorIn(response, AutraFields.CODE);
  }

  private static String theMessageIn(AutraApiResponse response) {
    return theErrorIn(response, AutraFields.MSG);
  }

  private static String theErrorIn(AutraApiResponse response, String member) {
    return bodyOf(response).members().get(AutraFields.ERRORS).flatten().get(member).get(0);
  }

  private static AutraJson.JsonObject bodyOf(AutraApiResponse response) {
    return AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
  }

  @Test
  void an_approved_sale_names_the_acquirer_transaction_once_under_each_of_the_two_names() {
    AutraApi api = apiFor(Map.of());

    AutraJson.JsonObject body = bodyOf(sell(api, body("150.00", null)));

    assertThat(body.text("nsu")).isEqualTo(body.text("trackingNumber"));
    assertThat(body.text("trackingNumber")).contains(payments.all().get(0).trackingNumber());
  }

  @Test
  void an_approved_sale_whose_acquirer_named_no_transaction_publishes_neither_of_the_two_names() {
    AutraApi api =
        apiFor(Map.of(AutraAcquirerReport.SETTING, AutraAcquirerReport.NOT_REPORTED.name()));

    AutraApiResponse response = sell(api, body("150.00", null));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(bodyOf(response).members().keySet())
        .containsExactly(
            AutraFields.PAYMENT_ID_UPPERCASE_D,
            AutraFields.STATUS,
            AutraFields.AMOUNT,
            AutraFields.CURRENCY,
            AutraFields.INSTALLMENTS,
            AutraFields.RRN,
            AutraFields.AUTHORIZATION_CODE);
    assertThat(payments.all().get(0).trackingNumber()).isNull();
  }

  @Test
  void a_sale_asking_for_a_recurring_series_is_refused_rather_than_charged_without_one() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, aSaleAlsoCarrying("\"recurringData\":{\"isFirstRecurring\":true}"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.BAD_REQUEST);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.A_RECURRING_SERIES_IS_NOT_SERVED);
    assertThat(payments.all()).as("nothing was charged").isEmpty();
  }

  @Test
  void a_series_identifier_on_a_later_sale_is_refused_by_the_same_member_being_present() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response =
        sell(api, aSaleAlsoCarrying("\"recurringData\":{\"brandReferenceId\":\"series-abc-123\"}"));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.A_RECURRING_SERIES_IS_NOT_SERVED);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void a_sale_carrying_no_recurring_member_is_charged_as_it_was_before() {
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response = sell(api, body("150.00", null));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(payments.all()).hasSize(1);
  }

  private String aSaleAlsoCarrying(String member) {
    String written = body("150.00", null);
    return written.substring(0, written.length() - 1) + "," + member + "}";
  }
}
