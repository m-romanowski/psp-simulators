package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

final class StripeIntentView {

  static final String OBJECT = "payment_intent";

  private StripeIntentView() {}

  static StripeJson of(StripePaymentIntent intent, Instant now) {
    return StripeJson.object(
        "id",
        StripeJson.text(intent.intentId()),
        "object",
        StripeJson.text(OBJECT),
        StripeFields.ALLOWED_PAYMENT_METHOD_TYPES,
        StripeSelectionView.allowed(intent.selection()),
        "amount",
        StripeJson.number(intent.amount().value()),
        "amount_capturable",
        StripeJson.number(intent.capturableRemainderAt(now).toBigInteger().toString()),
        "amount_received",
        StripeJson.number(receivedBy(intent)),
        StripeFields.AUTOMATIC_PAYMENT_METHODS,
        StripeSelectionView.automatic(intent.selection()),
        "cancellation_reason",
        reasonOf(intent.cancellationReasonAt(now)),
        "capture_method",
        StripeJson.text(intent.captureMethod().wireValue()),
        StripeFields.CLIENT_SECRET,
        StripeJson.text(intent.clientSecret()),
        "created",
        StripeJson.number(Long.toString(intent.createdAt().getEpochSecond())),
        "currency",
        StripeJson.text(intent.currency()),
        "customer",
        StripeJson.textOrNull(intent.customer()),
        StripeFields.EXCLUDED_PAYMENT_METHOD_TYPES,
        StripeSelectionView.excluded(intent.selection()),
        "last_payment_error",
        StripeDeclineView.of(
            intent.lastPaymentErrorAt(now), intent.paymentMethod(), intent.latestCharge()),
        "latest_charge",
        StripeJson.textOrNull(intent.latestCharge()),
        "livemode",
        StripeJson.bool(false),
        "payment_method",
        StripeJson.textOrNull(
            intent.lastPaymentErrorAt(now) == null ? intent.paymentMethod() : null),
        StripeFields.PAYMENT_METHOD_CONFIGURATION_DETAILS,
        StripeSelectionView.configurationDetails(intent.selection()),
        StripeFields.PAYMENT_METHOD_TYPES,
        StripeSelectionView.offered(intent.selection()),
        "receipt_email",
        StripeJson.textOrNull(intent.receiptEmail()),
        "status",
        StripeJson.text(intent.statusAt(now).wireValue()));
  }

  private static String receivedBy(StripePaymentIntent intent) {
    return intent
        .ledger()
        .balances(false)
        .map(DerivedBalances::capturedAmount)
        .map(Amount::value)
        .orElse(BigDecimal.ZERO.toString());
  }

  private static StripeJson reasonOf(@Nullable StripeCancellationReason reason) {
    return reason == null ? StripeJson.nothing() : StripeJson.text(reason.wireValue());
  }
}
