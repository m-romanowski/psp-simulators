package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersonaSeeding;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SeededPersona;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
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

@Controller
class PersonaController {

  private static final String VIEW = "personas";

  private static final String PATH = "/ui/personas";

  private static final String SEED = PATH + "/seed";

  private final ProviderRegistry providers;
  private final PersonaAdmission personas;
  private final PaymentMethodFixtures seeded;
  private final ActionTrail actions;
  private final TestRunId defaultTestRun;

  PersonaController(
      ProviderRegistry providers,
      PersonaAdmission personas,
      PaymentMethodFixtures seeded,
      ActionTrail actions,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.personas = personas;
    this.seeded = seeded;
    this.actions = actions;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String personas(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      Model model) {
    return render(provider, testRunId, null, null, null, null, model);
  }

  @PostMapping(SEED)
  String seedThePersona(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String persona,
      @RequestParam(required = false) @Nullable String merchantId,
      @RequestParam(required = false) @Nullable String confirm,
      Model model) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, null, null, null, null, model);
    }
    if (persona == null || persona.isBlank()) {
      recordTheRefusal(
          chosen,
          PersonaScreen.theRefusalOfObjectsItDidNotCreate(),
          ActionFacts.none()
              .and(ActionFacts.REFUSED_BECAUSE, PersonaScreen.A_PERSONA_IS_NAMED_BY_THE_FORM)
              .asRecorded());
      return render(
          provider,
          testRunId,
          null,
          null,
          PersonaScreen.A_PERSONA_IS_NAMED_BY_THE_FORM,
          persona,
          model);
    }
    DeclaredPersonaSeeding asked =
        new DeclaredPersonaSeeding(chosen.testRunId().value(), persona, merchantId);
    if (!PersonaScreen.A_SEEDING_IS_CONFIRMED.equals(confirm)) {
      return show(chosen, asked, persona, model);
    }
    return write(chosen, asked, persona, model);
  }

  private String show(
      Selection.Chosen chosen, DeclaredPersonaSeeding asked, String persona, Model model) {
    return switch (personas.whatItWouldCreate(chosen.module(), asked)) {
      case PersonaAdmission.Plan.Rejected rejected ->
          refusing(chosen, persona, rejected.message(), model);
      case PersonaAdmission.Plan.Unknown unknown ->
          refusing(chosen, persona, unknown.message(), model);
      case PersonaAdmission.Plan.Refused refused ->
          refusing(chosen, persona, ScenarioRefusal.theWordsOf(refused.refusals()), model);
      case PersonaAdmission.Plan.Planned planned -> {
        recordTheRefusal(
            chosen,
            PersonaScreen.theRefusalOfObjectsItDidNotCreate(),
            ActionFacts.none()
                .and(PersonaAdmission.PERSONA, persona)
                .andCount("previewedRules", planned.rules().size())
                .and(
                    ActionFacts.REFUSED_BECAUSE,
                    PersonaScreen.THE_OBJECTS_ARE_SHOWN_BEFORE_THEY_ARE_CREATED)
                .asRecorded());
        yield render(
            chosen.provider(),
            chosen.testRunId().value(),
            PersonaScreen.proposalOf(
                planned.persona(), planned.rules(), planned.theRefusalThisPlanCannotForesee()),
            null,
            PersonaScreen.THE_OBJECTS_ARE_SHOWN_BEFORE_THEY_ARE_CREATED,
            persona,
            model);
      }
    };
  }

  private String write(
      Selection.Chosen chosen, DeclaredPersonaSeeding asked, String persona, Model model) {
    return switch (personas.seed(chosen.module(), asked, ObjectOrigin.UI)) {
      case PersonaAdmission.Verdict.Rejected rejected ->
          refusingAs(chosen, persona, rejected.theRefusalItReports(), rejected.message(), model);
      case PersonaAdmission.Verdict.Unknown unknown ->
          refusingAs(chosen, persona, unknown.theRefusalItReports(), unknown.message(), model);
      case PersonaAdmission.Verdict.Conflicting conflicting ->
          refusingAs(
              chosen, persona, conflicting.theRefusalItReports(), conflicting.message(), model);
      case PersonaAdmission.Verdict.Refused refused ->
          refusingAs(
              chosen,
              persona,
              refused.theRefusalItReports(),
              ScenarioRefusal.theWordsOf(refused.refusals()),
              model);
      case PersonaAdmission.Verdict.Seeded wasSeeded -> {
        SeededPersona created = wasSeeded.seeded();
        actions.record(
            AuditedAction.applied(
                chosen.module().id(),
                created.testRunId(),
                ObjectOrigin.UI,
                "POST",
                SEED,
                ActionVerdict.applied(HttpStatus.OK.value()),
                ActionFacts.none()
                    .and(PersonaAdmission.PERSONA, created.name())
                    .andEach("scenarioIds", created.theScenarioIdsItWrote())
                    .andEach("fixtureIds", created.theFixtureIdsItSeeded())
                    .asRecorded()));
        yield render(
            chosen.provider(),
            created.testRunId().value(),
            null,
            PersonaScreen.createdOf(created),
            null,
            persona,
            model);
      }
    };
  }

  private String refusing(Selection.Chosen chosen, String persona, String message, Model model) {
    return refusingAs(
        chosen, persona, PersonaScreen.theRefusalOfObjectsItDidNotCreate(), message, model);
  }

  private String refusingAs(
      Selection.Chosen chosen,
      String persona,
      ActionVerdict.Refused verdict,
      String message,
      Model model) {
    recordTheRefusal(
        chosen,
        verdict,
        ActionFacts.none()
            .and(PersonaAdmission.PERSONA, persona)
            .and(ActionFacts.REFUSED_BECAUSE, message)
            .asRecorded());
    return render(
        chosen.provider(), chosen.testRunId().value(), null, null, message, persona, model);
  }

  private void recordTheRefusal(
      Selection.Chosen chosen, ActionVerdict.Refused verdict, Map<String, List<String>> facts) {
    actions.record(
        AuditedAction.refusing(
            chosen.module().id(),
            chosen.testRunId(),
            ObjectOrigin.UI,
            "POST",
            SEED,
            verdict,
            facts));
  }

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      PersonaScreen.@Nullable Proposal proposal,
      PersonaScreen.@Nullable Created created,
      @Nullable String refusal,
      @Nullable String persona,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    model.addAttribute("proposal", proposal);
    model.addAttribute("created", created);
    model.addAttribute("open", persona);
    model.addAttribute("nothingHidden", PersonaScreen.NOTHING_IS_HIDDEN_BEHIND_A_PERSONA);
    model.addAttribute("runWide", PersonaScreen.THE_RULES_ARE_THE_RUNS_RULES);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused unusable -> model.addAttribute("refusal", unusable.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen -> describe(chosen, model);
    }
    if (refusal != null) {
      model.addAttribute("refusal", refusal);
    }
    return VIEW;
  }

  private void describe(Selection.Chosen chosen, Model model) {
    TestRunId run = chosen.testRunId();
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    model.addAttribute(
        "personas",
        PersonaScreen.of(
            chosen.provider(),
            run.value(),
            personas.publishedBy(chosen.module()),
            seeded.of(chosen.module().id(), run)));
  }
}
