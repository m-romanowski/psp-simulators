package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class TimelineScreen {

  public static final String ASK_FOR_A_KEY =
      "a timeline stands on one id. Type the id a tester holds: an id the provider answered with,"
          + " a field one of this run's objects names another object by, a callback event id, or a"
          + " value sent under one of the keys this provider compares repeats against.";

  public static final String NOTHING_OF_THIS_RUN_CARRIES_IT =
      "no object, callback or keyed request of this test run under this provider carries that"
          + " value. A test run that was never written to and one that was reset read the same way"
          + " here.";

  public static final String CARRIED_BY_A_ROW_THAT_NAMES_NO_OBJECT =
      "this test run carries that value and the row carrying it names no provider object, so there"
          + " is no payment to trace. It was carried by ";

  public static final String MORE_THAN_ONE_OBJECT_CARRIES_IT =
      "more than one object of this test run names that value, so this key does not pick out one"
          + " payment. Open the one you meant.";

  public static final String A_REQUEST_IS_PAIRED_BY_POSITION =
      "this simulator writes no link between a request row and the response row that answered it,"
          + " and it puts no object id on the request row. This request is on the timeline because"
          + " it is the nearest earlier request of the same method and path that no later response"
          + " had already claimed, which is the request that answered it in a test run driving one"
          + " call at a time.";

  public static final String THE_OPERATION_RAN_AND_THE_ANSWER_DID_NOT_ARRIVE =
      "the operation ran and took effect, and the fault landed on the response, so the caller"
          + " holds no answer for a change this simulator made";

  public static final String NOTHING_RAN_BEFORE_THE_FAULT =
      "the fault replaced the operation, so this call changed nothing before it broke";

  public static final String THE_TRAIL_IS_LONGER_THAN_THIS_TIMELINE_READ =
      "this test run's trail is longer than one timeline read, so this page stops at the oldest"
          + " rows it read and later events of this object are not on it";

  public static final String NO_CALLBACK_ROW_NAMES_ITS_DELIVERY =
      "an attempt on the trail above and a callback in this list are two rows this simulator does"
          + " not link: the trail row carries the attempt number and the target, and the delivery"
          + " row carries the plan and the next attempt. Where one object has two deliveries,"
          + " which trail row belongs to which delivery is not recorded.";

  public static final String NO_SCHEDULING_IS_RECORDED =
      "this simulator writes a trail row when a callback is attempted, withheld by a rule or"
          + " refused by the provider, and none when one is queued, so the moment a callback was"
          + " scheduled has no place on the timeline above. The callbacks below are this object's,"
          + " in the order the queue holds them.";

  public static final String NO_TRANSITION_IS_RECORDED =
      "a provider object carries the status it holds now and the operations that moved its money,"
          + " and no row per status change, so the changes below are the object's ledger in its own"
          + " order and not events of the timeline above. The call that caused each one is on the"
          + " timeline.";

  public static final String NO_ACTOR_IS_RECORDED =
      "this simulator records which surface acted and never who was at it: it holds no accounts,"
          + " no sessions and no principals, so a row says UI or CONTROL_API and can say no more."
          + " Two testers driving one test run are one actor here.";

  public static final String NO_KEYED_REQUEST_NAMES_ITS_TRAIL_ROW =
      "a request recorded under one of this provider's keys carries no audit id, so a replay or a"
          + " conflict has no place on the timeline above. The Retries screen holds them.";

  public static final String NO_KEYED_REQUEST_IS_RECORDED_FOR_THIS_RUN =
      "no request under one of the keys this provider compares repeats against is recorded for this"
          + " test run, so a value of one finds nothing here. A provider that records none of them"
          + " and a run that has sent none read the same way.";

  public enum Found {
    PROVIDER_OBJECT_ID("the id the provider answered with"),
    RELATED_FIELD("a field an object of this run names another object by"),
    CALLBACK_EVENT("a callback event id"),
    KEYED_REQUEST("a value sent under a key this provider compares repeats against");

    private final String describedAs;

    Found(String describedAs) {
      this.describedAs = describedAs;
    }

    public String describedAs() {
      return describedAs;
    }
  }

  public record Candidate(String providerObjectId, String field) {}

  public sealed interface Subject {

    record Resolved(String providerObjectId, Found by, String field) implements Subject {}

    record Ambiguous(List<Candidate> candidates) implements Subject {

      public Ambiguous {
        candidates = List.copyOf(candidates);
      }
    }

    record CarriedButNamesNoObject(String carriedBy) implements Subject {}

    record NotFound() implements Subject {}

    record NotAsked() implements Subject {}
  }

  public record Entry(
      int place,
      String kind,
      String surface,
      String method,
      @Nullable String path,
      @Nullable Integer status,
      @Nullable StatusBadge outcome,
      @Nullable String refused,
      @Nullable String scenarioId,
      int attempt,
      Instant at,
      String auditId,
      @Nullable String lostResponse,
      @Nullable String pairedByPosition,
      boolean open,
      List<Disclosure.Field> headers,
      List<Disclosure.Field> body) {

    public Entry {
      headers = List.copyOf(headers);
      body = List.copyOf(body);
    }
  }

  public record Callback(
      String deliveryId,
      String eventId,
      @Nullable String eventType,
      StatusBadge state,
      @Nullable String shapedBy,
      @Nullable String notSentBecause,
      int attemptsMade,
      int attemptCap,
      @Nullable Instant nextAttemptAt,
      @Nullable String targetUrl) {}

  public record Transition(
      int sequence,
      String kind,
      String amount,
      String nativeStatus,
      StatusBadge normalizedStatus,
      @Nullable String createdObjectId,
      Instant at) {}

  public record Absent(String what, String why) {}

  public record View(
      String provider,
      String testRunId,
      @Nullable String key,
      @Nullable String foundBy,
      @Nullable String unresolved,
      List<Candidate> candidates,
      @Nullable String providerObjectId,
      List<String> answersTo,
      @Nullable String nativeStatus,
      @Nullable StatusBadge normalizedStatus,
      Map<String, String> relatedObjects,
      List<Entry> entries,
      List<Callback> callbacks,
      List<Transition> transitions,
      List<Absent> theProviderHasNoSuchThing,
      List<Absent> thisBuildRecordsNoSuchThing,
      @Nullable String truncated,
      boolean valuesShown) {

    public View {
      candidates = List.copyOf(candidates);
      answersTo = List.copyOf(answersTo);
      relatedObjects = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(relatedObjects));
      entries = List.copyOf(entries);
      callbacks = List.copyOf(callbacks);
      transitions = List.copyOf(transitions);
      theProviderHasNoSuchThing = List.copyOf(theProviderHasNoSuchThing);
      thisBuildRecordsNoSuchThing = List.copyOf(thisBuildRecordsNoSuchThing);
    }

    public boolean namesAnObject() {
      return providerObjectId != null;
    }

    public boolean answersToMoreThanOneIdentifier() {
      return answersTo.size() > 1;
    }
  }

  private TimelineScreen() {}

  public static @Nullable String keyAsked(@Nullable String declared) {
    if (declared == null) {
      return null;
    }
    String given = declared.strip();
    return given.isEmpty() ? null : given;
  }

  public static Subject subjectOf(
      @Nullable String key,
      @Nullable AddressedProviderObject addressed,
      List<ProviderObjectState> objects,
      List<WebhookDelivery> callbacks,
      KeyedExchangePage exchanges) {
    if (key == null) {
      return new Subject.NotAsked();
    }
    if (addressed != null) {
      return new Subject.Resolved(
          addressed.canonical(), Found.PROVIDER_OBJECT_ID, "providerObjectId");
    }
    List<Candidate> named = theObjectsThatNameIt(key, objects);
    if (named.size() == 1) {
      Candidate only = named.get(0);
      return new Subject.Resolved(only.providerObjectId(), Found.RELATED_FIELD, only.field());
    }
    if (!named.isEmpty()) {
      return new Subject.Ambiguous(named);
    }
    Subject fromACallback = theCallbackThatCarriesIt(key, callbacks);
    if (fromACallback != null) {
      return fromACallback;
    }
    Subject fromAKey = theKeyedRequestThatCarriesIt(key, exchanges);
    if (fromAKey != null) {
      return fromAKey;
    }
    return new Subject.NotFound();
  }

  private static List<Candidate> theObjectsThatNameIt(
      String key, List<ProviderObjectState> objects) {
    List<Candidate> named = new ArrayList<>();
    for (ProviderObjectState object : objects) {
      for (Map.Entry<String, String> related : object.relatedObjects().entrySet()) {
        if (key.equals(related.getValue())) {
          named.add(new Candidate(object.providerObjectId(), related.getKey()));
          break;
        }
      }
    }
    return List.copyOf(named);
  }

  private static @Nullable Subject theCallbackThatCarriesIt(
      String key, List<WebhookDelivery> callbacks) {
    for (WebhookDelivery delivery : callbacks) {
      if (!key.equals(delivery.eventId()) && !key.equals(delivery.deliveryId())) {
        continue;
      }
      String object = delivery.providerObjectId();
      if (object == null) {
        return new Subject.CarriedButNamesNoObject("a callback of this test run");
      }
      return new Subject.Resolved(object, Found.CALLBACK_EVENT, "eventId");
    }
    return null;
  }

  private static @Nullable Subject theKeyedRequestThatCarriesIt(
      String key, KeyedExchangePage exchanges) {
    for (KeyedExchange exchange : exchanges.exchanges()) {
      if (!key.equals(exchange.keyValue())) {
        continue;
      }
      String object = exchange.firstRequest().providerObjectId();
      if (object == null) {
        return new Subject.CarriedButNamesNoObject(
            "a request recorded under key " + exchange.keyName());
      }
      return new Subject.Resolved(object, Found.KEYED_REQUEST, exchange.keyName());
    }
    return null;
  }

  public static View of(
      String provider,
      String testRunId,
      @Nullable String key,
      Subject subject,
      @Nullable ProviderObjectState state,
      List<AuditRecord> trail,
      boolean trailTruncated,
      List<WebhookDelivery> callbacks,
      KeyedExchangePage exchanges,
      ProviderVocabulary vocabulary,
      ProviderKeyMechanisms mechanisms,
      @Nullable String openAuditId,
      boolean valuesAsked) {
    Subject.Resolved resolved = subject instanceof Subject.Resolved only ? only : null;
    String object = resolved == null ? null : resolved.providerObjectId();
    List<String> answersTo = theIdentifiersRowsOfThatObjectCarry(object, state);
    return new View(
        provider,
        testRunId,
        key,
        resolved == null ? null : resolved.by().describedAs() + ", " + resolved.field(),
        unresolvedBy(subject),
        subject instanceof Subject.Ambiguous ambiguous ? ambiguous.candidates() : List.of(),
        object,
        answersTo,
        state == null ? null : state.nativeStatus(),
        state == null ? null : StatusBadge.of(state.normalizedStatus()),
        state == null ? Map.of() : state.relatedObjects(),
        theTimelineOf(answersTo, trail, openAuditId, valuesAsked),
        theCallbacksOf(answersTo, callbacks),
        state == null ? List.of() : theTransitionsOf(state),
        theProviderHasNoSuchThing(vocabulary, mechanisms),
        thisBuildRecordsNoSuchThing(exchanges),
        trailTruncated ? THE_TRAIL_IS_LONGER_THAN_THIS_TIMELINE_READ : null,
        valuesAsked);
  }

  public static List<String> theIdentifiersRowsOfThatObjectCarry(
      @Nullable String object, @Nullable ProviderObjectState state) {
    if (object == null) {
      return List.of();
    }
    return state == null ? List.of(object) : state.identity().members();
  }

  public static @Nullable String unresolvedBy(Subject subject) {
    return switch (subject) {
      case Subject.NotFound _ -> NOTHING_OF_THIS_RUN_CARRIES_IT;
      case Subject.CarriedButNamesNoObject carried ->
          CARRIED_BY_A_ROW_THAT_NAMES_NO_OBJECT + carried.carriedBy();
      case Subject.Resolved _, Subject.Ambiguous _, Subject.NotAsked _ -> null;
    };
  }

  public static boolean namesOneOf(List<String> answersTo, @Nullable String carried) {
    return carried != null && answersTo.contains(carried);
  }

  public static List<Entry> theTimelineOf(
      List<String> answersTo,
      List<AuditRecord> trail,
      @Nullable String openAuditId,
      boolean valuesAsked) {
    boolean[] chosen = new boolean[trail.size()];
    boolean[] paired = new boolean[trail.size()];
    for (int index = 0; index < trail.size(); index++) {
      AuditRecord record = trail.get(index);
      if (!namesOneOf(answersTo, record.providerObjectId())) {
        continue;
      }
      chosen[index] = true;
      if (!answersAProviderCall(record)) {
        continue;
      }
      int asked = theRequestThisAnswered(trail, chosen, index);
      if (asked >= 0) {
        chosen[asked] = true;
        paired[asked] = true;
      }
    }
    List<Entry> entries = new ArrayList<>();
    for (int index = 0; index < trail.size(); index++) {
      if (chosen[index]) {
        entries.add(entryOf(index, trail.get(index), paired[index], openAuditId, valuesAsked));
      }
    }
    return List.copyOf(entries);
  }

  private static boolean answersAProviderCall(AuditRecord record) {
    return record.direction() == AuditDirection.OUTBOUND_RESPONSE
        && record.origin() == ObjectOrigin.PROVIDER_API;
  }

  private static boolean asksAProviderCall(AuditRecord record) {
    return record.direction() == AuditDirection.INBOUND_REQUEST
        && record.origin() == ObjectOrigin.PROVIDER_API;
  }

  private static int theRequestThisAnswered(
      List<AuditRecord> trail, boolean[] chosen, int answeredAt) {
    AuditRecord answer = trail.get(answeredAt);
    for (int index = answeredAt - 1; index >= 0; index--) {
      AuditRecord earlier = trail.get(index);
      if (answersAProviderCall(earlier)) {
        return -1;
      }
      if (!asksAProviderCall(earlier) || chosen[index]) {
        continue;
      }
      if (earlier.method().equals(answer.method())
          && earlier.theCalledPath().equals(answer.theCalledPath())) {
        return index;
      }
    }
    return -1;
  }

  private static Entry entryOf(
      int place,
      AuditRecord record,
      boolean pairedByPosition,
      @Nullable String openAuditId,
      boolean valuesAsked) {
    boolean open = record.auditId().equals(openAuditId);
    ProviderOutcome outcome = record.outcome();
    return new Entry(
        place,
        kindOf(record),
        record.origin().name(),
        record.method(),
        record.path(),
        record.httpStatus(),
        outcome == null ? null : StatusBadge.of(outcome),
        AuditScreen.whatARowRefused(record.refusal()),
        record.scenarioId(),
        record.attempt(),
        record.createdAt(),
        record.auditId(),
        lostResponseOf(record),
        pairedByPosition ? A_REQUEST_IS_PAIRED_BY_POSITION : null,
        open,
        open ? Disclosure.of(record.sanitizedHeaders(), valuesAsked) : List.of(),
        open ? Disclosure.of(record.sanitizedBody(), valuesAsked) : List.of());
  }

  public static String kindOf(AuditRecord record) {
    return switch (record.direction()) {
      case INBOUND_REQUEST -> askedBy(record.origin());
      case OUTBOUND_RESPONSE -> "provider-compatible response sent";
      case OUTBOUND_WEBHOOK_ATTEMPT -> "callback delivery attempt";
      case OUTBOUND_WEBHOOK_DROPPED -> "callback withheld by a scenario rule";
      case OUTBOUND_WEBHOOK_NOT_SENT -> "callback this provider would not send";
    };
  }

  private static String askedBy(ObjectOrigin origin) {
    return switch (origin) {
      case PROVIDER_API -> "provider-compatible request received";
      case UI -> "manual action on this simulator's own screens";
      case CONTROL_API -> "control API action";
      case SCHEDULED_JOB -> "action of this simulator's own scheduler";
    };
  }

  public static @Nullable String lostResponseOf(AuditRecord record) {
    DeliveredResponse delivered = record.delivered();
    if (delivered == null) {
      return null;
    }
    AppliedFault fault = delivered.transportFault();
    if (fault == null) {
      return null;
    }
    return switch (fault.placement()) {
      case AFTER_THE_OPERATION -> THE_OPERATION_RAN_AND_THE_ANSWER_DID_NOT_ARRIVE;
      case INSTEAD_OF_THE_OPERATION -> NOTHING_RAN_BEFORE_THE_FAULT;
    };
  }

  public static List<Callback> theCallbacksOf(
      List<String> answersTo, List<WebhookDelivery> callbacks) {
    List<Callback> owned = new ArrayList<>();
    for (WebhookDelivery delivery : callbacks) {
      if (!namesOneOf(answersTo, delivery.providerObjectId())) {
        continue;
      }
      owned.add(
          new Callback(
              delivery.deliveryId(),
              delivery.eventId(),
              delivery.eventType(),
              StatusBadge.of(delivery.state()),
              delivery.shapedBy(),
              delivery.notSentBecause(),
              delivery.attemptsMade(),
              delivery.retryPlan().attemptCap(),
              delivery.nextAttemptAt(),
              delivery.targetUrl()));
    }
    return List.copyOf(owned);
  }

  private static List<Transition> theTransitionsOf(ProviderObjectState state) {
    List<Transition> transitions = new ArrayList<>();
    for (MonetaryOperation operation : state.ledger().operations()) {
      Amount amount = operation.amount();
      transitions.add(
          new Transition(
              operation.sequence(),
              operation.kind().name(),
              amount.value(),
              operation.nativeStatus(),
              StatusBadge.of(operation.normalizedStatus()),
              operation.createdObjectId(),
              operation.at()));
    }
    return List.copyOf(transitions);
  }

  public static List<Absent> theProviderHasNoSuchThing(
      ProviderVocabulary vocabulary, ProviderKeyMechanisms mechanisms) {
    Map<String, String> absent = new LinkedHashMap<>();
    for (MatchFact fact : MatchFact.values()) {
      String instead = vocabulary.factsItNeverCarries().get(fact);
      if (instead != null) {
        absent.put(fact.property(), instead);
      }
    }
    for (KeyRole role : KeyRole.values()) {
      if (!mechanisms.has(role)) {
        absent.put(role.publishedName(), mechanisms.theAbsenceOf(role));
      }
    }
    List<Absent> named = new ArrayList<>();
    absent.forEach((what, why) -> named.add(new Absent(what, why)));
    return List.copyOf(named);
  }

  public static List<Absent> thisBuildRecordsNoSuchThing(KeyedExchangePage exchanges) {
    List<Absent> named = new ArrayList<>();
    named.add(new Absent("a callback that was scheduled", NO_SCHEDULING_IS_RECORDED));
    named.add(new Absent("a state transition of its own", NO_TRANSITION_IS_RECORDED));
    named.add(new Absent("who took a manual action", NO_ACTOR_IS_RECORDED));
    named.add(
        new Absent(
            "the delivery an attempt on the trail belongs to", NO_CALLBACK_ROW_NAMES_ITS_DELIVERY));
    named.add(
        new Absent(
            "a replay or a conflict under a key",
            exchanges.exchanges().isEmpty()
                ? NO_KEYED_REQUEST_IS_RECORDED_FOR_THIS_RUN
                : NO_KEYED_REQUEST_NAMES_ITS_TRAIL_ROW));
    return List.copyOf(named);
  }
}
