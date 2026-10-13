package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldlineMintedIdentifiersTest {

  @Test
  void one_answer_covering_several_payments_is_split_per_payment_in_the_order_it_arrived() {
    Map<String, List<String>> keyed =
        WorldlineMintedIdentifiers.keyedByPayment(
            List.of(
                new WorldlineMintedIdentifiers.MintedFor("1000_0", "1000_1"),
                new WorldlineMintedIdentifiers.MintedFor("1000_0", "1000_2"),
                new WorldlineMintedIdentifiers.MintedFor("2000_0", "2000_1")));

    assertThat(keyed.keySet()).containsExactly("1000_0", "2000_0");
    assertThat(WorldlineMintedIdentifiers.theOnesMintedFor(keyed, "1000_0"))
        .as(
            "a payment answers to the identifier it was created under and to every identifier a"
                + " maintenance operation has minted since, in the order worldline minted them")
        .containsExactly("1000_1", "1000_2");
    assertThat(WorldlineMintedIdentifiers.theOnesMintedFor(keyed, "2000_0"))
        .containsExactly("2000_1");
  }

  @Test
  void a_payment_nothing_has_been_called_on_names_no_further_identifier() {
    Map<String, List<String>> keyed =
        WorldlineMintedIdentifiers.keyedByPayment(
            List.of(new WorldlineMintedIdentifiers.MintedFor("1000_0", "1000_1")));

    assertThat(WorldlineMintedIdentifiers.theOnesMintedFor(keyed, "2000_0")).isEmpty();
  }

  @Test
  void no_maintenance_at_all_is_no_entry_at_all() {
    assertThat(WorldlineMintedIdentifiers.keyedByPayment(List.of())).isEmpty();
  }
}
