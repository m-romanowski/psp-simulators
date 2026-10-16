package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

record TranzilaHostedFieldsEcho(
    String thtk,
    String amount,
    TranzilaPageCurrency currency,
    TranzilaTranmode tranmode,
    boolean tokenize,
    @Nullable String notifyUrl,
    SequencedMap<String, String> userFormData) {

  static final String TOKENIZED = "true";

  static final String NOT_TOKENIZED = "false";

  TranzilaHostedFieldsEcho {
    userFormData = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(userFormData));
  }

  SequencedMap<String, String> stored() {
    SequencedMap<String, String> kept = new LinkedHashMap<>();
    kept.put(TranzilaFields.THTK, thtk);
    kept.put(TranzilaFields.AMOUNT, amount);
    kept.put(TranzilaHostedFieldsRequest.CURRENCY_CODE, currency.numeric());
    kept.put(TranzilaFields.TRAN_MODE, tranmode.wire());
    kept.put(TranzilaFields.TOKENIZE, tokenize ? TOKENIZED : NOT_TOKENIZED);
    String notify = notifyUrl;
    if (notify != null) {
      kept.put(TranzilaHostedFieldsRequest.NOTIFY_URL, notify);
    }
    return kept;
  }

  static TranzilaHostedFieldsEcho ofTheStored(
      SequencedMap<String, String> kept, SequencedMap<String, String> userFormData) {
    return new TranzilaHostedFieldsEcho(
        String.valueOf(kept.get(TranzilaFields.THTK)),
        String.valueOf(kept.get(TranzilaFields.AMOUNT)),
        TranzilaStoredForm.currencyOf(
            String.valueOf(kept.get(TranzilaHostedFieldsRequest.CURRENCY_CODE))),
        TranzilaStoredForm.tranmodeOf(String.valueOf(kept.get(TranzilaFields.TRAN_MODE))),
        TOKENIZED.equals(kept.get(TranzilaFields.TOKENIZE)),
        kept.get(TranzilaHostedFieldsRequest.NOTIFY_URL),
        userFormData);
  }
}
