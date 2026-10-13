package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureAdmission;
import dev.marcinromanowski.pspsim.kernel.spi.PaymentMethodFixtureFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class PaymentMethodController {

  private static final String VIEW = "payment-methods";

  private static final String PATH = "/ui/payment-methods";

  static final String REMOVAL_IS_CONFIRMED = "yes";

  private final ProviderRegistry providers;
  private final PaymentMethodFixtures seeded;
  private final PaymentMethodFixtureAdmission fixtures;
  private final ScenarioRules scenarios;
  private final ActionTrail actions;
  private final TestRunId defaultTestRun;

  PaymentMethodController(
      ProviderRegistry providers,
      PaymentMethodFixtures seeded,
      PaymentMethodFixtureAdmission fixtures,
      ScenarioRules scenarios,
      ActionTrail actions,
      @Value("${simulator.default-test-run-id}") String defaultTestRunId) {
    this.providers = providers;
    this.seeded = seeded;
    this.fixtures = fixtures;
    this.scenarios = scenarios;
    this.actions = actions;
    this.defaultTestRun = TestRunId.of(defaultTestRunId);
  }

  @GetMapping(PATH)
  String paymentMethods(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      Model model) {
    return render(provider, testRunId, Map.of(), null, null, model);
  }

  @PostMapping(PATH + "/seed")
  String seedThePaymentMethod(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam Map<String, String> parameters,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, parameters, null, null, model);
    }
    DeclaredPaymentMethodFixture asked;
    try {
      asked = PaymentMethodForm.fixtureOf(chosen.testRunId().value(), parameters);
    } catch (IllegalArgumentException typed) {
      String unreadable = String.valueOf(typed.getMessage());
      recordTheRefusal(
          chosen,
          PATH + "/seed",
          PaymentMethodForm.theRefusalOfAFixtureThisSimulatorCannotRead(),
          ActionFacts.none().and(ActionFacts.REFUSED_BECAUSE, unreadable).asRecorded());
      return render(provider, testRunId, parameters, unreadable, null, model);
    }
    switch (fixtures.seed(chosen.module(), asked, ObjectOrigin.UI)) {
      case PaymentMethodFixtureAdmission.Verdict.Rejected rejected -> {
        recordTheRefusal(
            chosen,
            PATH + "/seed",
            rejected.theRefusalItReports(),
            ActionFacts.none().and(ActionFacts.REFUSED_BECAUSE, rejected.message()).asRecorded());
        return render(provider, testRunId, parameters, rejected.message(), null, model);
      }
      case PaymentMethodFixtureAdmission.Verdict.Refused refused -> {
        recordTheRefusal(
            chosen,
            PATH + "/seed",
            refused.theRefusalItReports(),
            ActionFacts.none()
                .andEach(ActionFacts.REFUSED_PROPERTIES, List.of(refused.property()))
                .and(ActionFacts.REFUSED_BECAUSE, refused.message())
                .asRecorded());
        return render(
            provider, testRunId, parameters, refused.inItsOwnWords(), refused.property(), model);
      }
      case PaymentMethodFixtureAdmission.Verdict.Conflicting conflicting -> {
        recordTheRefusal(
            chosen,
            PATH + "/seed",
            conflicting.theRefusalItReports(),
            ActionFacts.none()
                .and(ActionFacts.REFUSED_BECAUSE, conflicting.message())
                .asRecorded());
        return render(provider, testRunId, parameters, conflicting.message(), null, model);
      }
      case PaymentMethodFixtureAdmission.Verdict.Seeded created -> {
        record(chosen, PATH + "/seed", "POST", created.fixture());
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_PAYMENT_METHOD_IS_SEEDED);
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", created.fixture().testRunId().value());
      }
    }
    return "redirect:" + PATH;
  }

  @PostMapping(PATH + "/remove")
  String removeThePaymentMethod(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String fixtureId,
      @RequestParam(required = false) @Nullable String confirm,
      Model model,
      RedirectAttributes attributes) {
    if (!(Selection.of(provider, testRunId, providers, defaultTestRun)
        instanceof Selection.Chosen chosen)) {
      return render(provider, testRunId, Map.of(), null, null, model);
    }
    if (!REMOVAL_IS_CONFIRMED.equals(confirm)) {
      recordTheRefusal(
          chosen,
          PATH + "/remove",
          PaymentMethodForm.theRefusalOfAnUnconfirmedRemoval(),
          ActionFacts.none()
              .and("fixtureId", fixtureId)
              .and(ActionFacts.REFUSED_BECAUSE, PaymentMethodScreen.A_REMOVAL_IS_CONFIRMED)
              .asRecorded());
      return render(
          provider, testRunId, Map.of(), PaymentMethodScreen.A_REMOVAL_IS_CONFIRMED, null, model);
    }
    switch (fixtures.forget(chosen.module(), chosen.testRunId().value(), fixtureId)) {
      case PaymentMethodFixtureAdmission.Removal.Rejected rejected -> {
        recordTheRefusal(
            chosen,
            PATH + "/remove",
            rejected.theRefusalItReports(),
            ActionFacts.none()
                .and("fixtureId", fixtureId)
                .and(ActionFacts.REFUSED_BECAUSE, rejected.message())
                .asRecorded());
        return render(provider, testRunId, Map.of(), rejected.message(), null, model);
      }
      case PaymentMethodFixtureAdmission.Removal.NotSeeded absent -> {
        recordTheRefusal(
            chosen,
            PATH + "/remove",
            absent.theRefusalItReports(),
            ActionFacts.none()
                .and("fixtureId", fixtureId)
                .and(ActionFacts.REFUSED_BECAUSE, absent.message())
                .asRecorded());
        return render(provider, testRunId, Map.of(), absent.message(), null, model);
      }
      case PaymentMethodFixtureAdmission.Removal.Removed removed -> {
        record(chosen, PATH + "/remove", "POST", removed.fixture());
        attributes.addFlashAttribute(Done.ATTRIBUTE, Done.THE_FIXTURE_IS_REMOVED);
        attributes.addAttribute("provider", chosen.provider());
        attributes.addAttribute("testRunId", removed.fixture().testRunId().value());
      }
    }
    return "redirect:" + PATH;
  }

  private void record(
      Selection.Chosen chosen, String path, String method, PaymentMethodFixture fixture) {
    actions.record(
        AuditedAction.applied(
            chosen.module().id(),
            fixture.testRunId(),
            ObjectOrigin.UI,
            method,
            path,
            ActionVerdict.applied(HttpStatus.FOUND.value()),
            PaymentMethodFixtureFacts.of(fixture)));
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

  private String render(
      @Nullable String provider,
      @Nullable String testRunId,
      Map<String, String> submitted,
      @Nullable String refusal,
      @Nullable String refusedProperty,
      Model model) {
    model.addAttribute("providers", providers.names());
    model.addAttribute("path", PATH);
    model.addAttribute("screen", VIEW);
    model.addAttribute("provider", provider);
    model.addAttribute("testRunId", testRunId);
    switch (Selection.of(provider, testRunId, providers, defaultTestRun)) {
      case Selection.Refused unusable -> model.addAttribute("refusal", unusable.message());
      case Selection.Unnamed unnamed -> model.addAttribute("choose", unnamed.known());
      case Selection.Chosen chosen -> describe(chosen, submitted, refusedProperty, model);
    }
    if (refusal != null) {
      model.addAttribute("refusal", refusal);
    }
    return VIEW;
  }

  private void describe(
      Selection.Chosen chosen,
      Map<String, String> submitted,
      @Nullable String refusedProperty,
      Model model) {
    SimulatorProvider module = chosen.module();
    TestRunId run = chosen.testRunId();
    model.addAttribute("provider", chosen.provider());
    model.addAttribute("testRunId", run.value());
    List<PaymentMethodFixture> held = seeded.of(module.id(), run);
    List<ScenarioRule> written = scenarios.of(module.id(), run);
    model.addAttribute(
        "methods",
        PaymentMethodScreen.of(
            chosen.provider(), run.value(), held, written, ids -> module.states(run, ids)));
    model.addAttribute(
        "form",
        PaymentMethodForm.of(
            chosen.provider(),
            run.value(),
            module.paymentMethodFields(),
            submitted,
            written,
            refusedProperty));
  }
}
