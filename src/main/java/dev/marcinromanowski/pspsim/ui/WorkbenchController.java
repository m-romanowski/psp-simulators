package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurations;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class WorkbenchController {

  private final ProviderRegistry providers;
  private final ProviderConfigurations configured;
  private final ScenarioRules scenarios;
  private final WebhookDispatcher deliveries;
  private final SimulatorClock clock;
  private final String publicBaseUrl;
  private final TestRunId defaultTestRun;

  WorkbenchController(
      ProviderRegistry providers,
      ProviderConfigurations configured,
      ScenarioRules scenarios,
      WebhookDispatcher deliveries,
      SimulatorClock clock,
      @Value("${simulator.public-base-url}") String publicBaseUrl,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.configured = configured;
    this.scenarios = scenarios;
    this.deliveries = deliveries;
    this.clock = clock;
    this.publicBaseUrl = publicBaseUrl;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping("/ui")
  String workbench(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      Model model) {
    List<String> known = providers.names();
    model.addAttribute("providers", known);
    model.addAttribute("path", "/ui");
    model.addAttribute("screen", "workbench");
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("secrecy", WorkbenchScreen.A_SECRET_IS_NEVER_A_SUMMARY);
    model.addAttribute(
        "noExercisedTick", WorkbenchScreen.NO_CALL_RECORDS_THE_OPERATION_IT_ASKED_FOR);
    model.addAttribute("newestObjectsShown", WorkbenchScreen.THE_NEWEST_OBJECTS_IT_SHOWS);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen -> describe(chosen, known, model);
    }
    return "workbench";
  }

  private void describe(Selection.Chosen chosen, List<String> known, Model model) {
    SimulatorProvider module = chosen.module();
    TestRunId run = chosen.testRunId();
    List<ScenarioRule> rules = scenarios.of(module.id(), run);
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    model.addAttribute(
        "workbench",
        WorkbenchScreen.of(
            chosen.provider(),
            known,
            run.value(),
            module.accountFields(),
            configured.identitiesOf(module, run),
            accountId -> module.disclosureOf(run, accountId),
            publicBaseUrl,
            module.pairing(),
            configured.routeAliasesOf(module, run),
            module.vocabulary(),
            WorkbenchScreen.theNewestObjectsOf(
                module.objects(
                    new ProviderObjectQuery(
                        run, null, WorkbenchScreen.theLimitItAsksTheObjectReadFor())),
                clock.now(run)),
            rules,
            DeliveryScreen.queueOf(
                deliveries.of(module.id(), run),
                clock.now(run),
                null,
                false,
                deliveries::attemptsOf,
                List.of(),
                DeliveryForm.noneOffered()),
            HostedPages.of(providers.modules())));
  }
}
