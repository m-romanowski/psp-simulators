package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderFixtureRejectedException;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import java.util.List;
import java.util.Map;

final class AutraFixtureCards {

  static final String THE_NETWORK_TOKEN_SLUG_THIS_CONTRACT_PUBLISHES = "token-slug";

  static final String THE_STORED_CARD_SLUG_THIS_CONTRACT_PUBLISHES = "stored-card-slug";

  static final String PROVIDER_IDS = "providerIds";

  static final String TYPE_PROPERTY = "type";

  static final String NO_CUSTOMER =
      "provider autra publishes no customer, payer or owner object on its acquiring surface at"
          + " all: the only party field these endpoints carry is documentId, which this contract"
          + " carries as the merchant's own CPF or CNPJ, so a seeded customerId would name nothing"
          + " any call of this provider could be matched against";

  static final String A_STORED_CARD_IS_NAMED_BY_A_PAIR =
      "provider autra names a stored card by two values that travel together inside tokenData,"
          + " slugToken and slugStoredCard, and publishes a gloss for each that says which of the"
          + " two published fixture keys it is: slugToken is the \"Network token\", \"Single-use"
          + " token reference\", and slugStoredCard is the \"Reference your backend uses to charge"
          + " the card later\". A fixture of this provider therefore carries providerIds.token for"
          + " the first and providerIds.paymentMethodId for the second, and at least one of the"
          + " two, because a tokenData naming neither names no card at all";

  static final String A_TOKENIZED_CARD_IS_A_CARD =
      TYPE_PROPERTY
          + " must be "
          + PaymentMethodKind.CARD.publishedName()
          + ", because provider autra tokenizes a card on its hosted card page and publishes no"
          + " wallet and no other instrument anywhere on the acquiring surface this contract"
          + " covers";

  static final ProviderPaymentMethodFields PAYMENT_METHOD_FIELDS =
      new ProviderPaymentMethodFields(
          AutraApi.AUTRA,
          List.of(
              new ConfigurableField(
                  ProviderPaymentMethodFields.TOKEN,
                  "slugToken",
                  "The network-token half of tokenData, which autra says to prefer \"quando"
                      + " disponivel\". A fixture naming neither half names no card at all.",
                  false,
                  false,
                  List.of(),
                  THE_NETWORK_TOKEN_SLUG_THIS_CONTRACT_PUBLISHES),
              new ConfigurableField(
                  ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                  "slugStoredCard",
                  "The stored-card half of tokenData, which autra glosses as the \"Reference your"
                      + " backend uses to charge the card later\". A fixture naming neither half"
                      + " names no card at all.",
                  false,
                  false,
                  List.of(),
                  THE_STORED_CARD_SLUG_THIS_CONTRACT_PUBLISHES)),
          Map.of(ProviderPaymentMethodFields.CUSTOMER_ID, NO_CUSTOMER));

  private AutraFixtureCards() {}

  static PaymentMethodFixturePlan thePlanFor(NewPaymentMethodFixture fixture) {
    if (fixture.type() != PaymentMethodKind.CARD) {
      throw new ProviderFixtureRejectedException(TYPE_PROPERTY, A_TOKENIZED_CARD_IS_A_CARD);
    }
    if (slugIn(fixture, ProviderPaymentMethodFields.TOKEN).isEmpty()
        && slugIn(fixture, ProviderPaymentMethodFields.PAYMENT_METHOD_ID).isEmpty()) {
      throw new ProviderFixtureRejectedException(PROVIDER_IDS, A_STORED_CARD_IS_NAMED_BY_A_PAIR);
    }
    return PaymentMethodFixturePlan.storingNothingOfItsOwn();
  }

  static String slugIn(NewPaymentMethodFixture fixture, String key) {
    String slug = fixture.providerIds().get(key);
    return slug == null ? "" : slug.strip();
  }
}
