package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import org.jspecify.annotations.Nullable;

final class WorldlineAnswers {

  static final int OK = 200;

  static final int CREATED = 201;

  static final int BAD_REQUEST = 400;

  static final int PAYMENT_REJECTED = 402;

  static final int NOT_AUTHORIZED = 403;

  static final int NOT_FOUND = 404;

  static final int CONFLICT = 409;

  static final String NO_ERRORS_ID_IS_PUBLISHED_FOR_AN_UNKNOWN_HOSTED_TOKENIZATION =
      "provider worldline publishes four symbolic identifiers for an unknown object —"
          + " UNKNOWN_PAYMENT_ID, UNKNOWN_REFUND_ID, UNKNOWN_PAYOUT_ID and UNKNOWN_CAPTURE_ID —"
          + " and none of them is about a hosted tokenization session. This answer therefore"
          + " carries no errors.id at all, and its errorCode is the one the vendor publishes for"
          + " an unknown payment id, borrowed because the schema requires an errorCode and"
          + " publishes none for this resource";

  private final IdGenerator ids;

  WorldlineAnswers(IdGenerator ids) {
    this.ids = ids;
  }

  String anErrorId() {
    return WorldlineIdentifiers.aUuid(ids);
  }

  WorldlineOperationResult refusedCredential(String message) {
    return WorldlineOperationResult.failed(
        WorldlineRefusals.theCredentialWasRefused(),
        WorldlineBody.of(
            NOT_AUTHORIZED,
            WorldlineErrors.envelope(
                anErrorId(),
                WorldlineErrors.item(
                    null,
                    WorldlineErrors.DIRECT_PLATFORM_ERROR,
                    null,
                    NOT_AUTHORIZED,
                    WorldlineErrors.ACCESS_TO_MERCHANT_NOT_ALLOWED,
                    message,
                    null,
                    false))));
  }

  WorldlineOperationResult refusedValue(@Nullable String propertyName, String message) {
    return WorldlineOperationResult.failed(
        WorldlineRefusals.theBodyWasRefused(),
        WorldlineBody.of(
            BAD_REQUEST,
            WorldlineErrors.envelope(
                anErrorId(),
                WorldlineErrors.item(
                    WorldlineErrors.INVALID_VALUE_ERROR_CODE,
                    WorldlineErrors.DIRECT_PLATFORM_ERROR,
                    WorldlineErrors.INVALID_VALUE_DEPRECATED_CODE,
                    BAD_REQUEST,
                    WorldlineErrors.INVALID_VALUE,
                    message,
                    propertyName,
                    false))));
  }

  WorldlineOperationResult noSuchPayment(String message) {
    return WorldlineOperationResult.failed(
        RefusalSubject.noObjectAnsweredTo(WorldlineOperations.PAYMENT_ID),
        WorldlineBody.of(
            NOT_FOUND,
            WorldlineErrors.envelope(
                anErrorId(),
                WorldlineErrors.item(
                    WorldlineErrors.UNKNOWN_PAYMENT_ID_ERROR_CODE,
                    WorldlineErrors.DIRECT_PLATFORM_ERROR,
                    WorldlineErrors.UNKNOWN_PAYMENT_ID_DEPRECATED_CODE,
                    NOT_FOUND,
                    WorldlineErrors.UNKNOWN_PAYMENT_ID,
                    message,
                    WorldlineOperations.PAYMENT_ID,
                    false))));
  }

  WorldlineOperationResult noSuchHostedTokenization(String message) {
    return WorldlineOperationResult.failed(
        RefusalSubject.noObjectAnsweredTo(WorldlineFields.HOSTED_TOKENIZATION_ID),
        WorldlineBody.of(
            NOT_FOUND,
            WorldlineErrors.envelope(
                anErrorId(),
                WorldlineErrors.item(
                    WorldlineErrors.UNKNOWN_PAYMENT_ID_ERROR_CODE,
                    WorldlineErrors.DIRECT_PLATFORM_ERROR,
                    null,
                    NOT_FOUND,
                    null,
                    message,
                    WorldlineFields.HOSTED_TOKENIZATION_ID,
                    false))));
  }

