package dev.marcinromanowski.pspsim.provider.autra;

import static dev.marcinromanowski.pspsim.provider.autra.AutraPreAuthorizationTest.theCodeIn;
import static org.assertj.core.api.Assertions.assertThat;

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

class AutraPaymentLookupTest {

  private static final TestRunId RUN = TestRunId.of("autra-lookup");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String A_PAYMENT_ID_NOTHING_HOLDS = "550e8400-e29b-41d4-a716-446655440000";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);
  private final AutraApi api = apiFor(Map.of());

  private AutraApi apiFor(Map<String, String> configuration) {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT, configuration));
    return AutraWiring.api(merchants, sessions, payments, audit, RUN, NOW, scenarios);
  }

  private AutraApiResponse read(String paymentId, Map<String, List<String>> query) {
    return api.payment(
        AutraWire.get(
            AutraApiController.PAYMENTS + "/" + paymentId,
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            query),
        paymentId);
  }

  private AutraApiResponse readByQuery(String paymentId) {
    return read(paymentId, AutraWire.query(AutraFields.DOCUMENT_ID, DOCUMENT));
  }

  private AutraJson.JsonObject bodyOf(AutraApiResponse response) {
    return AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
  }

  private String aSaleOf(String amount) {
    return AutraFlow.sell(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, null).providerObjectId();
  }

  private String aHoldOf(String amount) {
    return AutraFlow.reserve(api, RUN, ACCOUNT, DOCUMENT, sessions, amount, null)
        .providerObjectId();
  }

  @Test
  void the_read_answers_every_published_property_it_has_a_value_for_in_the_vendors_own_order() {
    String paymentId =
        AutraFlow.sell(api, RUN, ACCOUNT, DOCUMENT, sessions, "500.00", "pedido-abc-123")
            .providerObjectId();

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));

    assertThat(body.members().keySet())
        .as(
            "provider autra declares twenty-one properties on this read in this order; this"
                + " payment answers nineteen of them, maskedCard and brandReferenceId being the"
                + " two it answers with nothing, and errors being absent because no step of it"
                + " was refused")
        .containsExactly(
            "paymentID",
            "orderId",
            "type",
            "status",
            "voidRetryable",
            "amount",
            "refundedAmount",
            "currency",
            "documentId",
            "transactionType",
            "transactionMethod",
            "installments",
            "authorizationCode",
            "rrn",
            "trackingNumber",
            "createdAt",
            "updatedAt",
            "events");
    assertThat(body.members()).hasSize(18);
  }

  @Test
  void a_refused_step_adds_errors_between_trackingNumber_and_createdAt_and_nothing_else() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.SALE),
            new ScenarioAction.TerminalFailure(AutraErrors.INSUFFICIENT_FUNDS),
            null));

    AutraJson.JsonObject body = bodyOf(readByQuery(theDeclinedPaymentId()));

    assertThat(body.members().keySet())
        .containsExactly(
            "paymentID",
            "type",
            "status",
            "voidRetryable",
            "amount",
            "refundedAmount",
            "currency",
            "documentId",
            "transactionType",
            "transactionMethod",
            "installments",
            "authorizationCode",
            "rrn",
            "trackingNumber",
            "errors",
            "createdAt",
            "updatedAt",
            "events");
  }

  @Test
  void an_orderId_the_sale_carried_is_answered_in_the_place_the_vendor_declares_it() {
    String paymentId =
        AutraFlow.sell(api, RUN, ACCOUNT, DOCUMENT, sessions, "500.00", "pedido-abc-123")
            .providerObjectId();

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));

    assertThat(body.members().keySet()).element(1).isEqualTo("orderId");
    assertThat(body.text("orderId")).contains("pedido-abc-123");
  }

  @Test
  void the_identifier_is_spelled_with_a_capital_d_in_the_body_and_a_small_one_in_the_path() {
    String paymentId = aSaleOf("500.00");

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));

    assertThat(body.text("paymentID")).contains(paymentId);
    assertThat(body.text("paymentId")).isEmpty();
  }

  @Test
  void a_sale_the_acquirer_accepted_reads_back_the_facts_it_was_charged_with() {
    String paymentId = aSaleOf("500.00");

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));

    assertThat(body.text("type")).contains("SALE");
    assertThat(body.text("status")).contains("ACCEPTED");
    assertThat(body.number("amount")).contains("500.00");
    assertThat(body.number("refundedAmount")).contains("0");
    assertThat(body.text("currency")).contains("BRL");
    assertThat(body.text("documentId")).contains(DOCUMENT);
    assertThat(body.text("transactionType")).contains("CREDIT");
    assertThat(body.text("transactionMethod")).contains("TOKEN");
    assertThat(body.number("installments")).contains("1");
    assertThat(body.text("createdAt")).contains("2026-09-22T10:00:00Z");
    assertThat(body.text("updatedAt")).contains("2026-09-22T10:00:00Z");
  }

  @Test
  void a_hold_reads_back_typed_PREAUTH_and_keeps_that_type_after_it_is_captured() {
    String paymentId = aHoldOf("500.00");
    assertThat(bodyOf(readByQuery(paymentId)).text("type")).contains("PREAUTH");

    capture(paymentId);

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));
    assertThat(body.text("type")).contains("PREAUTH");
    assertThat(body.text("status")).contains("CAPTURED");
  }

  @Test
  void an_open_reservation_and_an_accepted_sale_each_read_voidRetryable_true() {
    assertThat(bodyOf(readByQuery(aHoldOf("500.00"))).member("voidRetryable"))
        .isEqualTo(new AutraJson.JsonFlag(true));
    assertThat(bodyOf(readByQuery(aSaleOf("500.00"))).member("voidRetryable"))
        .isEqualTo(new AutraJson.JsonFlag(true));
  }

  @Test
  void a_released_reservation_reads_voidRetryable_false() {
    String paymentId = aHoldOf("500.00");

    voidOf(paymentId, "500.00");

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));
    assertThat(body.text("status")).contains(AutraStatus.VOIDED);
    assertThat(body.member("voidRetryable")).isEqualTo(new AutraJson.JsonFlag(false));
  }

  private AutraApiResponse voidOf(String paymentId, String amount) {
    return api.voidPayment(
        AutraWire.call(
            AutraApiController.PAYMENTS + "/" + paymentId + "/void",
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            "{\"transactionalToken\":\""
                + AutraFlow.session(sessions, RUN, ACCOUNT, DOCUMENT)
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\",\"amount\":"
                + amount
                + "}"),
        paymentId);
  }

  private void capture(String paymentId) {
    api.capture(
        AutraWire.call(
            AutraApiController.PAYMENTS + "/" + paymentId + "/capture",
            AutraWire.bearer(AutraFlow.bearer(api, ACCOUNT)),
            "{\"transactionalToken\":\""
                + AutraFlow.session(sessions, RUN, ACCOUNT, DOCUMENT)
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\"}"),
        paymentId);
  }

  @Test
  void a_captured_hold_reads_voidRetryable_false_although_a_void_of_it_is_still_accepted() {
    String paymentId = aHoldOf("500.00");
    capture(paymentId);

    assertThat(bodyOf(readByQuery(paymentId)).member("voidRetryable"))
        .as(
            "provider autra publishes false \"quando ja foi ... capturado\", and the flag follows"
                + " the published rule rather than this simulator's own void route, which is"
                + " wider")
        .isEqualTo(new AutraJson.JsonFlag(false));
    assertThat(voidOf(paymentId, "500.00").httpStatus())
        .as("the route this simulator serves still admits the reversal the flag advises against")
        .isEqualTo(200);
  }

  @Test
  void a_zero_dollar_sale_reserved_nothing_and_reads_voidRetryable_false() {
    String paymentId = aSaleOf("0");

    assertThat(bodyOf(readByQuery(paymentId)).member("voidRetryable"))
        .isEqualTo(new AutraJson.JsonFlag(false));
  }

  @Test
  void a_declined_sale_never_reserved_value_and_reads_voidRetryable_false() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.SALE),
            new ScenarioAction.TerminalFailure(AutraErrors.CARD_DECLINED),
            null));

    assertThat(bodyOf(readByQuery(theDeclinedPaymentId())).member("voidRetryable"))
        .isEqualTo(new AutraJson.JsonFlag(false));
  }

  @Test
  void a_partially_reversed_sale_reads_voidRetryable_false_although_value_remains() {
    String paymentId = aSaleOf("500.00");
    voidOf(paymentId, "200.00");

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));

    assertThat(body.text("status")).contains(AutraStatus.PARTIALLY_VOIDED);
    assertThat(body.member("voidRetryable")).isEqualTo(new AutraJson.JsonFlag(false));
  }

  @Test
  void a_reversed_sale_reads_back_the_sum_already_returned_under_refundedAmount() {
    String paymentId = aSaleOf("500.00");

    voidOf(paymentId, "200.00");

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));
    assertThat(body.number("refundedAmount")).contains("200.00");
    assertThat(body.text("status")).contains("PARTIALLY_VOIDED");
  }

  @Test
  void the_timeline_carries_one_event_per_step_in_the_order_the_steps_happened() {
    String paymentId = aHoldOf("500.00");
    capture(paymentId);

    AutraJson events = bodyOf(readByQuery(paymentId)).member(AutraFields.EVENTS);

    assertThat(events).isInstanceOf(AutraJson.JsonArray.class);
    List<AutraJson> timeline = ((AutraJson.JsonArray) events).elements();
    assertThat(timeline).hasSize(2);
    AutraJson.JsonObject reserved = (AutraJson.JsonObject) timeline.get(0);
    assertThat(reserved.members().keySet()).containsExactly("type", "status", "amount", "at");
    assertThat(reserved.text("type")).contains("PREAUTH");
    assertThat(reserved.text("status")).contains("ACCEPTED");
    assertThat(reserved.number("amount")).contains("500.00");
    assertThat(reserved.text("at")).contains("2026-09-22T10:00:00Z");
    AutraJson.JsonObject captured = (AutraJson.JsonObject) timeline.get(1);
    assertThat(captured.text("type")).contains("CAPTURE");
    assertThat(captured.text("status")).contains("CAPTURED");
  }

  @Test
  void a_declined_sale_reads_back_its_refusal_in_the_timeline_and_in_errors() {
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            10,
            ScenarioMatch.anyRequest().naming(AutraOperations.SALE),
            new ScenarioAction.TerminalFailure(AutraErrors.INSUFFICIENT_FUNDS),
            null));
    String paymentId = theDeclinedPaymentId();

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));

    assertThat(body.text("status")).contains("DECLINED");
    List<AutraJson> errors = ((AutraJson.JsonArray) body.member(AutraFields.ERRORS)).elements();
    assertThat(errors).hasSize(1);
    AutraJson.JsonObject refusal = (AutraJson.JsonObject) errors.get(0);
    assertThat(refusal.text("code")).contains(AutraErrors.INSUFFICIENT_FUNDS);
    assertThat(refusal.text("msg"))
        .contains(AutraDeclineMessages.THE_PUBLISHED_INSUFFICIENT_FUNDS_MESSAGE);
    AutraJson.JsonObject event =
        (AutraJson.JsonObject)
            ((AutraJson.JsonArray) body.member(AutraFields.EVENTS)).elements().get(0);
    assertThat(event.members().keySet())
        .containsExactly("type", "status", "amount", "at", "errorCode", "msg");
    assertThat(event.text("errorCode")).contains(AutraErrors.INSUFFICIENT_FUNDS);
  }

  private String theDeclinedPaymentId() {
    AutraFlow.sell(api, RUN, ACCOUNT, DOCUMENT, sessions, "500.00", null);
    return payments.all().getLast().paymentId();
  }

  @Test
  void maskedCard_and_brandReferenceId_are_the_two_published_properties_this_read_never_answers() {
    String paymentId = aSaleOf("500.00");

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));

    assertThat(body.has("maskedCard")).isFalse();
    assertThat(body.has("brandReferenceId")).isFalse();
  }

  @Test
  void includeRecurring_is_accepted_and_answers_exactly_what_a_call_without_it_answers() {
    String paymentId = aSaleOf("500.00");

    AutraApiResponse with =
        read(
            paymentId,
            AutraWire.query(
                AutraFields.DOCUMENT_ID, DOCUMENT, AutraFields.INCLUDE_RECURRING, "true"));

    assertThat(with.httpStatus()).isEqualTo(200);
    assertThat(AutraWire.bodyOf(with)).isEqualTo(AutraWire.bodyOf(readByQuery(paymentId)));
  }

  @Test
  void the_merchant_document_may_arrive_in_the_header_the_vendor_prefers() {
    String paymentId = aSaleOf("500.00");

    AutraApiResponse response =
        api.payment(
            AutraWire.get(
                AutraApiController.PAYMENTS + "/" + paymentId,
                Map.of(
                    AutraFields.AUTHORIZATION,
                    List.of(AutraFields.BEARER + AutraFlow.bearer(api, ACCOUNT)),
                    AutraFields.MERCHANT_DOCUMENT_HEADER,
                    List.of(DOCUMENT)),
                Map.of()),
            paymentId);

    assertThat(response.httpStatus()).isEqualTo(200);
  }

  @Test
  void the_header_is_the_one_read_when_a_call_names_a_document_in_both_places() {
    String paymentId = aSaleOf("500.00");

    AutraApiResponse response =
        api.payment(
            AutraWire.get(
                AutraApiController.PAYMENTS + "/" + paymentId,
                Map.of(
                    AutraFields.AUTHORIZATION,
                    List.of(AutraFields.BEARER + AutraFlow.bearer(api, ACCOUNT)),
                    AutraFields.MERCHANT_DOCUMENT_HEADER,
                    List.of(DOCUMENT)),
                AutraWire.query(AutraFields.DOCUMENT_ID, "99999999999999")),
            paymentId);

    assertThat(response.httpStatus()).isEqualTo(200);
  }

  @Test
  void a_path_parameter_that_is_not_a_uuid_is_answered_400_invalid_payment_id() {
    AutraApiResponse response = readByQuery("not-a-uuid");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_PAYMENT_ID);
  }

  @Test
  void a_call_that_names_the_merchant_nowhere_is_answered_400_missing_document_id() {
    AutraApiResponse response = read(A_PAYMENT_ID_NOTHING_HOLDS, Map.of());

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MISSING_DOCUMENT_ID);
  }

  @Test
  void a_document_that_is_not_digits_only_is_answered_422_invalid_document() {
    AutraApiResponse response =
        read(
            A_PAYMENT_ID_NOTHING_HOLDS,
            AutraWire.query(AutraFields.DOCUMENT_ID, "25.055.662/0001-90"));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_DOCUMENT);
  }

  @Test
  void a_document_longer_than_the_store_holds_is_answered_422_invalid_document() {
    AutraApiResponse response =
        read(A_PAYMENT_ID_NOTHING_HOLDS, AutraWire.query(AutraFields.DOCUMENT_ID, "1".repeat(33)));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.INVALID_DOCUMENT);
  }

  @Test
  void a_document_no_establishment_of_this_token_answers_to_is_answered_404_merchant_not_found() {
    AutraApiResponse response =
        read(A_PAYMENT_ID_NOTHING_HOLDS, AutraWire.query(AutraFields.DOCUMENT_ID, "11111111111"));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void the_read_answers_404_merchant_not_found_whatever_status_the_account_names_elsewhere() {
    merchants.merge(
        RUN,
        List.of(
            AutraDoubles.merchant(
                RUN,
                ACCOUNT,
                DOCUMENT,
                Map.of(AutraMerchantNotFound.SETTING, AutraMerchantNotFound.FORBIDDEN.name()))));

    AutraApiResponse response =
        read(A_PAYMENT_ID_NOTHING_HOLDS, AutraWire.query(AutraFields.DOCUMENT_ID, "11111111111"));

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void a_uuid_this_run_holds_no_payment_under_is_answered_404_payment_not_found() {
    AutraApiResponse response = readByQuery(A_PAYMENT_ID_NOTHING_HOLDS);

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.PAYMENT_NOT_FOUND);
  }

  @Test
  void a_payment_of_another_establishment_of_the_same_run_is_answered_404_payment_not_found() {
    String paymentId = aSaleOf("500.00");
    merchants.with(AutraDoubles.merchant(RUN, "merchant-2", "11222333000181", Map.of()));

    AutraApiResponse response =
        api.payment(
            AutraWire.get(
                AutraApiController.PAYMENTS + "/" + paymentId,
                AutraWire.bearer(AutraFlow.bearer(api, "merchant-2")),
                AutraWire.query(AutraFields.DOCUMENT_ID, "11222333000181")),
            paymentId);

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.PAYMENT_NOT_FOUND);
  }

  @Test
  void a_call_carrying_no_access_token_is_answered_401_on_this_route_like_every_other() {
    AutraApiResponse response =
        api.payment(
            AutraWire.get(
                AutraApiController.PAYMENTS + "/" + A_PAYMENT_ID_NOTHING_HOLDS,
                Map.of(),
                AutraWire.query(AutraFields.DOCUMENT_ID, DOCUMENT)),
            A_PAYMENT_ID_NOTHING_HOLDS);

    assertThat(response.httpStatus()).isEqualTo(AutraApi.UNAUTHORIZED_STATUS);
    assertThat(theCodeIn(response)).isEqualTo(AutraErrors.UNAUTHORIZED);
  }

  @Test
  void a_payment_whose_acquirer_named_no_transaction_leaves_trackingNumber_out() {
    merchants.merge(
        RUN,
        List.of(
            AutraDoubles.merchant(
                RUN,
                ACCOUNT,
                DOCUMENT,
                Map.of(AutraAcquirerReport.SETTING, AutraAcquirerReport.NOT_REPORTED.name()))));
    String paymentId = aSaleOf("500.00");

    AutraJson.JsonObject body = bodyOf(readByQuery(paymentId));

    assertThat(body.has("trackingNumber")).isFalse();
    assertThat(body.has("nsu")).isFalse();
  }

  @Test
  void the_read_never_answers_nsu_even_where_the_sale_that_made_the_payment_did() {
    String paymentId = aSaleOf("500.00");

    assertThat(bodyOf(readByQuery(paymentId)).has("nsu")).isFalse();
    assertThat(bodyOf(readByQuery(paymentId)).text("trackingNumber")).isPresent();
  }

  @Test
  void a_read_changes_nothing_a_later_read_would_see() {
    String paymentId = aSaleOf("500.00");
    String first = AutraWire.bodyOf(readByQuery(paymentId));

    readByQuery(paymentId);
    readByQuery(paymentId);

    assertThat(AutraWire.bodyOf(readByQuery(paymentId))).isEqualTo(first);
    assertThat(payments.everyEvent()).hasSize(1);
  }
}
