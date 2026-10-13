package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.bundle.BundleRefusal;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionBundle;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionManifest;
import dev.marcinromanowski.pspsim.kernel.repro.ReproductionReport;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredReproductionImport;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ReproductionAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ReproductionBundles;
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
class ReproductionController {

  private static final String VIEW = "repro-bundle";

  private static final String PATH = "/ui/repro-bundle";

  private static final String THE_IDS_IT_PRESERVES = "providerNativeIdsPreserved";

  private final ProviderRegistry providers;
  private final ReproductionBundles captures;
  private final ReproductionAdmission reproductions;
  private final ActionTrail actions;
  private final ObjectMapper json;
  private final TestRunId defaultTestRun;

  ReproductionController(
      ProviderRegistry providers,
      ReproductionBundles captures,
      ReproductionAdmission reproductions,
      ActionTrail actions,
      ObjectMapper json,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.captures = captures;
    this.reproductions = reproductions;
    this.actions = actions;
    this.json = json;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String capture(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String key,
      Model model) {
    return render(provider, testRunId, key, null, List.of(), null, null, model);
  }

  @PostMapping(PATH + "/import")
  String takeBack(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String key,
      @RequestParam(required = false) @Nullable String into,
      @RequestParam(required = false) @Nullable String document,
      @RequestParam(required = false) @Nullable String secrets,
      @RequestParam(required = false) @Nullable String remap,
      @RequestParam(required = false) @Nullable String dryRun,
      Model model) {
    Optional<Selection.Chosen> chosen = chosen(provider, testRunId);
    if (chosen.isEmpty()) {
      return render(provider, testRunId, key, into, List.of(), null, null, model);
    }
    Selection.Chosen taking = chosen.get();
    TheTrailRowAnImportOnAScreenLeaves row =
        TheTrailRowAnImportOnAScreenLeaves.of(taking, into, dryRun != null, PATH + "/import");

    RunBundleScreen.Supplied supplied =
        RunBundleScreen.theSecretsTypedInto(secrets, taking.module().accountFields());
    if (supplied instanceof RunBundleScreen.Supplied.Unreadable unreadable) {
      return refused(row, supplied, provider, testRunId, key, into, unreadable.message(), model);
    }
    ReproductionScreen.Remapping remapping = ReproductionScreen.theRemappingTypedInto(remap);
    if (remapping instanceof ReproductionScreen.Remapping.Unreadable unreadable) {
      return refused(row, supplied, provider, testRunId, key, into, unreadable.message(), model);
    }
    ReproductionScreen.Pasted pasted = ReproductionScreen.pastedInto(document, this::bundleOf);
    if (pasted instanceof ReproductionScreen.Pasted.Unreadable unreadable) {
      return refused(row, supplied, provider, testRunId, key, into, unreadable.message(), model);
    }

    Map<String, Map<String, String>> byAccount =
        ((RunBundleScreen.Supplied.Secrets) supplied).byAccount();
    DeclaredReproductionImport declared =
        new DeclaredReproductionImport(
            row.theRunItWritesInto(),
            row.itWritesNothing(),
            ((ReproductionScreen.Pasted.Bundle) pasted).bundle(),
            byAccount,
            ((ReproductionScreen.Remapping.Asked) remapping).byCarriedId());

    return switch (reproductions.takeBack(taking.module(), declared)) {
      case ReproductionAdmission.Verdict.Rejected rejected -> {
        recordTheRefusal(
            row,
            rejected.theRefusalItReports(),
            theFactsOf(row, declared, supplied),
            rejected.message());
        yield render(provider, testRunId, key, into, List.of(), rejected.message(), null, model);
      }
      case ReproductionAdmission.Verdict.Refused refusedBundle -> {
        recordTheRefusal(
            row,
            refusedBundle.theRefusalItReports(),
            theFactsOf(row, declared, supplied)
                .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(refusedBundle.refusals())),
            BundleRefusal.theWordsOf(refusedBundle.refusals()));
        yield render(provider, testRunId, key, into, refusedBundle.refusals(), null, null, model);
      }
      case ReproductionAdmission.Verdict.Conflicting conflicting -> {
        recordTheRefusal(
            row,
            conflicting.theRefusalItReports(),
            theFactsOf(row, declared, supplied)
                .andEach(ActionFacts.REFUSED_PROPERTIES, propertiesOf(conflicting.refusals())),
            BundleRefusal.theWordsOf(conflicting.refusals()));
        yield render(provider, testRunId, key, into, conflicting.refusals(), null, null, model);
      }
      case ReproductionAdmission.Verdict.Taken taken -> {
        ReproductionReport report = taken.report();
        if (report.applied()) {
          actions.record(
              row.applied(
                  theFactsOf(row, declared, supplied)
                      .andCount("created", report.setup().created())
                      .andCount("updated", report.setup().updated())
                      .andCount("skipped", report.setup().skipped())));
        }
        yield render(
            provider,
            testRunId,
            key,
            into,
            List.of(),
            report.applied() ? null : ReproductionScreen.NOTHING_WAS_WRITTEN,
            report,
            model);
      }
    };
  }

