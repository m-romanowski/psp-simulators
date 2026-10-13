package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.clock.DeclaredAdvance;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.TestRunSettings;
import dev.marcinromanowski.pspsim.kernel.spi.ClockAdvanceAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ClockAdvanceFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurationFacts;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfigurations;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.CallbackRetryPlans;
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
class ConfigurationController {

  private static final String VIEW = "configuration";

  private static final String PATH = "/ui/configuration";

  private final ProviderRegistry providers;
  private final ProviderAccounts accounts;
  private final ProviderConfigurations configured;
  private final ConfigurationAdmission configurations;
  private final ClockAdvanceAdmission clocks;
  private final FixedTimes fixedTimes;
  private final SimulatorClock clock;
  private final ActionTrail actions;
  private final CallbackRetryPlans retryPlans;
  private final DeclaredProviderSettings declaredSettings;
  private final TestRunSettings settings;
  private final String publicBaseUrl;
  private final String defaultTestRunId;
  private final TestRunId defaultTestRun;

  ConfigurationController(
      ProviderRegistry providers,
      ProviderAccounts accounts,
      ProviderConfigurations configured,
      ConfigurationAdmission configurations,
      ClockAdvanceAdmission clocks,
      FixedTimes fixedTimes,
      SimulatorClock clock,
      ActionTrail actions,
      CallbackRetryPlans retryPlans,
      DeclaredProviderSettings declaredSettings,
      TestRunSettings settings,
      @Value("${simulator.public-base-url}") String publicBaseUrl,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.accounts = accounts;
    this.configured = configured;
    this.configurations = configurations;
    this.clocks = clocks;
    this.fixedTimes = fixedTimes;
    this.clock = clock;
    this.actions = actions;
    this.retryPlans = retryPlans;
    this.declaredSettings = declaredSettings;
    this.settings = settings;
    this.publicBaseUrl = publicBaseUrl;
    this.defaultTestRunId = defaultTestRunId;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String configuration(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String open,
      @RequestParam(required = false) @Nullable String values,
      Model model) {
    return render(provider, testRunId, open, values, Map.of(), null, model);
  }

  @PostMapping(PATH + "/clock")
  String pinTheClock(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String fixedNow,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, null, null, Map.of(), null, model);
    }
    DeclaredConfiguration asked =
        ConfigurationScreen.clockChangeOf(chosen.testRunId().value(), fixedNow);
    ConfigurationAdmission.Verdict verdict = configurations.apply(chosen.module(), asked);
    switch (verdict) {
      case ConfigurationAdmission.Verdict.Rejected rejected -> {
        recordTheRefusal(
            chosen,
            PATH + "/clock",
            rejected.theRefusalItReports(),
            factsOf(chosen, asked),
            rejected.message());
        return render(provider, testRunId, null, null, Map.of(), rejected.message(), model);
      }
      case ConfigurationAdmission.Verdict.Refused refused -> {
        recordTheRefusal(
            chosen,
            PATH + "/clock",
            refused.theRefusalItReports(),
            factsOf(chosen, asked),
            refused.refusal().message());
        return render(
            provider, testRunId, null, null, Map.of(), refused.refusal().message(), model);
      }
      case ConfigurationAdmission.Verdict.Conflicting conflicting -> {
        recordTheRefusal(
            chosen,
            PATH + "/clock",
            conflicting.theRefusalItReports(),
            factsOf(chosen, asked),
            conflicting.refusal().message());
        return render(
            provider, testRunId, null, null, Map.of(), conflicting.refusal().message(), model);
      }
      case ConfigurationAdmission.Verdict.Applied applied -> {
        recordTheConfiguration(chosen, applied, PATH + "/clock", asked);
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_CLOCK_IS_PINNED);
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", applied.testRunId().value());
      }
    }
    return "redirect:" + PATH;
  }

  @PostMapping(PATH + "/clock/advance")
  String advanceTheClock(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String advanceBy,
      @RequestParam(name = ConfigurationScreen.THE_UNIT_AN_ADVANCE_IS_STATED_IN, required = false)
          @Nullable String unitPicked,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, null, null, Map.of(), null, model);
    }
    String path = PATH + "/clock/advance";
    String asTheSpanItNames = ConfigurationScreen.theSpanAnAdvanceNames(advanceBy, unitPicked);
    Map<String, List<String>> asked = ClockAdvanceFacts.asked(asTheSpanItNames);
    switch (clocks.advance(new DeclaredAdvance(chosen.testRunId().value(), asTheSpanItNames))) {
      case ClockAdvanceAdmission.Verdict.Rejected rejected -> {
        recordTheRefusal(chosen, path, rejected.theRefusalItReports(), asked, rejected.message());
        return render(provider, testRunId, null, null, Map.of(), rejected.message(), model);
      }
      case ClockAdvanceAdmission.Verdict.NotPinned following -> {
        recordTheRefusal(chosen, path, following.theRefusalItReports(), asked, following.message());
        return render(provider, testRunId, null, null, Map.of(), following.message(), model);
      }
      case ClockAdvanceAdmission.Verdict.Advanced advanced -> {
        actions.record(
            AuditedAction.applied(
                chosen.module().id(),
                advanced.testRunId(),
                ObjectOrigin.UI,
                "POST",
                path,
                ActionVerdict.applied(HttpStatus.FOUND.value()),
                ClockAdvanceFacts.of(advanced)));
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", advanced.testRunId().value());
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_CLOCK_HAS_MOVED_ON);
      }
    }
    return "redirect:" + PATH;
  }

  @PostMapping(PATH + "/route-alias")
  String bindTheRouteAlias(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String from,
      @RequestParam(required = false) @Nullable String to,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, null, null, Map.of(), null, model);
    }
    DeclaredConfiguration asked =
        ConfigurationScreen.routeAliasOf(chosen.testRunId().value(), from, to);
    ConfigurationAdmission.Verdict verdict = configurations.apply(chosen.module(), asked);
    switch (verdict) {
      case ConfigurationAdmission.Verdict.Rejected rejected -> {
        recordTheRefusal(
            chosen,
            PATH + "/route-alias",
            rejected.theRefusalItReports(),
            factsOf(chosen, asked),
            rejected.message());
        return render(provider, testRunId, null, null, Map.of(), rejected.message(), model);
      }
      case ConfigurationAdmission.Verdict.Refused refused -> {
        recordTheRefusal(
            chosen,
            PATH + "/route-alias",
            refused.theRefusalItReports(),
            factsOf(chosen, asked),
            refused.refusal().message());
        return render(
            provider, testRunId, null, null, Map.of(), refused.refusal().message(), model);
      }
      case ConfigurationAdmission.Verdict.Conflicting conflicting -> {
        recordTheRefusal(
            chosen,
            PATH + "/route-alias",
            conflicting.theRefusalItReports(),
            factsOf(chosen, asked),
            conflicting.refusal().message());
        return render(
            provider, testRunId, null, null, Map.of(), conflicting.refusal().message(), model);
      }
      case ConfigurationAdmission.Verdict.Applied applied -> {
        recordTheConfiguration(chosen, applied, PATH + "/route-alias", asked);
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_URL_IS_BOUND);
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", applied.testRunId().value());
      }
    }
    return "redirect:" + PATH;
  }

  @PostMapping(PATH + "/route-alias/release")
  String releaseTheRouteAlias(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String from,
      @RequestParam(required = false) @Nullable String confirm,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, null, null, Map.of(), null, model);
    }
    if (!ConfigurationScreen.REMOVAL_IS_CONFIRMED.equals(confirm)) {
      recordTheRefusal(
          chosen,
          PATH + "/route-alias/release",
          ConfigurationScreen.theRefusalOfAnUnconfirmedRelease(),
          ActionFacts.none()
              .and("from", from)
              .and(ActionFacts.REFUSED_BECAUSE, ConfigurationScreen.A_RELEASE_IS_CONFIRMED)
              .asRecorded(),
          ConfigurationScreen.A_RELEASE_IS_CONFIRMED);
      return render(
          provider,
          testRunId,
          null,
          null,
          Map.of(),
          ConfigurationScreen.A_RELEASE_IS_CONFIRMED,
          model);
    }
    switch (configurations.releaseRouteAlias(chosen.module(), chosen.testRunId().value(), from)) {
      case ConfigurationAdmission.Release.Rejected rejected -> {
        recordTheRefusal(
            chosen,
            PATH + "/route-alias/release",
            rejected.theRefusalItReports(),
            ActionFacts.none()
                .and("from", from)
                .and(ActionFacts.REFUSED_BECAUSE, rejected.message())
                .asRecorded(),
            rejected.message());
        return render(provider, testRunId, null, null, Map.of(), rejected.message(), model);
      }
      case ConfigurationAdmission.Release.NotBound absent -> {
        recordTheRefusal(
            chosen,
            PATH + "/route-alias/release",
            absent.theRefusalItReports(),
            ActionFacts.none()
                .and("from", from)
                .and(ActionFacts.REFUSED_BECAUSE, absent.message())
                .asRecorded(),
            absent.message());
        return render(provider, testRunId, null, null, Map.of(), absent.message(), model);
      }
      case ConfigurationAdmission.Release.Released released -> {
        actions.record(
            AuditedAction.applied(
                chosen.module().id(),
                released.testRunId(),
                ObjectOrigin.UI,
                "POST",
                PATH + "/route-alias/release",
                ActionVerdict.applied(HttpStatus.FOUND.value()),
                ActionFacts.none()
                    .and("releasedRouteAlias", released.from())
                    .andEach("configuredRouteAliases", released.routeAliases())
                    .asRecorded()));
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", released.testRunId().value());
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_URL_IS_RELEASED);
      }
    }
    return "redirect:" + PATH;
  }

  @PostMapping(PATH + "/settings")
  String setTheSettings(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam Map<String, String> parameters,
      Model model,
      RedirectAttributes attributes) {
    Map<String, String> submitted = SettingsForm.submittedSettingsOf(parameters);
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, null, null, submitted, null, model);
    }
    String path = PATH + "/settings";
    SimulatorProvider module = chosen.module();
    TestRunId run = chosen.testRunId();
    DeclaredConfiguration asked =
        SettingsForm.settingsOf(
            run.value(),
            declaredSettings.of(module.id()),
            settings.of(module.id(), run),
            submitted);
    switch (configurations.apply(module, asked)) {
      case ConfigurationAdmission.Verdict.Rejected rejected -> {
        recordTheRefusal(
            chosen,
            path,
            rejected.theRefusalItReports(),
            factsOf(chosen, asked),
            rejected.message());
        return render(provider, testRunId, null, null, submitted, rejected.message(), model);
      }
      case ConfigurationAdmission.Verdict.Refused refused -> {
        recordTheRefusal(
            chosen,
            path,
            refused.theRefusalItReports(),
            factsOf(chosen, asked),
            refused.refusal().message());
        return render(
            provider, testRunId, null, null, submitted, refused.refusal().message(), model);
      }
      case ConfigurationAdmission.Verdict.Conflicting conflicting -> {
        recordTheRefusal(
            chosen,
            path,
            conflicting.theRefusalItReports(),
            factsOf(chosen, asked),
            conflicting.refusal().message());
        return render(
            provider, testRunId, null, null, submitted, conflicting.refusal().message(), model);
      }
      case ConfigurationAdmission.Verdict.Applied applied -> {
        recordTheConfiguration(chosen, applied, path, asked);
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_SETTINGS_ARE_SET);
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", applied.testRunId().value());
      }
    }
    return "redirect:" + PATH;
  }

  @PostMapping(PATH + "/account/remove")
  String forgetTheAccount(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String confirm,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, null, null, Map.of(), null, model);
    }
    if (!ConfigurationScreen.REMOVAL_IS_CONFIRMED.equals(confirm)) {
      recordTheRefusal(
          chosen,
          PATH + "/account/remove",
          ConfigurationScreen.theRefusalOfAnUnconfirmedRemoval(),
          ActionFacts.none()
              .and("accountId", accountId)
              .and(ActionFacts.REFUSED_BECAUSE, ConfigurationScreen.A_REMOVAL_IS_CONFIRMED)
              .asRecorded(),
          ConfigurationScreen.A_REMOVAL_IS_CONFIRMED);
      return render(
          provider,
          testRunId,
          accountId,
          null,
          Map.of(),
          ConfigurationScreen.A_REMOVAL_IS_CONFIRMED,
          model);
    }
    switch (configurations.forget(chosen.module(), chosen.testRunId().value(), accountId)) {
      case ConfigurationAdmission.Removal.Rejected rejected -> {
        recordTheRefusal(
            chosen,
            PATH + "/account/remove",
            rejected.theRefusalItReports(),
            ActionFacts.none()
                .and("accountId", accountId)
                .and(ActionFacts.REFUSED_BECAUSE, rejected.message())
                .asRecorded(),
            rejected.message());
        return render(provider, testRunId, accountId, null, Map.of(), rejected.message(), model);
      }
      case ConfigurationAdmission.Removal.NotConfigured absent -> {
        recordTheRefusal(
            chosen,
            PATH + "/account/remove",
            absent.theRefusalItReports(),
            ActionFacts.none()
                .and("accountId", accountId)
                .and(ActionFacts.REFUSED_BECAUSE, absent.message())
                .asRecorded(),
            absent.message());
        return render(provider, testRunId, accountId, null, Map.of(), absent.message(), model);
      }
      case ConfigurationAdmission.Removal.Removed removed -> {
        actions.record(
            AuditedAction.applied(
                chosen.module().id(),
                removed.testRunId(),
                ObjectOrigin.UI,
                "POST",
                PATH + "/account/remove",
                ActionVerdict.applied(HttpStatus.FOUND.value()),
                ActionFacts.none()
                    .and("removedAccount", removed.accountId())
                    .andEach("configuredAccounts", removed.accountIds())
                    .asRecorded()));
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", removed.testRunId().value());
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.theAccountIsRemoved(removed.accountId()));
      }
    }
    return "redirect:" + PATH;
  }

  private void recordTheConfiguration(
      Selection.Chosen chosen,
      ConfigurationAdmission.Verdict.Applied applied,
      String path,
      DeclaredConfiguration asked) {
    actions.record(
        AuditedAction.applied(
            chosen.module().id(),
            applied.testRunId(),
            ObjectOrigin.UI,
            "POST",
            path,
            ActionVerdict.applied(HttpStatus.FOUND.value()),
            ConfigurationFacts.of(chosen.module().accountFields(), asked)));
  }

  private void recordTheRefusal(
      Selection.Chosen chosen,
      String path,
      ActionVerdict.Refused verdict,
      Map<String, List<String>> facts,
      String inItsOwnWords) {
    actions.record(
        AuditedAction.refusing(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            "POST",
            path,
            verdict,
            ActionFacts.beside(facts, ActionFacts.REFUSED_BECAUSE, inItsOwnWords)));
  }

  private static Map<String, List<String>> factsOf(
      Selection.Chosen chosen, DeclaredConfiguration asked) {
    return ConfigurationFacts.of(chosen.module().accountFields(), asked);
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String open,
      @Nullable String values,
      Map<String, String> submittedSettings,
      @Nullable String rejection,
      Model model) {
    List<String> known = providers.names();
    model.addAttribute("providers", known);
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("open", open);
    model.addAttribute("values", values);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen ->
          describe(chosen, open, values, submittedSettings, rejection, model);
    }
    if (rejection != null) {
      model.addAttribute("refusal", rejection);
    }
    return VIEW;
  }

  private void describe(
      Selection.Chosen chosen,
      @Nullable String open,
      @Nullable String values,
      Map<String, String> submittedSettings,
      @Nullable String rejection,
      Model model) {
    SimulatorProvider module = chosen.module();
    TestRunId run = chosen.testRunId();
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    model.addAttribute(
        "configuration",
        ConfigurationScreen.of(
            chosen.provider(),
            run.value(),
            accounts.identitiesOf(module.id(), run),
            module.accountFields(),
            accountId -> module.disclosureOf(run, accountId),
            open,
            Disclosure.valuesAsked(values),
            accounts.webhookTargetsOf(module.id(), run),
            configured.routeAliasesOf(module, run),
            fixedTimes.of(run).orElse(null),
            clock.now(run),
            ConfigurationScreen.deploymentOf(
                publicBaseUrl, defaultTestRunId, retryPlans, module.id(), run)));
    model.addAttribute(
        "settings",
        SettingsForm.of(
            chosen.provider(),
            run.value(),
            declaredSettings.of(module.id()),
            settings.of(module.id(), run),
            submittedSettings,
            rejection));
  }
}
