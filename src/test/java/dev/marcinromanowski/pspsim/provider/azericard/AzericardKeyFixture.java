package dev.marcinromanowski.pspsim.provider.azericard;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

final class AzericardKeyFixture {

  static final Map<String, String> THE_PUBLISHED_TEST_CARD_SENT_S2S =
      Map.of(
          AzericardFields.CARD,
          "4127208104942601",
          AzericardFields.EXP,
          "05",
          AzericardFields.EXP_YEAR,
          "31",
          AzericardFields.CVC2,
          "796");

  private AzericardKeyFixture() {}

  static final String MERCHANT_PRIVATE_PKCS8 =
      """
          -----BEGIN PRIVATE KEY-----
          MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCrIhZuphuj2pFl
          B4nBsV7s98VLSkrL14kZDyeBPHRhrEfIC5ZwSHjhKrmpflbTmsb/l/brvXTzQW/R
          +W4Fn2/DEhiufR8ajO8EDlhr8t7m2jECMLlGhnrXzHe1eOzNjAo1VPFZYW1/F5DT
          ZyYELj+trXEQ0jiljkd96evfURkeIyKr2cnZ8p5LLux+FISiE+q0TwFyiUQ8asIJ
          Z/4N4Rao0HgbgdUgXhVJUNRa9ERXUZOp9qhi/y9SvUBwVnvsm1cbEISR7jVVvqG9
          ve1S1lW0u3pfIAaGzsYY3+dXJvBPkOcsmn9LbjKQCatSQLA9pTNkzMHRL4lI1Egl
          cwE2B8C/AgMBAAECggEABxWxr7dWae7+BEc5ckM0EVkOmhfYxzNEnJIj32ClLCKp
          lBllYH+iQCbN1JEDlN4RrU+EFu1bKUjvvAB4sr3k2W1nMoNr5WRT7tXjM5DfSS0G
          SLduAYKcFPwE3AabaG+1lSX6RJTZJ3ZOdhoNQ7C1zSxi2RXtkhkKUlGmr1zto8bc
          1zs3Qva3XVfYAIBEdkDfW++gCdt8sO1p3K+Qt4a1ZAO20KsOsAVFFrzgPGEhdtjG
          J7AGYng188IUhGIKoUM7vC7mAPywbDvgzRsx4/ghgRf+D6lqQbpMTLiS1GVf9L4Y
          /iZC39xXM4igA/OowlqBat7wVbj48IrW7UALNRSCZQKBgQDoW0EaykSmsW2Z76Ok
          UIjVEmjr8AxpScn7GfMvPad8G0ui26cdGgf00jKMqXMbBw0L+JTySowrlRH/Mr5S
          nUW5nkqblJUOzrGKY38d0XKkAMLNQ6ppKkQAYq66DM7TwRy045wZzsKMKhXm6tYO
          aS2Jzkc8wNMO/BRF244lLrlRYwKBgQC8jADKB51EUjLspvqrRgQljJuHzmzTfHBc
          jouOeHdp9G8pyV9lKIM30T79/nafxynf6wv/vWdafLvyBIEXJj158HR+7hd89rUO
          E2as/NGuycyRBa50kjF6jm79fKW7dFQL5BK6RNbj2cn6qpioRrzIfC3AIWUMeDSa
          USl9pXy/9QKBgQCtDyqSdYxkS0pCmQXc4E7JxAh5kjTsRpU1fMJvsRAJk5rmFYuG
          VVkPs28K7TpaKwpAnEjznSqBW2ooFPdEOPcxpQAUmLncaX46gJiw8Mrd8+MjVv6Z
          jW1QjC9vSxLRDMgAwnYFksW796yQbOA5o4TTWtwcF9IbGw1OapSYhRBp2QKBgHGM
          Lv18UG3QwvJaLSPwapbictf6rPdAPbq/7bPDQMcCed/EcjrNhxiaX0E/edkHbGR5
          fSRqwE9yRw2dQNDcKrHA6ad3Y5XSTX9Il9EeAHku9o9vyHxTfpC8LoNiCb02dVcj
          51NeGbvmcQkQrBbLYu2qOzM16VD1I1i8ACy2+MXxAoGAJMI0++Ipiy3U5mMZwfVh
          U08fqduiIRF5Q6ukm7js8x2RKmrDS/xixFDlgq7Caj9Wa3HfqhBlhx97nxZCAxN6
          00HoTJwd0ksm/rUOtekSzYVeOKKS7tM+vzE6+Ipz0MeyhDJIxXKUMAEvBP1Af588
          vOa7yPu1s3wSFakqDZk45HU=
          -----END PRIVATE KEY-----
      """;

