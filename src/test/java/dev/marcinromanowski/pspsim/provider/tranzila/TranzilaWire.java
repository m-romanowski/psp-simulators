package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TranzilaWire {

  static final String A_VISA = "4580210000004245";

  static final String A_MASTERCARD = "5326100000001234";

  private TranzilaWire() {}

  static String aDebit() {
    return withCard("\"txn_type\":\"debit\"", A_VISA, "10.53");
  }

  static String aJ5(String amount) {
    return withCard("\"txn_type\":\"verify\",\"verify_mode\":5", A_VISA, amount);
  }

  static String aJ2() {
    return withCard("\"txn_type\":\"verify\",\"verify_mode\":2", A_VISA, "1");
  }

  static String withCard(String fields, String card, String amount) {
    return "{\"terminal_name\":\""
        + TranzilaWiring.TERMINAL
        + "\","
        + fields
        + ",\"card_number\":\""
        + card
        + "\",\"expire_month\":7,\"expire_year\":2030,"
        + items(amount)
        + "}";
  }

  static String items(String amount) {
    return "\"items\":[{\"name\":\"a\",\"units_number\":1,\"unit_price\":" + amount + "}]";
  }

  static String aForce(long reference, String card, String amount) {
    return withCard("\"txn_type\":\"force\",\"reference_txn_id\":" + reference, card, amount);
  }

  static String aCredit(long reference, String authorization, String card, String amount) {
    return withCard(
        "\"txn_type\":\"credit\",\"reference_txn_id\":"
            + reference
            + ",\"authorization_number\":\""
            + authorization
            + "\"",
        card,
        amount);
  }

  static String aReversal(long reference, String authorization) {
    return followOn("reversal", reference, authorization);
  }

  static String aCancel(long reference, String authorization) {
    return followOn("cancel", reference, authorization);
  }

  static String followOn(String type, long reference, String authorization) {
    return "{\"terminal_name\":\""
        + TranzilaWiring.TERMINAL
        + "\",\"txn_type\":\""
        + type
        + "\",\"reference_txn_id\":"
        + reference
        + ",\"authorization_number\":\""
        + authorization
        + "\"}";
  }

  static long transactionIdIn(String json) {
    Matcher found = Pattern.compile("\"transaction_id\":([0-9]+)").matcher(json);
    if (!found.find()) {
      throw new AssertionError("no transaction_id in " + json);
    }
    return Long.parseLong(found.group(1));
  }

  static String textIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(json);
    if (!found.find()) {
      throw new AssertionError("no " + field + " in " + json);
    }
    return found.group(1);
  }
}
