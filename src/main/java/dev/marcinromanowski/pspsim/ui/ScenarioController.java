package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditPage;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission;
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
import tools.jackson.databind.ObjectMapper;

@Controller
class ScenarioController {

  private static final String VIEW = "scenarios";

  private static final String PATH = "/ui/scenarios";

  private static final int THE_CALLS_ONE_RULE_SHOWS = 50;

  static final String THE_RUN_HOLDS_NO_SUCH_RULE =
      "this test run holds no scenario rule of this provider under that scenarioId; the run may"
          + " have been reset while this page was open";

  private final ProviderRegistry providers;
  private final ScenarioRules scenarios;
  private final ScenarioAdmission admission;
  private final AuditReads audit;
  private final ActionTrail actions;
  private final ObjectMapper json;
  private final TestRunId defaultTestRun;

  ScenarioController(
      ProviderRegistry providers,
      ScenarioRules scenarios,
      ScenarioAdmission admission,
      AuditReads audit,
      ActionTrail actions,
      ObjectMapper json,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.scenarios = scenarios;
    this.admission = admission;
    this.audit = audit;
    this.actions = actions;
    this.json = json;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String rules(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String open,
      @RequestParam(required = false) @Nullable String values,
      @RequestParam(required = false) @Nullable String refusal,
      Model model) {
    return render(
        provider,
        testRunId,
        open,
        values,
        WhatTheScenariosScreenCameBackWith.nothing(),
        refusal,
        model);
  }

  @PostMapping(PATH + "/write")
  String write(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @ModelAttribute("form") ScenarioForm form,
      Model model,
      RedirectAttributes attributes) {
    WhatTheScenariosScreenCameBackWith submitted =
        WhatTheScenariosScreenCameBackWith.aRuleWrittenByHand(form);
    Optional<Selection.Chosen> chosen = chosen(provider, testRunId);
    if (chosen.isEmpty()) {
      return render(provider, testRunId, null, null, submitted, null, model);
    }
    Selection.Chosen writing = chosen.get();
    return switch (form.read(writing.testRunId().value())) {
      case ScenarioForm.Read.Unreadable unreadable -> {
        recordTheRefusal(
            writing,
            PATH + "/write",
            unreadable.theRefusalItReports(),
            ScenarioForm.factsOfARefusal(null, unreadable.refusals()));
        yield render(
            provider,
            testRunId,
            null,
            null,
            submitted.andTheseRefusals(unreadable.refusals()),
            null,
            model);
      }
      case ScenarioForm.Read.Declared declared ->
          answer(
              admission.write(writing.module(), declared.scenario()),
              writing,
              PATH + "/write",
              null,
              provider,
              testRunId,
              submitted,
              model,
              attributes);
    };
  }

  @PostMapping(PATH + "/preset")
  String preset(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String preset,
      @RequestParam(required = false) @Nullable String priority,
      @RequestParam(required = false) @Nullable String confirm,
      Model model,
      RedirectAttributes attributes) {
    WhatTheScenariosScreenCameBackWith submitted =
        WhatTheScenariosScreenCameBackWith.aReadyMadeFlow(preset, priority);
    Optional<Selection.Chosen> chosen = chosen(provider, testRunId);
    if (chosen.isEmpty()) {
      return render(provider, testRunId, null, null, submitted, null, model);
    }
    Selection.Chosen expanding = chosen.get();
    return switch (ScenarioForm.readPreset(expanding.testRunId().value(), preset, priority)) {
      case ScenarioForm.Chosen.Unreadable unreadable -> {
        recordTheRefusal(
            expanding,
            PATH + "/preset",
            unreadable.theRefusalItReports(),
            ScenarioForm.factsOfARefusal(preset, unreadable.refusals()));
        yield render(
            provider,
            testRunId,
            null,
            null,
            submitted.andTheseRefusals(unreadable.refusals()),
            null,
            model);
      }
      case ScenarioForm.Chosen.Declared declared -> {
        if (!ScenarioScreen.AN_EXPANSION_IS_CONFIRMED.equals(confirm)) {
          yield show(expanding, declared.preset(), provider, testRunId, submitted, model);
        }
        yield answer(
            admission.expand(expanding.module(), declared.preset()),
            expanding,
            PATH + "/preset",
            declared.preset().preset(),
            provider,
            testRunId,
            submitted,
            model,
            attributes);
      }
    };
  }

  @PostMapping(PATH + "/import")
  String takeBack(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String document,
      Model model,
      RedirectAttributes attributes) {
    WhatTheScenariosScreenCameBackWith submitted =
        WhatTheScenariosScreenCameBackWith.aDocument(document);
    Optional<Selection.Chosen> chosen = chosen(provider, testRunId);
    if (chosen.isEmpty()) {
      return render(provider, testRunId, null, null, submitted, null, model);
    }
    Selection.Chosen taking = chosen.get();
    return switch (ScenarioScreen.pastedInto(
        document, taking.testRunId().value(), this::documentOf)) {
      case ScenarioScreen.Pasted.Unreadable unreadable -> {
        recordTheRefusal(
            taking,
            PATH + "/import",
            ScenarioScreen.theRefusalOfRulesItDidNotWrite(),
            ActionFacts.none().and(ActionFacts.REFUSED_BECAUSE, unreadable.message()).asRecorded());
        yield render(provider, testRunId, null, null, submitted, unreadable.message(), model);
      }
      case ScenarioScreen.Pasted.Document pasted ->
          answer(
              admission.takeBack(taking.module(), pasted.document()),
              taking,
              PATH + "/import",
              pasted.document().preset(),
              provider,
              testRunId,
              submitted,
              model,
              attributes);
    };
  }

  private String show(
      Selection.Chosen expanding,
      DeclaredPreset declared,
      @Nullable String provider,
      @Nullable String testRunId,
      WhatTheScenariosScreenCameBackWith submitted,
      Model model) {
    return switch (admission.preview(expanding.module(), declared)) {
      case ScenarioAdmission.Expansion.Rejected rejected -> {
        recordTheRefusal(
            expanding,
            PATH + "/preset",
            ScenarioScreen.theRefusalOfRulesItDidNotWrite(),
            ScenarioForm.factsOfARejection(declared.preset(), rejected.message()));
        yield render(provider, testRunId, null, null, submitted, rejected.message(), null, model);
      }
      case ScenarioAdmission.Expansion.Refused refused -> {
        recordTheRefusal(
            expanding,
            PATH + "/preset",
            ScenarioScreen.theRefusalOfRulesItDidNotWrite(),
            ScenarioForm.factsOfARefusal(declared.preset(), refused.refusals()));
        yield render(
            provider,
            testRunId,
            null,
            null,
            submitted.andTheseRefusals(refused.refusals()),
            null,
            null,
            model);
      }
      case ScenarioAdmission.Expansion.Expanded expanded -> {
        recordTheRefusal(
            expanding,
            PATH + "/preset",
            ScenarioScreen.theRefusalOfRulesItDidNotWrite(),
            ActionFacts.none()
                .and("preset", declared.preset())
                .andCount("previewedRules", expanded.rules().size())
                .and(
                    ActionFacts.REFUSED_BECAUSE,
                    ScenarioScreen.THE_RULES_ARE_SHOWN_BEFORE_THEY_ARE_WRITTEN)
                .asRecorded());
        yield render(
            provider,
            testRunId,
            null,
            null,
            submitted,
            ScenarioScreen.THE_RULES_ARE_SHOWN_BEFORE_THEY_ARE_WRITTEN,
            ScenarioScreen.previewOf(
                expanded.preset().publishedName(),
                expanding.provider(),
                expanded.priority(),
                expanded.rules(),
                json.writeValueAsString(expanded.asDocument(expanding.module().id()))),
            model);
      }
    };
  }

  private ScenarioDocument documentOf(String pasted) {
    return json.readValue(pasted, ScenarioDocument.class);
  }

  @PostMapping(PATH + "/delete")
  String delete(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam String scenarioId,
      RedirectAttributes attributes) {
    Optional<Selection.Chosen> chosen = chosen(provider, testRunId);
    chosen.ifPresent(deleting -> removeAndRecord(deleting, scenarioId.strip(), attributes));
    attributes.addAttribute("provider", provider);
    attributes.addAttribute("testRunId", testRunId);
    return "redirect:" + PATH;
  }

  private String answer(
      ScenarioAdmission.Verdict verdict,
      Selection.Chosen chosen,
      String path,
      @Nullable String preset,
      @Nullable String provider,
      @Nullable String testRunId,
      WhatTheScenariosScreenCameBackWith submitted,
      Model model,
      RedirectAttributes attributes) {
    return switch (verdict) {
      case ScenarioAdmission.Verdict.Rejected rejected -> {
        recordTheRefusal(
            chosen,
            path,
            rejected.theRefusalItReports(),
            ScenarioForm.factsOfARejection(preset, rejected.message()));
        yield render(provider, testRunId, null, null, submitted, rejected.message(), model);
      }
      case ScenarioAdmission.Verdict.Refused refused -> {
        recordTheRefusal(
            chosen,
            path,
            refused.theRefusalItReports(),
            ScenarioForm.factsOfARefusal(preset, refused.refusals()));
        yield render(
            provider,
            testRunId,
            null,
            null,
            submitted.andTheseRefusals(refused.refusals()),
            null,
            model);
      }
      case ScenarioAdmission.Verdict.Written written -> {
        recordTheRulesItWrote(chosen, path, preset, written);
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_RULES_ARE_WRITTEN);
        yield backTo(chosen, theOneRuleItWrote(written), attributes);
      }
    };
  }

