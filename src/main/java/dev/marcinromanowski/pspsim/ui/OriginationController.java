package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationControl;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TheParameterATestersValueArrivesUnder;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAStartedPaymentLeaves;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class OriginationController {

  private static final String VIEW = "origination";

  private static final String PATH = "/ui/objects/new";

  private static final String START = "/ui/objects/start";

  private final ProviderRegistry providers;
  private final ProviderAccounts accounts;
  private final ActionTrail actions;
  private final TheScreenAFormIsAnsweredOn answers;
  private final TestRunId defaultTestRun;

  OriginationController(
      ProviderRegistry providers,
      ProviderAccounts accounts,
      ActionTrail actions,
      TheScreenAFormIsAnsweredOn answers,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.accounts = accounts;
    this.actions = actions;
    this.answers = answers;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String wizard(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String startedAs,
      @RequestParam(required = false) @Nullable String started,
      Model model) {
    return render(provider, testRunId, accountId, startedAs, Map.of(), started, null, model);
  }

  @PostMapping(START)
  String startThePayment(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String startedAs,
      @RequestParam(required = false) @Nullable String returnTo,
      @RequestParam Map<String, String> parameters,
      Model model,
      RedirectAttributes attributes) {
    Map<String, String> submitted =
        TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(parameters);
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, accountId, startedAs, submitted, null, null, model);
    }
    SimulatorProvider module = chosen.module();
    if (accounts.identitiesOf(module.id(), chosen.testRunId()).isEmpty()) {
      recordTheRefusal(
          chosen,
          accountId,
          OriginationForm.theRefusalOfARunThatConfiguresNoAccount(),
          OriginationForm.theFactsOfARequestTurnedAway(
              accountId, startedAs, OriginationForm.THE_RUN_CONFIGURES_NO_ACCOUNT));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          OriginationForm.THE_RUN_CONFIGURES_NO_ACCOUNT,
          chosen,
          WhatWasTyped.theFieldsOf(submitted),
          attributes,
          () ->
              render(
                  provider,
                  testRunId,
                  accountId,
                  startedAs,
                  submitted,
                  null,
                  OriginationForm.THE_RUN_CONFIGURES_NO_ACCOUNT,
                  model));
    }
    OriginationRequest request;
    try {
      request =
          OriginationControl.asked(
              module.originationFields(),
              chosen.testRunId(),
              accountId == null ? "" : accountId,
              startedAs == null ? "" : startedAs,
              submitted,
              callbackTargetsOf(chosen),
              ObjectOrigin.UI);
    } catch (IllegalArgumentException refused) {
      String unreadable = String.valueOf(refused.getMessage());
      recordTheRefusal(
          chosen,
          accountId,
          OriginationForm.theRefusalOfAnOriginationThisSimulatorCannotRead(),
          OriginationForm.theFactsOfARequestTurnedAway(accountId, startedAs, unreadable));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          unreadable,
          chosen,
          WhatWasTyped.theFieldsOf(submitted),
          attributes,
          () ->
              render(
                  provider, testRunId, accountId, startedAs, submitted, null, unreadable, model));
    }
    OriginationOutcome outcome = module.originate(request);
    ActionVerdict.Refused turnedAway = OriginationForm.theRefusalItReports(outcome);
    if (turnedAway != null) {
      record(chosen, request, outcome);
      String inItsOwnWords = String.valueOf(OriginationForm.refusalOf(outcome));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          inItsOwnWords,
          chosen,
          WhatWasTyped.theFieldsOf(submitted),
          attributes,
          () ->
              render(
                  provider,
                  testRunId,
                  accountId,
                  startedAs,
                  submitted,
                  null,
                  inItsOwnWords,
                  model));
    }
    OriginationOutcome.Originated started = (OriginationOutcome.Originated) outcome;
    record(chosen, request, outcome);
    attributes.addAttribute("provider", chosen.provider());
    attributes.addAttribute("testRunId", chosen.testRunId().value());
    attributes.addAttribute("accountId", request.accountId());
    attributes.addAttribute("startedAs", request.startedAs());
    attributes.addAttribute("started", started.providerObjectId());
    return answers.aStartedPaymentIsAnnouncedOn(
        returnTo, PATH, chosen.provider(), started.providerObjectId(), attributes);
  }

  private void record(
      Selection.Chosen chosen, OriginationRequest request, OriginationOutcome outcome) {
    actions.record(
        TheTrailRowAStartedPaymentLeaves.of(
            chosen.module().id(),
            START,
            request,
            outcome,
            OriginationForm.THE_STATUS_THIS_SCREEN_ANSWERS_A_STARTED_PAYMENT_WITH));
  }

  private void recordTheRefusal(
      Selection.Chosen chosen,
      @Nullable String accountId,
      ActionVerdict.Refused verdict,
      Map<String, List<String>> facts) {
    actions.record(
        AuditedAction.refusing(
                chosen.module().id(),
                chosen.testRunId(),
                ObjectOrigin.UI,
                "POST",
                START,
                verdict,
                facts)
            .theAccountItActedFor(accountId));
  }

  private List<String> callbackTargetsOf(Selection.Chosen chosen) {
    List<String> urls = new ArrayList<>();
    for (WebhookTarget target :
        accounts.webhookTargetsOf(chosen.module().id(), chosen.testRunId())) {
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
      @Nullable String startedAs,
      Map<String, String> submitted,
      @Nullable String started,
      @Nullable String refusal,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused unusable -> model.addAttribute("refusal", unusable.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen ->
          describe(chosen, accountId, startedAs, submitted, started, model);
    }
    if (refusal != null) {
      model.addAttribute("refusal", refusal);
    }
    return VIEW;
  }

  private void describe(
      Selection.Chosen chosen,
      @Nullable String accountId,
      @Nullable String startedAs,
      Map<String, String> submitted,
      @Nullable String started,
      Model model) {
    SimulatorProvider module = chosen.module();
    TestRunId run = chosen.testRunId();
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    model.addAttribute(
        "wizard",
        OriginationForm.of(
            chosen.provider(),
            run.value(),
            accountId,
            startedAs,
            module.originationFields(),
            accounts.identitiesOf(module.id(), run),
            callbackTargetsOf(chosen),
            OriginationControl.theValuesThisTestRunHasAlreadyProduced(
                module.originationFields(),
                accountId,
                startedAs,
                (account, key) ->
                    module.theValuesThisTestRunHasAlreadyProducedFor(run, account, key)),
            submitted,
            started));
  }
}
