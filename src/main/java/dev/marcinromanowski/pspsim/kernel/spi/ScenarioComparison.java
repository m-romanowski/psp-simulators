package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioGrammar;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

public final class ScenarioComparison {

  public static final String PRIORITY = "priority";

  public static final String OPERATION = "match.operation";

  public static final String PROVIDER_CODE = "action.providerCode";

  public static final String ACTION_TYPE = "action.type";

  public static final String SETTLES_AFTER = "action.settlesAfterMs";

  public static final String HEADER = "match.header.";

  public static final String THE_HEADER_VALUE_NO_COMPARISON_SHOWS =
      ScenarioDocument.A_HEADER_VALUE_LEAVES_NOTHING;

  public static final String A_HEADER_SAID_ALIKE_IS_ALIKE_IN_ITS_NAME_ALONE =
      "a header property both sides say alike compares the header's name and never its value. A"
          + " header value is supplied verbatim by whoever wrote the rule and is the one part of a"
          + " rule that can hold a credential, so it is withheld on both sides and no comparison"
          + " reads it. Two rules narrowing on the same header with different values are reported"
          + " here, and one of them will match a request the other refuses, so this row is not a"
          + " claim that the two rules behave alike.";

  public static final String A_RULE_OF_ONE_PROVIDER_MEANS_NOTHING_TO_ANOTHER =
      "two scenarios of different providers are not compared. An operation, a decline code, an"
          + " event type and a processing state are spelled the way the provider that answers them"
          + " spells them, and every difference here is described in one provider's published"
          + " vocabulary, so a row putting one provider's operation beside another's would report a"
          + " difference where there is only a second contract. Compare two scenarios of one"
          + " provider, and read the other provider's rules against its own documentation.";

  public record Named(boolean named, boolean withheld, @Nullable String value) {

    public Named {
      if (!named && (withheld || value != null)) {
        throw new IllegalArgumentException(
            "a side that does not name a property carries no value and withholds none, because a"
                + " reader shown a withheld marker where a rule is simply silent would read a"
                + " narrowing into a rule that carries none");
      }
      if (withheld && value != null) {
        throw new IllegalArgumentException(
            "a value withheld from this comparison is not carried beside the marker that withholds"
                + " it, or the marker would travel with the thing it was put there to keep off the"
                + " screen");
      }
      if (named && !withheld && value == null) {
        throw new IllegalArgumentException(
            "a side that names a property carries the value it names it with, or says that value is"
                + " withheld");
      }
    }

    public static Named nothing() {
      return new Named(false, false, null);
    }

    public static Named of(String value) {
      return new Named(true, false, value);
    }

    public static Named whoseValueIsWithheld() {
      return new Named(true, true, null);
    }

    public boolean saysTheSameAs(Named other) {
      if (named != other.named || withheld != other.withheld) {
        return false;
      }
      String mine = value;
      return mine == null ? other.value == null : mine.equals(other.value);
    }
  }

  public record Difference(
      String property, Named onTheLeft, Named onTheRight, @Nullable String inTheProvidersWords) {}

  public record Side(
      String provider,
      String testRunId,
      String scenarioId,
      int priority,
      int specificity,
      long arrival) {

    public static Side of(ScenarioRule rule) {
      return new Side(
          rule.provider().value(),
          rule.testRunId().value(),
          rule.scenarioId(),
          rule.priority(),
          rule.specificity(),
          rule.arrival());
    }
  }

  public record NotHeldByAScenario(String dimension, String reason) {}

  public sealed interface Comparison {

    record OfTwoProviders(String onTheLeft, String onTheRight, String reason)
        implements Comparison {}

    record Compared(
        Side onTheLeft,
        Side onTheRight,
        List<Difference> differences,
        List<String> thePropertiesBothSidesSayAlike,
        List<String> thePropertiesSaidAlikeOnTheirNameAlone,
        @Nullable String whatAPropertySaidAlikeOnItsNameDoesNotCompare,
        List<NotHeldByAScenario> theDimensionsItCannotCompare)
        implements Comparison {

      public Compared {
        differences = List.copyOf(differences);
        thePropertiesBothSidesSayAlike = List.copyOf(thePropertiesBothSidesSayAlike);
        thePropertiesSaidAlikeOnTheirNameAlone =
            List.copyOf(thePropertiesSaidAlikeOnTheirNameAlone);
        theDimensionsItCannotCompare = List.copyOf(theDimensionsItCannotCompare);
        if (thePropertiesSaidAlikeOnTheirNameAlone.isEmpty()
            != (whatAPropertySaidAlikeOnItsNameDoesNotCompare == null)) {
          throw new IllegalArgumentException(
              "a comparison that says a property alike on its name alone carries the sentence"
                  + " saying what it did not compare, and one that says none carries no such"
                  + " sentence, because a marker standing beside no property would withhold"
                  + " nothing and a withholding standing beside no marker would not be read");
        }
      }

      public boolean saysAPropertyAlikeOnItsNameAlone() {
        return !thePropertiesSaidAlikeOnTheirNameAlone.isEmpty();
      }
    }
  }