  private void recordTheRulesItWrote(
      Selection.Chosen chosen,
      String path,
      @Nullable String preset,
      ScenarioAdmission.Verdict.Written written) {
    List<String> scenarioIds = new ArrayList<>();
    for (ScenarioRule rule : written.rules()) {
      scenarioIds.add(rule.scenarioId());
    }
    actions.record(
        AuditedAction.applied(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            "POST",
            path,
            ActionVerdict.applied(HttpStatus.FOUND.value()),
            ActionFacts.none()
                .and("preset", preset)
                .andEach("scenarioIds", scenarioIds)
                .asRecorded()));
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

  private void removeAndRecord(
      Selection.Chosen deleting, String scenarioId, RedirectAttributes attributes) {
    if (!scenarios.remove(deleting.module().id(), deleting.testRunId(), scenarioId)) {
      recordTheRefusal(
          deleting,
          PATH + "/delete",
          ActionVerdict.notFound(
              RefusalSubject.noObjectWasNamed(
                  ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_FORGET)),
          ActionFacts.none()
              .and("scenarioId", scenarioId)
              .and(ActionFacts.REFUSED_BECAUSE, THE_RUN_HOLDS_NO_SUCH_RULE)
              .asRecorded());
      attributes.addAttribute("refusal", THE_RUN_HOLDS_NO_SUCH_RULE);
      return;
    }
    actions.record(
        AuditedAction.applied(
            deleting.module().id(),
            deleting.testRunId(),
            ObjectOrigin.UI,
            "POST",
            PATH + "/delete",
            ActionVerdict.applied(HttpStatus.FOUND.value()),
            ActionFacts.none().andEach("scenarioIds", List.of(scenarioId)).asRecorded()));
    attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_RULE_IS_DELETED);
  }

