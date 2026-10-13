package dev.marcinromanowski.pspsim.provider.azericard;

import java.util.List;

enum AzericardTokenFlow {
  CARD_STORAGE(AzericardFields.TOKEN_ACTION + "=" + AzericardTokenFlow.REGISTER),

  STORED_CARD_PAYMENT(AzericardFields.TOKEN);

  static final String REGISTER = "REGISTER";

  static final String STORING_A_CARD = "S";

  static final String MERCHANT_INITIATED = "M";

  static final List<String> AGREEMENTS = List.of("N", "Y");

  private final String namedInAnOperation;

  AzericardTokenFlow(String namedInAnOperation) {
    this.namedInAnOperation = namedInAnOperation;
  }

  String namedInAnOperation() {
    return namedInAnOperation;
  }
}
