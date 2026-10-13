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

  private static String minorUnitsOf(BigDecimal amount) {
    return amount.toBigInteger().toString();
  }
}
