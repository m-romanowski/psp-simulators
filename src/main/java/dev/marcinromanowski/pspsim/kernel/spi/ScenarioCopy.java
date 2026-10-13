package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredAction;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.DeclaredScenario;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRefusal;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public final class ScenarioCopy {

  public static final String THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_COPY =
      "the scenario rule this request asked this provider to copy";

  public static final String A_COPY_IS_A_NEW_RULE =
      "a copy is written as a new rule, with a scenario id and an arrival of its own. The rule it"
          + " was copied from is left exactly as it was, because nothing in this simulator rewrites"
          + " a written rule: the audit rows naming the old id would then describe a rule that no"
          + " longer decides what they say it decided.";

  public static final String MATCH_HEADER = "match.header";

  private static final List<String> THE_PROPERTIES_A_COPY_MAY_CLEAR =
      List.of(
          "match.operation",
          "match.merchantId",
          "match.orderId",
          "match.providerObjectId",
          "match.customerId",
          "match.paymentMethodId",
          "match.amount",
          "match.currency",
          "match.eventType",
          MATCH_HEADER,
          "action.providerCode",
          "action.fault",
          "action.httpStatus",
          "action.delayMs",
          "action.settlesAfterMs",
          "story",
          "keyedOn",
          "whenState",
          "thenState");

  public sealed interface Copy {

    record Refused(List<ScenarioRefusal> refusals) implements Copy {

      public Refused {
        refusals = List.copyOf(refusals);
      }
    }

    record Declared(DeclaredScenario scenario) implements Copy {}
  }

  private ScenarioCopy() {}

  public static List<String> thePropertiesACopyMayClear() {
    return THE_PROPERTIES_A_COPY_MAY_CLEAR;
  }

  public static DeclaredScenario asDeclared(ScenarioRule rule) {
    ScenarioStory step = rule.story();
    return new DeclaredScenario(
        rule.testRunId().value(),
        rule.priority(),
        step == null ? null : step.name(),
        step == null ? null : step.keyedOn().name(),
        step == null ? null : step.whenState(),
        step == null ? null : step.thenState(),
        theMatchOf(rule),
        theActionOf(rule));
  }

  public static Copy of(ScenarioRule source, DeclaredScenarioCopy asked) {
    List<ScenarioRefusal> refusals = new ArrayList<>();
    List<String> cleared = new ArrayList<>();
    for (String property : asked.thePropertiesItClears()) {
      if (THE_PROPERTIES_A_COPY_MAY_CLEAR.contains(property)) {
        cleared.add(property);
      } else {
        refusals.add(aPropertyNoCopyClears(property));
      }
    }
    DeclaredScenario written = theCopyItWouldWrite(source, asked.edit(), cleared, refusals);
    if (!refusals.isEmpty()) {
      return new Copy.Refused(List.copyOf(refusals));
    }
    return new Copy.Declared(written);
  }

  private static DeclaredScenario theCopyItWouldWrite(
      ScenarioRule source,
      @Nullable DeclaredScenario edit,
      List<String> cleared,
      List<ScenarioRefusal> refusals) {
    DeclaredScenario carried = asDeclared(source);
    DeclaredMatch held = theMatchOf(source);
    DeclaredAction decided = theActionOf(source);
    DeclaredMatch editedMatch = edit == null ? null : edit.match();
    DeclaredAction editedAction = edit == null ? null : edit.action();
    return new DeclaredScenario(
        given(edit == null ? null : edit.testRunId(), carried.testRunId()),
        given(edit == null ? null : edit.priority(), carried.priority()),
        chosen("story", cleared, edit == null ? null : edit.story(), carried.story(), refusals),
        chosen(
            "keyedOn", cleared, edit == null ? null : edit.keyedOn(), carried.keyedOn(), refusals),
        chosen(
            "whenState",
            cleared,
            edit == null ? null : edit.whenState(),
            carried.whenState(),
            refusals),
        chosen(
            "thenState",
            cleared,
            edit == null ? null : edit.thenState(),
            carried.thenState(),
            refusals),
        new DeclaredMatch(
            chosen(
                "match.merchantId",
                cleared,
                editedMatch == null ? null : editedMatch.merchantId(),
                held.merchantId(),
                refusals),
            chosen(
                "match.orderId",
                cleared,
                editedMatch == null ? null : editedMatch.orderId(),
                held.orderId(),
                refusals),
            chosen(
                "match.providerObjectId",
                cleared,
                editedMatch == null ? null : editedMatch.providerObjectId(),
                held.providerObjectId(),
                refusals),
            chosen(
                "match.customerId",
                cleared,
                editedMatch == null ? null : editedMatch.customerId(),
                held.customerId(),
                refusals),
            chosen(
                "match.paymentMethodId",
                cleared,
                editedMatch == null ? null : editedMatch.paymentMethodId(),
                held.paymentMethodId(),
                refusals),
            chosen(
                "match.amount",
                cleared,
                editedMatch == null ? null : editedMatch.amount(),
                held.amount(),
                refusals),
            chosen(
                "match.currency",
                cleared,
                editedMatch == null ? null : editedMatch.currency(),
                held.currency(),
                refusals),
            chosen(
                "match.operation",
                cleared,
                editedMatch == null ? null : editedMatch.operation(),
                held.operation(),
                refusals),
            chosen(
                "match.eventType",
                cleared,
                editedMatch == null ? null : editedMatch.eventType(),
                held.eventType(),
                refusals),
            theHeaderItNarrowsOn(cleared, editedMatch, source.match().header(), refusals)),
        new DeclaredAction(
            given(editedAction == null ? null : editedAction.type(), decided.type()),
            editedAction == null ? null : editedAction.failuresBeforeSuccess(),
            chosen(
                "action.providerCode",
                cleared,
                editedAction == null ? null : editedAction.providerCode(),
                decided.providerCode(),
                refusals),
            chosen(
                "action.fault",
                cleared,
                editedAction == null ? null : editedAction.fault(),
                decided.fault(),
                refusals),
            chosen(
                "action.httpStatus",
                cleared,
                editedAction == null ? null : editedAction.httpStatus(),
                decided.httpStatus(),
                refusals),
            editedAction == null ? null : editedAction.terminalStatus(),
            chosen(
                "action.delayMs",
                cleared,
                editedAction == null ? null : editedAction.delayMs(),
                decided.delayMs(),
                refusals),
            chosen(
                "action.settlesAfterMs",
                cleared,
                editedAction == null ? null : editedAction.settlesAfterMs(),
                decided.settlesAfterMs(),
                refusals)));
  }

  private static Map<String, String> theHeaderItNarrowsOn(
      List<String> cleared,
      @Nullable DeclaredMatch edited,
      Map<String, String> carried,
      List<ScenarioRefusal> refusals) {
    Map<String, String> edit = edited == null ? null : edited.header();
    boolean edits = edit != null && !edit.isEmpty();
    if (cleared.contains(MATCH_HEADER)) {
      if (edits) {
        refusals.add(clearedAndGiven(MATCH_HEADER));
      }
      return Map.of();
    }
    return Map.copyOf(edits ? edit : carried);
  }

  private static <T> @Nullable T chosen(
      String property,
      List<String> cleared,
      @Nullable T edited,
      @Nullable T carried,
      List<ScenarioRefusal> refusals) {
    if (cleared.contains(property)) {
      if (edited != null) {
        refusals.add(clearedAndGiven(property));
      }
      return null;
    }
    return given(edited, carried);
  }

  private static <T> @Nullable T given(@Nullable T edited, @Nullable T carried) {
    return edited == null ? carried : edited;
  }

  private static ScenarioRefusal clearedAndGiven(String property) {
    return new ScenarioRefusal(
        property,
        "is cleared and given a value by the same copy, and a property cannot be both: say which of"
            + " the two the copy is meant to carry");
  }

  private static ScenarioRefusal aPropertyNoCopyClears(String property) {
    return new ScenarioRefusal(
        "clear",
        "a copy clears one of "
            + String.join(", ", THE_PROPERTIES_A_COPY_MAY_CLEAR)
            + ", and "
            + property
            + " is not one of them: a name this simulator does not read would be taken as a"
            + " property cleared and leave the copy carrying it");
  }

  public static @Nullable Long theMillisecondsItIsHeldBackBy(ScenarioAction action) {
    if (action instanceof ScenarioAction.DeliveryDelayed held) {
      return held.held().toMillis();
    }
    return ScenarioGrammar.faultOf(action) instanceof TransportFault.DelayedResponse waited
        ? waited.delay().toMillis()
        : null;
  }

  public static @Nullable Long theMillisecondsItSettlesAfter(ScenarioAction action) {
    return action instanceof ScenarioAction.ProcessingThenTerminal settling
        ? settling.settlesAfter().toMillis()
        : null;
  }

  private static @Nullable Integer theStatusItAnswersWith(@Nullable TransportFault fault) {
    return fault instanceof TransportFault.ServerError answered ? answered.httpStatus() : null;
  }

  private static DeclaredMatch theMatchOf(ScenarioRule rule) {
    ScenarioMatch match = rule.match();
    Amount amount = match.amount();
    return new DeclaredMatch(
        match.merchantId(),
        match.orderId(),
        match.providerObjectId(),
        match.customerId(),
        match.paymentMethodId(),
        amount == null ? null : amount.value(),
        match.currency(),
        match.operation(),
        match.eventType(),
        theHeadersInTheOrderTheRuleNamedThem(match));
  }

  private static SequencedMap<String, String> theHeadersInTheOrderTheRuleNamedThem(
      ScenarioMatch match) {
    LinkedHashMap<String, String> named = new LinkedHashMap<>(match.header());
    return Collections.unmodifiableSequencedMap(named);
  }

  private static DeclaredAction theActionOf(ScenarioRule rule) {
    ScenarioAction action = rule.action();
    TransportFault fault = ScenarioGrammar.faultOf(action);
    return new DeclaredAction(
        ScenarioGrammar.typeOf(action),
        null,
        action.theProviderCodeItComesToRestWith(),
        fault == null ? null : fault.kind().name(),
        theStatusItAnswersWith(fault),
        null,
        theMillisecondsItIsHeldBackBy(action),
        theMillisecondsItSettlesAfter(action));
  }
}
