package dev.marcinromanowski.pspsim.kernel.scenario;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fault.FaultDelayCeiling;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFault;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ScenarioGrammar {

  public static final String FIXED_SUCCESS = "FIXED_SUCCESS";

  public static final String TERMINAL_FAILURE = "TERMINAL_FAILURE";

  public static final String TRANSPORT_FAULT = "TRANSPORT_FAULT";

  public static final String TRANSPORT_FAULT_INSTEAD_OF_OPERATION =
      "TRANSPORT_FAULT_INSTEAD_OF_OPERATION";

  public static final String PROCESSING_THEN_TERMINAL = "PROCESSING_THEN_TERMINAL";

  public static final String DELIVERY_DELAYED = "DELIVERY_DELAYED";

  public static final String DELIVERY_DUPLICATED = "DELIVERY_DUPLICATED";

  public static final String DELIVERY_DROPPED = "DELIVERY_DROPPED";

  private static final String A_DELIVERY_IS_NOT_A_REQUEST =
      " narrows on a fact a request carries and a delivery does not, and "
          + "the actions that shape a delivery are "
          + DELIVERY_DELAYED
          + ", "
          + DELIVERY_DUPLICATED
          + " and "
          + DELIVERY_DROPPED;

  private static final List<String> THE_REMOVED_ACTION_TYPES =
      List.of("TRANSIENT_THEN_SUCCESS", "TRANSPORT_FAULT_THEN_SUCCESS");

  private static final String THE_ACTION_TYPES = String.join(", ", theDeclarableActionTypes());

  private static final String THE_FAULTS =
      TransportFaultKind.SERVER_ERROR
          + ", "
          + TransportFaultKind.TRUNCATED_BODY
          + ", "
          + TransportFaultKind.CORRUPTED_BODY
          + ", "
          + TransportFaultKind.DROPPED_CONNECTION
          + ", "
          + TransportFaultKind.DELAYED_RESPONSE;

  private final FaultDelayCeiling faultDelayCeiling;

  public ScenarioGrammar(FaultDelayCeiling faultDelayCeiling) {
    this.faultDelayCeiling = faultDelayCeiling;
  }

  public static List<String> theDeclarableActionTypes() {
    return List.of(
        FIXED_SUCCESS,
        TERMINAL_FAILURE,
        TRANSPORT_FAULT,
        TRANSPORT_FAULT_INSTEAD_OF_OPERATION,
        PROCESSING_THEN_TERMINAL,
        DELIVERY_DELAYED,
        DELIVERY_DUPLICATED,
        DELIVERY_DROPPED);
  }

  public static boolean shapesADelivery(@Nullable String type) {
    return DELIVERY_DELAYED.equals(type)
        || DELIVERY_DUPLICATED.equals(type)
        || DELIVERY_DROPPED.equals(type);
  }

  public static String typeOf(ScenarioAction action) {
    return switch (action) {
      case ScenarioAction.FixedSuccess _ -> FIXED_SUCCESS;
      case ScenarioAction.TerminalFailure _ -> TERMINAL_FAILURE;
      case ScenarioAction.TransportFaultAlways _ -> TRANSPORT_FAULT;
      case ScenarioAction.TransportFaultInsteadOfOperation _ ->
          TRANSPORT_FAULT_INSTEAD_OF_OPERATION;
      case ScenarioAction.ProcessingThenTerminal _ -> PROCESSING_THEN_TERMINAL;
      case ScenarioAction.DeliveryDelayed _ -> DELIVERY_DELAYED;
      case ScenarioAction.DeliveryDuplicated _ -> DELIVERY_DUPLICATED;
      case ScenarioAction.DeliveryDropped _ -> DELIVERY_DROPPED;
    };
  }

  public NewScenarioRule ruleOf(
      ProviderId provider, TestRunId testRunId, DeclaredScenario declared) {
    return new NewScenarioRule(
        provider,
        testRunId,
        declared.priority() == null ? 0 : declared.priority(),
        matchOf(declared.match()),
        actionOf(declared.action()),
        storyOf(declared));
  }

  public static ScenarioPreset presetOf(@Nullable String preset) {
    if (preset == null || preset.isBlank()) {
      throw new IllegalArgumentException(
          "preset is required, and must be one of " + ScenarioPreset.thePublishedNames());
    }
    return ScenarioPreset.of(preset)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "preset must be one of " + ScenarioPreset.thePublishedNames()));
  }

  public static @Nullable ScenarioStory storyOf(DeclaredScenario declared) {
    String named = declared.story();
    if (named == null) {
      if (declared.whenState() != null
          || declared.thenState() != null
          || declared.keyedOn() != null) {
        throw new IllegalArgumentException(
            "story is required by keyedOn, whenState and thenState, because a state and the key it"
                + " is held against are a state and a key of a named story");
      }
      return null;
    }
    return new ScenarioStory(
        named, keyingOf(declared.keyedOn()), declared.whenState(), declared.thenState());
  }

  private static StoryKeying keyingOf(@Nullable String declared) {
    if (declared == null) {
      return StoryKeying.FLOW;
    }
    return StoryKeying.of(declared)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "keyedOn must name one of "
                        + StoryKeying.theDeclarableNames()
                        + ", because a story is keyed either on the flow the provider resolves for"
                        + " the request or on the whole test run"));
  }

  public static ScenarioMatch matchOf(@Nullable DeclaredMatch match) {
    if (match == null) {
      return ScenarioMatch.anyRequest();
    }
    String amount = match.amount();
    return new ScenarioMatch(
        match.merchantId(),
        match.orderId(),
        match.providerObjectId(),
        match.customerId(),
        match.paymentMethodId(),
        amount == null ? null : Amount.of(amount),
        match.currency(),
        matchedOperationOf(match.operation()),
        matchedEventTypeOf(match.eventType()),
        headerOf(match.header()));
  }

  private static @Nullable String matchedEventTypeOf(@Nullable String eventType) {
    if (eventType == null) {
      return null;
    }
    if (eventType.isBlank()) {
      throw new IllegalArgumentException(
          "match.eventType must name the event the delivery reports, or be absent");
    }
    return eventType;
  }

  private static @Nullable String matchedOperationOf(@Nullable String operation) {
    if (operation == null) {
      return null;
    }
    if (operation.isBlank()) {
      throw new IllegalArgumentException(
          "match.operation must name the operation the rule is about, or be absent");
    }
    return operation;
  }

  private static Map<String, String> headerOf(@Nullable Map<String, String> header) {
    if (header == null) {
      return Map.of();
    }
    LinkedHashMap<String, String> named = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : header.entrySet()) {
      String value = entry.getValue();
      if (value == null) {
        throw new IllegalArgumentException(
            "match.header." + entry.getKey() + " must name the value the request carries");
      }
      named.put(entry.getKey(), value);
    }
    return Collections.unmodifiableSequencedMap(named);
  }

  public ScenarioAction actionOf(@Nullable DeclaredAction action) {
    if (action == null) {
      throw new IllegalArgumentException("action is required");
    }
    String type = action.type();
    if (TRANSPORT_FAULT.equals(type)) {
      rejectAProviderCodeThisActionCannotAnswerWith(
          action, "a transport fault leaves the provider's own answer in place");
      return new ScenarioAction.TransportFaultAlways(faultOf(action));
    }
    if (TRANSPORT_FAULT_INSTEAD_OF_OPERATION.equals(type)) {
      rejectAProviderCodeThisActionCannotAnswerWith(
          action, "a transport fault answers no provider envelope at all");
      return new ScenarioAction.TransportFaultInsteadOfOperation(faultOf(action));
    }
    if (shapesADelivery(type)) {
      rejectAProviderCodeThisActionCannotAnswerWith(
          action, "an action that shapes a delivery answers no provider envelope at all");
      return deliveryActionOf(action);
    }
    if (PROCESSING_THEN_TERMINAL.equals(type)) {
      return processingActionOf(action);
    }
    if (FIXED_SUCCESS.equals(type)) {
      rejectAProviderCodeThisActionCannotAnswerWith(
          action, "a " + FIXED_SUCCESS + " action declines nothing");
      return new ScenarioAction.FixedSuccess();
    }
    String providerCode = action.providerCode();
    if (providerCode == null || providerCode.isBlank()) {
      throw new IllegalArgumentException("action.providerCode is required");
    }
    return new ScenarioAction.TerminalFailure(providerCode);
  }

  private static ScenarioAction.ProcessingThenTerminal processingActionOf(DeclaredAction action) {
    Long settlesAfterMs = action.settlesAfterMs();
    if (settlesAfterMs == null) {
      throw new IllegalArgumentException(
          "action.settlesAfterMs is required by "
              + PROCESSING_THEN_TERMINAL
              + ", because a processing state with no scheduled outcome behind it is a payment"
              + " that never comes to rest");
    }
    return new ScenarioAction.ProcessingThenTerminal(
        Duration.ofMillis(settlesAfterMs), action.providerCode());
  }

  private static ScenarioAction.OnTheDelivery deliveryActionOf(DeclaredAction action) {
    String type = action.type();
    if (DELIVERY_DUPLICATED.equals(type)) {
      return new ScenarioAction.DeliveryDuplicated();
    }
    if (DELIVERY_DROPPED.equals(type)) {
      return new ScenarioAction.DeliveryDropped();
    }
    Long delayMs = action.delayMs();
    if (delayMs == null) {
      throw new IllegalArgumentException("action.delayMs is required by " + DELIVERY_DELAYED);
    }
    return new ScenarioAction.DeliveryDelayed(Duration.ofMillis(delayMs));
  }

  public static List<ScenarioRefusal> theActionTypeThisSimulatorCannotRead(
      @Nullable DeclaredAction action) {
    if (action == null) {
      return List.of();
    }
    String named = action.type();
    if (FIXED_SUCCESS.equals(named)
        || TERMINAL_FAILURE.equals(named)
        || TRANSPORT_FAULT.equals(named)
        || TRANSPORT_FAULT_INSTEAD_OF_OPERATION.equals(named)
        || PROCESSING_THEN_TERMINAL.equals(named)
        || shapesADelivery(named)) {
      return List.of();
    }
    return List.of(new ScenarioRefusal("action.type", theVocabularyInsteadOf(named)));
  }

  private static String theVocabularyInsteadOf(@Nullable String named) {
    if (named != null && THE_REMOVED_ACTION_TYPES.contains(named)) {
      return "an action of that name counted failures before a success and no longer exists; a"
          + " count of failures is a story of two steps, so give each rule a story name, a"
          + " whenState and a thenState, and name one of "
          + THE_ACTION_TYPES
          + " on each step";
    }
    return "action.type must be one of " + THE_ACTION_TYPES;
  }

  private static void rejectAProviderCodeThisActionCannotAnswerWith(
      DeclaredAction action, String because) {
    if (action.providerCode() != null) {
      throw new IllegalArgumentException(
          "action.providerCode applies only to " + TERMINAL_FAILURE + ", because " + because);
    }
  }

  private TransportFault faultOf(DeclaredAction action) {
    String named = action.fault();
    if (named == null) {
      throw new IllegalArgumentException(
          "action.fault is required by " + action.type() + ", and must be one of " + THE_FAULTS);
    }
    TransportFaultKind kind;
    try {
      kind = TransportFaultKind.valueOf(named);
    } catch (IllegalArgumentException unrecognised) {
      throw new IllegalArgumentException("action.fault must be one of " + THE_FAULTS);
    }
    return switch (kind) {
      case SERVER_ERROR -> new TransportFault.ServerError(requiredHttpStatus(action));
      case TRUNCATED_BODY -> new TransportFault.TruncatedBody();
      case CORRUPTED_BODY -> new TransportFault.CorruptedBody();
      case DROPPED_CONNECTION -> new TransportFault.DroppedConnection();
      case DELAYED_RESPONSE -> requiredDelay(action);
    };
  }

  private static int requiredHttpStatus(DeclaredAction action) {
    Integer httpStatus = action.httpStatus();
    if (httpStatus == null) {
      throw new IllegalArgumentException(
          "action.httpStatus is required by action.fault " + TransportFaultKind.SERVER_ERROR);
    }
    return httpStatus;
  }

  private TransportFault.DelayedResponse requiredDelay(DeclaredAction action) {
    Long delayMs = action.delayMs();
    if (delayMs == null) {
      throw new IllegalArgumentException(
          "action.delayMs is required by action.fault " + TransportFaultKind.DELAYED_RESPONSE);
    }
    return faultDelayCeiling.holdingFor(Duration.ofMillis(delayMs));
  }

  public static List<ScenarioRefusal> theActionFieldsThisSimulatorCannotPerform(
      @Nullable DeclaredAction action) {
    if (action == null) {
      return List.of();
    }
    List<ScenarioRefusal> refused = new ArrayList<>();
    if (action.failuresBeforeSuccess() != null) {
      refused.add(
          new ScenarioRefusal(
              "action.failuresBeforeSuccess",
              "a count of failures before success is a one-step story, and a story is written as"
                  + " steps: give each rule a story name, a whenState and a thenState, so the"
                  + " first step declines and moves the story on and the second lets the call"
                  + " through"));
    }
    if (action.httpStatus() != null && !performs(action, TransportFaultKind.SERVER_ERROR)) {
      refused.add(
          new ScenarioRefusal(
              "action.httpStatus",
              "a status is performed only by a transport fault put in place of the rendered"
                  + " response, which is action.fault "
                  + TransportFaultKind.SERVER_ERROR
                  + " on a "
                  + TRANSPORT_FAULT
                  + " or "
                  + TRANSPORT_FAULT_INSTEAD_OF_OPERATION
                  + " action"));
    }
    if (action.fault() != null && !performsAFault(action)) {
      refused.add(
          new ScenarioRefusal(
              "action.fault",
              "a transport fault damages the delivery of an already-rendered response or is"
                  + " answered in place of the operation that would have rendered one, which only"
                  + " a "
                  + TRANSPORT_FAULT
                  + " or "
                  + TRANSPORT_FAULT_INSTEAD_OF_OPERATION
                  + " action does"));
    }
    if (action.settlesAfterMs() != null && !PROCESSING_THEN_TERMINAL.equals(action.type())) {
      refused.add(
          new ScenarioRefusal(
              "action.settlesAfterMs",
              "a scheduled outcome is performed only by a "
                  + PROCESSING_THEN_TERMINAL
                  + " action, which reports a provider-native processing state and settles it"
                  + " afterwards; every other action answers what it answers and schedules"
                  + " nothing"));
    }
    if (action.terminalStatus() != null) {
      refused.add(
          new ScenarioRefusal(
              "action.terminalStatus",
              "a normalized status is derived from native status and the operation list, never"
                  + " written by a scenario"));
    }
    if (action.delayMs() != null
        && !performs(action, TransportFaultKind.DELAYED_RESPONSE)
        && !DELIVERY_DELAYED.equals(action.type())) {
      refused.add(
          new ScenarioRefusal(
              "action.delayMs",
              "a delay is performed either by holding an already-rendered response, which is"
                  + " action.fault "
                  + TransportFaultKind.DELAYED_RESPONSE
                  + " on a "
                  + TRANSPORT_FAULT
                  + " action, or by holding a webhook delivery back, which is a "
                  + DELIVERY_DELAYED
                  + " action"));
    }
    return List.copyOf(refused);
  }

  public List<ScenarioRefusal> theDelaysThisSimulatorWillNotHold(ScenarioAction action) {
    if (faultOf(action) instanceof TransportFault.DelayedResponse held
        && !faultDelayCeiling.admits(held.delay())) {
      return List.of(
          new ScenarioRefusal(
              "action.delayMs",
              "a preset may not hold a client's connection open longer than"
                  + " simulator.fault.longest-delay, which is "
                  + faultDelayCeiling.longest()));
    }
    return List.of();
  }

  public static @Nullable TransportFault faultOf(ScenarioAction action) {
    return switch (action) {
      case ScenarioAction.TransportFaultAlways always -> always.fault();
      case ScenarioAction.TransportFaultInsteadOfOperation instead -> instead.fault();
      case ScenarioAction.FixedSuccess _,
          ScenarioAction.TerminalFailure _,
          ScenarioAction.ProcessingThenTerminal _,
          ScenarioAction.OnTheDelivery _ ->
          null;
    };
  }

  public static List<ScenarioRefusal> theSubjectTheseRulesCannotNarrowOn(
      List<NewScenarioRule> rules) {
    List<ScenarioRefusal> refused = new ArrayList<>();
    for (NewScenarioRule rule : rules) {
      for (ScenarioRefusal refusal : theSubjectThisRuleCannotNarrowOn(rule)) {
        if (!refused.contains(refusal)) {
          refused.add(refusal);
        }
      }
    }
    return List.copyOf(refused);
  }

  public static List<ScenarioRefusal> theSubjectThisRuleCannotNarrowOn(NewScenarioRule rule) {
    ScenarioMatch match = rule.match();
    if (!(rule.action() instanceof ScenarioAction.OnTheDelivery)) {
      List<ScenarioRefusal> answering = new ArrayList<>(theEventTypeOnlyADeliveryCarries(match));
      if (rule.action() instanceof ScenarioAction.ProcessingThenTerminal
          && match.operation() == null) {
        answering.add(
            new ScenarioRefusal(
                "match.operation",
                "a processing state is the state one operation's answer comes to rest in, and a"
                    + " provider answers one on the operations it names and on no other, so a "
                    + PROCESSING_THEN_TERMINAL
                    + " rule that narrows on no operation would fire on calls that can never"
                    + " report it"));
      }
      return List.copyOf(answering);
    }
    List<ScenarioRefusal> refused = new ArrayList<>();
    if (rule.story() != null) {
      refused.add(
          new ScenarioRefusal(
              "story",
              "a story is moved on by the decision a request is owed, and a rule that shapes a"
                  + " delivery is read where no request is being answered, so a delivery leaves no"
                  + " step behind it"));
    }
    refuse("match.orderId", match.orderId(), refused);
    refuse("match.customerId", match.customerId(), refused);
    refuse("match.paymentMethodId", match.paymentMethodId(), refused);
    Amount amount = match.amount();
    refuse("match.amount", amount == null ? null : amount.value(), refused);
    refuse("match.currency", match.currency(), refused);
    refuse("match.operation", match.operation(), refused);
    if (!match.header().isEmpty()) {
      refuse("match.header", "named", refused);
    }
    return List.copyOf(refused);
  }

  private static List<ScenarioRefusal> theEventTypeOnlyADeliveryCarries(ScenarioMatch match) {
    if (match.eventType() == null) {
      return List.of();
    }
    return List.of(
        new ScenarioRefusal(
            "match.eventType",
            "an event type is carried by a webhook delivery and never by an inbound request, so"
                + " only an action that shapes a delivery narrows on it: "
                + DELIVERY_DELAYED
                + ", "
                + DELIVERY_DUPLICATED
                + " or "
                + DELIVERY_DROPPED));
  }

  private static void refuse(
      String property, @Nullable String named, List<ScenarioRefusal> refused) {
    if (named != null) {
      refused.add(new ScenarioRefusal(property, property + A_DELIVERY_IS_NOT_A_REQUEST));
    }
  }

  private static boolean performs(DeclaredAction action, TransportFaultKind kind) {
    return performsAFault(action) && kind.name().equals(action.fault());
  }

  private static boolean performsAFault(DeclaredAction action) {
    return TRANSPORT_FAULT.equals(action.type())
        || TRANSPORT_FAULT_INSTEAD_OF_OPERATION.equals(action.type());
  }
}
