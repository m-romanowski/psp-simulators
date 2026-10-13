package dev.marcinromanowski.pspsim.kernel.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer.ReceivedCallback;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalMerchantServerTest {

  private static final String SIGNATURE = "Stripe-Signature";

  private static final String WHY_THIS_READER_IS_PINNED =
      "Every claim of this suite that a callback a merchant received carried no named header"
          + " reads it through this reader and names the one spelling it was written with. "
          + TheSpellingsOfAHeaderName.ONE_HEADER_ANSWERS_TO_EVERY_SPELLING_OF_ITS_NAME
          + " Those claims are the claim they read as only because this reader answers about the"
          + " header rather than about the spelling a dispatcher put on the wire, which is a"
          + " property of the reader and not of the claims, so it is pinned here: a reader that"
          + " stopped folding the case of a name would leave those absences passing over a"
          + " callback that travelled signed.";

  @Test
  void
      a_callback_header_carried_under_another_spelling_is_still_the_one_this_reader_answers_with() {
    ReceivedCallback received = carrying(Map.of("stripe-signature", List.of("t=1,v1=abc")));

    assertThat(received.headers())
        .as(WHY_THIS_READER_IS_PINNED)
        .containsOnlyKeys("stripe-signature");
    assertThat(received.header(SIGNATURE))
        .as(WHY_THIS_READER_IS_PINNED)
        .containsExactly("t=1,v1=abc");
    assertThat(received.header("STRIPE-SIGNATURE")).containsExactly("t=1,v1=abc");
  }

  @Test
  void a_callback_carrying_no_spelling_of_the_name_reads_as_carrying_no_header_of_that_name() {
    ReceivedCallback received = carrying(Map.of("Content-Type", List.of("application/json")));

    assertThat(received.header(SIGNATURE)).as(WHY_THIS_READER_IS_PINNED).isEmpty();
  }

  private static ReceivedCallback carrying(Map<String, List<String>> headers) {
    return new ReceivedCallback("POST", "application/json", headers, "{}");
  }
}
