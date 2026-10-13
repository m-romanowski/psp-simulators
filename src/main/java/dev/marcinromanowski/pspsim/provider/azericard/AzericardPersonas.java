package dev.marcinromanowski.pspsim.provider.azericard;

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

final class AzericardPersonas {

  static final String THE_OWNER = "a-cardholder-azericard-holds-a-token-for";

  static final String NOTHING_IS_PUBLISHED_FOR_IT = "unpublished";

  static final String THE_PUBLISHED_LAST4 = "2601";

  static final int THE_PUBLISHED_EXPIRATION_MONTH = 5;

  static final int THE_PUBLISHED_EXPIRATION_YEAR = 2031;

  static final String HOW_A_TOKEN_IS_MINTED =
      "the TOKEN is minted by AzericardStoredCard.aToken, the same minting a card-storing"
          + " authorization uses: twenty-eight uppercase hexadecimal characters and no prefix, the"
          + " width a stored-card payment sends under TOKEN";

  static final String THE_CARD_FACTS_ARE_THE_FIRST_PUBLISHED_TEST_CARD =
      "the card facts are azericard's first published test card, the one whose PAN ends 2601 and"
          + " whose EXP_MONTH and EXP_YEAR are 05 and 31; brand and funding read "
          + NOTHING_IS_PUBLISHED_FOR_IT
          + " because that table publishes a PAN, an expiry, a CVV and an SMS OTP and names a"
          + " brand for one other row only";

  static final String AN_INSTRUMENT_BELONGS_TO_A_TERMINAL =
      "seed this persona into an account this test run configures for azericard: a stored card"
          + " belongs to the TERMINAL that stored it, and a card with no terminal could never be"
          + " charged";

  private AzericardPersonas() {}

  static ProviderPersonas of(IdGenerator ids) {
    return new ProviderPersonas(
        AzericardGateway.AZERICARD,
        List.of(
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "azericard-stored-card-that-succeeds",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
                "A stored card this run will pre-authorize and complete. "
                    + THE_CARD_FACTS_ARE_THE_FIRST_PUBLISHED_TEST_CARD
                    + ". "
                    + AN_INSTRUMENT_BELONGS_TO_A_TERMINAL),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
                "azericard-stored-card-that-is-declined",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE,
                "A stored card this run will pre-authorize and then decline on completion. "
                    + THE_CARD_FACTS_ARE_THE_FIRST_PUBLISHED_TEST_CARD
                    + ". "
                    + AN_INSTRUMENT_BELONGS_TO_A_TERMINAL),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
                "azericard-stored-card-whose-completion-answer-is-lost",
                ScenarioPreset.CAPTURE_ACCEPTED_RESPONSE_LOST,
                "A stored card whose completion this run drops the connection on, so the caller"
                    + " never learns whether the money moved. "
                    + THE_CARD_FACTS_ARE_THE_FIRST_PUBLISHED_TEST_CARD
                    + ". "
                    + AN_INSTRUMENT_BELONGS_TO_A_TERMINAL)),
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
                () -> Map.of(ProviderPaymentMethodFields.TOKEN, AzericardStoredCard.aToken(ids)),
                0)));
  }

  private static FixtureCard theCardItPublishes() {
    return new FixtureCard(
        NOTHING_IS_PUBLISHED_FOR_IT,
        THE_PUBLISHED_LAST4,
        THE_PUBLISHED_EXPIRATION_MONTH,
        THE_PUBLISHED_EXPIRATION_YEAR,
        NOTHING_IS_PUBLISHED_FOR_IT);
  }
}
