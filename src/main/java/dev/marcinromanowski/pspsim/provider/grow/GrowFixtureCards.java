package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import java.util.List;
import java.util.Map;

final class GrowFixtureCards {

  private GrowFixtureCards() {}

  private static final String NO_CUSTOMER =
      "provider grow publishes no customer or owner id on any endpoint of this contract, so there"
          + " is nothing on the wire for a seeded customerId to be matched against; the owner a"
          + " fixture belongs to is ownerId, which this simulator owns";

  private static final String THE_CARD_TOKEN_IS_THE_PAYMENT_METHOD_ID =
      "provider grow names a saved card cardToken and nothing else, so a fixture naming"
          + " providerIds.paymentMethodId would carry one id under a name grow never prints";

  static final String A_CARD_TOKEN_IS_MINTED_BY_A_SAVED_PROCESS =
      "provider grow mints a cardToken only where a hosted process saved a card, and"
          + " createTransactionWithToken resolves a cardToken against the process that saved it and"
          + " answers err.id 140 for one it cannot, so a token seeded here would be refused by the"
          + " very call the fixture was seeded for; a grow fixture therefore carries the card facts"
          + " and the scenario it names and no provider-native id at all";

  static final ProviderPaymentMethodFields PAYMENT_METHOD_FIELDS =
      new ProviderPaymentMethodFields(
          GrowLightApi.GROW,
          List.of(),
          Map.of(
              ProviderPaymentMethodFields.CUSTOMER_ID,
              NO_CUSTOMER,
              ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
              THE_CARD_TOKEN_IS_THE_PAYMENT_METHOD_ID,
              ProviderPaymentMethodFields.TOKEN,
              A_CARD_TOKEN_IS_MINTED_BY_A_SAVED_PROCESS));
}
