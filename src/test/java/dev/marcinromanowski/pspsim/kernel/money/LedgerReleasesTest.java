package dev.marcinromanowski.pspsim.kernel.money;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerReleasesTest {

  @Test
  void a_release_reduces_only_the_authorization_and_never_looks_like_collected_or_refunded_money() {
    Ledger held = new Ledger(
        List.of(
            operation(1, OperationKind.AUTHORIZATION, "20.00"),
            operation(2, OperationKind.RELEASE, "5.00")
        )
    );
    assertThat(held.capturableRemainder(true))
        .contains(new BigDecimal("15.00"));
    assertThat(held.refundableRemainder())
        .contains(BigDecimal.ZERO);
    assertThat(held.holds(OperationKind.CAPTURE))
        .isFalse();
    assertThat(held.holds(OperationKind.REFUND))
        .isFalse();

    Ledger completed = held.with(operation(3, OperationKind.CAPTURE, "15.00"));
    assertThat(completed.capturableRemainder(true))
        .contains(new BigDecimal("0.00"));
    assertThat(completed.refundableRemainder())
        .contains(new BigDecimal("15.00"));
  }

  private MonetaryOperation operation(int sequence, OperationKind kind, String amount) {
    return new MonetaryOperation(
        sequence,
        kind,
        Amount.of(amount),
        false,
        null,
        "0/00",
        NormalizedStatus.READY_FOR_CAPTURE,
        Instant.parse("2026-10-22T12:00:00Z")
    );
  }
}
