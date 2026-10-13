package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleReport;
import dev.marcinromanowski.pspsim.kernel.bundle.RunBundle;
import dev.marcinromanowski.pspsim.kernel.spi.BundleAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredBundleImport;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.RunBundles;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.ObjectMapper;

@Controller
class RunBundleController {

  private static final String VIEW = "run-bundle";

  private static final String PATH = "/ui/run-bundle";

  private final ProviderRegistry providers;
  private final RunBundles exports;
  private final BundleAdmission bundles;
  private final ActionTrail actions;
  private final ObjectMapper json;
  private final TestRunId defaultTestRun;

  RunBundleController(
      ProviderRegistry providers,
      RunBundles exports,
      BundleAdmission bundles,
      ActionTrail actions,
      ObjectMapper json,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.exports = exports;
    this.bundles = bundles;
    this.actions = actions;
    this.json = json;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String bundle(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      Model model) {
    return render(provider, testRunId, null, List.of(), null, null, model);
  }

  @PostMapping(PATH + "/import")
  String takeBack(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String into,
      @RequestParam(required = false) @Nullable String document,
      @RequestParam(required = false) @Nullable String secrets,
      @RequestParam(required = false) @Nullable String dryRun,
      Model model) {
    Optional<Selection.Chosen> chosen = chosen(provider, testRunId);
    if (chosen.isEmpty()) {
      return render(provider, testRunId, into, List.of(), null, null, model);
    }
    Selection.Chosen taking = chosen.get();
    TheTrailRowAnImportOnAScreenLeaves row =
        TheTrailRowAnImportOnAScreenLeaves.of(taking, into, dryRun != null, PATH + "/import");

    RunBundleScreen.Supplied supplied =
        RunBundleScreen.theSecretsTypedInto(secrets, taking.module().accountFields());
    if (supplied instanceof RunBundleScreen.Supplied.Unreadable unreadable) {
      recordTheRefusal(row, theFactsOf(row, supplied), unreadable.message());
      return render(provider, testRunId, into, List.of(), unreadable.message(), null, model);
    }

    RunBundleScreen.Pasted pasted = RunBundleScreen.pastedInto(document, this::bundleOf);
    if (pasted instanceof RunBundleScreen.Pasted.Unreadable unreadable) {
      recordTheRefusal(row, theFactsOf(row, supplied), unreadable.message());
      return render(provider, testRunId, into, List.of(), unreadable.message(), null, model);
    }

    Map<String, Map<String, String>> byAccount =
        ((RunBundleScreen.Supplied.Secrets) supplied).byAccount();
    DeclaredBundleImport declared =
        new DeclaredBundleImport(
            row.theRunItWritesInto(),
            row.itWritesNothing(),
            ((RunBundleScreen.Pasted.Bundle) pasted).bundle(),
            byAccount);

    return switch (bundles.takeBack(taking.module(), declared)) {
      case BundleAdmission.Verdict.Rejected rejected -> {
        recordTheRefusal(
            row, rejected.theRefusalItReports(), theFactsOf(row, supplied), rejected.message());
        yield render(provider, testRunId, into, List.of(), rejected.message(), null, model);
      }
      case BundleAdmission.Verdict.Refused refused -> {
        recordTheRefusal(
            row,
            refused.theRefusalItReports(),
            theFactsOf(row, supplied)
                .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(refused.refusals())),
            BundleRefusal.theWordsOf(refused.refusals()));
        yield render(provider, testRunId, into, refused.refusals(), null, null, model);
      }
      case BundleAdmission.Verdict.Conflicting conflicting -> {
        recordTheRefusal(
            row,
            conflicting.theRefusalItReports(),
            theFactsOf(row, supplied)
                .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(conflicting.refusals())),
            BundleRefusal.theWordsOf(conflicting.refusals()));
        yield render(provider, testRunId, into, conflicting.refusals(), null, null, model);
      }
      case BundleAdmission.Verdict.Taken taken -> {
        BundleReport report = taken.report();
        if (report.applied()) {
          actions.record(
              row.applied(
                  theFactsOf(row, supplied)
                      .andCount("created", report.created())
                      .andCount("updated", report.updated())
                      .andCount("skipped", report.skipped())));
        }
        yield render(
            provider,
            testRunId,
            into,
            List.of(),
            report.applied() ? null : RunBundleScreen.NOTHING_WAS_WRITTEN,
            report,
            model);
      }
    };
  }

  private RunBundle bundleOf(String pasted) {
    return json.readValue(pasted, RunBundle.class);
  }

  private static ActionFacts theFactsOf(
      TheTrailRowAnImportOnAScreenLeaves row, RunBundleScreen.Supplied supplied) {
    return row.theFactsOfWhatWasAsked(RunBundleScreen.theSecretNamesOf(supplied));
  }

  private void recordTheRefusal(
      TheTrailRowAnImportOnAScreenLeaves row, ActionFacts facts, String inItsOwnWords) {
    recordTheRefusal(
        row, RunBundleScreen.theRefusalOfABundleItDidNotTakeBack(), facts, inItsOwnWords);
  }

  private void recordTheRefusal(
      TheTrailRowAnImportOnAScreenLeaves row,
      ActionVerdict.Refused verdict,
      ActionFacts facts,
      String inItsOwnWords) {
    actions.record(row.turnedAway(verdict, facts, inItsOwnWords));
  }

  private static List<String> propertiesOf(List<BundleRefusal> refusals) {
    return refusals.stream().map(BundleRefusal::property).toList();
  }

  private Optional<Selection.Chosen> chosen(@Nullable String provider, @Nullable String testRunId) {
    if (Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen selected) {
      return Optional.of(selected);
    }
    return Optional.empty();
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String into,
      List<BundleRefusal> refusals,
      @Nullable String rejection,
      @Nullable BundleReport report,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("into", into);
    model.addAttribute("refusals", refusals);
    model.addAttribute("report", report);
    model.addAttribute("document", null);
    model.addAttribute("exportRefusals", List.of());
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen selected -> show(selected, model);
    }
    if (rejection != null) {
      model.addAttribute("refusal", rejection);
    }
    return VIEW;
  }

  private void show(Selection.Chosen selected, Model model) {
    model.addAttribute("provider", selected.provider());
    model.addAttribute("testRunId", selected.testRunId().value());
    model.addAttribute(
        RunBundleScreen.THE_BOX_TAKES_A_BLOCK,
        RunBundleScreen.theSecretsBoxTakesABlockOfManyLines(selected.module().accountFields()));
    switch (exports.of(selected.module(), selected.testRunId().value())) {
      case RunBundles.Export.Rejected rejected -> model.addAttribute("refusal", rejected.message());
      case RunBundles.Export.Refused refused ->
          model.addAttribute("exportRefusals", refused.refusals());
      case RunBundles.Export.Exported exported ->
          model.addAttribute("document", json.writeValueAsString(exported.bundle()));
    }
  }
}
