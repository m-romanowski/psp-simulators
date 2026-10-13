package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class InspectorController {

  private static final String VIEW = "inspector";

  private static final String PATH = "/ui/idempotency";

  private final ProviderRegistry providers;
  private final TestRunId defaultTestRun;

  InspectorController(
      ProviderRegistry providers,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String inspector(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String after,
      @RequestParam(required = false) @Nullable String limit,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused unusable -> model.addAttribute("refusal", unusable.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen -> describe(chosen, after, limit, model);
    }
    return VIEW;
  }

  private void describe(
      Selection.Chosen chosen, @Nullable String after, @Nullable String limit, Model model) {
    SimulatorProvider module = chosen.module();
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", chosen.testRunId().value());
    KeyedExchangeQuery query;
    try {
      query = KeyedExchangeQuery.of(chosen.testRunId().value(), after, limit, chosen.testRunId());
    } catch (IllegalArgumentException rejected) {
      model.addAttribute("refusal", String.valueOf(rejected.getMessage()));
      return;
    }
    model.addAttribute(
        "inspector",
        InspectorScreen.of(
            chosen.testRunId().value(),
            module.keyMechanisms(),
            module.keyedExchanges(query),
            query.limit(),
            objectsOf(module, chosen.testRunId())));
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
