package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

class ContributedScreensTest {

  private static final String A_PIX_PAGE =
      """
      <!DOCTYPE html>
      <html xmlns:th="http://www.thymeleaf.org">
        <head>
          <meta name="tab" content="Pix" />
          <meta name="group" content="daily-work" />
          <meta name="order" content="15" />
        </head>
        <body></body>
      </html>
      """;

  @Test
  void a_contributed_page_says_what_its_tab_reads_where_it_sits_and_in_what_order() {
    List<ContributedScreen> read =
        ContributedScreens.readFrom(Map.of("autra/pix.html", A_PIX_PAGE));

    assertThat(read)
        .singleElement()
        .satisfies(
            screen -> {
              assertThat(screen.provider()).isEqualTo("autra");
              assertThat(screen.screen()).isEqualTo("pix");
              assertThat(screen.label()).isEqualTo("Pix");
              assertThat(screen.group()).isEqualTo(RailGroup.DAILY_WORK);
              assertThat(screen.order()).isEqualTo(15);
              assertThat(screen.path()).isEqualTo("/ui/x/autra/pix");
              assertThat(screen.view()).isEqualTo("extensions/autra/pix");
              assertThat(screen.asAScreenNamesItself()).isEqualTo("x/autra/pix");
              assertThat(screen.file())
                  .isEqualTo("src/main/resources/templates/extensions/autra/pix.html");
            });
  }

  @Test
  void the_tab_of_a_page_is_the_content_it_declares_and_never_a_word_off_its_file_name() {
    List<ContributedScreen> read =
        ContributedScreens.readFrom(
            Map.of(
                "autra/qr.html",
                theHeadOf(
                    "<meta name=\"tab\" content=\"Pix por QR\" />"
                        + "<meta name=\"group\" content=\"daily-work\" />"
                        + "<meta name=\"order\" content=\"16\" />")));

    assertThat(read)
        .singleElement()
        .satisfies(
            screen -> {
              assertThat(screen.screen()).isEqualTo("qr");
              assertThat(screen.label())
                  .as(
                      "the one page this build ships is pix.html declaring \"Pix\", which is also"
                          + " what its own file name capitalises to, so every other fixture here"
                          + " passes just as well on a label derived from the file name. What a"
                          + " screen is for is the contributor's to say and not this simulator's to"
                          + " guess, and this is the fixture that holds it to that")
                  .isEqualTo("Pix por QR");
            });
  }

