package dev.marcinromanowski.pspsim.provider.azericard;

import java.security.PrivateKey;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record AzericardResponse(
    String desc,
    String terminal,
    String trtype,
    String order,
    String amount,
    String currency,
    String action,
    String rc,
    String approval,
    String rrn,
    String intRef,
    String timestamp,
    String nonce) {

  static final String CONTENT_TYPE = "text/html";

  static final String DESC_ELEMENT = "desc";

  static final String ACTION_ELEMENT = "action";

  static final String RC_ELEMENT = "rc";

  static final String INT_REF_ELEMENT = "intref";

  static final String SIGNATURE_ELEMENT = "p_sign";

  AzericardBody signedWith(PrivateKey gatewayKey) {
    String pSign =
        AzericardSignature.sign(
            AzericardMac.sourceOf(amount, terminal, approval, rrn, intRef), gatewayKey);
    Map<String, List<String>> fields = fieldsWith(pSign);
    return new AzericardBody(CONTENT_TYPE, xmlOf(fields), fields);
  }

  private Map<String, List<String>> fieldsWith(String pSign) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put(DESC_ELEMENT, List.of(desc));
    fields.put("terminal", List.of(terminal));
    fields.put("trtype", List.of(trtype));
    fields.put("order", List.of(order));
    fields.put("amount", List.of(amount));
    fields.put("currency", List.of(currency));
    fields.put(ACTION_ELEMENT, List.of(action));
    fields.put(RC_ELEMENT, List.of(rc));
    fields.put("approval", List.of(approval));
    fields.put("rrn", List.of(rrn));
    fields.put(INT_REF_ELEMENT, List.of(intRef));
    fields.put("timestamp", List.of(timestamp));
    fields.put("nonce", List.of(nonce));
    fields.put(SIGNATURE_ELEMENT, List.of(pSign));
    return Collections.unmodifiableMap(fields);
  }

  private static String xmlOf(Map<String, List<String>> fields) {
    StringBuilder xml = new StringBuilder("<response>");
    fields.forEach(
        (name, values) ->
            xml.append('<')
                .append(name)
                .append('>')
                .append(escaped(values.get(0)))
                .append("</")
                .append(name)
                .append('>'));
    return xml.append("</response>").toString();
  }

  private static String escaped(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
