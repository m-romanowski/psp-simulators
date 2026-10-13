package dev.marcinromanowski.pspsim.provider.azericard;

import java.util.List;
import java.util.Set;

final class AzericardFields {

  private AzericardFields() {}

  static final String AMOUNT = "AMOUNT";
  static final String CURRENCY = "CURRENCY";
  static final String ORDER = "ORDER";
  static final String DESC = "DESC";
  static final String MERCH_NAME = "MERCH_NAME";
  static final String MERCH_URL = "MERCH_URL";
  static final String TERMINAL = "TERMINAL";
  static final String EMAIL = "EMAIL";
  static final String TRTYPE = "TRTYPE";
  static final String COUNTRY = "COUNTRY";
  static final String MERCH_GMT = "MERCH_GMT";
  static final String BACKREF = "BACKREF";
  static final String TIMESTAMP = "TIMESTAMP";
  static final String NONCE = "NONCE";
  static final String LANG = "LANG";
  static final String P_SIGN = "P_SIGN";
  static final String NAME = "NAME";
  static final String M_INFO = "M_INFO";
  static final String RRN = "RRN";
  static final String INT_REF = "INT_REF";
  static final String ACTION = "ACTION";
  static final String RC = "RC";
  static final String APPROVAL = "APPROVAL";
  static final String TRAN_TRTYPE = "TRAN_TRTYPE";
  static final String TOKEN = "TOKEN";
  static final String TOKEN_ACTION = "TOKEN_ACTION";
  static final String MERCH_TRAN_STATE = "MERCH_TRAN_STATE";
  static final String MERCH_RN_ID = "MERCH_RN_ID";
  static final String MIT_AGREEMENT = "MIT_AGREEMENT";
  static final String EXT_NET_REF = "EXT_NET_REF";
  static final String CARD = "CARD";
  static final String EXP = "EXP";
  static final String EXP_YEAR = "EXP_YEAR";
  static final String CVC2 = "CVC2";
  static final String CVC2_RC = "CVC2_RC";

  static final AzericardRequiredField REQUIRED_TERMINAL =
      new AzericardRequiredField(TERMINAL, "merchant terminal");

  static final AzericardRequiredField REQUIRED_TRTYPE =
      new AzericardRequiredField(TRTYPE, "transaction type");

  static final AzericardRequiredField REQUIRED_ORDER =
      new AzericardRequiredField(ORDER, "merchant order");

  static final AzericardRequiredField REQUIRED_AMOUNT =
      new AzericardRequiredField(AMOUNT, "order total amount");

  static final AzericardRequiredField REQUIRED_CURRENCY =
      new AzericardRequiredField(CURRENCY, "order currency");

  static final AzericardRequiredField REQUIRED_TIMESTAMP =
      new AzericardRequiredField(TIMESTAMP, "merchant transaction timestamp");

  static final AzericardRequiredField REQUIRED_NONCE = new AzericardRequiredField(NONCE, "nonce");

  static final AzericardRequiredField REQUIRED_P_SIGN =
      new AzericardRequiredField(P_SIGN, "merchant MAC");

  static final AzericardRequiredField REQUIRED_RRN =
      new AzericardRequiredField(RRN, "retrieval reference number");

  static final AzericardRequiredField REQUIRED_INT_REF =
      new AzericardRequiredField(INT_REF, "gateway internal reference number");

  static final AzericardRequiredField REQUIRED_TRAN_TRTYPE =
      new AzericardRequiredField(TRAN_TRTYPE, "original transaction type");

  static final AzericardRequiredField REQUIRED_TOKEN =
      new AzericardRequiredField(TOKEN, "saved card token");

  static final AzericardRequiredField REQUIRED_TOKEN_ACTION =
      new AzericardRequiredField(TOKEN_ACTION, "token action");

  static final AzericardRequiredField REQUIRED_MERCH_TRAN_STATE =
      new AzericardRequiredField(MERCH_TRAN_STATE, "authorization initiation indicator");

  static final AzericardRequiredField REQUIRED_EXP =
      new AzericardRequiredField(EXP, "card expiration month");

  static final AzericardRequiredField REQUIRED_EXP_YEAR =
      new AzericardRequiredField(EXP_YEAR, "card expiration year");

  static final AzericardRequiredField REQUIRED_CVC2 =
      new AzericardRequiredField(CVC2, "card verification code");

  static final AzericardRequiredField REQUIRED_CVC2_RC =
      new AzericardRequiredField(CVC2_RC, "CVC2 reason code");

  static final String CVC2_PRESENT = "1";

  static final List<String> CVC2_REASON_CODES = List.of(CVC2_PRESENT, "0", "2");

  static final AzericardRequiredField REQUIRED_BACKREF =
      new AzericardRequiredField(BACKREF, "merchant URL for posting authorization result");

  static final AzericardRequiredField REQUIRED_MERCH_RN_ID =
      new AzericardRequiredField(MERCH_RN_ID, "merchant recurring nonce");

  static final AzericardRequiredField REQUIRED_MIT_AGREEMENT =
      new AzericardRequiredField(MIT_AGREEMENT, "payment agreement indicator");

  static final List<AzericardRequiredField> REQUIRED_BEFORE_THE_OPERATION_IS_KNOWN =
      List.of(REQUIRED_TERMINAL, REQUIRED_TRTYPE);

  static final List<AzericardRequiredField> REQUIRED_ON_AUTHORIZATION =
      List.of(
          REQUIRED_ORDER,
          REQUIRED_AMOUNT,
          REQUIRED_CURRENCY,
          REQUIRED_TIMESTAMP,
          REQUIRED_NONCE,
          REQUIRED_P_SIGN);

  static final List<AzericardRequiredField> REQUIRED_ON_SETTLEMENT =
      List.of(
          REQUIRED_ORDER,
          REQUIRED_AMOUNT,
          REQUIRED_CURRENCY,
          REQUIRED_RRN,
          REQUIRED_INT_REF,
          REQUIRED_TIMESTAMP,
          REQUIRED_NONCE,
          REQUIRED_P_SIGN);

  static final List<AzericardRequiredField> REQUIRED_ON_STATUS =
      List.of(
          REQUIRED_TRAN_TRTYPE,
          REQUIRED_ORDER,
          REQUIRED_TIMESTAMP,
          REQUIRED_NONCE,
          REQUIRED_P_SIGN);

  static final Set<String> CARD_DATA = Set.of(CARD, EXP, EXP_YEAR, CVC2);

  static final Set<String> SENSITIVE_REQUEST_FIELDS = Set.of(M_INFO, CARD, EXP, EXP_YEAR, CVC2);

  static final Set<String> SENSITIVE_RESPONSE_FIELDS = Set.of();

  static final Set<String> SENSITIVE_DELIVERY_FIELDS = Set.of();

  static final Set<String> SENSITIVE_HEADERS =
      Set.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key");
}
