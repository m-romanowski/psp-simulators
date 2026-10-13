package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StripeClientSecretTest {

  private static final String A_PAYMENT_INTENT = "pi_sim_0123456789abcdef";
  private static final String A_SETUP_INTENT = "seti_sim_0123456789abcdef";

  private final StripeDoubles.CountingIds ids = new StripeDoubles.CountingIds();

  @Test
  void a_client_secret_is_the_id_it_unlocks_then_the_published_infix_then_thirty_two_characters() {
    assertThat(StripeClientSecret.of(A_PAYMENT_INTENT, ids))
        .as("the format provider stripe publishes, pinned by the bytes it puts on the wire")
        .isEqualTo("pi_sim_0123456789abcdef_secret_00000000000000000000000000000001");
  }

  @Test
  void a_setup_intent_is_handed_the_one_format_a_payment_intent_is_handed_and_not_a_second_one() {
    StripeClientSecret.of(A_PAYMENT_INTENT, ids);

    assertThat(StripeClientSecret.of(A_SETUP_INTENT, ids))
        .as("one declaration serves both objects, and each secret draws a tail of its own")
        .isEqualTo("seti_sim_0123456789abcdef_secret_00000000000000000000000000000002");
  }
}
