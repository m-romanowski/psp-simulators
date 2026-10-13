package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TheReservedRunOnAScreenTest {

  private static final TestRunId A_RUN_A_TESTER_CONFIGURED = TestRunId.of("manual-mr-2026-10-13");

  private static final TestRunId THE_RUN_AN_EMPTY_BOX_READS = TestRunId.of("default");

  private static final Path WHERE_THE_BAR_IS_DRAWN =
      Path.of("src", "main", "resources", "templates", "fragments.html");

  private static final int THE_LONGEST_BLOCK_A_TESTER_READS_HERE = 40;

  @Test
  void a_screen_reading_a_configured_run_offers_the_reserved_one_and_says_nothing_about_it_yet() {
    TheReservedRunOnAScreen offered =
        TheReservedRunOnAScreen.offeredBeside(
            A_RUN_A_TESTER_CONFIGURED,
            THE_RUN_AN_EMPTY_BOX_READS,
            TheReservedRunOnAScreen.Reader.WHY_EACH_REQUEST_LANDED_HERE);

    assertThat(offered.readingIt())
        .as(
            "the banner that explains this namespace belongs on the screen reading it, and the"
                + " link to it belongs on every other run's screen")
        .isFalse();
    assertThat(offered.itsId()).isEqualTo(TestRunId.UNKNOWN.value());
  }

  @Test
  void a_screen_reading_the_reserved_run_says_why_those_requests_are_there() {
    TheReservedRunOnAScreen read = readingIt();

    assertThat(read.readingIt()).isTrue();
    assertThat(read.whyTheseRequestsAreHere())
        .contains("resolution named no test run")
        .contains("none of them belongs to a test run a tester configured");
  }

  @Test
  void what_lands_a_request_here_is_a_list_a_tester_reads_down_rather_than_one_long_sentence() {
    TheReservedRunOnAScreen read = readingIt();

    assertThat(read.whatLandsARequestHere())
        .as("the four resolutions that end in this namespace, one line each")
        .containsExactly(
            "a test-run header naming a run this simulator holds nothing under",
            "a header outside the published shape of an id",
            "two runs matching one request equally",
            "an action naming a run no namespace can hold");
    assertThat(read.whatLandsARequestHereIntroduces()).endsWith(":");
    assertThat(read.whatLandsARequestHere())
        .as("no item of the list repeats the sentence introducing it")
        .noneMatch(landed -> read.whatLandsARequestHereIntroduces().contains(landed));
  }

  @Test
  void the_screen_reading_it_tells_a_tester_how_to_read_a_test_run_again() {
    TheReservedRunOnAScreen read = readingIt();

    assertThat(read.howATesterReadsARunAgain())
        .as(
            "this assertion read \"a screen that writes refuses this namespace\" until the"
                + " classification it rested on was measured false: five screens that write"
                + " nothing refuse this namespace too, and a tester who lands here needs the way"
                + " out rather than the rule that put the other screens off limits")
        .isEqualTo(
            "Type a run id in the test run box at the top of this page, for example"
                + " checkout-smoke, or empty that box to read default.")
        .contains(TheShellOnEveryScreen.THE_BOX_A_TEST_RUN_IS_TYPED_INTO)
        .contains(THE_RUN_AN_EMPTY_BOX_READS.value());
    assertThat(read.whatATesterChangesAboutThem())
        .contains("What a tester changes is what the caller sends");
  }

  @Test
  void the_box_this_panel_names_is_the_one_the_bar_of_every_screen_draws() {
    assertThat(read(WHERE_THE_BAR_IS_DRAWN))
        .as(
            "a tester is sent to the box by the words labelling it on the page, so the label this"
                + " panel and this refusal name is the label the bar draws")
        .contains(
            "<label for=\"testRunId\">"
                + TheShellOnEveryScreen.THE_BOX_A_TEST_RUN_IS_TYPED_INTO
                + "</label>")
        .contains("name=\"testRunId\"");
  }

  @Test
  void no_block_of_this_panel_is_longer_than_a_tester_reads_before_acting() {
    TheReservedRunOnAScreen read = readingIt();
    List<String> blocks = new ArrayList<>(read.whatLandsARequestHere());
    blocks.add(read.whyTheseRequestsAreHere());
    blocks.add(read.whatLandsARequestHereIntroduces());
    blocks.add(read.whatTheRuleOnARowSays());
    blocks.add(read.whatATesterChangesAboutThem());
    blocks.add(read.howATesterReadsARunAgain());

    assertThat(blocks)
        .as(
            "this explanation was one 131-word paragraph of five sentences, four of which said why"
                + " and one of which a tester could act on; it is now one block per thing said and"
                + " none of them is a paragraph a tester gives up on")
        .hasSize(9)
        .allSatisfy(
            block ->
                assertThat(block.split("\\s+"))
                    .as(block)
                    .hasSizeLessThanOrEqualTo(THE_LONGEST_BLOCK_A_TESTER_READS_HERE));
  }

  @Test
  void each_reader_of_this_namespace_names_the_other_one_so_neither_is_a_dead_end() {
    TheReservedRunOnAScreen onTheTrail =
        TheReservedRunOnAScreen.offeredBeside(
            TestRunId.UNKNOWN,
            THE_RUN_AN_EMPTY_BOX_READS,
            TheReservedRunOnAScreen.Reader.WHY_EACH_REQUEST_LANDED_HERE);
    TheReservedRunOnAScreen onTheCorrelationScreen = readingIt();

    assertThat(onTheTrail.theOtherReader()).isEqualTo("/ui/correlation");
    assertThat(onTheTrail.theOtherReadersLabel())
        .isEqualTo("Why each of these requests was assigned here");
    assertThat(onTheCorrelationScreen.theOtherReader()).isEqualTo("/ui/audit");
    assertThat(onTheCorrelationScreen.theOtherReadersLabel())
        .isEqualTo("The whole trail of this namespace, requests and answers");
  }

  @Test
  void every_reader_is_named_in_the_label_the_rail_reads_so_a_tester_can_find_the_screen() {
    for (TheReservedRunOnAScreen.Reader reader : TheReservedRunOnAScreen.Reader.values()) {
      assertThat(theRailLabelOf(reader.path()))
          .as("the words sending a tester to %s name it as the rail does", reader.path())
          .isEqualTo(reader.itsLabelInTheRail());
    }
    assertThat(TheReservedRunOnAScreen.theScreensThatReadThisNamespace())
        .isEqualTo("the Audit screen and the Correlation screen");
  }

  @Test
  void the_screen_this_namespace_is_no_run_of_refuses_it_with_the_fact_and_the_way_out() {
    String refusal =
        TheReservedRunOnAScreen.theRefusalOfAScreenThatShowsWhatARunHolds(
            THE_RUN_AN_EMPTY_BOX_READS);

    assertThat(refusal)
        .as("the fact the store would throw on, unchanged")
        .startsWith("testRunId must not be UNKNOWN; that id is reserved for a failed resolution.");
    assertThat(refusal)
        .as(
            "a refusal of a shape ends in a worked example everywhere else in this build, and this"
                + " one names the box a tester retypes, an id that box accepts, the run an empty"
                + " box reads, and where the requests they came looking for are read")
        .contains("This screen shows what one test run holds")
        .contains(TheReservedRunOnAScreen.theBoxARunIsTypedInto(THE_RUN_AN_EMPTY_BOX_READS))
        .contains("checkout-smoke")
        .contains("the Audit screen and the Correlation screen");
  }

  @Test
  void the_panel_and_the_refusal_name_that_box_in_one_set_of_words() {
    assertThat(readingIt().howATesterReadsARunAgain())
        .as(
            "a tester meets this clause on the screen reading the namespace and in the refusal of"
                + " every screen it is no run of, and one composer writes both")
        .isEqualTo(TheReservedRunOnAScreen.theBoxARunIsTypedInto(THE_RUN_AN_EMPTY_BOX_READS));
  }

  private static TheReservedRunOnAScreen readingIt() {
    return TheReservedRunOnAScreen.offeredBeside(
        TestRunId.UNKNOWN,
        THE_RUN_AN_EMPTY_BOX_READS,
        TheReservedRunOnAScreen.Reader.THE_WHOLE_TRAIL);
  }

  private static String theRailLabelOf(String path) {
    for (RailDestination destination : TheRail.theScreensThisSimulatorCarries()) {
      if (destination.path().equals(path)) {
        return destination.label();
      }
    }
    throw new IllegalStateException(path + " is no screen the rail of this simulator carries");
  }

  private static String read(Path source) {
    try {
      return Files.readString(source);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
