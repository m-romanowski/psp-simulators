package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaIntent;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AutraPersonas {

  static final String THE_OWNER = "a-payer-autra-holds-slugs-for";

  static final String NOTHING_IS_PUBLISHED_FOR_IT = "unpublished";

  static final String HOW_THE_SLUGS_ARE_MINTED =
      "both slugs are minted by TokenizeOnTheHostedPage.aSlug, the same minting the hosted card"
          + " page uses when the payer names no card of their own: sixteen uppercase hexadecimal"
          + " characters each, one for tokenData.slugToken and one for tokenData.slugStoredCard,"
          + " because autra names a stored card by that pair and this build stores both";

  static final String THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE =
      "the card facts are the ones autra's own AutraCardTokenizedResult example prints, brand VISA"
          + " and last4 1111 expiring 12/2030; funding reads "
          + NOTHING_IS_PUBLISHED_FOR_IT
          + " because "
          + AutraPayerCards.AUTRA_PUBLISHES_NO_TEST_CARDS;

  private AutraPersonas() {}

  static ProviderPersonas of(IdGenerator ids) {
    return new ProviderPersonas(
        AutraApi.AUTRA,
        List.of(
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "autra-br-card-payer-that-succeeds",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
                "A stored card this run will pre-authorize and capture. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
                "autra-br-card-payer-that-is-declined",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE,
                "A stored card this run will pre-authorize and then decline on capture. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
                "autra-br-card-payer-whose-capture-answer-is-lost",
                ScenarioPreset.CAPTURE_ACCEPTED_RESPONSE_LOST,
                "A stored card whose capture this run drops the connection on, so the caller never"
                    + " learns whether the money moved. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE)),
        Map.of());
  }

  private static DeclaredPersona personaOf(
      IdGenerator ids,
      PersonaIntent intent,
      String name,
      ScenarioPreset preset,
      String whatItGives) {
    return new DeclaredPersona(
        intent,
        name,
        whatItGives,
        List.of(preset),
        List.of(
            new PersonaFixture(
                name,
                THE_OWNER,
                PaymentMethodKind.CARD,
                theCardItPublishes(),
                List.of(
                    ProviderPaymentMethodFields.TOKEN,
                    ProviderPaymentMethodFields.PAYMENT_METHOD_ID),
                HOW_THE_SLUGS_ARE_MINTED,
                () -> theSlugsOf(ids),
                0)));
  }

  private static FixtureCard theCardItPublishes() {
    return new FixtureCard(
        AutraPayerCards.THE_PUBLISHED_BRAND,
        AutraPayerCards.THE_PUBLISHED_LAST4,
        AutraPayerCards.THE_PUBLISHED_EXPIRATION_MONTH,
        AutraPayerCards.THE_PUBLISHED_EXPIRATION_YEAR,
        NOTHING_IS_PUBLISHED_FOR_IT);
  }

  private static Map<String, String> theSlugsOf(IdGenerator ids) {
    Map<String, String> minted = new LinkedHashMap<>();
    minted.put(ProviderPaymentMethodFields.TOKEN, TokenizeOnTheHostedPage.aSlug(ids));
    minted.put(ProviderPaymentMethodFields.PAYMENT_METHOD_ID, TokenizeOnTheHostedPage.aSlug(ids));
    return minted;
  }
}
