package dev.marcinromanowski.pspsim.provider.stripe;

import java.util.List;
import java.util.Map;

enum StripeVersionsAdmitted {
  THE_RANGE_ANSWERED,
  ALSO_THOSE_THE_MOBILE_SDKS_PIN;

  StripeVersionChoice choose(Map<String, List<String>> headers, StripeApiVersion accountDefault) {
    return switch (this) {
      case THE_RANGE_ANSWERED -> StripeVersionChoice.forTheAccount(headers, accountDefault);
      case ALSO_THOSE_THE_MOBILE_SDKS_PIN ->
          StripeVersionChoice.admittingTheMobileSdks(headers, accountDefault);
    };
  }
}