  private ScenarioComparison() {}

  public static Comparison of(
      ScenarioRule onTheLeft, ScenarioRule onTheRight, ProviderVocabulary vocabulary) {
    if (!onTheLeft.provider().equals(onTheRight.provider())) {
      return new Comparison.OfTwoProviders(
          onTheLeft.provider().value(),
          onTheRight.provider().value(),
          A_RULE_OF_ONE_PROVIDER_MEANS_NOTHING_TO_ANOTHER);
    }
    Map<String, Named> left = thePropertiesOf(onTheLeft);
    Map<String, Named> right = thePropertiesOf(onTheRight);
    List<Difference> differences = new ArrayList<>();
    List<String> alike = new ArrayList<>();
    List<String> onTheirNameAlone = new ArrayList<>();
    for (String property : thePropertiesCompared(left, right)) {
      Named mine = left.getOrDefault(property, Named.nothing());
      Named theirs = right.getOrDefault(property, Named.nothing());
      if (mine.saysTheSameAs(theirs)) {
        alike.add(property);
        if (mine.withheld()) {
          onTheirNameAlone.add(property);
        }
      } else {
        differences.add(
            new Difference(
                property,
                mine,
                theirs,
                inTheProvidersWords(property, onTheLeft, onTheRight, vocabulary)));
      }
    }
    return new Comparison.Compared(
        Side.of(onTheLeft),
        Side.of(onTheRight),
        differences,
        alike,
        onTheirNameAlone,
        onTheirNameAlone.isEmpty() ? null : A_HEADER_SAID_ALIKE_IS_ALIKE_IN_ITS_NAME_ALONE,
        theDimensionsAScenarioDoesNotHold());
  }

  public static List<NotHeldByAScenario> theDimensionsAScenarioDoesNotHold() {
    return List.of(
        new NotHeldByAScenario(
            "the state a payment comes to rest in",
            "no scenario action writes a normalized status: a status is derived from the native"
                + " status the answer carries and the operations the object has taken, so a rule"
                + " holds the answer and never the state that answer leaves behind. Two rules that"
                + " differ in nothing here can still leave a payment in different states, and the"
                + " Payments screen is where that is read"),
        new NotHeldByAScenario(
            "the webhook plan",
            "a rule shapes one delivery it matches, by holding it back, repeating it or dropping"
                + " it. Which events a provider sends at all, where they are sent and how often a"
                + " failed attempt is retried are held by the provider module and by the account's"
                + " webhook target, so this compares the shaping and not the plan"),
        new NotHeldByAScenario(
            "what a damaged answer puts on the wire",
            "a rule carries a transport fault by name, and its HTTP status where the fault is a"
                + " server error. How many bytes a truncated body keeps, what a corrupted one"
                + " becomes and where a dropped connection is cut are rendered at the moment of the"
                + " answer and are held by no scenario"),
        new NotHeldByAScenario(
            "the state a story stands in right now",
            "a step names the state it fires in and the state it moves the story to, and the state"
                + " a story actually stands in is held per key by the test run. Two identical steps"
                + " decide differently when their stories stand in different states, and a story"
                + " keyed on the flow stands under keys only the provider can produce"));
  }

  private static List<String> thePropertiesCompared(
      Map<String, Named> left, Map<String, Named> right) {
    List<String> properties = new ArrayList<>(left.keySet());
    for (String property : right.keySet()) {
      if (!properties.contains(property)) {
        properties.add(property);
      }
    }
    return List.copyOf(properties);
  }

