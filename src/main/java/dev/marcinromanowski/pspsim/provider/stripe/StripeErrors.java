package dev.marcinromanowski.pspsim.provider.stripe;

import org.jspecify.annotations.Nullable;

final class StripeErrors {

  private StripeErrors() {}

  static final int BAD_REQUEST = 400;
  static final int UNAUTHORIZED = 401;
  static final int REQUEST_FAILED = 402;
  static final int NOT_FOUND = 404;
  static final int CONFLICT = 409;

  static final String RESOURCE_MISSING = "resource_missing";
  static final String PAYMENT_INTENT_UNEXPECTED_STATE = "payment_intent_unexpected_state";
  static final String SETUP_INTENT_UNEXPECTED_STATE = "setup_intent_unexpected_state";
  static final String PARAMETER_UNKNOWN = "parameter_unknown";
  static final String CHARGE_EXPIRED_FOR_CAPTURE = "charge_expired_for_capture";
  static final String INVALID_CHARGE_AMOUNT = "invalid_charge_amount";
  static final String AMOUNT_TOO_LARGE = "amount_too_large";
  static final String PARAMETER_MISSING = "parameter_missing";
  static final String IDEMPOTENCY_KEY_IN_USE = "idempotency_key_in_use";
  static final String CHARGE_ALREADY_REFUNDED = "charge_already_refunded";

  static StripeError noApiKey() {
    return new StripeError(
        UNAUTHORIZED,
        StripeErrorType.INVALID_REQUEST_ERROR,
        null,
        null,
        "You did not provide an API key. You need to provide your API key in the Authorization"
            + " header, using Bearer auth (e.g. 'Authorization: Bearer YOUR_SECRET_KEY'). See"
            + " https://stripe.com/docs/api#authentication for details, or we can help at"
            + " https://support.stripe.com/.",
        null,
        null,
        null);
  }

  static StripeError noValidApiKey() {
    return new StripeError(
        UNAUTHORIZED,
        StripeErrorType.INVALID_REQUEST_ERROR,
        null,
        null,
        "No valid API key provided.",
        null,
        null,
        null);
  }

  static StripeError resourceMissing(String param) {
    return invalidRequest(
        NOT_FOUND,
        RESOURCE_MISSING,
        "The ID provided isn't valid. Either the resource doesn't exist, or an ID for a different"
            + " resource has been provided.",
        param);
  }

  static StripeError noResourceToMintAKeyFor() {
    return invalidRequest(
        BAD_REQUEST,
        PARAMETER_MISSING,
        "A required parameter is missing. Provider stripe creates a short-lived API key for a"
            + " given resource, and "
            + StripeFields.CUSTOMER
            + " is the only one of the four published resource parameters this simulator serves:"
            + " issuing_card, nonce and verification_session name objects no route of this build"
            + " answers, so a key naming one of them would reach nothing.",
        StripeFields.CUSTOMER);
  }

  static StripeError theShortLivedKeyHasExpired() {
    return invalidRequest(
        UNAUTHORIZED,
        null,
        "This request was authenticated with a short-lived key whose "
            + StripeFields.EXPIRES
            + " moment has passed. Provider stripe publishes an ephemeral key as a short-lived API"
            + " key; create another one at "
            + StripeApiController.EPHEMERAL_KEYS
            + ".",
        null);
  }

  static StripeError theShortLivedKeyReachesAnotherResource() {
    return invalidRequest(
        UNAUTHORIZED,
        null,
        "This request was authenticated with a short-lived key minted for another resource. A key"
            + " reaches only the resource this simulator names in "
            + StripeFields.ASSOCIATED_OBJECTS
            + ", and the "
            + StripeFields.CUSTOMER
            + " this route names is not that one.",
        null);
  }

  static StripeError theShortLivedKeyReachesNoRouteOfThisKind() {
    return invalidRequest(
        UNAUTHORIZED,
        null,
        "This request was authenticated with a short-lived key, and this route is not one a"
            + " short-lived key reaches in this build. The two it reaches are "
            + StripeApiController.CUSTOMERS
            + "/{customerId} and "
            + StripeApiController.CUSTOMERS
            + "/{customerId}"
            + StripeApiController.OF_A_CUSTOMER
            + ", both read with the resource the key was minted for. Every other route takes the"
            + " account's own test-mode secret key.",
        null);
  }

  static StripeError furtherOperationRefused(String message) {
    return invalidRequest(BAD_REQUEST, null, message, StripeFields.CUSTOMER);
  }

  static StripeError paymentMethodRefused(String message) {
    return invalidRequest(BAD_REQUEST, null, message, StripeFields.PAYMENT_METHOD);
  }

