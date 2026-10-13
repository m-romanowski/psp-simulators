package dev.marcinromanowski.pspsim.journey;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

final class AzericardMerchant {

  private final KeyPair merchant;
  private final KeyPair gateway;

  AzericardMerchant() {
    this.merchant = generate();
    this.gateway = generate();
  }

  String merchantPublicKeyPem() {
    return pem("PUBLIC KEY", merchant.getPublic().getEncoded());
  }

  String gatewayPrivateKeyPem() {
    return pem("PRIVATE KEY", gateway.getPrivate().getEncoded());
  }

  static String macSource(String... values) {
    StringBuilder source = new StringBuilder();
    for (String value : values) {
      if (value == null || value.isEmpty()) {
        source.append('-');
      } else {
        source.append(value.length()).append(value);
      }
    }
    return source.toString();
  }

  String sign(String... values) {
    return HexFormat.of()
        .formatHex(signature(merchant.getPrivate(), macSource(values)))
        .toUpperCase(Locale.ROOT);
  }

  boolean verifiesAsTheGateway(String hexSignature, String... values) {
    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(gateway.getPublic());
      verifier.update(macSource(values).getBytes(StandardCharsets.UTF_8));
      return verifier.verify(HexFormat.of().parseHex(hexSignature));
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException(refused);
    }
  }

  static String forJson(String pem) {
    return pem.replace("\n", "\\n");
  }

  private static byte[] signature(PrivateKey key, String source) {
    try {
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(key);
      signer.update(source.getBytes(StandardCharsets.UTF_8));
      return signer.sign();
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException(refused);
    }
  }

  private static KeyPair generate() {
    try {
      KeyPairGenerator keys = KeyPairGenerator.getInstance("RSA");
      keys.initialize(2048);
      return keys.generateKeyPair();
    } catch (GeneralSecurityException refused) {
      throw new IllegalStateException(refused);
    }
  }

  private static String pem(String label, byte[] encoded) {
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
  }

  static String element(String tag, String xml) {
    String open = "<" + tag + ">";
    int start = xml.indexOf(open);
    if (start < 0) {
      return null;
    }
    int end = xml.indexOf("</" + tag + ">", start);
    return xml.substring(start + open.length(), end);
  }
}
