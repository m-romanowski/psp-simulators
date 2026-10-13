package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.audit.TestRunActivity;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class DashboardScreen {

  public static String theHostedSessionsThisScreenDoesNotSum(HostedPages.Split hosted) {
    return hosted.theObjectsAsACount()
        + " waiting for a payer on a page the provider hosts are kept by "
        + hosted.thatKeepAnObjectAPayerFinishes().size()
        + " of the "
        + hosted.providers()
        + " providers here: "
        + hosted.theObjectsTheyKeep()
        + ". The other "
        + hosted.withNoPageAPayerFinishes().size()
        + ", "
        + String.join(" and ", hosted.withNoPageAPayerFinishes())
        + ", have no such page, and each says so in"
        + " its own words when one is asked for. Every one of those objects is answered by the"
        + " read that opens an object of a run and is listed by the read that lists them, so the"
        + " Payments screen shows them and presses the payer's own step. A count of the ones still"
        + " waiting could therefore be summed here. It is not built, and it is named here so"
        + " that a count nobody has written is never read as a zero.";
  }

  public static final String THE_LIVE_SESSIONS_QUEUE_THIS_SCREEN_DOES_NOT_OPEN =
      "There is no queue of their own to open. The objects that wait for a payer are listed beside"
          + " every other object of this test run on the Payments screen, which is where the"
          + " payer's step is pressed and where the page's own address is read.";

  public static final String THE_PAYMENT_METHODS_THIS_SCREEN_DOES_NOT_SUM =
      "A payment-method fixture is an ordinary object of this simulator: the Payment methods"
          + " screen linked above seeds one and removes it, the control API writes, reads and"
          + " deletes one under its fixtures route, and a persona seeds one in one press. What no"
          + " one has written is a column summing them per provider, so the sum is absent here"
          + " rather than shown as a zero.";

  public sealed interface Asked {

    record Run(TestRunId testRunId) implements Asked {}

    record Refused(String message) implements Asked {}

    static Asked of(@Nullable String declared, TestRunId whenAbsent) {
      try {
        return new Run(TestRunId.declaredOr(declared, whenAbsent));
      } catch (IllegalArgumentException malformed) {
        return new Refused(String.valueOf(malformed.getMessage()));
      }
    }
  }

  public static final String A_REMOVAL_LEAVES_THE_RUN_BEING_READ =
      "you are reading it; open another run to forget this one";

  public static final String A_REMOVAL_LEAVES_THE_DEFAULT_RUN =
      "every request that names no run belongs to it";

  public static final String A_FORGETTING_NAMES_A_RUN =
      "Name the test run to forget. The button beside a run in the list names it for you.";

  public static final String A_FORGETTING_IS_CONFIRMED =
      "Forgetting a test run empties it in every provider and drops its registration. Tick confirm"
          + " beside the run and submit again.";

  public static final String THE_RUNS_THE_TRAIL_HAS_RECORDED =
      "Touched, the most recently touched first";

  public static final String THE_RUNS_IT_HAS_NOT = "Known and never touched";

  public static final String A_REMOVAL_IS_CONFIRMED = "yes";

  static final int THE_RUNS_TOUCHED_MOST_RECENTLY_IT_LISTS = 6;

  static final int THE_RUNS_NEVER_TOUCHED_IT_LISTS = 3;

  public record KnownRun(
      String testRunId,
      boolean active,
      @Nullable Instant lastTouchedAt,
      int auditRows,
      @Nullable String whyItCannotBeForgotten) {

    public KnownRun {
      if (active && whyItCannotBeForgotten == null) {
        throw new IllegalArgumentException(
            "the run being read is never forgotten from the screen reading it: " + testRunId);
      }
    }

    public boolean forgettable() {
      return whyItCannotBeForgotten == null;
    }
  }

  public record RunGroup(String heading, List<KnownRun> runs) {

    public RunGroup {
      runs = List.copyOf(runs);
      if (runs.isEmpty()) {
        throw new IllegalArgumentException("a group nothing falls into is not listed: " + heading);
      }
    }
  }

  public record Listing(List<RunGroup> groups, String whatItHolds, @Nullable String match) {

    public Listing {
      groups = List.copyOf(groups);
    }
  }

  public record Emptied(int providers, int objects, int auditRecords) {}

  public sealed interface Forgetting {

    record Granted(TestRunId testRunId) implements Forgetting {}

    record Declined(String message) implements Forgetting {}

    static Forgetting of(
        @Nullable String named,
        @Nullable String confirm,
        TestRunId beingRead,
        TestRunId theDefault) {
      if (named == null || named.isBlank()) {
        return new Declined(A_FORGETTING_NAMES_A_RUN);
      }
      TestRunId asked;
      try {
        asked = TestRunId.of(named.strip());
      } catch (IllegalArgumentException malformed) {
        return new Declined(String.valueOf(malformed.getMessage()));
      }
      if (!A_REMOVAL_IS_CONFIRMED.equals(confirm)) {
        return new Declined(A_FORGETTING_IS_CONFIRMED);
      }
      if (asked.equals(beingRead)) {
        return new Declined(A_REMOVAL_LEAVES_THE_RUN_BEING_READ);
      }
      if (asked.equals(theDefault)) {
        return new Declined(A_REMOVAL_LEAVES_THE_DEFAULT_RUN);
      }
      return new Granted(asked);
    }
  }

  public record Provider(
      String provider,
      boolean answersCallsInThisRun,
      int accounts,
      int webhookTargets,
      int operations,
      int scenarios,
      int pending,
      int due,
      int failing,
      int exhausted,
      int awaitingCall,
      @Nullable Instant lastCallAt) {}

  public record Call(
      String auditId,
      String provider,
      String direction,
      @Nullable String outcome,
      String method,
      @Nullable String path,
      @Nullable Integer httpStatus,
      @Nullable String providerObjectId,
      @Nullable String scenarioId,
      Instant createdAt) {}

  public record Failure(String provider, DeliveryScreen.Row delivery) {}

  public record ActiveRule(String provider, WorkbenchScreen.Scenario scenario) {}

  public record Totals(
      int providers,
      int answering,
      int scenarios,
      int pending,
      int due,
      int failing,
      int exhausted) {}

  public record Reading(
      String provider,
      List<String> accountIds,
      List<WebhookTarget> webhookTargets,
      List<String> operations,
      List<ScenarioRule> scenarios,
      List<WebhookDelivery> deliveries,
      List<AuditRecord> traffic,
      List<AuditRecord> callbackAttempts) {

    public Reading {
      accountIds = List.copyOf(accountIds);
      webhookTargets = List.copyOf(webhookTargets);
      operations = List.copyOf(operations);
      scenarios = List.copyOf(scenarios);
      deliveries = List.copyOf(deliveries);
      traffic = List.copyOf(traffic);
      callbackAttempts = List.copyOf(callbackAttempts);
    }
  }

  public record View(
      String testRunId,
      Listing runs,
      List<Provider> providers,
      List<Call> traffic,
      List<Failure> failing,
      List<ActiveRule> scenarios,
      Totals totals,
      int exchangesRead,
      Instant now,
      String whyItSumsNoHostedSession,
      String whyItOpensNoLiveSessionsQueue,
      String whyItSumsNoPaymentMethod) {

    public View {
      providers = List.copyOf(providers);
      traffic = List.copyOf(traffic);
      failing = List.copyOf(failing);
      scenarios = List.copyOf(scenarios);
    }
  }

  private DashboardScreen() {}

  public static View of(
      TestRunId testRunId,
      Listing runs,
      List<Reading> readings,
      HostedPages.Split hosted,
      Instant now,
      int exchangesRead) {
    List<Provider> providers = new ArrayList<>();
    List<Failure> failing = new ArrayList<>();
    List<ActiveRule> scenarios = new ArrayList<>();
    int answering = 0;
    int scenarioCount = 0;
    int pending = 0;
    int due = 0;
    int failed = 0;
    int exhausted = 0;
    for (Reading reading : readings) {
      DeliveryScreen.Queue queue =
          DeliveryScreen.queueOf(
              reading.deliveries(),
              now,
              null,
              false,
              deliveryId -> List.of(),
              List.of(),
              DeliveryForm.noneOffered());
      Provider provider = providerOf(reading, queue);
      providers.add(provider);
      if (provider.answersCallsInThisRun()) {
        answering++;
      }
      scenarioCount += provider.scenarios();
      pending += queue.pending();
      due += queue.due();
      failed += queue.failing().size();
      exhausted += queue.exhausted();
      for (DeliveryScreen.Row refused : queue.failing()) {
        failing.add(new Failure(reading.provider(), refused));
      }
      for (WorkbenchScreen.Scenario rule : WorkbenchScreen.scenariosOf(reading.scenarios())) {
        scenarios.add(new ActiveRule(reading.provider(), rule));
      }
    }
    return new View(
        testRunId.value(),
        runs,
        List.copyOf(providers),
        theNewestCallsAcrossProviders(readings, exchangesRead),
        List.copyOf(failing),
        List.copyOf(scenarios),
        new Totals(providers.size(), answering, scenarioCount, pending, due, failed, exhausted),
        exchangesRead,
        now,
        theHostedSessionsThisScreenDoesNotSum(hosted),
        THE_LIVE_SESSIONS_QUEUE_THIS_SCREEN_DOES_NOT_OPEN,
        THE_PAYMENT_METHODS_THIS_SCREEN_DOES_NOT_SUM);
  }

  public static Listing listingOf(
      TestRunId active,
      TestRunId theDefault,
      List<TestRunId> registered,
      List<TestRunActivity> newestTouchFirst,
      @Nullable String match) {
    String narrowing = narrowing(match);
    List<TestRunId> known = knownRuns(active, registered);
    Map<TestRunId, TestRunActivity> activity = new LinkedHashMap<>();
    for (TestRunActivity touched : newestTouchFirst) {
      activity.put(touched.testRunId(), touched);
    }
    List<TestRunId> touchedRuns = new ArrayList<>();
    for (TestRunActivity touched : newestTouchFirst) {
      if (known.contains(touched.testRunId())) {
        touchedRuns.add(touched.testRunId());
      }
    }
    List<TestRunId> untouchedRuns = new ArrayList<>();
    for (TestRunId run : known) {
      if (!activity.containsKey(run)) {
        untouchedRuns.add(run);
      }
    }
    List<TestRunId> touchedMatching = pinnedFirst(matching(touchedRuns, narrowing), active);
    List<TestRunId> untouchedMatching = pinnedFirst(matching(untouchedRuns, narrowing), active);
    List<TestRunId> touchedShown = upTo(touchedMatching, THE_RUNS_TOUCHED_MOST_RECENTLY_IT_LISTS);
    List<TestRunId> untouchedShown = upTo(untouchedMatching, THE_RUNS_NEVER_TOUCHED_IT_LISTS);
    List<RunGroup> groups = new ArrayList<>();
    addGroup(groups, THE_RUNS_THE_TRAIL_HAS_RECORDED, touchedShown, active, theDefault, activity);
    addGroup(groups, THE_RUNS_IT_HAS_NOT, untouchedShown, active, theDefault, activity);
    return new Listing(
        List.copyOf(groups),
        whatItHolds(
            known.size(),
            narrowing,
            touchedMatching.size(),
            untouchedMatching.size(),
            touchedShown.size(),
            untouchedShown.size()),
        narrowing);
  }

  private static List<TestRunId> knownRuns(TestRunId active, List<TestRunId> registered) {
    Set<TestRunId> known = new LinkedHashSet<>();
    if (!registered.contains(active)) {
      known.add(active);
    }
    known.addAll(registered);
    return List.copyOf(known);
  }

  private static @Nullable String narrowing(@Nullable String match) {
    if (match == null || match.isBlank()) {
      return null;
    }
    return match.strip().toLowerCase(Locale.ROOT);
  }

  private static List<TestRunId> matching(List<TestRunId> runs, @Nullable String narrowing) {
    if (narrowing == null) {
      return List.copyOf(runs);
    }
    List<TestRunId> found = new ArrayList<>();
    for (TestRunId run : runs) {
      if (run.value().toLowerCase(Locale.ROOT).contains(narrowing)) {
        found.add(run);
      }
    }
    return List.copyOf(found);
  }

  private static List<TestRunId> pinnedFirst(List<TestRunId> runs, TestRunId active) {
    int at = runs.indexOf(active);
    if (at < 1) {
      return List.copyOf(runs);
    }
    List<TestRunId> pinned = new ArrayList<>(runs);
    pinned.add(0, pinned.remove(at));
    return List.copyOf(pinned);
  }

  private static List<TestRunId> upTo(List<TestRunId> runs, int cap) {
    return List.copyOf(runs.subList(0, Math.min(runs.size(), cap)));
  }

  private static void addGroup(
      List<RunGroup> groups,
      String heading,
      List<TestRunId> runs,
      TestRunId active,
      TestRunId theDefault,
      Map<TestRunId, TestRunActivity> activity) {
    if (runs.isEmpty()) {
      return;
    }
    List<KnownRun> listed = new ArrayList<>();
    for (TestRunId run : runs) {
      listed.add(knownRunOf(run, active, theDefault, activity.get(run)));
    }
    groups.add(new RunGroup(heading, List.copyOf(listed)));
  }

  private static KnownRun knownRunOf(
      TestRunId run, TestRunId active, TestRunId theDefault, @Nullable TestRunActivity activity) {
    return new KnownRun(
        run.value(),
        run.equals(active),
        activity == null ? null : activity.lastTouchedAt(),
        activity == null ? 0 : activity.auditRows(),
        whyItCannotBeForgotten(run, active, theDefault));
  }

  private static @Nullable String whyItCannotBeForgotten(
      TestRunId run, TestRunId active, TestRunId theDefault) {
    if (run.equals(active)) {
      return A_REMOVAL_LEAVES_THE_RUN_BEING_READ;
    }
    if (run.equals(theDefault)) {
      return A_REMOVAL_LEAVES_THE_DEFAULT_RUN;
    }
    return null;
  }

  private static String whatItHolds(
      int known,
      @Nullable String narrowing,
      int touched,
      int untouched,
      int touchedShown,
      int untouchedShown) {
    if (narrowing != null && touched + untouched == 0) {
      return "No test run of the " + known + " known here carries " + narrowing + ".";
    }
    StringBuilder said = new StringBuilder();
    if (narrowing == null) {
      said.append(asMany(known, "test run is", "test runs are")).append(" known here: ");
    } else {
      said.append(touched + untouched)
          .append(" of the ")
          .append(asMany(known, "test run known here carries ", "test runs known here carry "))
          .append(narrowing)
          .append(": ");
    }
    said.append(asMany(touched, "has", "have"))
        .append(" been touched and ")
        .append(asMany(untouched, "has", "have"))
        .append(" not. ");
    if (touchedShown < touched || untouchedShown < untouched) {
      said.append("This list holds the ")
          .append(touchedShown)
          .append(" touched most recently and ")
          .append(untouchedShown)
          .append(" never touched; narrow the list to reach another.");
    } else {
      said.append("Every one of them is listed below.");
    }
    return said.toString();
  }

  static String asMany(int counted, String one, String more) {
    return counted + " " + (counted == 1 ? one : more);
  }

  private static Provider providerOf(Reading reading, DeliveryScreen.Queue queue) {
    return new Provider(
        reading.provider(),
        !reading.accountIds().isEmpty(),
        reading.accountIds().size(),
        reading.webhookTargets().size(),
        reading.operations().size(),
        reading.scenarios().size(),
        queue.pending(),
        queue.due(),
        queue.failing().size(),
        queue.exhausted(),
        queue.awaitingCall(),
        theNewestCallOf(reading));
  }

  private static @Nullable Instant theNewestCallOf(Reading reading) {
    Instant newest = null;
    for (AuditRecord record : reading.traffic()) {
      if (newest == null || record.createdAt().isAfter(newest)) {
        newest = record.createdAt();
      }
    }
    return newest;
  }

  private static List<Call> theNewestCallsAcrossProviders(
      List<Reading> readings, int exchangesRead) {
    List<Call> calls = new ArrayList<>();
    for (Reading reading : readings) {
      for (AuditRecord record : reading.traffic()) {
        calls.add(callOf(reading.provider(), record));
      }
      for (AuditRecord record : reading.callbackAttempts()) {
        calls.add(callOf(reading.provider(), record));
      }
    }
    calls.sort(Comparator.comparing(Call::createdAt).reversed());
    return List.copyOf(calls.subList(0, Math.min(calls.size(), exchangesRead)));
  }

  private static Call callOf(String provider, AuditRecord record) {
    ProviderOutcome outcome = record.outcome();
    return new Call(
        record.auditId(),
        provider,
        record.direction().name(),
        outcome == null ? null : outcome.name(),
        record.method(),
        record.path(),
        record.httpStatus(),
        record.providerObjectId(),
        record.scenarioId(),
        record.createdAt());
  }
}
