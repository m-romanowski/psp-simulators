package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurations;
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
class PairingController {

  private static final String VIEW = "pairing";

  private final ProviderRegistry providers;
  private final ProviderAccounts accounts;
  private final ProviderConfigurations configured;
  private final String publicBaseUrl;
  private final TestRunId defaultTestRun;

  PairingController(
      ProviderRegistry providers,
      ProviderAccounts accounts,
      ProviderConfigurations configured,
      @Value("${simulator.public-base-url}") String publicBaseUrl,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.accounts = accounts;
    this.configured = configured;
    this.publicBaseUrl = publicBaseUrl;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PairingPaths.SCREEN)
  String pairing(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      Model model) {
    List<String> known = providers.names();
    model.addAttribute("providers", known);
    model.addAttribute("path", PairingPaths.SCREEN);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("secrecy", PairingScreen.A_SECRET_IS_NEVER_PAIRING_DATA);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen -> describe(chosen, model);
    }
    return VIEW;
  }

  private void describe(Selection.Chosen chosen, Model model) {
    SimulatorProvider module = chosen.module();
    TestRunId run = chosen.testRunId();
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    model.addAttribute(
        "pairing",
        PairingScreen.of(
            chosen.provider(),
            run.value(),
            publicBaseUrl,
            module.pairing(),
            module.accountFields(),
            accounts.identitiesOf(module.id(), run),
            accountId -> module.disclosureOf(run, accountId),
            accounts.webhookTargetsOf(module.id(), run),
            configured.routeAliasesOf(module, run)));
  }
}
