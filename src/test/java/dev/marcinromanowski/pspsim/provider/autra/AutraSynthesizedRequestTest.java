package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraSynthesizedRequestTest {

  private static final TestRunId RUN = TestRunId.of("autra-synthesized");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String DOCUMENT = "25055662000190";
  private static final String PAYMENT_ID = "550e8400-e29b-41d4-a716-446655440000";

  private static final AutraMerchant MERCHANT = AutraDoubles.merchant(RUN, ACCOUNT, DOCUMENT);

  @Test
  void a_payment_started_from_a_screen_is_posted_to_the_pre_authorization_route() {
    AutraApiCall call = AutraOrigination.callOf(origination("500.00", null), MERCHANT, "a-session");

    assertThat(call.method()).isEqualTo(AutraOrigination.WIRE_METHOD);
    assertThat(call.path()).isEqualTo(AutraApiController.PRE_AUTHORIZATION);
  }

  @Test
  void a_payment_started_from_a_screen_carries_the_six_fields_the_schema_requires_and_a_card() {
    AutraApiCall call = AutraOrigination.callOf(origination("500.00", null), MERCHANT, "a-session");

    AutraJson.JsonObject body = call.body().orElseThrow();
    assertThat(body.text(AutraFields.TRANSACTIONAL_TOKEN)).contains("a-session");
    assertThat(body.text(AutraFields.DOCUMENT_ID)).contains(DOCUMENT);
    assertThat(body.text(AutraFields.TRANSACTION_TYPE)).contains(AutraFields.CREDIT);
    assertThat(body.number(AutraFields.AMOUNT)).contains("500.00");
    assertThat(body.text(AutraFields.CURRENCY)).contains("BRL");
    assertThat(body.number(AutraFields.INSTALLMENTS)).contains("1");
    assertThat(body.object(AutraFields.CARD)).contains(AutraJson.empty());
    assertThat(body.has(AutraFields.TOKEN_DATA)).isFalse();
  }

  @Test
  void
      a_payment_started_without_an_order_carries_no_order_field_and_one_with_an_order_carries_it() {
    assertThat(
            AutraOrigination.callOf(origination("500.00", null), MERCHANT, "a-session")
                .body()
                .orElseThrow()
                .has(AutraFields.ORDER_ID))
        .isFalse();
    assertThat(
            AutraOrigination.callOf(origination("500.00", "pedido-1"), MERCHANT, "a-session")
                .body()
                .orElseThrow()
                .text(AutraFields.ORDER_ID))
        .contains("pedido-1");
  }

  @Test
  void a_sale_started_without_an_order_carries_no_order_field_and_one_with_an_order_carries_it() {
    assertThat(
            AutraOrigination.saleCallOf(saleOrigination("150.00", null), MERCHANT, "a-session")
                .body()
                .orElseThrow()
                .has(AutraFields.ORDER_ID))
        .isFalse();
    assertThat(
            AutraOrigination.saleCallOf(
                    saleOrigination("150.00", "pedido-1"), MERCHANT, "a-session")
                .body()
                .orElseThrow()
                .text(AutraFields.ORDER_ID))
        .contains("pedido-1");
  }

  @Test
  void a_pix_started_from_a_screen_is_posted_to_the_pix_checkout_route() {
    AutraApiCall call =
        AutraOrigination.pixCallOf(pixOrigination("99.90", null, null), MERCHANT, "a-session");

    assertThat(call.method()).isEqualTo(AutraOrigination.WIRE_METHOD);
    assertThat(call.path()).isEqualTo(AutraApiController.PIX_CHECKOUT);
  }

  @Test
  void a_pix_started_from_a_screen_carries_the_four_fields_the_checkout_requires_and_no_card() {
    AutraApiCall call =
        AutraOrigination.pixCallOf(pixOrigination("99.90", null, null), MERCHANT, "a-session");

    AutraJson.JsonObject body = call.body().orElseThrow();
    assertThat(body.text(AutraFields.TRANSACTIONAL_TOKEN)).contains("a-session");
    assertThat(body.text(AutraFields.DOCUMENT_ID)).contains(DOCUMENT);
    assertThat(body.number(AutraFields.AMOUNT)).contains("99.90");
    assertThat(body.text(AutraFields.CURRENCY)).contains("BRL");
    assertThat(body.has(AutraFields.CARD)).isFalse();
    assertThat(body.has(AutraFields.TRANSACTION_TYPE)).isFalse();
    assertThat(body.has(AutraFields.INSTALLMENTS)).isFalse();
  }

  @Test
  void
      a_pix_started_with_a_description_and_an_order_carries_both_and_one_without_carries_neither() {
    AutraJson.JsonObject both =
        AutraOrigination.pixCallOf(
                pixOrigination("99.90", "Pedido #1234", "pedido-1"), MERCHANT, "a-session")
            .body()
            .orElseThrow();
    AutraJson.JsonObject neither =
        AutraOrigination.pixCallOf(pixOrigination("99.90", null, null), MERCHANT, "a-session")
            .body()
            .orElseThrow();

    assertThat(both.text(AutraFields.DESCRIPTION)).contains("Pedido #1234");
    assertThat(both.text(AutraFields.ORDER_ID)).contains("pedido-1");
    assertThat(neither.has(AutraFields.DESCRIPTION)).isFalse();
    assertThat(neither.has(AutraFields.ORDER_ID)).isFalse();
  }

  @Test
  void a_capture_asked_for_on_a_screen_is_posted_to_the_capture_route_of_that_payment() {
    AutraApiCall call =
        AutraObjectActions.callOf(action(ObjectAction.CAPTURE, "480.00"), aHold(), "a-session");

    assertThat(call.path()).isEqualTo(AutraApiController.PAYMENTS + "/" + PAYMENT_ID + "/capture");
  }

  @Test
  void a_cancellation_and_a_refund_asked_for_on_a_screen_are_posted_to_the_void_route() {
    assertThat(
            AutraObjectActions.callOf(action(ObjectAction.CANCEL, null), aHold(), "a-session")
                .path())
        .isEqualTo(AutraApiController.PAYMENTS + "/" + PAYMENT_ID + "/void");
    assertThat(
            AutraObjectActions.callOf(action(ObjectAction.REFUND, "200.00"), aHold(), "a-session")
                .path())
        .isEqualTo(AutraApiController.PAYMENTS + "/" + PAYMENT_ID + "/void");
  }

  @Test
  void a_capture_asked_for_on_a_screen_sends_no_currency_because_the_capture_schema_names_none() {
    AutraJson.JsonObject body =
        AutraObjectActions.callOf(action(ObjectAction.CAPTURE, "480.00"), aHold(), "a-session")
            .body()
            .orElseThrow();

    assertThat(body.text(AutraFields.TRANSACTIONAL_TOKEN)).contains("a-session");
    assertThat(body.text(AutraFields.DOCUMENT_ID)).contains(DOCUMENT);
    assertThat(body.number(AutraFields.AMOUNT)).contains("480.00");
    assertThat(body.has(AutraFields.CURRENCY)).isFalse();
  }

  @Test
  void a_void_asked_for_on_a_screen_sends_the_currency_the_void_schema_carries_as_optional() {
    AutraJson.JsonObject body =
        AutraObjectActions.callOf(action(ObjectAction.REFUND, "200.00"), aHold(), "a-session")
            .body()
            .orElseThrow();

    assertThat(body.text(AutraFields.CURRENCY)).contains("BRL");
    assertThat(body.number(AutraFields.AMOUNT)).contains("200.00");
  }

  @Test
  void a_cancellation_asked_for_on_a_screen_sends_the_whole_amount_the_reservation_still_holds() {
    AutraJson.JsonObject body =
        AutraObjectActions.callOf(action(ObjectAction.CANCEL, null), aHold(), "a-session")
            .body()
            .orElseThrow();

    assertThat(body.number(AutraFields.AMOUNT)).contains("500.00");
  }

  private static OriginationRequest origination(String amount, String orderId) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AutraFields.AMOUNT, amount);
    values.put(AutraFields.CURRENCY, "BRL");
    if (orderId != null) {
      values.put(AutraFields.ORDER_ID, orderId);
    }
    return OriginationRequest.of(
        RUN, ACCOUNT, AutraOrigination.A_HOLD, values, ObjectOrigin.CONTROL_API);
  }

  private static OriginationRequest saleOrigination(String amount, String orderId) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AutraFields.AMOUNT, amount);
    values.put(AutraFields.CURRENCY, "BRL");
    values.put(AutraFields.TRANSACTION_TYPE, AutraFields.CREDIT);
    values.put(AutraFields.INSTALLMENTS, "1");
    values.put(AutraFields.SLUG_TOKEN, "slug-token-abc");
    if (orderId != null) {
      values.put(AutraFields.ORDER_ID, orderId);
    }
    return OriginationRequest.of(
        RUN, ACCOUNT, AutraOrigination.A_SALE, values, ObjectOrigin.CONTROL_API);
  }

  private static OriginationRequest pixOrigination(
      String amount, String description, String orderId) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AutraFields.AMOUNT, amount);
    values.put(AutraFields.CURRENCY, "BRL");
    if (description != null) {
      values.put(AutraFields.DESCRIPTION, description);
    }
    if (orderId != null) {
      values.put(AutraFields.ORDER_ID, orderId);
    }
    return OriginationRequest.of(
        RUN, ACCOUNT, AutraOrigination.A_PIX, values, ObjectOrigin.CONTROL_API);
  }

  private static ObjectActionRequest action(ObjectAction action, String amount) {
    return ObjectActionRequest.of(RUN, PAYMENT_ID, action, amount, ObjectOrigin.CONTROL_API);
  }

  private static AutraPayment aHold() {
    return new AutraPayment(
        RUN,
        PAYMENT_ID,
        ACCOUNT,
        DOCUMENT,
        null,
        AutraTimeline.PREAUTH,
        AutraFields.CREDIT,
        Amount.of("500.00"),
        "BRL",
        1,
        AutraStatus.ACCEPTED,
        "A1B2C3",
        "112676199769",
        "900123456789",
        null,
        null,
        Ledger.empty()
            .with(
                new MonetaryOperation(
                    1,
                    OperationKind.AUTHORIZATION,
                    Amount.of("500.00"),
                    Boolean.FALSE,
                    null,
                    AutraStatus.ACCEPTED,
                    NormalizedStatus.READY_FOR_CAPTURE,
                    NOW)),
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  @Test
  void an_amount_a_tester_typed_that_is_no_number_still_reaches_autra_as_a_body_it_can_read() {
    AutraJson.JsonObject pix =
        AutraOrigination.pixCallOf(pixOrigination("abc", null, null), MERCHANT, "a-session")
            .body()
            .orElseThrow();
    AutraJson.JsonObject hold =
        AutraOrigination.callOf(origination("abc", null), MERCHANT, "a-session")
            .body()
            .orElseThrow();
    AutraJson.JsonObject sale =
        AutraOrigination.saleCallOf(saleOrigination("abc", null), MERCHANT, "a-session")
            .body()
            .orElseThrow();

    for (AutraJson.JsonObject body : List.of(pix, hold, sale)) {
      assertThat(body.text(AutraFields.AMOUNT))
          .as(
              "a value written into a JSON number position unquoted malformed the whole body, and"
                  + " autra then answered INVALID_JSON about a body this simulator broke rather"
                  + " than the field refusal the screen is written to draw")
          .contains("abc");
      assertThat(body.number(AutraFields.AMOUNT)).isEmpty();
    }
  }

  @Test
  void an_instalment_count_a_tester_typed_that_is_no_number_reaches_autra_as_a_readable_body() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(AutraFields.AMOUNT, "150.00");
    values.put(AutraFields.CURRENCY, "BRL");
    values.put(AutraFields.TRANSACTION_TYPE, AutraFields.CREDIT);
    values.put(AutraFields.INSTALLMENTS, "two");
    values.put(AutraFields.SLUG_TOKEN, "slug-token-abc");

    AutraJson.JsonObject body =
        AutraOrigination.saleCallOf(
                OriginationRequest.of(
                    RUN, ACCOUNT, AutraOrigination.A_SALE, values, ObjectOrigin.CONTROL_API),
                MERCHANT,
                "a-session")
            .body()
            .orElseThrow();

    assertThat(body.text(AutraFields.INSTALLMENTS)).contains("two");
    assertThat(PreAuthorize.theInstallmentsIn(body))
        .as(
            "an instalment count autra cannot read as a whole number is out of the one-to-twelve"
                + " range it publishes, and never an absent field that defaults to one")
        .isEqualTo(Integer.valueOf(0));
  }

  @Test
  void an_amount_a_tester_typed_on_a_capture_reaches_autra_as_a_body_it_can_read() {
    AutraJson.JsonObject body =
        AutraObjectActions.callOf(action(ObjectAction.CAPTURE, "abc"), aHold(), "a-session")
            .body()
            .orElseThrow();

    assertThat(body.text(AutraFields.AMOUNT)).contains("abc");
    assertThat(AutraAmounts.isAboveZero("abc")).isFalse();
  }
}
