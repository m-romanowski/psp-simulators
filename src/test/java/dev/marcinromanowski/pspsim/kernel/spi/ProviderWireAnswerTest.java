package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProviderWireAnswerTest {

  private static final byte[] A_BODY = "{}".getBytes(StandardCharsets.UTF_8);

  @Test
  void the_lowest_and_the_highest_http_status_are_both_statuses_a_provider_may_answer_with() {
    assertThat(
            new ProviderWireAnswer.InThisProvidersOwnWords(
                    ProviderWireAnswer.LOWEST_HTTP_STATUS, "application/json", A_BODY)
                .httpStatus())
        .isEqualTo(ProviderWireAnswer.LOWEST_HTTP_STATUS);
    assertThat(
            new ProviderWireAnswer.InThisProvidersOwnWords(
                    ProviderWireAnswer.HIGHEST_HTTP_STATUS, "application/json", A_BODY)
                .httpStatus())
        .isEqualTo(ProviderWireAnswer.HIGHEST_HTTP_STATUS);
  }

  @Test
  void a_number_just_outside_the_range_of_http_statuses_is_not_one_a_provider_may_answer_with() {
    assertThatThrownBy(
            () ->
                new ProviderWireAnswer.InThisProvidersOwnWords(
                    ProviderWireAnswer.LOWEST_HTTP_STATUS - 1, "application/json", A_BODY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(String.valueOf(ProviderWireAnswer.LOWEST_HTTP_STATUS - 1));
    assertThatThrownBy(
            () ->
                new ProviderWireAnswer.InThisProvidersOwnWords(
                    ProviderWireAnswer.HIGHEST_HTTP_STATUS + 1, "application/json", A_BODY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(String.valueOf(ProviderWireAnswer.HIGHEST_HTTP_STATUS + 1));
    assertThatThrownBy(
            () -> new ProviderWireAnswer.InThisProvidersOwnWords(0, "application/json", A_BODY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void an_answer_with_no_media_type_on_it_is_refused_because_a_client_would_read_it_as_it_liked() {
    assertThatThrownBy(() -> new ProviderWireAnswer.InThisProvidersOwnWords(404, "", A_BODY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProviderWireAnswer.InThisProvidersOwnWords(404, "  ", A_BODY))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void the_bytes_a_provider_handed_over_are_not_the_bytes_it_can_still_change() {
    byte[] handed = "0".getBytes(StandardCharsets.UTF_8);
    ProviderWireAnswer.InThisProvidersOwnWords answer =
        new ProviderWireAnswer.InThisProvidersOwnWords(404, "application/json", handed);

    handed[0] = '1';
    answer.body()[0] = '2';

    assertThat(answer.body()).isEqualTo("0".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void a_provider_that_publishes_nothing_has_to_say_what_it_publishes_instead() {
    assertThatThrownBy(() -> new ProviderWireAnswer.NothingThisContractPublishes(""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ProviderWireAnswer.NothingThisContractPublishes(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new ProviderWireAnswer.NothingThisContractPublishes("a signed body and nothing else")
                .whatThisContractPublishesInstead())
        .isEqualTo("a signed body and nothing else");
  }
}
