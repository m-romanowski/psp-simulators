package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;

public record TheReservedRunOnAScreen(
    String itsId,
    boolean readingIt,
    String whyTheseRequestsAreHere,
    String whatLandsARequestHereIntroduces,
    List<String> whatLandsARequestHere,
    String whatTheRuleOnARowSays,
    String whatATesterChangesAboutThem,
    String howATesterReadsARunAgain,
    String theOtherReader,
    String theOtherReadersLabel) {

  public static final String WHY_THESE_REQUESTS_ARE_HERE =
      "this is the namespace this simulator keeps a request under when resolution named no test"
          + " run. Every request here is one whose assignment failed, and none of them belongs to a"
          + " test run a tester configured.";

  public static final String WHAT_LANDS_A_REQUEST_HERE_INTRODUCES =
      "A request is kept here when what it named could not be resolved to a namespace:";

  public static final List<String> WHAT_LANDS_A_REQUEST_HERE =
      List.of(
          "a test-run header naming a run this simulator holds nothing under",
          "a header outside the published shape of an id",
          "two runs matching one request equally",
          "an action naming a run no namespace can hold");

  public static final String WHAT_THE_RULE_ON_A_ROW_SAYS =
      "The rule on a row names which resolution ended, and is blank on a row written before any"
          + " resolution ran.";

  public static final String WHAT_A_TESTER_CHANGES_ABOUT_THEM =
      "What a tester changes is what the caller sends; the rows already here stay as the record of"
          + " what was sent.";

  public enum Reader {
    THE_WHOLE_TRAIL(
        "/ui/audit", "The whole trail of this namespace, requests and answers", "Audit"),
    WHY_EACH_REQUEST_LANDED_HERE(
        "/ui/correlation", "Why each of these requests was assigned here", "Correlation");

    private final String path;
    private final String label;
    private final String itsLabelInTheRail;

    Reader(String path, String label, String itsLabelInTheRail) {
      this.path = path;
      this.label = label;
      this.itsLabelInTheRail = itsLabelInTheRail;
    }

    public String path() {
      return path;
    }

    public String label() {
      return label;
    }

    public String itsLabelInTheRail() {
      return itsLabelInTheRail;
    }
  }

  public static TheReservedRunOnAScreen offeredBeside(
      TestRunId theRunBeingRead, TestRunId theRunAnEmptyBoxReads, Reader theOtherReader) {
    return new TheReservedRunOnAScreen(
        TestRunId.UNKNOWN.value(),
        TestRunId.UNKNOWN.equals(theRunBeingRead),
        WHY_THESE_REQUESTS_ARE_HERE,
        WHAT_LANDS_A_REQUEST_HERE_INTRODUCES,
        WHAT_LANDS_A_REQUEST_HERE,
        WHAT_THE_RULE_ON_A_ROW_SAYS,
        WHAT_A_TESTER_CHANGES_ABOUT_THEM,
        theBoxARunIsTypedInto(theRunAnEmptyBoxReads),
        theOtherReader.path(),
        theOtherReader.label());
  }

  public static String theRefusalOfAScreenThatShowsWhatARunHolds(TestRunId theRunAnEmptyBoxReads) {
    return TestRunId.theRefusalOfTheReservedId()
        + ". This screen shows what one test run holds, and that id names no test run. "
        + theBoxARunIsTypedInto(theRunAnEmptyBoxReads)
        + " Those requests are read on "
        + theScreensThatReadThisNamespace()
        + ".";
  }

  public static String theBoxARunIsTypedInto(TestRunId theRunAnEmptyBoxReads) {
    return "Type a run id in the "
        + TheShellOnEveryScreen.THE_BOX_A_TEST_RUN_IS_TYPED_INTO
        + " box at the top of this page, for example "
        + TestRunId.SHAPE.aValueItAccepts()
        + ", or empty that box to read "
        + theRunAnEmptyBoxReads.value()
        + ".";
  }

  public static String theScreensThatReadThisNamespace() {
    List<String> labels = new ArrayList<>();
    for (Reader reader : Reader.values()) {
      labels.add(reader.itsLabelInTheRail());
    }
    return "the " + String.join(" screen and the ", labels) + " screen";
  }
}