  WorldlineOperationResult actionNotAllowedOn(String paymentId, String message) {
    return WorldlineOperationResult.failed(
        RefusalSubject.theObject(paymentId),
        WorldlineBody.of(
            BAD_REQUEST,
            WorldlineErrors.envelope(
                anErrorId(),
                WorldlineErrors.item(
                    null,
                    WorldlineErrors.DIRECT_PLATFORM_ERROR,
                    null,
                    BAD_REQUEST,
                    WorldlineErrors.ACTION_NOT_ALLOWED_ON_TRANSACTION,
                    message,
                    WorldlineOperations.PAYMENT_ID,
                    false))));
  }

  WorldlineOperationResult cancellationIsNotAllowed(String paymentId, String message) {
    return WorldlineOperationResult.failed(
        RefusalSubject.theObject(paymentId),
        WorldlineBody.of(
            CONFLICT,
            WorldlineErrors.envelope(
                anErrorId(),
                WorldlineErrors.item(
                    null,
                    WorldlineErrors.DIRECT_PLATFORM_ERROR,
                    null,
                    CONFLICT,
                    WorldlineErrors.ACTION_NOT_ALLOWED_ON_TRANSACTION,
                    message,
                    WorldlineOperations.PAYMENT_ID,
                    false))));
  }

  WorldlineOperationResult refusedWhileTheKeyWasHeld(String message) {
    return WorldlineOperationResult.failed(
        WorldlineRefusals.theKeyIsStillHeld(),
        WorldlineBody.of(
            CONFLICT,
            WorldlineErrors.envelope(
                anErrorId(),
                WorldlineErrors.item(
                    null,
                    WorldlineErrors.DIRECT_PLATFORM_ERROR,
                    null,
                    CONFLICT,
                    null,
                    message,
                    null,
                    true))));
  }

  WorldlineOperationResult declinedOnARouteThatPublishesNoDecline(
      String providerCode, String message) {
    return WorldlineOperationResult.failed(
        WorldlineRefusals.theBodyWasRefused(),
        WorldlineBody.of(
            BAD_REQUEST,
            WorldlineErrors.envelope(
                anErrorId(),
                WorldlineErrors.item(
                    providerCode,
                    WorldlineErrors.PAYMENT_PLATFORM_ERROR,
                    null,
                    BAD_REQUEST,
                    WorldlineErrors.PAYMENT_PRODUCT_NOT_AVAILABLE,
                    message,
                    null,
                    false))));
  }

  WorldlineOperationResult refusedMaintenanceWithAnErrorCode(
      String paymentId, String providerCode, String message) {
    return WorldlineOperationResult.failed(
        RefusalSubject.theObject(paymentId),
        WorldlineBody.of(
            BAD_REQUEST,
            WorldlineErrors.envelope(
                anErrorId(),
                WorldlineErrors.item(
                    providerCode,
                    WorldlineErrors.PAYMENT_PLATFORM_ERROR,
                    null,
                    BAD_REQUEST,
                    WorldlineErrors.ACTION_NOT_ALLOWED_ON_TRANSACTION,
                    message,
                    WorldlineOperations.PAYMENT_ID,
                    false))));
  }

  WorldlineOperationResult declinedPayment(
      String paymentId,
      String providerCode,
      String message,
      @Nullable WorldlineJson paymentResult) {
    return WorldlineOperationResult.failed(
        WorldlineRefusals.aDeclinedPayment(paymentId),
        WorldlineBody.of(
            PAYMENT_REJECTED,
            WorldlineErrors.paymentEnvelope(
                anErrorId(),
                WorldlineErrors.item(
                    providerCode,
                    WorldlineErrors.PAYMENT_PLATFORM_ERROR,
                    null,
                    PAYMENT_REJECTED,
                    null,
                    message,
                    null,
                    false),
                paymentResult)));
  }
}
