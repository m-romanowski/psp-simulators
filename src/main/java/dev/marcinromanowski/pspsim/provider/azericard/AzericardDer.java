package dev.marcinromanowski.pspsim.provider.azericard;

final class AzericardDer {

  static final byte SEQUENCE = 0x30;

  static final byte OCTET_STRING = 0x04;

  private static final int SHORT_FORM_LIMIT = 0x80;

  private static final int BYTE_MASK = 0xff;

  private static final int BITS_PER_BYTE = 8;

  private AzericardDer() {}

  static byte[] wrapped(byte tag, byte[] content) {
    byte[] length = lengthOf(content.length);
    byte[] wrapped = new byte[1 + length.length + content.length];
    wrapped[0] = tag;
    System.arraycopy(length, 0, wrapped, 1, length.length);
    System.arraycopy(content, 0, wrapped, 1 + length.length, content.length);
    return wrapped;
  }

  static byte[] lengthOf(int length) {
    if (length < SHORT_FORM_LIMIT) {
      return new byte[] {(byte) length};
    }
    int significant = significantBytesOf(length);
    byte[] encoded = new byte[1 + significant];
    encoded[0] = (byte) (SHORT_FORM_LIMIT | significant);
    int remaining = length;
    for (int index = significant; index >= 1; index--) {
      encoded[index] = (byte) (remaining & BYTE_MASK);
      remaining >>>= BITS_PER_BYTE;
    }
    return encoded;
  }

  private static int significantBytesOf(int length) {
    int bits = Integer.SIZE - Integer.numberOfLeadingZeros(length);
    return (bits + BITS_PER_BYTE - 1) / BITS_PER_BYTE;
  }

  static byte[] joined(byte[] first, byte[] second) {
    byte[] joined = new byte[first.length + second.length];
    System.arraycopy(first, 0, joined, 0, first.length);
    System.arraycopy(second, 0, joined, first.length, second.length);
    return joined;
  }
}