  static StripeError chargeRefused(String message) {
    return invalidRequest(BAD_REQUEST, null, message, StripeFields.CHARGE);
  }

  static StripeError twoRefundTargets() {
    return invalidRequest(
        BAD_REQUEST,
        null,
        "Provider stripe publishes that a refund must specify a Charge or a PaymentIntent object"
            + " on which to create it, and this request names both "
            + StripeFields.CHARGE
            + " and "
            + StripeFields.PAYMENT_INTENT
            + ". Send one of them.",
        StripeFields.CHARGE);
  }

  static StripeError noRefundTarget() {
    return invalidRequest(
        BAD_REQUEST,
        PARAMETER_MISSING,
        "A required parameter is missing. A refund must name either "
            + StripeFields.PAYMENT_INTENT
            + " or "
            + StripeFields.CHARGE
            + " as the object to create it on.",
        StripeFields.PAYMENT_INTENT);
  }

  static StripeError setupUnexpectedState(String message) {
    return invalidRequest(BAD_REQUEST, SETUP_INTENT_UNEXPECTED_STATE, message, null);
  }

  static StripeError parameterUnknown(String param, String accepted) {
    return invalidRequest(
        BAD_REQUEST,
        PARAMETER_UNKNOWN,
        "Received unknown parameter. This endpoint accepts " + accepted + ".",
        param);
  }

  static StripeError unexpectedState(String message) {
    return invalidRequest(BAD_REQUEST, PAYMENT_INTENT_UNEXPECTED_STATE, message, null);
  }

  static StripeError chargeExpiredForCapture() {
    return invalidRequest(
        BAD_REQUEST,
        CHARGE_EXPIRED_FOR_CAPTURE,
        "The charge can't be captured because the authorization has expired.",
        null);
  }

  static StripeError invalidChargeAmount(String param) {
    return invalidRequest(
        BAD_REQUEST,
        INVALID_CHARGE_AMOUNT,
        "The specified amount is invalid. The charge amount must be a positive integer in the"
            + " smallest currency unit.",
        param);
  }

  static StripeError amountTooLarge(String param) {
    return invalidRequest(
        BAD_REQUEST,
        AMOUNT_TOO_LARGE,
        "The amount to capture is above the amount this PaymentIntent can capture.",
        param);
  }

  static StripeError nonFinalCaptureCeilingReached() {
    return invalidRequest(
        BAD_REQUEST, null, StripeMulticapture.CEILING_IS_REACHED, StripeFields.FINAL_CAPTURE);
  }

  static StripeError chargeAlreadyRefunded(String message) {
    return invalidRequest(BAD_REQUEST, CHARGE_ALREADY_REFUNDED, message, null);
  }

  static StripeError parameterMissing(String param) {
    return invalidRequest(
        BAD_REQUEST, PARAMETER_MISSING, "A required parameter is missing.", param);
  }

  static StripeError unreadableParameter(String param, String accepted) {
    return invalidRequest(
        BAD_REQUEST,
        null,
        "A parameter carries a value this API does not accept. It accepts " + accepted + ".",
        param);
  }

  static StripeError headerRefused(String message) {
    return new StripeError(
        BAD_REQUEST, StripeErrorType.INVALID_REQUEST_ERROR, null, null, message, null, null, null);
  }

  static StripeError idempotencyKeyInUse() {
    return new StripeError(
        CONFLICT,
        StripeErrorType.IDEMPOTENCY_ERROR,
        IDEMPOTENCY_KEY_IN_USE,
        null,
        "The idempotency key provided is currently being used in another request. This occurs if"
            + " your integration is making duplicate requests simultaneously.",
        null,
        null,
        null);
  }

  static StripeError idempotencyKeyReused() {
    return new StripeError(
        BAD_REQUEST,
        StripeErrorType.IDEMPOTENCY_ERROR,
        null,
        null,
        "Keys for idempotent requests can only be used with the same parameters they were first"
            + " used with. Try using a key other than the one in the request headers.",
        null,
        null,
        null);
  }

  static StripeError declined(StripeDecline decline) {
    return new StripeError(
        REQUEST_FAILED,
        StripeErrorType.CARD_ERROR,
        decline.errorCode(),
        decline.declineCode(),
        decline.publishedMessage(),
        null,
        null,
        null);
  }

  private static StripeError invalidRequest(
      int httpStatus, @Nullable String code, String message, @Nullable String param) {
    return new StripeError(
        httpStatus, StripeErrorType.INVALID_REQUEST_ERROR, code, null, message, param, null, null);
  }
}
