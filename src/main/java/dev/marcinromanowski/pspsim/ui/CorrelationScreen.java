package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class CorrelationScreen {

  public static final String THE_ID_THE_PROVIDER_ANSWERED_WITH =
      "the id the provider answered with";

  public static final String NO_PROVIDER_REQUEST_IS_RECORDED =
      "no provider-compatible request is recorded for this provider under this namespace. A"
          + " namespace that was never written to and one that was reset read the same way here.";

  public static final String THE_TRAIL_IS_LONGER_THAN_THIS_READ =
      "this namespace's trail is longer than one read of this screen, so the requests below begin"
          + " at the oldest row it read and earlier requests are not on it";

  public static final String AN_ANSWER_IS_PAIRED_BY_POSITION =
      "the rule that assigned this request is on the request row, and the scenario that answered"
          + " it, the object it reached and any refusal are on the response row. This simulator"
          + " writes no link between the two, so the answer read here is the nearest later response"
          + " of the same method and path that no earlier request had already claimed, which is the"
          + " answer to this request in a test run driving one call at a time.";

  public static final String NO_ANSWER_IS_RECORDED =
      "no response row of this namespace answers this request, so nothing here can say which"
          + " scenario answered it or which object it reached. A request whose answer was not"
          + " recorded and one still being answered read the same way.";

  public static final String MORE_RUNS_HOLD_A_BINDING_THAN_THIS_SCREEN_LISTS =
      "this simulator keeps a test run until something removes it, so a long-lived simulator holds"
          + " far more runs than a tie can have stood between. The runs above are the first this"
          + " screen lists and not the likeliest, because nothing recorded which of them tied.";

  public static final String NO_CANDIDATE_IS_RECORDED =
      "this simulator weighs every rung's candidates together and then stores the verdict alone."
          + " The test runs that tied are held in memory while the request is answered and no"
          + " column keeps them, so which runs this request could have been assigned to cannot be"
          + " read back. The runs offered beside this request are the runs holding a binding for"
          + " this provider now, which is where a tie comes from, and not a record of the tie.";

  public static final String NO_RUNG_IS_RECORDED_FOR_AN_AMBIGUITY =
      "the verdict says the request was ambiguous and not which rung was ambiguous: a tie on the"
          + " test-run header, on a route alias and on account credentials are all stored as the"
          + " one marker this request carries.";

  public static final String NO_ACCOUNT_IS_RECORDED =
      "the account a request resolved to is not a column of this simulator's trail, and the"
          + " credential fields that would name it are replaced by a marker when the row is"
          + " written, so the account cannot be recovered from the row either. The accounts offered"
          + " beside this request are the accounts this run holds now, not the account it matched.";

  public static final String NO_MERCHANT_ID_IS_HELD_BESIDE_AN_ACCOUNT =
      "this simulator identifies an account by one id and holds no second merchant id beside it, so"
          + " an account id is the whole of what a request can be said to have matched here.";

  public static final String NO_OWNER_IS_RECORDED =
      "this simulator records no owner or customer against a request, and a provider object carries"
          + " none either, so no row of this build can say which customer a request was assigned"
          + " to. An owner is held only on a seeded payment method, which no request names.";

  public static final String NO_PRIORITY_IS_RECORDED =
      "the trail records which scenario answered a request and never the priority it held when it"
          + " answered. A priority shown beside this request is read from that scenario as it"
          + " stands now: one edited since reads differently here, and one deleted since reads as"
          + " nothing at all.";

  public static final String NO_LOSING_SCENARIO_IS_RECORDED =
      "the engine keeps the scenario that won and discards the ones it outranked, so why this"
          + " scenario beat another is not recorded. The order it is consulted in is priority"
          + " first, then specificity, then arrival, and a rank shown beside this request is that"
          + " order over the scenarios this run holds now.";

  public static final String NO_UNMATCHED_FIELD_IS_RECORDED_FOR_RESOLUTION =
      "resolution stores the rule that decided and never the rungs it passed through, so a request"
          + " assigned by its credentials cannot say whether it also carried a header that named"
          + " nothing. The fields below are what the request carried, not what resolution compared.";

  public static final String NO_SCENARIO_ANSWERED_IT =
      "no scenario is recorded against this request. A run holding no scenario, a request no"
          + " scenario matched and a request the engine was never consulted about are one answer"
          + " here.";

  public static final String THIS_BUILD_RESOLVES_NO_REQUEST_BY_AN_OBJECT =
      "the published order puts correlation with an existing object below account credentials, and"
          + " no provider of this build ever offers a candidate at that rung, so no request here"
          + " can carry it. A tester seeing it absent is seeing a rung that never fires, not a"
          + " request that failed to reach one.";

  public record Fact(String what, String value, @Nullable String reconstructedFrom) {

    public boolean recorded() {
      return reconstructedFrom == null;
    }
  }

  public record Absent(String what, String why) {}

  public record Candidate(String testRunId, String holds) {}

  public record Candidates(List<Candidate> runs, int notShown, @Nullable String truncated) {

    public Candidates {
      runs = List.copyOf(runs);
    }
  }

  public record NamedObject(String providerObjectId, String field) {}

  public record Row(
      int place,
      String auditId,
      String testRunId,
      @Nullable String rule,
      String ruleMeans,
      String method,
      String path,
      @Nullable String scenarioId,
      @Nullable String providerObjectId,
      boolean ambiguous,
      Instant at,
      boolean open) {}

  public record Explanation(
      String auditId,
      @Nullable String rule,
      String ruleMeans,
      boolean namesATestRun,
      @Nullable String answerPairedByPosition,
      @Nullable String unanswered,
      List<Fact> facts,
      Candidates candidates,
      List<NamedObject> namedObjects,
      List<Disclosure.Field> headers,
      List<Disclosure.Field> body,
      List<Absent> theProviderHasNoSuchThing,
      List<Absent> thisBuildRecordsNoSuchThing) {

    public Explanation {
      facts = List.copyOf(facts);
      namedObjects = List.copyOf(namedObjects);
      headers = List.copyOf(headers);
      body = List.copyOf(body);
      theProviderHasNoSuchThing = List.copyOf(theProviderHasNoSuchThing);
      thisBuildRecordsNoSuchThing = List.copyOf(thisBuildRecordsNoSuchThing);
    }
  }

  public record View(
      String provider,
      String testRunId,
      List<Row> rows,
      @Nullable String empty,
      @Nullable String truncated,
      @Nullable Explanation explanation,
      boolean valuesShown) {

    public View {
      rows = List.copyOf(rows);
    }
  }

  private CorrelationScreen() {}

  public static String ruleMeans(@Nullable TestRunResolutionRule rule) {
    if (rule == null) {
      return "this request records no rule, which is what a row this simulator did not resolve"
          + " carries";
    }
    return switch (rule) {
      case TEST_HEADER -> "the request carried the test-run header and the run it named exists";
      case ROUTE_ALIAS -> "the request arrived on a URL bound to one test run";
      case ACCOUNT_CREDENTIALS ->
          "no header and no alias decided it, and the credentials the request carried are"
              + " configured for exactly one test run";
      case OBJECT_CORRELATION ->
          "the request named an object an existing test run already holds, which is a rung no"
              + " provider of this build offers a candidate at";
      case DEFAULT ->
          "no rule above matched, so the request was assigned to the default namespace rather than"
              + " refused";
      case AMBIGUOUS ->
          "more than one test run matched, so this simulator assigned the request to no run rather"
              + " than choosing between equal candidates";
      case TEST_HEADER_UNMATCHED ->
          "the request carried the test-run header and no run of that name exists, and a header"
              + " that names nothing ends resolution rather than falling through to the next rule";
    };
  }

  public static boolean isAProviderCompatibleRequest(AuditRecord record) {
    return record.direction() == AuditDirection.INBOUND_REQUEST
        && record.origin() == ObjectOrigin.PROVIDER_API;
  }

  public static boolean answersAProviderCall(AuditRecord record) {
    return record.direction() == AuditDirection.OUTBOUND_RESPONSE
        && record.origin() == ObjectOrigin.PROVIDER_API;
  }

  public static @Nullable AuditRecord theAnswerTo(
      List<AuditRecord> trail, int askedAt, boolean[] claimed) {
    AuditRecord asked = trail.get(askedAt);
    for (int index = askedAt + 1; index < trail.size(); index++) {
      AuditRecord later = trail.get(index);
      if (isAProviderCompatibleRequest(later)) {
        return null;
      }
      if (!answersAProviderCall(later) || claimed[index]) {
        continue;
      }
      if (later.method().equals(asked.method())
          && later.theCalledPath().equals(asked.theCalledPath())) {
        claimed[index] = true;
        return later;
      }
    }
    return null;
  }

  public static List<Row> theRequestsOf(
      List<AuditRecord> trail, @Nullable String openAuditId, int mostRowsShown) {
    boolean[] claimed = new boolean[trail.size()];
    List<Row> rows = new ArrayList<>();
    for (int index = 0; index < trail.size(); index++) {
      AuditRecord record = trail.get(index);
      if (!isAProviderCompatibleRequest(record)) {
        continue;
      }
      AuditRecord answer = theAnswerTo(trail, index, claimed);
      rows.add(rowOf(index, record, answer, record.auditId().equals(openAuditId)));
    }
    return List.copyOf(rows.subList(Math.max(0, rows.size() - mostRowsShown), rows.size()));
  }

  private static Row rowOf(
      int place, AuditRecord record, @Nullable AuditRecord answer, boolean open) {
    TestRunResolutionRule rule = record.resolutionRule();
    return new Row(
        place,
        record.auditId(),
        record.testRunId().value(),
        rule == null ? null : rule.name(),
        ruleMeans(rule),
        record.method(),
        record.theCalledPath(),
        answer == null ? null : answer.scenarioId(),
        answer == null ? null : answer.providerObjectId(),
        isAmbiguous(rule, answer),
        record.createdAt(),
        open);
  }

  public static boolean isAmbiguous(
      @Nullable TestRunResolutionRule rule, @Nullable AuditRecord answer) {
    if (rule == TestRunResolutionRule.AMBIGUOUS) {
      return true;
    }
    return answer != null && answer.refusal() instanceof RefusalSubject.SeveralObjectsAnsweredTo;
  }

  public static int theRequestOpened(List<AuditRecord> trail, @Nullable String openAuditId) {
    if (openAuditId == null) {
      return -1;
    }
    for (int index = 0; index < trail.size(); index++) {
      AuditRecord record = trail.get(index);
      if (record.auditId().equals(openAuditId) && isAProviderCompatibleRequest(record)) {
        return index;
      }
    }
    return -1;
  }

  public static @Nullable RouteAlias theAliasThatCouldHaveServed(
      String calledUrl, List<RouteAlias> aliases) {
    RouteAlias longest = null;
    String called = calledUrl.toLowerCase(Locale.ROOT);
    for (RouteAlias alias : aliases) {
      if (!called.contains(alias.key().value().toLowerCase(Locale.ROOT))) {
        continue;
      }
      if (longest == null || alias.key().length() > longest.key().length()) {
        longest = alias;
      }
    }
    return longest;
  }

  public static int theRankOf(ScenarioRule matched, List<ScenarioRule> held) {
    int outrankedBy = 0;
    for (ScenarioRule other : held) {
      if (ScenarioRule.THE_ORDER_THE_ENGINE_CONSULTS_THEM.compare(other, matched) > 0) {
        outrankedBy++;
      }
    }
    return outrankedBy + 1;
  }

  public static Candidates theRunsThatHoldABinding(
      List<TestRunId> runs,
      Map<String, List<String>> accountsPerRun,
      Map<String, List<RouteAlias>> aliasesPerRun,
      int mostShown) {
    List<Candidate> named = new ArrayList<>();
    for (TestRunId run : runs) {
      String value = run.value();
      int accounts = accountsPerRun.getOrDefault(value, List.of()).size();
      int aliases = aliasesPerRun.getOrDefault(value, List.of()).size();
      if (accounts == 0 && aliases == 0) {
        continue;
      }
      named.add(new Candidate(value, holdsOf(accounts, aliases)));
    }
    if (named.size() <= mostShown) {
      return new Candidates(named, 0, null);
    }
    return new Candidates(
        named.subList(0, mostShown),
        named.size() - mostShown,
        MORE_RUNS_HOLD_A_BINDING_THAN_THIS_SCREEN_LISTS);
  }

  private static String holdsOf(int accounts, int aliases) {
    if (aliases == 0) {
      return accounts + " configured account" + (accounts == 1 ? "" : "s");
    }
    if (accounts == 0) {
      return aliases + " bound route alias" + (aliases == 1 ? "" : "es");
    }
    return accounts
        + " configured account"
        + (accounts == 1 ? "" : "s")
        + " and "
        + aliases
        + " bound route alias"
        + (aliases == 1 ? "" : "es");
  }

  public static List<NamedObject> theObjectsCarryingTheField(
      @Nullable String field, List<ProviderObjectState> objects) {
    if (field == null) {
      return List.of();
    }
    List<NamedObject> named = new ArrayList<>();
    for (ProviderObjectState object : objects) {
      for (Map.Entry<String, String> related : object.relatedObjects().entrySet()) {
        if (related.getKey().equalsIgnoreCase(field)) {
          named.add(new NamedObject(object.providerObjectId(), related.getKey()));
          break;
        }
      }
    }
    return List.copyOf(named);
  }

  public static @Nullable String theFieldAnAmbiguityNamed(@Nullable AuditRecord answer) {
    if (answer == null) {
      return null;
    }
    return answer.refusal() instanceof RefusalSubject.SeveralObjectsAnsweredTo several
        ? several.theFieldThatNamedThem()
        : null;
  }

  public static List<Absent> theProviderHasNoSuchThing(ProviderVocabulary vocabulary) {
    List<Absent> named = new ArrayList<>();
    for (MatchFact fact : List.of(MatchFact.MERCHANT_ID, MatchFact.CUSTOMER_ID)) {
      String instead = vocabulary.factsItNeverCarries().get(fact);
      if (instead != null) {
        named.add(new Absent(fact.property(), instead));
      }
    }
    return List.copyOf(named);
  }

  public static List<Absent> thisBuildRecordsNoSuchThing(
      @Nullable TestRunResolutionRule rule,
      ProviderVocabulary vocabulary,
      @Nullable AuditRecord answer) {
    List<Absent> named = new ArrayList<>();
    if (rule == TestRunResolutionRule.AMBIGUOUS) {
      named.add(new Absent("the test runs that tied", NO_CANDIDATE_IS_RECORDED));
      named.add(new Absent("the rung that was ambiguous", NO_RUNG_IS_RECORDED_FOR_AN_AMBIGUITY));
    }
    named.add(
        new Absent(
            "correlation with an existing object", THIS_BUILD_RESOLVES_NO_REQUEST_BY_AN_OBJECT));
    if (!vocabulary.factsItNeverCarries().containsKey(MatchFact.MERCHANT_ID)) {
      named.add(new Absent("the account the request matched", NO_ACCOUNT_IS_RECORDED));
      named.add(
          new Absent(
              "a merchant id beside that account", NO_MERCHANT_ID_IS_HELD_BESIDE_AN_ACCOUNT));
    }
    if (!vocabulary.factsItNeverCarries().containsKey(MatchFact.CUSTOMER_ID)) {
      named.add(new Absent("the owner or customer the request matched", NO_OWNER_IS_RECORDED));
    }
    if (answer != null && answer.scenarioId() != null) {
      named.add(
          new Absent("the priority that scenario held when it answered", NO_PRIORITY_IS_RECORDED));
      named.add(new Absent("the scenarios this one outranked", NO_LOSING_SCENARIO_IS_RECORDED));
    }
    named.add(
        new Absent(
            "the fields resolution compared and did not match",
            NO_UNMATCHED_FIELD_IS_RECORDED_FOR_RESOLUTION));
    return List.copyOf(named);
  }

  public static List<Fact> theFactsOf(
      AuditRecord record,
      @Nullable AuditRecord answer,
      @Nullable RouteAlias servedBy,
      List<String> accountsOfTheRun,
      @Nullable ScenarioRule scenario,
      int rank,
      int scenariosHeld,
      @Nullable ProviderObjectState state) {
    List<Fact> facts = new ArrayList<>();
    facts.add(new Fact("selected testRunId", record.testRunId().value(), null));
    TestRunResolutionRule rule = record.resolutionRule();
    facts.add(new Fact("resolution rule", rule == null ? "none recorded" : rule.name(), null));
    facts.add(new Fact("the URL the caller asked for", record.theCalledPath(), null));
    if (servedBy != null) {
      facts.add(
          new Fact(
              "the route alias that binds that URL",
              servedBy.key().value(),
              "the aliases this run holds now"));
    }
    for (String account : accountsOfTheRun) {
      facts.add(new Fact("an account this run holds", account, "this run's configuration now"));
    }
    facts.add(
        new Fact(
            "matched scenario id",
            answer == null || answer.scenarioId() == null ? "none" : answer.scenarioId(),
            null));
    if (scenario != null) {
      facts.add(
          new Fact(
              "that scenario's priority",
              String.valueOf(scenario.priority()),
              "that scenario as it stands now"));
      facts.add(
          new Fact(
              "where it sits in the order the engine consults",
              rank + " of " + scenariosHeld,
              "the scenarios this run holds now"));
    }
    if (answer != null && answer.providerObjectId() != null) {
      facts.add(new Fact(THE_ID_THE_PROVIDER_ANSWERED_WITH, answer.providerObjectId(), null));
    }
    addTheRefusal(answer, facts);
    if (state != null) {
      state
          .relatedObjects()
          .forEach(
              (field, id) ->
                  facts.add(new Fact("an object it names by " + field, id, "that object")));
    }
    return List.copyOf(facts);
  }

  private static void addTheRefusal(@Nullable AuditRecord answer, List<Fact> facts) {
    RefusalSubject refusal = answer == null ? null : answer.refusal();
    if (refusal == null) {
      return;
    }
    switch (refusal) {
      case RefusalSubject.NoObjectAnsweredTo none ->
          facts.add(
              new Fact(
                  "a field that named no object of this run", none.theFieldThatNamedIt(), null));
      case RefusalSubject.SeveralObjectsAnsweredTo several -> {
        facts.add(
            new Fact(
                "a field several objects of this run answered to",
                several.theFieldThatNamedThem(),
                null));
        facts.add(
            new Fact("how many answered to it", String.valueOf(several.howManyAnswered()), null));
      }
      case RefusalSubject.AnObjectItWillNotName withheld ->
          facts.add(
              new Fact(
                  "why this provider's contract names no object",
                  withheld.whyItsContractNamesNone(),
                  null));
      case RefusalSubject.NoObjectWasNamed other ->
          facts.add(new Fact("what it refused instead", other.whatItRefusedInstead(), null));
      case RefusalSubject.TheObject _ -> {
        return;
      }
    }
  }
}
