package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AzericardDerTest {

  @Test
  void encodes_a_length_a_single_byte_can_carry_as_that_byte_alone() {
    assertThat(AzericardDer.lengthOf(0)).containsExactly(0x00);
    assertThat(AzericardDer.lengthOf(0x7f)).containsExactly(0x7f);
  }

  @Test
  void encodes_a_longer_length_as_a_count_of_bytes_followed_by_them() {
    assertThat(AzericardDer.lengthOf(0x80)).containsExactly(0x81, 0x80);
    assertThat(AzericardDer.lengthOf(1190)).containsExactly(0x82, 0x04, 0xa6);
  }

  @Test
  void wraps_content_in_its_tag_and_its_length() {
    assertThat(AzericardDer.wrapped(AzericardDer.OCTET_STRING, new byte[] {1, 2, 3}))
        .containsExactly(0x04, 0x03, 1, 2, 3);
  }

  @Test
  void a_key_body_too_long_for_a_short_length_is_still_wrapped_whole() {
    byte[] body = new byte[300];
    body[299] = 7;

    byte[] wrapped = AzericardDer.wrapped(AzericardDer.SEQUENCE, body);

    assertThat(wrapped).hasSize(304);
    assertThat(wrapped[0]).isEqualTo(AzericardDer.SEQUENCE);
    assertThat(wrapped[1]).isEqualTo((byte) 0x82);
    assertThat(wrapped[303]).isEqualTo((byte) 7);
  }

  @Test
  void joins_two_pieces_of_a_key_in_the_order_they_were_given() {
    assertThat(AzericardDer.joined(new byte[] {1, 2}, new byte[] {3})).containsExactly(1, 2, 3);
  }
}
