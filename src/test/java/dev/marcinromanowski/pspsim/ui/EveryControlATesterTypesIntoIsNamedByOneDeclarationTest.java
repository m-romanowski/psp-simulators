package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EveryControlATesterTypesIntoIsNamedByOneDeclarationTest {

  private static final Path WHERE_TEMPLATES_ARE_SHIPPED =
      Path.of("src", "main", "resources", "templates");

  private static final String A_TEMPLATE_SPELLING_THE_NAME_ITSELF =
      "'" + TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL + "'";

  private static final String A_TEMPLATE_SPELLING_THE_UNIT_NAME_ITSELF =
      "'" + AccountForm.SUBMITTED_UNIT_PREFIX + "'";

  private static final String HOW_A_TEMPLATE_READS_THE_ONE_DECLARATION =
      "theParameterItArrivesUnder()";

  @Test
  void no_template_of_this_build_spells_the_parameter_a_testers_value_arrives_under_itself() {
    List<Path> shipped = everyTemplateThisBuildShips();
    List<String> spellingItThemselves = new ArrayList<>();
    for (Path template : shipped) {
      String markup = read(template);
      if (markup.contains(A_TEMPLATE_SPELLING_THE_NAME_ITSELF)
          || markup.contains(A_TEMPLATE_SPELLING_THE_UNIT_NAME_ITSELF)) {
        spellingItThemselves.add(template.getFileName().toString());
      }
    }

    assertThat(shipped)
        .as("a sweep finding nothing because it read nothing would pass on an empty folder")
        .hasSizeGreaterThan(20);
    assertThat(spellingItThemselves)
        .as(
            "a control drawn under a name a template spelled itself is a name no port is known to"
                + " parse, and the value a tester typed into it would be dropped on the way to the"
                + " provider without a word")
        .isEmpty();
  }

  @Test
  void the_templates_drawing_a_declared_field_read_the_name_off_the_one_declaration() {
    List<String> readingIt = new ArrayList<>();
    for (Path template : everyTemplateThisBuildShips()) {
      if (read(template).contains(HOW_A_TEMPLATE_READS_THE_ONE_DECLARATION)) {
        readingIt.add(template.getFileName().toString());
      }
    }

    assertThat(readingIt)
        .as(
            "both ports a tester supplies values through and the account form beside them draw"
                + " their controls off the one declaration, so the sweep above is measuring"
                + " templates that name a declared field rather than templates that name none")
        .containsExactlyInAnyOrder("account.html", "origination.html", "reads.html");
  }

  private static List<Path> everyTemplateThisBuildShips() {
    try (Stream<Path> walked = Files.walk(WHERE_TEMPLATES_ARE_SHIPPED)) {
      return walked.filter(Files::isRegularFile).toList();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static String read(Path template) {
    try {
      return Files.readString(template);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
