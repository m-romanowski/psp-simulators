package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import org.junit.jupiter.api.Test;

class AutraStoryKeyTest {

  @Test
  void two_payments_of_one_run_stand_on_two_story_keys_and_never_share_one() {
    assertThat(AutraStoryKey.ofPayment("550e8400-e29b-41d4-a716-446655440000"))
        .isNotEqualTo(AutraStoryKey.ofPayment("550e8400-e29b-41d4-a716-446655440001"))
        .endsWith("550e8400-e29b-41d4-a716-446655440000");
  }

  @Test
  void two_merchants_of_one_run_open_their_acquiring_sessions_on_two_story_keys() {
    assertThat(AutraStoryKey.ofSession("25055662000190"))
        .isNotEqualTo(AutraStoryKey.ofSession("11111111111"))
        .endsWith("25055662000190");
  }

  @Test
  void a_payment_and_a_session_that_read_the_same_are_still_two_keys_of_two_spaces() {
    assertThat(AutraStoryKey.ofPayment("25055662000190"))
        .isNotEqualTo(AutraStoryKey.ofSession("25055662000190"));
  }

  @Test
  void
      the_longest_session_key_a_configured_document_could_spell_is_one_a_story_can_be_stored_under() {
    assertThatCode(
            () ->
                StoryKey.rejectWhatNoStoryKeyCanCarry(
                    AutraMerchant.DOCUMENT_ID_PROPERTY,
                    AutraStoryKey.theLongestSessionKeyOf(
                        "1".repeat(AutraMerchant.LONGEST_DOCUMENT_THE_STORE_HOLDS))))
        .doesNotThrowAnyException();
  }
}
