package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AzericardSigningTest {

  private static final byte[] SOURCE = "511.483AZN87777777701".getBytes(UTF_8);

  @Test
  void accepts_the_private_key_both_openssl_major_versions_write() {
    String bySchemeEight =
        AzericardKeyFixture.signedWith(
            SOURCE,
            AzericardKeys.privateKeyOf("apiSecret", AzericardKeyFixture.MERCHANT_PRIVATE_PKCS8));
    String bySchemeOne =
        AzericardKeyFixture.signedWith(
            SOURCE,
            AzericardKeys.privateKeyOf("apiSecret", AzericardKeyFixture.MERCHANT_PRIVATE_PKCS1));

    assertThat(bySchemeOne).isEqualTo(bySchemeEight);
  }

  @Test
  void a_merchant_signature_verifies_against_the_merchant_public_key() {
    String pSign =
        AzericardSignature.sign(
            SOURCE,
            AzericardKeys.privateKeyOf("apiSecret", AzericardKeyFixture.MERCHANT_PRIVATE_PKCS8));

    assertThat(
            AzericardSignature.verifies(
                SOURCE,
                pSign,
                AzericardKeys.publicKeyOf("apiKey", AzericardKeyFixture.MERCHANT_PUBLIC)))
        .isTrue();
  }

  @Test
  void emits_the_mac_in_upper_case_hexadecimal_and_accepts_either_case_back() {
    PrivateKey merchant =
        AzericardKeys.privateKeyOf("apiSecret", AzericardKeyFixture.MERCHANT_PRIVATE_PKCS8);
    PublicKey merchantPublic =
        AzericardKeys.publicKeyOf("apiKey", AzericardKeyFixture.MERCHANT_PUBLIC);
    String pSign = AzericardSignature.sign(SOURCE, merchant);

    assertThat(pSign).isEqualTo(pSign.toUpperCase(Locale.ROOT)).matches("[0-9A-F]{512}");
    assertThat(AzericardSignature.verifies(SOURCE, pSign.toLowerCase(Locale.ROOT), merchantPublic))
        .isTrue();
  }

  @Test
  void a_mac_taken_over_other_values_does_not_verify() {
    String pSign =
        AzericardSignature.sign(
            SOURCE,
            AzericardKeys.privateKeyOf("apiSecret", AzericardKeyFixture.MERCHANT_PRIVATE_PKCS8));

    assertThat(
            AzericardSignature.verifies(
                "511.493AZN87777777701".getBytes(UTF_8),
                pSign,
                AzericardKeys.publicKeyOf("apiKey", AzericardKeyFixture.MERCHANT_PUBLIC)))
        .isFalse();
  }

  @ParameterizedTest(name = "refuses the mac {0}, which is not a hexadecimal string")
  @ValueSource(strings = {"ABC", "zz", "AB CD"})
  void refuses_a_mac_that_is_not_hexadecimal(String pSign) {
    assertThat(
            AzericardSignature.verifies(
                SOURCE,
                pSign,
                AzericardKeys.publicKeyOf("apiKey", AzericardKeyFixture.MERCHANT_PUBLIC)))
        .isFalse();
  }

  @Test
  void a_key_the_platform_cannot_use_verifies_nothing_rather_than_failing_the_call() {
    assertThat(AzericardSignature.verifies(SOURCE, "ABCD", new ForeignKey())).isFalse();
  }

  @Test
  void a_key_the_platform_cannot_use_refuses_to_sign_rather_than_answering_an_empty_mac() {
    ForeignKey foreign = new ForeignKey();

    assertThatThrownBy(() -> AzericardSignature.sign(SOURCE, foreign))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(AzericardSignature.ALGORITHM);
  }

  @ParameterizedTest(name = "refuses a private key given as {0}")
  @ValueSource(
      strings = {
        "",
        "   ",
        "not a pem at all",
        "-----BEGIN PRIVATE KEY-----\nnot base 64 %%%\n-----END PRIVATE KEY-----"
      })
  void refuses_a_private_key_it_cannot_read(String pem) {
    assertThatThrownBy(() -> AzericardKeys.privateKeyOf("accounts[0].apiSecret", pem))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accounts[0].apiSecret")
        .hasMessageContaining(AzericardKeys.PKCS1_PRIVATE);
  }

  @Test
  void refuses_a_private_key_given_as_no_value_at_all() {
    assertThatThrownBy(() -> AzericardKeys.privateKeyOf("accounts[0].apiSecret", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accounts[0].apiSecret");
  }

  @Test
  void refuses_a_public_key_handed_in_where_a_private_key_belongs() {
    assertThatThrownBy(
            () -> AzericardKeys.privateKeyOf("apiSecret", AzericardKeyFixture.MERCHANT_PUBLIC))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(AzericardKeys.PKCS8_PRIVATE);
  }

  @Test
  void refuses_a_private_key_handed_in_where_the_merchant_public_key_belongs() {
    assertThatThrownBy(
            () -> AzericardKeys.publicKeyOf("apiKey", AzericardKeyFixture.MERCHANT_PRIVATE_PKCS8))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(AzericardKeys.X509_PUBLIC);
  }

  @ParameterizedTest(name = "refuses a public key given as {0}")
  @ValueSource(
      strings = {
        "",
        "no pem here",
        "-----BEGIN PUBLIC KEY-----\nnot base 64 %%%\n-----END PUBLIC KEY-----"
      })
  void refuses_a_public_key_it_cannot_read(String pem) {
    assertThatThrownBy(() -> AzericardKeys.publicKeyOf("accounts[1].apiKey", pem))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accounts[1].apiKey");
  }

  @Test
  void refuses_a_public_key_given_as_no_value_at_all() {
    assertThatThrownBy(() -> AzericardKeys.publicKeyOf("accounts[1].apiKey", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accounts[1].apiKey");
  }

  @Test
  void refuses_a_pem_whose_body_is_not_a_key_at_all() {
    assertThatThrownBy(
            () ->
                AzericardKeys.privateKeyOf(
                    "apiSecret", "-----BEGIN PRIVATE KEY-----\nQUJD\n-----END PRIVATE KEY-----"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                AzericardKeys.privateKeyOf(
                    "apiSecret",
                    "-----BEGIN RSA PRIVATE KEY-----\nQUJD\n-----END RSA PRIVATE KEY-----"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                AzericardKeys.publicKeyOf(
                    "apiKey", "-----BEGIN PUBLIC KEY-----\nQUJD\n-----END PUBLIC KEY-----"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static final class ForeignKey implements PrivateKey, PublicKey {

    private static final long serialVersionUID = 1L;

    @Override
    public String getAlgorithm() {
      return "RSA";
    }

    @Override
    public String getFormat() {
      return "RAW";
    }

    @Override
    public byte[] getEncoded() {
      return new byte[] {1, 2, 3};
    }
  }
}
