package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.Optional;

enum StripeIncludable {
  THE_REFUNDS_OF_THE_CHARGE,
  THE_CASH_BALANCE,
  THE_INVOICE_CREDIT_BALANCE,
  THE_SOURCES,
  THE_SUBSCRIPTIONS,
  THE_TAX_DETAILS,
  THE_TAX_IDS;

  Optional<StripeExpandable> listing() {
    return this == THE_REFUNDS_OF_THE_CHARGE
        ? Optional.of(StripeExpandable.REFUND)
        : Optional.empty();
  }
}
