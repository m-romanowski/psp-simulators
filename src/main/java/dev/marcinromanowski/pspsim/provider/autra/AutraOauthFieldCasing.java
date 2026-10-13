package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum AutraOauthFieldCasing {
  CAMEL_CASE(AutraFields.ACCESS_TOKEN, AutraFields.TOKEN_TYPE, AutraFields.EXPIRES_IN),

  SNAKE_CASE(
      AutraFields.ACCESS_TOKEN_SNAKE, AutraFields.TOKEN_TYPE_SNAKE, AutraFields.EXPIRES_IN_SNAKE);

  static final String SETTING = "autra.oauth-field-casing";

  private final String accessToken;
  private final String tokenType;
  private final String expiresIn;

  AutraOauthFieldCasing(String accessToken, String tokenType, String expiresIn) {
    this.accessToken = accessToken;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
  }

  String accessToken() {
    return accessToken;
  }

  String tokenType() {
    return tokenType;
  }

  String expiresIn() {
    return expiresIn;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            CAMEL_CASE.name(),
            "the token response spells its three fields accessToken, tokenType and expiresIn"),
        new AcceptedValue(
            SNAKE_CASE.name(),
            "the token response spells them access_token, token_type and expires_in"));
  }

  static AutraOauthFieldCasing of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return CAMEL_CASE;
    }
    for (AutraOauthFieldCasing configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two spellings provider autra publishes for the three fields"
            + " of its token response. The reference publishes accessToken, tokenType and"
            + " expiresIn and marks all three required; the Setup guide publishes access_token,"
            + " token_type and expires_in, so the two values are "
            + CAMEL_CASE.name()
            + " and "
            + SNAKE_CASE.name());
  }
}
