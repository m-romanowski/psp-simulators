package dev.marcinromanowski.pspsim.provider.autra;

import java.util.Set;

final class AutraFields {

  private AutraFields() {}

  static final String GRANT_TYPE = "grant_type";

  static final String CLIENT_CREDENTIALS = "client_credentials";

  static final String AUTHORIZATION = "Authorization";

  static final String BASIC = "Basic ";

  static final String BEARER = "Bearer ";

  static final String ACCESS_TOKEN = "accessToken";

  static final String TOKEN_TYPE = "tokenType";

  static final String EXPIRES_IN = "expiresIn";

  static final String ACCESS_TOKEN_SNAKE = "access_token";

  static final String TOKEN_TYPE_SNAKE = "token_type";

  static final String EXPIRES_IN_SNAKE = "expires_in";

  static final String OAUTH_ERROR = "error";

  static final String ERRORS = "errors";

  static final String CODE = "code";

  static final String MSG = "msg";

  static final String DOCUMENT_ID = "documentId";

  static final String TRANSACTIONAL_TOKEN = "transactionalToken";

  static final String TRANSACTION_TYPE = "transactionType";

  static final String CURRENCY = "currency";

  static final String AMOUNT = "amount";

  static final String INSTALLMENTS = "installments";

  static final String ORDER_ID = "orderId";

  static final String CARD = "card";

  static final String TOKEN_DATA = "tokenData";

  static final String RECURRING_DATA = "recurringData";

  static final String SLUG_TOKEN = "slugToken";

  static final String SLUG_STORED_CARD = "slugStoredCard";

  static final String PAYMENT_ID_UPPERCASE_D = "paymentID";

  static final String PAYMENT_ID_LOWERCASE_D = "paymentId";

  static final String STATUS = "status";

  static final String TYPE = "type";

  static final String VOID_RETRYABLE = "voidRetryable";

  static final String REFUNDED_AMOUNT = "refundedAmount";

  static final String TRANSACTION_METHOD = "transactionMethod";

  static final String MASKED_CARD = "maskedCard";

  static final String BRAND_REFERENCE_ID = "brandReferenceId";

  static final String CREATED_AT = "createdAt";

  static final String UPDATED_AT = "updatedAt";

  static final String EVENTS = "events";

  static final String ERROR_CODE = "errorCode";

  static final String AT = "at";

  static final String ITEMS = "items";

  static final String PAGE = "page";

  static final String LIMIT = "limit";

  static final String HAS_MORE = "hasMore";

  static final String FROM = "from";

  static final String TO = "to";

  static final String INCLUDE_RECURRING = "includeRecurring";

  static final String MERCHANT_DOCUMENT_HEADER = "X-Merchant-Document";

  static final String CARD_METHOD = "CARD";

  static final String TOKEN_METHOD = "TOKEN";

  static final String AUTHORIZATION_CODE = "authorizationCode";

  static final String RRN = "rrn";

  static final String CREDIT = "CREDIT";

  static final String DEBIT = "DEBIT";

  static final String HOSTED_CARD_SESSION_ID = "hostedCardSessionId";

  static final String HOSTED_CARD_URL = "hostedCardUrl";

  static final String TOKEN_EXPIRATION_DATE = "tokenExpirationDate";

  static final String BRAND = "brand";

  static final String FIRST4 = "first4";

  static final String LAST4 = "last4";

  static final String EXPIRATION_MONTH = "expirationMonth";

  static final String EXPIRATION_YEAR = "expirationYear";

  static final String NICKNAME = "nickname";

  static final String PIX_ID = "pixId";

  static final String QR_CODE = "qrCode";

  static final String EXPIRES_AT = "expiresAt";

  static final String DESCRIPTION = "description";

  static final String EVENT_ID = "eventId";

  static final String EVENT_TYPE = "eventType";

  static final String OCCURRED_AT = "occurredAt";

  static final String TENANT_ID = "tenantId";

  static final String USER_DOCUMENT = "userDocument";

  static final String OWNER_ID = "ownerId";

  static final String COUNTERPARTY_NAME = "counterpartyName";

  static final String OPERATION_DATA = "operationData";

  static final String SUBJECT = "subject";

  static final String PRODUCT_TYPE = "productType";

  static final String PAYLOAD = "payload";

  static final String TRANSACTION_STATUS = "transactionStatus";

  static final String TOTAL_AMOUNT = "totalAmount";

  static final String SLUG = "slug";

  static final String MUID = "muid";

  static final String PAYOUT_ID = "payoutId";

  static final String EXPECTED_SETTLEMENT_DATE = "expectedSettlementDate";

  static final String PIX = "PIX";

  static final String JSON = "application/json";

  static final Set<String> SENSITIVE_HEADERS = Set.of("authorization");

  static final Set<String> SENSITIVE_REQUEST_FIELDS =
      Set.of(
          TRANSACTIONAL_TOKEN,
          CARD,
          CARD + ".number",
          CARD + ".cardNumber",
          CARD + ".cvv",
          CARD + ".securityCode",
          CARD + ".holderName",
          CARD + ".expirationDate");

  static final Set<String> SENSITIVE_RESPONSE_FIELDS =
      Set.of(TRANSACTIONAL_TOKEN, ACCESS_TOKEN, ACCESS_TOKEN_SNAKE, HOSTED_CARD_URL);

  static final Set<String> SENSITIVE_DELIVERY_FIELDS = Set.of();
}
