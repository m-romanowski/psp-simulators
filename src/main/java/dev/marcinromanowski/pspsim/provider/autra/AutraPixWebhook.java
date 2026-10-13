package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AutraPixWebhook {

  static final String USER_AGENT = "User-Agent";

  static final String SIGNATURE = "X-Autra-Signature";

  static final String THE_AGENT_AUTRA_SENDS = "Autra-Webhooks/1.0";

  static final String THE_ENVELOPE_CARRIES_ONLY_WHAT_THIS_CONTRACT_HOLDS =
      "provider autra lists tenantId, ownerId and counterpartyName among the webhook fields that"
          + " are \"sempre presentes\" and then publishes a Pix event example that carries none of"
          + " them. This contract has no tenant object, no owner object and no payer name anywhere"
          + " on it, so this simulator renders the Pix events as autra's own Pix examples render"
          + " them and adds userDocument, which is the half of the routing pair (tenantId,"
          + " userDocument) this contract does hold";

  private static final DateTimeFormatter AS_A_DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);

  private AutraPixWebhook() {}

  static WebhookPayload of(
      AutraPixEvent event,
      AutraPixPayment pix,
      String eventId,
      @Nullable String secret,
      Instant now) {
    AutraJson.JsonObject body = bodyOf(event, pix, eventId, now);
    byte[] bytes = AutraJsonWriter.toBytes(body);
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put(USER_AGENT, List.of(THE_AGENT_AUTRA_SENDS));
    if (secret != null) {
      headers.put(SIGNATURE, List.of(AutraWebhookSecret.signatureOf(secret, bytes)));
    }
    return new WebhookPayload(
        AutraFields.JSON,
        headers,
        bytes,
        Redaction.redact(body.flatten(), AutraFields.SENSITIVE_DELIVERY_FIELDS));
  }

  static AutraJson.JsonObject bodyOf(
      AutraPixEvent event, AutraPixPayment pix, String eventId, Instant now) {
    Map<String, AutraJson> envelope = new LinkedHashMap<>();
    envelope.put(AutraFields.EVENT_ID, AutraJson.text(eventId));
    envelope.put(AutraFields.EVENT_TYPE, AutraJson.text(event.eventType()));
    envelope.put(AutraFields.OCCURRED_AT, AutraJson.text(CreatePixCheckout.asAutraWritesIt(now)));
    envelope.put(AutraFields.USER_DOCUMENT, AutraJson.text(pix.documentId()));
    if (event == AutraPixEvent.FINANCIAL_TRANSACTION) {
      envelope.put(AutraFields.STATUS, AutraJson.text(pix.statusAt(now)));
    }
    envelope.put(AutraFields.OPERATION_DATA, operationDataOf(event, pix, now));
    return AutraJson.object(envelope);
  }

  private static AutraJson.JsonObject operationDataOf(
      AutraPixEvent event, AutraPixPayment pix, Instant now) {
    Map<String, AutraJson> operationData = new LinkedHashMap<>();
    operationData.put(AutraFields.SUBJECT, AutraJson.text(event.subject()));
    operationData.put(AutraFields.PRODUCT_TYPE, AutraJson.text(AutraFields.PIX));
    String orderId = pix.orderId();
    if (orderId != null) {
      operationData.put(AutraFields.ORDER_ID, AutraJson.text(orderId));
    }
    operationData.put(AutraFields.PIX_ID, AutraJson.text(pix.pixId()));
    operationData.put(
        AutraFields.PAYLOAD,
        event == AutraPixEvent.FINANCIAL_TRANSACTION
            ? theTransactionPayloadOf(pix, now)
            : thePayoutPayloadOf(pix, now));
    return AutraJson.object(operationData);
  }

  private static AutraJson.JsonObject theTransactionPayloadOf(AutraPixPayment pix, Instant now) {
    Map<String, AutraJson> payload = new LinkedHashMap<>();
    payload.put(AutraFields.SLUG, AutraJson.text(pix.slug()));
    payload.put(AutraFields.MUID, AutraJson.text(pix.muid()));
    payload.put(AutraFields.TRANSACTION_STATUS, AutraJson.text(pix.statusAt(now)));
    payload.put(AutraFields.TOTAL_AMOUNT, AutraJson.number(pix.amount().value()));
    payload.put(AutraFields.CURRENCY, AutraJson.text(pix.currency()));
    return AutraJson.object(payload);
  }

  private static AutraJson.JsonObject thePayoutPayloadOf(AutraPixPayment pix, Instant now) {
    Map<String, AutraJson> payload = new LinkedHashMap<>();
    payload.put(AutraFields.PAYOUT_ID, AutraJson.text(pix.slug()));
    payload.put(AutraFields.STATUS, AutraJson.text(pix.statusAt(now)));
    payload.put(AutraFields.AMOUNT, AutraJson.number(pix.amount().value()));
    payload.put(AutraFields.EXPECTED_SETTLEMENT_DATE, AutraJson.text(AS_A_DATE.format(now)));
    return AutraJson.object(payload);
  }
}
