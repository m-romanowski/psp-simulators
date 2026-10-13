package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import java.util.Locale;
import java.util.regex.Pattern;

final class AutraIdentifiers {

  static final int UUID_HEX_DIGITS = 32;

  private static final Pattern A_UUID =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private AutraIdentifiers() {}

  static boolean isAUuid(String value) {
    return A_UUID.matcher(value).matches();
  }

  static String uuidOf(IdGenerator ids) {
    String hex = ids.hex(UUID_HEX_DIGITS).toLowerCase(Locale.ROOT);
    return hex.substring(0, 8)
        + "-"
        + hex.substring(8, 12)
        + "-"
        + hex.substring(12, 16)
        + "-"
        + hex.substring(16, 20)
        + "-"
        + hex.substring(20, 32);
  }
}
