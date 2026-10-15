package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.RunResets;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class DashboardController {

  private static final String VIEW = "dashboard";

  private static final String PATH = "/ui/dashboard";

  private static final String ACCOUNT_FORM = "/ui/configuration/account";

  private static final int THE_EXCHANGES_AN_OVERVIEW_READS = 20;

  private final ProviderRegistry providers;
  private final ProviderAccounts accounts;
  private final ScenarioRules scenarios;
  private final WebhookDispatcher deliveries;
  private final AuditReads audit;
  private final TestRuns testRuns;
  private final SimulatorClock clock;
  private final ActionTrail actions;
  private final TestRunId defaultTestRun;
  private final RunResets resets;

  private record Forgotten(DashboardScreen.Emptied emptied, int registrations) {}

  DashboardController(
      ProviderRegistry providers,
      ProviderAccounts accounts,
      ScenarioRules scenarios,
      WebhookDispatcher deliveries,
      AuditReads audit,
      TestRuns testRuns,
      SimulatorClock clock,
      ActionTrail actions,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId,
      RunResets resets) {
    this.providers = providers;
    this.accounts = accounts;
    this.scenarios = scenarios;
    this.deliveries = deliveries;
    this.audit = audit;
    this.testRuns = testRuns;
    this.clock = clock;
    this.actions = actions;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
    this.resets = resets;
  }

  @GetMapping(PATH)
  String overview(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String runMatch,
      Model model) {
    return render(provider, testRunId, runMatch, null, model);
  }

  @PostMapping(PATH + "/test-run")
  String createTheTestRun(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String newTestRunId,
      Model model,
      RedirectAttributes attributes) {
    switch (DashboardScreen.Asked.of(newTestRunId, defaultTestRun)) {
      case DashboardScreen.Asked.Refused refused -> {
        return render(provider, testRunId, null, refused.message(), model);
      }
      case DashboardScreen.Asked.Run asked -> {
        carry("provider", provider, attributes);
        attributes.addAttribute("testRunId", asked.testRunId().value());
      }
    }
    return "redirect:" + ACCOUNT_FORM;
  }

  @PostMapping(PATH + "/reset")
  String resetTheTestRun(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String runMatch,
      Model model,
      RedirectAttributes attributes) {
    if (!(DashboardScreen.Asked.of(testRunId, defaultTestRun)
        instanceof DashboardScreen.Asked.Run asked)) {
      return render(provider, testRunId, runMatch, null, model);
    }
    DashboardScreen.Emptied emptied =
        resets.ofEvery(
            providers.modules(),
            asked.testRunId(),
            () -> emptyEveryProvider(asked.testRunId(), asked.testRunId(), PATH + "/reset"));
    carry("provider", provider, attributes);
    carry("runMatch", runMatch, attributes);
    attributes.addAttribute("testRunId", asked.testRunId().value());
    attributes.addFlashAttribute(
        Done.ATTRIBUTE,
        Done.theTestRunIsReset(emptied.providers(), emptied.objects(), emptied.auditRecords()));
    return "redirect:" + PATH;
  }

  @PostMapping(PATH + "/forget")
  String forgetTheTestRun(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String forgetTestRunId,
      @RequestParam(required = false) @Nullable String confirm,
      @RequestParam(required = false) @Nullable String runMatch,
      Model model,
      RedirectAttributes attributes) {
    if (!(DashboardScreen.Asked.of(testRunId, defaultTestRun)
        instanceof DashboardScreen.Asked.Run reading)) {
      return render(provider, testRunId, runMatch, null, model);
    }
    switch (DashboardScreen.Forgetting.of(
        forgetTestRunId, confirm, reading.testRunId(), defaultTestRun)) {
      case DashboardScreen.Forgetting.Declined declined -> {
        return render(provider, testRunId, runMatch, declined.message(), model);
      }
      case DashboardScreen.Forgetting.Granted granted -> {
        Forgotten forgotten =
            resets.ofEvery(
                providers.modules(),
                granted.testRunId(),
                () ->
                    new Forgotten(
                        emptyEveryProvider(
                            granted.testRunId(), reading.testRunId(), PATH + "/forget"),
                        testRuns.forget(granted.testRunId())));
        DashboardScreen.Emptied emptied = forgotten.emptied();
        int registrations = forgotten.registrations();
        carry("provider", provider, attributes);
        carry("runMatch", runMatch, attributes);
        attributes.addAttribute("testRunId", reading.testRunId().value());
        attributes.addFlashAttribute(
            Done.ATTRIBUTE,
            Done.theTestRunIsForgotten(granted.testRunId().value(), registrations, emptied));
        return "redirect:" + PATH;
      }
    }
  }

  private DashboardScreen.Emptied emptyEveryProvider(
      TestRunId emptied, TestRunId whereTheActionIsRecorded, String path) {
    int objects = 0;
    int auditRecords = 0;
    int cleared = 0;
    for (SimulatorProvider module : providers.modules()) {
      ResetOutcome outcome = module.reset(emptied, ResetScope.TEST_RUN);
      actions.record(
          AuditedAction.applied(
              module.id(),
              whereTheActionIsRecorded,
              ObjectOrigin.UI,
              "POST",
              path,
              ActionVerdict.applied(HttpStatus.FOUND.value()),
              ActionFacts.none()
                  .and("scope", ResetScope.TEST_RUN.name())
                  .and("emptied", emptied.value())
                  .andCount("clearedObjects", outcome.clearedObjects())
                  .andCount("clearedAuditRecords", outcome.clearedAuditRecords())
                  .asRecorded()));
      objects += outcome.clearedObjects();
      auditRecords += outcome.clearedAuditRecords();
      cleared++;
    }
    return new DashboardScreen.Emptied(cleared, objects, auditRecords);
  }

  private static void carry(String name, @Nullable String value, RedirectAttributes attributes) {
    if (value != null && !value.isBlank()) {
      attributes.addAttribute(name, value);
    }
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String runMatch,
      @Nullable String rejection,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("runMatch", runMatch);
    if (rejection != null) {
      model.addAttribute("refusal", rejection);
    }
    switch (DashboardScreen.Asked.of(testRunId, defaultTestRun)) {
      case DashboardScreen.Asked.Refused refused ->
          model.addAttribute("refusal", refused.message());
      case DashboardScreen.Asked.Run asked -> describe(asked.testRunId(), runMatch, model);
    }
    return VIEW;
  }

  private void describe(TestRunId run, @Nullable String runMatch, Model model) {
    model.addAttribute("testRunId", run.value());
    List<DashboardScreen.Reading> readings = new ArrayList<>();
    for (SimulatorProvider module : providers.modules()) {
      readings.add(
          new DashboardScreen.Reading(
              module.id().value(),
              accounts.identitiesOf(module.id(), run),
              accounts.webhookTargetsOf(module.id(), run),
              module.vocabulary().operationNames(),
              scenarios.of(module.id(), run),
              deliveries.of(module.id(), run),
              audit.latestOf(
                  module.id(), run, ObjectOrigin.PROVIDER_API, THE_EXCHANGES_AN_OVERVIEW_READS),
              audit.latestOf(
                  module.id(), run, ObjectOrigin.SCHEDULED_JOB, THE_EXCHANGES_AN_OVERVIEW_READS)));
    }
    model.addAttribute(
        "dashboard",
        DashboardScreen.of(
            run,
            DashboardScreen.listingOf(
                run, defaultTestRun, testRuns.all(), audit.theRunsNewestTouchFirst(), runMatch),
            List.copyOf(readings),
            HostedPages.of(providers.modules()),
            clock.now(run),
            THE_EXCHANGES_AN_OVERVIEW_READS));
  }
}
