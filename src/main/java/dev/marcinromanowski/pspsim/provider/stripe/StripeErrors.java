package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.List;
import org.jspecify.annotations.Nullable;

final class StripeErrors {

  private StripeErrors() {}

  static final int BAD_REQUEST = 400;
  static final int UNAUTHORIZED = 401;
  static final int FORBIDDEN = 403;
  static final int REQUEST_FAILED = 402;
  static final int NOT_FOUND = 404;
  static final int CONFLICT = 409;

  static final String RESOURCE_MISSING = "resource_missing";
  static final String PAYMENT_INTENT_UNEXPECTED_STATE = "payment_intent_unexpected_state";
  static final String SETUP_INTENT_UNEXPECTED_STATE = "setup_intent_unexpected_state";
  static final String CHARGE_EXPIRED_FOR_CAPTURE = "charge_expired_for_capture";
  static final String INVALID_CHARGE_AMOUNT = "invalid_charge_amount";
  static final String AMOUNT_TOO_LARGE = "amount_too_large";
  static final String PARAMETER_MISSING = "parameter_missing";
  static final String IDEMPOTENCY_KEY_IN_USE = "idempotency_key_in_use";
  static final String CHARGE_ALREADY_REFUNDED = "charge_already_refunded";
  static final String PAYMENT_METHOD_TYPES_NO_LONGER_SUPPORTED =
      "payment_method_types_no_longer_supported";
  static final String PARAMETERS_EXCLUSIVE = "parameters_exclusive";
  static final String PAYMENT_INTENT_INVALID_PARAMETER = "payment_intent_invalid_parameter";
  static final String SETUP_INTENT_INVALID_PARAMETER = "setup_intent_invalid_parameter";
  static final String PAYMENT_INTENT_INCOMPATIBLE_PAYMENT_METHOD =
      "payment_intent_incompatible_payment_method";
  static final String SECRET_KEY_REQUIRED = "secret_key_required";

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

  static StripeError secretKeyRequired() {
    return invalidRequest(
        FORBIDDEN,
        SECRET_KEY_REQUIRED,
        "The API key provided is a publishable key, but a secret key is required.",
        null);
  }

  static StripeError clientSecretMissing() {
    return invalidRequest(
        BAD_REQUEST,
        PARAMETER_MISSING,
        "Missing required param: " + StripeFields.CLIENT_SECRET + ".",
        StripeFields.CLIENT_SECRET);
  }

  static StripeError clientSecretDoesNotMatchThePayment() {
    return invalidRequest(
        BAD_REQUEST,
        PAYMENT_INTENT_INVALID_PARAMETER,
        "The client_secret provided does not match the client_secret associated with the"
            + " PaymentIntent.",
        StripeFields.CLIENT_SECRET);
  }

  static StripeError clientSecretDoesNotMatchTheSetup() {
    return invalidRequest(
        BAD_REQUEST,
        SETUP_INTENT_INVALID_PARAMETER,
        "The client_secret provided does not match the client_secret associated with the"
            + " SetupIntent.",
        StripeFields.CLIENT_SECRET);
  }

  static StripeError noSuchPaymentIntent(String intentId) {
    return invalidRequest(
        NOT_FOUND,
        RESOURCE_MISSING,
        "No such payment_intent: '" + intentId + "'",
        StripeLookup.INTENT_PARAMETER);
  }

  static StripeError noSuchSetupIntent(String setupIntentId) {
    return invalidRequest(
        NOT_FOUND,
        RESOURCE_MISSING,
        "No such setupintent: '" + setupIntentId + "'",
        StripeSetupLookup.SETUP_INTENT_PARAMETER);
  }

