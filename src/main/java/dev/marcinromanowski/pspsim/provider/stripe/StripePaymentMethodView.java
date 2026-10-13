package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class StripePaymentMethodView {

  static final String OBJECT = "payment_method";

  static final String LIST = "list";

  private StripePaymentMethodView() {}

  static StripeJson of(StripePaymentMethod method) {
    return of(method, null);
  }

  static StripeJson of(StripePaymentMethod method, @Nullable StripeJson expandedCustomer) {
    StripeJson customer =
        expandedCustomer == null ? StripeJson.textOrNull(method.customerId()) : expandedCustomer;
    StripeJson instrument =
        method.type().describesACard() ? cardOf(theCardItCarries(method)) : paypal();
    return StripeJson.object(
        "id",
        StripeJson.text(method.paymentMethodId()),
        "object",
        StripeJson.text(OBJECT),
        "type",
        StripeJson.text(method.type().wireValue()),
        "customer",
        customer,
        "created",
        StripeJson.number(Long.toString(method.createdAt().getEpochSecond())),
        "billing_details",
        billingDetails(),
        method.type().wireValue(),
        instrument);
  }

  static StripeJson listOf(List<StripePaymentMethod> methods, String url) {
    List<StripeJson> data = new ArrayList<>();
    for (StripePaymentMethod method : methods) {
      data.add(of(method));
    }
    return StripeJson.object(
        "object",
        StripeJson.text(LIST),
        "url",
        StripeJson.text(url),
        "has_more",
        StripeJson.bool(false),
        "data",
        StripeJson.array(data));
  }

  private static FixtureCard theCardItCarries(StripePaymentMethod method) {
    FixtureCard card = method.card();
    if (card == null) {
      throw new IllegalStateException(
          "a stored payment method of type "
              + StripePaymentMethodType.CARD.wireValue()
              + " reached the wire with no card, although the store refuses one");
    }
    return card;
  }

  private static StripeJson cardOf(FixtureCard card) {
    return StripeJson.object(
        "brand",
        StripeJson.text(card.brand()),
        "funding",
        StripeJson.text(card.funding()),
        "last4",
        StripeJson.text(card.last4()),
        "exp_month",
        StripeJson.number(Integer.toString(card.expMonth())),
        "exp_year",
        StripeJson.number(Integer.toString(card.expYear())),
        "country",
        StripeJson.nothing(),
        "networks",
        StripeJson.object(
            "available",
            StripeJson.array(List.of(StripeJson.text(card.brand()))),
            "preferred",
            StripeJson.nothing()),
        "checks",
        StripeJson.nothing());
  }

  private static StripeJson paypal() {
    return StripeJson.object(
        "payer_email",
        StripeJson.nothing(),
        "payer_id",
        StripeJson.nothing(),
        "country",
        StripeJson.nothing());
  }

  private static StripeJson billingDetails() {
    return StripeJson.object(
        "address",
        StripeJson.nothing(),
        "email",
        StripeJson.nothing(),
        "name",
        StripeJson.nothing(),
        "phone",
        StripeJson.nothing());
  }
}
