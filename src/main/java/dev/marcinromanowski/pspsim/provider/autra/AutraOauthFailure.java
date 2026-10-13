package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum AutraOauthFailure {
  BAD_REQUEST_WITH_ERROR_STRING(400),

  UNAUTHORIZED_INVALID_CLIENT(401);

  static final String SETTING = "autra.oauth-failure";

  static final String INVALID_CLIENT = "invalid_client";

  private final int httpStatus;

  AutraOauthFailure(int httpStatus) {
    this.httpStatus = httpStatus;
  }

  int httpStatus() {
    return httpStatus;
  }

  String bodyFor(String message) {
    return this == UNAUTHORIZED_INVALID_CLIENT ? INVALID_CLIENT : message;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            BAD_REQUEST_WITH_ERROR_STRING.name(),
            "a refused credential comes back 400 with a body that carries the message, as autra's"
                + " reference declares it"),
        new AcceptedValue(
            UNAUTHORIZED_INVALID_CLIENT.name(),
            "a refused credential comes back 401 invalid_client, as autra's setup guide answers"
                + " it"));
  }

  static AutraOauthFailure of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return BAD_REQUEST_WITH_ERROR_STRING;
    }
    for (AutraOauthFailure configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two answers provider autra publishes for a credential it"
            + " refuses. The reference gives the token endpoint a 400 whose body is {\"error\":"
            + " \"<mensagem>\"} and the Setup guide answers the same condition 401 invalid_client,"
            + " so the two values are "
            + BAD_REQUEST_WITH_ERROR_STRING.name()
            + " and "
            + UNAUTHORIZED_INVALID_CLIENT.name());
  }
}
