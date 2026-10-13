package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AzericardCallback {

  static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

  private static final char CORRUPTED_INTO = 'F';

  private static final char CORRUPTED_INSTEAD = '0';

  private AzericardCallback() {}

  static WebhookPayload of(
      AzericardPayment payment, AzericardTerminal terminal, String nonce, Instant now) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    put(fields, AzericardFields.AMOUNT, payment.amount().value());
    put(fields, AzericardFields.CURRENCY, payment.currency());
    put(fields, AzericardFields.ORDER, payment.order());
    put(fields, AzericardFields.ACTION, payment.action());
    put(fields, AzericardFields.RC, payment.responseCode());
    put(fields, AzericardFields.APPROVAL, approvalOf(payment));
    put(fields, AzericardFields.RRN, payment.rrn());
    put(fields, AzericardFields.INT_REF, payment.intRef());
    put(fields, AzericardFields.EMAIL, payment.email());
    put(fields, AzericardFields.CARD, maskedCardOf(payment));
    put(fields, AzericardFields.TOKEN, payment.token());
    put(fields, AzericardFields.TERMINAL, payment.terminalId());
    put(fields, AzericardFields.TRTYPE, payment.transactionType().wireValue());
    put(fields, AzericardFields.TIMESTAMP, AzericardTimestamp.write(now));
    put(fields, AzericardFields.NONCE, nonce);
    put(fields, AzericardFields.P_SIGN, signatureOf(payment, terminal));
    put(fields, AzericardFields.EXT_NET_REF, payment.extNetRef());

    Map<String, List<String>> sent = Collections.unmodifiableMap(fields);
    return new WebhookPayload(
        CONTENT_TYPE,
        Map.of(),
        urlencoded(sent).getBytes(StandardCharsets.UTF_8),
        Redaction.redact(sent, AzericardFields.SENSITIVE_DELIVERY_FIELDS));
  }

  private static @Nullable String maskedCardOf(AzericardPayment payment) {
    String last4 = payment.cardLast4();
    return last4 == null ? null : AzericardCardCheck.masked(last4);
  }

  private static String approvalOf(AzericardPayment payment) {
    String approval = payment.approval();
    return approval == null ? AzericardOutcomes.ABSENT : approval;
  }

  private static @Nullable String signatureOf(
      AzericardPayment payment, AzericardTerminal terminal) {
    AzericardCallbackSignature emission = terminal.callbackSignature();
    if (emission == AzericardCallbackSignature.ABSENT) {
      return null;
    }
    String pSign =
        AzericardSignature.sign(
            AzericardMac.sourceOf(
                payment.amount().value(),
                payment.terminalId(),
                payment.approval(),
                payment.rrn(),
                payment.intRef()),
            terminal.gatewayKey());
    return emission == AzericardCallbackSignature.CORRUPTED ? corrupted(pSign) : pSign;
  }

  static String corrupted(String pSign) {
    char last = pSign.charAt(pSign.length() - 1);
    char replacement = last == CORRUPTED_INTO ? CORRUPTED_INSTEAD : CORRUPTED_INTO;
    return pSign.substring(0, pSign.length() - 1) + replacement;
  }

  private static void put(Map<String, List<String>> fields, String name, @Nullable String value) {
    if (value != null) {
      fields.put(name, List.of(value));
    }
  }

  private static String urlencoded(Map<String, List<String>> fields) {
    StringBuilder body = new StringBuilder();
    fields.forEach(
        (name, values) -> {
          for (String value : values) {
            if (body.length() > 0) {
              body.append('&');
            }
            body.append(encoded(name)).append('=').append(encoded(value));
          }
        });
    return body.toString();
  }

  private static String encoded(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
