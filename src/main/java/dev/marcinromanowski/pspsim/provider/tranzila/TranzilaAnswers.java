package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import java.util.LinkedHashMap;
import java.util.Map;

final class TranzilaAnswers {

  static final int OK = 200;

  static final int BAD_REQUEST = 400;

  static final int UNAUTHORIZED = 401;

  static final int NOT_FOUND = 404;

  static final int METHOD_NOT_ALLOWED = 405;

  static final String SUCCESS = "success";

  private TranzilaAnswers() {}

  static TranzilaOperationResult refusedCredential(String code, String text, String why) {
    return TranzilaOperationResult.failed(
        TranzilaRefusals.theCredentialWasRefused(),
        TranzilaBody.of(UNAUTHORIZED, theErrorSchema(code, text + ": " + why)));
  }

  static TranzilaOperationResult refusedBody(String code, String text, String why) {
    return TranzilaOperationResult.failed(
        TranzilaRefusals.theBodyWasRefused(),
        TranzilaBody.of(BAD_REQUEST, theErrorSchema(code, text + ": " + why)));
  }

  static TranzilaOperationResult refusedSchema(String why) {
    return refusedBody(
        TranzilaErrors.JSON_SCHEMA_MISMATCH, TranzilaErrors.JSON_SCHEMA_MISMATCH_TEXT, why);
  }

  static TranzilaOperationResult applicationError(int code, RefusalSubject refusal) {
    return applicationError(code, TranzilaErrors.theTextOf(code), refusal);
  }

  static TranzilaOperationResult applicationError(
      int code, String message, RefusalSubject refusal) {
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TranzilaFields.ERROR_CODE, TranzilaJson.number(Integer.toString(code)));
    body.put(TranzilaFields.MESSAGE, TranzilaJson.text(message));
    return TranzilaOperationResult.failed(refusal, TranzilaBody.of(OK, TranzilaJson.object(body)));
  }

  static TranzilaJson.JsonObject theErrorSchema(String code, String message) {
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TranzilaFields.ERROR_CODE, TranzilaJson.text(code));
    body.put(TranzilaFields.MESSAGE, TranzilaJson.text(message));
    return TranzilaJson.object(body);
  }

  static TranzilaOperationResult approved(
      TranzilaTransaction transaction, TranzilaJson originalRequest) {
    return TranzilaOperationResult.succeeded(
        transaction.transactionIdAsText(),
        TranzilaBody.of(OK, theSuccessOf(0, SUCCESS, theResultOf(transaction), originalRequest)));
  }

  static TranzilaOperationResult aDuplicateOf(
      TranzilaTransaction original, TranzilaJson originalRequest) {
    return TranzilaOperationResult.succeeded(
        original.transactionIdAsText(),
        TranzilaBody.of(
            OK,
            theSuccessOf(
                0, TranzilaErrors.DUPLICATE_PAYMENT, theResultOf(original), originalRequest)));
  }

  static TranzilaOperationResult declined(TranzilaTransaction transaction) {
    String code = transaction.processorResponseCode();
    Map<String, TranzilaJson> result = new LinkedHashMap<>();
    result.put(TranzilaFields.PROCESSOR_RESPONSE_CODE, TranzilaJson.text(code));
    result.put(
        TranzilaFields.TRANSACTION_ID, TranzilaJson.number(transaction.transactionIdAsText()));
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(
        TranzilaFields.ERROR_CODE,
        TranzilaJson.number(Integer.toString(TranzilaResponseCodes.asAnErrorCode(code))));
    body.put(TranzilaFields.MESSAGE, TranzilaJson.text(TranzilaResponseCodes.theTextOf(code)));
    body.put(TranzilaFields.TRANSACTION_RESULT, TranzilaJson.object(result));
    return TranzilaOperationResult.failed(
        TranzilaRefusals.theTransaction(transaction.transactionId()),
        TranzilaBody.of(OK, TranzilaJson.object(body)));
  }

  private static TranzilaJson.JsonObject theSuccessOf(
      int errorCode, String message, TranzilaJson result, TranzilaJson originalRequest) {
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TranzilaFields.ERROR_CODE, TranzilaJson.number(Integer.toString(errorCode)));
    body.put(TranzilaFields.MESSAGE, TranzilaJson.text(message));
    body.put(TranzilaFields.TRANSACTION_RESULT, result);
    body.put(TranzilaFields.ORIGINAL_REQUEST, originalRequest);
    return TranzilaJson.object(body);
  }

  static TranzilaJson.JsonObject theResultOf(TranzilaTransaction transaction) {
    TranzilaCard card = transaction.card();
    Map<String, TranzilaJson> result = new LinkedHashMap<>();
    result.put(
        TranzilaFields.PROCESSOR_RESPONSE_CODE,
        TranzilaJson.text(transaction.processorResponseCode()));
    result.put(
        TranzilaFields.TRANSACTION_ID, TranzilaJson.number(transaction.transactionIdAsText()));
    result.put(
        TranzilaFields.AUTH_NUMBER, TranzilaJson.text(String.valueOf(transaction.authNumber())));
    result.put(TranzilaFields.RESPONSE_CVV, TranzilaJson.text(transaction.responseCvv()));
    result.put(TranzilaFields.RESPONSE_ID, TranzilaJson.text(transaction.responseId()));
    result.put(TranzilaFields.CARD_TYPE, TranzilaJson.number(Integer.toString(card.cardType())));
    result.put(TranzilaFields.CARD_TYPE_NAME, TranzilaJson.text(card.cardTypeName()));
    result.put(TranzilaFields.CURRENCY_CODE, TranzilaJson.text(transaction.currencyCode()));
    result.put(
        TranzilaFields.EXPIRY_MONTH, TranzilaJson.number(Integer.toString(card.expiryMonth())));
    result.put(
        TranzilaFields.EXPIRY_YEAR, TranzilaJson.number(Integer.toString(card.expiryYear())));
    result.put(TranzilaFields.PAYMENT_PLAN, TranzilaJson.number("1"));
    String token = transaction.token();
    if (token != null) {
      result.put(TranzilaFields.TOKEN, TranzilaJson.text(token));
    }
    result.put(TranzilaFields.LAST_4, TranzilaJson.text(card.last4()));
    result.put(TranzilaFields.CARD_MASK, TranzilaJson.text(card.cardMask()));
    result.put(TranzilaFields.CARD_LOCALITY, TranzilaJson.text(TranzilaCard.CARD_LOCALITY));
    result.put(TranzilaFields.AMOUNT, TranzilaJson.number(transaction.amount()));
    result.put(TranzilaFields.TXN_TYPE, TranzilaJson.text(transaction.type().wire()));
    transaction
        .tranmode()
        .ifPresent(tranmode -> result.put(TranzilaFields.TRANMODE, TranzilaJson.text(tranmode)));
    return TranzilaJson.object(result);
  }
}
