package dev.marcinromanowski.pspsim.provider.grow;

import org.jspecify.annotations.Nullable;

record GrowProcessOutcome(
    Kind kind,
    @Nullable String transactionId,
    @Nullable String transactionToken,
    @Nullable String asmachta,
    @Nullable String cardToken,
    @Nullable String paymentDate,
    @Nullable GrowPayerCard card) {

  enum Kind {
    COMPLETED,
    FAILED,
    ABANDONED
  }

  static GrowProcessOutcome failed() {
    return new GrowProcessOutcome(Kind.FAILED, null, null, null, null, null, null);
  }

  static GrowProcessOutcome abandoned() {
    return new GrowProcessOutcome(Kind.ABANDONED, null, null, null, null, null, null);
  }

  GrowRecordedCard cardCharged() {
    GrowPayerCard chosen = card;
    return chosen == null
        ? GrowRecordedCard.THE_PUBLISHED_SANDBOX_CARD
        : GrowRecordedCard.chosenBy(chosen);
  }
}
