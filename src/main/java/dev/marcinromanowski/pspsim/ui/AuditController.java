package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.audit.AuditPage;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.DeclaredAuditQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectAddressing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class AuditController {

  private static final String VIEW = "audit";

  private final ProviderRegistry providers;
  private final AuditReads audit;
  private final TestRunId defaultTestRun;

  AuditController(
      ProviderRegistry providers,
      AuditReads audit,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.audit = audit;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping("/ui/audit")
  String trail(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String auditId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String origin,
      @RequestParam(required = false) @Nullable String direction,
      @RequestParam(required = false) @Nullable String method,
      @RequestParam(required = false) @Nullable String status,
      @RequestParam(required = false) @Nullable String providerObjectId,
      @RequestParam(required = false) @Nullable String scenarioId,
      @RequestParam(required = false) @Nullable String from,
      @RequestParam(required = false) @Nullable String until,
      @RequestParam(required = false) @Nullable String after,
      @RequestParam(required = false) @Nullable String limit,
      @RequestParam(required = false) @Nullable String open,
      @RequestParam(required = false) @Nullable String values,
      Model model) {
    List<String> known = providers.names();
    model.addAttribute("providers", known);
    model.addAttribute("path", "/ui/audit");
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute(
        "filters",
        new Filters(
            auditId,
            accountId,
            origin,
            direction,
            method,
            status,
            providerObjectId,
            scenarioId,
            from,
            until,
            limit));
    model.addAttribute("after", AuditScreen.theCursorARelinkShouldCarry(after));
    model.addAttribute("open", open);
    model.addAttribute("values", values);
    switch (Selection.ofAScreenWhoseSubjectIsTheTrail(
        provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen ->
          read(
              chosen,
              new DeclaredAuditQuery(
                  chosen.testRunId().value(),
                  auditId,
                  accountId,
                  origin,
                  direction,
                  method,
                  status,
                  providerObjectId,
                  scenarioId,
                  from,
                  until,
                  after,
                  limit),
              open,
              values,
              model);
    }
    return VIEW;
  }

  private void read(
      Selection.Chosen chosen,
      DeclaredAuditQuery declared,
      @Nullable String open,
      @Nullable String values,
      Model model) {
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", chosen.testRunId().value());
    model.addAttribute(
        "theReservedRun",
        TheReservedRunOnAScreen.offeredBeside(
            chosen.testRunId(),
            defaultTestRun,
            TheReservedRunOnAScreen.Reader.WHY_EACH_REQUEST_LANDED_HERE));
    model.addAttribute(
        "theObjectsAnIdNames", chosen.module().vocabulary().theObjectsAnIdentifierAddresses());
    AuditQuery query;
    try {
      query =
          ProviderObjectAddressing.overEveryIdentifierOfTheSameObject(
              chosen.module(), AuditQuery.of(declared, defaultTestRun));
    } catch (IllegalArgumentException rejected) {
      model.addAttribute("refusal", String.valueOf(rejected.getMessage()));
      return;
    }
    switch (audit.pageOf(chosen.module().id(), query)) {
      case AuditPage.NoSuchCursor _ ->
          model.addAttribute(
              "refusal",
              "after must name an audit record of this provider and test run; the trail may have"
                  + " been reset while this page was open");
      case AuditPage.Listed listed ->
          model.addAttribute(
              "page",
              AuditScreen.pageOf(listed.records(), query, open, Disclosure.valuesAsked(values)));
    }
  }
}
