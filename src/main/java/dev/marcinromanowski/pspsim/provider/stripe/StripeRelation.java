package dev.marcinromanowski.pspsim.provider.stripe;

enum StripeRelation {
  A_CUSTOMER,
  A_PAYMENT_METHOD,
  A_CHARGE,
  A_PAYMENT_INTENT;

  StripeExpandable target() {
    return switch (this) {
      case A_CUSTOMER -> StripeExpandable.CUSTOMER;
      case A_PAYMENT_METHOD -> StripeExpandable.PAYMENT_METHOD;
      case A_CHARGE -> StripeExpandable.CHARGE;
      case A_PAYMENT_INTENT -> StripeExpandable.PAYMENT_INTENT;
    };
  }
}
