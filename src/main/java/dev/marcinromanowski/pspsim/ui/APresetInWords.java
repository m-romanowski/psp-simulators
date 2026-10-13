package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeying;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record APresetInWords(
    String preset, List<String> whatItDoes, @Nullable String whyThisProviderCannotExpressIt) {

  public static final String THE_CHOOSER_READS = " · ";

  public static final String NO_PROVIDER_HERE_EXPRESSES_IT = "this provider cannot express it";

  static final String THE_DELIVERY_A_FLOW_SHAPES = "its webhook";

  private static final int THE_PRIORITY_READING_A_PRESET_NEEDS_NOT_CARRY = 0;

  public APresetInWords {
    whatItDoes = List.copyOf(whatItDoes);
  }

  public String inTheChooser() {
    if (whyThisProviderCannotExpressIt != null) {
      return preset + THE_CHOOSER_READS + NO_PROVIDER_HERE_EXPRESSES_IT;
    }
    return preset + THE_CHOOSER_READS + howManyRules(whatItDoes.size());
  }

  public static List<APresetInWords> theOnesOffered(SimulatorProvider module, TestRunId run) {
    List<APresetInWords> offered = new ArrayList<>();
    for (ScenarioPreset preset : ScenarioPreset.values()) {
      offered.add(
          of(
              preset,
              module.expand(
                  new PresetRequest(
                      preset,
                      run,
                      THE_PRIORITY_READING_A_PRESET_NEEDS_NOT_CARRY,
                      ScenarioMatch.anyRequest()))));
    }
    return List.copyOf(offered);
  }

  public static APresetInWords of(ScenarioPreset preset, PresetExpansion expansion) {
    return switch (expansion) {
      case PresetExpansion.Rules written ->
          new APresetInWords(preset.publishedName(), theRulesOf(written.rules()), null);
      case PresetExpansion.NotExpressible unexpressed ->
          new APresetInWords(preset.publishedName(), List.of(), unexpressed.reason());
    };
  }

  static String howManyRules(int count) {
    return count == 1 ? "1 rule" : count + " rules";
  }

  static List<String> theRulesOf(List<NewScenarioRule> rules) {
    List<String> read = new ArrayList<>();
    for (NewScenarioRule rule : rules) {
      read.add(theRuleOf(rule));
    }
    return List.copyOf(read);
  }

  static String theRuleOf(NewScenarioRule rule) {
    return whatTheRuleIsAbout(rule)
        + " "
        + whatBecomesOfIt(rule.action())
        + whereItLeavesTheStory(rule.story());
  }

  private static String whatTheRuleIsAbout(NewScenarioRule rule) {
    ScenarioMatch match = rule.match();
    String operation = match.operation();
    return switch (rule.action()) {
      case ScenarioAction.OnTheRequest _ -> theCallItNarrowsOn(rule, operation);
      case ScenarioAction.OnTheDelivery _ -> theDeliveryItNarrowsOn(rule, match, operation);
    };
  }

  private static String theCallItNarrowsOn(NewScenarioRule rule, @Nullable String operation) {
    refuseANarrowingTheseWordsDoNotRead(rule, null);
    if (operation == null) {
      throw new IllegalArgumentException(
          theRefusalOf(rule)
              + " shapes the answer to a call and narrows on no operation, so these words have no"
              + " call to name and would read as though the rule were about a webhook. A preset"
              + " that shapes every call of a provider at once must say so in words of its own"
              + " before this screen can offer it.");
    }
    return operation;
  }

  private static String theDeliveryItNarrowsOn(
      NewScenarioRule rule, ScenarioMatch match, @Nullable String operation) {
    refuseANarrowingTheseWordsDoNotRead(rule, MatchFact.EVENT_TYPE);
    if (operation != null) {
      throw new IllegalArgumentException(
          theRefusalOf(rule)
              + " shapes a webhook delivery and narrows on the operation "
              + operation
              + ", so these words would name that operation and tell a tester the call itself is"
              + " shaped, which it is not. A delivery is narrowed on its event type, never on an"
              + " operation.");
    }
    String eventType = match.eventType();
    if (eventType == null) {
      return THE_DELIVERY_A_FLOW_SHAPES;
    }
    return THE_DELIVERY_A_FLOW_SHAPES + " " + eventType;
  }

  private static void refuseANarrowingTheseWordsDoNotRead(
      NewScenarioRule rule, @Nullable MatchFact theOneTheyRead) {
    ScenarioMatch match = rule.match();
    List<String> unread = new ArrayList<>();
    for (MatchFact fact : MatchFact.namedBy(match)) {
      if (fact != theOneTheyRead) {
        unread.add(fact.property());
      }
    }
    for (String name : match.header().keySet()) {
      unread.add("match.header." + name);
    }
    if (!unread.isEmpty()) {
      throw new IllegalArgumentException(
          theRefusalOf(rule)
              + " narrows on "
              + String.join(", ", unread)
              + ", and these words read nothing but the operation, the event type, the action and"
              + " the story, so the sentence would leave that narrowing out and tell a tester the"
              + " rule fires where it does not. Say it in these words or expand into a rule that"
              + " does not narrow on it.");
    }
  }

  private static String theRefusalOf(NewScenarioRule rule) {
    return "a preset of provider " + rule.provider().value() + " expanded into a rule that";
  }

  private static String whatBecomesOfIt(ScenarioAction action) {
    return switch (action) {
      case ScenarioAction.FixedSuccess _ -> "succeeds";
      case ScenarioAction.TerminalFailure failing -> "fails with code " + failing.providerCode();
      case ScenarioAction.TransportFaultAlways faulted ->
          "happens, and " + whatTheFaultDoesToItsAnswer(faulted.fault());
      case ScenarioAction.TransportFaultInsteadOfOperation instead ->
          "is answered HTTP " + instead.answer().httpStatus() + " and does not happen at all";
      case ScenarioAction.ProcessingThenTerminal settling ->
          "answers processing and comes to rest " + whatItComesToRestAs(settling);
      case ScenarioAction.DeliveryDelayed held -> "is held back by " + howLong(held.held());
      case ScenarioAction.DeliveryDuplicated _ -> "is delivered twice";
      case ScenarioAction.DeliveryDropped _ -> "is never delivered";
    };
  }

  private static String whatItComesToRestAs(ScenarioAction.ProcessingThenTerminal settling) {
    String code = settling.providerCode();
    String after = howLong(settling.settlesAfter()) + " later";
    if (code == null) {
      return after + " as a success";
    }
    return after + " as a failure with code " + code;
  }

  private static String whatTheFaultDoesToItsAnswer(TransportFault fault) {
    return switch (fault) {
      case TransportFault.ServerError replaced ->
          "its answer is replaced by HTTP " + replaced.httpStatus();
      case TransportFault.TruncatedBody _ -> "its answer arrives cut short";
      case TransportFault.CorruptedBody _ -> "its answer arrives with a damaged body";
      case TransportFault.DroppedConnection _ ->
          "the connection drops before its answer is complete";
      case TransportFault.DelayedResponse late ->
          "its answer arrives " + howLong(late.delay()) + " late";
    };
  }

  private static String whereItLeavesTheStory(@Nullable ScenarioStory story) {
    if (story == null) {
      return "";
    }
    return ", one step of the story "
        + story.name()
        + " kept per "
        + whatTheStoryStandsOn(story.keyedOn())
        + ", taken "
        + whenItIsTaken(story.whenState())
        + ", and leaving that story "
        + whereItLeavesIt(story.thenState());
  }

  private static String whenItIsTaken(@Nullable String whenState) {
    if (whenState == null) {
      return "whatever state that story stands in";
    }
    return "while that story stands at " + whenState;
  }

  private static String whereItLeavesIt(@Nullable String thenState) {
    if (thenState == null) {
      return "where it was";
    }
    return "at " + thenState;
  }

  private static String whatTheStoryStandsOn(StoryKeying keying) {
    return switch (keying) {
      case FLOW -> "the flow of one call";
      case TEST_RUN -> "the whole test run";
    };
  }

  private static String howLong(Duration span) {
    return ADurationInWords.of(span).map(ADurationInWords::inWords).orElseGet(span::toString);
  }
}
