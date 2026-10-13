package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OnlyAScreenWhoseSubjectIsTheTrailReadsTheReservedRunTest {

  private static final Path WHERE_THE_SCREENS_ARE =
      Path.of("src", "main", "java", "dev", "marcinromanowski", "pspsim", "ui");

  private static final String THE_RESOLUTION_THAT_ADMITS_THE_RESERVED_RUN =
      "ofAScreenWhoseSubjectIsTheTrail";

  private static final String WHERE_THAT_RESOLUTION_IS_DEFINED = "Selection.java";

  private static final List<String> WHAT_A_WRITE_IS_DECLARED_WITH =
      List.of("@PostMapping", "@PutMapping", "@DeleteMapping", "@PatchMapping");

  private static final String WHAT_A_ROW_IS_WRITTEN_THROUGH = "ActionTrail";

  private static final List<String> THE_SOURCES_THAT_MAY_RESOLVE_IT_THAT_WAY =
      List.of("AuditController.java", "CorrelationController.java");

  private static final String A_SOURCE_THAT_RESOLVES_A_RUN_AND_ROUTES_NOTHING =
      "ContributedPage.java";

  private static final String WHERE_THAT_SOURCES_ROUTES_AND_WRITES_ARE_DECLARED =
      "ObjectController.java";

  private static final String A_SOURCE_THAT_WRITES_A_ROW_BEHIND_A_READ = "ReadPage.java";

  @Test
  void the_sources_that_resolve_a_run_the_way_the_trail_screens_do_are_the_ones_named_here() {
    Map<String, String> swept = everySourceOfEveryScreen();

    assertThat(theSourcesResolvingTheReservedRun(swept))
        .as(
            "the trail of the reserved namespace is the whole subject of these screens and of"
                + " nothing else, so this is an equality and not a floor: a source reaching that"
                + " resolution without being named here is a screen admitted to the namespace"
                + " without anybody deciding it may be, whichever file its routes are declared in."
                + " %s sources were read.",
            swept.size())
        .containsExactlyElementsOf(THE_SOURCES_THAT_MAY_RESOLVE_IT_THAT_WAY);
  }

  @Test
  void nothing_that_writes_can_reach_the_resolution_that_admits_the_reserved_namespace() {
    Map<String, String> swept = everySourceOfEveryScreen();
    Map<String, List<String>> writingAndReachingIt = new LinkedHashMap<>();
    for (String resolving : theSourcesResolvingTheReservedRun(swept)) {
      List<String> writing = theWritersAmong(reachableFrom(resolving, swept), swept);
      if (!writing.isEmpty()) {
        writingAndReachingIt.put(resolving, writing);
      }
    }

    assertThat(theWritersAmong(swept.keySet(), swept))
        .as(
            "a sweep that finds no screen writing anything is a broken sweep, not a simulator"
                + " whose screens only read")
        .isNotEmpty();
    assertThat(writingAndReachingIt)
        .as(
            "a write aimed at that namespace is refused by the store with a failure and not by the"
                + " screen with a refusal a tester can read, so nothing that writes may reach the"
                + " resolution admitting it — neither in its own source nor in one that uses it")
        .isEmpty();
  }

  @Test
  void a_write_declared_in_another_source_than_the_one_resolving_the_run_is_still_seen() {
    Map<String, String> swept = everySourceOfEveryScreen();

    assertThat(theWritersAmong(List.of(A_SOURCE_THAT_RESOLVES_A_RUN_AND_ROUTES_NOTHING), swept))
        .as(
            "%s resolves a run and declares no route and no write of its own, so a sweep reading"
                + " one file at a time measures it as a screen that writes nothing",
            A_SOURCE_THAT_RESOLVES_A_RUN_AND_ROUTES_NOTHING)
        .isEmpty();
    assertThat(reachableFrom(A_SOURCE_THAT_RESOLVES_A_RUN_AND_ROUTES_NOTHING, swept))
        .as("the sweep reaches the source that declares that screen's routes")
        .contains(WHERE_THAT_SOURCES_ROUTES_AND_WRITES_ARE_DECLARED);
    assertThat(
            theWritersAmong(
                reachableFrom(A_SOURCE_THAT_RESOLVES_A_RUN_AND_ROUTES_NOTHING, swept), swept))
        .as(
            "so the measure this sweep applies is the resolution together with everything that"
                + " uses it, and that measure sees the four writes declared in %s",
            WHERE_THAT_SOURCES_ROUTES_AND_WRITES_ARE_DECLARED)
        .contains(WHERE_THAT_SOURCES_ROUTES_AND_WRITES_ARE_DECLARED);
  }

  @Test
  void a_row_written_behind_a_read_mapping_counts_as_a_write_here() {
    Map<String, String> swept = everySourceOfEveryScreen();

    assertThat(swept.get(A_SOURCE_THAT_WRITES_A_ROW_BEHIND_A_READ))
        .as(
            "%s answers a tester under read mappings alone and records a trail row under the run"
                + " it was given, so a sweep counting only the mappings a write is declared with"
                + " would measure it as a screen that writes nothing",
            A_SOURCE_THAT_WRITES_A_ROW_BEHIND_A_READ)
        .doesNotContain(WHAT_A_WRITE_IS_DECLARED_WITH.toArray(new String[0]));
    assertThat(theWritersAmong(List.of(A_SOURCE_THAT_WRITES_A_ROW_BEHIND_A_READ), swept))
        .as("and the measure this sweep applies counts the row it writes")
        .containsExactly(A_SOURCE_THAT_WRITES_A_ROW_BEHIND_A_READ);
  }

  @Test
  void the_screens_the_panel_hands_a_tester_on_to_are_the_ones_that_may_read_the_namespace() {
    Map<String, String> swept = everySourceOfEveryScreen();
    List<String> resolving = theSourcesResolvingTheReservedRun(swept);

    assertThat(TheReservedRunOnAScreen.Reader.values())
        .as("the panel offers one reader per screen that may read the namespace")
        .hasSameSizeAs(resolving);
    for (TheReservedRunOnAScreen.Reader reader : TheReservedRunOnAScreen.Reader.values()) {
      assertThat(resolving)
          .as(
              "the panel sends a tester to %s, so one of the screens that may read the namespace"
                  + " answers that address",
              reader.path())
          .anyMatch(source -> swept.get(source).contains("\"" + reader.path() + "\""));
    }
  }

  private static List<String> theSourcesResolvingTheReservedRun(Map<String, String> swept) {
    List<String> resolving = new ArrayList<>();
    swept.forEach(
        (name, java) -> {
          if (java.contains(THE_RESOLUTION_THAT_ADMITS_THE_RESERVED_RUN)
              && !name.equals(WHERE_THAT_RESOLUTION_IS_DEFINED)) {
            resolving.add(name);
          }
        });
    return List.copyOf(resolving);
  }

  private static List<String> theWritersAmong(Iterable<String> sources, Map<String, String> swept) {
    List<String> writing = new ArrayList<>();
    for (String source : sources) {
      String java = swept.get(source);
      if (WHAT_A_WRITE_IS_DECLARED_WITH.stream().anyMatch(java::contains)
          || java.contains(WHAT_A_ROW_IS_WRITTEN_THROUGH)) {
        writing.add(source);
      }
    }
    return List.copyOf(writing);
  }

  private static List<String> reachableFrom(String source, Map<String, String> swept) {
    TreeSet<String> reached = new TreeSet<>();
    List<String> toRead = new ArrayList<>(List.of(source));
    while (!toRead.isEmpty()) {
      String reading = toRead.remove(toRead.size() - 1);
      if (!reached.add(reading)) {
        continue;
      }
      String named = reading.substring(0, reading.length() - ".java".length());
      swept.forEach(
          (name, java) -> {
            if (!reached.contains(name) && mentions(java, named)) {
              toRead.add(name);
            }
          });
    }
    return List.copyOf(reached);
  }

  private static boolean mentions(String java, String className) {
    int at = java.indexOf(className);
    while (at >= 0) {
      boolean openedBefore = at > 0 && isPartOfAName(java.charAt(at - 1));
      int after = at + className.length();
      boolean continuesAfter = after < java.length() && isPartOfAName(java.charAt(after));
      if (!openedBefore && !continuesAfter) {
        return true;
      }
      at = java.indexOf(className, at + 1);
    }
    return false;
  }

  private static boolean isPartOfAName(char character) {
    return Character.isJavaIdentifierPart(character);
  }

  private static Map<String, String> everySourceOfEveryScreen() {
    Map<String, String> swept = new LinkedHashMap<>();
    try (Stream<Path> walked = Files.walk(WHERE_THE_SCREENS_ARE)) {
      walked
          .filter(Files::isRegularFile)
          .filter(source -> source.getFileName().toString().endsWith(".java"))
          .sorted()
          .forEach(source -> swept.put(source.getFileName().toString(), read(source)));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
    assertThat(swept)
        .as("a sweep of the screens of this build that read none of them measured nothing")
        .hasSizeGreaterThan(THE_SOURCES_THAT_MAY_RESOLVE_IT_THAT_WAY.size());
    return Collections.unmodifiableMap(swept);
  }

  private static String read(Path source) {
    try {
      return Files.readString(source);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
