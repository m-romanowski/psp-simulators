package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.AShapeSaidInWords;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record DeclaredPersona(
    PersonaIntent intent,
    String name,
    String whatItGives,
    List<ScenarioPreset> presets,
    List<PersonaFixture> fixtures) {

  private static final AShapeSaidInWords SHAPE =
      AShapeSaidInWords.of("[a-z][a-z0-9-]{0,63}", "a-customer-with-a-stored-card");

  public static final String A_PERSONA_IS_NAMED_THE_WAY_A_PRESET_IS =
      SHAPE.theRefusalOf("a persona name", "the shape a preset name already has");

  public static final String A_PERSONA_SEEDS_AN_INSTRUMENT =
      "a persona must seed at least one instrument. A persona that only wrote scenario rules would"
          + " be a preset under another name, and it would leave nothing in the run under a name of"
          + " its own, so pressing it twice could not be told from pressing it once";

  public static final String A_PERSONA_SAYS_WHAT_A_TESTER_GETS =
      "a persona must say, in words a tester who knows no provider internals can read, what"
          + " pressing it gives them";

  public static final String TWO_INSTRUMENTS_UNDER_ONE_ALIAS =
      "a persona must not seed two instruments under one alias, because a run holds one fixture"
          + " per alias and the second would be refused as a duplicate of the first";

  public static final String A_RULE_PLACE_NEEDS_A_RULE =
      "a persona instrument names the place of a rule this persona writes, and this persona writes"
          + " no rule, so the place names nothing";

  public DeclaredPersona {
    if (!SHAPE.admits(name)) {
      throw new IllegalArgumentException(A_PERSONA_IS_NAMED_THE_WAY_A_PRESET_IS);
    }
    if (whatItGives.isBlank()) {
      throw new IllegalArgumentException(A_PERSONA_SAYS_WHAT_A_TESTER_GETS);
    }
    if (fixtures.isEmpty()) {
      throw new IllegalArgumentException(A_PERSONA_SEEDS_AN_INSTRUMENT);
    }
    Set<String> aliases = new LinkedHashSet<>();
    for (PersonaFixture fixture : fixtures) {
      if (!aliases.add(fixture.alias())) {
        throw new IllegalArgumentException(TWO_INSTRUMENTS_UNDER_ONE_ALIAS);
      }
      if (fixture.triggersTheRuleAt() != null && presets.isEmpty()) {
        throw new IllegalArgumentException(A_RULE_PLACE_NEEDS_A_RULE);
      }
    }
    presets = List.copyOf(presets);
    fixtures = List.copyOf(fixtures);
  }

  public List<String> thePresetNamesItWrites() {
    List<String> names = new ArrayList<>();
    for (ScenarioPreset preset : presets) {
      names.add(preset.publishedName());
    }
    return List.copyOf(names);
  }
}
