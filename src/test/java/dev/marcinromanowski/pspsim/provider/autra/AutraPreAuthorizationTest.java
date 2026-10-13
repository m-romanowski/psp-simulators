package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
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

class AutraPreAuthorizationTest {

  private static final TestRunId RUN = TestRunId.of("autra-pre-authorization");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final AutraDoubles.CardSessions cardSessions = new AutraDoubles.CardSessions();

  private AutraApi apiFor(Map<String, String> configuration) {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, configuration));
    return AutraWiring.api(
        merchants,
        sessions,
        payments,
        new AutraDoubles.PixPayments(),
        cardSessions,
        audit,
        RUN,
        NOW,
        scenarios,
        new AutraDoubles.Runs(RUN));
  }

  private String aSession() {
    sessions.open(new AutraSession(RUN, "session-token", ACCOUNT, DOCUMENT, NOW));
    return "session-token";
  }

  private AutraApiResponse preAuthorize(AutraApi api, String body) {
    return api.preAuthorization(
        AutraWire.call(AutraApiController.PRE_AUTHORIZATION, AutraWire.bearer(aBearer(api)), body));
  }

  private String aBearer(AutraApi api) {
    return AutraTokenTest.theTokenIn(
        api.token(
            AutraWire.tokenCall(
                AutraFields.CLIENT_CREDENTIALS,
                AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT))));
  }

  private String schemaBody(String amount, String orderId) {
    return "{\"transactionalToken\":\""
        + aSession()
        + "\",\"documentId\":\""
        + DOCUMENT
        + "\",\"transactionType\":\"CREDIT\",\"currency\":\"BRL\",\"amount\":"
        + amount
        + ",\"installments\":1"
        + (orderId == null ? "" : ",\"orderId\":\"" + orderId + "\"")
        + ",\"tokenData\":{\"slugToken\":\"slug-token-abc\"}}";
  }

  @Test
  void a_reservation_is_answered_the_schemas_own_fields_with_the_identifier_spelled_paymentID() {
    AutraApiResponse response = preAuthorize(apiFor(Map.of()), schemaBody("500.00", null));

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.text(AutraFields.PAYMENT_ID_UPPERCASE_D)).isPresent();
    assertThat(body.text(AutraFields.PAYMENT_ID_LOWERCASE_D)).isEmpty();
    assertThat(body.text(AutraFields.STATUS)).contains("ACCEPTED");
    assertThat(body.number(AutraFields.AMOUNT)).contains("500.00");
    assertThat(body.text(AutraFields.CURRENCY)).contains("BRL");
    assertThat(body.text("trackingNumber")).isPresent();
    assertThat(body.text("nsu")).isPresent();
    assertThat(body.text(AutraFields.RRN))
        .as("the rrn is twelve digits of its own, not the tracking number printed beside it")
        .isPresent()
        .isNotEqualTo(body.text("trackingNumber"));
    assertThat(body.text(AutraFields.AUTHORIZATION_CODE)).isPresent();
    assertThat(body.number(AutraFields.INSTALLMENTS)).isEmpty();
  }

  @Test
  void the_reservation_it_answered_is_stored_ready_for_capture_and_holds_the_amount_it_reserved() {
    AutraApiResponse response = preAuthorize(apiFor(Map.of()), schemaBody("500.00", null));

    AutraPayment stored = payments.all().get(0);
    assertThat(stored.paymentId()).isEqualTo(response.providerObjectId());
    assertThat(stored.nativeStatus()).isEqualTo("ACCEPTED");
    assertThat(stored.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(stored.capturableRemainder()).isEqualByComparingTo("500.00");
    assertThat(stored.slugToken()).isEqualTo("slug-token-abc");
  }

  @Test
  void a_body_that_is_not_a_json_object_is_answered_400_invalid_json() {
    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), "not json")))
        .isEqualTo(AutraErrors.INVALID_JSON);
  }

  @Test
  void a_request_without_documentId_is_answered_400_missing_document_id() {
    String body = schemaBody("500.00", null).replace("\"documentId\":\"" + DOCUMENT + "\",", "");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body)))
        .isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
  }

  @Test
  void a_request_without_transactionalToken_is_answered_400_missing_transactional_token() {
    String body =
        schemaBody("500.00", null).replace("\"transactionalToken\":\"session-token\",", "");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body)))
        .isEqualTo(AutraErrors.MISSING_TRANSACTIONAL_TOKEN);
  }

  @Test
  void a_transactionalToken_this_run_opened_no_session_under_is_answered_400_bad_request() {
    String body = schemaBody("500.00", null).replace("session-token", "never-minted");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body))).isEqualTo(AutraErrors.BAD_REQUEST);
  }

  @Test
  void a_documentId_naming_no_merchant_of_this_run_is_answered_merchant_not_found_at_404() {
    String body = schemaBody("500.00", null).replace(DOCUMENT, "11111111111");

    AutraApiResponse response = preAuthorize(apiFor(Map.of()), body);

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void the_account_may_move_merchant_not_found_to_either_of_the_other_two_published_statuses() {
    String body = schemaBody("500.00", null).replace(DOCUMENT, "11111111111");

    assertThat(
            preAuthorize(
                    apiFor(
                        Map.of(
                            AutraMerchantNotFound.SETTING,
                            AutraMerchantNotFound.UNPROCESSABLE.name())),
                    body)
                .httpStatus())
        .isEqualTo(422);
  }

  @Test
  void a_debit_transaction_type_is_refused_because_the_pre_authorization_enum_is_credit_alone() {
    String body = schemaBody("500.00", null).replace("\"CREDIT\"", "\"DEBIT\"");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body))).isEqualTo(AutraErrors.BAD_REQUEST);
  }

  @Test
  void a_request_without_transactionType_is_refused_under_the_schema_that_requires_it() {
    String body = schemaBody("500.00", null).replace("\"transactionType\":\"CREDIT\",", "");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body))).isEqualTo(AutraErrors.BAD_REQUEST);
  }

  @Test
  void a_request_without_currency_is_refused() {
    String body = schemaBody("500.00", null).replace("\"currency\":\"BRL\",", "");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body))).isEqualTo(AutraErrors.BAD_REQUEST);
  }

  @Test
  void an_amount_that_is_not_above_zero_is_answered_400_invalid_amount() {
    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), schemaBody("0", null))))
        .isEqualTo(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void
      the_twelve_instalments_autra_publishes_as_the_most_it_takes_are_reserved_without_complaint() {
    String body = schemaBody("500.00", null).replace("\"installments\":1", "\"installments\":12");

    AutraApiResponse response = preAuthorize(apiFor(Map.of()), body);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(payments.all().get(0).installments()).isEqualTo(12);
  }

  @Test
  void the_identifier_a_reservation_draws_is_written_the_way_autra_publishes_its_payment_uuid() {
    AutraApiResponse response = preAuthorize(apiFor(Map.of()), schemaBody("500.00", null));

    assertThat(
            AutraJsonReader.objectIn(AutraWire.bodyOf(response))
                .orElseThrow()
                .text(AutraFields.PAYMENT_ID_UPPERCASE_D)
                .orElseThrow())
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
  }

  @Test
  void an_instalment_count_outside_one_to_twelve_is_refused() {
    String body = schemaBody("500.00", null).replace("\"installments\":1", "\"installments\":13");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body))).isEqualTo(AutraErrors.BAD_REQUEST);
  }

  @Test
  void a_request_without_installments_is_refused_under_the_schema_that_requires_it() {
    String body = schemaBody("500.00", null).replace(",\"installments\":1", "");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body))).isEqualTo(AutraErrors.BAD_REQUEST);
  }

  @Test
  void sending_both_card_and_tokenData_is_refused_as_the_guide_says_the_backend_refuses_it() {
    String body =
        schemaBody("500.00", null).replace("\"tokenData\":{", "\"card\":{},\"tokenData\":{");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body))).isEqualTo(AutraErrors.BAD_REQUEST);
  }

  @Test
  void sending_neither_card_nor_tokenData_is_refused_under_the_schema_that_requires_one_of_them() {
    String body =
        schemaBody("500.00", null).replace(",\"tokenData\":{\"slugToken\":\"slug-token-abc\"}", "");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body))).isEqualTo(AutraErrors.BAD_REQUEST);
  }

  @Test
  void the_guides_three_field_example_is_reserved_when_the_account_asks_for_that_publication() {
    AutraApi api =
        apiFor(
            Map.of(
                AutraRequestShape.PRE_AUTHORIZATION_SETTING,
                AutraRequestShape.PUBLISHED_GUIDE_EXAMPLE.name()));

    AutraApiResponse response =
        preAuthorize(
            api, "{\"documentId\":\"" + DOCUMENT + "\",\"amount\":500.00,\"currency\":\"BRL\"}");

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(payments.all().get(0).installments()).isEqualTo(1);
  }

  @Test
  void the_guides_three_field_example_is_refused_under_the_schema_this_simulator_defaults_to() {
    AutraApiResponse response =
        preAuthorize(
            apiFor(Map.of()),
            "{\"documentId\":\"" + DOCUMENT + "\",\"amount\":500.00,\"currency\":\"BRL\"}");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MISSING_TRANSACTIONAL_TOKEN);
  }

  @Test
  void a_second_reservation_under_an_order_that_still_holds_one_is_answered_409_and_creates_none() {
    AutraApi api = apiFor(Map.of());
    preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));

    AutraApiResponse repeated = preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));

    assertThat(repeated.httpStatus()).isEqualTo(409);
    assertThat(theCodeIn(repeated)).isEqualTo(AutraErrors.ORDER_ALREADY_PREAUTHORIZED);
    assertThat(payments.all()).hasSize(1);
    assertThat(payments.observedRequests())
        .last()
        .satisfies(
            observed -> {
              assertThat(observed.verdict()).isEqualTo(ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
              assertThat(observed.providerObjectId()).isEqualTo(payments.all().get(0).paymentId());
            });
  }

  @Test
  void the_409_it_answers_a_repeat_with_names_no_payment_at_all() {
    AutraApi api = apiFor(Map.of());
    preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));

    AutraApiResponse repeated = preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));

    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(repeated)).orElseThrow();
    assertThat(body.members().keySet()).containsExactly(AutraFields.ERRORS);
    assertThat(repeated.providerObjectId()).isNull();
  }

  @Test
  void a_repeat_arriving_while_the_first_still_holds_the_order_is_recorded_under_its_own_verdict() {
    payments.holdingAnUnfinishedClaimOn(RUN, DOCUMENT, "pedido-abc-123");
    AutraApi api = apiFor(Map.of());

    AutraApiResponse response = preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));

    assertThat(response.httpStatus()).isEqualTo(409);
    assertThat(payments.observedRequests())
        .singleElement()
        .satisfies(
            observed ->
                assertThat(observed.verdict())
                    .isEqualTo(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD));
  }

  @Test
  void two_reservations_that_name_no_order_are_two_reservations_and_record_no_keyed_request() {
    AutraApi api = apiFor(Map.of());

    preAuthorize(api, schemaBody("500.00", null));
    preAuthorize(api, schemaBody("500.00", null));

    assertThat(payments.all()).hasSize(2);
    assertThat(payments.observedRequests()).isEmpty();
  }

  @Test
  void a_reservation_is_recorded_under_its_order_with_the_answer_it_gave_and_the_payment_it_made() {
    preAuthorize(apiFor(Map.of()), schemaBody("500.00", "pedido-abc-123"));

    assertThat(payments.observedRequests())
        .singleElement()
        .satisfies(
            observed -> {
              assertThat(observed.stateChanged()).isTrue();
              assertThat(observed.providerObjectId()).isEqualTo(payments.all().get(0).paymentId());
              assertThat(observed.httpStatus()).isEqualTo(200);
              assertThat(observed.contentType()).isEqualTo(AutraFields.JSON);
              assertThat(observed.operation()).isEqualTo(AutraOperations.PRE_AUTHORIZATION);
            });
  }

  @Test
  void a_declined_attempt_is_recorded_as_a_request_that_changed_what_the_run_holds_as_well() {
    AutraApi api = apiFor(Map.of());
    declining();

    preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));

    assertThat(payments.observedRequests())
        .singleElement()
        .satisfies(
            observed -> {
              assertThat(observed.stateChanged()).isTrue();
              assertThat(observed.httpStatus()).isEqualTo(422);
            });
  }

  @Test
  void a_declined_attempt_releases_the_order_so_a_repeat_under_the_same_key_reserves() {
    AutraApi api = apiFor(Map.of());
    declining();

    AutraApiResponse declined = preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));
    rules.clearAll(AutraApi.AUTRA);
    AutraApiResponse retried = preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));

    assertThat(declined.httpStatus()).isEqualTo(422);
    assertThat(retried.httpStatus()).isEqualTo(200);
    assertThat(payments.all()).hasSize(2);
  }

  @Test
  void
      the_repeat_a_failed_attempt_let_through_is_recorded_as_a_repeat_and_not_as_a_first_request() {
    AutraApi api = apiFor(Map.of());
    declining();
    preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));
    rules.clearAll(AutraApi.AUTRA);

    preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));

    assertThat(payments.observedRequests())
        .extracting(AutraObservedRequest::verdict)
        .containsExactly(ExchangeVerdict.FIRST_REQUEST, ExchangeVerdict.REPEAT_ADMITTED);
  }

  @Test
  void a_decline_is_422_carrying_the_code_the_rule_named_and_the_message_the_account_configures() {
    AutraApi api = apiFor(Map.of(AutraAuthorizerMessage.SETTING, "51 - Not sufficient funds"));
    declining();

    AutraApiResponse response = preAuthorize(api, schemaBody("500.00", null));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.AUTHORIZER_REJECTED);
    assertThat(theMessageIn(response)).isEqualTo("51 - Not sufficient funds");
  }

  @Test
  void a_declined_attempt_is_kept_as_an_object_although_the_wire_answer_names_none() {
    AutraApi api = apiFor(Map.of());
    declining();

    AutraApiResponse response = preAuthorize(api, schemaBody("500.00", null));

    assertThat(
            AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow().members().keySet())
        .containsExactly(AutraFields.ERRORS);
    assertThat(payments.all())
        .singleElement()
        .satisfies(
            payment -> {
              assertThat(payment.nativeStatus()).isEqualTo(AutraStatus.DECLINED);
              assertThat(payment.normalizedStatus()).isEqualTo(NormalizedStatus.FAILED);
            });
  }

  @Test
  void a_fault_answered_instead_of_the_operation_leaves_the_order_free_for_the_retry_to_use() {
    AutraApi api = apiFor(Map.of());
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.PRE_AUTHORIZATION),
            new ScenarioAction.TransportFaultInsteadOfOperation(
                new TransportFault.ServerError(502)),
            null));

    AutraApiResponse faulted = preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));
    rules.clearAll(AutraApi.AUTRA);
    AutraApiResponse retried = preAuthorize(api, schemaBody("500.00", "pedido-abc-123"));

    assertThat(faulted.httpStatus()).isEqualTo(502);
    assertThat(retried.httpStatus()).isEqualTo(200);
    assertThat(payments.all()).hasSize(1);
  }

  @Test
  void an_instalment_count_that_is_not_a_whole_number_is_refused_rather_than_rounded() {
    String body = schemaBody("500.00", null).replace("\"installments\":1", "\"installments\":1.5");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body))).isEqualTo(AutraErrors.BAD_REQUEST);
  }

  @Test
  void a_documentId_sent_as_blank_text_is_refused_the_way_an_absent_one_is() {
    String body = schemaBody("500.00", null).replace(DOCUMENT, "   ");

    assertThat(theCodeIn(preAuthorize(apiFor(Map.of()), body)))
        .isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
  }

  @Test
  void a_stored_card_named_only_by_the_stored_card_slug_records_that_half_and_no_network_token() {
    String body =
        schemaBody("500.00", null)
            .replace(
                "\"slugToken\":\"slug-token-abc\"", "\"slugStoredCard\":\"slug-stored-card-xyz\"");

    preAuthorize(apiFor(Map.of()), body);

    assertThat(payments.all().get(0).slugToken()).isNull();
    assertThat(payments.all().get(0).slugStoredCard()).isEqualTo("slug-stored-card-xyz");
  }

  @Test
  void a_stored_card_naming_both_halves_records_each_half_under_the_name_autra_gives_it() {
    String body =
        schemaBody("500.00", null)
            .replace(
                "{\"slugToken\":\"slug-token-abc\"}",
                "{\"slugToken\":\"slug-token-abc\",\"slugStoredCard\":\"slug-stored-card-xyz\"}");

    preAuthorize(apiFor(Map.of()), body);

    assertThat(payments.all().get(0).slugToken()).isEqualTo("slug-token-abc");
    assertThat(payments.all().get(0).slugStoredCard()).isEqualTo("slug-stored-card-xyz");
  }

  @Test
  void a_reservation_paid_with_a_card_rather_than_a_token_records_no_stored_card_reference() {
    String body =
        schemaBody("500.00", null)
            .replace(",\"tokenData\":{\"slugToken\":\"slug-token-abc\"}", ",\"card\":{}");

    AutraApiResponse response = preAuthorize(apiFor(Map.of()), body);

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(payments.all().get(0).slugToken()).isNull();
  }

  @Test
  void a_token_object_naming_neither_slug_names_no_card_and_is_refused_as_on_the_sale() {
    String body = schemaBody("500.00", null).replace("{\"slugToken\":\"slug-token-abc\"}", "{}");

    AutraApiResponse response = preAuthorize(apiFor(Map.of()), body);

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.BAD_REQUEST);
    assertThat(theMessageIn(response)).isEqualTo(ChargeStoredCard.THE_TOKEN_DATA_NAMES_NO_CARD);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void a_reservation_a_rule_let_through_but_the_store_refused_is_not_reported_as_a_success() {
    AutraApi api = apiFor(Map.of());
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.CAPTURE),
            new ScenarioAction.FixedSuccess(),
            null));
    String paymentId = preAuthorize(api, schemaBody("500.00", null)).providerObjectId();
    payments.losingTheNextLedgerClaim();

    AutraApiResponse response =
        api.capture(
            AutraWire.call(
                AutraApiController.PAYMENTS + "/" + paymentId + "/capture",
                AutraWire.bearer(aBearer(api)),
                "{\"transactionalToken\":\""
                    + aSession()
                    + "\",\"documentId\":\""
                    + DOCUMENT
                    + "\"}"),
            paymentId);

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(response.outcome()).isEqualTo(ProviderOutcome.FAILURE);
  }

  @Test
  void a_payment_identifier_the_store_will_not_take_is_drawn_again_until_one_is_claimed() {
    payments.refusingTheNextPaymentIds(PreAuthorize.PAYMENT_ID_ATTEMPTS - 1);

    assertThat(preAuthorize(apiFor(Map.of()), schemaBody("500.00", null)).httpStatus())
        .isEqualTo(200);
  }

  @Test
  void a_payment_identifier_no_attempt_could_claim_is_a_call_this_simulator_cannot_answer() {
    AutraApi api = apiFor(Map.of());
    String body = schemaBody("500.00", null);
    payments.refusingTheNextPaymentIds(PreAuthorize.PAYMENT_ID_ATTEMPTS);

    assertThatThrownBy(() -> preAuthorize(api, body))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("paymentID");
  }

  @Test
  void a_slug_no_card_of_this_run_holds_is_refused_as_a_token_no_longer_available() {
    AutraApiResponse response =
        preAuthorize(apiFor(Map.of()), holding("{\"slugToken\":\"slug-nobody-minted\"}"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.TOKEN_NOT_AVAILABLE);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void two_halves_of_two_different_cards_name_no_card_and_are_refused() {
    AutraApi api = apiFor(Map.of());
    cardSessions.holding(tokenized(RUN));

    AutraApiResponse response =
        preAuthorize(
            api,
            holding(
                "{\"slugToken\":\"slug-of-the-page\",\"slugStoredCard\":\""
                    + AutraDoubles.THE_EXAMPLE_SLUG_STORED_CARD
                    + "\"}"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.TOKEN_NOT_AVAILABLE);
    assertThat(payments.all()).isEmpty();
  }

  @Test
  void a_slug_a_payer_tokenized_on_the_card_page_is_a_card_the_hold_reserves() {
    AutraApi api = apiFor(Map.of());
    cardSessions.holding(tokenized(RUN));

    AutraApiResponse response = preAuthorize(api, holding("{\"slugToken\":\"slug-of-the-page\"}"));

    assertThat(response.httpStatus()).isEqualTo(200);
    assertThat(payments.all().get(0).slugToken()).isEqualTo("slug-of-the-page");
  }

  @Test
  void a_slug_tokenized_in_another_run_is_no_card_of_this_one() {
    AutraApi api = apiFor(Map.of());
    cardSessions.holding(tokenized(TestRunId.of("elsewhere")));

    AutraApiResponse response = preAuthorize(api, holding("{\"slugToken\":\"slug-of-the-page\"}"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.TOKEN_NOT_AVAILABLE);
    assertThat(payments.all()).isEmpty();
  }

  private String holding(String tokenData) {
    return schemaBody("500.00", null)
        .replace("\"tokenData\":{\"slugToken\":\"slug-token-abc\"}", "\"tokenData\":" + tokenData);
  }

  private static AutraCardSession tokenized(TestRunId run) {
    return AutraDoubles.aTokenizedSession(
        run, ACCOUNT, DOCUMENT, "slug-of-the-page", "stored-of-the-page", NOW);
  }

  private void declining() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.PRE_AUTHORIZATION),
            new ScenarioAction.TerminalFailure(AutraErrors.AUTHORIZER_REJECTED),
            null));
  }

  static String theCodeIn(AutraApiResponse response) {
    return theErrorFieldIn(response, AutraFields.CODE);
  }

  static String theMessageIn(AutraApiResponse response) {
    return theErrorFieldIn(response, AutraFields.MSG);
  }

  private static String theErrorFieldIn(AutraApiResponse response, String field) {
    Map<String, List<String>> flattened =
        AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow().flatten();
    return flattened.getOrDefault(AutraFields.ERRORS + "." + field, List.of()).get(0);
  }

  @Test
  void a_reservation_names_the_acquirer_transaction_once_under_each_of_the_two_published_names() {
    AutraApiResponse response = preAuthorize(apiFor(Map.of()), schemaBody("500.00", null));

    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.members().keySet())
        .containsExactly(
            AutraFields.PAYMENT_ID_UPPERCASE_D,
            AutraFields.STATUS,
            AutraFields.AMOUNT,
            AutraFields.CURRENCY,
            "trackingNumber",
            "nsu",
            AutraFields.RRN,
            AutraFields.AUTHORIZATION_CODE);
    assertThat(body.text("nsu")).isEqualTo(body.text("trackingNumber"));
    assertThat(body.text("trackingNumber")).contains(payments.all().get(0).trackingNumber());
  }

  @Test
  void a_reservation_whose_acquirer_named_no_transaction_publishes_neither_of_the_two_names() {
    AutraApiResponse response =
        preAuthorize(
            apiFor(Map.of(AutraAcquirerReport.SETTING, AutraAcquirerReport.NOT_REPORTED.name())),
            schemaBody("500.00", null));

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraJson.JsonObject body = AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
    assertThat(body.has("trackingNumber")).isFalse();
    assertThat(body.has("nsu")).isFalse();
    assertThat(body.text(AutraFields.RRN)).isPresent();
    assertThat(body.text(AutraFields.AUTHORIZATION_CODE)).isPresent();
    assertThat(payments.all().get(0).trackingNumber()).isNull();
  }
}
