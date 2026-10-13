package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.audit.AuditPage;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDispatcher;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class TimelineController {

  private static final String VIEW = "timeline";

  private static final String PATH = "/ui/timeline";

  private static final int PAGES_ONE_TIMELINE_READS = 8;

  private final ProviderRegistry providers;
  private final AuditReads audit;
  private final WebhookDispatcher deliveries;
  private final TestRunId defaultTestRun;

  TimelineController(
      ProviderRegistry providers,
      AuditReads audit,
      WebhookDispatcher deliveries,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.audit = audit;
    this.deliveries = deliveries;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String timeline(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String key,
      @RequestParam(required = false) @Nullable String open,
      @RequestParam(required = false) @Nullable String values,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("key", key);
    model.addAttribute("open", open);
    model.addAttribute("values", values);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen -> trace(chosen, key, open, values, model);
    }
    return VIEW;
  }

  private void trace(
      Selection.Chosen chosen,
      @Nullable String declaredKey,
      @Nullable String open,
      @Nullable String values,
      Model model) {
    SimulatorProvider module = chosen.module();
    TestRunId testRunId = chosen.testRunId();
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", testRunId.value());
    model.addAttribute(
        "theObjectsAnIdNames", module.vocabulary().theObjectsAnIdentifierAddresses());

    String key = TimelineScreen.keyAsked(declaredKey);
    List<WebhookDelivery> callbacks = deliveries.of(module.id(), testRunId);
    KeyedExchangePage exchanges = exchangesOf(module, testRunId);
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            key,
            key == null ? null : AddressedProviderObject.read(module, testRunId, key).orElse(null),
            objectsOf(module, testRunId),
            callbacks,
            exchanges);

    ProviderObjectState state = null;
    if (subject instanceof TimelineScreen.Subject.Resolved resolved) {
      state = module.state(testRunId, resolved.providerObjectId()).orElse(null);
    }
    Trail trail = trailOf(module, testRunId);

    model.addAttribute(
        "timeline",
        TimelineScreen.of(
            chosen.provider(),
            testRunId.value(),
            key,
            subject,
            state,
            trail.records(),
            trail.truncated(),
            callbacks,
            exchanges,
            module.vocabulary(),
            module.keyMechanisms(),
            open,
            Disclosure.valuesAsked(values)));
  }

  private record Trail(List<AuditRecord> records, boolean truncated) {}

  private Trail trailOf(SimulatorProvider module, TestRunId testRunId) {
    List<AuditRecord> read = new ArrayList<>();
    String after = null;
    for (int page = 0; page < PAGES_ONE_TIMELINE_READS; page++) {
      AuditQuery query =
          new AuditQuery(
              testRunId,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(),
              null,
              null,
              null,
              after,
              AuditQuery.LARGEST_LIMIT);
      if (!(audit.pageOf(module.id(), query) instanceof AuditPage.Listed listed)) {
        return new Trail(read, true);
      }
      read.addAll(listed.records());
      if (listed.records().size() < AuditQuery.LARGEST_LIMIT) {
        return new Trail(read, false);
      }
      after = listed.records().get(listed.records().size() - 1).auditId();
    }
    return new Trail(read, true);
  }

  private static List<ProviderObjectState> objectsOf(
      SimulatorProvider module, TestRunId testRunId) {
    ProviderObjectQuery query =
        new ProviderObjectQuery(testRunId, null, ProviderObjectQuery.LARGEST_LIMIT);
    return module.objects(query) instanceof ProviderObjectPage.Listed listed
        ? listed.objects()
        : List.of();
  }

  private static KeyedExchangePage exchangesOf(SimulatorProvider module, TestRunId testRunId) {
    return module.keyedExchanges(
        new KeyedExchangeQuery(
            testRunId, KeyedExchangeQuery.FROM_THE_NEWEST, KeyedExchangeQuery.LARGEST_LIMIT));
  }
}
