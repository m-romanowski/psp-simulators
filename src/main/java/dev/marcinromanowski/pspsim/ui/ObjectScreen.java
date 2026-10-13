package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class ObjectScreen {

  public static final String A_CHANGE_IS_CONFIRMED = "yes";

  public static final String YES = "yes";

  public static final String NO = "no";

  public static final String THIS_PROVIDER_STATES_NEITHER = "not stated";

  public static final String PROVIDER_OBJECT_ID = "providerObjectId";

  public static final String THE_AMOUNT_AN_ACTION_WAS_ASKED_FOR = "action.amount.";

  public static final String CONFIRM_BEFORE_MOVING_MONEY =
      "tick confirm before moving money on a payment: an action here runs the provider operation"
          + " itself, writes the provider's own state and cannot be undone by closing the page";

  public static final String CONFIRM_BEFORE_SENDING_A_CALLBACK =
      "tick confirm before sending a callback: the merchant endpoint this test run configures is"
          + " called for real, and an application listening on it acts on what it receives";

  public static final String NO_CALLBACK_WAS_SCHEDULED =
      "this provider scheduled no callback for that object. A callback is sent to the merchant"
          + " target this test run configures, and a provider schedules one only for an object"
          + " its own contract reports to a merchant";

  public static final String THE_ACTION_IT_ASKED_THIS_PROVIDER_TO_PERFORM =
      "the action on a payment this request asked this provider to perform";

  public static final String THE_RUN_HOLDS_NO_SUCH_OBJECT =
      "this test run holds no object of this provider under that providerObjectId";

  public record Offered(
      String action,
      String asked,
      boolean takesAnAmount,
      @Nullable String suggestedAmount,
      boolean undoesMoneyAlreadyTakenOrHeld) {

    public @Nullable String amountDrawnFrom(WhatWasTyped typed) {
      String typedIn = typed.valueOf(theAmountFieldOf(action));
      return typedIn.isEmpty() ? suggestedAmount : typedIn;
    }
  }

  public record Unoffered(String action, String asked, String whatThisProviderHasInstead) {}

  @FunctionalInterface
  public interface WhatThisRunHoldsUnderTheseIds {

    List<ProviderObjectState> under(List<String> providerObjectIds);
  }

  public record Named(
      String field,
      String id,
      @Nullable String nativeStatus,
      @Nullable StatusBadge normalizedStatus) {

    public boolean namesAnObjectThisRunHolds() {
      return nativeStatus != null;
    }
  }

  public record WhatStillMovesIt(
      boolean anOperationOfThisProviderApplies,
      boolean aFollowUpOfThisProviderApplies,
      boolean aPayerStepIsStillToBeTaken) {

    public boolean nothingStillMovesIt() {
      return !anOperationOfThisProviderApplies
          && !aFollowUpOfThisProviderApplies
          && !aPayerStepIsStillToBeTaken;
    }

    public boolean somethingStillMovesIt() {
      return !nothingStillMovesIt();
    }

    public boolean onlyThePayerStepStillMovesIt() {
      return aPayerStepIsStillToBeTaken
          && !anOperationOfThisProviderApplies
          && !aFollowUpOfThisProviderApplies;
    }
  }

  public static String theAmountFieldOf(String action) {
    return THE_AMOUNT_AN_ACTION_WAS_ASKED_FOR + action;
  }

  public static WhatWasTyped theAmountTypedFor(@Nullable String action, @Nullable String amount) {
    return WhatWasTyped.theFieldTyped(theAmountFieldOf(action == null ? "" : action), amount);
  }

  public record Unreachable(String scenarioId, List<String> headers) {

    public Unreachable {
      headers = List.copyOf(headers);
    }
  }

  public record Operation(
      int sequence,
      String kind,
      String amount,
      @Nullable Boolean closesAuthorization,
      @Nullable String createdObjectId,
      String nativeStatus,
      StatusBadge normalizedStatus,
      Instant at) {

    public String closesTheAuthorizationAsThisScreenReadsIt() {
      Boolean closes = closesAuthorization;
      if (closes == null) {
        return THIS_PROVIDER_STATES_NEITHER;
      }
      return closes ? YES : NO;
    }
  }

  public record Balances(
      String captured, String refunded, String capturableRemainder, String refundableRemainder) {}

  public record View(
      String provider,
      String testRunId,
      String providerObjectId,
      ProviderObjectKind kind,
      String addressedBy,
      List<String> answersTo,
      String nativeStatus,
      StatusBadge normalizedStatus,
      @Nullable String amount,
      @Nullable String currency,
      List<Operation> operations,
      @Nullable Balances balances,
      Map<String, String> relatedObjects,
      String origin,
      Instant updatedAt,
      @Nullable Session session,
      boolean capturesAccepted,
      boolean holdsMoneyNoCaptureCanTake,
      boolean refundsAccepted,
      boolean holdsMoneyNoRefundCanReturn,
      boolean capturable,
      boolean refundable,
      List<Offered> offered,
      List<Unoffered> unoffered,
      List<Unreachable> unreachable) {

    public View {
      answersTo = List.copyOf(answersTo);
      operations = List.copyOf(operations);
      relatedObjects = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(relatedObjects));
      offered = List.copyOf(offered);
      unoffered = List.copyOf(unoffered);
      unreachable = List.copyOf(unreachable);
    }

    public boolean answersToMoreThanOneIdentifier() {
      return answersTo.size() > 1;
    }

    public boolean wasAddressedByALaterIdentifier() {
      return !addressedBy.equals(providerObjectId);
    }
  }

  public record Session(
      String account,
      String party,
      boolean aPartyIsNamed,
      String operation,
      Instant createdAt,
      String expires,
      boolean itExpiresAtAKnownTime,
      boolean itsDeadlineHasPassed) {}

  public record Row(
      String providerObjectId,
      ProviderObjectKind kind,
      String nativeStatus,
      StatusBadge normalizedStatus,
      @Nullable String amount,
      @Nullable String currency,
      String origin,
      boolean placedByATester,
      Instant updatedAt,
      @Nullable Session session) {}

  public record Listing(List<Row> rows, int limit, @Nullable String nextCursor) {

    public Listing {
      rows = List.copyOf(rows);
    }

    public List<Row> theObjectsAPayerFinishes() {
      List<Row> finished = new ArrayList<>();
      for (Row row : rows) {
        if (row.session() != null) {
          finished.add(row);
        }
      }
      return List.copyOf(finished);
    }
  }

  private ObjectScreen() {}

  public static @Nullable Session sessionOf(HostedSessionFacts facts, Instant now) {
    return switch (facts) {
      case HostedSessionFacts.NotAnObjectAPayerFinishes _ -> null;
      case HostedSessionFacts.AnObjectAPayerFinishes finished ->
          new Session(
              finished.theAccountItBelongsTo(),
              finished.thePartyAsItReads(),
              finished.aPartyIsNamed(),
              finished.theOperationItWasStartedFor(),
              finished.createdAt(),
              finished.theDeadlineAsItReads(),
              finished.itExpiresAtAKnownTime(),
              finished.itsDeadlineHasPassedBy(now));
    };
  }

  public static Listing pageOf(List<ProviderObjectState> objects, int limit, Instant now) {
    List<Row> rows = new ArrayList<>();
    for (ProviderObjectState state : objects) {
      Amount amount = state.amount();
      rows.add(
          new Row(
              state.providerObjectId(),
              state.kind(),
              state.nativeStatus(),
              StatusBadge.of(state.normalizedStatus()),
              amount == null ? null : amount.value(),
              state.currency(),
              state.origin().name(),
              state.origin() != ObjectOrigin.PROVIDER_API,
              state.updatedAt(),
              sessionOf(state.hostedSession(), now)));
    }
    return new Listing(rows, limit, cursorAfter(rows, limit));
  }

  private static @Nullable String cursorAfter(List<Row> rows, int limit) {
    return rows.size() < limit ? null : rows.get(rows.size() - 1).providerObjectId();
  }

  public static View of(
      String provider,
      TestRunId testRunId,
      AddressedProviderObject addressed,
      ObjectActions actions,
      List<ScenarioRule> rules,
      Instant now) {
    ProviderObjectState state = addressed.object();
    Optional<DerivedBalances> derived = state.derivedBalances();
    boolean stillHolds = derived.filter(it -> isPositive(it.capturableRemainder())).isPresent();
    boolean capturable = state.capturesAccepted() && stillHolds;
    boolean stillOwesBack = derived.filter(it -> isPositive(it.refundableRemainder())).isPresent();
    boolean refundable = state.refundsAccepted() && stillOwesBack;
    Amount amount = state.amount();
    return new View(
        provider,
        testRunId.value(),
        state.providerObjectId(),
        state.kind(),
        addressed.addressedBy(),
        addressed.answersTo(),
        state.nativeStatus(),
        StatusBadge.of(state.normalizedStatus()),
        amount == null ? null : amount.value(),
        state.currency(),
        operationsOf(state),
        derived.map(ObjectScreen::balancesOf).orElse(null),
        state.relatedObjects(),
        state.origin().name(),
        state.updatedAt(),
        sessionOf(state.hostedSession(), now),
        state.capturesAccepted(),
        !state.capturesAccepted() && stillHolds,
        state.refundsAccepted(),
        !state.refundsAccepted() && stillOwesBack,
        capturable,
        refundable,
        offeredOn(actions, derived.orElse(null), capturable, refundable),
        unofferedBy(actions),
        theRulesNoActionHereCanFire(rules));
  }

  public static WhatStillMovesIt whatStillMovesIt(
      View object, List<FollowUpForm.Offered> followUps, HostedCompletionForm.View completion) {
    return new WhatStillMovesIt(
        !object.offered().isEmpty(), !followUps.isEmpty(), completion.offersAPayerStep());
  }

  public static List<Named> theObjectsItNames(View object, WhatThisRunHoldsUnderTheseIds held) {
    Map<String, String> related = object.relatedObjects();
    if (related.isEmpty()) {
      return List.of();
    }
    List<ProviderObjectState> found = held.under(List.copyOf(related.values()));
    List<Named> named = new ArrayList<>();
    related.forEach((field, id) -> named.add(theObjectNamedBy(field, id, found)));
    return List.copyOf(named);
  }

  private static Named theObjectNamedBy(String field, String id, List<ProviderObjectState> found) {
    return TheObjectAnIdentifierNames.among(found, id)
        .map(
            state ->
                new Named(
                    field, id, state.nativeStatus(), StatusBadge.of(state.normalizedStatus())))
        .orElseGet(() -> new Named(field, id, null, null));
  }

  public static List<Unreachable> theRulesNoActionHereCanFire(List<ScenarioRule> rules) {
    List<Unreachable> unreachable = new ArrayList<>();
    for (ScenarioRule rule : rules) {
      if (!(rule.action() instanceof ScenarioAction.OnTheRequest)) {
        continue;
      }
      Map<String, String> narrowed = rule.match().header();
      if (narrowed.isEmpty()) {
        continue;
      }
      List<String> named = new ArrayList<>();
      narrowed.forEach((header, value) -> named.add(header + ": " + value));
      unreachable.add(new Unreachable(rule.scenarioId(), List.copyOf(named)));
    }
    return List.copyOf(unreachable);
  }

  private static List<Offered> offeredOn(
      ObjectActions actions,
      @Nullable DerivedBalances derived,
      boolean capturable,
      boolean refundable) {
    if (derived == null) {
      return List.of();
    }
    List<Offered> offered = new ArrayList<>();
    if (capturable && actions.offers(ObjectAction.CAPTURE)) {
      offered.add(
          new Offered(
              ObjectAction.CAPTURE.name(),
              ObjectAction.CAPTURE.asked(),
              true,
              derived.capturableRemainder().value(),
              ObjectAction.CAPTURE.undoesMoneyAlreadyTakenOrHeld()));
    }
    if (capturable && actions.offers(ObjectAction.CANCEL)) {
      offered.add(
          new Offered(
              ObjectAction.CANCEL.name(),
              ObjectAction.CANCEL.asked(),
              false,
              null,
              ObjectAction.CANCEL.undoesMoneyAlreadyTakenOrHeld()));
    }
    if (refundable && actions.offers(ObjectAction.REFUND)) {
      offered.add(
          new Offered(
              ObjectAction.REFUND.name(),
              ObjectAction.REFUND.asked(),
              true,
              derived.refundableRemainder().value(),
              ObjectAction.REFUND.undoesMoneyAlreadyTakenOrHeld()));
    }
    return List.copyOf(offered);
  }

  private static List<Unoffered> unofferedBy(ObjectActions actions) {
    List<Unoffered> unoffered = new ArrayList<>();
    for (ObjectAction action : ObjectAction.values()) {
      if (!actions.offers(action)) {
        unoffered.add(
            new Unoffered(action.name(), action.asked(), actions.whatItHasInsteadOf(action)));
      }
    }
    return List.copyOf(unoffered);
  }

  public static @Nullable String refusalOf(ObjectActionOutcome outcome) {
    return switch (outcome) {
      case ObjectActionOutcome.Applied _ -> null;
      case ObjectActionOutcome.RefusedByTheProvider refused -> refused.inItsOwnWords();
      case ObjectActionOutcome.AnsweredByAScenarioInstead answered ->
          "a scenario rule answered this call with a transport fault in place of the operation,"
              + " so the provider ran nothing and this payment did not move. The rule is "
              + answered.scenarioId()
              + ", and a rule shaped that way is what it does over the provider's own route"
              + " too.";
      case ObjectActionOutcome.NotOffered notOffered -> notOffered.whatThisProviderHasInstead();
      case ObjectActionOutcome.NoSuchObject _ -> THE_RUN_HOLDS_NO_SUCH_OBJECT;
    };
  }

  public static Map<String, List<String>> factsOf(
      ObjectActionRequest request, ObjectActionOutcome.Applied applied) {
    Amount asked = request.amount();
    return ActionFacts.none()
        .and("action", request.action().name())
        .and("amount", asked == null ? null : asked.value())
        .and("createdObjectId", applied.createdObjectId())
        .asRecorded();
  }

  public static Map<String, List<String>> factsOf(
      @Nullable String action, @Nullable String amount, String refusal) {
    return ActionFacts.none()
        .and("action", action)
        .and("amount", amount)
        .and(ActionFacts.REFUSED_BECAUSE, refusal)
        .asRecorded();
  }

  public static Map<String, List<String>> factsOf(ObjectActionRequest request, String refusal) {
    Amount asked = request.amount();
    return ActionFacts.none()
        .and("action", request.action().name())
        .and("amount", asked == null ? null : asked.value())
        .and(ActionFacts.REFUSED_BECAUSE, refusal)
        .asRecorded();
  }

  public static ActionVerdict.Refused theRefusalOfAnUnconfirmedChange() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(THE_ACTION_IT_ASKED_THIS_PROVIDER_TO_PERFORM));
  }

  public static ActionVerdict.Refused theRefusalOfAnActionThisSimulatorCannotRead() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(THE_ACTION_IT_ASKED_THIS_PROVIDER_TO_PERFORM));
  }

  public static ActionVerdict.@Nullable Refused theRefusalItReports(
      ObjectActionOutcome outcome, String providerObjectId, boolean theRunHoldsThatObject) {
    return switch (outcome) {
      case ObjectActionOutcome.Applied _ -> null;
      case ObjectActionOutcome.NoSuchObject _ ->
          ActionVerdict.notFound(RefusalSubject.noObjectAnsweredTo(PROVIDER_OBJECT_ID));
      case ObjectActionOutcome.NotOffered _,
          ObjectActionOutcome.RefusedByTheProvider _,
          ObjectActionOutcome.AnsweredByAScenarioInstead _ ->
          ActionVerdict.refused(theSubjectOf(providerObjectId, theRunHoldsThatObject));
    };
  }

  private static RefusalSubject theSubjectOf(
      String providerObjectId, boolean theRunHoldsThatObject) {
    return theRunHoldsThatObject
        ? RefusalSubject.theObject(providerObjectId)
        : RefusalSubject.noObjectWasNamed(THE_ACTION_IT_ASKED_THIS_PROVIDER_TO_PERFORM);
  }

  private static boolean isPositive(Amount amount) {
    return amount.number().filter(number -> number.signum() > 0).isPresent();
  }

  private static Balances balancesOf(DerivedBalances derived) {
    return new Balances(
        derived.capturedAmount().value(),
        derived.refundedAmount().value(),
        derived.capturableRemainder().value(),
        derived.refundableRemainder().value());
  }

  private static List<Operation> operationsOf(ProviderObjectState state) {
    List<Operation> operations = new ArrayList<>();
    for (MonetaryOperation operation : state.ledger().operations()) {
      operations.add(
          new Operation(
              operation.sequence(),
              operation.kind().name(),
              operation.amount().value(),
              operation.closesAuthorization(),
              operation.createdObjectId(),
              operation.nativeStatus(),
              StatusBadge.of(operation.normalizedStatus()),
              operation.at()));
    }
    return List.copyOf(operations);
  }
}
