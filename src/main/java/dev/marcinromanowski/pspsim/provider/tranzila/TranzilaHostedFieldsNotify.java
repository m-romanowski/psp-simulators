package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

final class TranzilaHostedFieldsNotify {

  private TranzilaHostedFieldsNotify() {}

  static SequencedMap<String, String> theFieldsOf(
      TranzilaTransaction transaction,
      TranzilaHostedFieldsRequest request,
      @Nullable String requestParams) {
    TranzilaCard card = transaction.card();
    SequencedMap<String, String> fields = new LinkedHashMap<>();
    fields.put(TranzilaFields.SUPPLIER, transaction.terminalName());
    fields.put(TranzilaFields.SUM, request.amount());
    fields.put(TranzilaFields.CURRENCY, request.currency().numeric());
    fields.put(TranzilaFields.RESPONSE, transaction.processorResponseCode());
    request
        .userFormData()
        .forEach(
            (name, value) -> {
              if (!TranzilaBrowserReturn.THE_NAMES_THE_RETURN_SETS.contains(name)) {
                fields.put(name, value);
              }
            });
    fields.put(TranzilaFields.TRANSACTION_ID, transaction.transactionIdAsText());
    fields.put(TranzilaFields.INDEX, transaction.transactionIdAsText());
    fields.put(TranzilaFields.CCNO, card.last4());
    fields.put(TranzilaFields.RESPONSE_CV, transaction.responseCvv());
    fields.put(TranzilaFields.RESPONSE_ID, transaction.responseId());
    fields.put(TranzilaFields.CARDTYPE, Integer.toString(card.cardType()));
    fields.put(TranzilaFields.CARDISSUER, TranzilaBrowserReturn.THE_SAMPLES_ISSUER);
    fields.put(TranzilaFields.CARDACQUIRER, TranzilaBrowserReturn.theAcquirerOf(card.cardType()));
    String token = transaction.token();
    if (request.tokenize() && transaction.approved() && token != null) {
      fields.put(TranzilaFields.TRANZILA_TK, token);
    }
    fields.put(TranzilaFields.TXN_TYPE, transaction.type().wire());
    fields.put(TranzilaFields.TRANMODE, request.tranmode().wire());
    TranzilaBrowserReturn.theRequestParamsIn(requestParams).forEach(fields::putIfAbsent);
    String authNumber = transaction.authNumber();
    if (authNumber != null) {
      fields.put(TranzilaFields.CONFIRMATION_CODE, authNumber);
    }
    return Collections.unmodifiableSequencedMap(fields);
  }
}
