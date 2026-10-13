package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.PortableScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ScenarioAdmission {

  public static final String THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD =
      "the scenario rule this request asked this provider to hold";

  public static final String THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_FORGET =
      "the scenario rule this request asked this provider to forget";

  public sealed interface Expansion {

    record Rejected(String message) implements Expansion {}

    record Refused(List<ScenarioRefusal> refusals) implements Expansion {

      public Refused {
        refusals = List.copyOf(refusals);
      }
    }

    record Expanded(
        TestRunId testRunId, ScenarioPreset preset, int priority, List<NewScenarioRule> rules)
        implements Expansion {

      public Expanded {
        rules = List.copyOf(rules);
      }

      public ScenarioDocument asDocument(ProviderId provider) {
        return ScenarioDocument.of(testRunId, provider, preset, rules);
      }
    }
  }

  public sealed interface Reception {

    record Rejected(String message) implements Reception {}

    record Refused(List<ScenarioRefusal> refusals) implements Reception {

      public Refused {
        refusals = List.copyOf(refusals);
      }
    }

    record Receivable(TestRunId testRunId, List<NewScenarioRule> rules) implements Reception {

      public Receivable {
        rules = List.copyOf(rules);
      }
    }
  }

  public sealed interface Verdict {

    ActionVerdict.@Nullable Refused theRefusalItReports();

    record Rejected(String message) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.rejected(
            RefusalSubject.noObjectWasNamed(THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD));
      }
    }

    record Refused(List<ScenarioRefusal> refusals) implements Verdict {

      @Override
      public ActionVerdict.Refused theRefusalItReports() {
        return ActionVerdict.refused(
            RefusalSubject.noObjectWasNamed(THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD));
      }
    }

    record Written(List<ScenarioRule> rules) implements Verdict {

      @Override
      public ActionVerdict.@Nullable Refused theRefusalItReports() {
        return null;
      }
    }
  }

  private final ScenarioGrammar grammar;
  private final ScenarioRules rules;
  private final TestRuns testRuns;
  private final TestRunId defaultTestRun;

  public ScenarioAdmission(
      ScenarioGrammar grammar, ScenarioRules rules, TestRuns testRuns, TestRunId defaultTestRun) {
    this.grammar = grammar;
    this.rules = rules;
    this.testRuns = testRuns;
    this.defaultTestRun = defaultTestRun;
  }

  public Verdict write(SimulatorProvider module, DeclaredScenario declared) {
    TestRunId testRunId;
    try {
      testRunId = TestRunId.declaredOr(declared.testRunId(), defaultTestRun);
    } catch (IllegalArgumentException rejected) {
      return rejected(rejected);
    }

    List<ScenarioRefusal> unreadable =
        ScenarioGrammar.theActionTypeThisSimulatorCannotRead(declared.action());
    if (!unreadable.isEmpty()) {
      List<ScenarioRefusal> refusals = new ArrayList<>(unreadable);
      addEach(
          ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(declared.action()), refusals);
      return new Verdict.Refused(List.copyOf(refusals));
    }

    NewScenarioRule rule;
    try {
      rule = grammar.ruleOf(module.id(), testRunId, declared);
    } catch (IllegalArgumentException rejected) {
      return rejected(rejected);
    }

    List<ScenarioRefusal> refusals =
        new ArrayList<>(
            ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(declared.action()));
    addEach(ScenarioGrammar.theSubjectThisRuleCannotNarrowOn(rule), refusals);
    refusals.addAll(module.vocabulary().refusals(rule, rules.of(module.id(), testRunId)));
    if (!refusals.isEmpty()) {
      return new Verdict.Refused(List.copyOf(refusals));
    }

    testRuns.register(testRunId);
    return new Verdict.Written(List.of(rules.add(rule)));
  }

  public Verdict expand(SimulatorProvider module, DeclaredPreset declared) {
    return switch (expansionOf(module, declared)) {
      case Expansion.Rejected rejected -> new Verdict.Rejected(rejected.message());
      case Expansion.Refused refused -> new Verdict.Refused(refused.refusals());
      case Expansion.Expanded expanded -> write(expanded.testRunId(), expanded.rules());
    };
  }

  public Expansion preview(SimulatorProvider module, DeclaredPreset declared) {
    Expansion expansion = expansionOf(module, declared);
    if (!(expansion instanceof Expansion.Expanded expanded)) {
      return expansion;
    }
    List<ScenarioRefusal> withheld =
        ScenarioDocument.theNarrowingsNoDocumentCarries(expanded.rules());
    return withheld.isEmpty() ? expanded : new Expansion.Refused(withheld);
  }

  public Expansion expansionOf(SimulatorProvider module, DeclaredPreset declared) {
    TestRunId testRunId;
    PresetRequest request;
    try {
      testRunId = TestRunId.declaredOr(declared.testRunId(), defaultTestRun);
      request =
          new PresetRequest(
              ScenarioGrammar.presetOf(declared.preset()),
              testRunId,
              declared.priority() == null ? 0 : declared.priority(),
              ScenarioGrammar.matchOf(declared.match()));
    } catch (IllegalArgumentException rejected) {
      return new Expansion.Rejected(String.valueOf(rejected.getMessage()));
    }

    PresetExpansion expansion = module.expand(request);
    if (expansion instanceof PresetExpansion.NotExpressible unexpressed) {
      return new Expansion.Refused(List.of(new ScenarioRefusal("preset", unexpressed.reason())));
    }
    List<NewScenarioRule> expanded = ((PresetExpansion.Rules) expansion).rules();

    List<ScenarioRefusal> refusals = theRefusalsOf(module, testRunId, expanded);
    if (!refusals.isEmpty()) {
      return new Expansion.Refused(refusals);
    }
    return new Expansion.Expanded(testRunId, request.preset(), request.priority(), expanded);
  }

  public Verdict takeBack(SimulatorProvider module, ScenarioDocument document) {
    return switch (theRulesItWouldWrite(module, document)) {
      case Reception.Rejected rejected -> new Verdict.Rejected(rejected.message());
      case Reception.Refused refused -> new Verdict.Refused(refused.refusals());
      case Reception.Receivable receivable -> write(receivable.testRunId(), receivable.rules());
    };
  }

  public Reception theRulesItWouldWrite(SimulatorProvider module, ScenarioDocument document) {
    List<ScenarioRefusal> unwritable = document.theReasonsItCannotBeTakenBackBy(module.id());
    if (!unwritable.isEmpty()) {
      return new Reception.Refused(unwritable);
    }

    List<ScenarioRefusal> unreadable = new ArrayList<>();
    for (PortableScenario carried : document.carriedRules()) {
      addEach(ScenarioGrammar.theActionTypeThisSimulatorCannotRead(carried.action()), unreadable);
      addEach(
          ScenarioGrammar.theActionFieldsThisSimulatorCannotPerform(carried.action()), unreadable);
    }
    if (!unreadable.isEmpty()) {
      return new Reception.Refused(List.copyOf(unreadable));
    }

    TestRunId testRunId;
    List<NewScenarioRule> incoming = new ArrayList<>();
    try {
      testRunId = TestRunId.declaredOr(document.testRunId(), defaultTestRun);
      for (PortableScenario carried : document.carriedRules()) {
        incoming.add(grammar.ruleOf(module.id(), testRunId, carried.asDeclared()));
      }
    } catch (IllegalArgumentException rejected) {
      return new Reception.Rejected(String.valueOf(rejected.getMessage()));
    }

    List<ScenarioRefusal> refusals = theRefusalsOf(module, testRunId, incoming);
    if (!refusals.isEmpty()) {
      return new Reception.Refused(refusals);
    }
    return new Reception.Receivable(testRunId, List.copyOf(incoming));
  }

  private Verdict write(TestRunId testRunId, List<NewScenarioRule> written) {
    testRuns.register(testRunId);
    return new Verdict.Written(rules.addAll(written));
  }

  private List<ScenarioRefusal> theRefusalsOf(
      SimulatorProvider module, TestRunId testRunId, List<NewScenarioRule> proposed) {
    List<ScenarioRefusal> refusals = new ArrayList<>();
    for (NewScenarioRule rule : proposed) {
      addEach(grammar.theDelaysThisSimulatorWillNotHold(rule.action()), refusals);
    }
    addEach(ScenarioGrammar.theSubjectTheseRulesCannotNarrowOn(proposed), refusals);
    addEach(module.vocabulary().refusals(proposed, rules.of(module.id(), testRunId)), refusals);
    return List.copyOf(refusals);
  }

  private static Verdict rejected(IllegalArgumentException rejected) {
    return new Verdict.Rejected(String.valueOf(rejected.getMessage()));
  }

  private static void addEach(List<ScenarioRefusal> found, List<ScenarioRefusal> refusals) {
    for (ScenarioRefusal refusal : found) {
      if (!refusals.contains(refusal)) {
        refusals.add(refusal);
      }
    }
  }
}
