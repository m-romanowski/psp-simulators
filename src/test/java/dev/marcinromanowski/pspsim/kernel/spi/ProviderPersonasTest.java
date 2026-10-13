package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderPersonasTest {

  private static final ProviderId PROVIDER = ProviderId.of("onepersona");

  @Test
  void a_provider_that_neither_publishes_nor_declines_an_intent_is_refused() {
    assertThatThrownBy(
            () ->
                new ProviderPersonas(
                    PROVIDER, List.of(personaOf("a-name", theFirstIntent())), Map.of()))
        .hasMessageContaining(ProviderPersonas.A_PROVIDER_ACCOUNTS_FOR_EVERY_INTENT)
        .hasMessageContaining("a-saved-card-that-is-declined");
  }

  @Test
  void a_provider_that_both_publishes_and_declines_one_intent_is_refused() {
    Map<PersonaIntent, String> declined = new LinkedHashMap<>(theRest());
    declined.put(theFirstIntent(), "and also has none");

    assertThatThrownBy(
            () ->
                new ProviderPersonas(
                    PROVIDER, List.of(personaOf("a-name", theFirstIntent())), declined))
        .hasMessageContaining("publishes a persona for intent a-saved-card-that-succeeds")
        .hasMessageContaining("and also says it has none");
  }

  @Test
  void a_provider_that_declines_an_intent_without_saying_why_is_refused() {
    Map<PersonaIntent, String> blank = new LinkedHashMap<>();
    for (PersonaIntent intent : PersonaIntent.values()) {
      blank.put(intent, "  ");
    }

    assertThatThrownBy(() -> new ProviderPersonas(PROVIDER, List.of(), blank))
        .hasMessageContaining(ProviderPersonas.A_DECLINED_INTENT_SAYS_WHY);
  }

  @Test
  void a_provider_that_publishes_two_personas_for_one_intent_is_refused() {
    assertThatThrownBy(
            () ->
                new ProviderPersonas(
                    PROVIDER,
                    List.of(
                        personaOf("a-name", theFirstIntent()),
                        personaOf("another-name", theFirstIntent())),
                    theRest()))
        .hasMessageContaining("publishes two personas for intent a-saved-card-that-succeeds");
  }

  @Test
  void a_provider_that_publishes_two_personas_under_one_name_is_refused() {
    Map<PersonaIntent, String> declined = new LinkedHashMap<>();
    declined.put(PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST, "nothing is lost here");

    assertThatThrownBy(
            () ->
                new ProviderPersonas(
                    PROVIDER,
                    List.of(
                        personaOf("a-name", PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS),
                        personaOf("a-name", PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED)),
                    declined))
        .hasMessageContaining("publishes two personas named a-name");
  }

  @Test
  void a_catalogue_of_none_declines_every_intent_for_the_reason_the_module_gave() {
    ProviderPersonas none = ProviderPersonas.noneOfThem(PROVIDER, "it holds no instrument");

    assertThat(none.personas()).isEmpty();
    assertThat(none.intentsItPublishesNoneFor()).hasSize(PersonaIntent.values().length);
    for (PersonaIntent intent : PersonaIntent.values()) {
      assertThat(none.intentsItPublishesNoneFor()).containsEntry(intent, "it holds no instrument");
    }
  }

  @Test
  void a_catalogue_of_none_refuses_every_name_by_saying_it_publishes_no_persona_at_all() {
    ProviderPersonas none = ProviderPersonas.noneOfThem(PROVIDER, "it holds no instrument");

    assertThat(none.named("a-name")).isEmpty();
    assertThat(none.theRefusalOfAnUnknownPersona())
        .isEqualTo(
            "provider onepersona publishes no persona at all, so no name could be seeded for it");
  }

  @Test
  void a_catalogue_that_publishes_personas_refuses_an_unknown_name_by_listing_the_known_ones() {
    ProviderPersonas published =
        new ProviderPersonas(PROVIDER, List.of(personaOf("a-name", theFirstIntent())), theRest());

    assertThat(published.named("another-name")).isEmpty();
    assertThat(published.named("a-name")).isPresent();
    assertThat(published.theNamesItPublishes()).containsExactly("a-name");
    assertThat(published.theRefusalOfAnUnknownPersona())
        .isEqualTo("persona must name one this provider publishes, which are [a-name]");
  }

  private static PersonaIntent theFirstIntent() {
    return PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS;
  }

  private static Map<PersonaIntent, String> theRest() {
    Map<PersonaIntent, String> declined = new LinkedHashMap<>();
    for (PersonaIntent intent : PersonaIntent.values()) {
      if (intent != theFirstIntent()) {
        declined.put(intent, "this provider has " + intent.publishedName() + " nowhere");
      }
    }
    return declined;
  }

  private static DeclaredPersona personaOf(String name, PersonaIntent intent) {
    return new DeclaredPersona(
        intent,
        name,
        "what a tester gets",
        List.of(ScenarioPreset.CARD_ADD_SUCCESS),
        List.of(
            new PersonaFixture(
                name,
                "an-owner",
                PaymentMethodKind.CARD,
                new FixtureCard("VISA", "1111", 12, 2030, "credit"),
                List.of(),
                "",
                Map::of,
                null)));
  }
}