  static final String MERCHANT_PRIVATE_PKCS1 =
      """
          -----BEGIN RSA PRIVATE KEY-----
          MIIEowIBAAKCAQEAqyIWbqYbo9qRZQeJwbFe7PfFS0pKy9eJGQ8ngTx0YaxHyAuW
          cEh44Sq5qX5W05rG/5f2671080Fv0fluBZ9vwxIYrn0fGozvBA5Ya/Le5toxAjC5
          RoZ618x3tXjszYwKNVTxWWFtfxeQ02cmBC4/ra1xENI4pY5Hfenr31EZHiMiq9nJ
          2fKeSy7sfhSEohPqtE8BcolEPGrCCWf+DeEWqNB4G4HVIF4VSVDUWvREV1GTqfao
          Yv8vUr1AcFZ77JtXGxCEke41Vb6hvb3tUtZVtLt6XyAGhs7GGN/nVybwT5DnLJp/
          S24ykAmrUkCwPaUzZMzB0S+JSNRIJXMBNgfAvwIDAQABAoIBAAcVsa+3Vmnu/gRH
          OXJDNBFZDpoX2MczRJySI99gpSwiqZQZZWB/okAmzdSRA5TeEa1PhBbtWylI77wA
          eLK95NltZzKDa+VkU+7V4zOQ30ktBki3bgGCnBT8BNwGm2hvtZUl+kSU2Sd2TnYa
          DUOwtc0sYtkV7ZIZClJRpq9c7aPG3Nc7N0L2t11X2ACARHZA31vvoAnbfLDtadyv
          kLeGtWQDttCrDrAFRRa84DxhIXbYxiewBmJ4NfPCFIRiCqFDO7wu5gD8sGw74M0b
          MeP4IYEX/g+pakG6TEy4ktRlX/S+GP4mQt/cVzOIoAPzqMJagWre8FW4+PCK1u1A
          CzUUgmUCgYEA6FtBGspEprFtme+jpFCI1RJo6/AMaUnJ+xnzLz2nfBtLotunHRoH
          9NIyjKlzGwcNC/iU8kqMK5UR/zK+Up1FuZ5Km5SVDs6ximN/HdFypADCzUOqaSpE
          AGKuugzO08EctOOcGc7CjCoV5urWDmktic5HPMDTDvwURduOJS65UWMCgYEAvIwA
          ygedRFIy7Kb6q0YEJYybh85s03xwXI6Ljnh3afRvKclfZSiDN9E+/f52n8cp3+sL
          /71nWny78gSBFyY9efB0fu4XfPa1DhNmrPzRrsnMkQWudJIxeo5u/Xylu3RUC+QS
          ukTW49nJ+qqYqEa8yHwtwCFlDHg0mlEpfaV8v/UCgYEArQ8qknWMZEtKQpkF3OBO
          ycQIeZI07EaVNXzCb7EQCZOa5hWLhlVZD7NvCu06WisKQJxI850qgVtqKBT3RDj3
          MaUAFJi53Gl+OoCYsPDK3fPjI1b+mY1tUIwvb0sS0QzIAMJ2BZLFu/eskGzgOaOE
          01rcHBfSGxsNTmqUmIUQadkCgYBxjC79fFBt0MLyWi0j8GqW4nLX+qz3QD26v+2z
          w0DHAnnfxHI6zYcYml9BP3nZB2xkeX0kasBPckcNnUDQ3CqxwOmnd2OV0k1/SJfR
          HgB5LvaPb8h8U36QvC6DYgm9NnVXI+dTXhm75nEJEKwWy2LtqjszNelQ9SNYvAAs
          tvjF8QKBgCTCNPviKYst1OZjGcH1YVNPH6nboiEReUOrpJu47PMdkSpqw0v8YsRQ
          5YKuwmo/Vmtx36oQZYcfe58WQgMTetNB6EycHdJLJv61DrXpEs2FXjiiku7TPr8x
          OviKc9DHsoQySMVylDABLwT9QH+fPLzmu8j7tbN8EhWpKg2ZOOR1
          -----END RSA PRIVATE KEY-----
      """;

