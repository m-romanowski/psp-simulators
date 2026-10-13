package dev.marcinromanowski.pspsim.kernel.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RequestFingerprint {

  public static final String ALGORITHM = "SHA-256";

  private static final char BETWEEN_PARTS = '\n';

  private static final char BETWEEN_NAME_AND_VALUE = '=';

  private RequestFingerprint() {}

  public static String of(String method, String path, Map<String, List<String>> form) {
    StringBuilder compared = new StringBuilder();
    compared.append(method).append(BETWEEN_PARTS).append(path);
    List<String> parameters = new ArrayList<>();
    form.forEach(
        (name, values) -> {
          for (String value : values) {
            parameters.add(name + BETWEEN_NAME_AND_VALUE + value);
          }
        });
    Collections.sort(parameters);
    for (String parameter : parameters) {
      compared.append(BETWEEN_PARTS).append(parameter);
    }
    return digestOf(compared.toString());
  }

  private static String digestOf(String compared) {
    try {
      MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      return HexFormat.of()
          .formatHex(digest.digest(compared.getBytes(StandardCharsets.UTF_8)))
          .toLowerCase(Locale.ROOT);
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(
          ALGORITHM + " is required to compare a repeat against the request it repeats",
          unavailable);
    }
  }
}
