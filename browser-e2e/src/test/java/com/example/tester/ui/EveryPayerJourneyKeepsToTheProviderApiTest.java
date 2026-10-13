package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EveryPayerJourneyKeepsToTheProviderApiTest {

  private static final Path THE_JOURNEYS = Path.of("src/test/java/com/example/tester/ui");

  private static final String THE_BASE = "AJourneyInAMobileAppsWebview";

  private static final Pattern EXTENDS = Pattern.compile("\\bclass (\\w+) extends (\\w+)");

  private static final List<String> A_SIDE_DOOR =
      List.of(
          "APIRequestContext",
          ".request()",
          "HttpClient",
          "HttpURLConnection",
          "URLConnection",
          "openStream",
          "Socket",
          "TheTestSetup",
          "new TheProviderApi",
          "TheBrowser.opened",
          "Playwright.create");

  private static final String THE_SETUP = "setup()";

  private static final String ONLY_IN_SETUP = "@BeforeEach";

  @Test
  @DisplayName("every payer journey the rule covers is found, so the rule is never vacuous")
  void everyPayerJourneyIsFound() {
    assertThat(thePayerJourneys().keySet())
        .contains(
            "APayerFinishesGrowsHostedPageTest",
            "APayerFinishesWorldlinesHostedTokenizationPageTest",
            "APayerSavesACardOnAutrasHostedPageTest",
            "APayerSavesACardInAzericardsWebviewTest",
            "APayerHoldsFundsInAzericardsWebviewTest",
            "AnAzericardWebviewJourney");
  }

  @Test
  @DisplayName(
      "no payer journey opens a way past the guard: no request context, no HTTP client of its"
          + " own and no test setup of its own")
  void noPayerJourneyOpensASideDoor() {
    List<String> doors = new ArrayList<>();
    thePayerJourneys()
        .forEach(
            (journey, lines) -> {
              for (int at = 0; at < lines.size(); at++) {
                for (String door : A_SIDE_DOOR) {
                  if (lines.get(at).contains(door)) {
                    doors.add(journey + ":" + (at + 1) + " uses " + door);
                  }
                }
              }
            });
    assertThat(doors).isEmpty();
  }

  @Test
  @DisplayName("a payer journey reaches the test setup only in a method that runs before the test")
  void aPayerJourneyReachesTheSetupOnlyBeforeTheTest() {
    List<String> outsideSetup = new ArrayList<>();
    thePayerJourneys()
        .forEach(
            (journey, lines) -> {
              for (int at = 0; at < lines.size(); at++) {
                if (lines.get(at).contains(THE_SETUP)
                    && !theEnclosingMemberIsAnnotated(lines, at, ONLY_IN_SETUP)) {
                  outsideSetup.add(journey + ":" + (at + 1));
                }
              }
            });
    assertThat(outsideSetup).isEmpty();
  }

  @Test
  @DisplayName("the setup rule tells a method that runs before the test from a test body")
  void theSetupRuleTellsABeforeEachFromATestBody() {
    List<String> source =
        List.of(
            "class A extends " + THE_BASE + " {",
            "  @BeforeEach",
            "  void ready() {",
            "    " + THE_SETUP + ".configure(",
            "        1);",
            "  }",
            "",
            "  @Test",
            "  @DisplayName(",
            "      \"a test\")",
            "  void aTest() {",
            "    " + THE_SETUP + ".configure(2);",
            "  }",
            "}");
    assertThat(theEnclosingMemberIsAnnotated(source, 3, ONLY_IN_SETUP)).isTrue();
    assertThat(theEnclosingMemberIsAnnotated(source, 11, ONLY_IN_SETUP)).isFalse();
  }

  static boolean theEnclosingMemberIsAnnotated(List<String> lines, int at, String annotation) {
    int member = at;
    while (member >= 0 && !aMemberStartsAt(lines.get(member))) {
      member--;
    }
    for (int above = member - 1; above >= 0; above--) {
      String line = lines.get(above);
      if (indentOf(line) > 2 || line.isBlank()) {
        continue;
      }
      if (!line.trim().startsWith("@")) {
        return false;
      }
      if (line.trim().equals(annotation)) {
        return true;
      }
    }
    return false;
  }

  private static boolean aMemberStartsAt(String line) {
    return indentOf(line) == 2 && !line.trim().startsWith("@") && !line.trim().startsWith("}");
  }

  private static int indentOf(String line) {
    int indent = 0;
    while (indent < line.length() && line.charAt(indent) == ' ') {
      indent++;
    }
    return indent;
  }

  private static Map<String, List<String>> thePayerJourneys() {
    Map<String, List<String>> sources = new HashMap<>();
    Map<String, String> parents = new HashMap<>();
    try (Stream<Path> files = Files.list(THE_JOURNEYS)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        List<String> lines = Files.readAllLines(file);
        Matcher extension = EXTENDS.matcher(String.join("\n", lines));
        if (extension.find()) {
          sources.put(extension.group(1), lines);
          parents.put(extension.group(1), extension.group(2));
        }
      }
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
    Map<String, List<String>> journeys = new TreeMap<>();
    for (String journey : sources.keySet()) {
      if (descendsFrom(journey, parents, Set.of(THE_BASE))) {
        journeys.put(journey, sources.get(journey));
      }
    }
    return journeys;
  }

  private static boolean descendsFrom(
      String journey, Map<String, String> parents, Set<String> ancestors) {
    String parent = parents.get(journey);
    while (parent != null) {
      if (ancestors.contains(parent)) {
        return true;
      }
      parent = parents.get(parent);
    }
    return false;
  }
}
