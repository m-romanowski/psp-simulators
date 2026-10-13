package dev.marcinromanowski.pspsim.provider.stripe;

import org.jspecify.annotations.Nullable;

final class StripeFlag {

  static final String TRUE = "true";

  static final String FALSE = "false";

  private StripeFlag() {}

  static final String THE_VALUES_IT_ACCEPTS = TRUE + ", " + FALSE;

  static @Nullable Boolean of(@Nullable String sent) {
    if (sent == null) {
      return Boolean.FALSE;
    }
    if (TRUE.equals(sent)) {
      return Boolean.TRUE;
    }
    return FALSE.equals(sent) ? Boolean.FALSE : null;
  }
}