  static StripeError noSuchPaymentMethod(String paymentMethodId) {
    return invalidRequest(
        BAD_REQUEST,
        RESOURCE_MISSING,
        "No such PaymentMethod: '"
            + paymentMethodId
            + "'; It's possible this PaymentMethod exists on one of your connected accounts, in"
            + " which case you should retry this request on that connected account. Learn more at"
            + " https://stripe.com/docs/connect/authentication",
        StripeFields.PAYMENT_METHOD);
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

  static StripeError paymentMethodTypesNoLongerSupported() {
    return invalidRequest(
        BAD_REQUEST,
        PAYMENT_METHOD_TYPES_NO_LONGER_SUPPORTED,
        StripeFields.PAYMENT_METHOD_TYPES
            + " is no longer a writable parameter at the API version this request is answered at."
            + " It stays on the object as the list of payment method types the intent computed."
            + " Send "
            + StripeFields.ALLOWED_PAYMENT_METHOD_TYPES
            + " to name the types you accept, or "
            + StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES
            + " to narrow the dynamic ones.",
        StripeFields.PAYMENT_METHOD_TYPES);
  }

  static StripeError invalidValue(String param, String oneOf) {
    return invalidRequest(
        BAD_REQUEST, null, "Invalid " + param + ": must be one of " + oneOf, param);
  }

  static StripeError parametersExclusive(String param, String other) {
    return invalidRequest(
        BAD_REQUEST,
        PARAMETERS_EXCLUSIVE,
        "You may only specify one of these parameters: " + param + ", " + other + ".",
        param);
  }

  static StripeError paymentMethodTypeNotSupported(String type, String when) {
    return invalidRequest(
        BAD_REQUEST,
        null,
        "The payment method type `" + type + "` is not supported when " + when + ".",
        StripeFields.PAYMENT_METHOD_TYPES);
  }

  static StripeError noPaymentMethodTypeLeftForThePayment(String param) {
    return invalidRequest(
        BAD_REQUEST,
        PAYMENT_INTENT_INVALID_PARAMETER,
        StripeSelection.NO_TYPE_IS_LEFT + " PaymentIntent.",
        param);
  }

  static StripeError noPaymentMethodTypeLeftForTheSetup(String param) {
    return invalidRequest(
        BAD_REQUEST,
        SETUP_INTENT_INVALID_PARAMETER,
        StripeSelection.NO_TYPE_IS_LEFT + " SetupIntent.",
        param);
  }

  static StripeError paymentMethodNotAllowedForThePayment(String type, String allowed) {
    return invalidRequest(
        BAD_REQUEST,
        PAYMENT_INTENT_INCOMPATIBLE_PAYMENT_METHOD,
        notAllowedFor(type, "PaymentIntent", allowed),
        StripeFields.PAYMENT_METHOD);
  }

  static StripeError paymentMethodNotAllowedForTheSetup(String type, String allowed) {
    return invalidRequest(
        BAD_REQUEST,
        SETUP_INTENT_INVALID_PARAMETER,
        notAllowedFor(type, "SetupIntent", allowed),
        StripeFields.PAYMENT_METHOD);
  }

  static StripeError paymentMethodNotAllowedByTheSelectionSentForThePayment(
      String type, String selected) {
    return invalidRequest(
        BAD_REQUEST,
        PAYMENT_INTENT_INCOMPATIBLE_PAYMENT_METHOD,
        notAllowedByTheSelectionSent(type, "PaymentIntent", selected),
        StripeFields.PAYMENT_METHOD);
  }

  static StripeError paymentMethodNotAllowedByTheSelectionSentForTheSetup(
      String type, String selected) {
    return invalidRequest(
        BAD_REQUEST,
        SETUP_INTENT_INVALID_PARAMETER,
        notAllowedByTheSelectionSent(type, "SetupIntent", selected),
        StripeFields.PAYMENT_METHOD);
  }

  private static String notAllowedFor(String type, String intent, String allowed) {
    return "The PaymentMethod provided ("
        + type
        + ") is not allowed for this "
        + intent
        + ". Attach a PaymentMethod of one of the following types: "
        + allowed
        + ".";
  }

  private static String notAllowedByTheSelectionSent(String type, String intent, String selected) {
    return "The PaymentMethod provided ("
        + type
        + ") is not allowed by the payment method types this request selects: "
        + selected
        + ". This "
        + intent
        + " keeps the payment method types it had.";
  }

  static StripeError returnUrlRequiredForThePayment() {
    return returnUrlRequired("Payment Intent");
  }

  static StripeError returnUrlRequiredForTheSetup() {
    return returnUrlRequired("Setup Intent");
  }

  private static StripeError returnUrlRequired(String intent) {
    return invalidRequest(
        BAD_REQUEST,
        null,
        "A `"
            + StripeFields.RETURN_URL
            + "` must be specified because this "
            + intent
            + " is configured to automatically accept the payment methods enabled in the"
            + " Dashboard, some of which may require a full page redirect to succeed. If you do"
            + " not want to accept redirect-based payment methods, set `"
            + StripeFields.AUTOMATIC_PAYMENT_METHODS
            + "["
            + StripeSelections.ENABLED
            + "]` to `true` and `"
            + StripeFields.AUTOMATIC_PAYMENT_METHODS
            + "["
            + StripeSelections.ALLOW_REDIRECTS
            + "]` to `never` when creating Setup Intents and Payment Intents.",
        StripeFields.RETURN_URL);
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

  static StripeError notExpandable(String reached) {
    return invalidRequest(
        BAD_REQUEST, null, "This property cannot be expanded (" + reached + ").", null);
  }

  static StripeError doesNotExist(String reached) {
    return invalidRequest(
        BAD_REQUEST,
        null,
        "This property cannot be expanded because it doesn't exist: " + reached,
        null);
  }

  static StripeError notExpandableOnAList(String reached, String suggested) {
    return invalidRequest(
        BAD_REQUEST,
        null,
        "This property cannot be expanded ("
            + reached
            + "). You may want to try expanding '"
            + suggested
            + "' instead.",
        null);
  }

  static StripeError expandedTooDeep(String path) {
    return invalidRequest(
        BAD_REQUEST,
        null,
        "This property cannot be expanded ("
            + path
            + "). Expansions have a maximum depth of four levels.",
        null);
  }

  static StripeError unreadableEncoding(@Nullable String param, String why) {
    return invalidRequest(
        BAD_REQUEST,
        null,
        "The request encodes a parameter in a way this API cannot read: " + why + ".",
        param);
  }

  static StripeError headerRefused(String message) {
    return new StripeError(
        BAD_REQUEST, StripeErrorType.INVALID_REQUEST_ERROR, null, null, message, null, null, null);
  }

  static StripeError versionNotAnswered(String sent) {
    return headerRefused(
        "Stripe-Version "
            + sent
            + " is a published Stripe API version this simulator does not answer. It answers "
            + StripeApiVersions.theRangeAnswered()
            + ", every release of "
            + theMajorsAnswered()
            + ", because its behaviour carries the breaking changes "
            + StripeApiVersions.oldestAnswered().wireValue()
            + " shipped. Send one of those, or send no Stripe-Version to be answered at the"
            + " account's default version.");
  }

  static StripeError versionLaterThanTheCatalogue(String sent) {
    return headerRefused(
        "Stripe-Version "
            + sent
            + " is dated after "
            + StripeApiVersions.newest().wireValue()
            + ", the newest Stripe API version this simulator was built with, so it may be a"
            + " release Stripe published since. This simulator does not answer it. It answers "
            + StripeApiVersions.theRangeAnswered()
            + ". Send one of those, or send no Stripe-Version to be answered at the account's"
            + " default version.");
  }

  static StripeError versionAsksForABeta(String sent) {
    return headerRefused(
        "Stripe-Version "
            + sent
            + " asks for a beta, and this simulator answers no beta. It answers "
            + StripeApiVersions.theRangeAnswered()
            + ", sent without anything after the version.");
  }

  static StripeError versionUnrecognised(String sent) {
    return headerRefused("Invalid Stripe API version: " + sent);
  }

  private static String theMajorsAnswered() {
    return inProse(
        StripeApiVersions.majorsAnswered().stream().map(StripeApiMajor::codename).toList());
  }

  static String inProse(List<String> names) {
    StringBuilder named = new StringBuilder();
    for (int at = 0; at < names.size(); at++) {
      named.append(theWordsBefore(at, names.size())).append(names.get(at));
    }
    return named.toString();
  }

  private static String theWordsBefore(int at, int count) {
    if (at == 0) {
      return "";
    }
    return at == count - 1 ? " and " : ", ";
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
