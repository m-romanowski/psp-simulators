package dev.marcinromanowski.pspsim.kernel.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerReleasesTest {

  @Test
  void a_release_reduces_only_the_authorization_and_never_looks_like_collected_or_refunded_money() {
    Ledger held =
        new Ledger(
            List.of(
                operation(1, OperationKind.AUTHORIZATION, "20.00"),
                operation(2, OperationKind.RELEASE, "5.00")));

    assertThat(held.balances(true))
        .contains(
            new DerivedBalances(
                Amount.of("0"), Amount.of("0"), Amount.of("15.00"), Amount.of("0")));

    Ledger completed = held.with(operation(3, OperationKind.CAPTURE, "15.00"));

    assertThat(completed.balances(true))
        .contains(
            new DerivedBalances(
                Amount.of("15.00"), Amount.of("0"), Amount.of("0.00"), Amount.of("15.00")));
  }

  @Test
  void admits_a_release_of_exactly_what_the_authorization_still_holds_after_a_capture() {
    Ledger released =
        new Ledger(
            List.of(
                operation(1, OperationKind.AUTHORIZATION, "20.00"),
                operation(2, OperationKind.CAPTURE, "5.00"),
                operation(3, OperationKind.RELEASE, "10.00"),
                operation(4, OperationKind.RELEASE, "5.00")));

    assertThat(released.capturableRemainder(true)).contains(new BigDecimal("0.00"));
  }

  @Test
  void refuses_a_release_of_more_than_the_authorization_still_holds_uncaptured_and_unreleased() {
    Ledger held =
        new Ledger(
            List.of(
                operation(1, OperationKind.AUTHORIZATION, "20.00"),
                operation(2, OperationKind.CAPTURE, "5.00"),
                operation(3, OperationKind.RELEASE, "10.00")));

    assertThatThrownBy(() -> held.with(operation(4, OperationKind.RELEASE, "5.01")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("release more than its authorization still holds");
  }

  @Test
  void refuses_a_release_that_no_authorization_precedes() {
    List<MonetaryOperation> unheld =
        List.of(
            operation(1, OperationKind.RELEASE, "5.00"),
            operation(2, OperationKind.AUTHORIZATION, "20.00"));

    assertThatThrownBy(() -> new Ledger(unheld))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("release more than its authorization still holds");
  }

  @Test
  void leaves_a_release_whose_amounts_are_not_numbers_to_the_provider_that_wrote_them() {
    Ledger unreadable =
        new Ledger(
            List.of(
                operation(1, OperationKind.AUTHORIZATION, "five"),
                operation(2, OperationKind.RELEASE, "seven")));

    assertThat(unreadable.capturableRemainder(true)).isEmpty();
  }

  private MonetaryOperation operation(int sequence, OperationKind kind, String amount) {
    return new MonetaryOperation(
        sequence,
        kind,
        Amount.of(amount),
        null,
        null,
        "0/00",
        NormalizedStatus.READY_FOR_CAPTURE,
        Instant.parse("2026-10-22T12:00:00Z"));
  }
}