  private String refused(
      TheTrailRowAnImportOnAScreenLeaves row,
      RunBundleScreen.Supplied supplied,
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String key,
      @Nullable String into,
      String message,
      Model model) {
    recordTheRefusal(
        row,
        ReproductionScreen.theRefusalOfABundleItDidNotTakeBack(),
        row.theFactsOfWhatWasAsked(RunBundleScreen.theSecretNamesOf(supplied)),
        message);
    return render(provider, testRunId, key, into, List.of(), message, null, model);
  }

  private void recordTheRefusal(
      TheTrailRowAnImportOnAScreenLeaves row,
      ActionVerdict.Refused verdict,
      ActionFacts facts,
      String inItsOwnWords) {
    actions.record(row.turnedAway(verdict, facts, inItsOwnWords));
  }

  private static ActionFacts theFactsOf(
      TheTrailRowAnImportOnAScreenLeaves row,
      DeclaredReproductionImport declared,
      RunBundleScreen.Supplied supplied) {
    return row.theFactsOfWhatWasAsked(RunBundleScreen.theSecretNamesOf(supplied))
        .and(THE_IDS_IT_PRESERVES, String.valueOf(declared.preservesProviderNativeIds()));
  }

  private ReproductionBundle bundleOf(String pasted) {
    return json.readValue(pasted, ReproductionBundle.class);
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
      @Nullable String key,
      @Nullable String into,
      List<BundleRefusal> refusals,
      @Nullable String rejection,
      @Nullable ReproductionReport report,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("key", key);
    model.addAttribute("into", into);
    model.addAttribute("refusals", refusals);
    model.addAttribute("report", report);
    model.addAttribute("document", null);
    model.addAttribute("contents", null);
    model.addAttribute("captureRefusals", List.of());
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen selected -> show(selected, key, model);
    }
    if (rejection != null) {
      model.addAttribute("refusal", rejection);
    }
    return VIEW;
  }

  private void show(Selection.Chosen selected, @Nullable String key, Model model) {
    model.addAttribute("provider", selected.provider());
    model.addAttribute("testRunId", selected.testRunId().value());
    model.addAttribute(
        RunBundleScreen.THE_BOX_TAKES_A_BLOCK,
        RunBundleScreen.theSecretsBoxTakesABlockOfManyLines(selected.module().accountFields()));
    model.addAttribute(
        "theObjectsAnIdNames", selected.module().vocabulary().theObjectsAnIdentifierAddresses());
    if (key == null || key.isBlank()) {
      return;
    }
    switch (captures.of(selected.module(), selected.testRunId().value(), key)) {
      case ReproductionBundles.Capture.Rejected rejected ->
          model.addAttribute("refusal", rejected.message());
      case ReproductionBundles.Capture.Unknown unknown ->
          model.addAttribute("refusal", unknown.message());
      case ReproductionBundles.Capture.Refused refused ->
          model.addAttribute("captureRefusals", refused.refusals());
      case ReproductionBundles.Capture.Captured captured -> {
        ReproductionBundle bundle = captured.bundle();
        model.addAttribute("document", json.writeValueAsString(bundle));
        ReproductionManifest contents = bundle.contents();
        model.addAttribute(
            "contents",
            contents == null
                ? ReproductionManifest.of(bundle.setup(), bundle.evidence())
                : contents);
      }
    }
  }
}
