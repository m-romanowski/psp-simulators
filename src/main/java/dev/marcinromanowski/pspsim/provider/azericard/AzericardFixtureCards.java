package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AzericardFixtureCards {

  private AzericardFixtureCards() {}

  static final String TOKEN_PROPERTY = "providerIds.token";

  static final String MERCHANT_PROPERTY = "merchantId";

  static final String TYPE_PROPERTY = "type";

  static final String A_TOKEN_OF_THE_WIDTH_THIS_GATEWAY_CARRIES = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4";

  static final String A_TOKEN_IS_CLAIMED_ONCE =
      "providerIds.token names a TOKEN this test run already holds a stored card for, and provider"
          + " azericard resolves a stored-card payment by TOKEN alone, so a second card under the"
          + " same value would make which card was charged a matter of arrival order";

  static final String A_TOKEN_IS_WHAT_A_STORED_CARD_PAYMENT_SENDS =
      "providerIds.token must be the TOKEN a stored-card payment sends, which provider azericard"
          + " sizes at exactly "
          + AzericardStoredCard.TOKEN_LENGTH
          + " characters; a value the gateway could never carry would be seeded and never"
          + " reachable";

  private static final String NO_CUSTOMER =
      "provider azericard publishes no customer or owner id on any endpoint of this contract, so"
          + " there is nothing on the wire for a seeded customerId to be matched against; the owner"
          + " a fixture belongs to is ownerId, which this simulator owns";

  private static final String THE_TOKEN_IS_THE_PAYMENT_METHOD_ID =
      "provider azericard names a saved card TOKEN and nothing else, and a stored-card payment"
          + " sends it in TOKEN, so a fixture naming both providerIds.token and"
          + " providerIds.paymentMethodId would carry one id under two names";

  static final ProviderPaymentMethodFields PAYMENT_METHOD_FIELDS =
      new ProviderPaymentMethodFields(
          AzericardGateway.AZERICARD,
          List.of(
              new ConfigurableField(
                  ProviderPaymentMethodFields.TOKEN,
                  "TOKEN",
                  "The value a stored-card payment sends instead of a card, which provider"
                      + " azericard sizes at exactly "
                      + AzericardStoredCard.TOKEN_LENGTH
                      + " characters.",
                  true,
                  false,
                  List.of(),
                  A_TOKEN_OF_THE_WIDTH_THIS_GATEWAY_CARRIES)),
          Map.of(
              ProviderPaymentMethodFields.CUSTOMER_ID,
              NO_CUSTOMER,
              ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
              THE_TOKEN_IS_THE_PAYMENT_METHOD_ID));

  static AzericardTerminal theTerminalItIsSeededFor(
      NewPaymentMethodFixture fixture, @Nullable AzericardTerminal terminal) {
    if (fixture.type() != PaymentMethodKind.CARD) {
      throw new ProviderFixtureRejectedException(
          TYPE_PROPERTY,
          "provider azericard stores a card against a TOKEN and publishes no wallet and no other"
              + " instrument anywhere in this contract, so only type "
              + PaymentMethodKind.CARD.publishedName()
              + " can be seeded for it");
    }
    if (terminal == null) {
      throw new ProviderFixtureRejectedException(
          MERCHANT_PROPERTY,
          "merchantId must name an account this test run configures for provider azericard,"
              + " because a stored card belongs to the TERMINAL that stored it and a card with no"
              + " terminal could never be charged");
    }
    if (tokenOf(fixture).length() != AzericardStoredCard.TOKEN_LENGTH) {
      throw new ProviderFixtureRejectedException(
          TOKEN_PROPERTY, A_TOKEN_IS_WHAT_A_STORED_CARD_PAYMENT_SENDS);
    }
    return terminal;
  }

  static String tokenOf(NewPaymentMethodFixture fixture) {
    String token = fixture.providerIds().get(ProviderPaymentMethodFields.TOKEN);
    return token == null ? "" : token;
  }

  static String tokenOf(PaymentMethodFixture fixture) {
    String token = fixture.providerIds().get(ProviderPaymentMethodFields.TOKEN);
    if (token == null) {
      throw new IllegalStateException(
          "a payment-method fixture of provider azericard reached the store with no"
              + " providerIds.token, although the seed refuses one that leaves it out");
    }
    return token;
  }
}
