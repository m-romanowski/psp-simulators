package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum AzericardCallbackSignature {
  VALID,

  CORRUPTED,

  ABSENT;

  static final String SETTING = "azericard.callback-signature";

  static final String IS_ONE_OF_THREE =
      SETTING
          + " must be VALID, CORRUPTED or ABSENT. Provider azericard signs every callback and"
          + " publishes no wrong signature and no unsigned callback, so CORRUPTED and ABSENT are"
          + " the simulator's own names for the two callbacks a merchant's own P_SIGN"
          + " verification has to refuse";

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            VALID.name(),
            "the callback carries the P_SIGN this gateway signs it with, so your own check"
                + " passes"),
        new AcceptedValue(
            CORRUPTED.name(),
            "the callback carries a P_SIGN that cannot verify, so your check has something to"
                + " refuse"),
        new AcceptedValue(
            ABSENT.name(),
            "the callback carries no P_SIGN at all, so your check has nothing to verify"));
  }

  static AzericardCallbackSignature of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return VALID;
    }
    for (AzericardCallbackSignature emission : values()) {
      if (emission.name().equals(declared)) {
        return emission;
      }
    }
    throw new IllegalArgumentException(property + IS_ONE_OF_THREE);
  }
}
