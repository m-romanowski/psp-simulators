package dev.marcinromanowski.pspsim.provider.tranzila;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class TranzilaHostedFieldsAnswer {

  static final String SUCCESS = "success";

  static final String ERROR = "error";

  static final String CREDIT_CARD_LAST_4_DIGITS = "credit_card_last_4_digits";

  static final String CVV_STATUS = "cvv_status";

  static final String ID_STATUS = "id_status";

  static final String CARD_ISSUER = "card_issuer";

  static final String TOTAL_INSTALLMENTS_NUMBER = "total_installments_number";

  static final String CODE = "code";

  static final String PARAM = "param";

  static final String EVERY_CARD_IS_ISSUED_BY = "1";

  static final String THE_ONE_PAYMENT_PLAN = "1";

  static final char AN_UNSEEN_DIGIT = '*';

  static final String TWO_DIGITS = "%02d";

  private TranzilaHostedFieldsAnswer() {}

  static TranzilaJson.JsonObject theResponseOf(
      TranzilaTransaction transaction, TranzilaHostedFieldsEcho request) {
    TranzilaCard card = transaction.card();
    boolean approved = transaction.approved();
    String code = transaction.processorResponseCode();
    Map<String, TranzilaJson> response = new LinkedHashMap<>();
    response.put(SUCCESS, new TranzilaJson.JsonFlag(approved));
    response.put(
        ERROR, approved ? nothing() : TranzilaJson.text(TranzilaResponseCodes.theTextOf(code)));
    response.put(TranzilaFields.PROCESSOR_RESPONSE_CODE, TranzilaJson.text(code));
    response.put(
        TranzilaFields.TRANSACTION_ID, TranzilaJson.text(transaction.transactionIdAsText()));
    String authNumber = transaction.authNumber();
    response.put(
        TranzilaFields.AUTH_NUMBER, authNumber == null ? nothing() : TranzilaJson.text(authNumber));
    response.put(TranzilaFields.AMOUNT, TranzilaJson.text(inTwoPlaces(transaction.amount())));
    response.put(
        TranzilaFields.CURRENCY_CODE,
        TranzilaJson.number(theNumericCodeOf(transaction.currencyCode())));
    response.put(CREDIT_CARD_LAST_4_DIGITS, TranzilaJson.text(card.last4()));
    response.put(
        TranzilaFields.EXPIRY_MONTH,
        TranzilaJson.text(String.format(TWO_DIGITS, Integer.valueOf(card.expiryMonth()))));
    response.put(
        TranzilaFields.EXPIRY_YEAR,
        TranzilaJson.text(String.format(TWO_DIGITS, Integer.valueOf(card.expiryYear()))));
    response.put(TranzilaFields.USER_FORM_DATA, theUserFormDataOf(request));
    response.put(TranzilaFields.CARD_TYPE, TranzilaJson.text(Integer.toString(card.cardType())));
    response.put(
        TranzilaFields.CARD_MASK,
        TranzilaJson.text(card.cardMask().replace(TranzilaCard.UNSEEN.charAt(0), AN_UNSEEN_DIGIT)));
    response.put(TranzilaFields.CARD_LOCALITY, TranzilaJson.text(TranzilaCard.CARD_LOCALITY));
    response.put(TranzilaFields.TXN_TYPE, TranzilaJson.text(transaction.type().wire()));
    response.put(
        TranzilaFields.TRANMODE,
        TranzilaJson.text(transaction.tranmode().orElse(request.tranmode().wire())));
    response.put(TranzilaFields.CARD_TYPE_NAME, TranzilaJson.text(card.cardTypeName()));
    response.put(CVV_STATUS, TranzilaJson.text(transaction.responseCvv()));
    response.put(ID_STATUS, TranzilaJson.text(transaction.responseId()));
    response.put(CARD_ISSUER, TranzilaJson.text(EVERY_CARD_IS_ISSUED_BY));
    String token = transaction.token();
    if (request.tokenize() && approved && token != null) {
      response.put(TranzilaFields.TOKEN, TranzilaJson.text(token));
    }
    response.put(TranzilaFields.PAYMENT_PLAN, TranzilaJson.number(THE_ONE_PAYMENT_PLAN));
    response.put(TOTAL_INSTALLMENTS_NUMBER, nothing());
    return TranzilaJson.object(response);
  }

  static String theNumericCodeOf(String currency) {
    Optional<TranzilaPageCurrency> known = TranzilaPageCurrency.alphabetic(currency);
    if (known.isEmpty()) {
      throw new IllegalStateException(
          currency + " has no numeric code the hosted fields answer could carry");
    }
    return known.get().numeric();
  }

  static String inTwoPlaces(String amount) {
    return new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  static TranzilaJson.JsonObject theUserFormDataOf(TranzilaHostedFieldsEcho request) {
    Map<String, TranzilaJson> echoed = new LinkedHashMap<>();
    request.userFormData().forEach((name, value) -> echoed.put(name, TranzilaJson.text(value)));
    return TranzilaJson.object(echoed);
  }

  static TranzilaJson.JsonObject aChallenge(String challengeUrl, String trackId) {
    Map<String, TranzilaJson> answered = new LinkedHashMap<>();
    answered.put(TranzilaThreeDSecureAnswers.CHALLENGE_URL, TranzilaJson.text(challengeUrl));
    answered.put(TranzilaThreeDSecureAnswers.TRACK_ID, TranzilaJson.text(trackId));
    return TranzilaJson.object(answered);
  }

  static TranzilaJson.JsonArray theErrorsOf(List<TranzilaHostedFieldsRequest.Refusal> refusals) {
    List<TranzilaJson> errors = new ArrayList<>();
    for (TranzilaHostedFieldsRequest.Refusal refusal : refusals) {
      Map<String, TranzilaJson> error = new LinkedHashMap<>();
      error.put(CODE, TranzilaJson.number(Integer.toString(refusal.code())));
      error.put(PARAM, TranzilaJson.text(refusal.param()));
      error.put(TranzilaFields.MESSAGE, TranzilaJson.text(refusal.message()));
      errors.add(TranzilaJson.object(error));
    }
    return TranzilaJson.array(errors);
  }

  static TranzilaJson.JsonObject answered(
      @Nullable TranzilaJson errors, @Nullable TranzilaJson response, @Nullable String secret) {
    Map<String, TranzilaJson> hashed = new LinkedHashMap<>();
    hashed.put(TranzilaFields.ERRORS, errors == null ? nothing() : errors);
    hashed.put(TranzilaFields.TRANSACTION_RESPONSE, response == null ? nothing() : response);
    Map<String, TranzilaJson> answered = new LinkedHashMap<>(hashed);
    answered.put(
        TranzilaFields.RESPONSE_HASH,
        secret == null
            ? nothing()
            : TranzilaJson.text(
                TranzilaResponseHash.of(
                    TranzilaJsonWriter.toText(TranzilaJson.object(hashed)), secret)));
    return TranzilaJson.object(answered);
  }

  private static TranzilaJson nothing() {
    return new TranzilaJson.JsonNothing();
  }
}
