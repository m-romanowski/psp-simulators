package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

enum TranzilaWallet {
  APPLE_PAY("apple_pay", "apple_pay", "Apple Pay", false),

  GOOGLE_PAY("google_pay", "GOOGLE_PAY", "Google Pay", true);

  static final String ENABLED = "1";

  static final Set<TranzilaTranmode> THE_TRANMODES_A_WALLET_PAYS =
      Set.of(TranzilaTranmode.A, TranzilaTranmode.V, TranzilaTranmode.AK, TranzilaTranmode.VK);

  private final String parameter;
  private final String broker;
  private final String label;
  private final boolean itsTokenChargesAgain;

  TranzilaWallet(String parameter, String broker, String label, boolean itsTokenChargesAgain) {
    this.parameter = parameter;
    this.broker = broker;
    this.label = label;
    this.itsTokenChargesAgain = itsTokenChargesAgain;
  }

  String parameter() {
    return parameter;
  }

  String broker() {
    return broker;
  }

  String label() {
    return label;
  }

  boolean itsTokenChargesAgain() {
    return itsTokenChargesAgain;
  }

  boolean isOfferedOn(TranzilaPage page) {
    return ENABLED.equals(page.echoed().get(parameter))
        && THE_TRANMODES_A_WALLET_PAYS.contains(page.tranmode());
  }

  static List<TranzilaWallet> offeredOn(TranzilaPage page) {
    List<TranzilaWallet> offered = new ArrayList<>();
    for (TranzilaWallet wallet : values()) {
      if (wallet.isOfferedOn(page)) {
        offered.add(wallet);
      }
    }
    return List.copyOf(offered);
  }

  static Optional<TranzilaWallet> named(String parameter) {
    for (TranzilaWallet wallet : values()) {
      if (wallet.parameter.equals(parameter)) {
        return Optional.of(wallet);
      }
    }
    return Optional.empty();
  }

  static Optional<TranzilaWallet> ofTheBroker(String broker) {
    for (TranzilaWallet wallet : values()) {
      if (wallet.broker.equals(broker)) {
        return Optional.of(wallet);
      }
    }
    return Optional.empty();
  }
}
