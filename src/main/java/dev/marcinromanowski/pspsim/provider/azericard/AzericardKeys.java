package dev.marcinromanowski.pspsim.provider.azericard;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

final class AzericardKeys {

  static final String PKCS1_PRIVATE = "RSA PRIVATE KEY";

  static final String PKCS8_PRIVATE = "PRIVATE KEY";

  static final String X509_PUBLIC = "PUBLIC KEY";

  static final String PRIVATE_KEY_SHAPE =
      "an RSA private key in PEM form, labelled either "
          + PKCS8_PRIVATE
          + " or "
          + PKCS1_PRIVATE
          + ", as the two OpenSSL major versions write it";

  static final String PUBLIC_KEY_SHAPE = "an RSA " + X509_PUBLIC + " in PEM form";

  static final String THE_LINE_A_PUBLIC_KEY_BEGINS_WITH = theLineAPemBlockBeginsWith(X509_PUBLIC);

  static final String THE_LINE_A_PRIVATE_KEY_BEGINS_WITH =
      theLineAPemBlockBeginsWith(PKCS8_PRIVATE);

  static final String HOW_A_KEY_PAIR_IS_WRITTEN =
      "Provider azericard publishes no key of its own to paste here and publishes only how to"
          + " write one: \"openssl genrsa -out merchant_name_private_key.pem 2048\" and"
          + " \"openssl rsa -in merchant_name_private_key.pem -pubout -out"
          + " merchant_name_public_key.pem\". Paste the whole block, the BEGIN and END lines"
          + " included; a key is 2048 bits of base64, so there is no short value this box could"
          + " show you.";

  static final List<String> THE_LINES_A_PUBLIC_KEY_MAY_END_WITH =
      List.of(theLineAPemBlockEndsWith(X509_PUBLIC));

  static final List<String> THE_LINES_A_PRIVATE_KEY_MAY_END_WITH =
      List.of(theLineAPemBlockEndsWith(PKCS8_PRIVATE), theLineAPemBlockEndsWith(PKCS1_PRIVATE));

  private static String theLineAPemBlockBeginsWith(String label) {
    return "-----BEGIN " + label + "-----";
  }

  private static String theLineAPemBlockEndsWith(String label) {
    return "-----END " + label + "-----";
  }

  private static final String RSA = "RSA";

  private static final Pattern PEM =
      Pattern.compile("-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END \\1-----", Pattern.DOTALL);

  private AzericardKeys() {}

  static PrivateKey privateKeyOf(String property, @Nullable String pem) {
    Matcher block = blockOf(property, pem, PRIVATE_KEY_SHAPE);
    byte[] der = derOf(property, String.valueOf(block.group(2)), PRIVATE_KEY_SHAPE);
    byte[] pkcs8 =
        switch (String.valueOf(block.group(1))) {
          case PKCS8_PRIVATE -> der;
          case PKCS1_PRIVATE -> pkcs8Of(der);
          default -> throw unusable(property, PRIVATE_KEY_SHAPE);
        };
    try {
      return KeyFactory.getInstance(RSA).generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    } catch (GeneralSecurityException unreadable) {
      throw unusable(property, PRIVATE_KEY_SHAPE);
    }
  }

  static PublicKey publicKeyOf(String property, @Nullable String pem) {
    Matcher block = blockOf(property, pem, PUBLIC_KEY_SHAPE);
    if (!X509_PUBLIC.equals(block.group(1))) {
      throw unusable(property, PUBLIC_KEY_SHAPE);
    }
    byte[] der = derOf(property, String.valueOf(block.group(2)), PUBLIC_KEY_SHAPE);
    try {
      return KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(der));
    } catch (GeneralSecurityException unreadable) {
      throw unusable(property, PUBLIC_KEY_SHAPE);
    }
  }

  private static Matcher blockOf(String property, @Nullable String pem, String shape) {
    if (pem == null || pem.isBlank()) {
      throw unusable(property, shape);
    }
    Matcher block = PEM.matcher(pem);
    if (!block.find()) {
      throw unusable(property, shape);
    }
    return block;
  }

  private static byte[] derOf(String property, String base64, String shape) {
    try {
      return Base64.getMimeDecoder().decode(base64.strip());
    } catch (IllegalArgumentException notBase64) {
      throw unusable(property, shape);
    }
  }

  private static byte[] pkcs8Of(byte[] pkcs1) {
    byte[] preamble = {
      0x02,
      0x01,
      0x00,
      0x30,
      0x0d,
      0x06,
      0x09,
      0x2a,
      (byte) 0x86,
      0x48,
      (byte) 0x86,
      (byte) 0xf7,
      0x0d,
      0x01,
      0x01,
      0x01,
      0x05,
      0x00
    };
    return AzericardDer.wrapped(
        AzericardDer.SEQUENCE,
        AzericardDer.joined(preamble, AzericardDer.wrapped(AzericardDer.OCTET_STRING, pkcs1)));
  }

  private static IllegalArgumentException unusable(String property, String shape) {
    return new IllegalArgumentException(property + " must hold " + shape);
  }
}
