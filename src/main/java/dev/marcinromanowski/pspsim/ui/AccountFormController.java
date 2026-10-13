package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationRefusal;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
class AccountFormController {

  private static final String VIEW = "account";

  private static final String PATH = "/ui/configuration/account";

  private static final String CONFIGURATION = "/ui/configuration";

  private final ProviderRegistry providers;
  private final ProviderAccounts accounts;
  private final ConfigurationAdmission configurations;
  private final ActionTrail actions;
  private final TestRunId defaultTestRun;

  AccountFormController(
      ProviderRegistry providers,
      ProviderAccounts accounts,
      ConfigurationAdmission configurations,
      ActionTrail actions,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.accounts = accounts;
    this.configurations = configurations;
    this.actions = actions;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String form(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String callbackUrl,
      Model model) {
    return render(provider, testRunId, accountId, Map.of(), Map.of(), callbackUrl, null, model);
  }

  @PostMapping(PATH)
  String configureTheAccount(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String callbackUrl,
      @RequestParam Map<String, String> parameters,
      Model model,
      RedirectAttributes attributes) {
    Map<String, String> submitted =
        TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(parameters);
    Map<String, String> units = AccountForm.submittedUnitsOf(parameters);
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, accountId, submitted, units, callbackUrl, null, model);
    }
    if (AccountForm.namesACallbackTargetForNoAccount(accountId, callbackUrl)) {
      recordTheRefusal(
          chosen,
          AccountForm.theRefusalOfACallbackTargetForNoAccount(),
          ConfigurationFacts.of(
              chosen.module().accountFields(),
              AccountForm.configurationOf(
                  chosen.testRunId().value(),
                  accountId,
                  chosen.module().accountFields(),
                  submitted,
                  units,
                  callbackUrl)),
          AccountForm.A_CALLBACK_TARGET_BELONGS_TO_A_NAMED_ACCOUNT);
      return render(
          provider,
          testRunId,
          accountId,
          submitted,
          units,
          callbackUrl,
          ConfigurationRefusal.of(AccountForm.A_CALLBACK_TARGET_BELONGS_TO_A_NAMED_ACCOUNT),
          model);
    }
    SimulatorProvider module = chosen.module();
    DeclaredConfiguration asked =
        AccountForm.configurationOf(
            chosen.testRunId().value(),
            accountId,
            module.accountFields(),
            submitted,
            units,
            callbackUrl);
    ConfigurationAdmission.Verdict verdict = configurations.apply(module, asked);
    switch (verdict) {
      case ConfigurationAdmission.Verdict.Rejected rejected -> {
        recordTheRefusal(
            chosen,
            rejected.theRefusalItReports(),
            ConfigurationFacts.of(module.accountFields(), asked),
            rejected.message());
        return render(
            provider,
            testRunId,
            accountId,
            submitted,
            units,
            callbackUrl,
            ConfigurationRefusal.of(rejected.message()),
            model);
      }
      case ConfigurationAdmission.Verdict.Refused refused -> {
        recordTheRefusal(
            chosen,
            refused.theRefusalItReports(),
            ConfigurationFacts.of(module.accountFields(), asked),
            refused.refusal().message());
        return render(
            provider,
            testRunId,
            accountId,
            submitted,
            units,
            callbackUrl,
            refused.refusal(),
            model);
      }
      case ConfigurationAdmission.Verdict.Conflicting conflicting -> {
        recordTheRefusal(
            chosen,
            conflicting.theRefusalItReports(),
            ConfigurationFacts.of(module.accountFields(), asked),
            conflicting.refusal().message());
        return render(
            provider,
            testRunId,
            accountId,
            submitted,
            units,
            callbackUrl,
            conflicting.refusal(),
            model);
      }
      case ConfigurationAdmission.Verdict.Applied applied -> {
        actions.record(
            AuditedAction.applied(
                module.id(),
                applied.testRunId(),
                ObjectOrigin.UI,
                "POST",
                PATH,
                ActionVerdict.applied(HttpStatus.FOUND.value()),
                ConfigurationFacts.of(module.accountFields(), asked)));
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", applied.testRunId().value());
        attributes.addAttribute("open", String.valueOf(accountId).strip());
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_ACCOUNT_IS_CONFIGURED);
      }
    }
    return "redirect:" + CONFIGURATION;
  }

  private void recordTheRefusal(
      Selection.Chosen chosen,
      ActionVerdict.Refused verdict,
      Map<String, List<String>> facts,
      String inItsOwnWords) {
    actions.record(
        AuditedAction.refusing(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            "POST",
            PATH,
            verdict,
            ActionFacts.beside(facts, ActionFacts.REFUSED_BECAUSE, inItsOwnWords)));
  }

  private List<String> callbackTargetsOf(SimulatorProvider module, TestRunId run) {
    List<String> urls = new ArrayList<>();
    for (WebhookTarget target : accounts.webhookTargetsOf(module.id(), run)) {
      if (!urls.contains(target.url())) {
        urls.add(target.url());
      }
    }
    return List.copyOf(urls);
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String accountId,
      Map<String, String> submitted,
      Map<String, String> units,
      @Nullable String callbackUrl,
      @Nullable ConfigurationRefusal refusal,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", "configuration");
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("accountId", accountId);
    AccountForm.@Nullable View drawn = null;
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused unusable -> model.addAttribute("refusal", unusable.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen ->
          drawn = describe(chosen, accountId, submitted, units, callbackUrl, refusal, model);
    }
    if (refusal != null) {
      model.addAttribute(
          "refusal",
          drawn == null
              ? refusal.message()
              : drawn.theRefusalAsTheBoxesOnThisScreenAreNamed(refusal));
    }
    return VIEW;
  }

  private AccountForm.View describe(
      Selection.Chosen chosen,
      @Nullable String accountId,
      Map<String, String> submitted,
      Map<String, String> units,
      @Nullable String callbackUrl,
      @Nullable ConfigurationRefusal refusal,
      Model model) {
    SimulatorProvider module = chosen.module();
    TestRunId run = chosen.testRunId();
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    List<String> already = accounts.identitiesOf(module.id(), run);
    AccountForm.View drawn =
        AccountForm.of(
            chosen.provider(),
            run.value(),
            accountId,
            module.accountFields(),
            submitted,
            units,
            already,
            callbackUrl,
            callbackTargetsOf(module, run),
            refusal);
    model.addAttribute("account", drawn);
    return drawn;
  }
}
