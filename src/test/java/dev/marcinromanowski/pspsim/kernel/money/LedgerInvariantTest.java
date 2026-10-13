package dev.marcinromanowski.pspsim.kernel.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerInvariantTest {

  private static final Instant AT = Instant.parse("2026-09-10T12:00:00Z");

  @Test
  void refuses_a_ledger_that_returned_more_than_it_ever_captured() {
    List<MonetaryOperation> impossible =
        List.of(
            operation(1, OperationKind.CAPTURE, "5.00"),
            operation(2, OperationKind.REFUND, "7.00"));

    assertThatThrownBy(() -> new Ledger(impossible))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("more than it captured");
  }

  @Test
  void refuses_a_refund_appended_past_the_remainder_of_a_ledger_that_was_sound() {
    Ledger sound = Ledger.empty().with(operation(1, OperationKind.CAPTURE, "5.00"));
    MonetaryOperation excessive = operation(2, OperationKind.REFUND, "5.01");

    assertThatThrownBy(() -> sound.with(excessive)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void admits_a_ledger_refunded_down_to_exactly_nothing_and_calls_it_a_full_refund() {
    Ledger settled =
        new Ledger(
            List.of(
                operation(1, OperationKind.CAPTURE, "5.00"),
                operation(2, OperationKind.REFUND, "2.00"),
                operation(3, OperationKind.REFUND, "3.00")));

    assertThat(settled.refundExtent()).isEqualTo(Ledger.RefundExtent.FULL);
    assertThat(settled.refundableRemainder()).contains(new BigDecimal("0.00"));
  }

  @Test
  void leaves_a_ledger_whose_amounts_are_not_numbers_to_the_provider_that_wrote_them() {
    Ledger unreadable =
        new Ledger(
            List.of(
                operation(1, OperationKind.CAPTURE, "five"),
                operation(2, OperationKind.REFUND, "seven")));

    assertThat(unreadable.refundableRemainder()).isEmpty();
  }

  @Test
  void leaves_a_hold_that_captured_nothing_alone_because_it_returned_nothing_either() {
    Ledger held = new Ledger(List.of(operation(1, OperationKind.AUTHORIZATION, "11.48")));

    assertThat(held.refundableRemainder()).contains(BigDecimal.ZERO);
    assertThat(held.capturableRemainder(true)).contains(new BigDecimal("11.48"));
  }

  private static MonetaryOperation operation(int sequence, OperationKind kind, String amount) {
    return new MonetaryOperation(
        sequence, kind, Amount.of(amount), null, null, "0/00", NormalizedStatus.SUCCEEDED, AT);
  }
}
