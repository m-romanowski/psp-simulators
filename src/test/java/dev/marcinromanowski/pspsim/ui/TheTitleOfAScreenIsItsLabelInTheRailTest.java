package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class TheTitleOfAScreenIsItsLabelInTheRailTest {

  private static final String EVERY_CONTRIBUTED_PAGE = "classpath*:templates/extensions/*/*.html";

  private static final String THE_PAGE_OF_A_SCREEN = "classpath:templates/";

  private static final Pattern THE_TITLE_IT_PASSES_THE_SHELL =
      Pattern.compile("fragments\\s*::\\s*head\\('([^']*)'\\)");

  private static final Pattern THE_TAB_IT_ASKS_FOR =
      Pattern.compile("<meta\\s+name=\"tab\"\\s+content=\"([^\"]*)\"");

  private static final String THE_PUBLISHED_API_IS_NOT_A_PAGE_OF_THIS_BUILD = "";

  private final PathMatchingResourcePatternResolver resources =
      new PathMatchingResourcePatternResolver();

  @Test
  void every_screen_the_rail_offers_titles_itself_with_the_label_the_rail_reads()
      throws IOException {
    Map<String, String> theTitleEachLabelOpens = new TreeMap<>();
    Map<String, String> readingSomethingElse = new TreeMap<>();
    for (RailDestination destination : TheRail.theScreensThisSimulatorCarries()) {
      if (THE_PUBLISHED_API_IS_NOT_A_PAGE_OF_THIS_BUILD.equals(destination.screen())) {
        continue;
      }
      String title = theTitleOf(read(destination.screen() + ".html"));
      theTitleEachLabelOpens.put(destination.label(), title);
      if (!destination.label().equals(title)) {
        readingSomethingElse.put(destination.label(), title);
      }
    }

    assertThat(theTitleEachLabelOpens)
        .as("the rail offers no screen with a page of its own, so this check read no title")
        .hasSize(19);
    assertThat(readingSomethingElse)
        .as(
            "a tester with no technical knowledge reaches a screen by the label the rail reads and"
                + " knows they arrived by the title the browser tab reads, so the two are the same"
                + " words in the same order; a screen titled anything else is a second name for"
                + " one place")
        .isEmpty();
    assertThat(theTitleEachLabelOpens)
        .containsEntry("Compare scenarios", "Compare scenarios")
        .containsEntry("Payments", "Payments");
  }

  @Test
  void every_contributed_screen_titles_itself_with_the_tab_it_asks_the_rail_for()
      throws IOException {
    Map<String, String> readingSomethingElse = new TreeMap<>();
    Resource[] contributed = resources.getResources(EVERY_CONTRIBUTED_PAGE);
    for (Resource page : contributed) {
      String html = read(page);
      String tab = theOnlyGroup(THE_TAB_IT_ASKS_FOR, html, page.getFilename() + " asks for no tab");
      String title = theOnlyGroup(THE_TITLE_IT_PASSES_THE_SHELL, html, page.getFilename());
      if (!tab.equals(title)) {
        readingSomethingElse.put(tab, title);
      }
    }

    assertThat(contributed)
        .as("nothing on the classpath matches " + EVERY_CONTRIBUTED_PAGE + ", so nothing was read")
        .isNotEmpty();
    assertThat(readingSomethingElse)
        .as(
            "a screen a provider contributes is reached by the tab it declares, so it titles itself"
                + " with that tab and never with a second name for the same place")
        .isEmpty();
  }

  private String theTitleOf(String html) {
    return theOnlyGroup(THE_TITLE_IT_PASSES_THE_SHELL, html, "a page of the rail");
  }

  private static String theOnlyGroup(Pattern pattern, String html, String what) {
    Matcher found = pattern.matcher(html);
    assertThat(found.find()).as(what + " matches " + pattern.pattern()).isTrue();
    return found.group(1);
  }

  private String read(String page) throws IOException {
    return read(resources.getResource(THE_PAGE_OF_A_SCREEN + page));
  }

  private static String read(Resource page) throws IOException {
    try (InputStream bytes = page.getInputStream()) {
      return new String(bytes.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
