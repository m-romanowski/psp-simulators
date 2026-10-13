package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaIntent;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import java.util.List;
import java.util.Map;

final class WorldlinePersonas {

  static final String THE_OWNER = "a-shopper-worldline-holds-a-token-for";

  static final String NOTHING_IS_PUBLISHED_FOR_IT = "unpublished";

  static final String HOW_A_TOKEN_IS_MINTED =
      "the token is minted by WorldlineIdentifiers.aToken, the same minting a completed hosted"
          + " tokenization hands back: thirty-two lowercase hexadecimal characters and no prefix,"
          + " so cardPaymentMethodSpecificInput.token carries it as it stands";

  static final String THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE =
      "the card facts are the ones the published tokenCard sample prints, last4 1111 expiring"
          + " 12/2030; brand and funding read "
          + NOTHING_IS_PUBLISHED_FOR_IT
          + " because "
          + WorldlinePayerCards.WORLDLINE_PUBLISHES_NO_TEST_CARD_LIST_ON_THESE_PAGES;

  private WorldlinePersonas() {}

  static ProviderPersonas of(IdGenerator ids) {
    return new ProviderPersonas(
        WorldlineApi.WORLDLINE,
        List.of(
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "worldline-customer-with-tokenized-card",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
                "A token this run will authorize and capture. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
                "worldline-tokenized-card-that-is-declined",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE,
                "A token this run will authorize and then decline on capture. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
                "worldline-tokenized-card-whose-capture-answer-is-lost",
                ScenarioPreset.CAPTURE_ACCEPTED_RESPONSE_LOST,
                "A token whose capture this run drops the connection on, so the caller never"
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
                List.of(ProviderPaymentMethodFields.TOKEN),
                HOW_A_TOKEN_IS_MINTED,
                () -> Map.of(ProviderPaymentMethodFields.TOKEN, WorldlineIdentifiers.aToken(ids)),
                0)));
  }

  private static FixtureCard theCardItPublishes() {
    return new FixtureCard(
        NOTHING_IS_PUBLISHED_FOR_IT,
        WorldlinePayerCards.THE_PUBLISHED_LAST4,
        WorldlinePayerCards.THE_PUBLISHED_EXPIRATION_MONTH,
        WorldlinePayerCards.THE_PUBLISHED_EXPIRATION_YEAR,
        NOTHING_IS_PUBLISHED_FOR_IT);
  }
}
