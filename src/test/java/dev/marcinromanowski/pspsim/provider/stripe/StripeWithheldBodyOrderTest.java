package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class StripeWithheldBodyOrderTest {

  private static final int THE_FIELDS_A_BODY_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_DECLARES = 12;

  private static Map<String, List<String>> aBodyOfThatWidth() {
    Map<String, List<String>> declared = new LinkedHashMap<>();
    for (int at = 1; at <= THE_FIELDS_A_BODY_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_DECLARES; at++) {
      declared.put("field_" + at, List.of("the value the response wrote " + at + "th"));
    }
    return declared;
  }

  @Test
  void
      a_withheld_body_hands_its_fields_on_in_the_order_the_response_declared_them_and_a_body_of_twelve_has_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, List<String>> declared = aBodyOfThatWidth();

    Map<String, List<String>> withheld =
        StripeFields.withholding(declared, StripeFields.SENSITIVE_RESPONSE_FIELDS);

    assertThat(withheld.keySet())
        .as(
            "a tester reads the trail row field by field and tells what the simulator answered"
                + " from the order those fields are in, so the withheld body has to come back in"
                + " the order the response declared it. Twelve fields leave 479001600 orders an"
                + " unordered copy could draw from the keys' hashes and a per-JVM salt, so this"
                + " assertion cannot pass by coincidence on a build that copies them unordered")
        .containsExactlyElementsOf(declared.keySet());
  }

  @Test
  void
      a_withheld_body_is_handed_on_in_a_map_that_carries_an_order_rather_than_one_drawn_from_a_per_jvm_salt() {
    Map<String, List<String>> asTheAuditRowKeepsIt =
        StripeFields.withholding(aBodyOfThatWidth(), StripeFields.SENSITIVE_RESPONSE_FIELDS);

    assertThat(asTheAuditRowKeepsIt)
        .as(
            "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
                + " entries are in such a map there is no way left to tell it from an ordered"
                + " one, so the audit row is handed a map whose type carries an order on every"
                + " run rather than one that happens to have printed in the right order on this"
                + " one")
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void a_withheld_body_a_caller_is_handed_cannot_be_written_to() {
    Map<String, List<String>> withheld =
        StripeFields.withholding(aBodyOfThatWidth(), StripeFields.SENSITIVE_RESPONSE_FIELDS);

    assertThatThrownBy(() -> withheld.put("field_13", List.of("a field no response declared")))
        .as(
            "the audit row keeps this map for as long as the trail keeps the row, so the order"
                + " and the fields a reader sees are the ones the response declared and not ones"
                + " a later caller added")
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void
      withholding_a_body_redacts_every_sensitive_leaf_however_deep_its_path_and_leaves_every_other_field_the_values_the_response_wrote() {
    Map<String, List<String>> declared = new LinkedHashMap<>();
    declared.put("id", List.of("pi_sim_0000000000000001"));
    declared.put("error.payment_intent.client_secret", List.of("pi_sim_1_secret_2"));
    declared.put("error.payment_intent.id", List.of("pi_sim_0000000000000001"));
    declared.put("payment_method_types", List.of("card", "link"));
    declared.put(
        StripeFields.SECRET,
        List.of(
            "ek_sim_00000000000000000000000000000001", "ek_sim_00000000000000000000000000000002"));

    Map<String, List<String>> withheld =
        StripeFields.withholding(declared, StripeFields.SENSITIVE_RESPONSE_FIELDS);

    assertThat(withheld)
        .as(
            "withholding a body in the order the response declared withholds exactly what"
                + " withholding it unordered withheld: a leaf this provider calls sensitive is"
                + " replaced by one marker per value it carried, however deep the path to it,"
                + " and every other field keeps its name and all of its values. This claim is"
                + " about which fields and values come back and not about their order, which the"
                + " two assertions above are the ones to fail on")
        .containsOnly(
            Map.entry("id", List.of("pi_sim_0000000000000001")),
            Map.entry("error.payment_intent.client_secret", List.of(Redaction.REDACTED)),
            Map.entry("error.payment_intent.id", List.of("pi_sim_0000000000000001")),
            Map.entry("payment_method_types", List.of("card", "link")),
            Map.entry(StripeFields.SECRET, List.of(Redaction.REDACTED, Redaction.REDACTED)));
  }
}
