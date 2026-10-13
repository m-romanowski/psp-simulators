package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.UnknownProviderObjectException;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookControl;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookNotSchedulableException;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.AttemptRefusedException;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
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
class DeliveryController {

  private static final String VIEW = "webhooks";

  private static final String SEND = "/ui/webhooks/send";

  private static final String RETRY = "/ui/webhooks/retry";

  private static final String DELIVER_DUE = "/ui/webhooks/deliver-due";

  private final ProviderRegistry providers;
  private final WebhookDispatcher deliveries;
  private final ProviderAccounts accounts;
  private final SimulatorClock clock;
  private final ActionTrail actions;
  private final TestRunId defaultTestRun;

  DeliveryController(
      ProviderRegistry providers,
      WebhookDispatcher deliveries,
      ProviderAccounts accounts,
      SimulatorClock clock,
      ActionTrail actions,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.deliveries = deliveries;
    this.accounts = accounts;
    this.clock = clock;
    this.actions = actions;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping("/ui/webhooks")
  String queue(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String open,
      @RequestParam(required = false) @Nullable String values,
      @RequestParam(required = false) @Nullable String providerObjectId,
      @RequestParam(required = false) @Nullable String eventType,
      @RequestParam(required = false) @Nullable String delayMs,
      @RequestParam(required = false) @Nullable String reuseEventId,
      @RequestParam(required = false) @Nullable String refusal,
      Model model) {
    List<String> known = providers.names();
    model.addAttribute("providers", known);
    model.addAttribute("path", "/ui/webhooks");
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("open", open);
    model.addAttribute("values", values);
    if (refusal != null) {
      model.addAttribute("refusal", refusal);
    }
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused unusable -> model.addAttribute("refusal", unusable.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen ->
          list(
              chosen,
              open,
              values,
              new Composed(providerObjectId, eventType, delayMs, reuseEventId),
              model);
    }
    return VIEW;
  }

  private record Composed(
      @Nullable String providerObjectId,
      @Nullable String eventType,
      @Nullable String delayMs,
      @Nullable String reuseEventId) {}

  @PostMapping("/ui/webhooks/send")
  String send(
      @RequestParam String provider,
      @RequestParam String testRunId,
      @RequestParam(required = false) @Nullable String providerObjectId,
      @RequestParam(required = false) @Nullable String eventType,
      @RequestParam(required = false) @Nullable String delayMs,
      @RequestParam(required = false) @Nullable String reuseEventId,
      RedirectAttributes attributes) {
    attributes.addAttribute("provider", provider);
    attributes.addAttribute("testRunId", testRunId);
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return "redirect:/ui/webhooks";
    }
    attributes.addAttribute("providerObjectId", providerObjectId);
    attributes.addAttribute("eventType", eventType);
    attributes.addAttribute("delayMs", delayMs);
    attributes.addAttribute("reuseEventId", reuseEventId);
    switch (DeliveryForm.composed(
        chosen.testRunId().value(), providerObjectId, eventType, delayMs, reuseEventId)) {
      case DeliveryForm.Composed.Refused refused -> {
        recordTheRefusal(
            chosen,
            SEND,
            refused.theRefusalItReports(),
            DeliveryForm.factsOfARefusedSend(providerObjectId, eventType, refused.message()));
        attributes.addAttribute("refusal", refused.message());
      }
      case DeliveryForm.Composed.Send asked -> scheduleIt(chosen, asked, attributes);
    }
    return "redirect:/ui/webhooks";
  }

