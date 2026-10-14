package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class TranzilaThreeDSecureAnswers {

  static final String CHALLENGE_URL = "challengeUrl";

  static final String TRACK_ID = "track_id";

  static final String T3DS_DATA = "t3ds_data";

  static final String VERSION = "version";

  static final String STATUS_CODE = "statusCode";

  static final String STATUS_MESSAGE = "statusMessage";

  static final String XID = "xid";

  static final String CAVV = "cavv";

  static final String ECI = "eci";

  static final String THE_SAMPLES_VERSION = "1.0";

  static final String AUTHENTICATED = "YA";

  static final String AUTHENTICATED_TEXT = "Authenticated successfully.";

  static final String THE_SAMPLES_ECI = "05";

  static final int CRYPTOGRAM_HEX = 40;

  static final String COMPLETED = "Success";

  static final String THE_ONE_PAYMENT_PLAN = "1";

  static final String TWO_DIGITS = "%02d";

  static final char AN_UNSEEN_DIGIT = '*';

  private TranzilaThreeDSecureAnswers() {}

  static TranzilaOperationResult aChallenge(String challengeUrl, String trackId) {
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TranzilaFields.ERROR_CODE, TranzilaJson.number("0"));
    body.put(TranzilaFields.MESSAGE, TranzilaJson.text(TranzilaAnswers.SUCCESS));
    body.put(CHALLENGE_URL, TranzilaJson.text(challengeUrl));
    body.put(TRACK_ID, TranzilaJson.text(trackId));
    return TranzilaOperationResult.succeeded(
        trackId, TranzilaBody.of(TranzilaAnswers.OK, TranzilaJson.object(body)));
  }

  static TranzilaJson.JsonObject theAuthentication(IdGenerator ids) {
    Map<String, TranzilaJson> data = new LinkedHashMap<>();
    data.put(VERSION, TranzilaJson.text(THE_SAMPLES_VERSION));
    data.put(STATUS_CODE, TranzilaJson.text(AUTHENTICATED));
    data.put(STATUS_MESSAGE, TranzilaJson.text(AUTHENTICATED_TEXT));
    data.put(XID, TranzilaJson.text(aCryptogram(ids)));
    data.put(CAVV, TranzilaJson.text(aCryptogram(ids)));
    data.put(ECI, TranzilaJson.text(THE_SAMPLES_ECI));
    return TranzilaJson.object(data);
  }

  static String aCryptogram(IdGenerator ids) {
    return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(ids.hex(CRYPTOGRAM_HEX)));
  }

  static TranzilaOperationResult withTheAuthentication(
      TranzilaOperationResult result, TranzilaJson authentication) {
    if (!(result.body().json() instanceof TranzilaJson.JsonObject answered)
        || !answered.has(TranzilaFields.ORIGINAL_REQUEST)) {
      return result;
    }
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    answered
        .members()
        .forEach(
            (name, value) -> {
              if (TranzilaFields.ORIGINAL_REQUEST.equals(name)) {
                body.put(T3DS_DATA, authentication);
              }
              body.put(name, value);
            });
    return new TranzilaOperationResult(
        result.outcome(),
        result.providerObjectId(),
        result.refusal(),
        result.scenarioId(),
        result.fault(),
        result.insteadOfTheOperation(),
        TranzilaBody.of(result.body().httpStatus(), TranzilaJson.object(body)));
  }

  static TranzilaOperationResult completed(
      TranzilaTransaction transaction,
      TranzilaJson.@Nullable JsonObject authentication,
      TranzilaJson originalRequest) {
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TranzilaFields.ERROR_CODE, TranzilaJson.number("0"));
    body.put(TranzilaFields.MESSAGE, TranzilaJson.text(COMPLETED));
    body.put(TranzilaFields.TRANSACTION_RESULT, theResultOf(transaction));
    if (authentication != null) {
      body.put(T3DS_DATA, authentication);
    }
    body.put(TranzilaFields.ORIGINAL_REQUEST, originalRequest);
    return TranzilaOperationResult.succeeded(
        transaction.transactionIdAsText(),
        TranzilaBody.of(TranzilaAnswers.OK, TranzilaJson.object(body)));
  }

  static TranzilaJson.JsonObject theResultOf(TranzilaTransaction transaction) {
    TranzilaCard card = transaction.card();
    Map<String, TranzilaJson> result = new LinkedHashMap<>();
    result.put(
        TranzilaFields.PROCESSOR_RESPONSE_CODE,
        TranzilaJson.text(transaction.processorResponseCode()));
    result.put(TranzilaFields.TRANSACTION_ID, TranzilaJson.text(transaction.transactionIdAsText()));
    result.put(
        TranzilaFields.AUTH_NUMBER, TranzilaJson.text(String.valueOf(transaction.authNumber())));
    result.put(TranzilaFields.CARD_TYPE, TranzilaJson.text(Integer.toString(card.cardType())));
    result.put(TranzilaFields.CARD_TYPE_NAME, TranzilaJson.text(card.cardTypeName()));
    result.put(TranzilaFields.AMOUNT, TranzilaJson.number(transaction.amount()));
    result.put(TranzilaFields.CURRENCY_CODE, TranzilaJson.text(transaction.currencyCode()));
    result.put(
        TranzilaFields.EXPIRY_MONTH,
        TranzilaJson.text(String.format(TWO_DIGITS, Integer.valueOf(card.expiryMonth()))));
    result.put(
        TranzilaFields.EXPIRY_YEAR,
        TranzilaJson.text(String.format(TWO_DIGITS, Integer.valueOf(card.expiryYear()))));
    result.put(TranzilaFields.PAYMENT_PLAN, TranzilaJson.text(THE_ONE_PAYMENT_PLAN));
    String token = transaction.token();
    if (token != null) {
      result.put(TranzilaFields.TOKEN, TranzilaJson.text(token));
    }
    result.put(TranzilaFields.LAST_4, TranzilaJson.text(card.last4()));
    result.put(
        TranzilaFields.CARD_MASK,
        TranzilaJson.text(card.cardMask().replace(TranzilaCard.UNSEEN.charAt(0), AN_UNSEEN_DIGIT)));
    result.put(TranzilaFields.CARD_LOCALITY, TranzilaJson.text(TranzilaCard.CARD_LOCALITY));
    return TranzilaJson.object(result);
  }

  static TranzilaOperationResult noChallengeToComplete() {
    return TranzilaAnswers.applicationError(
        Integer.parseInt(TranzilaResponseCodes.THREE_D_SECURE_FAILED),
        TranzilaResponseCodes.THREE_D_SECURE_FAILED_TEXT,
        RefusalSubject.noObjectAnsweredTo(TRACK_ID));
  }

  static TranzilaOperationResult notAnsweredYet(String trackId) {
    return TranzilaAnswers.applicationError(
        Integer.parseInt(TranzilaResponseCodes.THREE_D_SECURE_FAILED),
        TranzilaResponseCodes.THREE_D_SECURE_FAILED_TEXT,
        RefusalSubject.theObject(trackId));
  }
}
