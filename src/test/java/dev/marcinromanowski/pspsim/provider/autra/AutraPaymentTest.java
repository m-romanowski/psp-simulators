package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AutraPaymentTest {

  private static final TestRunId RUN = TestRunId.of("autra-payment");
  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

  @Test
  void a_status_this_contract_produces_no_flow_for_is_reported_as_unknown_rather_than_refused() {
    assertThat(paymentAt("SETTLED", Ledger.empty(), null).normalizedStatus())
        .isEqualTo(NormalizedStatus.UNKNOWN);
    assertThat(paymentAt("CHARGEBACK_OPENED", Ledger.empty(), null).normalizedStatus())
        .isEqualTo(NormalizedStatus.UNKNOWN);
  }

  @Test
  void a_voided_payment_that_captured_money_reads_refunded_and_one_that_captured_none_cancelled() {
    Ledger captured =
        Ledger.empty()
            .with(
                new MonetaryOperation(
                    1,
                    OperationKind.CAPTURE,
                    Amount.of("500.00"),
                    Boolean.TRUE,
                    null,
                    AutraStatus.CAPTURED,
                    NormalizedStatus.SUCCEEDED,
                    NOW))
            .with(
                new MonetaryOperation(
                    2,
                    OperationKind.REFUND,
                    Amount.of("500.00"),
                    null,
                    null,
                    AutraStatus.VOIDED,
                    NormalizedStatus.REFUNDED,
                    NOW));

    assertThat(paymentAt(AutraStatus.VOIDED, captured, null).normalizedStatus())
        .isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(
            paymentAt(AutraTimeline.PREAUTH, AutraStatus.VOIDED, aReservationOf("500.00"), NOW)
                .normalizedStatus())
        .isEqualTo(NormalizedStatus.CANCELLED);
  }

  @Test
  void a_reservation_a_release_has_closed_is_no_longer_a_hold_nothing_has_closed() {
    assertThat(
            paymentAt(AutraTimeline.PREAUTH, AutraStatus.ACCEPTED, aReservationOf("500.00"), null)
                .isAHoldNothingHasClosed())
        .isTrue();
    assertThat(
            paymentAt(AutraTimeline.PREAUTH, AutraStatus.ACCEPTED, aReservationOf("500.00"), NOW)
                .isAHoldNothingHasClosed())
        .isFalse();
  }

  @Test
  void a_sale_the_acquirer_accepted_is_no_hold_although_it_answers_what_a_hold_answers() {
    AutraPayment sale =
        paymentAt(AutraTimeline.SALE, AutraStatus.ACCEPTED, aCaptureOf("500.00"), null);

    assertThat(sale.nativeStatus()).isEqualTo(AutraStatus.ACCEPTED);
    assertThat(sale.isAHoldNothingHasClosed()).isFalse();
    assertThat(sale.isASaleTheAcquirerAccepted()).isTrue();
    assertThat(sale.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void a_reservation_that_answers_what_a_sale_answers_is_a_hold_and_reads_ready_for_capture() {
    AutraPayment held =
        paymentAt(AutraTimeline.PREAUTH, AutraStatus.ACCEPTED, aReservationOf("500.00"), null);

    assertThat(held.nativeStatus()).isEqualTo(AutraStatus.ACCEPTED);
    assertThat(held.isAHoldNothingHasClosed()).isTrue();
    assertThat(held.isASaleTheAcquirerAccepted()).isFalse();
    assertThat(held.normalizedStatus()).isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
  }

  @Test
  void a_reservation_a_capture_has_taken_is_no_longer_a_hold_nothing_has_closed() {
    AutraPayment taken =
        paymentAt(
            AutraTimeline.PREAUTH,
            AutraStatus.CAPTURED,
            aReservationOf("500.00").with(theCaptureOf("500.00")),
            null);

    assertThat(taken.isAHoldNothingHasClosed()).isFalse();
    assertThat(taken.normalizedStatus()).isEqualTo(NormalizedStatus.SUCCEEDED);
  }

  @Test
  void the_sale_route_refuses_another_order_under_the_two_statuses_it_names_and_under_no_other() {
    assertThat(
            paymentAt(AutraTimeline.SALE, "ACCEPTED", aCaptureOf("500.00"), null)
                .isAtAStatusTheSaleRouteRefusesAnotherOrderUnder())
        .isTrue();
    assertThat(
            paymentAt(
                    AutraTimeline.PREAUTH,
                    "CAPTURED",
                    aReservationOf("500.00").with(theCaptureOf("500.00")),
                    null)
                .isAtAStatusTheSaleRouteRefusesAnotherOrderUnder())
        .isTrue();
    assertThat(
            paymentAt(AutraTimeline.PREAUTH, "ACCEPTED", aReservationOf("500.00"), null)
                .isAtAStatusTheSaleRouteRefusesAnotherOrderUnder())
        .as(
            "the rule this answers is keyed on the status and on nothing else: \"Se um pagamento"
                + " com o mesmo orderId ja estiver ACCEPTED ou CAPTURED, retorna 409 sem criar um"
                + " novo debito\". A reservation answers ACCEPTED, so it blocks, and no page of"
                + " the 189 autra publishes says otherwise")
        .isTrue();
    assertThat(
            paymentAt(AutraTimeline.SALE, "ACCEPTED", Ledger.empty(), null)
                .isAtAStatusTheSaleRouteRefusesAnotherOrderUnder())
        .as(
            "a zero-dollar card validation took no money and still blocks, because the rule is"
                + " keyed on the status")
        .isTrue();
    assertThat(
            paymentAt(AutraTimeline.SALE, "DECLINED", Ledger.empty(), null)
                .isAtAStatusTheSaleRouteRefusesAnotherOrderUnder())
        .isFalse();
    assertThat(
            paymentAt(AutraTimeline.SALE, "VOIDED", aCaptureOf("500.00"), null)
                .isAtAStatusTheSaleRouteRefusesAnotherOrderUnder())
        .isFalse();
    assertThat(
            paymentAt(AutraTimeline.SALE, "PARTIALLY_VOIDED", aCaptureOf("500.00"), null)
                .isAtAStatusTheSaleRouteRefusesAnotherOrderUnder())
        .isFalse();
    assertThat(
            paymentAt(AutraTimeline.PREAUTH, "VOIDED", aReservationOf("500.00"), NOW)
                .isAtAStatusTheSaleRouteRefusesAnotherOrderUnder())
        .isFalse();
  }

  @Test
  void a_pre_authorization_the_acquirer_refused_reserved_nothing_and_is_no_hold() {
    assertThat(
            paymentAt(AutraTimeline.PREAUTH, AutraStatus.DECLINED, Ledger.empty(), null)
                .isAHoldNothingHasClosed())
        .isFalse();
  }

  @Test
  void each_case_the_vendor_names_for_an_ineligible_pre_authorization_is_said_in_its_own_words() {
    assertThat(paymentAt(AutraStatus.VOIDED, Ledger.empty(), null).whyItIsNotEligible())
        .contains("already been cancelled");
    assertThat(paymentAt(AutraStatus.CAPTURED, Ledger.empty(), null).whyItIsNotEligible())
        .contains("already been captured");
    assertThat(paymentAt(AutraStatus.PARTIALLY_VOIDED, Ledger.empty(), null).whyItIsNotEligible())
        .contains("already been captured");
    assertThat(paymentAt(AutraStatus.DECLINED, Ledger.empty(), null).whyItIsNotEligible())
        .contains("reserved nothing");
  }

  @Test
  void an_ineligible_status_the_vendor_publishes_nowhere_is_reported_with_its_open_set_warning() {
    assertThat(paymentAt("CHARGEBACK_OPENED", Ledger.empty(), null).whyItIsNotEligible())
        .contains("CHARGEBACK_OPENED")
        .contains("a lista pode crescer");
  }

  @Test
  void a_reservation_accepts_a_reversal_and_a_payment_with_nothing_left_to_return_does_not() {
    assertThat(
            paymentAt(AutraTimeline.PREAUTH, AutraStatus.ACCEPTED, aReservationOf("500.00"), null)
                .reversalsAccepted())
        .isTrue();
    assertThat(paymentAt(AutraStatus.DECLINED, Ledger.empty(), null).reversalsAccepted()).isFalse();
  }

  @Test
  void a_captured_payment_accepts_a_reversal_although_no_reservation_of_it_is_open_any_more() {
    AutraPayment captured = paymentAt(AutraStatus.CAPTURED, aCaptureOf("500.00"), null);

    assertThat(captured.isAHoldNothingHasClosed()).isFalse();
    assertThat(captured.reversalsAccepted()).isTrue();
    assertThat(captured.reversibleRemainder()).isEqualByComparingTo("500.00");
  }

  @Test
  void a_payment_charged_on_a_card_itself_reads_CARD_and_one_charged_on_a_token_reads_TOKEN() {
    assertThat(paymentInstrumentedBy(null, null).transactionMethod()).isEqualTo("CARD");
    assertThat(paymentInstrumentedBy("slug-token-abc", null).transactionMethod())
        .isEqualTo("TOKEN");
    assertThat(paymentInstrumentedBy(null, "slug-stored-card-abc").transactionMethod())
        .isEqualTo("TOKEN");
    assertThat(paymentInstrumentedBy("slug-token-abc", "slug-stored-card-abc").transactionMethod())
        .isEqualTo("TOKEN");
  }

  @Test
  void a_payment_that_returned_nothing_reads_zero_and_one_that_returned_money_reads_the_sum() {
    assertThat(paymentAt(AutraStatus.ACCEPTED, Ledger.empty(), null).refundedAmount())
        .isEqualTo("0");
    assertThat(paymentAt(AutraStatus.ACCEPTED, aCaptureOf("500.00"), null).refundedAmount())
        .isEqualTo("0");
    assertThat(
            paymentAt(
                    AutraStatus.PARTIALLY_VOIDED,
                    aCaptureOf("500.00").with(aReversalOf("200.00")),
                    null)
                .refundedAmount())
        .isEqualTo("200.00");
  }

  @Test
  void a_ledger_whose_amount_is_no_number_reads_as_having_returned_nothing() {
    Ledger unreadable =
        Ledger.empty()
            .with(
                new MonetaryOperation(
                    1,
                    OperationKind.CAPTURE,
                    Amount.of("about five hundred"),
                    Boolean.TRUE,
                    null,
                    AutraStatus.CAPTURED,
                    NormalizedStatus.SUCCEEDED,
                    NOW));

    assertThat(paymentAt(AutraStatus.ACCEPTED, unreadable, null).refundedAmount()).isEqualTo("0");
  }

  private static MonetaryOperation aReversalOf(String amount) {
    return new MonetaryOperation(
        2,
        OperationKind.REFUND,
        Amount.of(amount),
        null,
        null,
        AutraStatus.PARTIALLY_VOIDED,
        NormalizedStatus.PARTIALLY_REFUNDED,
        NOW);
  }

  private static AutraPayment paymentInstrumentedBy(String slugToken, String slugStoredCard) {
    AutraPayment charged = paymentAt(AutraStatus.ACCEPTED, Ledger.empty(), null);
    return new AutraPayment(
        charged.testRunId(),
        charged.paymentId(),
        charged.accountId(),
        charged.documentId(),
        charged.orderId(),
        charged.type(),
        charged.transactionType(),
        charged.amount(),
        charged.currency(),
        charged.installments(),
        charged.nativeStatus(),
        charged.authorizationCode(),
        charged.rrn(),
        charged.trackingNumber(),
        slugToken,
        slugStoredCard,
        charged.ledger(),
        charged.holdReleasedAt(),
        charged.origin(),
        charged.createdAt(),
        charged.updatedAt());
  }

  private static MonetaryOperation theCaptureOf(String amount) {
    return new MonetaryOperation(
        2,
        OperationKind.CAPTURE,
        Amount.of(amount),
        Boolean.TRUE,
        null,
        AutraStatus.CAPTURED,
        NormalizedStatus.SUCCEEDED,
        NOW);
  }

  private static Ledger aCaptureOf(String amount) {
    return Ledger.empty()
        .with(
            new MonetaryOperation(
                1,
                OperationKind.CAPTURE,
                Amount.of(amount),
                Boolean.TRUE,
                null,
                AutraStatus.CAPTURED,
                NormalizedStatus.SUCCEEDED,
                NOW));
  }

  private static Ledger aReservationOf(String amount) {
    return Ledger.empty()
        .with(
            new MonetaryOperation(
                1,
                OperationKind.AUTHORIZATION,
                Amount.of(amount),
                Boolean.FALSE,
                null,
                AutraStatus.ACCEPTED,
                NormalizedStatus.READY_FOR_CAPTURE,
                NOW));
  }

  private static AutraPayment paymentAt(String nativeStatus, Ledger ledger, Instant released) {
    return paymentAt(AutraTimeline.SALE, nativeStatus, ledger, released);
  }

  private static AutraPayment paymentAt(
      String type, String nativeStatus, Ledger ledger, Instant released) {
    return new AutraPayment(
        RUN,
        "550e8400-e29b-41d4-a716-446655440000",
        "merchant-1",
        "25055662000190",
        null,
        type,
        AutraFields.CREDIT,
        Amount.of("500.00"),
        "BRL",
        1,
        nativeStatus,
        "A1B2C3",
        "112676199769",
        "900123456789",
        null,
        null,
        ledger,
        released,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }
}
