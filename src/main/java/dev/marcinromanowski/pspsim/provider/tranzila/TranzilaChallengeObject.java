package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class TranzilaChallengeObject {

  static final String AWAITING_THE_PAYER = "challenge awaiting the payer";

  static final String PASSED = "challenge passed, not completed";

  static final String FAILED = "challenge failed, not completed";

  private TranzilaChallengeObject() {}

  static ProviderObjectState stateOf(
      TranzilaChallenge challenge, @Nullable TranzilaObject transaction, Instant now) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(challenge.trackId()),
        TranzilaObjectKinds.A_CHALLENGE,
        nativeStatusOf(challenge, transaction),
        transaction == null
            ? NormalizedStatus.NOT_INITIALIZED
            : transaction.normalizedStatusAt(now),
        Amount.of(challenge.amount()),
        challenge.currencyCode(),
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjectsOf(challenge),
        challenge.origin(),
        updatedAtOf(challenge));
  }

  static Instant updatedAtOf(TranzilaChallenge challenge) {
    Instant completedAt = challenge.completedAt();
    if (completedAt != null) {
      return completedAt;
    }
    Instant answeredAt = challenge.answeredAt();
    return answeredAt == null ? challenge.createdAt() : answeredAt;
  }

  static String nativeStatusOf(TranzilaChallenge challenge, @Nullable TranzilaObject transaction) {
    if (transaction != null) {
      return transaction.nativeStatus();
    }
    if (!challenge.isAnswered()) {
      return AWAITING_THE_PAYER;
    }
    return challenge.passed() ? PASSED : FAILED;
  }

  static Map<String, String> relatedObjectsOf(TranzilaChallenge challenge) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put(TranzilaTerminal.TERMINAL_NAME_PROPERTY, challenge.terminalName());
    related.put(TranzilaFields.CARD_MASK, challenge.card().cardMask());
    if (challenge.isCompleted()) {
      related.put(TranzilaFields.TRANSACTION_ID, Long.toString(challenge.transactionId()));
    }
    return related;
  }
}
