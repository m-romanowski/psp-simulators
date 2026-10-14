package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Set;

final class TranzilaFields {

  private TranzilaFields() {}

  static final String JSON = "application/json";

  static final String CONTENT_TYPE = "Content-Type";

  static final String APP_KEY = "X-tranzila-api-app-key";

  static final String REQUEST_TIME = "X-tranzila-api-request-time";

  static final String NONCE = "X-tranzila-api-nonce";

  static final String ACCESS_TOKEN = "X-tranzila-api-access-token";

  static final String TERMINAL_NAME = "terminal_name";

  static final String TXN_TYPE = "txn_type";

  static final String VERIFY_MODE = "verify_mode";

  static final String CARD_NUMBER = "card_number";

  static final String EXPIRE_MONTH = "expire_month";

  static final String EXPIRE_YEAR = "expire_year";

  static final String CVV = "cvv";

  static final String CARD_HOLDER_ID = "card_holder_id";

  static final String TXN_CURRENCY_CODE = "txn_currency_code";

  static final String ITEMS = "items";

  static final String NAME = "name";

  static final String UNITS_NUMBER = "units_number";

  static final String UNIT_PRICE = "unit_price";

  static final String TYPE = "type";

  static final String PRICE_TYPE = "price_type";

  static final String VAT_PERCENT = "vat_percent";

  static final String REFERENCE_TXN_ID = "reference_txn_id";

  static final String AUTHORIZATION_NUMBER = "authorization_number";

  static final String PAYMENT_PLAN = "payment_plan";

  static final String PAN_ENTRY_MODE = "pan_entry_mode";

  static final String USER_DEFINED_FIELDS = "user_defined_fields";

  static final String VALUE = "value";

  static final String DC_DISABLE = "DCdisable";

  static final String ACTIVATE_3DS = "activate_3ds";

  static final Set<String> THE_REQUEST_FIELDS_THE_SCHEMA_PUBLISHES =
      Set.of(
          TERMINAL_NAME,
          TXN_TYPE,
          VERIFY_MODE,
          CARD_NUMBER,
          EXPIRE_MONTH,
          EXPIRE_YEAR,
          CVV,
          CARD_HOLDER_ID,
          TXN_CURRENCY_CODE,
          ITEMS,
          REFERENCE_TXN_ID,
          AUTHORIZATION_NUMBER,
          PAYMENT_PLAN,
          PAN_ENTRY_MODE,
          USER_DEFINED_FIELDS,
          "client",
          "remarks",
          "response_language",
          "created_by_user",
          "created_by_system",
          ACTIVATE_3DS,
          "3ds_settings");

  static final String ERROR_CODE = "error_code";

  static final String MESSAGE = "message";

  static final String TRANSACTION_RESULT = "transaction_result";

  static final String ORIGINAL_REQUEST = "original_request";

  static final String PROCESSOR_RESPONSE_CODE = "processor_response_code";

  static final String TRANSACTION_ID = "transaction_id";

  static final String AUTH_NUMBER = "auth_number";

  static final String RESPONSE_CVV = "Responsecvv";

  static final String RESPONSE_ID = "Responseid";

  static final String CARD_TYPE = "card_type";

  static final String CARD_TYPE_NAME = "card_type_name";

  static final String CURRENCY_CODE = "currency_code";

  static final String EXPIRY_MONTH = "expiry_month";

  static final String EXPIRY_YEAR = "expiry_year";

  static final String TOKEN = "token";

  static final String LAST_4 = "last_4";

  static final String CARD_MASK = "card_mask";

  static final String CARD_LOCALITY = "card_locality";

  static final String AMOUNT = "amount";

  static final String TRANMODE = "tranmode";

  static final String MASKED_CVV = "***";

  static final String FORM = "application/x-www-form-urlencoded";

  static final String SUM = "sum";

  static final String CURRENCY = "currency";

  static final String CRED_TYPE = "cred_type";

  static final String SUCCESS_URL_ADDRESS = "success_url_address";

  static final String FAIL_URL_ADDRESS = "fail_url_address";

  static final String NOTIFY_URL_ADDRESS = "notify_url_address";

  static final String THTK = "thtk";

  static final String REQUEST_PARAMS = "request_params";

  static final String MYID = "myid";

  static final String SUPPLIER = "supplier";

  static final String RESPONSE = "Response";

  static final String INDEX = "index";

  static final String CCNO = "ccno";

  static final String RESPONSE_CV = "Responsecv";

  static final String CARDTYPE = "cardtype";

  static final String CARDISSUER = "cardissuer";

  static final String CARDACQUIRER = "cardacquirer";

  static final String TRANZILA_TK = "TranzilaTK";

  static final String CONFIRMATION_CODE = "ConfirmationCode";

  static final Set<String> SENSITIVE_HEADERS = Set.of("x-tranzila-api-access-token");

  static final Set<String> SENSITIVE_REQUEST_FIELDS =
      Set.of(CARD_NUMBER, CVV, CARD_HOLDER_ID, MYID);

  static final Set<String> SENSITIVE_RESPONSE_FIELDS =
      Set.of(
          TRANSACTION_RESULT + "." + TOKEN,
          ORIGINAL_REQUEST + "." + CARD_NUMBER,
          ORIGINAL_REQUEST + "." + CARD_HOLDER_ID,
          TRANZILA_TK,
          MYID);

  static final Set<String> SENSITIVE_DELIVERY_FIELDS = Set.of(TRANZILA_TK, MYID);
}
