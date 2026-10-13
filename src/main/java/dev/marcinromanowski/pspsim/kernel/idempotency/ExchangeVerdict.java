package dev.marcinromanowski.pspsim.kernel.idempotency;

public enum ExchangeVerdict {
  FIRST_REQUEST("the first request this simulator answered under this key"),
  TRUE_IDEMPOTENT_REPLAY("the original response, replayed without running the operation again"),
  DUPLICATE_KEY_REFUSAL("refused as a duplicate, with no operation run and no response replayed"),
  REPEAT_ADMITTED("a repeat of a value already seen that this duplicate check let through"),
  CORRELATED_RETRY("another request addressing the object this key names"),
  CONFLICTING_PAYLOAD("refused because this repeat did not send what the first request sent"),
  REFUSED_WHILE_THE_KEY_WAS_HELD(
      "refused because the key was still held by a request this simulator had not finished"
          + " answering, so there was no stored answer to replay and no completed request to"
          + " compare against");

  private final String describedAs;

  ExchangeVerdict(String describedAs) {
    this.describedAs = describedAs;
  }

  public String describedAs() {
    return describedAs;
  }

  public boolean couldHaveChangedState() {
    return switch (this) {
      case FIRST_REQUEST, REPEAT_ADMITTED, CORRELATED_RETRY -> true;
      case TRUE_IDEMPOTENT_REPLAY,
          DUPLICATE_KEY_REFUSAL,
          CONFLICTING_PAYLOAD,
          REFUSED_WHILE_THE_KEY_WAS_HELD ->
          false;
    };
  }

  public boolean isOrderedAgainstTheRequestThatClaimedTheKey() {
    return this != REFUSED_WHILE_THE_KEY_WAS_HELD;
  }

  public boolean isAdmittedUnder(KeyRole role) {
    if (!isOrderedAgainstTheRequestThatClaimedTheKey()) {
      return true;
    }
    return switch (role) {
      case TRUE_IDEMPOTENCY_KEY ->
          this == FIRST_REQUEST || this == TRUE_IDEMPOTENT_REPLAY || this == CONFLICTING_PAYLOAD;
      case DUPLICATE_DETECTION_KEY ->
          this == FIRST_REQUEST || this == DUPLICATE_KEY_REFUSAL || this == REPEAT_ADMITTED;
      case CORRELATION_KEY -> this == FIRST_REQUEST || this == CORRELATED_RETRY;
    };
  }
}
