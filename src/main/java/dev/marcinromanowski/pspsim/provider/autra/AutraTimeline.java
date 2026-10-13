package dev.marcinromanowski.pspsim.provider.autra;

import java.util.List;

final class AutraTimeline {

  private AutraTimeline() {}

  static final String SALE = "SALE";

  static final String PREAUTH = "PREAUTH";

  static final String CAPTURE = "CAPTURE";

  static final String VOID = "VOID";

  static final List<String> THE_TYPES_A_PAYMENT_IS = List.of(SALE, PREAUTH);

  static boolean isATypeAPaymentIs(String type) {
    return THE_TYPES_A_PAYMENT_IS.contains(type);
  }
}