  private static Map<String, Named> thePropertiesOf(ScenarioRule rule) {
    Map<String, Named> properties = new LinkedHashMap<>();
    ScenarioMatch match = rule.match();
    ScenarioAction action = rule.action();
    TransportFault fault = ScenarioGrammar.faultOf(action);
    properties.put(PRIORITY, Named.of(String.valueOf(rule.priority())));
    properties.put(OPERATION, named(match.operation()));
    properties.put("match.merchantId", named(match.merchantId()));
    properties.put("match.orderId", named(match.orderId()));
    properties.put("match.providerObjectId", named(match.providerObjectId()));
    properties.put("match.customerId", named(match.customerId()));
    properties.put("match.paymentMethodId", named(match.paymentMethodId()));
    Amount amount = match.amount();
    properties.put("match.amount", named(amount == null ? null : amount.value()));
    properties.put("match.currency", named(match.currency()));
    properties.put("match.eventType", named(match.eventType()));
    for (String header : new TreeSet<>(match.header().keySet())) {
      properties.put(HEADER + header, Named.whoseValueIsWithheld());
    }
    properties.put(ACTION_TYPE, Named.of(ScenarioGrammar.typeOf(action)));
    properties.put(PROVIDER_CODE, named(action.theProviderCodeItComesToRestWith()));
    properties.put("action.fault", named(fault == null ? null : fault.kind().name()));
    properties.put(
        "action.httpStatus",
        named(
            fault instanceof TransportFault.ServerError answered
                ? String.valueOf(answered.httpStatus())
                : null));
    properties.put(
        "action.delayMs", named(spelled(ScenarioCopy.theMillisecondsItIsHeldBackBy(action))));
    properties.put(
        SETTLES_AFTER, named(spelled(ScenarioCopy.theMillisecondsItSettlesAfter(action))));
    ScenarioStory step = rule.story();
    properties.put("story", named(step == null ? null : step.name()));
    properties.put("keyedOn", named(step == null ? null : step.keyedOn().name()));
    properties.put("whenState", named(step == null ? null : step.whenState()));
    properties.put("thenState", named(step == null ? null : step.thenState()));
    return properties;
  }

  private static @Nullable String spelled(@Nullable Long milliseconds) {
    return milliseconds == null ? null : String.valueOf(milliseconds);
  }

  private static Named named(@Nullable String value) {
    return value == null ? Named.nothing() : Named.of(value);
  }

  private static @Nullable String inTheProvidersWords(
      String property,
      ScenarioRule onTheLeft,
      ScenarioRule onTheRight,
      ProviderVocabulary vocabulary) {
    if (property.equals(OPERATION)) {
      return theOperationsItExposes(onTheLeft, onTheRight, vocabulary);
    }
    if (property.equals(PROVIDER_CODE)) {
      return theCodesItDeclinesWith(onTheLeft, onTheRight, vocabulary);
    }
    if (property.equals(ACTION_TYPE) || property.equals(SETTLES_AFTER)) {
      return theOperationsThatReportAProcessingState(onTheLeft, onTheRight, vocabulary);
    }
    return null;
  }

  static String theCodesItDeclinesWith(
      ScenarioRule onTheLeft, ScenarioRule onTheRight, ProviderVocabulary vocabulary) {
    StringBuilder said =
        new StringBuilder("provider ")
            .append(vocabulary.provider().value())
            .append(" declines with ")
            .append(vocabulary.failureCodes().describedAs());
    for (String operation : theOperationsNamedBy(onTheLeft, onTheRight)) {
      if (vocabulary.declaresTheCodesOf(operation)) {
        said.append("; operation ")
            .append(operation)
            .append(" with ")
            .append(vocabulary.theCodesOf(operation).describedAs());
      }
    }
    return said.toString();
  }

  private static String theOperationsItExposes(
      ScenarioRule onTheLeft, ScenarioRule onTheRight, ProviderVocabulary vocabulary) {
    StringBuilder said =
        new StringBuilder("provider ")
            .append(vocabulary.provider().value())
            .append(" exposes ")
            .append(String.join(", ", vocabulary.operationNames()))
            .append(" on this contract");
    boolean first = true;
    for (String operation : theOperationsNamedBy(onTheLeft, onTheRight)) {
      String keyedOn =
          vocabulary.operation(operation).map(ProviderOperation::storiesKeyedOn).orElse(null);
      if (keyedOn == null) {
        continue;
      }
      said.append(first ? ", and keys a story about " : " and about ")
          .append(operation)
          .append(" on ")
          .append(keyedOn);
      first = false;
    }
    return said.toString();
  }

  private static @Nullable String theOperationsThatReportAProcessingState(
      ScenarioRule onTheLeft, ScenarioRule onTheRight, ProviderVocabulary vocabulary) {
    if (!settlesLater(onTheLeft) && !settlesLater(onTheRight)) {
      return null;
    }
    List<String> reported = vocabulary.theOperationsThatReportAProcessingState();
    if (reported.isEmpty()) {
      return null;
    }
    return "provider "
        + vocabulary.provider().value()
        + " answers with a processing state on "
        + String.join(", ", reported)
        + " and on no other operation of this contract";
  }

  private static boolean settlesLater(ScenarioRule rule) {
    return rule.action() instanceof ScenarioAction.ProcessingThenTerminal;
  }

  private static List<String> theOperationsNamedBy(
      ScenarioRule onTheLeft, ScenarioRule onTheRight) {
    List<String> named = new ArrayList<>();
    String left = onTheLeft.match().operation();
    String right = onTheRight.match().operation();
    if (left != null) {
      named.add(left);
    }
    if (right != null && !named.contains(right)) {
      named.add(right);
    }
    return List.copyOf(named);
  }
}
