package dev.marcinromanowski.pspsim.provider.autra;

import java.util.List;

enum AutraPixEvent {
  FINANCIAL_TRANSACTION("acquiring.financial_transaction", "FINANCIAL_TRANSACTION"),
  PAYOUT("acquiring.payout", "PAYOUT");

  static final String NO_SUCH_EVENT =
      "provider autra publishes five event types on the acquiring domain and a Pix of this contract"
          + " produces two of them: acquiring.financial_transaction, which reports what the payer"
          + " did, and acquiring.payout, which reports the settlement of what they paid. The other"
          + " three — acquiring.financial_cycle, acquiring.payment_slip_notification and"
          + " acquiring.merchant_onboarding_status — are published with no payload at all, so"
          + " there is nothing for this simulator to render for them";

  private final String eventType;

  private final String subject;

  AutraPixEvent(String eventType, String subject) {
    this.eventType = eventType;
    this.subject = subject;
  }

  String eventType() {
    return eventType;
  }

  String subject() {
    return subject;
  }

  static List<String> theEventTypesItRenders() {
    return List.of(FINANCIAL_TRANSACTION.eventType, PAYOUT.eventType);
  }

  static AutraPixEvent named(String eventType) {
    for (AutraPixEvent known : values()) {
      if (known.eventType.equals(eventType)) {
        return known;
      }
    }
    throw new IllegalArgumentException(NO_SUCH_EVENT);
  }
}
