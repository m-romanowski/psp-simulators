package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditPage;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class CorrelationController {

  private static final String VIEW = "correlation";

  private static final String PATH = "/ui/correlation";

  private static final int PAGES_ONE_READ_TAKES = 4;

  private static final int MOST_REQUESTS_SHOWN = 100;

  private static final int MOST_RUNS_OFFERED = 20;

  private final ProviderRegistry providers;
  private final AuditReads audit;
  private final ProviderAccounts accounts;
  private final RouteAliases aliases;
  private final ScenarioRules scenarios;
  private final TestRuns testRuns;
  private final TestRunId defaultTestRun;

  CorrelationController(
      ProviderRegistry providers,
      AuditReads audit,
      ProviderAccounts accounts,
      RouteAliases aliases,
      ScenarioRules scenarios,
      TestRuns testRuns,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.audit = audit;
    this.accounts = accounts;
    this.aliases = aliases;
    this.scenarios = scenarios;
    this.testRuns = testRuns;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String correlation(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String open,
      @RequestParam(required = false) @Nullable String values,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("open", open);
    model.addAttribute("values", values);
    switch (Selection.ofAScreenWhoseSubjectIsTheTrail(
        provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen ->
          explain(chosen.module(), chosen.testRunId(), open, values, model);
    }
    return VIEW;
  }

  private void explain(
      SimulatorProvider module,
      TestRunId testRunId,
      @Nullable String open,
      @Nullable String values,
      Model model) {
    model.addAttribute("provider", module.id().value());
    model.addAttribute("testRunId", testRunId.value());
    model.addAttribute(
        "theReservedRun",
        TheReservedRunOnAScreen.offeredBeside(
            testRunId, defaultTestRun, TheReservedRunOnAScreen.Reader.THE_WHOLE_TRAIL));
    Trail trail = trailOf(module.id(), testRunId);
    List<CorrelationScreen.Row> rows =
        CorrelationScreen.theRequestsOf(trail.records(), open, MOST_REQUESTS_SHOWN);
    model.addAttribute(
        "correlation",
        new CorrelationScreen.View(
            module.id().value(),
            testRunId.value(),
            rows,
            rows.isEmpty() ? CorrelationScreen.NO_PROVIDER_REQUEST_IS_RECORDED : null,
            trail.truncated() ? CorrelationScreen.THE_TRAIL_IS_LONGER_THAN_THIS_READ : null,
            explanationOf(module, testRunId, trail.records(), open, values),
            Disclosure.valuesAsked(values)));
  }

  private CorrelationScreen.@Nullable Explanation explanationOf(
      SimulatorProvider module,
      TestRunId testRunId,
      List<AuditRecord> trail,
      @Nullable String open,
      @Nullable String values) {
    int askedAt = CorrelationScreen.theRequestOpened(trail, open);
    if (askedAt < 0) {
      return null;
    }
    AuditRecord asked = trail.get(askedAt);
    AuditRecord answer = CorrelationScreen.theAnswerTo(trail, askedAt, new boolean[trail.size()]);
    TestRunResolutionRule rule = asked.resolutionRule();
    boolean namesATestRun = rule == null || rule.namesATestRun();

    List<RouteAlias> held = namesATestRun ? aliases.of(module.id(), testRunId) : List.of();
    List<String> accountsHeld =
        rule == TestRunResolutionRule.ACCOUNT_CREDENTIALS
            ? accounts.identitiesOf(module.id(), testRunId)
            : List.of();
    List<ScenarioRule> scenariosHeld =
        namesATestRun ? scenarios.of(module.id(), testRunId) : List.of();
    ScenarioRule matched = theScenarioBehind(module.id(), testRunId, answer);
    ProviderObjectState state = theObjectBehind(module, testRunId, answer);
    String ambiguousField = CorrelationScreen.theFieldAnAmbiguityNamed(answer);
    List<ProviderObjectState> objects =
        ambiguousField == null ? List.of() : objectsOf(module, testRunId);

    return new CorrelationScreen.Explanation(
        asked.auditId(),
        rule == null ? null : rule.name(),
        CorrelationScreen.ruleMeans(rule),
        namesATestRun,
        answer == null ? null : CorrelationScreen.AN_ANSWER_IS_PAIRED_BY_POSITION,
        answer == null ? CorrelationScreen.NO_ANSWER_IS_RECORDED : null,
        CorrelationScreen.theFactsOf(
            asked,
            answer,
            rule == TestRunResolutionRule.ROUTE_ALIAS
                ? CorrelationScreen.theAliasThatCouldHaveServed(asked.theCalledPath(), held)
                : null,
            accountsHeld,
            matched,
            matched == null ? 0 : CorrelationScreen.theRankOf(matched, scenariosHeld),
            scenariosHeld.size(),
            state),
        rule == TestRunResolutionRule.AMBIGUOUS
            ? theRunsThatHoldABinding(module.id())
            : new CorrelationScreen.Candidates(List.of(), 0, null),
        CorrelationScreen.theObjectsCarryingTheField(ambiguousField, objects),
        Disclosure.of(asked.sanitizedHeaders(), Disclosure.valuesAsked(values)),
        Disclosure.of(asked.sanitizedBody(), Disclosure.valuesAsked(values)),
        CorrelationScreen.theProviderHasNoSuchThing(module.vocabulary()),
        CorrelationScreen.thisBuildRecordsNoSuchThing(rule, module.vocabulary(), answer));
  }

  private @Nullable ScenarioRule theScenarioBehind(
      ProviderId provider, TestRunId testRunId, @Nullable AuditRecord answer) {
    String scenarioId = answer == null ? null : answer.scenarioId();
    if (scenarioId == null) {
      return null;
    }
    return scenarios.find(provider, testRunId, scenarioId).orElse(null);
  }

  private static @Nullable ProviderObjectState theObjectBehind(
      SimulatorProvider module, TestRunId testRunId, @Nullable AuditRecord answer) {
    String objectId = answer == null ? null : answer.providerObjectId();
    if (objectId == null) {
      return null;
    }
    return module.state(testRunId, objectId).orElse(null);
  }

  private CorrelationScreen.Candidates theRunsThatHoldABinding(ProviderId provider) {
    List<TestRunId> runs = testRuns.all();
    Map<String, List<String>> accountsPerRun = new LinkedHashMap<>();
    Map<String, List<RouteAlias>> aliasesPerRun = new LinkedHashMap<>();
    for (TestRunId run : runs) {
      accountsPerRun.put(run.value(), accounts.identitiesOf(provider, run));
      aliasesPerRun.put(run.value(), aliases.of(provider, run));
    }
    return CorrelationScreen.theRunsThatHoldABinding(
        runs, accountsPerRun, aliasesPerRun, MOST_RUNS_OFFERED);
  }

  private record Trail(List<AuditRecord> records, boolean truncated) {}

  private Trail trailOf(ProviderId provider, TestRunId testRunId) {
    List<AuditRecord> read = new ArrayList<>();
    String after = null;
    for (int page = 0; page < PAGES_ONE_READ_TAKES; page++) {
      AuditQuery query =
          new AuditQuery(
              testRunId,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(),
              null,
              null,
              null,
              after,
              AuditQuery.LARGEST_LIMIT);
      if (!(audit.pageOf(provider, query) instanceof AuditPage.Listed listed)) {
        return new Trail(read, true);
      }
      read.addAll(listed.records());
      if (listed.records().size() < AuditQuery.LARGEST_LIMIT) {
        return new Trail(read, false);
      }
      after = listed.records().get(listed.records().size() - 1).auditId();
    }
    return new Trail(read, true);
  }

  private static List<ProviderObjectState> objectsOf(
      SimulatorProvider module, TestRunId testRunId) {
    ProviderObjectQuery query =
        new ProviderObjectQuery(testRunId, null, ProviderObjectQuery.LARGEST_LIMIT);
    return module.objects(query) instanceof ProviderObjectPage.Listed listed
        ? listed.objects()
        : List.of();
  }
}
