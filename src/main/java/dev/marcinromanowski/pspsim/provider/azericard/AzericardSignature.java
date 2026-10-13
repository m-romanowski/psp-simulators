package dev.marcinromanowski.pspsim.provider.azericard;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

final class AzericardSignature {

  static final String ALGORITHM = "SHA256withRSA";

  private static final char[] UPPER_CASE_HEX = "0123456789ABCDEF".toCharArray();

  private static final int HEX_RADIX = 16;

  private static final int HIGH_NIBBLE = 4;

  private static final int LOW_NIBBLE_MASK = 0x0f;

  private static final int BYTE_MASK = 0xff;

  private AzericardSignature() {}

  static String sign(byte[] source, PrivateKey key) {
    try {
      Signature signature = Signature.getInstance(ALGORITHM);
      signature.initSign(key);
      signature.update(source);
      return hex(signature.sign());
    } catch (GeneralSecurityException unusable) {
      throw new IllegalStateException(
          "the gateway key configured for this terminal cannot produce a " + ALGORITHM + " MAC");
    }
  }

  static boolean verifies(byte[] source, String pSign, PublicKey key) {
    byte[] claimed = bytesOf(pSign);
    if (claimed.length == 0) {
      return false;
    }
    try {
      Signature signature = Signature.getInstance(ALGORITHM);
      signature.initVerify(key);
      signature.update(source);
      return signature.verify(claimed);
    } catch (GeneralSecurityException unreadable) {
      return false;
    }
  }

  static String hex(byte[] bytes) {
    StringBuilder hex = new StringBuilder();
    for (byte value : bytes) {
      hex.append(UPPER_CASE_HEX[(value & BYTE_MASK) >>> HIGH_NIBBLE]);
      hex.append(UPPER_CASE_HEX[value & LOW_NIBBLE_MASK]);
    }
    return hex.toString();
  }

  private static byte[] bytesOf(String hex) {
    if (hex.length() % 2 != 0) {
      return new byte[0];
    }
    byte[] decoded = new byte[hex.length() / 2];
    for (int index = 0; index < decoded.length; index++) {
      int high = Character.digit(hex.charAt(index * 2), HEX_RADIX);
      int low = Character.digit(hex.charAt(index * 2 + 1), HEX_RADIX);
      if (high < 0 || low < 0) {
        return new byte[0];
      }
      decoded[index] = (byte) ((high << HIGH_NIBBLE) | low);
    }
    return decoded;
  }
}
