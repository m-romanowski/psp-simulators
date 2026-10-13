package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import java.time.Instant;
import java.util.Optional;

record TranzilaObject(TranzilaPayment payment, TranzilaTransaction subject) {

  static Optional<TranzilaObject> of(TranzilaPayment payment, long transactionId) {
    return payment.transaction(transactionId).map(subject -> new TranzilaObject(payment, subject));
  }

  boolean isTheOriginal() {
    return subject.isTheOriginal();
  }

  String objectId() {
    return subject.transactionIdAsText();
  }

  String nativeStatus() {
    return subject.nativeStatus();
  }

  NormalizedStatus normalizedStatusAt(Instant now) {
    if (isTheOriginal()) {
      return payment.normalizedStatusAt(now);
    }
    if (!subject.approved()) {
      return NormalizedStatus.FAILED;
    }
    return payment.isCancelled(subject) ? NormalizedStatus.CANCELLED : NormalizedStatus.SUCCEEDED;
  }

  Instant updatedAt() {
    if (isTheOriginal()) {
      return payment.updatedAt();
    }
    return payment
        .theCancelOf(subject)
        .map(TranzilaTransaction::createdAt)
        .orElse(subject.createdAt());
  }
}
