package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class StylesheetTest {

  private static final String THE_STYLESHEET = "classpath:static/ui.css";

  private static final String THE_CONTRIBUTED_PAGES = "classpath*:templates/extensions/*/*.html";

  private static final List<String> THE_PAGES =
      List.of("classpath*:templates/*.html", "classpath*:static/**/*.html");

  private static final Map<String, String> THE_ONE_CLASS_EACH_SCRIPT_THE_PAGES_LOAD_WRITES =
      Map.of(
          "classpath*:META-INF/resources/webjars/htmx.org/*/dist/htmx.min.js",
          "htmx-request",
          "classpath*:META-INF/resources/webjars/swagger-ui/*/swagger-ui-standalone-preset.js",
          "dark-mode");

  private static final Pattern A_CLASS_ATTRIBUTE = Pattern.compile("\\bclass=\"([^\"]*)\"");

  private static final Pattern A_CLASS_THYMELEAF_ADDS =
      Pattern.compile("\\bth:(?:classappend|class)=\"([^\"]*)\"");

  private static final Pattern A_QUOTED_NAME_IN_AN_EXPRESSION = Pattern.compile("'([\\w-]+)'");

  private static final Pattern A_SELECTOR_LIST = Pattern.compile("([^{}@;]+)\\{");

  private static final Pattern A_CLASS = Pattern.compile("\\.([A-Za-z][\\w-]*)");

  private static final Pattern AN_ELEMENT_WITH_A_CLASS =
      Pattern.compile("^([a-z][a-z0-9]*)\\.([A-Za-z][\\w-]*)$");

  private final PathMatchingResourcePatternResolver resources =
      new PathMatchingResourcePatternResolver();

  @Test
  void every_class_the_stylesheet_styles_is_written_by_a_page_or_the_script_the_pages_load()
      throws IOException {
    Set<String> written = everyClassAPageOrItsScriptWrites();
    Set<String> styledButNeverWritten = new TreeSet<>();
    for (String selectors : selectorListsOf(read(resources.getResource(THE_STYLESHEET)))) {
      Matcher styled = A_CLASS.matcher(selectors);
      while (styled.find()) {
        if (!written.contains(styled.group(1))) {
          styledButNeverWritten.add(styled.group(1));
        }
      }
    }

    assertThat(styledButNeverWritten)
        .as(
            "a class ui.css styles that no template, static page, script they load or status tone"
                + " writes is dead weight a"
                + " reader has to rule out before changing anything near it")
        .isEmpty();
  }

  @Test
  void no_selector_list_names_a_class_both_bare_and_narrowed_to_an_element() throws IOException {
    List<String> redundant = new ArrayList<>();
    for (String selectors : selectorListsOf(read(resources.getResource(THE_STYLESHEET)))) {
      List<String> each = List.of(selectors.split(","));
      for (String selector : each) {
        Matcher narrowed = AN_ELEMENT_WITH_A_CLASS.matcher(selector.strip());
        if (narrowed.matches()
            && each.stream().anyMatch(other -> other.strip().equals("." + narrowed.group(2)))) {
          redundant.add(selector.strip() + " beside ." + narrowed.group(2));
        }
      }
    }

    assertThat(redundant)
        .as(
            "a bare class in a selector list already reaches every element of that class, so the"
                + " same class narrowed to one element in the same list says nothing more")
        .isEmpty();
  }

  @Test
  void every_class_a_contributed_page_writes_is_one_the_stylesheet_already_styles()
      throws IOException {
    Set<String> styled = new TreeSet<>();
    for (String selectors : selectorListsOf(read(resources.getResource(THE_STYLESHEET)))) {
      Matcher named = A_CLASS.matcher(selectors);
      while (named.find()) {
        styled.add(named.group(1));
      }
    }
    for (StatusBadge.Tone tone : StatusBadge.Tone.values()) {
      styled.add(tone.cssClass());
    }
    Map<String, Set<String>> invented = new TreeMap<>();
    for (Resource page : found(THE_CONTRIBUTED_PAGES)) {
      Set<String> written = new TreeSet<>(everyClassWrittenBy(read(page)));
      written.removeAll(styled);
      if (!written.isEmpty()) {
        invented.put(String.valueOf(page.getFilename()), written);
      }
    }

    assertThat(invented)
        .as(
            "a page contributed under templates/extensions is drawn with the vocabulary this"
                + " stylesheet already has, because a class it invents for itself reaches the"
                + " browser unstyled and the tester reading it cannot tell a screen that is plain"
                + " from one that failed to load")
        .isEmpty();
  }

  private Set<String> everyClassAPageOrItsScriptWrites() throws IOException {
    Set<String> written = new TreeSet<>();
    for (String where : THE_PAGES) {
      for (Resource page : found(where)) {
        written.addAll(everyClassWrittenBy(read(page)));
      }
    }
    for (Map.Entry<String, String> script :
        THE_ONE_CLASS_EACH_SCRIPT_THE_PAGES_LOAD_WRITES.entrySet()) {
      for (Resource loaded : found(script.getKey())) {
        assertThat(read(loaded))
            .as(script.getKey() + " writes the class " + script.getValue())
            .contains("\"" + script.getValue() + "\"");
      }
      written.add(script.getValue());
    }
    for (StatusBadge.Tone tone : StatusBadge.Tone.values()) {
      written.add(tone.cssClass());
    }
    return written;
  }

  private static Set<String> everyClassWrittenBy(String markup) {
    Set<String> written = new TreeSet<>();
    Matcher attribute = A_CLASS_ATTRIBUTE.matcher(markup);
    while (attribute.find()) {
      written.addAll(List.of(attribute.group(1).strip().split("\\s+")));
    }
    Matcher added = A_CLASS_THYMELEAF_ADDS.matcher(markup);
    while (added.find()) {
      written.addAll(everyMatch(A_QUOTED_NAME_IN_AN_EXPRESSION, added.group(1)));
    }
    return written;
  }

  private Resource[] found(String where) throws IOException {
    Resource[] found = resources.getResources(where);
    assertThat(found).as("nothing on the classpath matches " + where).isNotEmpty();
    return found;
  }

  private static List<String> everyMatch(Pattern pattern, String text) {
    List<String> matched = new ArrayList<>();
    Matcher each = pattern.matcher(text);
    while (each.find()) {
      matched.add(each.group(1));
    }
    return matched;
  }

  private static List<String> selectorListsOf(String stylesheet) {
    List<String> lists = new ArrayList<>();
    Matcher list = A_SELECTOR_LIST.matcher(stylesheet);
    while (list.find()) {
      lists.add(list.group(1).strip());
    }
    return lists;
  }

  private static String read(Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
