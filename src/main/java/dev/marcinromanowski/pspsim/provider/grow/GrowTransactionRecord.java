package dev.marcinromanowski.pspsim.provider.grow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class GrowTransactionRecord {

  private GrowTransactionRecord() {}

  private static final String ZERO = "0";

  private static final String ONE = "1";

  private static final String CARD_TRANSACTION_TYPE_ID = "1";

  private static final String REGULAR_PAYMENT_TYPE = "2";

  private static final String NO_PAYMENT_TYPE = "0";

  static GrowJson lookedUp(
      GrowTransaction transaction, String processId, String processToken, GrowJson customField) {
    GrowRecordedCard card = transaction.card();
    return GrowJson.object(
        GrowResponseFields.ASMACHTA, GrowJson.text(transaction.asmachta()),
        GrowResponseFields.CARD_SUFFIX, GrowJson.text(card.suffix()),
        GrowResponseFields.CARD_TYPE, GrowJson.text(card.type()),
        GrowResponseFields.CARD_TYPE_CODE, code(card.typeCode()),
        GrowResponseFields.CARD_BRAND, GrowJson.text(card.brand()),
        GrowResponseFields.CARD_BRAND_CODE, code(card.brandCode()),
        GrowResponseFields.CARD_EXP, GrowJson.text(card.expiry()),
        GrowResponseFields.FIRST_PAYMENT_SUM, number(ZERO),
        GrowResponseFields.PERIODICAL_PAYMENT_SUM, number(ZERO),
        GrowResponseFields.STATUS, GrowJson.text(transaction.nativeStatus().label()),
        GrowResponseFields.STATUS_CODE, number(transaction.nativeStatus().code()),
        GrowResponseFields.TRANSACTION_TYPE_ID, GrowJson.text(CARD_TRANSACTION_TYPE_ID),
        GrowResponseFields.PAYMENT_TYPE, GrowJson.text(REGULAR_PAYMENT_TYPE),
        GrowResponseFields.SUM, GrowJson.text(transaction.amount().value()),
        GrowResponseFields.PAYMENTS_NUM, number(ZERO),
        GrowResponseFields.ALL_PAYMENTS_NUM, number(ONE),
        GrowResponseFields.PAYMENT_DATE, GrowJson.text(transaction.paymentDate()),
        GrowResponseFields.DESCRIPTION, GrowJson.text(transaction.description()),
        GrowResponseFields.FULL_NAME, GrowJson.text(transaction.payerFullName()),
        GrowResponseFields.PAYER_PHONE, GrowJson.text(transaction.payerPhone()),
        GrowResponseFields.PAYER_EMAIL, GrowJson.text(orEmpty(transaction.payerEmail())),
        GrowResponseFields.TRANSACTION_ID, number(transaction.transactionId()),
        GrowResponseFields.TRANSACTION_TOKEN,
            GrowJson.text(orEmpty(transaction.transactionToken())),
        GrowResponseFields.PROCESS_ID, GrowJson.text(processId),
        GrowResponseFields.PROCESS_TOKEN, GrowJson.text(processToken),
        GrowResponseFields.CUSTOM_FIELD, customField);
  }

  static GrowJson storedCardCharge(GrowTransaction transaction) {
    GrowRecordedCard card = transaction.card();
    return GrowJson.object(
        GrowResponseFields.ASMACHTA, GrowJson.text(transaction.asmachta()),
        GrowResponseFields.CARD_SUFFIX, GrowJson.text(card.suffix()),
        GrowResponseFields.CARD_TYPE, GrowJson.text(card.type()),
        GrowResponseFields.CARD_TYPE_CODE, code(card.typeCode()),
        GrowResponseFields.CARD_BRAND, GrowJson.text(card.brand()),
        GrowResponseFields.CARD_BRAND_CODE, code(card.brandCode()),
        GrowResponseFields.CARD_EXP, GrowJson.text(card.expiry()),
        GrowResponseFields.FIRST_PAYMENT_SUM, number(ZERO),
        GrowResponseFields.PERIODICAL_PAYMENT_SUM, number(ZERO),
        GrowResponseFields.STATUS, GrowJson.text(transaction.nativeStatus().label()),
        GrowResponseFields.STATUS_CODE, number(transaction.nativeStatus().code()),
        GrowResponseFields.TRANSACTION_TYPE_ID, GrowJson.text(CARD_TRANSACTION_TYPE_ID),
        GrowResponseFields.PAYMENT_TYPE, GrowJson.text(REGULAR_PAYMENT_TYPE),
        GrowResponseFields.SUM, GrowJson.text(transaction.amount().value()),
        GrowResponseFields.PAYMENTS_NUM, number(ZERO),
        GrowResponseFields.ALL_PAYMENTS_NUM, number(ONE),
        GrowResponseFields.PAYMENT_DATE, GrowJson.text(transaction.paymentDate()),
        GrowResponseFields.DESCRIPTION, GrowJson.text(transaction.description()),
        GrowResponseFields.FULL_NAME, GrowJson.text(transaction.payerFullName()),
        GrowResponseFields.PAYER_PHONE, GrowJson.text(transaction.payerPhone()),
        GrowResponseFields.PAYER_EMAIL, GrowJson.text(orEmpty(transaction.payerEmail())),
        GrowResponseFields.TRANSACTION_ID, number(transaction.transactionId()),
        GrowResponseFields.TRANSACTION_TOKEN,
            GrowJson.text(orEmpty(transaction.transactionToken())));
  }

  static GrowJson chargedRow(GrowTransaction transaction) {
    GrowRecordedCard card = transaction.card();
    return GrowJson.object(
        GrowResponseFields.ASMACHTA, GrowJson.text(transaction.asmachta()),
        GrowResponseFields.CARD_SUFFIX, GrowJson.text(card.suffix()),
        GrowResponseFields.CARD_TYPE, GrowJson.text(card.type()),
        GrowResponseFields.CARD_TYPE_CODE, GrowJson.text(card.typeCode()),
        GrowResponseFields.CARD_BRAND, GrowJson.text(card.brand()),
        GrowResponseFields.CARD_BRAND_CODE, GrowJson.text(card.brandCode()),
        GrowResponseFields.CARD_EXP, GrowJson.text(card.expiry()),
        GrowResponseFields.FIRST_PAYMENT_SUM, GrowJson.text(ZERO),
        GrowResponseFields.PERIODICAL_PAYMENT_SUM, GrowJson.text(ZERO),
        GrowResponseFields.STATUS, GrowJson.text(transaction.nativeStatus().label()),
        GrowResponseFields.STATUS_CODE, GrowJson.text(transaction.nativeStatus().code()),
        GrowResponseFields.TRANSACTION_TYPE_ID, GrowJson.text(CARD_TRANSACTION_TYPE_ID),
        GrowResponseFields.PAYMENT_TYPE, GrowJson.text(REGULAR_PAYMENT_TYPE),
        GrowResponseFields.SUM, GrowJson.text(transaction.amount().value()),
        GrowResponseFields.PAYMENTS_NUM, GrowJson.text(ZERO),
        GrowResponseFields.ALL_PAYMENTS_NUM, GrowJson.text(ONE),
        GrowResponseFields.PAYMENT_DATE, GrowJson.text(transaction.paymentDate()),
        GrowResponseFields.DESCRIPTION, GrowJson.text(transaction.description()),
        GrowResponseFields.FULL_NAME, GrowJson.text(transaction.payerFullName()),
        GrowResponseFields.PAYER_PHONE, GrowJson.text(transaction.payerPhone()),
        GrowResponseFields.PAYER_EMAIL, GrowJson.text(orEmpty(transaction.payerEmail())),
        GrowResponseFields.TRANSACTION_ID, GrowJson.text(transaction.transactionId()),
        GrowResponseFields.TRANSACTION_TOKEN,
            GrowJson.text(orEmpty(transaction.transactionToken())));
  }

  static GrowJson unpaidRow(GrowPaymentProcess process) {
    return GrowJson.object(
        GrowResponseFields.ASMACHTA, GrowJson.text(""),
        GrowResponseFields.CARD_SUFFIX, GrowJson.text(""),
        GrowResponseFields.CARD_TYPE, GrowJson.text(""),
        GrowResponseFields.CARD_TYPE_CODE, GrowJson.text(""),
        GrowResponseFields.CARD_BRAND, GrowJson.text(""),
        GrowResponseFields.CARD_BRAND_CODE, GrowJson.text(""),
        GrowResponseFields.CARD_EXP, GrowJson.text(""),
        GrowResponseFields.FIRST_PAYMENT_SUM, GrowJson.text(ZERO),
        GrowResponseFields.PERIODICAL_PAYMENT_SUM, GrowJson.text(ZERO),
        GrowResponseFields.STATUS, GrowJson.text(GrowNativeStatus.NOT_PAID),
        GrowResponseFields.STATUS_CODE, GrowJson.text(GrowNativeStatus.NOT_PAID_CODE),
        GrowResponseFields.TRANSACTION_TYPE_ID, GrowJson.text(CARD_TRANSACTION_TYPE_ID),
        GrowResponseFields.PAYMENT_TYPE, GrowJson.text(NO_PAYMENT_TYPE),
        GrowResponseFields.SUM, GrowJson.text(process.chargeSum()),
        GrowResponseFields.PAYMENTS_NUM, GrowJson.text(ZERO),
        GrowResponseFields.ALL_PAYMENTS_NUM, GrowJson.text(ZERO),
        GrowResponseFields.PAYMENT_DATE, GrowJson.text(""),
        GrowResponseFields.DESCRIPTION, GrowJson.text(process.description()),
        GrowResponseFields.FULL_NAME, GrowJson.text(process.payerFullName()),
        GrowResponseFields.PAYER_PHONE, GrowJson.text(process.payerPhone()),
        GrowResponseFields.PAYER_EMAIL, GrowJson.text(orEmpty(process.payerEmail())),
        GrowResponseFields.TRANSACTION_ID, GrowJson.text(ZERO),
        GrowResponseFields.TRANSACTION_TOKEN, GrowJson.text(""));
  }

  static GrowJson customField(GrowPaymentProcess process) {
    return customFieldsOf(process.customFields());
  }

  static GrowJson customFieldsOf(Map<String, String> sent) {
    if (sent.isEmpty()) {
      return GrowJson.array(List.of());
    }
    List<Object> keysAndValues = new ArrayList<>();
    for (String name : GrowFields.CUSTOM_FIELDS) {
      String value = sent.get(name);
      if (value != null) {
        keysAndValues.add(name);
        keysAndValues.add(GrowJson.text(value));
      }
    }
    return GrowJson.object(keysAndValues.toArray());
  }

  private static GrowJson number(String digits) {
    return GrowJson.number(Long.parseLong(digits));
  }

  private static GrowJson code(String mapped) {
    return mapped.isEmpty() ? GrowJson.text(mapped) : number(mapped);
  }

  private static String orEmpty(@Nullable String value) {
    return value == null ? "" : value;
  }
}
