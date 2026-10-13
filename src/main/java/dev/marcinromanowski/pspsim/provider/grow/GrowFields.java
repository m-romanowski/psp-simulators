package dev.marcinromanowski.pspsim.provider.grow;

import java.util.List;
import java.util.Set;

final class GrowFields {

  private GrowFields() {}

  static final String PAGE_CODE = "pageCode";
  static final String USER_ID = "userId";
  static final String CHARGE_TYPE = "chargeType";
  static final String SUM = "sum";
  static final String DESCRIPTION = "description";
  static final String SUCCESS_URL = "successUrl";
  static final String CANCEL_URL = "cancelUrl";
  static final String NOTIFY_URL = "notifyUrl";
  static final String SAVE_CARD_TOKEN = "saveCardToken";
  static final String FULL_NAME = "pageField[fullName]";
  static final String PHONE = "pageField[phone]";
  static final String EMAIL = "pageField[email]";
  static final String PAYMENT_NUM = "paymentNum";
  static final String MAX_PAYMENT_NUM = "maxPaymentNum";
  static final String TRANSACTION_ID = "transactionId";
  static final String TRANSACTION_TOKEN = "transactionToken";
  static final String PROCESS_ID = "processId";
  static final String PROCESS_TOKEN = "processToken";
  static final String REFUND_SUM = "refundSum";
  static final String CARD_TOKEN = "cardToken";
  static final String PAYMENT_TYPE = "paymentType";
  static final String TRANSACTION_UNIQUE_IDENTIFIER = "transactionUniqueIdentifier";
  static final String TRANSACTION_GROUP_IDENTIFIER = "transactionGroupIdentifier";
  static final String URL = "url";

  static final String CHARGE_TYPE_SUSPENDED = "2";

  static final String CHARGE_TYPE_TOKEN_ONLY = "3";

  static final String SAVED_CARD_TOKEN = "1";

  static final Set<String> CHARGE_TYPES = Set.of(CHARGE_TYPE_SUSPENDED, CHARGE_TYPE_TOKEN_ONLY);

  static final List<String> REQUIRED_ON_CREATE_PAYMENT_PROCESS =
      List.of(
          PAGE_CODE, USER_ID, CHARGE_TYPE, DESCRIPTION, SUCCESS_URL, CANCEL_URL, FULL_NAME, PHONE);

  static final List<String> REQUIRED_ON_CREATE_TRANSACTION_WITH_TOKEN =
      List.of(USER_ID, CARD_TOKEN, PAYMENT_TYPE, PAYMENT_NUM, DESCRIPTION, FULL_NAME, PHONE);

  static final List<String> REQUIRED_ON_SETTLE_SUSPENDED_TRANSACTION =
      List.of(USER_ID, TRANSACTION_ID, TRANSACTION_TOKEN);

  static final List<String> REQUIRED_ON_REFUND_TRANSACTION =
      List.of(TRANSACTION_ID, TRANSACTION_TOKEN, REFUND_SUM);

  static final List<String> REQUIRED_ON_GET_TRANSACTION_INFO =
      List.of(PAGE_CODE, TRANSACTION_ID, TRANSACTION_TOKEN);

  static final List<String> REQUIRED_ON_GET_PAYMENT_PROCESS_INFO =
      List.of(PAGE_CODE, PROCESS_ID, PROCESS_TOKEN);

  static final List<String> REQUIRED_ON_GET_TOKEN_TRANSACTIONS = List.of(USER_ID, CARD_TOKEN);

  static final List<String> REQUIRED_ON_CANCEL_BIT_TRANSACTION =
      List.of(PAGE_CODE, PROCESS_ID, PROCESS_TOKEN);

  static final String PAYMENT_INTENT_FIELD = "cField2";

  static final List<String> CUSTOM_FIELDS =
      List.of(
          "cField1", "cField2", "cField3", "cField4", "cField5", "cField6", "cField7", "cField8",
          "cField9");

  static final String HOSTED_LINK = "l";

  static final Set<String> SENSITIVE_REQUEST_FIELDS =
      Set.of(
          USER_ID, PAGE_CODE, TRANSACTION_TOKEN, PROCESS_TOKEN, CARD_TOKEN, HOSTED_LINK, "cField1");

  static final Set<String> SENSITIVE_RESPONSE_FIELDS =
      Set.of(
          "data." + PROCESS_TOKEN,
          "data.url",
          "data." + TRANSACTION_TOKEN,
          "data.transactions." + TRANSACTION_TOKEN,
          "data.customField.cField1");

  static final Set<String> SENSITIVE_DELIVERY_FIELDS =
      Set.of(
          "data[customFields][cField1]",
          "data[processToken]",
          "data[transactionToken]",
          "data[cardToken]");

  static final Set<String> SENSITIVE_HEADERS =
      Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");
}
