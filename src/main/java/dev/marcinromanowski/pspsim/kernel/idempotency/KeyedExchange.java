package dev.marcinromanowski.pspsim.kernel.idempotency;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record KeyedExchange(
    ProviderId provider,
    TestRunId testRunId,
    String accountId,
    String keyName,
    KeyRole role,
    String keyValue,
    long arrival,
    List<KeyedRequest> requests) {

  public static final String SCOPE_THEN_VALUE = " ";

  public static final String THE_REQUEST_THAT_RAN_WAS_NOT_RECORDED =
      "the request that ran the operation under this key, and stored the answer these requests"
          + " were given, was not recorded, so its answer is shown only as they replayed it";

  public static final String THE_REQUEST_IT_CONFLICTS_WITH_WAS_NOT_RECORDED =
      "this repeat did not send what the request that ran the operation under this key sent, and"
          + " that request was not recorded, so there is no first request to compare it against";

  public KeyedExchange {
    if (requests.isEmpty()) {
      throw new IllegalArgumentException(
          "an exchange is the requests this simulator answered under one key, and an exchange with"
              + " none of them would show a tester a key nothing was ever sent under");
    }
    for (int index = 0; index < requests.size(); index++) {
      KeyedRequest request = requests.get(index);
      if (request.sequence() != index) {
        throw new IllegalArgumentException(
            "the requests under key "
                + keyName
                + " are numbered in the order this simulator answered them, and request "
                + request.sequence()
                + " sits in position "
                + index
                + ", so a reader could not tell which answer came first");
      }
      if (!request.verdict().isAdmittedUnder(role)) {
        throw new IllegalArgumentException(
            "key "
                + keyName
                + " is a "
                + role.publishedName()
                + ", where "
                + role.whatARepeatDoes()
                + ", so a request under it can never be recorded as "
                + request.verdict().name()
                + ": that would teach a tester the opposite of what this provider does");
      }
    }
    KeyedRequest claimant = theRequestThatClaimedTheKey(requests);
    if (claimant != null && claimant.verdict() != ExchangeVerdict.FIRST_REQUEST) {
      throw new IllegalArgumentException(
          "the earliest request recorded under key "
              + keyName
              + " that ran the operation is a "
              + claimant.verdict().name()
              + ", and a repeat with nothing to repeat is a record of an answer this simulator"
              + " never gave");
    }
    requests = List.copyOf(requests);
  }

  private static @Nullable KeyedRequest theRequestThatClaimedTheKey(List<KeyedRequest> requests) {
    for (KeyedRequest request : requests) {
      if (request.verdict().couldHaveChangedState()) {
        return request;
      }
    }
    return null;
  }

  public @Nullable String whatThisLogDidNotRecord() {
    for (KeyedRequest request : requests) {
      if (request.verdict().couldHaveChangedState()) {
        return null;
      }
      if (request.verdict().answersFromWhatTheRequestThatRanStored()) {
        return THE_REQUEST_THAT_RAN_WAS_NOT_RECORDED;
      }
    }
    return null;
  }

  public boolean isKeyedBy(String sent) {
    return isKeyedExactlyBy(sent) || keyValue.endsWith(SCOPE_THEN_VALUE + sent);
  }

  public boolean isKeyedExactlyBy(String sent) {
    return keyValue.equals(sent);
  }

  public static @Nullable KeyedExchange theOneKeyedBy(String sent, List<KeyedExchange> exchanges) {
    for (KeyedExchange exchange : exchanges) {
      if (exchange.isKeyedExactlyBy(sent)) {
        return exchange;
      }
    }
    for (KeyedExchange exchange : exchanges) {
      if (exchange.isKeyedBy(sent)) {
        return exchange;
      }
    }
    return null;
  }

  public KeyedRequest firstRequest() {
    KeyedRequest claimant = theRequestThatClaimedTheKey(requests);
    if (claimant != null) {
      return claimant;
    }
    for (KeyedRequest request : requests) {
      if (request.verdict().isOrderedAgainstTheRequestThatClaimedTheKey()) {
        return request;
      }
    }
    return requests.get(0);
  }

  public Instant firstRequestAt() {
    return firstRequest().at();
  }

  public String fingerprint() {
    return firstRequest().fingerprint();
  }

  public String operation() {
    return firstRequest().operation();
  }

  public List<KeyedRequest> repeats() {
    KeyedRequest first = firstRequest();
    List<KeyedRequest> later = new ArrayList<>();
    for (KeyedRequest request : requests) {
      if (request.sequence() != first.sequence()) {
        later.add(request);
      }
    }
    return List.copyOf(later);
  }

  public List<Instant> replayTimestamps() {
    List<Instant> replayed = new ArrayList<>();
    for (KeyedRequest repeat : repeats()) {
      if (repeat.verdict() == ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY) {
        replayed.add(repeat.at());
      }
    }
    return List.copyOf(replayed);
  }

  public boolean stateChanged() {
    for (KeyedRequest request : requests) {
      if (request.stateChanged()) {
        return true;
      }
    }
    return false;
  }

  public @Nullable String conflictReasonOf(KeyedRequest request) {
    if (request.verdict() != ExchangeVerdict.CONFLICTING_PAYLOAD) {
      return null;
    }
    KeyedRequest first = firstRequest();
    if (first.sequence() == request.sequence()) {
      return THE_REQUEST_IT_CONFLICTS_WITH_WAS_NOT_RECORDED;
    }
    if (!request.operation().equals(first.operation())) {
      return "this repeat asked for "
          + request.operation()
          + ", and the first request under this key asked for "
          + first.operation()
          + ", so the two are not the same request under one key";
    }
    return "this repeat sent parameters the first request did not: its request fingerprint is "
        + request.fingerprint()
        + " and the first request under this key fingerprinted as "
        + first.fingerprint();
  }
}