  @Test
  void a_page_that_names_no_tab_is_refused_by_the_file_it_is_in() {
    assertThatThrownBy(() -> ContributedScreens.readFrom(Map.of("autra/pix.html", theHeadOf(""))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("extensions/autra/pix.html")
        .hasMessageContaining("<meta name=\"tab\"")
        .hasMessageContaining("derives nothing from the name of the file");
  }

  @Test
  void a_page_that_names_no_group_is_refused_and_told_which_groups_this_rail_has() {
    assertThatThrownBy(
            () ->
                ContributedScreens.readFrom(
                    Map.of("autra/pix.html", theHeadOf("<meta name=\"tab\" content=\"Pix\" />"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("extensions/autra/pix.html")
        .hasMessageContaining("<meta name=\"group\"")
        .hasMessageContaining("daily-work, set-up, evidence, take-it-elsewhere");
  }

  @Test
  void a_page_that_invents_a_group_of_its_own_is_refused_and_the_rail_stays_a_tester_s() {
    assertThatThrownBy(
            () ->
                ContributedScreens.readFrom(
                    Map.of(
                        "autra/pix.html",
                        theHeadOf(
                            "<meta name=\"tab\" content=\"Pix\" />"
                                + "<meta name=\"group\" content=\"brazil\" />"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("extensions/autra/pix.html")
        .hasMessageContaining("content=\"brazil\"")
        .hasMessageContaining("not a list of vendors")
        .hasMessageContaining("daily-work, set-up, evidence, take-it-elsewhere");
  }

  @Test
  void a_page_that_names_no_order_or_no_whole_number_is_refused() {
    assertThatThrownBy(
            () ->
                ContributedScreens.readFrom(
                    Map.of("autra/pix.html", theHeadOf(aTabInDailyWork("")))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("<meta name=\"order\"")
        .hasMessageContaining("whichever order the classpath happened to hand them over");

    assertThatThrownBy(
            () ->
                ContributedScreens.readFrom(
                    Map.of(
                        "autra/pix.html",
                        theHeadOf(aTabInDailyWork("<meta name=\"order\" content=\"soon\" />")))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("content=\"soon\"")
        .hasMessageContaining("no whole number");
  }

  @Test
  void a_page_whose_folder_or_name_no_route_could_carry_is_refused() {
    assertThatThrownBy(() -> ContributedScreens.readFrom(Map.of("autra/My Pix.html", A_PIX_PAGE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("autra/My Pix")
        .hasMessageContaining("lowercase letters, digits and hyphens");
  }

  @Test
  void the_pages_are_read_in_an_order_the_classpath_does_not_decide() {
    Map<String, String> handedOverBackwards = new LinkedHashMap<>();
    handedOverBackwards.put("zeta/second.html", A_PIX_PAGE);
    handedOverBackwards.put("zeta/first.html", A_PIX_PAGE);
    handedOverBackwards.put("autra/pix.html", A_PIX_PAGE);

    assertThat(ContributedScreens.readFrom(handedOverBackwards))
        .extracting(ContributedScreen::path)
        .containsExactly("/ui/x/autra/pix", "/ui/x/zeta/first", "/ui/x/zeta/second");
  }

  @Test
  void a_screen_is_served_from_the_page_that_is_there_and_from_nothing_else() {
    ContributedScreens screens =
        new ContributedScreens(ContributedScreens.readFrom(Map.of("autra/pix.html", A_PIX_PAGE)));

    assertThat(screens.serving("autra", "pix")).isPresent();
    assertThat(screens.serving("autra", "../../application")).isEmpty();
    assertThat(screens.serving("grow", "pix")).isEmpty();
    assertThat(screens.of("autra")).hasSize(1);
    assertThat(screens.of("grow")).isEmpty();
    assertThat(screens.theRefusalOfAScreenThisBuildDoesNotServe("autra", "qr"))
        .contains("src/main/resources/templates/extensions/autra/qr.html")
        .contains("/ui/x/autra/pix");
  }

  @Test
  void a_page_a_folder_off_is_refused_by_name_rather_than_served_by_nothing() {
    assertThatThrownBy(
            () -> ContributedScreens.readFrom(Map.of("grow/deep/nested.html", A_PIX_PAGE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("extensions/grow/deep/nested.html")
        .hasMessageContaining("is served by nothing")
        .hasMessageContaining("exactly one folder under extensions")
        .hasMessageContaining("extensions/<provider>/<screen>.html");

    assertThatThrownBy(() -> ContributedScreens.readFrom(Map.of("loose.html", A_PIX_PAGE)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("extensions/loose.html")
        .hasMessageContaining("is served by nothing");
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void a_page_that_reaches_a_bean_of_this_application_is_refused_and_told_what_it_reached() {
    assertThatThrownBy(
            () ->
                ContributedScreens.readFrom(
                    Map.of(
                        "grow/leak.html",
                        theHeadOf(aTabInDailyWork("<meta name=\"order\" content=\"15\" />"))
                            + "<p th:text=\"${@environment.getProperty('spring.datasource"
                            + ".password')}\"></p>")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("extensions/grow/leak.html")
        .hasMessageContaining("[environment]")
        .hasMessageContaining("database password");
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void every_page_this_build_ships_is_served_from_its_own_file_and_declares_its_own_tab()
      throws IOException {
    List<ContributedScreen> shipped = new ContributedScreens().all();

    assertThat(shipped)
        .as(
            "discovery reads whatever is under templates/extensions on the classpath of this build,"
                + " and a check over none of them proves nothing about any of them")
        .isNotEmpty();
    for (ContributedScreen screen : shipped) {
      String markup = Files.readString(Path.of(screen.file()));
      assertThat(markup)
          .as(
              screen.file()
                  + " is the file the route "
                  + screen.path()
                  + " serves, and the label of its tab is declared there and derived from nothing")
          .contains("content=\"" + screen.label() + "\"");
      assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches(markup)).isEmpty();
      assertThat(screen.path()).isEqualTo("/ui/x/" + screen.provider() + "/" + screen.screen());
      assertThat(screen.view())
          .isEqualTo("extensions/" + screen.provider() + "/" + screen.screen());
    }
  }

  private static String aTabInDailyWork(String more) {
    return "<meta name=\"tab\" content=\"Pix\" /><meta name=\"group\" content=\"daily-work\" />"
        + more;
  }

  private static String theHeadOf(String metas) {
    return "<html><head>" + metas + "</head><body></body></html>";
  }
}
