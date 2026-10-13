package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioComparison;
import dev.marcinromanowski.pspsim.kernel.spi.ScenarioCopy;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ScenarioDiffScreen {

  public static final String TWO_RULES_ARE_NAMED_BEFORE_ANYTHING_IS_COMPARED =
      "a comparison reads two rules of this test run. Choose one on each side and compare them: the"
          + " screen names which fields differ and what each side says, and it compares nothing"
          + " until both sides are named.";

  public static final String A_HEADER_NARROWING_IS_NOT_COPIED_ON_A_SCREEN =
      "this rule narrows on a request header, and a copy form would have to put that header's value"
          + " back on this page to carry it. A header value is supplied verbatim by whoever wrote"
          + " the rule and is the one part of a rule that can hold a credential, so the copy form is"
          + " not offered for it. Copy this rule through the control API, which takes the rule and"
          + " the delta and renders neither.";

  public static final String THEY_DIFFER_IN_NO_FIELD_A_SCENARIO_HOLDS =
      "These two rules say the same thing in every field a scenario holds. They differ in no"
          + " property named below, so whatever makes them behave differently is not in either"
          + " rule: the dimensions this comparison cannot reach are listed at the foot of this"
          + " screen.";

  public static final String THEY_DIFFER_IN_NO_FIELD_THIS_COMPARISON_READS =
      "These two rules differ in no property this comparison reads, and one of the properties they"
          + " say alike is said alike on its name alone: its value is withheld from both sides and"
          + " was never compared. They may therefore narrow on the same header with different"
          + " values, and one of them match a request the other refuses. The dimensions this"
          + " comparison cannot reach are listed at the foot of this screen.";

  public static final String THE_LEFT = "left";

  public static final String THE_RIGHT = "right";

  public static String theRunHoldsNoRuleNamedOnThe(
      String side, String scenarioId, String provider, String testRunId) {
    return "the rule named on the "
        + side
        + ", "
        + scenarioId
        + ", is not written for provider "
        + provider
        + " in test run "
        + testRunId
        + ": it may have been deleted, or the run reset, while this page was open. Nothing was"
        + " compared, because a comparison missing one side would read as a rule that narrows on"
        + " nothing.";
  }

  public record Choice(
      String scenarioId, @Nullable String operation, String action, @Nullable String story) {}

  public record View(
      String provider,
      String testRunId,
      List<Choice> choices,
      List<Choice> choicesOnTheRight,
      @Nullable String left,
      @Nullable String right,
      @Nullable String rightProvider,
      @Nullable String rightTestRunId,
      boolean compared,
      ScenarioComparison.@Nullable Side onTheLeft,
      ScenarioComparison.@Nullable Side onTheRight,
      List<ScenarioComparison.Difference> differences,
      @Nullable String whenNothingDiffers,
      List<String> thePropertiesBothSidesSayAlike,
      List<String> thePropertiesSaidAlikeOnTheirNameAlone,
      @Nullable String whatAPropertySaidAlikeOnItsNameDoesNotCompare,
      List<ScenarioComparison.NotHeldByAScenario> theDimensionsItCannotCompare,
      @Nullable ScenarioForm copyOfTheLeft,
      @Nullable String whyTheLeftIsNotOfferedAsACopy,
      @Nullable ScenarioForm copyOfTheRight,
      @Nullable String whyTheRightIsNotOfferedAsACopy,
      String theHeaderValueItWithholds,
      String aCopyIsANewRule) {

    public View {
      choices = List.copyOf(choices);
      choicesOnTheRight = List.copyOf(choicesOnTheRight);
      differences = List.copyOf(differences);
      thePropertiesBothSidesSayAlike = List.copyOf(thePropertiesBothSidesSayAlike);
      thePropertiesSaidAlikeOnTheirNameAlone = List.copyOf(thePropertiesSaidAlikeOnTheirNameAlone);
      theDimensionsItCannotCompare = List.copyOf(theDimensionsItCannotCompare);
    }
  }

  public record Asked(
      @Nullable String left,
      @Nullable String right,
      @Nullable String rightProvider,
      @Nullable String rightTestRunId) {

    public static Asked nothing() {
      return new Asked(null, null, null, null);
    }
  }

  private ScenarioDiffScreen() {}

  public static ActionVerdict.Refused theRefusalOfACopyItCouldNotRead() {
    return ActionVerdict.refused(
        RefusalSubject.noObjectWasNamed(
            ScenarioCopy.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_COPY));
  }

  public static ActionVerdict.Refused theRefusalOfARuleItCannotCopy() {
    return ActionVerdict.notFound(
        RefusalSubject.noObjectWasNamed(
            ScenarioCopy.THE_SCENARIO_RULE_IT_ASKED_THIS_PROVIDER_TO_COPY));
  }

  public static View of(
      String provider,
      String testRunId,
      List<ScenarioRule> ofTheRun,
      List<ScenarioRule> ofTheRunOnTheRight,
      Asked asked,
      ScenarioComparison.Comparison.@Nullable Compared compared,
      @Nullable ScenarioRule onTheLeft,
      @Nullable ScenarioRule onTheRight) {
    return new View(
        provider,
        testRunId,
        choicesOf(ofTheRun),
        choicesOf(ofTheRunOnTheRight),
        asked.left(),
        asked.right(),
        asked.rightProvider(),
        asked.rightTestRunId(),
        compared != null,
        compared == null ? null : compared.onTheLeft(),
        compared == null ? null : compared.onTheRight(),
        compared == null ? List.of() : compared.differences(),
        theSentenceOfTwoRulesThatDifferInNothing(compared),
        compared == null ? List.of() : compared.thePropertiesBothSidesSayAlike(),
        compared == null ? List.of() : compared.thePropertiesSaidAlikeOnTheirNameAlone(),
        compared == null ? null : compared.whatAPropertySaidAlikeOnItsNameDoesNotCompare(),
        compared == null ? List.of() : compared.theDimensionsItCannotCompare(),
        theCopyItOffers(onTheLeft),
        whyItOffersNoCopy(onTheLeft),
        theCopyItOffers(onTheRight),
        whyItOffersNoCopy(onTheRight),
        ScenarioComparison.THE_HEADER_VALUE_NO_COMPARISON_SHOWS,
        ScenarioCopy.A_COPY_IS_A_NEW_RULE);
  }

  public static @Nullable String theSentenceOfTwoRulesThatDifferInNothing(
      ScenarioComparison.Comparison.@Nullable Compared compared) {
    if (compared == null || !compared.differences().isEmpty()) {
      return null;
    }
    return compared.saysAPropertyAlikeOnItsNameAlone()
        ? THEY_DIFFER_IN_NO_FIELD_THIS_COMPARISON_READS
        : THEY_DIFFER_IN_NO_FIELD_A_SCENARIO_HOLDS;
  }

  public static @Nullable ScenarioForm theCopyItOffers(@Nullable ScenarioRule rule) {
    if (rule == null || narrowsOnAHeader(rule)) {
      return null;
    }
    return formOf(rule);
  }

  public static @Nullable String whyItOffersNoCopy(@Nullable ScenarioRule rule) {
    if (rule == null || !narrowsOnAHeader(rule)) {
      return null;
    }
    return A_HEADER_NARROWING_IS_NOT_COPIED_ON_A_SCREEN;
  }

  private static boolean narrowsOnAHeader(ScenarioRule rule) {
    return !rule.match().header().isEmpty();
  }

  private static List<Choice> choicesOf(List<ScenarioRule> ofTheRun) {
    List<ScenarioRule> ordered = new ArrayList<>(ofTheRun);
    ordered.sort(ScenarioRule.THE_ORDER_THE_ENGINE_CONSULTS_THEM.reversed());
    List<Choice> choices = new ArrayList<>();
    for (ScenarioRule rule : ordered) {
      ScenarioStory step = rule.story();
      choices.add(
          new Choice(
              rule.scenarioId(),
              rule.match().operation(),
              ScenarioGrammar.typeOf(rule.action()),
              step == null ? null : step.name()));
    }
    return List.copyOf(choices);
  }

  private static ScenarioForm formOf(ScenarioRule rule) {
    ScenarioMatch match = rule.match();
    ScenarioAction action = rule.action();
    ScenarioStory step = rule.story();
    Amount amount = match.amount();
    TransportFault fault = ScenarioGrammar.faultOf(action);
    return new ScenarioForm(
        String.valueOf(rule.priority()),
        match.operation(),
        match.merchantId(),
        match.orderId(),
        match.providerObjectId(),
        match.customerId(),
        match.paymentMethodId(),
        amount == null ? null : amount.value(),
        match.currency(),
        match.eventType(),
        null,
        null,
        step == null ? null : step.name(),
        step == null ? null : step.keyedOn().name(),
        step == null ? null : step.whenState(),
        step == null ? null : step.thenState(),
        ScenarioGrammar.typeOf(action),
        action.theProviderCodeItComesToRestWith(),
        fault == null ? null : fault.kind().name(),
        fault instanceof TransportFault.ServerError answered
            ? String.valueOf(answered.httpStatus())
            : null,
        spelled(ScenarioCopy.theMillisecondsItIsHeldBackBy(action)),
        spelled(ScenarioCopy.theMillisecondsItSettlesAfter(action)));
  }

  private static @Nullable String spelled(@Nullable Long milliseconds) {
    return milliseconds == null ? null : String.valueOf(milliseconds);
  }
}
