package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class TranzilaPageObject {

  static final String AWAITING_THE_PAYER = "awaiting the payer";

  static final String LEFT = "left without paying";

  static final String THE_CONTACT = "contact";

  static final String NO_PARTY =
      "provider tranzila names a payer on its payment page only in the predetermined field"
          + " contact, and the parameters this page was opened with carried none";

  static final String NO_DEADLINE =
      "provider tranzila publishes no lifetime for its payment page; the one time bound it"
          + " publishes is the handshake's, \"retained for 20 minutes\" and renewed by a"
          + " successful charge, which binds a thtk and not the page";

  private TranzilaPageObject() {}

  static ProviderObjectState stateOf(
      TranzilaPage page, @Nullable TranzilaObject transaction, Instant now) {
    Instant settledAt = page.settledAt();
    return new ProviderObjectState(
        ProviderObjectIdentity.of(page.pageId()),
        TranzilaObjectKinds.A_PAYMENT_PAGE,
        nativeStatusOf(page, transaction),
        normalizedStatusOf(page, transaction, now),
        Amount.of(page.sum()),
        page.currency().alpha(),
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjectsOf(page),
        page.origin(),
        settledAt == null ? page.createdAt() : settledAt,
        factsOf(page));
  }

  static String nativeStatusOf(TranzilaPage page, @Nullable TranzilaObject transaction) {
    if (transaction != null) {
      return transaction.nativeStatus();
    }
    PayerOutcome outcome = page.outcome();
    if (outcome == null) {
      return AWAITING_THE_PAYER;
    }
    if (outcome == PayerOutcome.ABANDONED) {
      return LEFT;
    }
    return page.tranmode().wire() + " " + page.response();
  }

  static NormalizedStatus normalizedStatusOf(
      TranzilaPage page, @Nullable TranzilaObject transaction, Instant now) {
    if (transaction != null) {
      return transaction.normalizedStatusAt(now);
    }
    PayerOutcome outcome = page.outcome();
    if (outcome == null) {
      return NormalizedStatus.NOT_INITIALIZED;
    }
    return outcome == PayerOutcome.ABANDONED
        ? NormalizedStatus.CANCELLED
        : NormalizedStatus.SUCCEEDED;
  }

  static Map<String, String> relatedObjectsOf(TranzilaPage page) {
    Map<String, String> related = new LinkedHashMap<>();
    related.put(TranzilaTerminal.TERMINAL_NAME_PROPERTY, page.terminalName());
    Long transactionId = page.transactionId();
    if (transactionId != null) {
      related.put(TranzilaFields.TRANSACTION_ID, transactionId.toString());
    }
    return related;
  }

  static HostedSessionFacts factsOf(TranzilaPage page) {
    String contact = page.echoed().get(THE_CONTACT);
    return new HostedSessionFacts.AnObjectAPayerFinishes(
        page.accountId(),
        contact == null
            ? new HostedSessionFacts.Party.NoPartyThisProviderPublishes(NO_PARTY)
            : new HostedSessionFacts.Party.NamedBy(contact),
        page.theOperation(),
        page.createdAt(),
        new HostedSessionFacts.Deadline.NoDeadlineThisProviderPublishes(NO_DEADLINE));
  }
}
