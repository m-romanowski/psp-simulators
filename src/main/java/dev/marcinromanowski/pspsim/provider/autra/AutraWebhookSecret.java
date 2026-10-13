package dev.marcinromanowski.pspsim.provider.autra;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;

final class AutraWebhookSecret {

  static final String SETTING = "webhookSecret";

  static final String ALGORITHM = "HmacSHA256";

  static final String PREFIX = "sha256=";

  static final int SHORTEST_SECRET = 32;

  static final String A_SECRET_AUTRA_WOULD_TAKE = "autra-callback-secret-one-per-tenant";

  static final String IT_IS_AT_LEAST_THIRTY_TWO_CHARACTERS =
      SETTING
          + " must be at least "
          + SHORTEST_SECRET
          + " characters, because provider autra publishes the callback secret as \"uma string de"
          + " 32+ chars unica por tenant\" and a shorter one would be configured here and then"
          + " sign every delivery of the account with a credential autra would not have issued";

  static final String NOTHING_SIGNS_A_DELIVERY =
      "provider autra signs every acquiring callback with the tenant's own secret and this test run"
          + " configures none for the account the payment belongs to, so the delivery carries no"
          + " X-Autra-Signature at all. Configure "
          + SETTING
          + " on the account for a receiver that verifies the signature";

  private AutraWebhookSecret() {}

  static @Nullable String of(String propertyPrefix, @Nullable String configured) {
    if (configured == null || configured.isBlank()) {
      return null;
    }
    String secret = configured.strip();
    if (secret.length() < SHORTEST_SECRET) {
      throw new IllegalArgumentException(propertyPrefix + IT_IS_AT_LEAST_THIRTY_TWO_CHARACTERS);
    }
    return secret;
  }

  static String signatureOf(String secret, byte[] body) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      return PREFIX + HexFormat.of().formatHex(mac.doFinal(body));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException(
          "the runtime this simulator is on refused " + ALGORITHM, impossible);
    }
  }
}
