package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AzericardPayerSessionObject {

  static final String AWAITING_THE_PAYER = "awaiting the payer";

  static final String INT_REF = "intRef";

  static final String A_PAGE_IS_NOT_A_TRANSACTION =
      "provider azericard moves no money on a payment page. The page is what a payer has not"
          + " finished yet, and every completion or reversal this gateway publishes names the RRN"
          + " and INT_REF of an authorization, which a page has neither of until a payer submits"
          + " it. POST /__simulator/azericard/hosted/{providerObjectId}/complete is that submission"
          + " on the control API; this object carries the authorization it created as"
          + " relatedObjects."
          + INT_REF;

  static final String NO_PARTY =
      "provider azericard names no payer on the request that opens this page: NAME is optional and"
          + " is the cardholder's name as printed on the card, and this request carried none";

  static final String NO_DEADLINE =
      "provider azericard publishes no lifetime for its payment page; the only time bound on the"
          + " request that opens it is the one-hour TIMESTAMP window, which this simulator checks"
          + " when the page is opened and not again when the payer submits it";

  private AzericardPayerSessionObject() {}

  static ProviderObjectState stateOf(
      AzericardPayerSession session, @Nullable AzericardPayment payment) {
    AzericardMessage message = session.message();
    return new ProviderObjectState(
        ProviderObjectIdentity.of(session.sessionId()),
        AzericardObjectKinds.ofAPayerPage(),
        nativeStatusOf(session, payment),
        normalizedStatusOf(session, payment),
        Amount.of(message.amount()),
        message.currency(),
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjectsOf(session),
        session.origin(),
        session.settledAt() == null ? session.createdAt() : session.settledAt(),
        factsOf(session, message));
  }

  private static String nativeStatusOf(
      AzericardPayerSession session, @Nullable AzericardPayment payment) {
    if (payment != null) {
      return payment.nativeStatus();
    }
    PayerOutcome outcome = session.outcome();
    return outcome == null ? AWAITING_THE_PAYER : outcome.name();
  }

  private static NormalizedStatus normalizedStatusOf(
      AzericardPayerSession session, @Nullable AzericardPayment payment) {
    if (payment != null) {
      return payment.normalizedStatus();
    }
    return session.isSettled() ? NormalizedStatus.FAILED : NormalizedStatus.NOT_INITIALIZED;
  }

  private static HostedSessionFacts factsOf(
      AzericardPayerSession session, AzericardMessage message) {
    String named = message.cardholderName();
    return new HostedSessionFacts.AnObjectAPayerFinishes(
        session.merchantId(),
        named == null
            ? new HostedSessionFacts.Party.NoPartyThisProviderPublishes(NO_PARTY)
            : new HostedSessionFacts.Party.NamedBy(named),
        message.operationName(),
        session.createdAt(),
        new HostedSessionFacts.Deadline.NoDeadlineThisProviderPublishes(NO_DEADLINE));
  }

  private static Map<String, String> relatedObjectsOf(AzericardPayerSession session) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put("merchantId", session.merchantId());
    related.put("terminal", session.terminalId());
    related.put("order", session.message().order());
    String intRef = session.intRef();
    if (intRef != null) {
      related.put(INT_REF, intRef);
    }
    return related;
  }
}
