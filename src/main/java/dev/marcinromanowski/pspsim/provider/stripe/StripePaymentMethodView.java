package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StripePaymentMethodView {

  static final String OBJECT = "payment_method";

  static final String LIST = "list";

  static final String METADATA = "metadata";

  private StripePaymentMethodView() {}

  static StripeJson of(StripePaymentMethod method) {
    StripeJson instrument =
        method.type().describesACard() ? cardOf(theCardItCarries(method)) : paypal();
    Map<String, StripeJson> metadata = new LinkedHashMap<>();
    method.metadata().forEach((key, value) -> metadata.put(key, StripeJson.text(value)));
    return StripeJson.object(
        "id",
        StripeJson.text(method.paymentMethodId()),
        "object",
        StripeJson.text(OBJECT),
        "allow_redisplay",
        StripeJson.text(method.allowRedisplay()),
        "billing_details",
        billingDetailsOf(method.billingDetails()),
        "created",
        StripeJson.number(Long.toString(method.createdAt().getEpochSecond())),
        "customer",
        StripeJson.textOrNull(method.customerId()),
        "livemode",
        StripeJson.bool(false),
        METADATA,
        new StripeJson.JsonObject(metadata),
        "type",
        StripeJson.text(method.type().wireValue()),
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

  private static StripeCard theCardItCarries(StripePaymentMethod method) {
    StripeCard card = method.card();
    if (card == null) {
      throw new IllegalStateException(
          "a stored payment method of type "
              + StripePaymentMethodType.CARD.wireValue()
              + " reached the wire with no card, although the store refuses one");
    }
    return card;
  }

  private static StripeJson cardOf(StripeCard card) {
    List<String> networks = card.networks();
    Boolean threeDSecure = card.threeDSecureSupported();
    return StripeJson.object(
        "brand",
        StripeJson.text(card.brand()),
        "checks",
        StripeJson.object(
            "address_line1_check",
            StripeJson.nothing(),
            "address_postal_code_check",
            StripeJson.nothing(),
            "cvc_check",
            StripeJson.nothing()),
        "country",
        StripeJson.textOrNull(card.country()),
        "display_brand",
        StripeJson.textOrNull(card.displayBrand()),
        "exp_month",
        StripeJson.number(Integer.toString(card.expMonth())),
        "exp_year",
        StripeJson.number(Integer.toString(card.expYear())),
        "funding",
        StripeJson.text(card.funding()),
        "generated_from",
        StripeJson.nothing(),
        "last4",
        StripeJson.text(card.last4()),
        "networks",
        networks == null
            ? StripeJson.nothing()
            : StripeJson.object(
                "available",
                StripeJson.array(networks.stream().<StripeJson>map(StripeJson::text).toList()),
                "preferred",
                StripeJson.nothing()),
        "regulated_status",
        StripeJson.textOrNull(card.regulatedStatus()),
        "three_d_secure_usage",
        threeDSecure == null
            ? StripeJson.nothing()
            : StripeJson.object("supported", StripeJson.bool(threeDSecure)),
        "wallet",
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

  private static StripeJson billingDetailsOf(StripeBillingDetails details) {
    Map<String, StripeJson> address = new LinkedHashMap<>();
    for (String member : StripeBillingDetails.THE_ADDRESS_MEMBERS) {
      address.put(member, StripeJson.textOrNull(details.addressMember(member)));
    }
    Map<String, StripeJson> billing = new LinkedHashMap<>();
    billing.put(StripeBillingDetails.ADDRESS, new StripeJson.JsonObject(address));
    for (String member : StripeBillingDetails.THE_PERSON_MEMBERS) {
      billing.put(member, StripeJson.textOrNull(details.personMember(member)));
    }
    return new StripeJson.JsonObject(billing);
  }
}
