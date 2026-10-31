package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Optional;

final class StripeRelatedObjects {

  private final StripePaymentIntents intents;
  private final StripeCharges charges;
  private final StripeCustomers customers;
  private final StripePaymentMethods paymentMethods;

  StripeRelatedObjects(
      StripePaymentIntents intents,
      StripeCharges charges,
      StripeCustomers customers,
      StripePaymentMethods paymentMethods) {
    this.intents = intents;
    this.charges = charges;
    this.customers = customers;
    this.paymentMethods = paymentMethods;
  }

  Reach reachedBy(TestRunId testRunId, StripeAccount account, Instant now) {
    return new Reach(this, testRunId, account, now);
  }

  record Reach(StripeRelatedObjects held, TestRunId testRunId, StripeAccount account, Instant now) {

    Optional<StripeJson> find(StripeRelation relation, String id) {
      return switch (relation) {
        case A_CUSTOMER ->
            StripeCustomerLookup.of(held.customers, testRunId, account, id)
                .map(StripeCustomerView::of);
        case A_PAYMENT_METHOD ->
            StripePaymentMethodLookup.of(held.paymentMethods, testRunId, account, id)
                .map(StripePaymentMethodView::of);
        case A_CHARGE ->
            StripeChargeLookup.of(held.charges, testRunId, account, id)
                .flatMap(
                    charge ->
                        StripeLookup.of(held.intents, testRunId, account, charge.intentId())
                            .map(payment -> StripeChargeView.of(charge, payment, now)));
        case A_PAYMENT_INTENT ->
            StripeLookup.of(held.intents, testRunId, account, id)
                .map(intent -> StripeIntentView.of(intent, now));
      };
    }
  }
}
