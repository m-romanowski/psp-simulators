package dev.marcinromanowski.pspsim.provider.stripe;

final class StripeCustomerView {

  static final String OBJECT = "customer";

  static final String DELETED = "deleted";

  private StripeCustomerView() {}

  static StripeJson of(StripeCustomer customer) {
    if (customer.deleted()) {
      return deletedFormOf(customer);
    }
    return StripeJson.object(
        "id",
        StripeJson.text(customer.customerId()),
        "object",
        StripeJson.text(OBJECT),
        "created",
        StripeJson.number(Long.toString(customer.createdAt().getEpochSecond())),
        "email",
        StripeJson.textOrNull(customer.email()),
        "livemode",
        StripeJson.bool(false));
  }

  static StripeJson deletedFormOf(StripeCustomer customer) {
    return StripeJson.object(
        "id",
        StripeJson.text(customer.customerId()),
        "object",
        StripeJson.text(OBJECT),
        DELETED,
        StripeJson.bool(true));
  }
}
