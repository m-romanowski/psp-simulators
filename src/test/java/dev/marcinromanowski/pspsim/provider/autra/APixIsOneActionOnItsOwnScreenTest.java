package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class APixIsOneActionOnItsOwnScreenTest {

  private static final Path WHERE_THE_PIX_SCREEN_IS_SHIPPED =
      Path.of("src", "main", "resources", "templates", "extensions", "autra", "pix.html");

  @Test
  void the_way_of_starting_a_pix_is_named_by_the_words_on_the_button_that_starts_one() {
    String label = AutraOrigination.DECLARED.wayNamed(AutraOrigination.A_PIX).orElseThrow().label();

    assertThat(thePixScreenThisBuildShips())
        .as(
            "a refusal of this form quotes the way of starting by this label, so a label the"
                + " screen does not carry hands a tester a refusal naming a button that is not"
                + " on the screen they are looking at")
        .contains("<button type=\"submit\">" + label + "</button>");
  }

  @Test
  void the_form_on_that_screen_posts_the_very_way_of_starting_whose_label_it_carries() {
    assertThat(thePixScreenThisBuildShips())
        .contains("name=\"startedAs\" value=\"" + AutraOrigination.A_PIX + "\"");
  }

  private static String thePixScreenThisBuildShips() {
    try {
      return Files.readString(WHERE_THE_PIX_SCREEN_IS_SHIPPED);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
