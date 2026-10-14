package dev.marcinromanowski.pspsim.kernel.idempotency;

import org.jspecify.annotations.Nullable;

public enum KeyedOutcome {
  RAN_THE_OPERATION(null),
  REFUSED_AS_A_DUPLICATE(ExchangeVerdict.DUPLICATE_KEY_REFUSAL),
  REPLAYED(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY),
  REFUSED_AS_A_CONFLICT(ExchangeVerdict.CONFLICTING_PAYLOAD),
  REFUSED_WHILE_THE_KEY_WAS_HELD(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD);

  private final @Nullable ExchangeVerdict refusedOrReplayedAs;

  KeyedOutcome(@Nullable ExchangeVerdict refusedOrReplayedAs) {
    this.refusedOrReplayedAs = refusedOrReplayedAs;
  }

  public ExchangeVerdict recordedWhereNoRequestRanTheOperation() {
    ExchangeVerdict kept = refusedOrReplayedAs;
    return kept == null ? ExchangeVerdict.FIRST_REQUEST : kept;
  }

  public ExchangeVerdict recordedAfterTheRequestThatRanTheOperation(KeyRole role) {
    ExchangeVerdict kept = refusedOrReplayedAs;
    return kept == null ? role.aRepeatThatRanTheOperation() : kept;
  }
}
