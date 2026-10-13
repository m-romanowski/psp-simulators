package dev.marcinromanowski.pspsim.provider.worldline;

import java.util.Set;

final class WorldlineFields {

  private WorldlineFields() {}

  static final String AUTHORIZATION = "Authorization";

  static final String DATE = "Date";

  static final String CONTENT_TYPE = "Content-Type";

  static final String LOCATION = "Location";

  static final String CANONICAL_HEADER_PREFIX = "x-gcs-";

  static final String IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT =
      "X-GCS-Idempotence-Key";

  static final String IDEMPOTENCY_KEY_AS_THE_MANUAL_AUTHENTICATION_PAGE_SPELLS_IT =
      "X-GCS-Idempotency-Key";

  static final String IDEMPOTENCE_REQUEST_TIMESTAMP = "X-GCS-Idempotence-Request-Timestamp";

  static final String AUTHENTICATION_TOKEN = "X-GCS-Authentication-Token";

  static final String JSON = "application/json";

  static final String HOSTED_TOKENIZATION_ID = "hostedTokenizationId";

  static final String HOSTED_TOKENIZATION_URL = "hostedTokenizationUrl";

  static final String PARTIAL_REDIRECT_URL = "partialRedirectUrl";

  static final String TOKENS = "tokens";

  static final String INVALID_TOKENS = "invalidTokens";

  static final String EXPIRED_CARD_TOKENS = "expiredCardTokens";

  static final String TOKEN = "token";

  static final String TOKEN_STATUS = "tokenStatus";

  static final String IS_TEMPORARY = "isTemporary";

  static final String CARD = "card";

  static final String DATA = "data";

  static final String CARD_WITHOUT_CVV = "cardWithoutCvv";

  static final String CARD_NUMBER = "cardNumber";

  static final String EXPIRY_DATE = "expiryDate";

  static final String ORDER = "order";

  static final String AMOUNT_OF_MONEY = "amountOfMoney";

  static final String AMOUNT = "amount";

  static final String CURRENCY_CODE = "currencyCode";

  static final String REFERENCES = "references";

  static final String MERCHANT_REFERENCE = "merchantReference";

  static final String CUSTOMER = "customer";

  static final String MERCHANT_CUSTOMER_ID = "merchantCustomerId";

  static final String CARD_PAYMENT_METHOD_SPECIFIC_INPUT = "cardPaymentMethodSpecificInput";

  static final String CARD_PAYMENT_METHOD_SPECIFIC_OUTPUT = "cardPaymentMethodSpecificOutput";

  static final String AUTHORIZATION_MODE = "authorizationMode";

  static final String IS_RECURRING = "isRecurring";

  static final String IS_FINAL = "isFinal";

  static final String CREATION_OUTPUT = "creationOutput";

  static final String PAYMENT = "payment";

  static final String PAYMENT_OUTPUT = "paymentOutput";

  static final String PAYMENT_METHOD = "paymentMethod";

  static final String CAPTURE_OUTPUT = "captureOutput";

  static final String ID = "id";

  static final String STATUS = "status";

  static final String STATUS_OUTPUT = "statusOutput";

  static final String STATUS_CATEGORY = "statusCategory";

  static final String STATUS_CODE = "statusCode";

  static final String IS_CANCELLABLE = "isCancellable";

  static final String IS_AUTHORIZED = "isAuthorized";

  static final String IS_REFUNDABLE = "isRefundable";

  static final String ERROR_ID = "errorId";

  static final String ERRORS = "errors";

  static final String ERROR_CODE = "errorCode";

  static final String CATEGORY = "category";

  static final String CODE = "code";

  static final String HTTP_STATUS_CODE = "httpStatusCode";

  static final String MESSAGE = "message";

  static final String PROPERTY_NAME = "propertyName";

  static final String RETRIABLE = "retriable";

  static final String PAYMENT_RESULT = "paymentResult";

  static final String CARD_AS_A_PAYMENT_METHOD = "card";

  static final String EXPIRES_AT = "expiresAt";

  static final Set<String> SENSITIVE_HEADERS =
      Set.of("authorization", "x-gcs-authentication-token");

  static final Set<String> SENSITIVE_REQUEST_FIELDS =
      Set.of(
          CARD_PAYMENT_METHOD_SPECIFIC_INPUT + "." + CARD,
          CARD_PAYMENT_METHOD_SPECIFIC_INPUT + "." + CARD + "." + CARD_NUMBER,
          CARD_PAYMENT_METHOD_SPECIFIC_INPUT + "." + CARD + ".cvv",
          CARD_PAYMENT_METHOD_SPECIFIC_INPUT + "." + CARD + "." + EXPIRY_DATE);

  static final Set<String> SENSITIVE_RESPONSE_FIELDS =
      Set.of(HOSTED_TOKENIZATION_URL, PARTIAL_REDIRECT_URL);
}
