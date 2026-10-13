package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

record TranzilaPayment(TranzilaTransaction root, List<TranzilaTransaction> followOns) {

  TranzilaPayment {
    followOns = List.copyOf(followOns);
  }

  String paymentId() {
    return root.transactionIdAsText();
  }

  ProviderObjectIdentity identity() {
    ProviderObjectIdentity identity = ProviderObjectIdentity.of(paymentId());
    for (TranzilaTransaction followOn : followOns) {
      identity = identity.answeringAlsoTo(followOn.transactionIdAsText());
    }
    return identity;
  }

  Optional<TranzilaTransaction> transaction(long transactionId) {
    if (root.transactionId() == transactionId) {
      return Optional.of(root);
    }
    return followOns.stream().filter(held -> held.transactionId() == transactionId).findFirst();
  }

  Optional<TranzilaTransaction> theForce() {
    return approvedOfType(TranzilaTxnType.FORCE).findFirst();
  }

  Optional<TranzilaTransaction> theReversal() {
    return approvedOfType(TranzilaTxnType.REVERSAL).findFirst();
  }

  boolean isCancelled(TranzilaTransaction transaction) {
    return approvedOfType(TranzilaTxnType.CANCEL)
        .anyMatch(cancel -> cancel.references(transaction.transactionId()));
  }

  boolean isAJ5StillHeldAt(Instant now) {
    return root.isAJ5()
        && root.approved()
        && theForce().isEmpty()
        && theReversal().isEmpty()
        && root.isLiveAt(now);
  }

  Optional<TranzilaTransaction> whatACreditIsAgainst() {
    if (!root.approved()) {
      return Optional.empty();
    }
    if (root.type() == TranzilaTxnType.DEBIT) {
      return Optional.of(root);
    }
    return theForce();
  }

  BigDecimal theCreditsAgainst(TranzilaTransaction original) {
    return approvedOfType(TranzilaTxnType.CREDIT)
        .filter(credit -> credit.references(original.transactionId()))
        .filter(credit -> !isCancelled(credit))
        .map(TranzilaTransaction::theAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  BigDecimal refundableRemainder() {
    return whatACreditIsAgainst()
        .filter(original -> !isCancelled(original))
        .map(original -> original.theAmount().subtract(theCreditsAgainst(original)))
        .orElse(BigDecimal.ZERO);
  }

  NormalizedStatus normalizedStatusAt(Instant now) {
    if (!root.approved()) {
      return NormalizedStatus.FAILED;
    }
    if (root.isAJ2()) {
      return NormalizedStatus.SUCCEEDED;
    }
    if (root.type() == TranzilaTxnType.DEBIT) {
      return theRefundStatusOf(root);
    }
    Optional<TranzilaTransaction> force = theForce();
    if (force.isPresent()) {
      return theRefundStatusOf(force.get());
    }
    return isAJ5StillHeldAt(now) ? NormalizedStatus.READY_FOR_CAPTURE : NormalizedStatus.CANCELLED;
  }

  private NormalizedStatus theRefundStatusOf(TranzilaTransaction original) {
    if (isCancelled(original)) {
      return NormalizedStatus.CANCELLED;
    }
    BigDecimal credited = theCreditsAgainst(original);
    if (credited.signum() == 0) {
      return NormalizedStatus.SUCCEEDED;
    }
    return credited.compareTo(original.theAmount()) >= 0
        ? NormalizedStatus.REFUNDED
        : NormalizedStatus.PARTIALLY_REFUNDED;
  }

  String nativeStatus() {
    TranzilaTransaction latest = root;
    for (TranzilaTransaction followOn : followOns) {
      if (followOn.approved()) {
        latest = followOn;
      }
    }
    return latest.nativeStatus();
  }

  Ledger ledger() {
    if (!root.approved() || root.isAJ2()) {
      return Ledger.empty();
    }
    List<MonetaryOperation> operations = new ArrayList<>();
    boolean charged = root.type() == TranzilaTxnType.DEBIT;
    operations.add(
        operation(
            operations,
            OperationKind.AUTHORIZATION,
            root.amount(),
            Boolean.valueOf(charged),
            root,
            charged ? NormalizedStatus.SUCCEEDED : NormalizedStatus.READY_FOR_CAPTURE));
    Optional<TranzilaTransaction> original = whatACreditIsAgainst();
    if (original.isEmpty() || isCancelled(original.get())) {
      return new Ledger(operations);
    }
    TranzilaTransaction captured = original.get();
    operations.add(
        operation(
            operations,
            OperationKind.CAPTURE,
            captured.amount(),
            Boolean.TRUE,
            captured,
            NormalizedStatus.SUCCEEDED));
    BigDecimal credited = BigDecimal.ZERO;
    for (TranzilaTransaction credit : followOns) {
      if (credit.type() != TranzilaTxnType.CREDIT
          || !credit.approved()
          || !credit.references(captured.transactionId())
          || isCancelled(credit)) {
        continue;
      }
      credited = credited.add(credit.theAmount());
      operations.add(
          operation(
              operations,
              OperationKind.REFUND,
              credit.amount(),
              null,
              credit,
              credited.compareTo(captured.theAmount()) >= 0
                  ? NormalizedStatus.REFUNDED
                  : NormalizedStatus.PARTIALLY_REFUNDED));
    }
    return new Ledger(operations);
  }

  private static MonetaryOperation operation(
      List<MonetaryOperation> before,
      OperationKind kind,
      String amount,
      @org.jspecify.annotations.Nullable Boolean closesAuthorization,
      TranzilaTransaction by,
      NormalizedStatus status) {
    return new MonetaryOperation(
        before.size() + 1,
        kind,
        Amount.of(amount),
        closesAuthorization,
        null,
        by.nativeStatus(),
        status,
        by.createdAt());
  }

  boolean refundsAccepted() {
    return refundableRemainder().signum() > 0;
  }

  Instant updatedAt() {
    Instant latest = root.createdAt();
    for (TranzilaTransaction followOn : followOns) {
      if (followOn.approved() && followOn.createdAt().isAfter(latest)) {
        latest = followOn.createdAt();
      }
    }
    return latest;
  }

  private java.util.stream.Stream<TranzilaTransaction> approvedOfType(TranzilaTxnType type) {
    return followOns.stream()
        .filter(held -> held.type() == type)
        .filter(TranzilaTransaction::approved);
  }
}
