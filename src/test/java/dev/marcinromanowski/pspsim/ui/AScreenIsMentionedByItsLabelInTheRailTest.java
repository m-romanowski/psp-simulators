package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AScreenIsMentionedByItsLabelInTheRailTest {

  record Mention(String file, int line, String written, String labelItShouldRead) {}

  private record Found(int from, int to, Mention mention) {}

  private record AsOneSentence(String words, List<Integer> whereEachOfThemWas) {}

  private static final Path EVERY_PAGE = Path.of("src", "main", "resources", "templates");

  private static final Path THIS_REPOSITORY = Path.of("");

  private static final String WHAT_EVERY_MODULE_KEEPS_ITS_SOURCES_IN = "src";

  private static final List<String> WHAT_GIT_IGNORES_OR_THIS_BUILD_GENERATES =
      List.of(
          ".git",
          ".claude",
          ".tmp",
          ".idea",
          ".vscode",
          ".settings",
          ".gradle",
          "build",
          "bin",
          "out");

  private static final List<String> THE_KINDS_OF_SOURCE_THIS_SWEEP_READS =
      List.of(".java", ".kt", ".kts", ".html");

  private static final int THE_FEWEST_TREES_A_WORKING_DERIVATION_FINDS = 5;

  private static final int THE_WRONG_CASED_FIXTURES_THIS_SWEEP_IS_WRITTEN_AROUND = 6;

  private static final Path WHERE_THIS_SWEEPS_OWN_WRONG_CASED_FIXTURES_ARE_WRITTEN =
      Path.of(
          "src",
          "test",
          "java",
          "dev",
          "marcinromanowski",
          "pspsim",
          "ui",
          "AScreenIsMentionedByItsLabelInTheRailTest.java");

  private static final String THE_WORD_THAT_MAKES_IT_A_PLACE = " screen";

  private static final String THE_FOLDER_A_CONTRIBUTED_PAGE_SITS_UNDER = "extensions";

  private static final Pattern THE_TAB_A_CONTRIBUTED_PAGE_ASKS_FOR =
      Pattern.compile("<meta\\s+name=\"tab\"\\s+content=\"([^\"]*)\"");

  private static final int THE_FEWEST_MENTIONS_A_WORKING_SWEEP_FINDS = 50;

  private static final Pattern WHAT_SEPARATES_TWO_WORDS_OF_ONE_SENTENCE =
      Pattern.compile("[\\s\"+]");

  private static final Comparator<Found> THE_LONGEST_NAME_IT_COULD_BE =
      Comparator.<Found>comparingInt(found -> found.to() - found.from())
          .reversed()
          .thenComparingInt(Found::from);

  @Test
  void every_mention_of_a_screen_reads_the_label_the_rail_reads() {
    Map<String, List<Path>> byTheTreeItIsIn = everySourceByTheTreeItIsIn();
    List<Path> swept = new ArrayList<>();
    byTheTreeItIsIn.values().forEach(swept::addAll);
    List<Mention> mentioned = everyMentionIn(swept);
    List<Mention> readingSomethingElse = new ArrayList<>();
    List<Mention> theFixturesThisSweepPassesOver = new ArrayList<>();
    for (Mention mention : mentioned) {
      if (!mention.written().equals(mention.labelItShouldRead())) {
        if (mention
            .file()
            .equals(WHERE_THIS_SWEEPS_OWN_WRONG_CASED_FIXTURES_ARE_WRITTEN.toString())) {
          theFixturesThisSweepPassesOver.add(mention);
        } else {
          readingSomethingElse.add(mention);
        }
      }
    }

    assertThat(swept)
        .as("this sweep read no source of this build at all, so it measured nothing")
        .isNotEmpty();
    assertThat(byTheTreeItIsIn)
        .as(
            "a tree found here that contributes no source is a sweep that read nothing where it"
                + " claims to read something, and it reports the same zero either way")
        .allSatisfy((tree, sources) -> assertThat(sources).as(tree).isNotEmpty());
    assertThat(byTheTreeItIsIn)
        .as(
            "the trees are not listed here but found by walking this repository for directories"
                + " named \"%s\", so a module added later is swept without this file being"
                + " touched; finding fewer than the modules this build has means the walk stopped"
                + " rather than the repository shrank, and a module removed on purpose is this"
                + " floor lowered on purpose",
            WHAT_EVERY_MODULE_KEEPS_ITS_SOURCES_IN)
        .hasSizeGreaterThanOrEqualTo(THE_FEWEST_TREES_A_WORKING_DERIVATION_FINDS);
    assertThat(swept)
        .as(
            "the one file this sweep passes over is passed over by name, so a rename has to be"
                + " read here rather than quietly widening the sweep's blind spot")
        .contains(WHERE_THIS_SWEEPS_OWN_WRONG_CASED_FIXTURES_ARE_WRITTEN);
    assertThat(theFixturesThisSweepPassesOver)
        .as(
            "this sweep passes over its own source because the wrong-cased names it asserts on"
                + " are written there, and over exactly as many of them as it is written around;"
                + " one more is a mention nothing reads, in the one file this sweep cannot look"
                + " in, and one fewer is a fixture gone and an exemption with nothing left to"
                + " exempt")
        .hasSize(THE_WRONG_CASED_FIXTURES_THIS_SWEEP_IS_WRITTEN_AROUND);
    assertThat(mentioned)
        .as(
            "a sweep of "
                + swept.size()
                + " sources that finds almost no screen named at all has stopped reading rather"
                + " than found a repository that names none")
        .hasSizeGreaterThan(THE_FEWEST_MENTIONS_A_WORKING_SWEEP_FINDS);
    assertThat(mentioned).extracting(Mention::file).anyMatch(file -> file.endsWith(".html"));
    assertThat(mentioned).extracting(Mention::file).anyMatch(file -> file.endsWith(".java"));
    assertThat(readingSomethingElse)
        .as(
            "a tester with no technical knowledge reaches a screen by the label the rail reads, so"
                + " words that send them to one name it in that label and in its case; a screen"
                + " called anything else is a second place as far as that tester can tell. Each"
                + " finding below is what the words wrote where the rail reads the label beside"
                + " it.")
        .isEmpty();
  }

  @Test
  void a_mention_is_read_across_the_lines_and_the_pieces_a_java_string_is_written_in() {
    assertThat(
            theMentionsIn(
                "Screen.java",
                """
                "the fixture is on the payment methods"
                    + " screen, where it is removed";
                """))
        .extracting(Mention::written, Mention::labelItShouldRead, Mention::line)
        .containsExactly(tuple("payment methods", "Payment methods", 1));
  }

  @Test
  void a_mention_that_already_reads_the_label_is_no_finding() {
    assertThat(theMentionsIn("page.html", "go to the Payment methods screen for it"))
        .extracting(Mention::written, Mention::labelItShouldRead)
        .containsExactly(tuple("Payment methods", "Payment methods"));
  }

  @Test
  void a_label_mentioned_without_the_word_screen_is_not_a_mention_of_a_place() {
    assertThat(theMentionsIn("page.html", "the payment methods this run holds")).isEmpty();
    assertThat(theMentionsIn("page.html", "a dashboard, not a landing page")).isEmpty();
    assertThat(theMentionsIn("page.html", "the audit trail of this run")).isEmpty();
  }

  @Test
  void a_screen_called_the_name_the_code_knows_it_by_is_a_finding_of_its_own() {
    assertThat(theMentionsIn("page.html", "Copy the snippet on the Pairing screen"))
        .extracting(Mention::written, Mention::labelItShouldRead)
        .containsExactly(tuple("Pairing", "Connect my app"));
    assertThat(theMentionsIn("page.html", "the objects screen lists them"))
        .extracting(Mention::written, Mention::labelItShouldRead)
        .containsExactly(tuple("objects", "Payments"));
  }

  @Test
  void a_longer_label_is_the_one_mentioned_and_not_the_shorter_one_inside_it() {
    assertThat(theMentionsIn("page.html", "write two rules on the Compare scenarios screen"))
        .extracting(Mention::written, Mention::labelItShouldRead)
        .containsExactly(tuple("Compare scenarios", "Compare scenarios"));
  }

  @Test
  void every_mention_carries_the_file_and_the_line_it_was_written_on() {
    assertThat(theMentionsIn("page.html", "one\ntwo\nopen the audit screen\n"))
        .extracting(Mention::file, Mention::line, Mention::written)
        .containsExactly(tuple("page.html", 3, "audit"));
  }

  @Test
  void a_screen_a_provider_contributed_is_named_by_the_tab_it_asks_the_rail_for() {
    assertThat(
            everyFileUnder(EVERY_PAGE.resolve(THE_FOLDER_A_CONTRIBUTED_PAGE_SITS_UNDER), ".html"))
        .as("no contributed page was read, so no contributed tab is in this sweep at all")
        .isNotEmpty();
    assertThat(theScreensThisBuildOffers())
        .containsEntry("Pix", "Pix")
        .containsEntry("Read back", "Read back")
        .containsEntry("read-back", "Read back");
    assertThat(theMentionsIn("page.html", "the read-back screen prints the slugs"))
        .extracting(Mention::written, Mention::labelItShouldRead)
        .containsExactly(tuple("read-back", "Read back"));
    assertThat(theMentionsIn("page.html", "the pix screen hands one out"))
        .extracting(Mention::written, Mention::labelItShouldRead)
        .containsExactly(tuple("pix", "Pix"));
  }

  static List<Mention> theMentionsIn(String file, String source) {
    return theMentionsIn(file, source, theScreensThisBuildOffers());
  }

  static List<Mention> theMentionsIn(String file, String source, Map<String, String> byName) {
    AsOneSentence read = asOneSentence(source);
    List<Found> found = new ArrayList<>();
    byName.forEach((name, label) -> findEach(file, source, read, name, label, found));
    return theLongestNameAtEachPlace(found);
  }

  static Map<String, String> theScreensThisBuildOffers() {
    Map<String, String> byName = new LinkedHashMap<>();
    for (RailDestination destination : TheRail.theScreensThisSimulatorCarries()) {
      for (String name : theNamesItAnswersTo(destination.label(), destination.screen())) {
        byName.put(name, destination.label());
      }
    }
    for (Path page :
        everyFileUnder(EVERY_PAGE.resolve(THE_FOLDER_A_CONTRIBUTED_PAGE_SITS_UNDER), ".html")) {
      String label = theTabItAsksFor(page);
      String theNameTheCodeKnowsItBy = withoutItsSuffix(page.getFileName().toString());
      for (String name : theNamesItAnswersTo(label, theNameTheCodeKnowsItBy)) {
        byName.put(name, label);
      }
    }
    return Map.copyOf(byName);
  }

  private static String theTabItAsksFor(Path page) {
    Matcher declared = THE_TAB_A_CONTRIBUTED_PAGE_ASKS_FOR.matcher(read(page));
    if (!declared.find()) {
      throw new IllegalStateException(
          page
              + " is a contributed screen that declares no tab, so this sweep cannot know the name"
              + " a tester reaches it by");
    }
    return declared.group(1);
  }

  private static String withoutItsSuffix(String fileName) {
    return fileName.substring(0, fileName.lastIndexOf('.'));
  }

  private static List<Mention> theLongestNameAtEachPlace(List<Found> found) {
    List<Found> longestFirst = new ArrayList<>(found);
    longestFirst.sort(THE_LONGEST_NAME_IT_COULD_BE);
    List<Found> kept = new ArrayList<>();
    for (Found one : longestFirst) {
      if (!itOverlapsOneAlreadyKept(one, kept)) {
        kept.add(one);
      }
    }
    kept.sort(Comparator.comparingInt(Found::from));
    List<Mention> mentioned = new ArrayList<>();
    for (Found one : kept) {
      mentioned.add(one.mention());
    }
    return List.copyOf(mentioned);
  }

  private static boolean itOverlapsOneAlreadyKept(Found one, List<Found> kept) {
    for (Found already : kept) {
      if (one.from() < already.to() && already.from() < one.to()) {
        return true;
      }
    }
    return false;
  }

  private static List<String> theNamesItAnswersTo(String label, String theNameTheCodeKnowsItBy) {
    List<String> names = new ArrayList<>();
    names.add(label);
    if (!theNameTheCodeKnowsItBy.isEmpty()
        && !theNameTheCodeKnowsItBy.equals(asASentenceReadsIt(label))) {
      names.add(theNameTheCodeKnowsItBy);
    }
    return names;
  }

  private static String asASentenceReadsIt(String label) {
    return label.toLowerCase(Locale.ROOT);
  }

  private static void findEach(
      String file,
      String source,
      AsOneSentence read,
      String name,
      String label,
      List<Found> found) {
    Matcher mentioned =
        Pattern.compile(
                Pattern.quote(name) + Pattern.quote(THE_WORD_THAT_MAKES_IT_A_PLACE),
                Pattern.CASE_INSENSITIVE)
            .matcher(read.words());
    while (mentioned.find()) {
      int at = read.whereEachOfThemWas().get(mentioned.start());
      found.add(
          new Found(
              mentioned.start(),
              mentioned.end(),
              new Mention(
                  file,
                  whichLineItIsOn(source, at),
                  read.words().substring(mentioned.start(), mentioned.start() + name.length()),
                  label)));
    }
  }

  private static int whichLineItIsOn(String source, int at) {
    return source.substring(0, at).split("\n", -1).length;
  }

  private static AsOneSentence asOneSentence(String source) {
    StringBuilder words = new StringBuilder(source.length());
    List<Integer> whereEachOfThemWas = new ArrayList<>(source.length() + 1);
    boolean theLastOneWasAGap = true;
    for (int on = 0; on < source.length(); on++) {
      String written = String.valueOf(source.charAt(on));
      if (WHAT_SEPARATES_TWO_WORDS_OF_ONE_SENTENCE.matcher(written).matches()) {
        if (!theLastOneWasAGap) {
          whereEachOfThemWas.add(on);
          words.append(' ');
        }
        theLastOneWasAGap = true;
        continue;
      }
      whereEachOfThemWas.add(on);
      words.append(written);
      theLastOneWasAGap = false;
    }
    whereEachOfThemWas.add(source.length());
    return new AsOneSentence(words.toString(), List.copyOf(whereEachOfThemWas));
  }

  private static List<Mention> everyMentionIn(List<Path> swept) {
    Map<String, String> byName = theScreensThisBuildOffers();
    List<Mention> found = new ArrayList<>();
    for (Path source : swept) {
      found.addAll(theMentionsIn(source.toString(), read(source), byName));
    }
    return List.copyOf(found);
  }

  private static Map<String, List<Path>> everySourceByTheTreeItIsIn() {
    Map<String, List<Path>> byTheTreeItIsIn = new LinkedHashMap<>();
    for (Path tree : everySourceTreeOfThisRepository()) {
      byTheTreeItIsIn.put(
          tree.toString(), everyFileUnder(tree, THE_KINDS_OF_SOURCE_THIS_SWEEP_READS));
    }
    return Map.copyOf(byTheTreeItIsIn);
  }

  private static List<Path> everySourceTreeOfThisRepository() {
    try (Stream<Path> walked = Files.walk(THIS_REPOSITORY)) {
      return walked
          .filter(Files::isDirectory)
          .filter(each -> each.getFileName() != null)
          .filter(
              each -> WHAT_EVERY_MODULE_KEEPS_ITS_SOURCES_IN.equals(each.getFileName().toString()))
          .filter(each -> nothingOnItsWayIsIgnored(each))
          .sorted()
          .toList();
    } catch (IOException noRepositoryToWalk) {
      throw new UncheckedIOException(noRepositoryToWalk);
    }
  }

  private static boolean nothingOnItsWayIsIgnored(Path each) {
    for (Path segment : each) {
      if (WHAT_GIT_IGNORES_OR_THIS_BUILD_GENERATES.contains(segment.toString())) {
        return false;
      }
    }
    return true;
  }

  private static List<Path> everyFileUnder(Path root, String suffix) {
    return everyFileUnder(root, List.of(suffix));
  }

  private static List<Path> everyFileUnder(Path root, List<String> suffixes) {
    try (Stream<Path> walked = Files.walk(root)) {
      return walked
          .filter(Files::isRegularFile)
          .filter(file -> itEndsWithOneOf(file.getFileName().toString(), suffixes))
          .sorted()
          .toList();
    } catch (IOException noSourceTreeToRead) {
      throw new UncheckedIOException(noSourceTreeToRead);
    }
  }

  private static boolean itEndsWithOneOf(String fileName, List<String> suffixes) {
    for (String suffix : suffixes) {
      if (fileName.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private static String read(Path source) {
    try {
      return Files.readString(source);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
