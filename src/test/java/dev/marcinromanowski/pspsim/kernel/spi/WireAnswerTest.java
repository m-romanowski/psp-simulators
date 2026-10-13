package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WireAnswerTest {

  @Test
  void the_bytes_an_answer_carries_are_not_the_bytes_its_caller_can_still_change() {
    byte[] handed = "0".getBytes(StandardCharsets.UTF_8);
    WireAnswer answer = new WireAnswer(404, "text/plain", handed);

    handed[0] = '1';
    answer.body()[0] = '2';

    assertThat(answer.body()).isEqualTo("0".getBytes(StandardCharsets.UTF_8));
    assertThat(answer.httpStatus()).isEqualTo(404);
    assertThat(answer.contentType()).isEqualTo("text/plain");
  }

  @Test
  void an_answer_with_nothing_in_it_names_no_media_type_because_nothing_is_read_from_no_bytes() {
    WireAnswer answered = WireAnswer.withNothingInIt(503);

    assertThat(answered.httpStatus()).isEqualTo(503);
    assertThat(answered.body()).isEmpty();
    assertThat(answered.contentType()).isEmpty();
  }

  @Test
  void bytes_handed_over_without_a_media_type_are_refused_rather_than_sent_unlabelled() {
    byte[] carried = "{}".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> new WireAnswer(500, "", carried))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names the media type");
    assertThatThrownBy(() -> new WireAnswer(500, "   ", carried))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
