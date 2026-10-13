package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class InspectorScreen {

  public static final String NOTHING_HAS_BEEN_SENT_UNDER_A_KEY =
      "no request carrying one of the keys this provider declares has been answered for this test"
          + " run yet";

  public static final String NO_REPLAY_WAS_ANSWERED_UNDER_THIS_KEY =
      "no request under this key was answered by replaying the original response: read the role"
          + " above for what this provider does with a repeat instead";

  public static final String NO_REQUEST_UNDER_THIS_KEY_CLAIMED_IT =
      "every request under this key was refused because the key was still held by one that had not"
          + " finished answering, and the request that was holding it recorded nothing under this"
          + " key, so there is no original response to show: a request the endpoint refuses before"
          + " executing frees the key and records no answer under it";

  public static final String THIS_KEY_REACHED_NO_OBJECT =
      "no request under this key named an object of this simulator, so there is nothing for this"
          + " run's objects to link to or from";

  public static final String NO_OBJECT_OF_THIS_RUN_LINKS_TO_IT =
      "no object this run holds names the object this key reached, and none of that object's own"
          + " fields names another";

  public record Mechanism(
      String name,
      String role,
      String roleMeans,
      String carriedIn,
      String scopedBy,
      boolean canBeHeld,
      String whileItIsHeld) {}

  public record Absence(String role, String roleMeans, String instead) {}

  public record NamedObject(String field, String providerObjectId) {}

  public record Request(
      int sequence,
      String verdict,
      String verdictMeans,
      String operation,
      String requestFingerprint,
      @Nullable Integer status,
      @Nullable String contentType,
      @Nullable String body,
      @Nullable String providerObjectId,
      boolean stateChanged,
      @Nullable String timeout,
      @Nullable String conflictReason,
      Instant at) {

    public boolean carriesAResponse() {
      return status != null && body != null;
    }

    public boolean namesAConflict() {
      return conflictReason != null;
    }

    public boolean namesAFault() {
      return timeout != null;
    }

    public boolean replaysTheOriginal() {
      return ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY.name().equals(verdict);
    }

    public boolean neverReachedTheKeysOwnRecord() {
      return ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD.name().equals(verdict);
    }
  }

  public record Row(
      String cursor,
      String accountId,
      String keyName,
      String role,
      String roleMeans,
      String keyValue,
      String operation,
      String requestFingerprint,
      Instant firstRequestAt,
      List<Instant> replayTimestamps,
      boolean stateChanged,
      @Nullable String providerObjectId,
      List<NamedObject> objectsItNames,
      List<NamedObject> objectsThatNameIt,
      List<Request> requests) {

    public Row {
      replayTimestamps = List.copyOf(replayTimestamps);
      objectsItNames = List.copyOf(objectsItNames);
      objectsThatNameIt = List.copyOf(objectsThatNameIt);
      requests = List.copyOf(requests);
    }

    public boolean reachedAnObject() {
      return providerObjectId != null;
    }

    public boolean linksToAnything() {
      return !objectsItNames.isEmpty() || !objectsThatNameIt.isEmpty();
    }

    public List<Request> replayResponses() {
      List<Request> replayed = new ArrayList<>();
      for (Request request : requests) {
        if (request.replaysTheOriginal()) {
          replayed.add(request);
        }
      }
      return List.copyOf(replayed);
    }

    public boolean wasReplayed() {
      return !replayTimestamps.isEmpty();
    }

    public List<Request> repeats() {
      Request first = firstRequest();
      List<Request> later = new ArrayList<>();
      for (Request request : requests) {
        if (request.sequence() != first.sequence()) {
          later.add(request);
        }
      }
      return List.copyOf(later);
    }

    public boolean wasRepeated() {
      return requests.size() > 1;
    }

    public Request firstRequest() {
      for (Request request : requests) {
        if (!request.neverReachedTheKeysOwnRecord()) {
          return request;
        }
      }
      return requests.get(0);
    }

    public boolean aRequestClaimedThisKey() {
      return !firstRequest().neverReachedTheKeysOwnRecord();
    }
  }

  public record View(
      String provider,
      String testRunId,
      List<Mechanism> mechanisms,
      List<Absence> absences,
      List<Row> rows,
      @Nullable String nextCursor) {

    public View {
      mechanisms = List.copyOf(mechanisms);
      absences = List.copyOf(absences);
      rows = List.copyOf(rows);
    }
  }

  private InspectorScreen() {}

  public static View of(
      String testRunId,
      ProviderKeyMechanisms mechanisms,
      KeyedExchangePage page,
      int pageSize,
      List<ProviderObjectState> objectsOfTheRun) {
    List<Mechanism> declared = new ArrayList<>();
    for (DeclaredKey key : mechanisms.keys()) {
      declared.add(
          new Mechanism(
              key.name(),
              key.role().publishedName(),
              key.role().whatARepeatDoes(),
              key.carriedIn().describedAs(),
              key.scopedBy(),
              key.whileItIsHeld().canBeHeld(),
              key.whileItIsHeld().describedAs()));
    }
    List<Absence> absent = new ArrayList<>();
    for (KeyRole role : KeyRole.values()) {
      if (!mechanisms.has(role)) {
        absent.add(
            new Absence(
                role.publishedName(), role.whatARepeatDoes(), mechanisms.theAbsenceOf(role)));
      }
    }
    List<Row> rows = new ArrayList<>();
    for (KeyedExchange exchange : page.exchanges()) {
      rows.add(rowOf(exchange, objectsOfTheRun));
    }
    return new View(
        mechanisms.provider().value(),
        testRunId,
        declared,
        absent,
        rows,
        page.cursorToCarryOnFrom(pageSize));
  }

  private static Row rowOf(KeyedExchange exchange, List<ProviderObjectState> objectsOfTheRun) {
    List<Request> requests = new ArrayList<>();
    for (KeyedRequest request : exchange.requests()) {
      requests.add(
          new Request(
              request.sequence(),
              request.verdict().name(),
              request.verdict().describedAs(),
              request.operation(),
              request.fingerprint(),
              request.httpStatus(),
              request.contentType(),
              request.body(),
              request.providerObjectId(),
              request.stateChanged(),
              request.whenTheFaultLanded(),
              exchange.conflictReasonOf(request),
              request.at()));
    }
    String object = theObjectItReached(exchange);
    return new Row(
        String.valueOf(exchange.arrival()),
        exchange.accountId(),
        exchange.keyName(),
        exchange.role().publishedName(),
        exchange.role().whatARepeatDoes(),
        exchange.keyValue(),
        exchange.operation(),
        exchange.fingerprint(),
        exchange.firstRequestAt(),
        exchange.replayTimestamps(),
        exchange.stateChanged(),
        object,
        theObjectsItNames(object, objectsOfTheRun),
        theObjectsThatNameIt(object, objectsOfTheRun),
        requests);
  }

  private static @Nullable String theObjectItReached(KeyedExchange exchange) {
    for (KeyedRequest request : exchange.requests()) {
      String named = request.providerObjectId();
      if (named != null) {
        return named;
      }
    }
    return null;
  }

  public static List<String> theIdentifiersThatObjectAnswersTo(
      @Nullable String object, List<ProviderObjectState> objectsOfTheRun) {
    if (object == null) {
      return List.of();
    }
    for (ProviderObjectState held : objectsOfTheRun) {
      if (held.identity().answersTo(object)) {
        return held.identity().members();
      }
    }
    return List.of(object);
  }

  private static List<NamedObject> theObjectsItNames(
      @Nullable String object, List<ProviderObjectState> objectsOfTheRun) {
    List<NamedObject> named = new ArrayList<>();
    for (ProviderObjectState held : objectsOfTheRun) {
      if (object == null || !held.identity().answersTo(object)) {
        continue;
      }
      for (Map.Entry<String, String> related : held.relatedObjects().entrySet()) {
        named.add(new NamedObject(related.getKey(), related.getValue()));
      }
    }
    return List.copyOf(named);
  }

  private static List<NamedObject> theObjectsThatNameIt(
      @Nullable String object, List<ProviderObjectState> objectsOfTheRun) {
    List<String> answersTo = theIdentifiersThatObjectAnswersTo(object, objectsOfTheRun);
    List<NamedObject> naming = new ArrayList<>();
    for (ProviderObjectState held : objectsOfTheRun) {
      if (answersTo.contains(held.identity().canonical())) {
        continue;
      }
      for (Map.Entry<String, String> related : held.relatedObjects().entrySet()) {
        if (answersTo.contains(related.getValue())) {
          naming.add(new NamedObject(related.getKey(), held.providerObjectId()));
        }
      }
    }
    return List.copyOf(naming);
  }
}
