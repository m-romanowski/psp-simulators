package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

enum AzericardTransactionType {
  PRE_AUTHORIZATION(
      "0", AzericardRequestShape.AUTHORIZATION, AzericardMoneyMovement.HOLDS_THE_FUNDS),
  AUTHORIZATION(
      "1", AzericardRequestShape.AUTHORIZATION, AzericardMoneyMovement.TAKES_THE_FUNDS_IN_ONE_CALL),
  SALES_COMPLETION("21", AzericardRequestShape.SETTLEMENT, AzericardMoneyMovement.COMPLETES_A_HOLD),
  ONLINE_REVERSAL("22", AzericardRequestShape.SETTLEMENT, AzericardMoneyMovement.RETURNS_THE_FUNDS),
  OFFLINE_REVERSAL(
      "24", AzericardRequestShape.SETTLEMENT, AzericardMoneyMovement.RETURNS_THE_FUNDS),
  TRANSACTION_STATUS("90", AzericardRequestShape.STATUS, null);

  static final String MOVES_NOTHING =
      " asks provider azericard what became of a payment and moves no money, so it has no"
          + " operation kind, no approved state and no authorization to close";

  private final String wireValue;
  private final AzericardRequestShape shape;
  private final @Nullable AzericardMoneyMovement movement;

  AzericardTransactionType(
      String wireValue, AzericardRequestShape shape, @Nullable AzericardMoneyMovement movement) {
    this.wireValue = wireValue;
    this.shape = shape;
    this.movement = movement;
  }

  String wireValue() {
    return wireValue;
  }

  String operationName() {
    return AzericardFields.TRTYPE + "=" + wireValue;
  }

  AzericardRequestShape shape() {
    return shape;
  }

  AzericardMoneyMovement movement() {
    AzericardMoneyMovement moved = movement;
    if (moved == null) {
      throw new IllegalStateException(operationName() + MOVES_NOTHING);
    }
    return moved;
  }

  boolean createsThePayment() {
    return shape == AzericardRequestShape.AUTHORIZATION;
  }

  boolean looksUpThePayment() {
    return shape == AzericardRequestShape.STATUS;
  }

  boolean reversesThePayment() {
    AzericardMoneyMovement moved = movement;
    return moved != null && moved.writes() == OperationKind.REFUND;
  }

  static Optional<AzericardTransactionType> of(String wireValue) {
    for (AzericardTransactionType type : values()) {
      if (type.wireValue.equals(wireValue)) {
        return Optional.of(type);
      }
    }
    return Optional.empty();
  }

  static Optional<AzericardTransactionType> storedType(String wireValue) {
    return of(wireValue).filter(AzericardTransactionType::createsThePayment);
  }
}
