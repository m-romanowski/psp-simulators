package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

final class TranzilaHostedFieldsErrors {

  static final int MANDATORY_KEY_IS_MISSING = 10000;

  static final int INVALID_CREDIT_CARD_NUMBER = 10002;

  static final int INVALID_CVV = 10003;

  static final int INVALID_EXPIRATION_MONTH = 10004;

  static final int INVALID_EXPIRATION_YEAR = 10005;

  static final int INVALID_AMOUNT = 10006;

  static final int INVALID_PAYMENT_PLAN = 10007;

  static final int INVALID_CARD_HOLDER_ID_NUMBER = 10011;

  static final int INVALID_CURRENCY = 10012;

  static final int TERMINAL_NOT_FOUND = 10013;

  static final int INVALID_TRAN_MODE = 10014;

  static final int ILLEGAL_OPERATION = 912791;

  static final String ILLEGAL_OPERATION_TEXT = "Illegal Operation 912791";

  static final int THE_NOTIFY_URL_IS_NO_TARGET = 400;

  static final SequencedMap<Integer, String> THE_PUBLISHED_TABLE = published();

  private TranzilaHostedFieldsErrors() {}

  private static SequencedMap<Integer, String> published() {
    SequencedMap<Integer, String> table = new LinkedHashMap<>();
    table.put(Integer.valueOf(MANDATORY_KEY_IS_MISSING), "MANDATORY KEY IS MISSING");
    table.put(Integer.valueOf(10001), "INVALID TERMINAL NAME");
    table.put(Integer.valueOf(INVALID_CREDIT_CARD_NUMBER), "INVALID CREDIT CARD NUMBER");
    table.put(Integer.valueOf(INVALID_CVV), "INVALID CVV");
    table.put(Integer.valueOf(INVALID_EXPIRATION_MONTH), "INVALID EXPIRATION MONTH");
    table.put(Integer.valueOf(INVALID_EXPIRATION_YEAR), "INVALID EXPIRATION YEAR");
    table.put(Integer.valueOf(INVALID_AMOUNT), "INVALID AMOUNT OR AMOUNT ZERO");
    table.put(Integer.valueOf(INVALID_PAYMENT_PLAN), "INVALID PAYMENT PLAN");
    table.put(Integer.valueOf(10008), "INVALID TOTAL INSTALLMENTS NUMBER");
    table.put(Integer.valueOf(10009), "INVALID FIRST INSTALLMENT AMOUNT");
    table.put(Integer.valueOf(10010), "INVALID OTHER INSTALLMENTS AMOUNT");
    table.put(Integer.valueOf(INVALID_CARD_HOLDER_ID_NUMBER), "INVALID CARD HOLDER ID NUMBER");
    table.put(Integer.valueOf(INVALID_CURRENCY), "INVALID CURRENCY");
    table.put(Integer.valueOf(TERMINAL_NOT_FOUND), "TERMINAL NOT FOUND OR BAD CONFIGURATION");
    table.put(Integer.valueOf(INVALID_TRAN_MODE), "INVALID TRAN MODE");
    table.put(Integer.valueOf(10015), "TOTAL INSTALLMENTS NUMBER MISSING");
    table.put(Integer.valueOf(10016), "INSTALLMENTS AMOUNT FIELDS MISSING");
    return Collections.unmodifiableSequencedMap(table);
  }

  static TranzilaHostedFieldsRequest.Refusal of(int code, String param) {
    String published = THE_PUBLISHED_TABLE.get(Integer.valueOf(code));
    if (published == null) {
      throw new IllegalArgumentException(
          code + " is no code of the hosted fields error table tranzila publishes");
    }
    return new TranzilaHostedFieldsRequest.Refusal(code, param, published);
  }

  static TranzilaHostedFieldsRequest.Refusal theNotifyUrlIsNoTarget(String inItsOwnWords) {
    return new TranzilaHostedFieldsRequest.Refusal(
        THE_NOTIFY_URL_IS_NO_TARGET, TranzilaHostedFieldsRequest.NOTIFY_URL, inItsOwnWords);
  }

  static TranzilaHostedFieldsRequest.Refusal theHandshakeDoesNotLockIt() {
    return new TranzilaHostedFieldsRequest.Refusal(
        ILLEGAL_OPERATION, TranzilaFields.THTK, ILLEGAL_OPERATION_TEXT);
  }
}
