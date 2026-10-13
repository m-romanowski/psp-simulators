package dev.marcinromanowski.pspsim.provider.stripe;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class StripeSignature {

  static final String HEADER = "Stripe-Signature";

  static final String SEPARATOR = ".";

  static final String TEST_SCHEME_MATERIAL = "v0" + SEPARATOR;

  static final String A_KEY_THE_MERCHANT_WAS_NEVER_GIVEN = "not-";

  private static final String ALGORITHM = "HmacSHA256";

  private StripeSignature() {}

  static String of(
      String secret, StripeSignatureEmission emission, Instant timestamp, byte[] body) {
    String seconds = Long.toString(timestamp.getEpochSecond());
    byte[] signed = joined(seconds + SEPARATOR, body);
    String keyOfTheV1 =
        emission == StripeSignatureEmission.CORRUPTED
            ? A_KEY_THE_MERCHANT_WAS_NEVER_GIVEN + secret
            : secret;
    return "t="
        + seconds
        + ",v1="
        + hmac(keyOfTheV1, signed)
        + ",v0="
        + hmac(secret, joined(TEST_SCHEME_MATERIAL + seconds + SEPARATOR, body));
  }

  private static byte[] joined(String prefix, byte[] body) {
    ByteArrayOutputStream material = new ByteArrayOutputStream();
    material.writeBytes(prefix.getBytes(StandardCharsets.UTF_8));
    material.writeBytes(body);
    return material.toByteArray();
  }

  private static String hmac(String key, byte[] message) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(message));
    } catch (NoSuchAlgorithmException | InvalidKeyException unavailable) {
      throw new IllegalStateException("a signed Stripe delivery needs " + ALGORITHM, unavailable);
    }
  }
}
