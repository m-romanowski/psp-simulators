package dev.marcinromanowski.pspsim.provider.stripe;

import java.math.BigDecimal;
import java.time.Instant;

final class StripeChargeView {

  static final String OBJECT = "charge";

  static final String ONLY_THE_FIELDS_THIS_CONTRACT_LISTS =
      "stripe.md, \"Charges\" specifies a charge field by field, so this object carries those"
          + " fields and nothing else; every other published attribute of a Stripe charge is a"
          + " deliberate narrowing of this build";

  private StripeChargeView() {}

  static StripeJson of(StripeCharge charge, StripePaymentIntent intent, Instant now) {
    return StripeJson.object(
        "id",
        StripeJson.text(charge.chargeId()),
        "object",
        StripeJson.text(OBJECT),
        "amount",
        StripeJson.number(charge.amount().value()),
        "amount_captured",
        StripeJson.number(minorUnitsOf(charge.amountCaptured(intent))),
        "amount_refunded",
        StripeJson.number(minorUnitsOf(charge.amountRefunded(intent))),
        "captured",
        StripeJson.bool(charge.captured(intent, now)),
        "currency",
        StripeJson.text(charge.currency()),
        "customer",
        StripeJson.textOrNull(intent.customer()),
        "outcome",
        outcomeOf(charge),
        "paid",
        StripeJson.bool(charge.paid()),
        "payment_intent",
        StripeJson.text(charge.intentId()),
        "payment_method",
        StripeJson.textOrNull(charge.paymentMethod()),
        "refunded",
        StripeJson.bool(charge.refunded(intent, now)),
        "status",
        StripeJson.text(charge.status().wireValue()));
  }

  private static StripeJson outcomeOf(StripeCharge charge) {
    if (charge.paid()) {
      return StripeJson.object(
          "network_status",
          StripeJson.text("approved_by_network"),
          "reason",
          StripeJson.nothing(),
          "risk_level",
          StripeJson.text("normal"),
          "seller_message",
          StripeJson.text("Payment complete."),
          "type",
          StripeJson.text("authorized"));
    }
    StripeDecline failure = charge.failure();
    return StripeJson.object(
        "network_status",
        StripeJson.text("declined_by_network"),
        "reason",
        StripeJson.textOrNull(
            failure == null
                ? null
                : failure.declineCode() == null ? failure.errorCode() : failure.declineCode()),
        "risk_level",
        StripeJson.text("normal"),
        "seller_message",
        StripeJson.textOrNull(failure == null ? null : failure.publishedMessage()),
        "type",
        StripeJson.text("issuer_declined"));
  }

  private static String minorUnitsOf(BigDecimal amount) {
    return amount.toBigInteger().toString();
  }
}
