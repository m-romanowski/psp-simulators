package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;

public final class APresetReadsBackInWords {

  private APresetReadsBackInWords() {}

  public static void assertItDoes(ScenarioPreset preset, PresetExpansion expansion) {
    APresetInWords read = APresetInWords.of(preset, expansion);

    assertThat(read.inTheChooser())
        .as("a tester picks a flow by the name the control API publishes for it")
        .startsWith(preset.publishedName() + APresetInWords.THE_CHOOSER_READS);
    switch (expansion) {
      case PresetExpansion.Rules written -> {
        assertThat(read.whatItDoes())
            .as(
                "a flow reads back one line per rule it writes, because a line that stood for two"
                    + " rules would have to claim an order between them that the rules do not"
                    + " carry")
            .hasSameSizeAs(written.rules());
        assertThat(read.whatItDoes())
            .allSatisfy(
                line ->
                    assertThat(line)
                        .as("a rule reads back as something, and never as a sequence of rules")
                        .isNotBlank()
                        .doesNotContain(", then "));
      }
      case PresetExpansion.NotExpressible unexpressed -> {
        assertThat(read.whatItDoes())
            .as("a flow this provider cannot express writes no rule, so it reads back none")
            .isEmpty();
        assertThat(read.whyThisProviderCannotExpressIt())
            .as("the reason a tester reads is the provider's own, word for word")
            .isEqualTo(unexpressed.reason());
        assertThat(read.inTheChooser()).endsWith(APresetInWords.NO_PROVIDER_HERE_EXPRESSES_IT);
      }
    }
  }
}
