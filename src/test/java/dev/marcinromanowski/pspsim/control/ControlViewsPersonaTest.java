package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.control.ControlPayloads.PersonaCatalogueResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.PersonaInstrumentView;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaIntent;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ControlViewsPersonaTest {

  private static final ProviderId PROVIDER = ProviderId.of("viewed");

  @Test
  void the_catalogue_a_harness_reads_names_the_persona_its_intent_and_the_presets_it_writes() {
    PersonaCatalogueResponse view = ControlViews.viewOf(aCatalogue());

    assertThat(view.provider()).isEqualTo("viewed");
    assertThat(view.personas())
        .singleElement()
        .satisfies(
            persona -> {
              assertThat(persona.name()).isEqualTo("a-card-that-works");
              assertThat(persona.intent()).isEqualTo("a-saved-card-that-succeeds");
              assertThat(persona.whatATesterGets())
                  .isEqualTo(PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS.whatATesterGets());
              assertThat(persona.whatItGives()).isEqualTo("a stored card this provider captures");
              assertThat(persona.presets()).containsExactly("preauth-success-capture-success");
            });
  }

  @Test
  void the_catalogue_names_every_intent_it_publishes_no_persona_for_and_what_it_has_instead() {
    PersonaCatalogueResponse view = ControlViews.viewOf(aCatalogue());

    assertThat(view.uncovered())
        .hasSize(PersonaIntent.values().length - 1)
        .anySatisfy(
            gap -> {
              assertThat(gap.intent()).isEqualTo("a-saved-card-that-is-declined");
              assertThat(gap.instead()).isEqualTo("nothing is declined here");
            });
  }

  @Test
  void
      an_instrument_view_carries_the_keys_it_mints_and_the_card_it_describes_and_no_minted_value() {
    List<PersonaInstrumentView> views =
        ControlViews.viewsOf(aCatalogue().personas().get(0).fixtures());

    assertThat(views)
        .singleElement()
        .satisfies(
            instrument -> {
              assertThat(instrument.alias()).isEqualTo("a-card-that-works");
              assertThat(instrument.ownerId()).isEqualTo("an-owner");
              assertThat(instrument.type()).isEqualTo("card");
              assertThat(instrument.providerNativeIds()).containsExactly("token");
              assertThat(instrument.howThoseAreMinted()).isEqualTo("a minting of its own");
              assertThat(instrument.triggersTheRuleAt()).isEqualTo(0);
              assertThat(instrument.card()).isNotNull();
              assertThat(instrument.card().last4()).isEqualTo("1111");
              assertThat(instrument.card().brand()).isEqualTo("VISA");
            });
  }

  @Test
  void an_instrument_that_describes_no_card_carries_no_card_view() {
    PersonaFixture wallet =
        new PersonaFixture(
            "a-wallet", "an-owner", PaymentMethodKind.WALLET, null, List.of(), "", Map::of, null);

    assertThat(ControlViews.viewsOf(List.of(wallet)))
        .singleElement()
        .satisfies(
            instrument -> {
              assertThat(instrument.card()).isNull();
              assertThat(instrument.type()).isEqualTo("wallet");
              assertThat(instrument.triggersTheRuleAt()).isNull();
            });
  }

  @Test
  void a_provider_that_publishes_no_persona_reads_as_an_empty_list_and_every_intent_uncovered() {
    PersonaCatalogueResponse view =
        ControlViews.viewOf(ProviderPersonas.noneOfThem(PROVIDER, "it holds no instrument"));

    assertThat(view.personas()).isEmpty();
    assertThat(view.uncovered()).hasSize(PersonaIntent.values().length);
  }

  private static ProviderPersonas aCatalogue() {
    Map<PersonaIntent, String> declined = new LinkedHashMap<>();
    declined.put(PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED, "nothing is declined here");
    declined.put(PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST, "nothing is lost here");
    return new ProviderPersonas(
        PROVIDER,
        List.of(
            new DeclaredPersona(
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "a-card-that-works",
                "a stored card this provider captures",
                List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
                List.of(
                    new PersonaFixture(
                        "a-card-that-works",
                        "an-owner",
                        PaymentMethodKind.CARD,
                        new FixtureCard("VISA", "1111", 12, 2030, "credit"),
                        List.of(ProviderPaymentMethodFields.TOKEN),
                        "a minting of its own",
                        () -> Map.of(ProviderPaymentMethodFields.TOKEN, "tok_1"),
                        0)))),
        declined);
  }
}
