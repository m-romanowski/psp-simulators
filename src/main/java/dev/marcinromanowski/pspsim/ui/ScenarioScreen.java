package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioAdmission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ScenarioScreen {

  public record Fact(String property, String instead) {}

  public record Vocabulary(
      List<String> operations,
      List<Fact> factsThisProviderNeverCarries,
      List<String> actionTypes,
      List<String> faults,
      List<String> keyings) {

    public Vocabulary {
      operations = List.copyOf(operations);
      factsThisProviderNeverCarries = List.copyOf(factsThisProviderNeverCarries);
      actionTypes = List.copyOf(actionTypes);
      faults = List.copyOf(faults);
      keyings = List.copyOf(keyings);
    }

    public boolean carries(String property) {
      for (Fact fact : factsThisProviderNeverCarries) {
        if (fact.property().equals(property)) {
          return false;
        }
      }
      return true;
    }
  }

  public record Call(
      String auditId,
      Instant at,
      String direction,
      String method,
      @Nullable String path,
      @Nullable Integer httpStatus,
      @Nullable String outcome,
      @Nullable String providerObjectId) {}

  public record Decided(List<Call> calls, boolean everyCallItDecided) {

    public Decided {
      calls = List.copyOf(calls);
    }
  }

  public record Row(
      String scenarioId,
      int priority,
      int specificity,
      long arrival,
      @Nullable String operation,
      String action,
      @Nullable String providerCode,
      @Nullable String fault,
      @Nullable Integer httpStatus,
      @Nullable Long delayMs,
      @Nullable Long settlesAfterMs,
      boolean answeredWithoutTheOperation,
      @Nullable String story,
      @Nullable String keyedOn,
      @Nullable String whenState,
      @Nullable String thenState,
      List<String> narrows,
      Instant createdAt,
      boolean open,
      List<Disclosure.Field> match,
      @Nullable Decided decided) {

    public Row {
      narrows = List.copyOf(narrows);
      match = List.copyOf(match);
    }
  }

  public record Instance(String story, String state) {}

  public record View(
      String provider,
      String testRunId,
      Vocabulary vocabulary,
      List<Row> rules,
      List<Instance> storiesKeyedOnTheTestRun,
      boolean valuesShown) {

    public View {
      rules = List.copyOf(rules);
      storiesKeyedOnTheTestRun = List.copyOf(storiesKeyedOnTheTestRun);
    }
  }

  public interface Decisions {
    List<AuditRecord> of(String scenarioId);
  }

  public interface DocumentReader {
    ScenarioDocument read(String pasted);
  }

  public record Proposed(
      int priority,
      @Nullable String operation,
      String action,
      @Nullable String providerCode,
      @Nullable String fault,
      @Nullable Integer httpStatus,
      @Nullable Long delayMs,
      @Nullable Long settlesAfterMs,
      @Nullable String story,
      @Nullable String keyedOn,
      @Nullable String whenState,
      @Nullable String thenState) {}

  public record Preview(
      String preset, String provider, int priority, List<Proposed> rules, String document) {

    public Preview {
      rules = List.copyOf(rules);
    }
  }

  public sealed interface Pasted {

    record Document(ScenarioDocument document) implements Pasted {}

    record Unreadable(String message) implements Pasted {}
  }

  public static final String AN_EXPANSION_IS_CONFIRMED = "yes";

  public static final String THE_RULES_ARE_SHOWN_BEFORE_THEY_ARE_WRITTEN =
      "a preset writes the rules of a whole flow at once, and which rules those are is this"
          + " provider's answer and not the preset's, so this form shows them before it writes any:"
          + " read the rules below, tick confirm and expand the same preset again to write them";

  public static final String NOTHING_WAS_PASTED =
      "document must carry the JSON a preview of this screen or of the control API produced; an"
          + " empty box names no rule to take back";

  public static final String THE_DOCUMENT_IS_NOT_JSON =
      "document must be the JSON a preview produced, and what was pasted is not JSON this"
          + " simulator can read at all";

  public static ActionVerdict.Refused theRefusalOfRulesItDidNotWrite() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(
            ScenarioAdmission.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_HOLD));
  }

  public static Pasted pastedInto(
      @Nullable String document, String testRunId, DocumentReader reader) {
    String pasted = document == null ? "" : document.strip();
    if (pasted.isEmpty()) {
      return new Pasted.Unreadable(NOTHING_WAS_PASTED);
    }
    ScenarioDocument read;
    try {
      read = reader.read(pasted);
    } catch (RuntimeException unreadable) {
      return new Pasted.Unreadable(THE_DOCUMENT_IS_NOT_JSON);
    }
    return new Pasted.Document(
        new ScenarioDocument(testRunId, read.provider(), read.preset(), read.carriedRules()));
  }

  public static Preview previewOf(
      String preset,
      String provider,
      int priority,
      List<NewScenarioRule> expanded,
      String document) {
    List<Proposed> shown = new ArrayList<>();
    for (NewScenarioRule rule : expanded) {
      shown.add(proposedOf(rule));
    }
    return new Preview(preset, provider, priority, shown, document);
  }

  public static Proposed proposalOf(NewScenarioRule rule) {
    return proposedOf(rule);
  }

  private static Proposed proposedOf(NewScenarioRule rule) {
    ScenarioStory step = rule.story();
    ScenarioAction action = rule.action();
    TransportFault fault = ScenarioGrammar.faultOf(action);
    return new Proposed(
        rule.priority(),
        rule.match().operation(),
        ScenarioGrammar.typeOf(action),
        action.theProviderCodeItComesToRestWith(),
        fault == null ? null : fault.kind().name(),
        fault instanceof TransportFault.ServerError answered ? answered.httpStatus() : null,
        heldForOf(action, fault),
        settlesAfterOf(action),
        step == null ? null : step.name(),
        step == null ? null : step.keyedOn().name(),
        step == null ? null : step.whenState(),
        step == null ? null : step.thenState());
  }

  private static final Comparator<ScenarioRule> THE_ORDER_THE_ENGINE_CONSULTS_THEM =
      Comparator.comparingInt(ScenarioRule::priority)
          .thenComparingInt(ScenarioRule::specificity)
          .thenComparingLong(ScenarioRule::arrival)
          .reversed();

  private ScenarioScreen() {}

  public static View of(
      String provider,
      String testRunId,
      List<ScenarioRule> rules,
      ProviderVocabulary vocabulary,
      Map<String, String> storiesKeyedOnTheTestRun,
      @Nullable String openScenarioId,
      boolean valuesAsked,
      int callsShownPerRule,
      Decisions decisions) {
    List<ScenarioRule> consulted = new ArrayList<>(rules);
    consulted.sort(THE_ORDER_THE_ENGINE_CONSULTS_THEM);
    List<Row> shown = new ArrayList<>();
    for (ScenarioRule rule : consulted) {
      boolean open = rule.scenarioId().equals(openScenarioId);
      shown.add(rowOf(rule, open, valuesAsked, callsShownPerRule, decisions));
    }
    return new View(
        provider,
        testRunId,
        vocabularyOf(vocabulary),
        shown,
        instancesOf(storiesKeyedOnTheTestRun),
        valuesAsked);
  }

  private static Vocabulary vocabularyOf(ProviderVocabulary vocabulary) {
    List<Fact> absent = new ArrayList<>();
    for (MatchFact fact : MatchFact.values()) {
      String instead = vocabulary.factsItNeverCarries().get(fact);
      if (instead != null) {
        absent.add(new Fact(fact.property(), instead));
      }
    }
    List<String> faults = new ArrayList<>();
    for (TransportFaultKind kind : TransportFaultKind.values()) {
      faults.add(kind.name());
    }
    List<String> keyings = new ArrayList<>();
    for (StoryKeying keying : StoryKeying.values()) {
      keyings.add(keying.name());
    }
    return new Vocabulary(
        vocabulary.operationNames(),
        absent,
        ScenarioGrammar.theDeclarableActionTypes(),
        faults,
        keyings);
  }

  private static List<Instance> instancesOf(Map<String, String> states) {
    List<Instance> instances = new ArrayList<>();
    states.forEach((story, state) -> instances.add(new Instance(story, state)));
    return List.copyOf(instances);
  }

  private static Row rowOf(
      ScenarioRule rule,
      boolean open,
      boolean valuesAsked,
      int callsShownPerRule,
      Decisions decisions) {
    ScenarioStory step = rule.story();
    ScenarioAction action = rule.action();
    TransportFault fault = ScenarioGrammar.faultOf(action);
    Map<String, List<String>> narrowings = theFactsItNarrowsOn(rule.match());
    return new Row(
        rule.scenarioId(),
        rule.priority(),
        rule.specificity(),
        rule.arrival(),
        rule.match().operation(),
        ScenarioGrammar.typeOf(action),
        action.theProviderCodeItComesToRestWith(),
        fault == null ? null : fault.kind().name(),
        fault instanceof TransportFault.ServerError answered ? answered.httpStatus() : null,
        heldForOf(action, fault),
        settlesAfterOf(action),
        action instanceof ScenarioAction.TransportFaultInsteadOfOperation,
        step == null ? null : step.name(),
        step == null ? null : step.keyedOn().name(),
        step == null ? null : step.whenState(),
        step == null ? null : step.thenState(),
        List.copyOf(narrowings.keySet()),
        rule.createdAt(),
        open,
        open ? Disclosure.of(narrowings, valuesAsked) : List.of(),
        open ? decidedBy(rule, callsShownPerRule, decisions) : null);
  }

  private static @Nullable Long settlesAfterOf(ScenarioAction action) {
    return action instanceof ScenarioAction.ProcessingThenTerminal settling
        ? settling.settlesAfter().toMillis()
        : null;
  }

  private static @Nullable Long heldForOf(ScenarioAction action, @Nullable TransportFault fault) {
    if (action instanceof ScenarioAction.DeliveryDelayed held) {
      return held.held().toMillis();
    }
    return fault instanceof TransportFault.DelayedResponse waited
        ? waited.delay().toMillis()
        : null;
  }

  private static Map<String, List<String>> theFactsItNarrowsOn(ScenarioMatch match) {
    Map<String, List<String>> narrowings = new LinkedHashMap<>();
    named(narrowings, MatchFact.MERCHANT_ID, match.merchantId());
    named(narrowings, MatchFact.ORDER_ID, match.orderId());
    named(narrowings, MatchFact.PROVIDER_OBJECT_ID, match.providerObjectId());
    named(narrowings, MatchFact.CUSTOMER_ID, match.customerId());
    named(narrowings, MatchFact.PAYMENT_METHOD_ID, match.paymentMethodId());
    Amount amount = match.amount();
    named(narrowings, MatchFact.AMOUNT, amount == null ? null : amount.value());
    named(narrowings, MatchFact.CURRENCY, match.currency());
    named(narrowings, MatchFact.EVENT_TYPE, match.eventType());
    match.header().forEach((name, value) -> narrowings.put("match.header." + name, List.of(value)));
    return Collections.unmodifiableMap(narrowings);
  }

  private static void named(
      Map<String, List<String>> narrowings, MatchFact fact, @Nullable String value) {
    if (value != null) {
      narrowings.put(fact.property(), List.of(value));
    }
  }

  private static Decided decidedBy(ScenarioRule rule, int callsShownPerRule, Decisions decisions) {
    List<AuditRecord> records = decisions.of(rule.scenarioId());
    List<Call> calls = new ArrayList<>();
    for (int at = records.size() - 1; at >= 0; at--) {
      calls.add(callOf(records.get(at)));
    }
    return new Decided(calls, calls.size() < callsShownPerRule);
  }

  private static Call callOf(AuditRecord record) {
    ProviderOutcome outcome = record.outcome();
    return new Call(
        record.auditId(),
        record.createdAt(),
        record.direction().name(),
        record.method(),
        record.path(),
        record.httpStatus(),
        outcome == null ? null : outcome.name(),
        record.providerObjectId());
  }
}
