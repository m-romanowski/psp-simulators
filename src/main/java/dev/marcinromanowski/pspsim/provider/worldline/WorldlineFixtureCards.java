package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import java.util.List;
import java.util.Map;

final class WorldlineFixtureCards {

  static final String THE_TOKEN_THIS_CONTRACT_PUBLISHES = "token_123";

  static final String PROVIDER_IDS = "providerIds";

  static final String TYPE_PROPERTY = "type";

  static final String NO_CUSTOMER_ID_IS_A_PAYMENT_METHOD_IDENTITY =
      "provider worldline publishes order.customer.merchantCustomerId on a create-payment request"
          + " and this module reads it, but it is the merchant's own identifier for a shopper and"
          + " not an identifier of a stored card: nothing on this contract looks a token up by it"
          + " and nothing answers with it. A fixture seeded with one would name a value no call"
          + " of this provider could be resolved by";

  static final String NO_PAYMENT_METHOD_ID_ON_THIS_CONTRACT =
      "provider worldline names a stored card by exactly one value on every route of this"
          + " contract, cardPaymentMethodSpecificInput.token, which the published response spells"
          + " as token on tokenResponse and on cardPaymentMethodSpecificOutput alike. It"
          + " publishes a second identifier namespace nowhere: what its schema calls a captureId"
          + " is a payment id, and \"CaptureId is only necessary for Paypal/PostfinancePay"
          + " multi-capture payments\"";

  static final String A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN =
      "provider worldline charges a stored card by cardPaymentMethodSpecificInput.token, so a"
          + " fixture of this provider carries providerIds.token and a fixture naming none names"
          + " no card any payment of this contract could charge";

  static final String A_TOKENIZED_CARD_IS_A_CARD =
      TYPE_PROPERTY
          + " must be "
          + PaymentMethodKind.CARD.publishedName()
          + ", because the hosted tokenization page of provider worldline tokenizes a card and"
          + " answers a tokenCard, and this slice serves no wallet input at all";

  static final ProviderPaymentMethodFields PAYMENT_METHOD_FIELDS =
      new ProviderPaymentMethodFields(
          WorldlineApi.WORLDLINE,
          List.of(
              new ConfigurableField(
                  ProviderPaymentMethodFields.TOKEN,
                  "token",
                  "The value cardPaymentMethodSpecificInput.token carries and the one a completed"
                      + " hosted tokenization hands back. A fixture naming none names no card any"
                      + " payment of this contract could charge.",
                  true,
                  false,
                  List.of(),
                  THE_TOKEN_THIS_CONTRACT_PUBLISHES)),
          Map.of(
              ProviderPaymentMethodFields.CUSTOMER_ID, NO_CUSTOMER_ID_IS_A_PAYMENT_METHOD_IDENTITY,
              ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                  NO_PAYMENT_METHOD_ID_ON_THIS_CONTRACT));

  private WorldlineFixtureCards() {}

  static PaymentMethodFixturePlan thePlanFor(NewPaymentMethodFixture fixture) {
    if (fixture.type() != PaymentMethodKind.CARD) {
      throw new ProviderFixtureRejectedException(TYPE_PROPERTY, A_TOKENIZED_CARD_IS_A_CARD);
    }
    if (tokenIn(fixture).isEmpty()) {
      throw new ProviderFixtureRejectedException(PROVIDER_IDS, A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN);
    }
    return PaymentMethodFixturePlan.storingNothingOfItsOwn();
  }

  static String tokenIn(NewPaymentMethodFixture fixture) {
    String token = fixture.providerIds().get(ProviderPaymentMethodFields.TOKEN);
    return token == null ? "" : token.strip();
  }
}
