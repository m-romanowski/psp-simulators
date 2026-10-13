package dev.marcinromanowski.pspsim.provider.azericard;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AzericardMacTest {

  @Test
  void prefixes_every_value_with_its_decimal_length_as_the_vendor_publishes() {
    assertThat(
            source(
                "11.48",
                "USD",
                "99999999",
                "1",
                "20030105153021",
                "F2B2DD7E603A7ADA",
                "www.sample.com"))
        .isEqualTo("511.483USD89999999911142003010515302116F2B2DD7E603A7ADA14www.sample.com");
  }

  @Test
  void reproduces_the_published_callback_source() {
    assertThat(source("11.48", "99999999", "168975", "306276834930", "4A29E93C607E33DC"))
        .isEqualTo("511.48899999999616897512306276834930164A29E93C607E33DC");
  }

  @Test
  void puts_a_bare_hyphen_and_no_length_where_a_value_is_absent() {
    assertThat(source("11.48", "99999999", null, null, null)).isEqualTo("511.48899999999---");
  }

  @Test
  void treats_a_value_sent_empty_as_a_value_that_is_absent() {
    assertThat(source("11.48", "99999999", "", "", "")).isEqualTo("511.48899999999---");
  }

  private static String source(String... values) {
    return new String(AzericardMac.sourceOf(values), UTF_8);
  }
}
