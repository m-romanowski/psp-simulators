package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TranzilaPaymentTest {

  private static final Instant AT = TranzilaWiring.NOW;

  private static final Instant LATER = AT.plusSeconds(60);

  private static final TranzilaCard CARD = TranzilaCard.ofThePan("4580210000004245", 7, 2030);

  @Test
  void an_approved_debit_succeeded_charged_its_amount_and_takes_credits() {
    TranzilaPayment payment = new TranzilaPayment(debit(1, "000"), List.of());

    assertThat(payment.normalizedStatusAt(AT)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(payment.nativeStatus()).isEqualTo("debit 000");
    assertThat(payment.refundableRemainder()).isEqualByComparingTo("100");
    assertThat(payment.refundsAccepted()).isTrue();
    assertThat(payment.isAJ5StillHeldAt(AT)).isFalse();
    assertThat(payment.ledger().operations())
        .containsExactly(
            new MonetaryOperation(
                1,
                OperationKind.AUTHORIZATION,
                Amount.of("100"),
                Boolean.TRUE,
                null,
                "debit 000",
                NormalizedStatus.SUCCEEDED,
                AT),
            new MonetaryOperation(
                2,
                OperationKind.CAPTURE,
                Amount.of("100"),
                Boolean.TRUE,
                null,
                "debit 000",
                NormalizedStatus.SUCCEEDED,
                AT));
  }

  @Test
  void a_declined_original_failed_moved_no_money_and_takes_nothing() {
    TranzilaPayment payment = new TranzilaPayment(debit(1, "004"), List.of());

    assertThat(payment.normalizedStatusAt(AT)).isEqualTo(NormalizedStatus.FAILED);
    assertThat(payment.nativeStatus()).isEqualTo("debit 004");
    assertThat(payment.ledger().operations()).isEmpty();
    assertThat(payment.refundsAccepted()).isFalse();
    assertThat(payment.whatACreditIsAgainst()).isEmpty();
  }

  @Test
  void an_approved_j2_succeeded_and_holds_nothing() {
    TranzilaPayment payment = new TranzilaPayment(verify(1, 2, null), List.of());

    assertThat(payment.normalizedStatusAt(AT)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(payment.nativeStatus()).isEqualTo("verify 2 777");
    assertThat(payment.ledger().operations()).isEmpty();
    assertThat(payment.isAJ5StillHeldAt(AT)).isFalse();
    assertThat(payment.refundsAccepted()).isFalse();
  }

  @Test
  void a_live_j5_is_ready_for_capture_until_its_validity_and_cancelled_from_then() {
    TranzilaPayment payment = new TranzilaPayment(verify(1, 5, AT.plusSeconds(10)), List.of());

    assertThat(payment.normalizedStatusAt(AT.plusSeconds(9)))
        .isEqualTo(NormalizedStatus.READY_FOR_CAPTURE);
    assertThat(payment.isAJ5StillHeldAt(AT.plusSeconds(9))).isTrue();
    assertThat(payment.normalizedStatusAt(AT.plusSeconds(10)))
        .isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(payment.isAJ5StillHeldAt(AT.plusSeconds(10))).isFalse();
    assertThat(payment.nativeStatus()).isEqualTo("verify 5 777");
    assertThat(payment.refundsAccepted()).isFalse();
    assertThat(payment.ledger().operations())
        .containsExactly(
            new MonetaryOperation(
                1,
                OperationKind.AUTHORIZATION,
                Amount.of("100"),
                Boolean.FALSE,
                null,
                "verify 5 777",
                NormalizedStatus.READY_FOR_CAPTURE,
                AT));
  }

  @Test
  void a_forced_j5_succeeded_with_its_force_captured_and_credits_measured_against_the_force() {
    TranzilaTransaction force = followOn(2, 1, 1, TranzilaTxnType.FORCE, "60", "000", LATER);
    TranzilaTransaction credit =
        followOn(3, 1, 2, TranzilaTxnType.CREDIT, "20", "000", LATER.plusSeconds(1));
    TranzilaPayment forced =
        new TranzilaPayment(verify(1, 5, AT.plus(Duration.ofDays(7))), List.of(force));
    TranzilaPayment credited =
        new TranzilaPayment(verify(1, 5, AT.plus(Duration.ofDays(7))), List.of(force, credit));

    assertThat(forced.normalizedStatusAt(LATER)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(forced.isAJ5StillHeldAt(LATER)).isFalse();
    assertThat(forced.nativeStatus()).isEqualTo("force 000");
    assertThat(forced.theForce()).contains(force);
    assertThat(forced.whatACreditIsAgainst()).contains(force);
    assertThat(credited.normalizedStatusAt(LATER)).isEqualTo(NormalizedStatus.PARTIALLY_REFUNDED);
    assertThat(credited.refundableRemainder()).isEqualByComparingTo("40");
    assertThat(credited.ledger().operations())
        .extracting(
            MonetaryOperation::kind,
            MonetaryOperation::amount,
            MonetaryOperation::closesAuthorization,
            MonetaryOperation::nativeStatus,
            MonetaryOperation::normalizedStatus,
            MonetaryOperation::at)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                OperationKind.AUTHORIZATION,
                Amount.of("100"),
                false,
                "verify 5 777",
                NormalizedStatus.READY_FOR_CAPTURE,
                AT),
            org.assertj.core.groups.Tuple.tuple(
                OperationKind.CAPTURE,
                Amount.of("60"),
                true,
                "force 000",
                NormalizedStatus.SUCCEEDED,
                LATER),
            org.assertj.core.groups.Tuple.tuple(
                OperationKind.REFUND,
                Amount.of("20"),
                null,
                "credit 000",
                NormalizedStatus.PARTIALLY_REFUNDED,
                LATER.plusSeconds(1)));
    assertThat(credited.updatedAt()).isEqualTo(LATER.plusSeconds(1));
  }

  @Test
  void credits_equal_to_the_original_refund_it_in_full() {
    TranzilaPayment payment =
        new TranzilaPayment(
            debit(1, "000"),
            List.of(
                followOn(2, 1, 1, TranzilaTxnType.CREDIT, "40", "000", LATER),
                followOn(3, 1, 1, TranzilaTxnType.CREDIT, "60", "000", LATER)));

    assertThat(payment.normalizedStatusAt(LATER)).isEqualTo(NormalizedStatus.REFUNDED);
    assertThat(payment.refundableRemainder()).isEqualByComparingTo("0");
    assertThat(payment.refundsAccepted()).isFalse();
    assertThat(payment.ledger().operations())
        .extracting(MonetaryOperation::normalizedStatus)
        .containsExactly(
            NormalizedStatus.SUCCEEDED,
            NormalizedStatus.SUCCEEDED,
            NormalizedStatus.PARTIALLY_REFUNDED,
            NormalizedStatus.REFUNDED);
  }

  @Test
  void a_declined_or_cancelled_credit_returns_nothing() {
    TranzilaTransaction cancelledCredit =
        followOn(3, 1, 1, TranzilaTxnType.CREDIT, "40", "000", LATER);
    TranzilaPayment payment =
        new TranzilaPayment(
            debit(1, "000"),
            List.of(
                followOn(2, 1, 1, TranzilaTxnType.CREDIT, "10", "004", LATER),
                cancelledCredit,
                followOn(4, 1, 3, TranzilaTxnType.CANCEL, "40", "000", LATER)));

    assertThat(payment.isCancelled(cancelledCredit)).isTrue();
    assertThat(payment.theCreditsAgainst(payment.root())).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(payment.normalizedStatusAt(LATER)).isEqualTo(NormalizedStatus.SUCCEEDED);
    assertThat(payment.nativeStatus()).isEqualTo("cancel 000");
    assertThat(payment.ledger().operations()).hasSize(2);
  }

  @Test
  void a_credit_of_another_transaction_is_not_counted_against_this_one() {
    TranzilaPayment payment =
        new TranzilaPayment(
            debit(1, "000"),
            List.of(followOn(2, 1, 9, TranzilaTxnType.CREDIT, "10", "000", LATER)));

    assertThat(payment.theCreditsAgainst(payment.root())).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(payment.ledger().operations()).hasSize(2);
  }

  @Test
  void a_cancelled_debit_is_cancelled_charged_nothing_and_takes_no_credit() {
    TranzilaPayment payment =
        new TranzilaPayment(
            debit(1, "000"),
            List.of(followOn(2, 1, 1, TranzilaTxnType.CANCEL, "100", "000", LATER)));

    assertThat(payment.normalizedStatusAt(LATER)).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(payment.refundableRemainder()).isEqualByComparingTo("0");
    assertThat(payment.ledger().operations())
        .extracting(MonetaryOperation::kind)
        .containsExactly(OperationKind.AUTHORIZATION);
    assertThat(payment.updatedAt()).isEqualTo(LATER);
  }

  @Test
  void a_cancel_that_was_declined_cancels_nothing() {
    TranzilaPayment payment =
        new TranzilaPayment(
            debit(1, "000"),
            List.of(followOn(2, 1, 1, TranzilaTxnType.CANCEL, "100", "004", LATER)));

    assertThat(payment.isCancelled(payment.root())).isFalse();
    assertThat(payment.nativeStatus()).isEqualTo("debit 000");
    assertThat(payment.updatedAt()).isEqualTo(AT);
  }

  @Test
  void a_reversed_j5_is_cancelled_and_holds_nothing() {
    TranzilaPayment payment =
        new TranzilaPayment(
            verify(1, 5, AT.plus(Duration.ofDays(7))),
            List.of(followOn(2, 1, 1, TranzilaTxnType.REVERSAL, "100", "000", LATER)));

    assertThat(payment.normalizedStatusAt(LATER)).isEqualTo(NormalizedStatus.CANCELLED);
    assertThat(payment.isAJ5StillHeldAt(LATER)).isFalse();
    assertThat(payment.theReversal()).isPresent();
    assertThat(payment.nativeStatus()).isEqualTo("reversal 000");
  }

  @Test
  void a_declined_force_neither_closes_the_j5_nor_charges_it() {
    TranzilaPayment payment =
        new TranzilaPayment(
            verify(1, 5, AT.plus(Duration.ofDays(7))),
            List.of(followOn(2, 1, 1, TranzilaTxnType.FORCE, "200", "507", LATER)));

    assertThat(payment.theForce()).isEmpty();
    assertThat(payment.isAJ5StillHeldAt(LATER)).isTrue();
    assertThat(payment.whatACreditIsAgainst()).isEmpty();
    assertThat(payment.ledger().operations()).hasSize(1);
  }

  @Test
  void a_payment_answers_to_its_original_and_to_every_transaction_made_on_it() {
    TranzilaPayment payment =
        new TranzilaPayment(
            debit(1, "000"),
            List.of(
                followOn(2, 1, 1, TranzilaTxnType.CREDIT, "10", "000", LATER),
                followOn(3, 1, 1, TranzilaTxnType.CREDIT, "10", "004", LATER)));

    assertThat(payment.paymentId()).isEqualTo("1");
    assertThat(payment.identity().canonical()).isEqualTo("1");
    assertThat(payment.identity().members()).containsExactly("1", "2", "3");
    assertThat(payment.transaction(3))
        .map(TranzilaTransaction::processorResponseCode)
        .contains("004");
    assertThat(payment.transaction(1)).contains(payment.root());
    assertThat(payment.transaction(4)).isEmpty();
  }

  @Test
  void transactions_are_grouped_under_their_originals_in_the_order_they_arrived() {
    TranzilaTransaction first = debit(1, "000");
    TranzilaTransaction second = debit(2, "000");
    TranzilaTransaction ofTheFirst = followOn(3, 1, 1, TranzilaTxnType.CREDIT, "1", "000", LATER);

    List<TranzilaPayment> grouped = TranzilaGroups.paymentsOf(List.of(first, second, ofTheFirst));
    List<ArrivalOf<TranzilaPayment>> ordered =
        TranzilaGroups.inTheOrderOf(
            List.of(new ArrivalOf<>(20L, 2L), new ArrivalOf<>(10L, 1L), new ArrivalOf<>(5L, 7L)),
            List.of(first, second, ofTheFirst));

    assertThat(grouped)
        .containsExactly(
            new TranzilaPayment(first, List.of(ofTheFirst)),
            new TranzilaPayment(second, List.of()));
    assertThat(ordered)
        .extracting(ArrivalOf::arrival, arrived -> arrived.row().paymentId())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(20L, "2"),
            org.assertj.core.groups.Tuple.tuple(10L, "1"));
    assertThat(TranzilaGroups.theOnePaymentOf(List.of())).isEmpty();
  }

  @Test
  void a_transaction_names_its_status_as_type_mode_and_code() {
    assertThat(debit(1, "000").nativeStatus()).isEqualTo("debit 000");
    assertThat(verify(1, 5, LATER).nativeStatus()).isEqualTo("verify 5 777");
    assertThat(verify(1, 5, LATER).isAJ5()).isTrue();
    assertThat(verify(1, 5, LATER).isAJ2()).isFalse();
    assertThat(verify(1, 2, null).isAJ2()).isTrue();
    assertThat(debit(1, "000").isAJ5()).isFalse();
    assertThat(debit(1, "000").isAJ2()).isFalse();
    assertThat(debit(1, "000").isTheOriginal()).isTrue();
    assertThat(followOn(2, 1, 1, TranzilaTxnType.CANCEL, "1", "000", AT).isTheOriginal()).isFalse();
    assertThat(debit(1, "000").references(1)).isFalse();
    assertThat(followOn(2, 1, 1, TranzilaTxnType.CANCEL, "1", "000", AT).references(1)).isTrue();
    assertThat(followOn(2, 1, 1, TranzilaTxnType.CANCEL, "1", "000", AT).references(2)).isFalse();
    assertThat(debit(1, "000").isLiveAt(AT)).isFalse();
  }

  @Test
  void every_type_answers_the_tranmode_the_lookups_publish_for_it() {
    assertThat(TranzilaTxnType.DEBIT.tranmodeOf(null)).contains("A");
    assertThat(TranzilaTxnType.VERIFY.tranmodeOf(5)).contains("V");
    assertThat(TranzilaTxnType.VERIFY.tranmodeOf(2)).contains("N");
    assertThat(TranzilaTxnType.FORCE.tranmodeOf(null)).contains("F");
    assertThat(TranzilaTxnType.CREDIT.tranmodeOf(null)).contains("C");
    assertThat(TranzilaTxnType.CANCEL.tranmodeOf(null)).contains("D");
    assertThat(TranzilaTxnType.REVERSAL.tranmodeOf(null)).isEmpty();
    assertThat(TranzilaTxnType.of("reversal")).contains(TranzilaTxnType.REVERSAL);
    assertThat(TranzilaTxnType.of("sto")).isEmpty();
    assertThat(TranzilaStoredForm.typeOf("cancel")).isEqualTo(TranzilaTxnType.CANCEL);
  }

  @Test
  void what_each_type_carries_is_what_the_schema_requires_of_it() {
    assertThat(TranzilaTxnType.values())
        .filteredOn(TranzilaTxnType::carriesACard)
        .containsExactly(
            TranzilaTxnType.DEBIT,
            TranzilaTxnType.VERIFY,
            TranzilaTxnType.FORCE,
            TranzilaTxnType.CREDIT);
    assertThat(TranzilaTxnType.values())
        .filteredOn(TranzilaTxnType::carriesAnAmount)
        .containsExactly(
            TranzilaTxnType.DEBIT,
            TranzilaTxnType.VERIFY,
            TranzilaTxnType.FORCE,
            TranzilaTxnType.CREDIT);
    assertThat(TranzilaTxnType.values())
        .filteredOn(TranzilaTxnType::requiresTheAuthorizationNumber)
        .containsExactly(TranzilaTxnType.REVERSAL, TranzilaTxnType.CREDIT, TranzilaTxnType.CANCEL);
    assertThat(TranzilaTxnType.values())
        .filteredOn(TranzilaTxnType::isAnOriginal)
        .containsExactly(TranzilaTxnType.DEBIT, TranzilaTxnType.VERIFY);
  }

  static TranzilaTransaction debit(long id, String code) {
    return new TranzilaTransaction(
        TranzilaWiring.RUN,
        id,
        id,
        TranzilaWiring.ACCOUNT,
        TranzilaWiring.TERMINAL,
        TranzilaTxnType.DEBIT,
        null,
        null,
        code,
        "100",
        "ILS",
        TranzilaResponseCodes.approves(code) ? "1000001" : null,
        "t000000000000014245",
        CARD,
        "0",
        "0",
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  static TranzilaTransaction verify(long id, int mode, @Nullable Instant expires) {
    return new TranzilaTransaction(
        TranzilaWiring.RUN,
        id,
        id,
        TranzilaWiring.ACCOUNT,
        TranzilaWiring.TERMINAL,
        TranzilaTxnType.VERIFY,
        mode,
        null,
        "777",
        "100",
        "ILS",
        "1000001",
        "t000000000000014245",
        CARD,
        "0",
        "0",
        null,
        expires,
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  static TranzilaTransaction followOn(
      long id,
      long root,
      long reference,
      TranzilaTxnType type,
      String amount,
      String code,
      Instant at) {
    return new TranzilaTransaction(
        TranzilaWiring.RUN,
        id,
        root,
        TranzilaWiring.ACCOUNT,
        TranzilaWiring.TERMINAL,
        type,
        null,
        reference,
        code,
        amount,
        "ILS",
        TranzilaResponseCodes.approves(code) ? "1000002" : null,
        null,
        CARD,
        "0",
        "0",
        null,
        null,
        ObjectOrigin.PROVIDER_API,
        at);
  }
}