  static final String MERCHANT_PUBLIC =
      """
          -----BEGIN PUBLIC KEY-----
          MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqyIWbqYbo9qRZQeJwbFe
          7PfFS0pKy9eJGQ8ngTx0YaxHyAuWcEh44Sq5qX5W05rG/5f2671080Fv0fluBZ9v
          wxIYrn0fGozvBA5Ya/Le5toxAjC5RoZ618x3tXjszYwKNVTxWWFtfxeQ02cmBC4/
          ra1xENI4pY5Hfenr31EZHiMiq9nJ2fKeSy7sfhSEohPqtE8BcolEPGrCCWf+DeEW
          qNB4G4HVIF4VSVDUWvREV1GTqfaoYv8vUr1AcFZ77JtXGxCEke41Vb6hvb3tUtZV
          tLt6XyAGhs7GGN/nVybwT5DnLJp/S24ykAmrUkCwPaUzZMzB0S+JSNRIJXMBNgfA
          vwIDAQAB
          -----END PUBLIC KEY-----
      """;

  static final String GATEWAY_PRIVATE_PKCS8 =
      """
          -----BEGIN PRIVATE KEY-----
          MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDmwMCi5vYpJ0f+
          wRYnc/DncL9anYPkTZyhfRCqZvDOdz+whr5Q8Yla6+/NScY8uQo1mhq96BM5lH/v
          xQsQsZc6kqJodap/GOpdf3MLS21D5yGDt8IK+RPyIP0cHTuLVXHzJoIIsqB3R0Mv
          tZvh7992nbcUDE+xKlKNuPnseGwJU+CLbDYN6fvAs84pEsVMta6Tw9FExjYosnmy
          6hckQR0rWRSN+pgsOcqFhxsK9WaSNInsy9LilOjY1K+KMMqXyzhjA6NiAfCVqAPz
          YTwS1xKn4j7ZLedE7bcawUtfqYO0URqd6TfKkgiN7lC6h1reP968LE2fH7M/56mw
          SBS3LJlVAgMBAAECggEAEcOcgN35bN0gioDoWPSwwsx3+ryeEvUTng3/+rJOjwJ4
          pXkWDSqvCZAYx6vKhlGzcsFwlhaX5kqjl7yKV8hoLULtHFAUplCveC+v1lqB6KSS
          PK7okgovSihWe/PXKeiTy2V3RL6lolEa38EiJUBfamUFW1aASxIIRW9MVDNIE+aa
          +j2RzER7ZmP942FGFyBbnCc5AYRpUiAaArxZrbVzoHW+Y/hQztLYSoS2wWjSGqzo
          TWv5C0fwUI+ZTE7CqtAEJNlbNAht/yOIJSN1XOxUuc+TP1Mg1lfHLO1RdVoR8ISu
          ezOdkSJg+YmRz1xMwZXakWavLIKmmV1Fo1ruY5MkQQKBgQD0rQpbaqrBdbsNj1/Z
          LUOqvzDNqmcETvNMxRJPvOFwME0nGbQJ0PU5KdFDDXVdv4Qla0lCm+rADiyDSus3
          Hdw7VStlpfYu6GyuGs99T5x6s4BZ+kszuW7oamMd/UiWs/z3xz0cjBQYLoJ5S4li
          NqnYOm9xZmgd44odWva4TCNqQQKBgQDxbsAG6tP6gWWNRRfVqZO+34E1axXrqVgQ
          NJHmh8Cf1KEzrmoCggAtE3SGwrnKG4ofWtggga1Wpg1Esj9ZUtqUiO3d5AxdKxsG
          QBnEuwy94EyUdtkNaIdx7+ILtYqU9iYju4IkkVd8DcBvJu9AJDzndAMh53byYW7w
          gHgnE5hiFQKBgQDEXaADUC8sxoTR1DBzhw1YmsrbNJgq8AX5pewVTJyNQmaZK9q/
          ShGUyAfnTYTVMWAn+9u29pVHf9ifol/2QyO+BpOmp7RVXznh3SiLBHqsSyWGkQcL
          wxXMt51Gs0EvmdI7NY7JO27hHWqfWE0xInfbTsKj+zEyU6xD9P4vWQPPQQKBgFcS
          rkbQYmsyR8HPbW0tn49zZFIzsNAxXwVZMFWIGDyMnybYfHszD4UyQxBnIkJ19Dai
          uNfgbPeXI6nm71nzu8leKQuht6PSbAixDZoaK9o/Dt9sjZN8Y30AGvJGIxjePZsd
          gS5XDMo82247CIaFi0GeYZLUu91mrnAmpS7Srl6RAoGBAO+8SUkATDxmAyY0Pi+s
          Bp9dRmiI7Sn0lTcOlbLGVTtVfii0rWlBUb8SfUzhcPxwOZySqF6JPWk1qYUwOu/h
          lz3c1S+KHVGg5+/pBfay/Hv9rw7IUoM3jc15Xb5JxO8NFmZ94KKLsZMwYmCyXTbe
          1ZkBrKL9BXS03SDjcmgudUjQ
          -----END PRIVATE KEY-----
      """;

