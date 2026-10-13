package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class DeclaredPersonaTest {

  @Test
  void a_persona_that_seeds_no_instrument_is_refused_because_it_would_be_a_preset() {
    assertThatThrownBy(
            () ->
                new DeclaredPersona(
                    PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                    "a-name",
                    "what a tester gets",
                    List.of(ScenarioPreset.CARD_ADD_SUCCESS),
                    List.of()))
        .hasMessage(DeclaredPersona.A_PERSONA_SEEDS_AN_INSTRUMENT);
  }

  @Test
  void a_persona_named_in_a_shape_a_request_cannot_carry_is_refused() {
    assertThatThrownBy(() -> personaNamed("A-Name"))
        .hasMessage(DeclaredPersona.A_PERSONA_IS_NAMED_THE_WAY_A_PRESET_IS);
    assertThatThrownBy(() -> personaNamed("a name"))
        .hasMessage(DeclaredPersona.A_PERSONA_IS_NAMED_THE_WAY_A_PRESET_IS);
    assertThatThrownBy(() -> personaNamed("-a-name"))
        .hasMessage(DeclaredPersona.A_PERSONA_IS_NAMED_THE_WAY_A_PRESET_IS);
    assertThatThrownBy(() -> personaNamed(""))
        .hasMessage(DeclaredPersona.A_PERSONA_IS_NAMED_THE_WAY_A_PRESET_IS);
  }

  @Test
  void the_shape_a_persona_name_has_is_said_before_the_worked_example_and_not_after_it() {
    assertThat(DeclaredPersona.A_PERSONA_IS_NAMED_THE_WAY_A_PRESET_IS)
        .as(
            "the clause used to be appended after the whole refusal, where it read \"for example"
                + " a-customer-with-a-stored-card, the shape a preset name already has\" and"
                + " qualified the example rather than the pattern")
        .isEqualTo(
            "a persona name must match [a-z][a-z0-9-]{0,63}, which is at most 64 characters, the"
                + " first a lowercase letter and the rest a lowercase letter, a digit or a hyphen,"
                + " the shape a preset name already has, for example"
                + " a-customer-with-a-stored-card");
  }

  @Test
  void a_persona_that_says_nothing_about_what_a_tester_gets_is_refused() {
    assertThatThrownBy(
            () ->
                new DeclaredPersona(
                    PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                    "a-name",
                    "  ",
                    List.of(ScenarioPreset.CARD_ADD_SUCCESS),
                    List.of(instrument("an-alias", null))))
        .hasMessage(DeclaredPersona.A_PERSONA_SAYS_WHAT_A_TESTER_GETS);
  }

  @Test
  void a_persona_that_seeds_two_instruments_under_one_alias_is_refused() {
    assertThatThrownBy(
            () ->
                new DeclaredPersona(
                    PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                    "a-name",
                    "what a tester gets",
                    List.of(ScenarioPreset.CARD_ADD_SUCCESS),
                    List.of(instrument("an-alias", null), instrument("an-alias", null))))
        .hasMessage(DeclaredPersona.TWO_INSTRUMENTS_UNDER_ONE_ALIAS);
  }

  @Test
  void an_instrument_that_names_a_rule_of_a_persona_that_writes_none_is_refused() {
    assertThatThrownBy(
            () ->
                new DeclaredPersona(
                    PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                    "a-name",
                    "what a tester gets",
                    List.of(),
                    List.of(instrument("an-alias", 0))))
        .hasMessage(DeclaredPersona.A_RULE_PLACE_NEEDS_A_RULE);
  }

  @Test
  void a_persona_that_writes_no_preset_and_names_no_rule_is_a_persona() {
    DeclaredPersona persona =
        new DeclaredPersona(
            PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
            "a-name",
            "what a tester gets",
            List.of(),
            List.of(instrument("an-alias", null)));

    assertThat(persona.thePresetNamesItWrites()).isEmpty();
  }

  @Test
  void the_preset_names_it_writes_are_the_published_names_in_the_order_it_declared_them() {
    DeclaredPersona persona =
        new DeclaredPersona(
            PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
            "a-name",
            "what a tester gets",
            List.of(ScenarioPreset.PSP_5XX_THEN_SUCCESS, ScenarioPreset.CARD_ADD_SUCCESS),
            List.of(instrument("an-alias", 1)));

    assertThat(persona.thePresetNamesItWrites())
        .containsExactly("psp-5xx-then-success", "card-add-success");
  }

  private static DeclaredPersona personaNamed(String name) {
    return new DeclaredPersona(
        PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
        name,
        "what a tester gets",
        List.of(ScenarioPreset.CARD_ADD_SUCCESS),
        List.of(instrument("an-alias", null)));
  }

  private static PersonaFixture instrument(String alias, @Nullable Integer place) {
    return new PersonaFixture(
        alias,
        "an-owner",
        PaymentMethodKind.CARD,
        new FixtureCard("VISA", "1111", 12, 2030, "credit"),
        List.of(),
        "",
        Map::of,
        place);
  }
}
