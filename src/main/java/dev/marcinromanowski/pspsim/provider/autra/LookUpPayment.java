package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Optional;

final class LookUpPayment {

  static final String A_PAYMENT_ID_IS_A_UUID =
      "paymentId is the identifier provider autra draws for a payment, which it types as a string"
          + " of format uuid in the path of this route, and this path parameter is not one";

  static final String NO_SUCH_PAYMENT =
      "paymentId names no payment of this tenant and this establishment: provider autra publishes"
          + " PAYMENT_NOT_FOUND for all three of \"inexistente, de outro tenant ou de outro"
          + " estabelecimento\" and this simulator answers the same one code for the same three";

  private final AutraPayments payments;

  LookUpPayment(AutraPayments payments) {
    this.payments = payments;
  }

  AutraOperationResult handle(
      AutraApiCall call, TestRunId testRunId, AutraMerchant merchant, String paymentId) {
    if (!AutraIdentifiers.isAUuid(paymentId)) {
      return AutraAnswers.refusedRequest(AutraErrors.INVALID_PAYMENT_ID, A_PAYMENT_ID_IS_A_UUID);
    }
    AutraOperationResult refused = AutraMerchantDocument.theRefusalOf(call, merchant);
    if (refused != null) {
      return refused;
    }
    Optional<AutraPayment> found =
        payments
            .findById(testRunId, paymentId)
            .filter(payment -> payment.accountId().equals(merchant.accountId()));
    if (found.isEmpty()) {
      return AutraAnswers.noSuchPayment(NO_SUCH_PAYMENT);
    }
    return AutraOperationResult.succeeded(
        paymentId,
        AutraBody.of(
            AutraAnswers.OK,
            AutraPaymentRead.theWholePaymentWithItsTimeline(
                found.get(), payments.timelineOf(testRunId, paymentId))));
  }
}
