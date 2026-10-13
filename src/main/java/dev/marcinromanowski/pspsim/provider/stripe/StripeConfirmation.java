package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import java.time.Instant;

final class StripeConfirmation {

  private StripeConfirmation() {}

  static MonetaryOperation operationOf(
      int sequence, StripeCaptureMethod captureMethod, Amount amount, Instant at) {
    StripeIntentStatus reached = captureMethod.afterASuccessfulConfirmation();
    if (captureMethod.holdsTheFundsForALaterCapture()) {
      return new MonetaryOperation(
          sequence,
          OperationKind.AUTHORIZATION,
          amount,
          null,
          null,
          reached.wireValue(),
          reached.normalized(),
          at);
    }
    return new MonetaryOperation(
        sequence,
        OperationKind.CAPTURE,
        amount,
        Boolean.TRUE,
        null,
        reached.wireValue(),
        reached.normalized(),
        at);
  }
}
