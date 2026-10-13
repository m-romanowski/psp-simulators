package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ALineRefusalNamesTheBoxItWasTypedIntoTest {

  private static final Path WHERE_TEMPLATES_ARE_SHIPPED =
      Path.of("src", "main", "resources", "templates");

  private static final String A_LINE_NO_BOX_CAN_READ = "nothing this box can read";

  private record Box(String screen, String name, String theWordsALineIsRefusedIn) {}

  private static final Map<String, Box> THE_BOXES_A_LINE_IS_TYPED_INTO = theBoxesALineIsTypedInto();

  private static final ProviderAccountFields THE_FIELDS_OF_A_PROVIDER_THAT_DRAWS_NO_BLOCK =
      new ProviderAccountFields(
          ProviderId.of("twokeys"),
          List.of(
              new ConfigurableField(
                  "secretKey",
                  "the key",
                  "a sentence a test does not read",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read")));

  private static Map<String, Box> theBoxesALineIsTypedInto() {
    Map<String, Box> boxes = new LinkedHashMap<>();
    boxes.put(
        RunBundleScreen.THE_BOX_SECRETS_ARE_TYPED_INTO,
        new Box(
            "run-bundle.html",
            RunBundleScreen.THE_BOX_SECRETS_ARE_TYPED_INTO,
            theWordsOf(
                RunBundleScreen.theSecretsTypedInto(
                    A_LINE_NO_BOX_CAN_READ, THE_FIELDS_OF_A_PROVIDER_THAT_DRAWS_NO_BLOCK))));
    boxes.put(
        ReproductionScreen.THE_BOX_A_REMAPPING_IS_TYPED_INTO,
        new Box(
            "repro-bundle.html",
            ReproductionScreen.THE_BOX_A_REMAPPING_IS_TYPED_INTO,
            theWordsOf(ReproductionScreen.theRemappingTypedInto(A_LINE_NO_BOX_CAN_READ))));
    return Map.copyOf(boxes);
  }

  @Test
  void each_box_a_line_is_typed_into_refuses_a_line_of_it_by_the_name_its_screen_draws_it_under() {
    assertThat(THE_BOXES_A_LINE_IS_TYPED_INTO)
        .as("the boxes of this build a tester types a line at a time into")
        .hasSize(2)
        .allSatisfy(
            (name, box) -> {
              String markup = read(WHERE_TEMPLATES_ARE_SHIPPED.resolve(box.screen()));
              assertThat(markup)
                  .as("the screen %s draws the box this refusal names", box.screen())
                  .contains("name=\"" + name + "\"")
                  .contains("<label for=\"" + name + "\">" + name);
              assertThat(box.theWordsALineIsRefusedIn())
                  .as(
                      "a tester is told which box of %s could not be read in the name that box is"
                          + " drawn and labelled under, and never in the name of a field of a"
                          + " body no screen posts",
                      box.screen())
                  .startsWith(name + " line 1:");
            });
  }

  private static String theWordsOf(RunBundleScreen.Supplied supplied) {
    return ((RunBundleScreen.Supplied.Unreadable) supplied).message();
  }

  private static String theWordsOf(ReproductionScreen.Remapping remapping) {
    return ((ReproductionScreen.Remapping.Unreadable) remapping).message();
  }

  private static String read(Path template) {
    try {
      return Files.readString(template);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
