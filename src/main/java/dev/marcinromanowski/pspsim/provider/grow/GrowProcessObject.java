package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.util.LinkedHashMap;
import java.util.Map;

final class GrowProcessObject {

  static final String MERCHANT_ID = "merchantId";

  static final String TRANSACTION_ID = "transactionId";

  static final String A_PROCESS_IS_NOT_A_TRANSACTION =
      "provider grow moves no money on a payment process. A process is the hosted page a payer has"
          + " not finished yet, and every operation grow publishes for capturing, settling or"
          + " refunding names a transactionId and a transactionToken, which a process has neither"
          + " of until a payer completes it. A payer's completion is what creates that"
          + " transaction, and POST /__simulator/grow/hosted/{processId}/complete is that"
          + " completion on the control API; this object carries the id it created as"
          + " relatedObjects."
          + TRANSACTION_ID;

  static final String THE_TOKEN_A_SAVED_PROCESS_MINTED_IS_READ_FROM_ITS_OBJECT =
      "The cardToken a process that saved a card mints is printed in one place on the wire, the"
          + " callback this simulator posts to the merchant's own endpoint, and that field is"
          + " withheld on the trail as a card credential, so the process object carries it as"
          + " relatedObjects."
          + GrowFields.CARD_TOKEN
          + ": open the completed process on the Payments screen and the value"
          + " createTransactionWithToken and getTokenTransactionsByExternalIdentifiers each"
          + " require is the one printed there, which is how a tester with no callback endpoint of"
          + " their own reaches either call";

  static final String NO_DEADLINE_ON_A_PROCESS =
      "provider grow publishes no deadline on a payment process at all: \"The request schema"
          + " carries no hold-lifetime parameter\", and createPaymentProcess has no TTL field"
          + " anywhere in it, so a process waits for its payer until someone finishes the page and"
          + " nothing expires it. The windows grow does publish are windows on the transaction a"
          + " completion creates and not on this object";

  private GrowProcessObject() {}

  static ProviderObjectState stateOf(GrowPaymentProcess process) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(process.processId()),
        GrowObjectKinds.of(process),
        process.nativeStatusCode(),
        process.normalizedStatus(),
        Amount.of(process.chargeSum()),
        null,
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjectsOf(process),
        process.origin(),
        process.updatedAt(),
        sessionFactsOf(process));
  }

  static HostedSessionFacts sessionFactsOf(GrowPaymentProcess process) {
    return new HostedSessionFacts.AnObjectAPayerFinishes(
        process.merchantId(),
        new HostedSessionFacts.Party.NamedBy(process.payerFullName()),
        process.holdsFunds() ? GrowOrigination.A_HOLD : GrowOrigination.A_SAVED_CARD,
        process.createdAt(),
        new HostedSessionFacts.Deadline.NoDeadlineThisProviderPublishes(NO_DEADLINE_ON_A_PROCESS));
  }

  static ObjectActionOutcome noActionMovesMoneyOnAProcess() {
    return new ObjectActionOutcome.RefusedByTheProvider(A_PROCESS_IS_NOT_A_TRANSACTION);
  }

  private static Map<String, String> relatedObjectsOf(GrowPaymentProcess process) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put(MERCHANT_ID, process.merchantId());
    GrowProcessOutcome outcome = process.outcome();
    String transactionId = outcome == null ? null : outcome.transactionId();
    if (transactionId != null) {
      related.put(TRANSACTION_ID, transactionId);
    }
    String cardToken = outcome == null ? null : outcome.cardToken();
    if (cardToken != null) {
      related.put(GrowFields.CARD_TOKEN, cardToken);
    }
    return related;
  }
}