  static final String GATEWAY_PUBLIC =
      """
          -----BEGIN PUBLIC KEY-----
          MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5sDAoub2KSdH/sEWJ3Pw
          53C/Wp2D5E2coX0Qqmbwznc/sIa+UPGJWuvvzUnGPLkKNZoavegTOZR/78ULELGX
          OpKiaHWqfxjqXX9zC0ttQ+chg7fCCvkT8iD9HB07i1Vx8yaCCLKgd0dDL7Wb4e/f
          dp23FAxPsSpSjbj57HhsCVPgi2w2Den7wLPOKRLFTLWuk8PRRMY2KLJ5suoXJEEd
          K1kUjfqYLDnKhYcbCvVmkjSJ7MvS4pTo2NSvijDKl8s4YwOjYgHwlagD82E8EtcS
          p+I+2S3nRO23GsFLX6mDtFEanek3ypIIje5Quoda3j/evCxNnx+zP+epsEgUtyyZ
          VQIDAQAB
          -----END PUBLIC KEY-----
      """;

  static String signedByTheMerchant(byte[] macSource) {
    return signedWith(macSource, privateKeyOf(MERCHANT_PRIVATE_PKCS8));
  }

  static String signedWith(byte[] macSource, PrivateKey key) {
    try {
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(key);
      signature.update(macSource);
      return AzericardSignature.hex(signature.sign());
    } catch (GeneralSecurityException unusable) {
      throw new IllegalStateException(unusable);
    }
  }

  static boolean verifiedByTheGateway(byte[] macSource, String pSign) {
    return AzericardSignature.verifies(macSource, pSign, publicKeyOf(GATEWAY_PUBLIC));
  }

  static PrivateKey privateKeyOf(String pem) {
    try {
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(derOf(pem)));
    } catch (GeneralSecurityException unusable) {
      throw new IllegalStateException(unusable);
    }
  }

  static PublicKey publicKeyOf(String pem) {
    try {
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(derOf(pem)));
    } catch (GeneralSecurityException unusable) {
      throw new IllegalStateException(unusable);
    }
  }

  private static byte[] derOf(String pem) {
    String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64);
  }
}