  private void scheduleIt(
      Selection.Chosen chosen, DeliveryForm.Composed.Send asked, RedirectAttributes attributes) {
    List<String> jobIds;
    try {
      jobIds =
          WebhookControl.send(chosen.module(), chosen.testRunId(), asked.call(), ObjectOrigin.UI);
    } catch (IllegalArgumentException
        | WebhookNotSchedulableException
        | UnknownProviderObjectException refused) {
      String unschedulable = String.valueOf(refused.getMessage());
      recordTheRefusal(
          chosen,
          SEND,
          DeliveryForm.theRefusalOfAScheduling(refused),
          DeliveryForm.factsOfARefusedSend(
              asked.call().providerObjectId(), asked.call().eventType(), unschedulable));
      attributes.addAttribute("refusal", unschedulable);
      return;
    }
    if (jobIds.isEmpty()) {
      recordTheRefusal(
          chosen,
          SEND,
          DeliveryForm.theRefusalOfASendThatScheduledNothing(),
          DeliveryForm.factsOfARefusedSend(
              asked.call().providerObjectId(),
              asked.call().eventType(),
              ObjectScreen.NO_CALLBACK_WAS_SCHEDULED));
      attributes.addAttribute("refusal", ObjectScreen.NO_CALLBACK_WAS_SCHEDULED);
      return;
    }
    recordTheCallback(chosen, asked, jobIds);
    attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_CALLBACK_IS_SCHEDULED);
  }

  private void recordTheRefusal(
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

  private void recordTheCallback(
      Selection.Chosen chosen, DeliveryForm.Composed.Send asked, List<String> jobIds) {
    actions.record(
        AuditedAction.applied(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            "POST",
            SEND,
            ActionVerdict.applied(HttpStatus.FOUND.value()),
            asked.call().providerObjectId(),
            DeliveryForm.factsOf(asked.call(), jobIds)));
  }

  @PostMapping("/ui/webhooks/retry")
  String retry(
      @RequestParam String provider,
      @RequestParam String testRunId,
      @RequestParam String deliveryId,
      @RequestParam(required = false) @Nullable String targetUrl,
      RedirectAttributes attributes) {
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused _ -> {}
      case Selection.Unnamed _ -> {}
      case Selection.Chosen chosen -> attemptIt(chosen, deliveryId.strip(), targetUrl, attributes);
    }
    attributes.addAttribute("provider", provider);
    attributes.addAttribute("testRunId", testRunId);
    attributes.addAttribute("open", deliveryId);
    return "redirect:/ui/webhooks";
  }

  private void attemptIt(
      Selection.Chosen chosen,
      String deliveryId,
      @Nullable String targetUrl,
      RedirectAttributes attributes) {
    try {
      attemptItAt(chosen, deliveryId, targetUrl, attributes);
    } catch (AttemptRefusedException refused) {
      String message = String.valueOf(refused.getMessage());
      recordTheRefusal(
          chosen,
          RETRY,
          refused.theRefusalItReports(),
          DeliveryForm.factsOfARefusedRetry(deliveryId, targetUrl, message));
      attributes.addAttribute("refusal", message);
    }
  }

  private void attemptItAt(
      Selection.Chosen chosen,
      String deliveryId,
      @Nullable String targetUrl,
      RedirectAttributes attributes) {
    switch (DeliveryForm.postingTo(
        accounts.webhookTargetsOf(chosen.module().id(), chosen.testRunId()), targetUrl)) {
      case DeliveryForm.Posting.Refused refused -> {
        recordTheRefusal(
            chosen,
            RETRY,
            refused.theRefusalItReports(),
            DeliveryForm.factsOfARefusedRetry(deliveryId, targetUrl, refused.message()));
        attributes.addAttribute("refusal", refused.message());
      }
      case DeliveryForm.Posting.ToTheTargetItWasScheduledFor _ ->
          attempted(
              chosen,
              deliveries.retry(chosen.module().id(), chosen.testRunId(), deliveryId),
              deliveryId,
              null,
              attributes);
      case DeliveryForm.Posting.ToAConfiguredTarget elsewhere ->
          attempted(
              chosen,
              deliveries.retry(
                  chosen.module().id(), chosen.testRunId(), deliveryId, elsewhere.url()),
              deliveryId,
              elsewhere.url(),
              attributes);
    }
  }

  private void attempted(
      Selection.Chosen chosen,
      Optional<WebhookDelivery> retried,
      String deliveryId,
      @Nullable String targetUrl,
      RedirectAttributes attributes) {
    if (retried.isEmpty()) {
      recordTheRefusal(
          chosen,
          RETRY,
          DeliveryForm.theRefusalOfARetryOfNoDeliveryOfThisRun(),
          DeliveryForm.factsOfARefusedRetry(
              deliveryId, targetUrl, DeliveryForm.NO_DELIVERY_OF_THIS_RUN_ANSWERS_TO_THAT_ID));
      attributes.addAttribute("refusal", DeliveryForm.NO_DELIVERY_OF_THIS_RUN_ANSWERS_TO_THAT_ID);
      return;
    }
    recordTheRetry(chosen, retried.get(), targetUrl);
    attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_DELIVERY_WAS_ATTEMPTED);
  }

  @PostMapping("/ui/webhooks/deliver-due")
  String deliverDue(
      @RequestParam String provider,
      @RequestParam String testRunId,
      RedirectAttributes attributes) {
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused _ -> {}
      case Selection.Unnamed _ -> {}
      case Selection.Chosen chosen -> {
        recordTheRun(chosen);
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.EVERY_DUE_DELIVERY_WAS_ATTEMPTED);
      }
    }
    attributes.addAttribute("provider", provider);
    attributes.addAttribute("testRunId", testRunId);
    return "redirect:/ui/webhooks";
  }

  private void recordTheRetry(
      Selection.Chosen chosen, WebhookDelivery retried, @Nullable String targetUrl) {
    actions.record(
        AuditedAction.applied(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            "POST",
            RETRY,
            ActionVerdict.applied(HttpStatus.FOUND.value()),
            retried.providerObjectId(),
            DeliveryForm.factsOf(retried.deliveryId(), targetUrl)));
  }

  private void recordTheRun(Selection.Chosen chosen) {
    int attempted = deliveries.deliverDue(chosen.module().id(), chosen.testRunId());
    actions.record(
        AuditedAction.applied(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            "POST",
            DELIVER_DUE,
            ActionVerdict.applied(HttpStatus.FOUND.value()),
            ActionFacts.none().andCount("attempted", attempted).asRecorded()));
  }

  private void list(
      Selection.Chosen chosen,
      @Nullable String open,
      @Nullable String values,
      Composed composed,
      Model model) {
    TestRunId run = chosen.testRunId();
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    model.addAttribute(
        "theObjectsAnIdNames", chosen.module().vocabulary().theObjectsAnIdentifierAddresses());
    List<WebhookDelivery> queued = deliveries.of(chosen.module().id(), run);
    List<WebhookTarget> configured = accounts.webhookTargetsOf(chosen.module().id(), run);
    model.addAttribute(
        "queue",
        DeliveryScreen.queueOf(
            queued,
            clock.now(run),
            open,
            Disclosure.valuesAsked(values),
            deliveries::attemptsOf,
            configured,
            DeliveryForm.composerOf(
                queued,
                composed.providerObjectId(),
                composed.eventType(),
                composed.delayMs(),
                composed.reuseEventId())));
  }
}