  private static @Nullable String theOneRuleItWrote(ScenarioAdmission.Verdict.Written written) {
    return written.rules().size() == 1 ? written.rules().getFirst().scenarioId() : null;
  }

  private Optional<Selection.Chosen> chosen(@Nullable String provider, @Nullable String testRunId) {
    if (Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen selected) {
      return Optional.of(selected);
    }
    return Optional.empty();
  }

  private static String backTo(
      Selection.Chosen chosen, @Nullable String openScenarioId, RedirectAttributes attributes) {
    attributes.addAttribute("provider", chosen.provider());
    attributes.addAttribute("testRunId", chosen.testRunId().value());
    if (openScenarioId != null) {
      attributes.addAttribute("open", openScenarioId);
    }
    return "redirect:" + PATH;
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String open,
      @Nullable String values,
      WhatTheScenariosScreenCameBackWith submitted,
      @Nullable String rejection,
      Model model) {
    return render(provider, testRunId, open, values, submitted, rejection, null, model);
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      @Nullable String open,
      @Nullable String values,
      WhatTheScenariosScreenCameBackWith submitted,
      @Nullable String rejection,
      ScenarioScreen.@Nullable Preview preview,
      Model model) {
    model.addAttribute("preview", preview);
    List<String> known = providers.names();
    model.addAttribute("providers", known);
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("open", open);
    model.addAttribute("values", values);
    model.addAttribute("form", submitted.rule());
    model.addAttribute(WhatTheScenariosScreenCameBackWith.ATTRIBUTE, submitted);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused refused -> model.addAttribute("refusal", refused.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen selected -> list(selected, open, values, model);
    }
    if (rejection != null) {
      model.addAttribute("refusal", rejection);
    }
    return VIEW;
  }

  private void list(
      Selection.Chosen selected, @Nullable String open, @Nullable String values, Model model) {
    SimulatorProvider found = selected.module();
    TestRunId run = selected.testRunId();
    model.addAttribute("provider", selected.provider());
    model.addAttribute("testRunId", run.value());
    model.addAttribute("presets", APresetInWords.theOnesOffered(found, run));
    model.addAttribute(
        "rules",
        ScenarioScreen.of(
            selected.provider(),
            run.value(),
            scenarios.of(found.id(), run),
            found.vocabulary(),
            scenarios.storyStatesOf(found.id(), run, StoryKey.ofTestRun(run)),
            open,
            Disclosure.valuesAsked(values),
            THE_CALLS_ONE_RULE_SHOWS,
            scenarioId -> theCallsItDecided(found, run, scenarioId)));
  }

  private List<AuditRecord> theCallsItDecided(
      SimulatorProvider module, TestRunId run, String scenarioId) {
    AuditQuery query =
        new AuditQuery(
            run,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            scenarioId,
            null,
            null,
            null,
            THE_CALLS_ONE_RULE_SHOWS);
    return switch (audit.pageOf(module.id(), query)) {
      case AuditPage.Listed listed -> listed.records();
      case AuditPage.NoSuchCursor _ -> List.of();
    };
  }
}
