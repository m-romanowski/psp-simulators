package dev.marcinromanowski.pspsim.provider.autra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AutraErrors {

  private AutraErrors() {}

  static final String BAD_REQUEST = "BAD_REQUEST";

  static final String INVALID_JSON = "INVALID_JSON";

  static final String MISSING_DOCUMENT_ID = "MISSING_DOCUMENT_ID";

  static final String MISSING_TRANSACTIONAL_TOKEN = "MISSING_TRANSACTIONAL_TOKEN";

  static final String INVALID_AMOUNT = "INVALID_AMOUNT";

  static final String INVALID_STATUS = "INVALID_STATUS";

  static final String INVALID_PAYMENT_ID = "INVALID_PAYMENT_ID";

  static final String TENANT_REQUIRED = "TENANT_REQUIRED";

  static final String INVALID_DOCUMENT = "INVALID_DOCUMENT";

  static final String PAYMENT_LOOKUP_ERROR = "PAYMENT_LOOKUP_ERROR";

  static final String INVALID_FROM = "INVALID_FROM";

  static final String INVALID_TO = "INVALID_TO";

  static final String INVALID_RANGE = "INVALID_RANGE";

  static final String RANGE_TOO_LARGE = "RANGE_TOO_LARGE";

  static final String INVALID_TYPE = "INVALID_TYPE";

  static final String INVALID_PAGE = "INVALID_PAGE";

  static final String INVALID_LIMIT = "INVALID_LIMIT";

  static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";

  static final String MERCHANT_NOT_FOUND = "MERCHANT_NOT_FOUND";

  static final String ORDER_ALREADY_PREAUTHORIZED = "ORDER_ALREADY_PREAUTHORIZED";

  static final String ORDER_ALREADY_APPROVED = "ORDER_ALREADY_APPROVED";

  static final String INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS";

  static final String CARD_DECLINED = "CARD_DECLINED";

  static final String AUTHORIZER_REJECTED = "AUTHORIZER_REJECTED";

  static final String TOKEN_NOT_AVAILABLE = "TOKEN_NOT_AVAILABLE";

  static final String INVALID_CARD_BRAND = "INVALID_CARD_BRAND";

  static final String MSG_GENERAL_ERROR = "MSG_GENERAL_ERROR";

  static final String UNAUTHORIZED = "UNAUTHORIZED";

  static final String AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE =
      "AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE";

  static final String CANT_REFUND_TRANSACTION_FROM_TODAY = "CANT_REFUND_TRANSACTION_FROM_TODAY";

  static final String ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD = "ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD";

  static final String CARD_BRAND_NOT_SUPPORTED = "CARD_BRAND_NOT_SUPPORTED";

  static final String ORIGINAL_CYCLE_NOT_ACCEPTED = "ORIGINAL_CYCLE_NOT_ACCEPTED";

  static final List<String> THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES =
      AutraDecline.theCodesADeclineOfThisContractCarries();

  static final String DECLINE_CODES =
      "provider autra answers a refusal as errors[], an array whose items carry a code and a msg"
          + " and nothing else, and a scenario names one of the codes its contract publishes for"
          + " the acquiring surface: "
          + String.join(", ", THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES)
          + ". The reason an issuer gave is not in the code but inside msg, as a two-digit code, a"
          + " space-hyphen-space and a text, so every issuer refusal is AUTHORIZER_REJECTED and"
          + " which of the published strings accompanies it is the account setting "
          + AutraAuthorizerMessage.SETTING
          + " rather than a value this rule can name";

  static String theCodesAScenarioMayName() {
    return String.join("|", THE_CODES_A_DECLINE_OF_THIS_CONTRACT_CARRIES);
  }

  static AutraJson.JsonObject item(String code, String msg) {
    Map<String, AutraJson> member = new LinkedHashMap<>();
    member.put(AutraFields.CODE, AutraJson.text(code));
    member.put(AutraFields.MSG, AutraJson.text(msg));
    return AutraJson.object(member);
  }

  static AutraJson.JsonObject envelope(String code, String msg) {
    return envelope(List.of(item(code, msg)));
  }

  static AutraJson.JsonObject envelope(List<AutraJson.JsonObject> items) {
    List<AutraJson> listed = new ArrayList<>(items);
    return AutraJson.object(Map.of(AutraFields.ERRORS, AutraJson.array(listed)));
  }

  static AutraJson.JsonObject oauthEnvelope(String message) {
    return AutraJson.object(Map.of(AutraFields.OAUTH_ERROR, AutraJson.text(message)));
  }
}
