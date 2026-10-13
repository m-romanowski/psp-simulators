package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;

final class GrowObjectKinds {

  static final ProviderObjectKind A_PAGE_THAT_HOLDS_MONEY =
      new ProviderObjectKind(
          "a hosted page that holds money",
          "A payment process waiting for its payer on the page grow hosts. Nothing is held on any"
              + " card yet: grow holds the money when the payer picks a card and finishes that"
              + " page, and the transaction that completion creates is the one a settlement"
              + " settles and a refund refunds.");

  static final ProviderObjectKind A_PAGE_THAT_SAVES_A_CARD =
      new ProviderObjectKind(
          "a hosted page that saves a card",
          "A registration process waiting for its payer on the page grow hosts. It moves no money"
              + " ever: finishing it saves the card and hands back a token, and charging that"
              + " token later is a transaction of its own.");

  static final ProviderObjectKind A_PAYMENT_A_PAYER_FINISHED =
      new ProviderObjectKind(
          "a payment a payer finished",
          "The transaction grow created when a payer finished a hosted page. Whether it holds"
              + " money is decided by the page behind it: a page that only saved a card leaves a"
              + " transaction carrying no operation at all.");

  static final ProviderObjectKind A_CHARGE_ON_A_SAVED_CARD =
      new ProviderObjectKind(
          "a charge on a saved card",
          "A transaction grow created from a card token it had already saved, with no payer and no"
              + " hosted page anywhere in it.");

  static final ProviderObjectKind A_REFUND =
      new ProviderObjectKind(
          "a refund",
          "A transaction grow created to return money an earlier transaction took. Grow lists it"
              + " as a transaction in its own right, and it names the transaction it returned the"
              + " money from.");

  private GrowObjectKinds() {}

  static ProviderObjectKind of(GrowTransaction transaction) {
    return switch (transaction.kind()) {
      case PAYMENT -> A_PAYMENT_A_PAYER_FINISHED;
      case TOKEN_PAYMENT -> A_CHARGE_ON_A_SAVED_CARD;
      case REFUND -> A_REFUND;
    };
  }

  static ProviderObjectKind of(GrowPaymentProcess process) {
    return process.holdsFunds() ? A_PAGE_THAT_HOLDS_MONEY : A_PAGE_THAT_SAVES_A_CARD;
  }
}
