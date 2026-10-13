package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class GrowCallback {

  static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

  private GrowCallback() {}

  static WebhookPayload of(GrowPaymentProcess process, GrowProcessOutcome outcome) {
    GrowCallbackForm form =
        outcome.kind() == GrowProcessOutcome.Kind.COMPLETED
            ? completed(process, outcome)
            : failed();
    return new WebhookPayload(
        CONTENT_TYPE,
        Map.of(),
        form.toBytes(),
        Redaction.redact(form.forAudit(), GrowFields.SENSITIVE_DELIVERY_FIELDS));
  }

  private static GrowCallbackForm completed(
      GrowPaymentProcess process, GrowProcessOutcome outcome) {
    GrowCallbackForm form = new GrowCallbackForm();
    form.put("err", "");
    form.put("status", "1");
    form.put("data[status]", GrowNativeStatus.SUSPENDED);
    form.put("data[statusCode]", GrowNativeStatus.SUSPENDED_CODE);
    form.put("data[sum]", process.chargeSum());
    form.put("data[firstPaymentSum]", ZERO);
    form.put("data[periodicalPaymentSum]", ZERO);
    form.put("data[paymentsNum]", ZERO);
    form.put("data[allPaymentsNum]", ONE);
    form.put("data[paymentDate]", orEmpty(outcome.paymentDate()));
    form.put("data[asmachta]", orEmpty(outcome.asmachta()));
    form.put("data[description]", process.description());
    form.put("data[fullName]", process.payerFullName());
    form.put("data[payerPhone]", process.payerPhone());
    form.put("data[payerEmail]", orEmpty(process.payerEmail()));
    if (process.saveCardToken()) {
      form.put("data[cardToken]", orEmpty(outcome.cardToken()));
    }
    GrowRecordedCard card = outcome.cardCharged();
    form.put("data[cardSuffix]", card.suffix());
    form.put("data[cardType]", card.type());
    form.put("data[cardTypeCode]", card.typeCode());
    form.put("data[cardBrand]", card.brand());
    form.put("data[cardBrandCode]", card.brandCode());
    form.put("data[cardExp]", card.expiry());
    form.put("data[payerBankAccountDetails][bankNum]", "");
    form.put("data[payerBankAccountDetails][branchNum]", "");
    form.put("data[payerBankAccountDetails][accountNum]", "");
    form.put("data[payerBankAccountDetails][accountName]", "");
    Map<String, String> customFields = process.customFields();
    for (String name : GrowFields.CUSTOM_FIELDS) {
      String value = customFields.get(name);
      if (value != null) {
        form.put("data[customFields][" + name + "]", value);
      }
    }
    form.put("data[transactionId]", orEmpty(outcome.transactionId()));
    form.put("data[transactionToken]", orEmpty(outcome.transactionToken()));
    form.put("data[processId]", process.processId());
    form.put("data[processToken]", process.processToken());
    return form;
  }

  private static GrowCallbackForm failed() {
    GrowCallbackForm form = new GrowCallbackForm();
    form.put("status", "0");
    form.put("err[id]", Integer.toString(GrowErrors.CALLBACK_FAILURE.id()));
    form.put("err[message]", GrowErrors.CALLBACK_FAILURE.message());
    form.put("data", "");
    return form;
  }

  private static final String ZERO = "0";

  private static final String ONE = "1";

  private static String orEmpty(@Nullable String value) {
    return value == null ? "" : value;
  }
}
