package dev.marcinromanowski.pspsim.provider.tranzila;

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

final class TranzilaPersonas {

  static final String THE_OWNER = "a-card-holder-tranzila-holds-a-token-for";

  static final String THE_BRAND = "visa";

  static final String THE_LAST4 = "4245";

  static final int THE_EXPIRY_MONTH = 7;

  static final int THE_EXPIRY_YEAR = 2030;

  static final String THE_FUNDING = "unpublished";

  static final String HOW_A_TOKEN_IS_MINTED =
      "the token is minted the way an approved transaction of this simulator mints one: a"
          + " letter, fourteen hexadecimal characters and the card's last four digits, the way the"
          + " Hosted Fields response example's token ends in the last four digits it answers";

  static final String THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE =
      "the card facts are the published create example's, a Visa ending 4245; its funding is "
          + THE_FUNDING
          + " because tranzila publishes none";

  private TranzilaPersonas() {}

  static ProviderPersonas of(IdGenerator ids) {
    return new ProviderPersonas(
        TranzilaApi.TRANZILA,
        List.of(
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "tranzila-token-that-is-forced",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS,
                "A token this run will hold with a J5 and force. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
                "tranzila-token-whose-force-is-declined",
                ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_DECLINE,
                "A token this run will hold with a J5 and then decline 004 on the force. "
                    + THE_CARD_FACTS_ARE_THE_PUBLISHED_SAMPLE),
            personaOf(
                ids,
                PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
                "tranzila-token-whose-force-answer-is-lost",
                ScenarioPreset.CAPTURE_ACCEPTED_RESPONSE_LOST,
                "A token whose force this run drops the connection on, so the caller never"
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
                new FixtureCard(
                    THE_BRAND, THE_LAST4, THE_EXPIRY_MONTH, THE_EXPIRY_YEAR, THE_FUNDING),
                List.of(ProviderPaymentMethodFields.TOKEN),
                HOW_A_TOKEN_IS_MINTED,
                () ->
                    Map.of(
                        ProviderPaymentMethodFields.TOKEN,
                        TranzilaIdentifiers.aTokenFor(ids, THE_LAST4)),
                0)));
  }
}
