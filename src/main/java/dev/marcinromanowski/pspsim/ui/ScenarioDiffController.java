package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioComparison;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class ScenarioDiffController {

  private static final String VIEW = "scenario-diff";

  private static final String PATH = "/ui/scenarios/diff";

  private final ProviderRegistry providers;
  private final ScenarioRules scenarios;
  private final ScenarioAdmission admission;
  private final ActionTrail actions;
  private final TestRunId defaultTestRun;

  ScenarioDiffController(
      ProviderRegistry providers,
      ScenarioRules scenarios,
      ScenarioAdmission admission,
      ActionTrail actions,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.scenarios = scenarios;
    this.admission = admission;
    this.actions = actions;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String diff(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String left,
      @RequestParam(required = false) @Nullable String right,
      @RequestParam(required = false) @Nullable String rightProvider,
      @RequestParam(required = false) @Nullable String rightTestRunId,
      @RequestParam(required = false) @Nullable String refusal,
      Model model) {
    return render(
        provider,
        testRunId,
        new ScenarioDiffScreen.Asked(left, right, rightProvider, rightTestRunId),
        ScenarioForm.empty(),
        List.of(),
        refusal,
        model);
  }

  @PostMapping(PATH + "/copy")
  String copy(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String copiedFrom,
      @ModelAttribute("form") ScenarioForm form,
      Model model,
      RedirectAttributes attributes) {
    Optional<Selection.Chosen> chosen = chosen(provider, testRunId);
    if (chosen.isEmpty()) {
      return render(
          provider, testRunId, ScenarioDiffScreen.Asked.nothing(), form, List.of(), null, model);
    }
    Selection.Chosen copying = chosen.get();
    String source = copiedFrom == null ? "" : copiedFrom.strip();
    Optional<ScenarioRule> copied =
        scenarios.find(copying.module().id(), copying.testRunId(), source);
    if (copied.isEmpty()) {
      String refusal =
          ScenarioDiffScreen.theRunHoldsNoRuleNamedOnThe(
              ScenarioDiffScreen.THE_LEFT, source, copying.provider(), copying.testRunId().value());
      record(
          copying,
          ScenarioDiffScreen.theRefusalOfARuleItCannotCopy(),
          ActionFacts.none()
              .and("copiedFrom", source)
              .and(ActionFacts.REFUSED_BECAUSE, refusal)
              .asRecorded());
      return render(
          provider, testRunId, ScenarioDiffScreen.Asked.nothing(), form, List.of(), refusal, model);
    }
    return switch (form.read(copying.testRunId().value())) {
      case ScenarioForm.Read.Unreadable unreadable -> {
        record(
            copying,
            ScenarioDiffScreen.theRefusalOfACopyItCouldNotRead(),
            theFactsOfARefusedCopy(source, unreadable.refusals()));
        yield render(
            provider,
            testRunId,
            new ScenarioDiffScreen.Asked(source, null, null, null),
            form,
            unreadable.refusals(),
            null,
            model);
      }
      case ScenarioForm.Read.Declared declared ->
          answer(
              admission.write(copying.module(), declared.scenario()),
              copying,
              source,
              provider,
              testRunId,
              form,
              model,
              attributes);
    };
  }

  private String answer(
      ScenarioAdmission.Verdict verdict,
      Selection.Chosen copying,
      String source,
      @Nullable String provider,
      @Nullable String testRunId,
      ScenarioForm form,
      Model model,
      RedirectAttributes attributes) {
    return switch (verdict) {
      case ScenarioAdmission.Verdict.Rejected rejected -> {
        record(
            copying,
            rejected.theRefusalItReports(),
            ActionFacts.none()
                .and("copiedFrom", source)
                .and(ActionFacts.REFUSED_BECAUSE, rejected.message())
                .asRecorded());
        yield render(
            provider,
            testRunId,
            new ScenarioDiffScreen.Asked(source, null, null, null),
            form,
            List.of(),
            rejected.message(),
            model);
      }
      case ScenarioAdmission.Verdict.Refused refused -> {
        record(
            copying,
            refused.theRefusalItReports(),
            theFactsOfARefusedCopy(source, refused.refusals()));
        yield render(
            provider,
            testRunId,
            new ScenarioDiffScreen.Asked(source, null, null, null),
            form,
            refused.refusals(),
            null,
            model);
      }
      case ScenarioAdmission.Verdict.Written written -> {
        String copy = written.rules().getFirst().scenarioId();
        actions.record(
            AuditedAction.applied(
                copying.module().id(),
                copying.testRunId(),
                ObjectOrigin.UI,
                "POST",
                PATH + "/copy",
                ActionVerdict.applied(HttpStatus.FOUND.value()),
                ActionFacts.none()
                    .and("copiedFrom", source)
                    .andEach("scenarioIds", List.of(copy))
                    .asRecorded()));
        attributes.addAttribute("provider", copying.provider());
        attributes.addAttribute("testRunId", copying.testRunId().value());
        attributes.addAttribute("left", source);
        attributes.addAttribute("right", copy);
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_COPY_IS_WRITTEN);
        yield "redirect:" + PATH;
      }
    };
  }

  private static Map<String, List<String>> theFactsOfARefusedCopy(
      String source, List<ScenarioRefusal> refusals) {
    List<String> properties = new ArrayList<>();
    for (ScenarioRefusal refusal : refusals) {
      properties.add(refusal.property());
    }
    return ActionFacts.none()
        .and("copiedFrom", source)
        .andEach(ActionFacts.REFUSED_PROPERTIES, properties)
        .and(ActionFacts.REFUSED_BECAUSE, ScenarioRefusal.theWordsOf(refusals))
        .asRecorded();
  }

  private void record(
      Selection.Chosen copying, ActionVerdict.Refused verdict, Map<String, List<String>> facts) {
    actions.record(
        AuditedAction.refusing(
            copying.module().id(),
            copying.testRunId(),
            ObjectOrigin.UI,
            "POST",
            PATH + "/copy",
            verdict,
            facts));
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
      ScenarioDiffScreen.Asked asked,
      ScenarioForm form,
      List<ScenarioRefusal> refusals,
      @Nullable String refusal,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("form", form);
    model.addAttribute("refusals", refusals);
    if (refusal != null) {
      model.addAttribute("refusal", refusal);
    }
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen selected -> compare(selected, asked, model);
    }
    return VIEW;
  }

  private void compare(Selection.Chosen selected, ScenarioDiffScreen.Asked asked, Model model) {
    SimulatorProvider module = selected.module();
    TestRunId run = selected.testRunId();
    model.addAttribute("provider", selected.provider());
    model.addAttribute("testRunId", run.value());
    List<ScenarioRule> ofTheRun = scenarios.of(module.id(), run);
    TestRunId otherRun;
    try {
      otherRun = TestRunId.declaredOr(asked.rightTestRunId(), run);
    } catch (IllegalArgumentException malformed) {
      model.addAttribute("refusal", String.valueOf(malformed.getMessage()));
      model.addAttribute("diff", nothingCompared(selected, run, ofTheRun, ofTheRun, asked));
      return;
    }
    List<ScenarioRule> ofTheRunOnTheRight =
        otherRun.equals(run) ? ofTheRun : scenarios.of(module.id(), otherRun);
    String left = named(asked.left());
    String right = named(asked.right());
    if (left == null || right == null) {
      model.addAttribute(
          "nothingWasCompared", ScenarioDiffScreen.TWO_RULES_ARE_NAMED_BEFORE_ANYTHING_IS_COMPARED);
      model.addAttribute(
          "diff", nothingCompared(selected, run, ofTheRun, ofTheRunOnTheRight, asked));
      return;
    }
    Optional<SimulatorProvider> against = theProviderOfTheRight(module, asked);
    if (against.isEmpty()) {
      model.addAttribute("refusal", providers.theRefusalOfAnUnknownProvider());
      model.addAttribute(
          "diff", nothingCompared(selected, run, ofTheRun, ofTheRunOnTheRight, asked));
      return;
    }
    SimulatorProvider other = against.get();
    Optional<ScenarioRule> onTheLeft = scenarios.find(module.id(), run, left);
    Optional<ScenarioRule> onTheRight = scenarios.find(other.id(), otherRun, right);
    if (onTheLeft.isEmpty()) {
      model.addAttribute(
          "refusal",
          ScenarioDiffScreen.theRunHoldsNoRuleNamedOnThe(
              ScenarioDiffScreen.THE_LEFT, left, selected.provider(), run.value()));
    } else if (onTheRight.isEmpty()) {
      model.addAttribute(
          "refusal",
          ScenarioDiffScreen.theRunHoldsNoRuleNamedOnThe(
              ScenarioDiffScreen.THE_RIGHT, right, other.id().value(), otherRun.value()));
    }
    if (onTheLeft.isEmpty() || onTheRight.isEmpty()) {
      model.addAttribute(
          "diff", nothingCompared(selected, run, ofTheRun, ofTheRunOnTheRight, asked));
      return;
    }
    ScenarioRule leftRule = onTheLeft.get();
    ScenarioRule rightRule = onTheRight.get();
    ScenarioComparison.Comparison comparison =
        ScenarioComparison.of(leftRule, rightRule, module.vocabulary());
    ScenarioComparison.Comparison.Compared compared =
        comparison instanceof ScenarioComparison.Comparison.Compared it ? it : null;
    if (comparison instanceof ScenarioComparison.Comparison.OfTwoProviders refused) {
      model.addAttribute("refusal", refused.reason());
    }
    model.addAttribute(
        "diff",
        ScenarioDiffScreen.of(
            selected.provider(),
            run.value(),
            ofTheRun,
            ofTheRunOnTheRight,
            asked,
            compared,
            leftRule,
            rightRule));
  }

  private Optional<SimulatorProvider> theProviderOfTheRight(
      SimulatorProvider module, ScenarioDiffScreen.Asked asked) {
    String named = named(asked.rightProvider());
    return named == null ? Optional.of(module) : providers.find(named);
  }

  private static ScenarioDiffScreen.View nothingCompared(
      Selection.Chosen selected,
      TestRunId run,
      List<ScenarioRule> ofTheRun,
      List<ScenarioRule> ofTheRunOnTheRight,
      ScenarioDiffScreen.Asked asked) {
    return ScenarioDiffScreen.of(
        selected.provider(), run.value(), ofTheRun, ofTheRunOnTheRight, asked, null, null, null);
  }

  private static @Nullable String named(@Nullable String asked) {
    return asked == null || asked.isBlank() ? null : asked.strip();
  }
}
