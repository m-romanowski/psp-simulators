package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.AShapeSaidInWords;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class AzericardDuplicateCode {

  static final String SETTING = "azericard.duplicate-response-code";

  static final String DEFAULT = "-1";

  private static final String A_SIGN_THE_GATEWAY_WAS_OBSERVED_PUTTING_IN_FRONT = "-?";

  private static final String THE_CODE_ITSELF = "[0-9A-Za-z]{1,2}";

  static final String SHAPE = A_SIGN_THE_GATEWAY_WAS_OBSERVED_PUTTING_IN_FRONT + THE_CODE_ITSELF;

  private static final AShapeSaidInWords THE_CODE_IN_WORDS =
      AShapeSaidInWords.of(THE_CODE_ITSELF, "05");

  private static final String A_CODE_IS_SIZED_THE_WAY_RC_IS =
      ", which is a response code of "
          + THE_CODE_IN_WORDS.theLengthAndCharactersInWords()
          + ", optionally signed with a leading hyphen that count does not include, for example "
          + DEFAULT
          + ". Its width is the size provider azericard publishes RC at, and the sign is admitted"
          + " because every code the gateway was observed answering a refused message with"
          + " carries one";

  private static final Pattern ADMITTED = Pattern.compile(SHAPE);

  private AzericardDuplicateCode() {}

  static String of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return DEFAULT;
    }
    if (!ADMITTED.matcher(declared).matches()) {
      throw new IllegalArgumentException(
          property + " must match " + SHAPE + A_CODE_IS_SIZED_THE_WAY_RC_IS);
    }
    return declared;
  }
}
