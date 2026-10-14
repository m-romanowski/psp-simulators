package dev.marcinromanowski.pspsim.kernel.idempotency;

import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RequestFingerprint {

  public static final String ALGORITHM = "HmacSHA256";

  private static final char BETWEEN_PARTS = '\n';

  private RequestFingerprint() {}

  public static String of(FingerprintKey key, FingerprintedRequest request) {
    StringBuilder compared = new StringBuilder();
    compared.append(request.method()).append(BETWEEN_PARTS).append(request.path());
    for (String parameter : request.comparedParameters()) {
      compared.append(BETWEEN_PARTS).append(parameter);
    }
    return keyedDigestOf(key, compared.toString());
  }

  private static String keyedDigestOf(FingerprintKey key, String compared) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(key.bytes(), ALGORITHM));
      return HexFormat.of().formatHex(mac.doFinal(compared.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException unavailable) {
      throw new IllegalStateException(
          ALGORITHM + " is required to compare a repeat against the request it repeats",
          unavailable);
    }
  }
}
