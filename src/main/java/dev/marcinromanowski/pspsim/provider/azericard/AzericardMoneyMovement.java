package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import org.jspecify.annotations.Nullable;

enum AzericardMoneyMovement {
  HOLDS_THE_FUNDS(OperationKind.AUTHORIZATION, NormalizedStatus.READY_FOR_CAPTURE, null),

  TAKES_THE_FUNDS_IN_ONE_CALL(OperationKind.CAPTURE, NormalizedStatus.SUCCEEDED, Boolean.TRUE),

  COMPLETES_A_HOLD(OperationKind.CAPTURE, NormalizedStatus.SUCCEEDED, null),

  RETURNS_THE_FUNDS(OperationKind.REFUND, NormalizedStatus.REFUNDED, null);

  private final OperationKind writes;
  private final NormalizedStatus whenApproved;
  private final @Nullable Boolean closesAuthorization;

  AzericardMoneyMovement(
      OperationKind writes, NormalizedStatus whenApproved, @Nullable Boolean closesAuthorization) {
    this.writes = writes;
    this.whenApproved = whenApproved;
    this.closesAuthorization = closesAuthorization;
  }

  OperationKind writes() {
    return writes;
  }

  NormalizedStatus whenApproved() {
    return whenApproved;
  }

  @Nullable Boolean closesAuthorization() {
    return closesAuthorization;
  }
}
