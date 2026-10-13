package dev.marcinromanowski.pspsim.provider.azericard;

import java.security.PrivateKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record AzericardStatusResponse(
    String terminal,
    String actionCode,
    String responseCode,
    String statusMsg,
    @Nullable String amount,
    @Nullable String currency,
    @Nullable String transactionDate,
    @Nullable String order,
    @Nullable String approval,
    @Nullable String rrn,
    @Nullable String intRef,
    String originalTransactionType,
    String nonce,
    String timestamp) {

  static final String CONTENT_TYPE = "application/json; charset=UTF-8";

  static final String TERMINAL_KEY = "terminal";

  static final String ACTION_KEY = "actionCode";

  static final String RESPONSE_CODE_KEY = "responseCode";

  static final String MESSAGE_KEY = "statusMsg";

  static final String AMOUNT_KEY = "amount";

  static final String CURRENCY_KEY = "currency";

  static final String DATE_KEY = "transactionDate";

  static final String ORDER_KEY = "order";

  static final String APPROVAL_KEY = "approval";

  static final String RRN_KEY = "rrn";

  static final String INT_REF_KEY = "intRef";

  static final String ORIGINAL_TYPE_KEY = "tr_type";

  static final String NONCE_KEY = "nonce";

  static final String SIGNATURE_KEY = "signature";

  static final String TIMESTAMP_KEY = "timestamp";

  static AzericardStatusResponse refusing(
      AzericardMessage message,
      String actionCode,
      String responseCode,
      String statusMsg,
      String timestamp) {
    return new AzericardStatusResponse(
        message.terminal(),
        actionCode,
        responseCode,
        statusMsg,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        message.originalTransactionTypeAsSentOrNothing(),
        message.nonce(),
        timestamp);
  }

  AzericardBody signedWith(PrivateKey gatewayKey) {
    String signature =
        AzericardSignature.sign(
            AzericardMac.sourceOf(amount, terminal, approval, rrn, intRef), gatewayKey);
    Map<String, List<String>> fields = fieldsWith(signature);
    return new AzericardBody(CONTENT_TYPE, jsonOf(fields), fields);
  }

  private Map<String, List<String>> fieldsWith(String signature) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    put(fields, TERMINAL_KEY, terminal);
    put(fields, ACTION_KEY, actionCode);
    put(fields, RESPONSE_CODE_KEY, responseCode);
    put(fields, MESSAGE_KEY, statusMsg);
    put(fields, AMOUNT_KEY, amount);
    put(fields, CURRENCY_KEY, currency);
    put(fields, DATE_KEY, transactionDate);
    put(fields, ORDER_KEY, order);
    put(fields, APPROVAL_KEY, approval);
    put(fields, RRN_KEY, rrn);
    put(fields, INT_REF_KEY, intRef);
    put(fields, ORIGINAL_TYPE_KEY, originalTransactionType);
    put(fields, NONCE_KEY, nonce);
    put(fields, SIGNATURE_KEY, signature);
    put(fields, TIMESTAMP_KEY, timestamp);
    return Collections.unmodifiableMap(fields);
  }

  private static void put(Map<String, List<String>> fields, String key, @Nullable String value) {
    if (value != null) {
      fields.put(key, List.of(value));
    }
  }

  private static String jsonOf(Map<String, List<String>> fields) {
    StringBuilder json = new StringBuilder("{");
    fields.forEach(
        (key, values) -> {
          if (json.length() > 1) {
            json.append(',');
          }
          json.append('"')
              .append(escaped(key))
              .append("\":\"")
              .append(escaped(values.get(0)))
              .append('"');
        });
    return json.append('}').toString();
  }

  private static String escaped(String value) {
    StringBuilder written = new StringBuilder();
    for (int index = 0; index < value.length(); index++) {
      char one = value.charAt(index);
      switch (one) {
        case '"' -> written.append("\\\"");
        case '\\' -> written.append("\\\\");
        default -> appendPlainly(written, one);
      }
    }
    return written.toString();
  }

  private static void appendPlainly(StringBuilder written, char one) {
    if (one < ' ') {
      written.append(String.format("\\u%04x", (int) one));
    } else {
      written.append(one);
    }
  }
}
