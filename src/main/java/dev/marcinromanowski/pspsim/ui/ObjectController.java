package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredWebhookCall;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpControl;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.FollowUpRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionControl;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionControl;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowACompletedHostedPageLeaves;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookControl;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
class ObjectController {

  private static final String VIEW = "objects";

  private static final String PATH = "/ui/objects";

  private static final String ACT = "/ui/objects/act";

  private static final String COMPLETE = "/ui/objects/complete";

  private static final String WEBHOOK = "/ui/objects/webhook";

  private static final String FOLLOW_UP = "/ui/objects/follow-up";

  private final ProviderRegistry providers;
  private final ActionTrail actions;
  private final ScenarioRules scenarios;
  private final SimulatorClock clock;
  private final TheRail rail;
  private final TheScreenAFormIsAnsweredOn answers;
  private final TestRunId defaultTestRun;

  ObjectController(
      ProviderRegistry providers,
      ActionTrail actions,
      ScenarioRules scenarios,
      SimulatorClock clock,
      TheRail rail,
      TheScreenAFormIsAnsweredOn answers,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.actions = actions;
    this.scenarios = scenarios;
    this.clock = clock;
    this.rail = rail;
    this.answers = answers;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String object(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String providerObjectId,
      @RequestParam(required = false) @Nullable String after,
      @RequestParam(required = false) @Nullable String limit,
      Model model) {
    return render(provider, testRunId, providerObjectId, after, limit, null, model);
  }

  @PostMapping(ACT)
  String act(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String providerObjectId,
      @RequestParam(required = false) @Nullable String action,
      @RequestParam(required = false) @Nullable String amount,
      @RequestParam(required = false) @Nullable String confirm,
      @RequestParam(required = false) @Nullable String returnTo,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, providerObjectId, null, null, null, model);
    }
    WhatWasTyped typed = ObjectScreen.theAmountTypedFor(action, amount);
    if (!ObjectScreen.A_CHANGE_IS_CONFIRMED.equals(confirm)) {
      record(
          chosen,
          ACT,
          ObjectScreen.theRefusalOfAnUnconfirmedChange(),
          ObjectScreen.factsOf(action, amount, ObjectScreen.CONFIRM_BEFORE_MOVING_MONEY));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          ObjectScreen.CONFIRM_BEFORE_MOVING_MONEY,
          chosen,
          typed,
          attributes,
          () ->
              render(
                  provider,
                  testRunId,
                  providerObjectId,
                  null,
                  null,
                  ObjectScreen.CONFIRM_BEFORE_MOVING_MONEY,
                  typed,
                  model));
    }
    ObjectAction asked;
    ObjectActionRequest request;
    try {
      asked = ObjectAction.declared(action == null ? "" : action);
      request =
          ObjectActionRequest.of(
              chosen.testRunId(),
              providerObjectId == null ? "" : providerObjectId,
              asked,
              amount,
              ObjectOrigin.UI);
    } catch (IllegalArgumentException rejected) {
      String unreadable = String.valueOf(rejected.getMessage());
      record(
          chosen,
          ACT,
          ObjectScreen.theRefusalOfAnActionThisSimulatorCannotRead(),
          ObjectScreen.factsOf(action, amount, unreadable));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          unreadable,
          chosen,
          typed,
          attributes,
          () ->
              render(provider, testRunId, providerObjectId, null, null, unreadable, typed, model));
    }
    ObjectActionOutcome outcome = ObjectActionControl.perform(chosen.module(), request);
    ActionVerdict.Refused turnedAway =
        ObjectScreen.theRefusalItReports(
            outcome, request.providerObjectId(), holds(chosen, request.providerObjectId()));
    if (turnedAway != null) {
      String refusal = String.valueOf(ObjectScreen.refusalOf(outcome));
      recordTheRefusal(chosen, request, outcome, turnedAway, refusal);
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          refusal,
          chosen,
          typed,
          attributes,
          () -> render(provider, testRunId, providerObjectId, null, null, refusal, typed, model));
    }
    recordTheAction(chosen, request, (ObjectActionOutcome.Applied) outcome);
    attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_PROVIDER_PERFORMED_THE_ACTION);
    attributes.addAttribute("provider", chosen.provider());
    attributes.addAttribute("testRunId", chosen.testRunId().value());
    attributes.addAttribute("providerObjectId", request.providerObjectId());
    return "redirect:" + rail.theScreenToReturnTo(returnTo, PATH);
  }

  @PostMapping(COMPLETE)
  String finishTheHostedPage(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String providerObjectId,
      @RequestParam(required = false) @Nullable String outcome,
      @RequestParam(required = false) @Nullable String card,
      @RequestParam(required = false) @Nullable String confirm,
      @RequestParam(required = false) @Nullable String returnTo,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, providerObjectId, null, null, null, model);
    }
    String named = providerObjectId == null ? "" : providerObjectId.strip();
    HostedCompletionOffer offered = chosen.module().hostedCompletion(chosen.testRunId(), named);
    WhatWasTyped chose = WhatWasTyped.thePayerStepChosenFor(named, outcome, card);
    if (!ObjectScreen.A_CHANGE_IS_CONFIRMED.equals(confirm)) {
      String caution = HostedCompletionForm.theCautionBefore(offered);
      record(
          chosen,
          COMPLETE,
          HostedCompletionForm.theRefusalBeforeTakingOne(offered),
          HostedCompletionForm.theFactsOfACompletionThisScreenTurnedAway(outcome, card, caution));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          caution,
          chosen,
          chose,
          attributes,
          () -> render(provider, testRunId, providerObjectId, null, null, caution, chose, model));
    }
    HostedCompletionRequest request;
    try {
      request =
          HostedCompletionControl.asked(
              offered, chosen.testRunId(), named, outcome, card, ObjectOrigin.UI);
    } catch (IllegalArgumentException refused) {
      String unreadable = String.valueOf(refused.getMessage());
      actions.record(
          TheTrailRowACompletedHostedPageLeaves.ofACompletionTurnedAwayBeforeTheProviderWasReached(
              chosen.module().id(),
              chosen.testRunId(),
              ObjectOrigin.UI,
              COMPLETE,
              named,
              outcome,
              card,
              unreadable));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          unreadable,
          chosen,
          chose,
          attributes,
          () ->
              render(provider, testRunId, providerObjectId, null, null, unreadable, chose, model));
    }
    HostedCompletionOutcome completion = chosen.module().complete(request);
    actions.record(
        TheTrailRowACompletedHostedPageLeaves.of(
            chosen.module().id(),
            COMPLETE,
            request,
            completion,
            HostedCompletionForm.THE_STATUS_THIS_SCREEN_ANSWERS_A_COMPLETED_PAGE_WITH));
    if (completion.theRefusalItReports() != null) {
      String refusal = String.valueOf(HostedCompletionForm.refusalOf(completion));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          refusal,
          chosen,
          chose,
          attributes,
          () -> render(provider, testRunId, providerObjectId, null, null, refusal, chose, model));
    }
    HostedCompletionOutcome.Completed completed = (HostedCompletionOutcome.Completed) completion;
    attributes.addFlashAttribute(
        Done.ATTRIBUTE, HostedCompletionForm.thePayerStepTaken(offered, completed));
    attributes.addAttribute("provider", chosen.provider());
    attributes.addAttribute("testRunId", chosen.testRunId().value());
    attributes.addAttribute("providerObjectId", request.providerObjectId());
    return "redirect:" + rail.theScreenToReturnTo(returnTo, PATH);
  }

  @PostMapping(WEBHOOK)
  String sendACallback(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String providerObjectId,
      @RequestParam(required = false) @Nullable String eventType,
      @RequestParam(required = false) @Nullable String confirm,
      @RequestParam(required = false) @Nullable String returnTo,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, providerObjectId, null, null, null, model);
    }
    WhatWasTyped typed = DeliveryForm.theEventTypeTypedIn(eventType);
    if (!ObjectScreen.A_CHANGE_IS_CONFIRMED.equals(confirm)) {
      record(
          chosen,
          WEBHOOK,
          DeliveryForm.theRefusalOfAnUnconfirmedCallback(),
          DeliveryForm.factsOfARefusedSend(
              providerObjectId, eventType, ObjectScreen.CONFIRM_BEFORE_SENDING_A_CALLBACK));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          ObjectScreen.CONFIRM_BEFORE_SENDING_A_CALLBACK,
          chosen,
          typed,
          attributes,
          () ->
              render(
                  provider,
                  testRunId,
                  providerObjectId,
                  null,
                  null,
                  ObjectScreen.CONFIRM_BEFORE_SENDING_A_CALLBACK,
                  typed,
                  model));
    }
    List<String> jobIds;
    try {
      jobIds =
          WebhookControl.send(
              chosen.module(),
              chosen.testRunId(),
              new DeclaredWebhookCall(
                  chosen.testRunId().value(),
                  null,
                  providerObjectId,
                  eventType,
                  null,
                  null,
                  null,
                  null,
                  null),
              ObjectOrigin.UI);
    } catch (IllegalArgumentException
        | WebhookNotSchedulableException
        | UnknownProviderObjectException refused) {
      String unschedulable = String.valueOf(refused.getMessage());
      record(
          chosen,
          WEBHOOK,
          DeliveryForm.theRefusalOfAScheduling(refused),
          DeliveryForm.factsOfARefusedSend(providerObjectId, eventType, unschedulable));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          unschedulable,
          chosen,
          typed,
          attributes,
          () ->
              render(
                  provider, testRunId, providerObjectId, null, null, unschedulable, typed, model));
    }
    if (jobIds.isEmpty()) {
      record(
          chosen,
          WEBHOOK,
          DeliveryForm.theRefusalOfASendThatScheduledNothing(),
          DeliveryForm.factsOfARefusedSend(
              providerObjectId, eventType, ObjectScreen.NO_CALLBACK_WAS_SCHEDULED));
      return answers.aRefusalIsDrawnOn(
          returnTo,
          PATH,
          ObjectScreen.NO_CALLBACK_WAS_SCHEDULED,
          chosen,
          typed,
          attributes,
          () ->
              render(
                  provider,
                  testRunId,
                  providerObjectId,
                  null,
                  null,
                  ObjectScreen.NO_CALLBACK_WAS_SCHEDULED,
                  typed,
                  model));
    }
    String named = String.valueOf(providerObjectId).strip();
    recordTheCallback(chosen, named, eventType, jobIds);
    attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_CALLBACK_IS_SCHEDULED);
    attributes.addAttribute("provider", chosen.provider());
    attributes.addAttribute("testRunId", chosen.testRunId().value());
    attributes.addAttribute("providerObjectId", named);
    return "redirect:" + rail.theScreenToReturnTo(returnTo, PATH);
  }

  @PostMapping(FOLLOW_UP)
  String followUp(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String providerObjectId,
      @RequestParam(required = false) @Nullable String followUp,
      @RequestParam(required = false) @Nullable String confirm,
      @RequestParam(required = false) @Nullable String returnTo,
      Model model,
      RedirectAttributes attributes) {
    return switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Chosen chosen ->
          switch (FollowUpForm.asked(chosen.testRunId(), providerObjectId, followUp, confirm)) {
            case FollowUpForm.Asking.NotAsked notAsked -> {
              record(
                  chosen,
                  FOLLOW_UP,
                  FollowUpForm.theRejectionOfAFollowUpNeverAsked(),
                  FollowUpForm.factsOf(
                      FollowUpForm.theObjectItNamed(providerObjectId), followUp, notAsked.why()));
              yield answers.aRefusalIsDrawnOn(
                  returnTo,
                  PATH,
                  notAsked.why(),
                  chosen,
                  WhatWasTyped.nothing(),
                  attributes,
                  () ->
                      render(
                          provider,
                          testRunId,
                          providerObjectId,
                          null,
                          null,
                          notAsked.why(),
                          model));
            }
            case FollowUpForm.Asking.Asked asked ->
                perform(chosen, asked.request(), returnTo, model, attributes);
          };
      default -> render(provider, testRunId, providerObjectId, null, null, null, model);
    };
  }

  private String perform(
      Selection.Chosen chosen,
      FollowUpRequest request,
      @Nullable String returnTo,
      Model model,
      RedirectAttributes attributes) {
    return switch (FollowUpControl.perform(
        chosen.module(), request, clock.now(chosen.testRunId()))) {
      case FollowUpOutcome.Refused refused -> {
        record(
            chosen,
            FOLLOW_UP,
            refused.verdict(),
            FollowUpForm.factsOf(
                request.providerObjectId(), request.name(), refused.inItsOwnWords()));
        yield answers.aRefusalIsDrawnOn(
            returnTo,
            PATH,
            refused.inItsOwnWords(),
            chosen,
            WhatWasTyped.nothing(),
            attributes,
            () ->
                render(
                    chosen.provider(),
                    chosen.testRunId().value(),
                    request.providerObjectId(),
                    null,
                    null,
                    refused.inItsOwnWords(),
                    model));
      }
      case FollowUpOutcome.Performed performed -> {
        actions.record(
            AuditedAction.applied(
                chosen.module().id(),
                chosen.testRunId(),
                ObjectOrigin.UI,
                "POST",
                FOLLOW_UP,
                ActionVerdict.applied(HttpStatus.FOUND.value()),
                performed.after().canonical(),
                FollowUpForm.factsOf(performed)));
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_FOLLOW_UP_IS_PERFORMED);
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", chosen.testRunId().value());
        attributes.addAttribute("providerObjectId", request.providerObjectId());
        yield "redirect:" + rail.theScreenToReturnTo(returnTo, PATH);
      }
    };
  }

  private boolean holds(Selection.Chosen chosen, String providerObjectId) {
    return chosen.module().state(chosen.testRunId(), providerObjectId).isPresent();
  }

  private void record(
      Selection.Chosen chosen,
      String path,
      ActionVerdict.Refused verdict,
      Map<String, List<String>> facts) {
    actions.record(
        AuditedAction.refusing(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            "POST",
            path,
            verdict,
            facts));
  }

  private void recordTheAction(
      Selection.Chosen chosen, ObjectActionRequest request, ObjectActionOutcome.Applied applied) {
    actions.record(
        AuditedAction.applied(
                chosen.module().id(),
                chosen.testRunId(),
                ObjectOrigin.UI,
                "POST",
                ACT,
                ActionVerdict.applied(HttpStatus.FOUND.value()),
                request.providerObjectId(),
                ObjectScreen.factsOf(request, applied))
            .decidedBy(applied.scenarioId()));
  }

  private void recordTheRefusal(
      Selection.Chosen chosen,
      ObjectActionRequest request,
      ObjectActionOutcome outcome,
      ActionVerdict.Refused verdict,
      String refusal) {
    actions.record(
        AuditedAction.refusing(
                chosen.module().id(),
                chosen.testRunId(),
                ObjectOrigin.UI,
                "POST",
                ACT,
                verdict,
                ObjectScreen.factsOf(request, refusal))
            .decidedBy(outcome.theRuleThatDecidedIt()));
  }

  private void recordTheCallback(
      Selection.Chosen chosen,
      String providerObjectId,
      @Nullable String eventType,
      List<String> jobIds) {
    actions.record(
        AuditedAction.applied(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            "POST",
            WEBHOOK,
            ActionVerdict.applied(HttpStatus.FOUND.value()),
            providerObjectId,
            ActionFacts.none()
                .and("operation", WebhookControl.Operation.SEND.name())
                .and("eventType", eventType)
                .andEach("webhookJobIds", jobIds)
                .asRecorded()));
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String providerObjectId,
      @Nullable String after,
      @Nullable String limit,
      @Nullable String refusal,
      Model model) {
    return render(
        provider,
        testRunId,
        providerObjectId,
        after,
        limit,
        refusal,
        ContributedPage.whatTheTesterTyped(model),
        model);
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String providerObjectId,
      @Nullable String after,
      @Nullable String limit,
      @Nullable String refusal,
      WhatWasTyped typed,
      Model model) {
    List<String> known = providers.names();
    model.addAttribute("providers", known);
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("providerObjectId", providerObjectId);
    model.addAttribute("after", after);
    model.addAttribute("limit", limit);
    model.addAttribute(WhatWasTyped.ATTRIBUTE, typed);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen -> read(chosen, providerObjectId, after, limit, typed, model);
    }
    if (refusal != null) {
      model.addAttribute("refusal", refusal);
    }
    return VIEW;
  }

  private void read(
      Selection.Chosen chosen,
      @Nullable String providerObjectId,
      @Nullable String after,
      @Nullable String limit,
      WhatWasTyped typed,
      Model model) {
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", chosen.testRunId().value());
    if (!list(chosen, after, limit, model)) {
      return;
    }
    look(chosen, providerObjectId, typed, model);
  }

  private boolean list(
      Selection.Chosen chosen, @Nullable String after, @Nullable String limit, Model model) {
    ProviderObjectQuery query;
    try {
      query =
          ProviderObjectQuery.of(
              new DeclaredObjectQuery(chosen.testRunId().value(), after, limit), defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      model.addAttribute("refusal", String.valueOf(rejected.getMessage()));
      return false;
    }
    switch (chosen.module().objects(query)) {
      case ProviderObjectPage.NoSuchCursor _ -> {
        model.addAttribute(
            "refusal",
            "after must name an object of this provider and test run; the run may have been"
                + " reset while this page was open");
        return false;
      }
      case ProviderObjectPage.Listed listed ->
          model.addAttribute(
              "listing",
              ObjectScreen.pageOf(listed.objects(), query.limit(), clock.now(chosen.testRunId())));
    }
    return true;
  }

  private void look(
      Selection.Chosen chosen, @Nullable String providerObjectId, WhatWasTyped typed, Model model) {
    if (providerObjectId == null || providerObjectId.isBlank()) {
      return;
    }
    String named = providerObjectId.strip();
    Optional<AddressedProviderObject> addressed =
        AddressedProviderObject.read(chosen.module(), chosen.testRunId(), named);
    if (addressed.isEmpty()) {
      model.addAttribute("refusal", ObjectScreen.THE_RUN_HOLDS_NO_SUCH_OBJECT);
      return;
    }
    ObjectScreen.View object =
        ObjectScreen.of(
            chosen.provider(),
            chosen.testRunId(),
            addressed.get(),
            chosen.module().objectActions(),
            scenarios.of(chosen.module().id(), chosen.testRunId()),
            clock.now(chosen.testRunId()));
    List<FollowUpForm.Offered> followUps =
        FollowUpForm.of(
            chosen
                .module()
                .followUps(
                    chosen.testRunId(),
                    addressed.get().canonical(),
                    clock.now(chosen.testRunId())));
    HostedCompletionForm.View completion =
        HostedCompletionForm.of(
            chosen.provider(),
            chosen.testRunId().value(),
            named,
            chosen.module().hostedCompletion(chosen.testRunId(), named),
            typed.theStepChosenFor(named),
            typed.theCardChosenFor(named));
    model.addAttribute("object", object);
    model.addAttribute("followUps", followUps);
    model.addAttribute("completion", completion);
    model.addAttribute("stillMoves", ObjectScreen.whatStillMovesIt(object, followUps, completion));
    model.addAttribute(
        "named",
        ObjectScreen.theObjectsItNames(
            object, ids -> chosen.module().states(chosen.testRunId(), ids)));
  }
}
