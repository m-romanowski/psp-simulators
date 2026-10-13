package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.List;

final class StripeEphemeralKeyView {

  static final String OBJECT = "ephemeral_key";

  private StripeEphemeralKeyView() {}

  static StripeJson of(StripeEphemeralKey key) {
    return StripeJson.object(
        "id",
        StripeJson.text(key.ephemeralKeyId()),
        "object",
        StripeJson.text(OBJECT),
        StripeFields.ASSOCIATED_OBJECTS,
        theResourceItWasMintedFor(key),
        "created",
        StripeJson.number(Long.toString(key.createdAt().getEpochSecond())),
        StripeFields.EXPIRES,
        StripeJson.number(Long.toString(key.expiresAt().getEpochSecond())),
        "livemode",
        StripeJson.bool(false),
        StripeFields.SECRET,
        StripeJson.text(key.secret()));
  }

  private static StripeJson theResourceItWasMintedFor(StripeEphemeralKey key) {
    return StripeJson.array(
        List.of(
            StripeJson.object(
                StripeFields.TYPE,
                StripeJson.text(StripeCustomerView.OBJECT),
                "id",
                StripeJson.text(key.customerId()))));
  }
}
