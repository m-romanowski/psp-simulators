package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

enum TranzilaAccessToken {
  AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT,

  AS_THE_QUICKSTART_SIGNS_IT;

  static final String SETTING = "tranzila.access-token";

  static final String ALGORITHM = "HmacSHA256";

  static final String WHY_THERE_ARE_TWO =
      "provider tranzila publishes the access token twice and the two do not agree. The"
          + " authentication pages hash the app key with the secret, the request time and the"
          + " nonce as the key, \"hash_hmac using 'sha256' on application key with secret +"
          + " request-time + nonce\", with the request time in Unix seconds. The quickstart hashes"
          + " the app key, the request time and the nonce with the secret as the key, and takes"
          + " the request time in milliseconds";

  String expectedFor(String appKey, String secret, String requestTime, String nonce) {
    return switch (this) {
      case AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT ->
          hexOfTheHmac(secret + requestTime + nonce, appKey);
      case AS_THE_QUICKSTART_SIGNS_IT -> hexOfTheHmac(secret, appKey + requestTime + nonce);
    };
  }

  Optional<Instant> requestTimeIn(String sent) {
    Optional<Long> counted = TranzilaAccessToken.wholeNumberIn(sent);
    return switch (this) {
      case AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT ->
          counted.map(seconds -> Instant.ofEpochSecond(seconds.longValue()));
      case AS_THE_QUICKSTART_SIGNS_IT ->
          counted.map(millis -> Instant.ofEpochMilli(millis.longValue()));
    };
  }

  String theUnitOfTheRequestTime() {
    return switch (this) {
      case AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT -> "Unix seconds";
      case AS_THE_QUICKSTART_SIGNS_IT -> "Unix milliseconds";
    };
  }

  static Optional<Long> wholeNumberIn(String sent) {
    if (!sent.matches("[0-9]{1,15}")) {
      return Optional.empty();
    }
    return Optional.of(Long.valueOf(sent));
  }

  static String hexOfTheHmac(String key, String message) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException unavailable) {
      throw new IllegalStateException(
          "a provider tranzila access token needs " + ALGORITHM, unavailable);
    }
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT.name(),
            "the HMAC of the app key keyed with the secret, the request time in seconds and the"
                + " nonce, as the authentication pages publish it"),
        new AcceptedValue(
            AS_THE_QUICKSTART_SIGNS_IT.name(),
            "the HMAC of the app key, the request time in milliseconds and the nonce keyed with"
                + " the secret, as the quickstart signs it"));
  }

  static TranzilaAccessToken of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT;
    }
    for (TranzilaAccessToken configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two access token constructions this simulator verifies for"
            + " provider tranzila. "
            + WHY_THERE_ARE_TWO
            + ". The two values are "
            + AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT.name()
            + ", the default, and "
            + AS_THE_QUICKSTART_SIGNS_IT.name());
  }
}
