package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum AzericardPartialCompletion {
  CLOSES_THE_AUTHORIZATION(Boolean.TRUE),

  LEAVES_THE_REMAINDER_CAPTURABLE(Boolean.FALSE);

  static final String SETTING = "azericard.partial-completion";

  private final Boolean closesAuthorization;

  AzericardPartialCompletion(Boolean closesAuthorization) {
    this.closesAuthorization = closesAuthorization;
  }

  Boolean closesTheAuthorizationAfter(boolean completedInFull) {
    return completedInFull ? Boolean.TRUE : closesAuthorization;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            CLOSES_THE_AUTHORIZATION.name(),
            "completing part of the amount closes the authorization, so nothing is left to"
                + " capture"),
        new AcceptedValue(
            LEAVES_THE_REMAINDER_CAPTURABLE.name(),
            "completing part of the amount leaves the rest capturable by a second completion"));
  }

  static AzericardPartialCompletion of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return CLOSES_THE_AUTHORIZATION;
    }
    for (AzericardPartialCompletion configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names no partial-completion behaviour of provider azericard, which are "
            + CLOSES_THE_AUTHORIZATION.name()
            + " and "
            + LEAVES_THE_REMAINDER_CAPTURABLE.name());
  }
}
