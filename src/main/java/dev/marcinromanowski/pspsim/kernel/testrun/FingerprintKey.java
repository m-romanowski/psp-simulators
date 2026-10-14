package dev.marcinromanowski.pspsim.kernel.testrun;

import java.util.HexFormat;

public record FingerprintKey(String secret) {

  public static final int HEX_DIGITS = 64;

  public FingerprintKey {
    if (secret.length() != HEX_DIGITS) {
      throw new IllegalArgumentException(
          "the key a test run fingerprints its requests with is "
              + HEX_DIGITS
              + " hexadecimal digits, so a key of any other length was not minted by this"
              + " simulator");
    }
    HexFormat.of().parseHex(secret);
  }

  public byte[] bytes() {
    return HexFormat.of().parseHex(secret);
  }

  @Override
  public String toString() {
    return "FingerprintKey[secret withheld]";
  }
}
