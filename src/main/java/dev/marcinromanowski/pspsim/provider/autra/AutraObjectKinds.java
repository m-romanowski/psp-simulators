package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;

final class AutraObjectKinds {

  static final ProviderObjectKind A_HOLD =
      new ProviderObjectKind(
          "a hold on a card",
          "Autra reserved the limit on the card and has charged nothing yet — \"O limite do cartao"
              + " esta reservado, mas o cliente ainda nao foi cobrado\" — so a capture is what"
              + " takes the money and a void is what releases it.");

  static final ProviderObjectKind A_SALE =
      new ProviderObjectKind(
          "a sale on a stored card",
          "Autra charged a stored card the payer is not present for, the moment it accepted the"
              + " call, so there is no reservation left for a capture to take and what the screen"
              + " offers afterwards is a reversal.");

  static final ProviderObjectKind A_PIX =
      new ProviderObjectKind(
          "a Pix",
          "A Pix is the Brazilian instant transfer a payer pays by copying the payload behind its"
              + " QR code into their own bank app. No card is involved anywhere in one, and autra"
              + " publishes no status endpoint for one — \"Sem endpoint público de status\", no"
              + " public status endpoint — so what moves one is the payer step and what reports"
              + " one is the callback the merchant receives.");

  static final ProviderObjectKind A_CARD_SESSION =
      new ProviderObjectKind(
          "a hosted card page",
          "A card page autra hosts for a payer to type a card on, good for fifteen minutes from"
              + " when it was opened. No money moves on it ever: what finishing it leaves behind"
              + " is a stored card a later sale charges.");

  static final ProviderObjectKind A_CARD_PAYMENT_AUTRA_DECLINED =
      new ProviderObjectKind(
          "a card payment",
          "Autra declined this payment and keeps nothing that says which call asked for it: a"
              + " pre-authorization goes out as transactionType CREDIT and so can a sale, and an"
              + " attempt autra declined reserved nothing and charged nothing, so its operation"
              + " list is empty either way. A declined payment on transactionType DEBIT is a sale,"
              + " because autra accepts CREDIT alone on a pre-authorization.");

  private AutraObjectKinds() {}

  static ProviderObjectKind of(AutraPayment payment) {
    if (payment.ledger().holds(OperationKind.AUTHORIZATION)) {
      return A_HOLD;
    }
    if (payment.isASaleTheAcquirerAccepted() || payment.ledger().holds(OperationKind.CAPTURE)) {
      return A_SALE;
    }
    if (AutraFields.DEBIT.equals(payment.transactionType())) {
      return A_SALE;
    }
    return A_CARD_PAYMENT_AUTRA_DECLINED;
  }

  static ProviderObjectKind ofAPix() {
    return A_PIX;
  }

  static ProviderObjectKind ofACardSession() {
    return A_CARD_SESSION;
  }
}
