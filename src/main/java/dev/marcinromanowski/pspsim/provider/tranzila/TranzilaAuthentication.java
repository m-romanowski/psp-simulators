package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum TranzilaAuthentication {
  THE_ACCESS_TOKEN_IS_VERIFIED,

  THE_ACCESS_TOKEN_IS_NOT_VERIFIED;

  static final String SETTING = "tranzila.authentication";

  static final String WHY_THERE_ARE_TWO =
      "provider tranzila authenticates every call with four headers, the app key, the request"
          + " time, a nonce and an access token computed from them with the secret, and this"
          + " simulator verifies the token by default so that a client whose signing is wrong"
          + " finds out here rather than in production. The second value is this simulator's own"
          + " decision and no reading of the vendor, which publishes no permissive mode: under it"
          + " the app key must still name this account while the token, the request time and the"
          + " nonce are left unchecked";

  boolean verifiesTheAccessToken() {
    return this == THE_ACCESS_TOKEN_IS_VERIFIED;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            THE_ACCESS_TOKEN_IS_VERIFIED.name(),
            "every call must carry an access token this simulator can recompute, a request time"
                + " within five minutes and a nonce not used already, so a client that signs"
                + " wrongly finds out here"),
        new AcceptedValue(
            THE_ACCESS_TOKEN_IS_NOT_VERIFIED.name(),
            "the app key must still name this account, but the access token, the request time"
                + " and the nonce go unchecked, so a test may drive the wire without signing"));
  }

  static TranzilaAuthentication of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return THE_ACCESS_TOKEN_IS_VERIFIED;
    }
    for (TranzilaAuthentication configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two authentication modes this simulator offers for provider"
            + " tranzila. "
            + WHY_THERE_ARE_TWO
            + ". The two values are "
            + THE_ACCESS_TOKEN_IS_VERIFIED.name()
            + ", the default, and "
            + THE_ACCESS_TOKEN_IS_NOT_VERIFIED.name());
  }
}
