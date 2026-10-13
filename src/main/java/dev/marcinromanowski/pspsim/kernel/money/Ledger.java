package dev.marcinromanowski.pspsim.kernel.money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record Ledger(List<MonetaryOperation> operations) {

  public Ledger {
    operations = List.copyOf(operations);
    refuseMoreRefundedThanWasEverCaptured(operations);
  }

  private static void refuseMoreRefundedThanWasEverCaptured(List<MonetaryOperation> operations) {
    BigDecimal captured = BigDecimal.ZERO;
    BigDecimal refunded = BigDecimal.ZERO;
    for (MonetaryOperation operation : operations) {
      Optional<BigDecimal> amount = operation.amount().number();
      if (amount.isEmpty()) {
        return;
      }
      if (operation.kind() == OperationKind.CAPTURE) {
        captured = captured.add(amount.get());
      } else if (operation.kind() == OperationKind.REFUND) {
        refunded = refunded.add(amount.get());
      }
    }
    if (refunded.compareTo(captured) > 0) {
      throw new IllegalArgumentException(
          "a ledger cannot return more than it captured: its refund operations exceed its capture"
              + " operations, which no provider state can mean and which would be reported as a"
              + " full refund by the extent this ledger answers with");
    }
  }

  public static Ledger empty() {
    return new Ledger(List.of());
  }

  public Ledger with(MonetaryOperation operation) {
    List<MonetaryOperation> appended = new ArrayList<>(operations);
    appended.add(operation);
    return new Ledger(appended);
  }

  public int nextSequence() {
    return operations.size() + 1;
  }

  public int count(OperationKind kind) {
    return (int) operations.stream().filter(operation -> operation.kind() == kind).count();
  }

  public boolean holds(OperationKind kind) {
    return count(kind) > 0;
  }

  public RefundExtent refundExtent() {
    Optional<BigDecimal> remaining = refundableRemainder();
    if (!holds(OperationKind.REFUND) || remaining.isEmpty()) {
      return RefundExtent.NONE;
    }
    return remaining.get().signum() > 0 ? RefundExtent.PARTIAL : RefundExtent.FULL;
  }

  public enum RefundExtent {
    NONE,
    PARTIAL,
    FULL
  }

  public Optional<BigDecimal> refundableRemainder() {
    return totals().map(Totals::refundable);
  }

  public Optional<DerivedBalances> balances(boolean authorizationStillOpen) {
    return totals()
        .map(
            totals ->
                new DerivedBalances(
                    Amount.of(totals.captured()),
                    Amount.of(totals.refunded()),
                    Amount.of(capturableIn(totals, authorizationStillOpen)),
                    Amount.of(totals.refundable())));
  }

  public Optional<BigDecimal> capturableRemainder(boolean authorizationStillOpen) {
    return totals().map(totals -> capturableIn(totals, authorizationStillOpen));
  }

  private BigDecimal capturableIn(Totals totals, boolean authorizationStillOpen) {
    return closesAnAuthorization() || !authorizationStillOpen
        ? BigDecimal.ZERO
        : totals.authorized().subtract(totals.captured());
  }

  private boolean closesAnAuthorization() {
    return operations.stream()
        .anyMatch(operation -> Boolean.TRUE.equals(operation.closesAuthorization()));
  }

  private Optional<Totals> totals() {
    BigDecimal authorized = BigDecimal.ZERO;
    BigDecimal captured = BigDecimal.ZERO;
    BigDecimal refunded = BigDecimal.ZERO;
    for (MonetaryOperation operation : operations) {
      Optional<BigDecimal> amount = operation.amount().number();
      if (amount.isEmpty()) {
        return Optional.empty();
      }
      switch (operation.kind()) {
        case AUTHORIZATION -> authorized = authorized.add(amount.get());
        case CAPTURE -> captured = captured.add(amount.get());
        case REFUND -> refunded = refunded.add(amount.get());
      }
    }
    return Optional.of(new Totals(authorized, captured, refunded));
  }

  private record Totals(BigDecimal authorized, BigDecimal captured, BigDecimal refunded) {

    BigDecimal refundable() {
      return captured.subtract(refunded);
    }
  }
}
