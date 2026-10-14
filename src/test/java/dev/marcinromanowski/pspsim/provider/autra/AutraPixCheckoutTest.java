package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AutraPixCheckoutTest {

  private static final TestRunId RUN = TestRunId.of("autra-pix-checkout");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String ORDER = "pedido-abc-123";

  private final AutraDoubles.Merchants merchants = new AutraDoubles.Merchants();
  private final AutraDoubles.Sessions sessions = new AutraDoubles.Sessions();
  private final AutraDoubles.Payments payments = new AutraDoubles.Payments();
  private final AutraDoubles.PixPayments pixPayments = new AutraDoubles.PixPayments();
  private final AutraDoubles.Audit audit = new AutraDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final ScenarioEngine scenarios = new ScenarioEngine(rules);

  private AutraApi api() {
    merchants.with(AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT));
    return AutraWiring.api(
        merchants,
        sessions,
        payments,
        pixPayments,
        audit,
        RUN,
        NOW,
        scenarios,
        new AutraDoubles.Runs(RUN));
  }

  @Test
  void the_pix_initialize_answers_the_only_code_autra_publishes_beside_the_session_token() {
    AutraApi api = api();

    AutraApiResponse response = initialize(api, "{\"documentId\":\"" + DOCUMENT + "\"}");

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraJson.JsonObject body = bodyOf(response);
    assertThat(body.text(AutraFields.CODE)).contains(AutraStatus.ACCEPTED);
    assertThat(body.text(AutraFields.TRANSACTIONAL_TOKEN)).isPresent();
  }

  @Test
  void the_card_initialize_answers_no_code_because_its_own_published_example_carries_none() {
    AutraApi api = api();

    AutraApiResponse response =
        api.initialize(
            AutraWire.call(
                AutraApiController.INITIALIZE,
                AutraWire.bearer(aBearer(api)),
                "{\"documentId\":\"" + DOCUMENT + "\"}"));

    assertThat(bodyOf(response).text(AutraFields.CODE)).isEmpty();
    assertThat(bodyOf(response).text(AutraFields.TRANSACTIONAL_TOKEN)).isPresent();
  }

  @Test
  void a_pix_initialize_for_a_document_no_merchant_of_this_run_holds_is_refused_as_not_found() {
    AutraApi api = api();

    AutraApiResponse response = initialize(api, "{\"documentId\":\"11111111111\"}");

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response).members()).containsKey(AutraFields.ERRORS);
  }

  @Test
  void a_checkout_is_answered_the_published_fields_with_the_status_pending_and_a_qr_payload() {
    AutraApi api = api();

    AutraApiResponse response = checkout(api, body(api, "99.90", ORDER, "Pedido #1234"));

    assertThat(response.httpStatus()).isEqualTo(200);
    AutraJson.JsonObject body = bodyOf(response);
    assertThat(body.text(AutraFields.PIX_ID)).isPresent();
    assertThat(body.text(AutraFields.STATUS)).contains(AutraStatus.PENDING);
    assertThat(body.text(AutraFields.QR_CODE)).isPresent();
    assertThat(body.number(AutraFields.AMOUNT)).contains("99.90");
    assertThat(body.text(AutraFields.CURRENCY)).contains("BRL");
    assertThat(body.text(AutraFields.EXPIRES_AT)).contains("2026-09-22T10:05:00Z");
  }

  @Test
  void the_pix_identifier_it_answers_with_is_thirty_two_lowercase_hex_digits() {
    AutraApi api = api();

    String pixId =
        bodyOf(checkout(api, body(api, "99.90", null, null)))
            .text(AutraFields.PIX_ID)
            .orElseThrow();

    assertThat(pixId).hasSize(AutraIdentifiers.UUID_HEX_DIGITS).matches("[0-9a-f]+");
  }

  @Test
  void the_qr_payload_carries_the_prefix_autra_publishes_and_the_pix_it_belongs_to() {
    AutraApi api = api();

    AutraJson.JsonObject body = bodyOf(checkout(api, body(api, "99.90", null, null)));

    assertThat(body.text(AutraFields.QR_CODE))
        .contains(
            CreatePixCheckout.THE_PUBLISHED_QR_PREFIX
                + body.text(AutraFields.PIX_ID).orElseThrow());
  }

  @Test
  void two_checkouts_under_one_order_produce_two_pix_identifiers_and_two_qr_payloads() {
    AutraApi api = api();

    AutraJson.JsonObject first = bodyOf(checkout(api, body(api, "99.90", ORDER, null)));
    AutraApiResponse repeat = checkout(api, body(api, "99.90", ORDER, null));
    AutraJson.JsonObject second = bodyOf(repeat);

    assertThat(repeat.httpStatus()).isEqualTo(200);
    assertThat(second.text(AutraFields.PIX_ID))
        .isPresent()
        .isNotEqualTo(first.text(AutraFields.PIX_ID));
    assertThat(second.text(AutraFields.QR_CODE))
        .isPresent()
        .isNotEqualTo(first.text(AutraFields.QR_CODE));
    assertThat(pixPayments.newestFirst(RUN, null, 10)).hasSize(2);
  }

  @Test
  void a_repeated_checkout_records_no_keyed_exchange_because_this_endpoint_claims_no_order() {
    AutraApi api = api();

    checkout(api, body(api, "99.90", ORDER, null));
    checkout(api, body(api, "99.90", ORDER, null));

    assertThat(payments.observedRequests()).isEmpty();
  }

  @Test
  void the_pix_it_created_is_stored_awaiting_its_payer_and_normalizes_as_still_in_flight() {
    AutraApi api = api();

    AutraApiResponse response = checkout(api, body(api, "99.90", ORDER, "Pedido #1234"));

    AutraPixPayment stored =
        pixPayments.findById(RUN, String.valueOf(response.providerObjectId())).orElseThrow();
    assertThat(stored.nativeStatus()).isEqualTo(AutraStatus.PENDING);
    assertThat(stored.normalizedStatusAt(NOW)).isEqualTo(NormalizedStatus.PROCESSING);
    assertThat(stored.isAwaitingThePayer()).isTrue();
    assertThat(stored.orderId()).isEqualTo(ORDER);
    assertThat(stored.description()).isEqualTo("Pedido #1234");
    assertThat(stored.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(stored.expiresAt()).isEqualTo(NOW.plus(CreatePixCheckout.THE_QR_LIVES_FOR));
  }

  @Test
  void a_checkout_whose_session_token_names_no_session_is_refused_403_as_autra_publishes() {
    AutraApi api = api();

    AutraApiResponse response =
        checkout(
            api,
            "{\"transactionalToken\":\"a-token-that-expired\",\"documentId\":\""
                + DOCUMENT
                + "\",\"amount\":99.90,\"currency\":\"BRL\"}");

    assertThat(response.httpStatus()).isEqualTo(403);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.CODE))
        .containsExactly(AutraErrors.UNAUTHORIZED);
    assertThat(pixPayments.findById(RUN, "any")).isEmpty();
  }

  @Test
  void a_checkout_that_names_no_session_token_is_refused_for_the_missing_field_and_not_403() {
    AutraApi api = api();

    AutraApiResponse response =
        checkout(
            api, "{\"documentId\":\"" + DOCUMENT + "\",\"amount\":99.90,\"currency\":\"BRL\"}");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.CODE))
        .containsExactly(AutraErrors.MISSING_TRANSACTIONAL_TOKEN);
  }

  @Test
  void a_checkout_that_names_no_merchant_document_is_refused_before_the_session_is_read() {
    AutraApi api = api();

    AutraApiResponse response = checkout(api, "{\"amount\":99.90,\"currency\":\"BRL\"}");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.CODE))
        .containsExactly(AutraErrors.MISSING_DOCUMENT_ID);
  }

  @Test
  void a_checkout_carrying_the_payers_document_rather_than_the_merchants_finds_no_merchant() {
    AutraApi api = api();

    AutraApiResponse response =
        checkout(
            api,
            "{\"transactionalToken\":\""
                + aSession()
                + "\",\"documentId\":\"12345678901\",\"amount\":99.90,\"currency\":\"BRL\"}");

    assertThat(response.httpStatus()).isEqualTo(404);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.CODE))
        .containsExactly(AutraErrors.MERCHANT_NOT_FOUND);
  }

  @Test
  void a_checkout_that_names_no_currency_is_refused_before_the_amount_is_read() {
    AutraApi api = api();

    AutraApiResponse response =
        checkout(
            api,
            "{\"transactionalToken\":\""
                + aSession()
                + "\",\"documentId\":\""
                + DOCUMENT
                + "\",\"amount\":99.90}");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.MSG))
        .containsExactly(CreatePixCheckout.THE_CURRENCY_IS_REQUIRED);
  }

  @Test
  void a_checkout_for_an_amount_that_is_not_above_zero_is_refused_and_creates_no_pix() {
    AutraApi api = api();

    AutraApiResponse response = checkout(api, body(api, "0", null, null));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.CODE))
        .containsExactly(AutraErrors.INVALID_AMOUNT);
  }

  @Test
  void a_checkout_whose_amount_is_no_number_names_the_amount_and_not_the_body_it_could_not_read() {
    AutraApi api = api();

    AutraApiResponse response = checkout(api, body(api, "\"abc\"", null, null));

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.CODE))
        .as(
            "no JSON was involved in the mistake: the body parsed and one field held a value that"
                + " is not a number, so the refusal names that field")
        .containsExactly(AutraErrors.INVALID_AMOUNT);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.MSG))
        .containsExactly(CreatePixCheckout.THE_AMOUNT_MUST_BE_ABOVE_ZERO);
    assertThat(pixPayments.newestFirst(RUN, null, 10)).isEmpty();
  }

  @Test
  void a_checkout_whose_body_is_not_a_json_object_is_refused_as_invalid_json() {
    AutraApi api = api();

    AutraApiResponse response = checkout(api, "not json at all");

    assertThat(response.httpStatus()).isEqualTo(400);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.CODE))
        .containsExactly(AutraErrors.INVALID_JSON);
  }

  @Test
  void a_rule_that_declines_the_checkout_answers_422_and_leaves_no_pix_behind() {
    AutraApi api = api();
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(AutraOperations.PIX_CHECKOUT),
            new ScenarioAction.TerminalFailure(AutraErrors.MSG_GENERAL_ERROR),
            null));

    AutraApiResponse response = checkout(api, body(api, "99.90", null, null));

    assertThat(response.httpStatus()).isEqualTo(422);
    assertThat(bodyOf(response).flatten().get(AutraFields.ERRORS + "." + AutraFields.CODE))
        .containsExactly(AutraErrors.MSG_GENERAL_ERROR);
    assertThat(bodyOf(response).text(AutraFields.PIX_ID)).isEmpty();
    assertThat(pixPayments.newestFirst(RUN, null, 10)).isEmpty();
  }

  @Test
  void a_rule_written_for_the_pre_authorization_does_not_decide_a_pix_checkout() {
    AutraApi api = api();
    rules.add(
        new NewScenarioRule(
            AutraApi.AUTRA,
            RUN,
            1,
            ScenarioMatch.anyRequest().naming(AutraOperations.PRE_AUTHORIZATION),
            new ScenarioAction.TerminalFailure(AutraErrors.MSG_GENERAL_ERROR),
            null));

    assertThat(checkout(api, body(api, "99.90", null, null)).httpStatus()).isEqualTo(200);
  }

  @Test
  void a_checkout_that_draws_a_pix_identifier_this_run_already_holds_gives_up_after_five_tries() {
    AutraApi api = api();
    for (int drawn = 1; drawn <= CreatePixCheckout.PIX_ID_ATTEMPTS; drawn++) {
      pixPayments.insertIfAbsent(aPixAlreadyDrawnAs(theHexOf(drawn)));
    }

    String body = body(api, "99.90", null, null);

    assertThatThrownBy(() -> checkout(api, body))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(String.valueOf(CreatePixCheckout.PIX_ID_ATTEMPTS));
  }

  @Test
  void a_checkout_whose_sixth_draw_would_be_free_still_gives_up_on_the_fifth() {
    AutraApi api = api();
    for (int drawn = 1; drawn < CreatePixCheckout.PIX_ID_ATTEMPTS; drawn++) {
      pixPayments.insertIfAbsent(aPixAlreadyDrawnAs(theHexOf(drawn)));
    }

    assertThat(checkout(api, body(api, "99.90", null, null)).providerObjectId())
        .isEqualTo(theHexOf(CreatePixCheckout.PIX_ID_ATTEMPTS));
  }

  private static String theHexOf(int drawn) {
    String hex = Integer.toHexString(drawn);
    return "0".repeat(AutraIdentifiers.UUID_HEX_DIGITS - hex.length()) + hex;
  }

  private static AutraPixPayment aPixAlreadyDrawnAs(String pixId) {
    return new AutraPixPayment(
        RUN,
        pixId,
        ACCOUNT,
        DOCUMENT,
        null,
        null,
        Amount.of("1.00"),
        "BRL",
        AutraStatus.PENDING,
        CreatePixCheckout.theQrCodeOf(pixId),
        "A1",
        "B2",
        NOW.plus(CreatePixCheckout.THE_QR_LIVES_FOR),
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static AutraJson.JsonObject bodyOf(AutraApiResponse response) {
    return AutraJsonReader.objectIn(AutraWire.bodyOf(response)).orElseThrow();
  }

  private AutraApiResponse initialize(AutraApi api, String body) {
    return api.pixInitialize(
        AutraWire.call(AutraApiController.PIX_INITIALIZE, AutraWire.bearer(aBearer(api)), body));
  }

  private AutraApiResponse checkout(AutraApi api, String body) {
    return api.pixCheckout(
        AutraWire.call(AutraApiController.PIX_CHECKOUT, AutraWire.bearer(aBearer(api)), body));
  }

  private String body(AutraApi api, String amount, String orderId, String description) {
    return "{\"transactionalToken\":\""
        + theTokenOfAPixSession(api)
        + "\",\"documentId\":\""
        + DOCUMENT
        + "\",\"amount\":"
        + amount
        + ",\"currency\":\"BRL\""
        + (description == null ? "" : ",\"description\":\"" + description + "\"")
        + (orderId == null ? "" : ",\"orderId\":\"" + orderId + "\"")
        + "}";
  }

  private String theTokenOfAPixSession(AutraApi api) {
    return bodyOf(initialize(api, "{\"documentId\":\"" + DOCUMENT + "\"}"))
        .text(AutraFields.TRANSACTIONAL_TOKEN)
        .orElseThrow();
  }

  private String aSession() {
    sessions.open(new AutraSession(RUN, "a-session-token", ACCOUNT, DOCUMENT, NOW));
    return "a-session-token";
  }

  private String aBearer(AutraApi api) {
    return AutraTokenTest.theTokenIn(
        api.token(
            AutraWire.tokenCall(
                AutraFields.CLIENT_CREDENTIALS,
                AutraWire.basic("client-" + ACCOUNT, "secret-" + ACCOUNT))));
  }
}
