package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.smoke.CheckTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.ChecksAlreadyRunningException;
import dev.marcinromanowski.pspsim.kernel.smoke.CompatibilityChecks;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeNamespace;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeResults;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRunner;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class SmokeController {

  private static final String VIEW = "smoke";

  private static final String PATH = "/ui/smoke";

  private static final @Nullable String NOTHING_WAS_REFUSED_BY_RUNNING_THE_CHECKS = null;

  private final ProviderRegistry providers;
  private final CompatibilityChecks checks;
  private final SmokeRunner runner;
  private final SmokeResults results;
  private final CheckTransport transport;
  private final TestRunId defaultTestRun;

  SmokeController(
      ProviderRegistry providers,
      CompatibilityChecks checks,
      SmokeRunner runner,
      SmokeResults results,
      CheckTransport transport,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.checks = checks;
    this.runner = runner;
    this.results = results;
    this.transport = transport;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String smoke(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      Model model) {
    return render(provider, testRunId, NOTHING_WAS_REFUSED_BY_RUNNING_THE_CHECKS, model);
  }

  @PostMapping(PATH + "/run")
  String run(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      Model model,
      RedirectAttributes attributes) {
    try {
      results.hold(runner.run(checks.all(), transport));
    } catch (ChecksAlreadyRunningException refused) {
      return render(provider, testRunId, refused.getMessage(), model);
    }
    attributes.addAttribute("provider", provider);
    attributes.addAttribute("testRunId", testRunId);
    return "redirect:" + PATH;
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String whatRunningTheChecksRefused,
      Model model) {
    model.addAttribute(
        "refusal",
        SmokeScreen.theRefusalThisScreenPrints(
            whatRunningTheChecksRefused,
            Selection.of(provider, testRunId, providers, defaultTestRun)));
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("checkRun", SmokeNamespace.RUN.value());
    model.addAttribute("smoke", SmokeScreen.of(results.last(), checks.providersWithNoCheckYet()));
    return VIEW;
  }
}
