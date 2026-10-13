package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import java.util.List;
import java.util.Map;

final class TranzilaFixtureCards {

  static final String A_TOKEN_TRANZILA_PRINTS = "me4b50240d8c6222312";

  static final String PROVIDER_IDS = "providerIds";

  static final String TYPE_PROPERTY = "type";

  static final String CARD_BRAND = "card.brand";

  static final String THE_CARD_TYPES =
      "mastercard, visa, diners, amex, isracard and maestro, the six card types of the payment"
          + " page's cardtype table, 1 to 6";

  static final String NO_CUSTOMER_ID_ON_THIS_CONTRACT =
      "provider tranzila names a stored card by its token alone: the token goes where the card"
          + " number goes, and no request of this contract names a customer a token belongs to";

  static final String NO_PAYMENT_METHOD_ID_ON_THIS_CONTRACT =
      "provider tranzila publishes one identifier for a stored card, the token that \"replaces"
          + " only the full card number\", and no second identifier namespace for a payment"
          + " method";

  static final String A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN =
      "provider tranzila charges a stored card by sending its token as card_number, so a fixture"
          + " of this provider carries providerIds.token, and that token must be 8 to 19 letters"
          + " and digits with at least one letter, or the request would read it as a card number";

  static final String A_STORED_CARD_IS_A_CARD =
      TYPE_PROPERTY
          + " must be "
          + PaymentMethodKind.CARD.publishedName()
          + ", because a token of provider tranzila replaces a card number and nothing else";

  static final String A_CARD_TYPE_IS_PUBLISHED =
      CARD_BRAND + " must be one of " + THE_CARD_TYPES + ", because a charge answers card_type";

  static final ProviderPaymentMethodFields PAYMENT_METHOD_FIELDS =
      new ProviderPaymentMethodFields(
          TranzilaApi.TRANZILA,
          List.of(
              new ConfigurableField(
                  ProviderPaymentMethodFields.TOKEN,
                  "token",
                  "The value a request sends as card_number in place of the card number, and the"
                      + " one an approved transaction answers in transaction_result.token. Letters"
                      + " and digits, 8 to 19 of them, at least one a letter.",
                  true,
                  false,
                  List.of(),
                  A_TOKEN_TRANZILA_PRINTS)),
          Map.of(
              ProviderPaymentMethodFields.CUSTOMER_ID, NO_CUSTOMER_ID_ON_THIS_CONTRACT,
              ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                  NO_PAYMENT_METHOD_ID_ON_THIS_CONTRACT));

  private TranzilaFixtureCards() {}

  static PaymentMethodFixturePlan thePlanFor(NewPaymentMethodFixture fixture) {
    FixtureCard card = fixture.card();
    if (fixture.type() != PaymentMethodKind.CARD || card == null) {
      throw new ProviderFixtureRejectedException(TYPE_PROPERTY, A_STORED_CARD_IS_A_CARD);
    }
    if (!isAToken(fixture.providerIds().get(ProviderPaymentMethodFields.TOKEN))) {
      throw new ProviderFixtureRejectedException(PROVIDER_IDS, A_STORED_CARD_IS_NAMED_BY_ITS_TOKEN);
    }
    if (TranzilaCard.cardTypeNamed(card.brand()).isEmpty()) {
      throw new ProviderFixtureRejectedException(CARD_BRAND, A_CARD_TYPE_IS_PUBLISHED);
    }
    return PaymentMethodFixturePlan.storingNothingOfItsOwn();
  }

  static boolean isAToken(@org.jspecify.annotations.Nullable String token) {
    return token != null && token.matches("(?=.*[A-Za-z])[0-9A-Za-z]{8,19}");
  }
}
