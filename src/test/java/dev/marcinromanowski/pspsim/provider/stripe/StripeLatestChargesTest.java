package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class StripeLatestChargesTest {

  @Test
  void the_charge_an_intent_answers_with_is_the_last_one_the_answer_carried_for_it() {
    Map<String, String> keyed =
        StripeLatestCharges.keyedByIntent(
            List.of(
                new StripeLatestCharges.ChargeOf("pi_1", "ch_first"),
                new StripeLatestCharges.ChargeOf("pi_1", "ch_second"),
                new StripeLatestCharges.ChargeOf("pi_2", "ch_other")));

    assertThat(keyed)
        .as(
            "stripe publishes latest_charge, so an intent charged twice answers with the newest"
                + " charge and never with the one it was charged on first")
        .containsExactly(
            Assertions.entry("pi_1", "ch_second"), Assertions.entry("pi_2", "ch_other"));
  }

  @Test
  void an_intent_the_answer_carried_no_charge_for_names_none() {
    Map<String, String> keyed =
        StripeLatestCharges.keyedByIntent(
            List.of(new StripeLatestCharges.ChargeOf("pi_1", "ch_first")));

    assertThat(keyed.get("pi_2")).isNull();
  }

  @Test
  void no_charge_at_all_is_no_entry_at_all() {
    assertThat(StripeLatestCharges.keyedByIntent(List.of())).isEmpty();
  }
}
