package dev.marcinromanowski.pspsim.provider.autra;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class AutraAuthorizerMessage {

  static final String SETTING = "autra.authorizer-rejected-message";

  static final String DEFAULT = "05 - Do not honor";

  static final String SHAPE = "[0-9]{2} - .+";

  private static final Pattern ADMITTED = Pattern.compile(SHAPE);

  private AutraAuthorizerMessage() {}

  static String of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return DEFAULT;
    }
    if (!ADMITTED.matcher(declared).matches()) {
      throw new IllegalArgumentException(
          property
              + " must match "
              + SHAPE
              + ", the grammar every msg provider autra publishes beside AUTHORIZER_REJECTED is"
              + " written in: a two-digit issuer code, a space, a hyphen, a space and the text");
    }
    return declared;
  }
}
