package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;

final class AutraAnswers {

  static final int OK = 200;

  static final int BAD_REQUEST = 400;

  static final int NOT_FOUND = 404;

  static final int CONFLICT = 409;

  static final int UNPROCESSABLE = 422;

  private AutraAnswers() {}

  static AutraOperationResult refusedRequest(String code, String msg) {
    return AutraOperationResult.failed(
        AutraRefusals.theBodyWasRefused(),
        AutraBody.of(BAD_REQUEST, AutraErrors.envelope(code, msg)));
  }

  static AutraOperationResult noSuchMerchant(AutraMerchantNotFound status, String msg) {
    return AutraOperationResult.failed(
        RefusalSubject.noObjectAnsweredTo(AutraFields.DOCUMENT_ID),
        AutraBody.of(
            status.httpStatus(), AutraErrors.envelope(AutraErrors.MERCHANT_NOT_FOUND, msg)));
  }

  static AutraOperationResult noSuchMerchantOnARead(String msg) {
    return AutraOperationResult.failed(
        RefusalSubject.noObjectAnsweredTo(AutraFields.DOCUMENT_ID),
        AutraBody.of(NOT_FOUND, AutraErrors.envelope(AutraErrors.MERCHANT_NOT_FOUND, msg)));
  }

  static AutraOperationResult refusedDocument(String msg) {
    return AutraOperationResult.failed(
        RefusalSubject.noObjectAnsweredTo(AutraFields.DOCUMENT_ID),
        AutraBody.of(UNPROCESSABLE, AutraErrors.envelope(AutraErrors.INVALID_DOCUMENT, msg)));
  }

  static AutraOperationResult noSuchPayment(String msg) {
    return AutraOperationResult.failed(
        RefusalSubject.noObjectAnsweredTo(AutraFields.PAYMENT_ID_LOWERCASE_D),
        AutraBody.of(NOT_FOUND, AutraErrors.envelope(AutraErrors.PAYMENT_NOT_FOUND, msg)));
  }

  static AutraOperationResult refusedObject(String paymentId, String code, String msg) {
    return AutraOperationResult.failed(
        RefusalSubject.theObject(paymentId),
        AutraBody.of(UNPROCESSABLE, AutraErrors.envelope(code, msg)));
  }

  static AutraOperationResult tokenNotAvailable(String msg) {
    return AutraOperationResult.failed(
        RefusalSubject.noObjectAnsweredTo(AutraFields.TOKEN_DATA),
        AutraBody.of(UNPROCESSABLE, AutraErrors.envelope(AutraErrors.TOKEN_NOT_AVAILABLE, msg)));
  }

  static AutraOperationResult orderAlreadyApproved(String msg) {
    return AutraOperationResult.failed(
        AutraRefusals.theOrderIsAlreadyApproved(),
        AutraBody.of(CONFLICT, AutraErrors.envelope(AutraErrors.ORDER_ALREADY_APPROVED, msg)));
  }

  static AutraOperationResult orderAlreadyHeld(String msg) {
    return AutraOperationResult.failed(
        AutraRefusals.theOrderIsAlreadyHeld(),
        AutraBody.of(CONFLICT, AutraErrors.envelope(AutraErrors.ORDER_ALREADY_PREAUTHORIZED, msg)));
  }
}
