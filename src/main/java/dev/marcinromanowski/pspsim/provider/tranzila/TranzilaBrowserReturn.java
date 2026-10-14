package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class TranzilaBrowserReturn {

  static final Set<String> THE_NAMES_THE_RETURN_SETS =
      Set.of(
          TranzilaFields.SUPPLIER,
          TranzilaFields.SUM,
          TranzilaFields.CURRENCY,
          TranzilaFields.RESPONSE,
          TranzilaFields.TRANSACTION_ID,
          TranzilaFields.INDEX,
          TranzilaFields.CCNO,
          TranzilaFields.RESPONSE_CV,
          TranzilaFields.RESPONSE_ID,
          TranzilaFields.CARDTYPE,
          TranzilaFields.CARDISSUER,
          TranzilaFields.CARDACQUIRER,
          TranzilaFields.TRANZILA_TK,
          TranzilaFields.TXN_TYPE,
          TranzilaFields.TRANMODE,
          TranzilaFields.CONFIRMATION_CODE);

  static final String THE_SAMPLES_ISSUER = "1";

  static final String ISRACARD_ACQUIRES = "1";

  static final String VISA_CAL_ACQUIRES = "2";

  static final String DINERS_ACQUIRES = "3";

  static final String AMEX_ACQUIRES = "4";

  static final String NOT_ENTERED = "0";

  private TranzilaBrowserReturn() {}

  static SequencedMap<String, String> theFieldsOf(
      TranzilaPage page, @Nullable TranzilaTransaction transaction) {
    SequencedMap<String, String> fields = new LinkedHashMap<>();
    fields.put(TranzilaFields.SUPPLIER, page.terminalName());
    fields.put(TranzilaFields.SUM, page.sum());
    fields.put(TranzilaFields.CURRENCY, page.currency().numeric());
    fields.put(TranzilaFields.RESPONSE, theResponseOf(page, transaction));
    fields.putAll(page.echoed());
    if (transaction != null) {
      fields.put(TranzilaFields.TRANSACTION_ID, transaction.transactionIdAsText());
      fields.put(TranzilaFields.INDEX, transaction.transactionIdAsText());
    }
    theCardOf(page, transaction)
        .ifPresent(
            card -> {
              fields.put(TranzilaFields.CCNO, card.last4());
              fields.put(
                  TranzilaFields.RESPONSE_CV,
                  transaction == null ? NOT_ENTERED : transaction.responseCvv());
              fields.put(
                  TranzilaFields.RESPONSE_ID,
                  transaction == null ? NOT_ENTERED : transaction.responseId());
              fields.put(TranzilaFields.CARDTYPE, Integer.toString(card.cardType()));
              fields.put(TranzilaFields.CARDISSUER, THE_SAMPLES_ISSUER);
              fields.put(TranzilaFields.CARDACQUIRER, theAcquirerOf(card.cardType()));
            });
    theTokenOf(page, transaction).ifPresent(token -> fields.put(TranzilaFields.TRANZILA_TK, token));
    if (transaction != null) {
      fields.put(TranzilaFields.TXN_TYPE, transaction.type().wire());
    }
    fields.put(TranzilaFields.TRANMODE, page.tranmode().wire());
    theRequestParamsOf(page).forEach(fields::putIfAbsent);
    return Collections.unmodifiableSequencedMap(fields);
  }

  static SequencedMap<String, String> theNotifyOf(
      TranzilaPage page, @Nullable TranzilaTransaction transaction) {
    SequencedMap<String, String> fields = new LinkedHashMap<>(theFieldsOf(page, transaction));
    String authNumber = transaction == null ? null : transaction.authNumber();
    if (authNumber != null) {
      fields.put(TranzilaFields.CONFIRMATION_CODE, authNumber);
    }
    return Collections.unmodifiableSequencedMap(fields);
  }

  static String theAcquirerOf(int cardType) {
    return switch (cardType) {
      case TranzilaCard.ISRACARD -> ISRACARD_ACQUIRES;
      case TranzilaCard.AMEX -> AMEX_ACQUIRES;
      case TranzilaCard.DINERS -> DINERS_ACQUIRES;
      default -> VISA_CAL_ACQUIRES;
    };
  }

  static boolean isApproved(TranzilaPage page, @Nullable TranzilaTransaction transaction) {
    return TranzilaResponseCodes.approves(theResponseOf(page, transaction));
  }

  static String theResponseOf(TranzilaPage page, @Nullable TranzilaTransaction transaction) {
    if (transaction != null) {
      return transaction.processorResponseCode();
    }
    return String.valueOf(page.response());
  }

  static Optional<TranzilaCard> theCardOf(
      TranzilaPage page, @Nullable TranzilaTransaction transaction) {
    if (transaction != null) {
      return Optional.of(transaction.card());
    }
    String mask = page.cardMask();
    Integer type = page.cardType();
    if (mask == null || type == null) {
      return Optional.empty();
    }
    return Optional.of(new TranzilaCard(mask, type.intValue(), 0, 0));
  }

  static Optional<String> theTokenOf(TranzilaPage page, @Nullable TranzilaTransaction transaction) {
    if (transaction != null) {
      return transaction.approved() ? Optional.ofNullable(transaction.token()) : Optional.empty();
    }
    return Optional.ofNullable(page.token());
  }

  static SequencedMap<String, String> theRequestParamsOf(TranzilaPage page) {
    return theRequestParamsIn(page.requestParams());
  }

  static SequencedMap<String, String> theRequestParamsIn(@Nullable String stored) {
    SequencedMap<String, String> echoed = new LinkedHashMap<>();
    if (stored == null) {
      return echoed;
    }
    TranzilaJsonReader.objectIn(stored)
        .ifPresent(
            params -> {
              for (Map.Entry<String, TranzilaJson> member : params.members().entrySet()) {
                echoed.put(member.getKey(), asReturned(member.getValue()));
              }
            });
    return echoed;
  }

  static String asReturned(TranzilaJson value) {
    return switch (value) {
      case TranzilaJson.JsonText text -> text.value();
      case TranzilaJson.JsonNumber number -> number.lexical();
      default -> TranzilaJsonWriter.toText(value);
    };
  }
}
