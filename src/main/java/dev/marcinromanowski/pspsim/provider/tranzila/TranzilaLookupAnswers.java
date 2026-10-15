package dev.marcinromanowski.pspsim.provider.tranzila;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TranzilaLookupAnswers {

  static final int THE_API_SLOTS = 20;

  static final int THE_REPORT_SLOTS = 25;

  static final String TRANSACTIONS = "transactions";

  static final String USER_DEFINED = "user_defined";

  static final String TOTAL = "total";

  static final String ROWS = "rows";

  static final String USER_DEFINED_SLOT = "user_defined_";

  static final String CREDIT_CARD_TOKEN = "credit_card_token";

  static final String CREDIT_CARD_OWNER_ID = "credit_card_owner_id";

  static final String NOT_HELD = "";

  static final String THE_ISSUER = "1";

  static final String DOMESTIC = "0";

  static final String ONE_PAYMENT = "1";

  static final String NO_INSTALMENTS = "0";

  static final String NO_STARS = "0";

  static final String SUCCESSFUL = "1";

  static final String FAILED = "0";

  static final String AUTHORISATION_CAPABLE = "1";

  static final String A_CARD = "CC";

  static final String THE_PROCESSOR = "SHVA";

  static final String NOT_MANDATORY = "N";

  static final String A_STRING = "string";

  static final String J2 = "j2";

  static final DateTimeFormatter THE_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);

  static final DateTimeFormatter THE_TIME =
      DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC);

  static final DateTimeFormatter THE_UPDATE_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC);

  record Found(
      String terminalName,
      List<TranzilaRecorded> rows,
      long total,
      List<TranzilaUserSlot> slots,
      boolean detailed) {

    Found {
      rows = List.copyOf(rows);
      slots = List.copyOf(slots);
    }
  }

  private TranzilaLookupAnswers() {}

  static TranzilaJson.JsonObject ofTheApi(Found found) {
    List<TranzilaJson> transactions = new ArrayList<>();
    for (TranzilaRecorded row : found.rows()) {
      transactions.add(aRowOfTheApi(row, found.slots()));
    }
    return answer(
        transactions, theSlotsOf(found, THE_API_SLOTS, false), found.total(), found.rows().size());
  }

  static TranzilaJson.JsonObject ofTheReport(Found found) {
    List<TranzilaJson> transactions = new ArrayList<>();
    for (TranzilaRecorded row : found.rows()) {
      transactions.add(aRowOfTheReport(row, found.slots(), found.detailed()));
    }
    return answer(
        transactions,
        theSlotsOf(found, THE_REPORT_SLOTS, true),
        found.total(),
        found.rows().size());
  }

  private static TranzilaJson.JsonObject answer(
      List<TranzilaJson> transactions, List<TranzilaJson> slots, long total, int rows) {
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TRANSACTIONS, TranzilaJson.array(transactions));
    body.put(USER_DEFINED, TranzilaJson.array(slots));
    body.put(TOTAL, TranzilaJson.text(Long.toString(total)));
    body.put(ROWS, TranzilaJson.number(Integer.toString(rows)));
    return TranzilaJson.object(body);
  }

  static TranzilaJson.JsonObject aRowOfTheApi(TranzilaRecorded row, List<TranzilaUserSlot> slots) {
    TranzilaTransaction transaction = row.transaction();
    TranzilaCard card = transaction.card();
    Map<String, TranzilaJson> fields = new LinkedHashMap<>();
    text(fields, TranzilaFields.INDEX, transaction.transactionIdAsText());
    text(fields, "transaction_date", THE_DATE.format(transaction.createdAt()));
    text(fields, "transaction_time", THE_TIME.format(transaction.createdAt()));
    text(fields, TranzilaFields.AMOUNT, TranzilaAmounts.inMinorUnits(transaction.amount()));
    text(fields, CREDIT_CARD_TOKEN, theTokenOf(transaction));
    text(fields, "expiration_month", twoDigits(card.expiryMonth()));
    text(fields, "expiration_year", twoDigits(card.expiryYear()));
    text(fields, CREDIT_CARD_OWNER_ID, NOT_HELD);
    text(fields, TranzilaFields.CARD_TYPE, THE_ISSUER);
    text(fields, "card_description", card.cardTypeName());
    text(fields, "card_brand", Integer.toString(card.cardType()));
    text(fields, "clearing_processor", TranzilaBrowserReturn.theAcquirerOf(card.cardType()));
    text(fields, "stars", NO_STARS);
    text(fields, "is_foreign", DOMESTIC);
    text(fields, "club_number", NOT_HELD);
    text(fields, TranzilaFields.AUTHORIZATION_NUMBER, theAuthorizationOf(transaction));
    text(fields, "bank", NOT_HELD);
    text(fields, "bank_branch", NOT_HELD);
    text(fields, "bank_account", NOT_HELD);
    text(fields, TranzilaFields.PAYMENT_PLAN, ONE_PAYMENT);
    text(fields, TranzilaFields.CURRENCY, theNumericCurrencyOf(transaction));
    text(fields, "number_of_payments", NO_INSTALMENTS);
    text(fields, "first_payment_amount", NO_INSTALMENTS);
    text(fields, "other_payment_amount", NO_INSTALMENTS);
    text(fields, "host_ip", NOT_HELD);
    text(fields, TranzilaFields.PROCESSOR_RESPONSE_CODE, transaction.processorResponseCode());
    text(fields, TranzilaFields.TRANMODE, transaction.tranmode().orElse(NOT_HELD));
    text(fields, "refnr", NOT_HELD);
    text(fields, "tempref", NOT_HELD);
    text(fields, "xid", NOT_HELD);
    text(fields, "cavv", NOT_HELD);
    text(fields, "eci", NOT_HELD);
    text(fields, "child_terminal", transaction.terminalName());
    text(fields, "uid", NOT_HELD);
    text(fields, "transtatus", theStatusOf(transaction));
    text(fields, "telauthability", AUTHORISATION_CAPABLE);
    text(fields, TranzilaFields.TXN_TYPE, theTypeOf(transaction));
    text(fields, "txn_payment_method", A_CARD);
    text(fields, "processor", THE_PROCESSOR);
    text(fields, "broker", theBrokerOf(transaction));
    text(fields, "broker_product", NOT_HELD);
    text(fields, TranzilaFields.PAN_ENTRY_MODE, NOT_HELD);
    theSlotValues(fields, row, slots, THE_API_SLOTS);
    return TranzilaJson.object(fields);
  }

  static TranzilaJson.JsonObject aRowOfTheReport(
      TranzilaRecorded row, List<TranzilaUserSlot> slots, boolean detailed) {
    TranzilaTransaction transaction = row.transaction();
    TranzilaCard card = transaction.card();
    Map<String, TranzilaJson> fields = new LinkedHashMap<>();
    number(fields, TranzilaFields.INDEX, transaction.transactionIdAsText());
    text(fields, "transaction_date", THE_DATE.format(transaction.createdAt()));
    text(fields, "transaction_time", THE_TIME.format(transaction.createdAt()));
    number(fields, TranzilaFields.AMOUNT, TranzilaAmounts.inMinorUnits(transaction.amount()));
    text(fields, CREDIT_CARD_TOKEN, theTokenOf(transaction));
    text(fields, "expiration_month", twoDigits(card.expiryMonth()));
    text(fields, "expiration_year", twoDigits(card.expiryYear()));
    text(fields, CREDIT_CARD_OWNER_ID, NOT_HELD);
    number(fields, "card_issuer", THE_ISSUER);
    text(fields, "card_description", card.cardTypeName());
    number(fields, "card_brand", Integer.toString(card.cardType()));
    number(fields, "clearing_processor", TranzilaBrowserReturn.theAcquirerOf(card.cardType()));
    number(fields, "is_foreign", DOMESTIC);
    text(fields, "club_number", NOT_HELD);
    text(fields, TranzilaFields.AUTHORIZATION_NUMBER, theAuthorizationOf(transaction));
    nothing(fields, "bank");
    nothing(fields, "bank_branch");
    nothing(fields, "bank_account");
    number(fields, TranzilaFields.PAYMENT_PLAN, ONE_PAYMENT);
    text(fields, TranzilaFields.CURRENCY, theNumericCurrencyOf(transaction));
    number(fields, "number_of_payments", NO_INSTALMENTS);
    number(fields, "first_payment_amount", NO_INSTALMENTS);
    number(fields, "other_payment_amount", NO_INSTALMENTS);
    text(fields, "host_ip", NOT_HELD);
    text(fields, TranzilaFields.PROCESSOR_RESPONSE_CODE, transaction.processorResponseCode());
    text(fields, TranzilaFields.TRANMODE, transaction.tranmode().orElse(NOT_HELD));
    text(fields, "refnr", NOT_HELD);
    text(fields, "tempref", NOT_HELD);
    text(fields, "xid", NOT_HELD);
    text(fields, "cavv", NOT_HELD);
    text(fields, "eci", NOT_HELD);
    text(fields, "child_terminal", transaction.terminalName());
    text(fields, "uid", NOT_HELD);
    number(fields, "transtatus", theStatusOf(transaction));
    number(fields, "telauthability", AUTHORISATION_CAPABLE);
    nothing(fields, "txnfdid");
    nothing(fields, "cancelfdid");
    nothing(fields, "txnfdnumber");
    nothing(fields, "cancelfdnumber");
    text(fields, "txn_payment_method", A_CARD);
    text(fields, TranzilaFields.TXN_TYPE, theTypeOf(transaction).toUpperCase(Locale.ROOT));
    text(fields, TranzilaFields.PAN_ENTRY_MODE, NOT_HELD);
    text(fields, "processor", THE_PROCESSOR);
    text(fields, "broker", theBrokerOf(transaction));
    text(fields, "broker_product", NOT_HELD);
    theSlotValues(fields, row, slots, THE_REPORT_SLOTS);
    if (detailed) {
      List<TranzilaJson> items = new ArrayList<>();
      for (TranzilaRecorded.Item item : row.items()) {
        items.add(anItem(item));
      }
      fields.put(TranzilaFields.ITEMS, TranzilaJson.array(items));
    }
    return TranzilaJson.object(fields);
  }

  static String theBrokerOf(TranzilaTransaction transaction) {
    TranzilaWallet broker = transaction.broker();
    return broker == null ? NOT_HELD : broker.broker();
  }

  static TranzilaJson.JsonObject anItem(TranzilaRecorded.Item recorded) {
    TranzilaItem item = recorded.item();
    Map<String, TranzilaJson> fields = new LinkedHashMap<>();
    number(fields, "item_id", Long.toString(recorded.itemId()));
    text(fields, "item_name", item.name());
    text(fields, "quantity", item.unitsNumber() + ".00");
    number(fields, "price", TranzilaAmounts.inMinorUnits(item.unitPrice()));
    text(fields, "item_type", item.itemType());
    return TranzilaJson.object(fields);
  }

  static List<TranzilaJson> theSlotsOf(Found found, int slots, boolean typed) {
    List<TranzilaJson> described = new ArrayList<>();
    for (TranzilaUserSlot slot : found.slots().reversed()) {
      if (slot.fieldId() > slots) {
        continue;
      }
      Map<String, TranzilaJson> fields = new LinkedHashMap<>();
      text(fields, "TERMINAL_ID", found.terminalName());
      String fieldId = Integer.toString(slot.fieldId());
      fields.put("FIELD_ID", typed ? TranzilaJson.number(fieldId) : TranzilaJson.text(fieldId));
      text(fields, "API_PARAMETER_NAME", slot.name());
      text(fields, "CAPTION", slot.name());
      nothing(fields, "DEFAULT_VALUE");
      nothing(fields, "DISPLAY_ORDER");
      text(fields, "MANDATORY", NOT_MANDATORY);
      text(fields, "DATA_TYPE", A_STRING);
      text(fields, "UPDATE_DATE", THE_UPDATE_DATE.format(slot.firstRecordedAt()));
      described.add(TranzilaJson.object(fields));
    }
    return List.copyOf(described);
  }

  private static void theSlotValues(
      Map<String, TranzilaJson> fields,
      TranzilaRecorded row,
      List<TranzilaUserSlot> slots,
      int howMany) {
    Map<String, String> sent = new LinkedHashMap<>();
    for (TranzilaUserField field : row.userDefinedFields()) {
      sent.put(field.name(), field.value());
    }
    Map<Integer, String> bySlot = new LinkedHashMap<>();
    for (TranzilaUserSlot slot : slots) {
      bySlot.put(Integer.valueOf(slot.fieldId()), sent.getOrDefault(slot.name(), NOT_HELD));
    }
    for (int slot = 1; slot <= howMany; slot++) {
      text(fields, USER_DEFINED_SLOT + slot, bySlot.getOrDefault(Integer.valueOf(slot), NOT_HELD));
    }
  }

  static String theTypeOf(TranzilaTransaction transaction) {
    return transaction.isAJ2() ? J2 : transaction.type().wire();
  }

  static String theStatusOf(TranzilaTransaction transaction) {
    return transaction.approved() ? SUCCESSFUL : FAILED;
  }

  static String theNumericCurrencyOf(TranzilaTransaction transaction) {
    return TranzilaPageCurrency.alphabetic(transaction.currencyCode())
        .map(TranzilaPageCurrency::numeric)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "the store holds "
                        + transaction.currencyCode()
                        + " as a currency, which no numeric code names"));
  }

  private static String theTokenOf(TranzilaTransaction transaction) {
    String token = transaction.token();
    return token == null ? NOT_HELD : token;
  }

  private static String theAuthorizationOf(TranzilaTransaction transaction) {
    String authorization = transaction.authNumber();
    return authorization == null ? NOT_HELD : authorization;
  }

  static String twoDigits(int value) {
    return String.format(Locale.ROOT, "%02d", value);
  }

  private static void text(Map<String, TranzilaJson> fields, String name, String value) {
    fields.put(name, TranzilaJson.text(value));
  }

  private static void number(Map<String, TranzilaJson> fields, String name, String value) {
    fields.put(name, TranzilaJson.number(value));
  }

  private static void nothing(Map<String, TranzilaJson> fields, String name) {
    fields.put(name, new TranzilaJson.JsonNothing());
  }
}
