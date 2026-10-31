package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StripeFormTest {

  @Test
  void reads_a_value_sent_under_a_plain_name_and_a_blank_one_as_no_value() {
    StripeForm form = formOf(pair("amount", "1099"), pair("customer", ""), pair("email", " "));

    assertThat(form.first("amount")).isEqualTo("1099");
    assertThat(form.first("customer")).isNull();
    assertThat(form.first("email")).isNull();
    assertThat(form.first("currency")).isNull();
    assertThat(form.unreadable()).isNull();
  }

  @Test
  void reads_a_list_appended_with_empty_brackets_in_the_order_the_client_sent_it() {
    StripeForm form = formOf(pair("expand[]", "customer", "payment_method"));

    assertThat(form.listOf("expand")).containsExactly("customer", "payment_method");
  }

  @Test
  void reads_a_list_numbered_by_index_in_the_order_its_indices_give_rather_than_the_order_sent() {
    StripeForm form = formOf(pair("expand[1]", "payment_method"), pair("expand[0]", "customer"));

    assertThat(form.listOf("expand")).containsExactly("customer", "payment_method");
  }

  @Test
  void orders_an_indexed_list_of_more_than_ten_elements_by_number_rather_than_by_spelling() {
    List<Sent> pairs = new ArrayList<>();
    for (int index = 11; index >= 0; index--) {
      pairs.add(pair("expand[" + index + "]", "e" + index));
    }

    assertThat(formOf(pairs).listOf("expand"))
        .containsExactly("e0", "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "e10", "e11");
  }

  @Test
  void reads_the_same_list_out_of_both_encodings_a_client_can_send() {
    StripeForm appended = formOf(pair("expand[]", "customer", "payment_method"));
    StripeForm indexed = formOf(pair("expand[0]", "customer"), pair("expand[1]", "payment_method"));

    assertThat(indexed.listOf("expand")).isEqualTo(appended.listOf("expand"));
  }

  @Test
  void reads_an_empty_value_as_a_list_of_no_elements_the_way_an_sdk_sends_an_empty_list() {
    assertThat(formOf(pair("expand", "")).listOf("expand")).isEmpty();
    assertThat(formOf(pair("amount", "1099")).listOf("expand")).isEmpty();
  }

  @Test
  void reads_no_list_out_of_a_value_a_hash_a_gap_or_an_index_spelled_two_ways() {
    assertThat(formOf(pair("expand", "customer")).listOf("expand")).isNull();
    assertThat(formOf(pair("expand[customer]", "true")).listOf("expand")).isNull();
    assertThat(
            formOf(pair("expand[0]", "customer"), pair("expand[2]", "payment_method"))
                .listOf("expand"))
        .isNull();
    assertThat(formOf(pair("expand[01]", "customer")).listOf("expand")).isNull();
    assertThat(formOf(pair("expand[1]", "customer")).listOf("expand")).isNull();
  }

  @Test
  void reads_no_list_of_values_out_of_a_list_whose_elements_are_hashes() {
    assertThat(formOf(pair("expand[0][path]", "customer")).listOf("expand")).isNull();
  }

  @Test
  void decodes_a_hash_keeping_its_keys_in_the_order_the_client_sent_them() {
    StripeForm form =
        formOf(pair("metadata[order_id]", "order-99"), pair("metadata[cart_id]", "7"));

    assertThat(form.parameters())
        .containsExactly(
            Map.entry(
                "metadata",
                new StripeParameter.Hashed(
                    sequenced(
                        List.of(
                            Map.entry("order_id", new StripeParameter.Scalar("order-99")),
                            Map.entry("cart_id", new StripeParameter.Scalar("7")))))));
    assertThat(form.parameters().get("metadata"))
        .isInstanceOfSatisfying(
            StripeParameter.Hashed.class,
            hash -> assertThat(hash.entries().keySet()).containsExactly("order_id", "cart_id"));
    assertThat(form.first("metadata")).isNull();
    assertThat(form.carries("metadata")).isTrue();
    assertThat(form.carries("metadata[order_id]")).isFalse();
  }

  @Test
  void decodes_a_hash_inside_a_hash() {
    StripeForm form =
        formOf(pair("payment_method_options[card][request_multicapture]", "if_available"));

    assertThat(form.parameters().get("payment_method_options"))
        .isEqualTo(
            new StripeParameter.Hashed(
                sequenced(
                    List.of(
                        Map.entry(
                            "card",
                            new StripeParameter.Hashed(
                                sequenced(
                                    List.of(
                                        Map.entry(
                                            "request_multicapture",
                                            new StripeParameter.Scalar("if_available"))))))))));
  }

  @Test
  void decodes_a_list_of_hashes_numbered_by_index_into_its_elements_in_index_order() {
    StripeForm form =
        formOf(
            pair("items[1][price]", "price_b"),
            pair("items[0][price]", "price_a"),
            pair("items[0][quantity]", "2"));

    assertThat(StripeParameter.elementsOf(form.parameters().get("items")))
        .contains(
            List.of(
                new StripeParameter.Hashed(
                    sequenced(
                        List.of(
                            Map.entry("price", new StripeParameter.Scalar("price_a")),
                            Map.entry("quantity", new StripeParameter.Scalar("2"))))),
                new StripeParameter.Hashed(
                    sequenced(
                        List.of(Map.entry("price", new StripeParameter.Scalar("price_b")))))));
  }

  @Test
  void keeps_brackets_inside_a_value_as_part_of_the_value() {
    assertThat(formOf(pair("description", "a[b]c]")).first("description")).isEqualTo("a[b]c]");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "amount[",
        "amount]",
        "amount[x",
        "amount[x]y",
        "amount[[x]]",
        "amount[x]]",
        "[amount]",
        "[]",
        "]amount",
        "]",
        "amount[[x]",
        "amount[x[y]",
        "amount[x]]y[z]"
      })
  void refuses_a_name_that_is_not_a_word_followed_by_bracketed_keys(String name) {
    StripeError unreadable = formOf(pair(name, "1")).unreadable();

    assertThat(unreadable).isNotNull();
    assertThat(unreadable.param()).isEqualTo(name);
    assertThat(unreadable.message())
        .contains(StripeFormGrammar.A_NAME_IS_A_WORD_AND_BRACKETED_KEYS);
  }

  @Test
  void refuses_a_value_sent_with_no_name_at_all_and_names_no_parameter() {
    StripeError unreadable = formOf(pair("", "orphan")).unreadable();

    assertThat(unreadable).isNotNull();
    assertThat(unreadable.param()).isNull();
  }

  @Test
  void refuses_a_key_after_empty_brackets_because_no_element_of_the_list_is_named() {
    StripeError unreadable = formOf(pair("items[][price]", "price_a")).unreadable();

    assertThat(unreadable).isNotNull();
    assertThat(unreadable.param()).isEqualTo("items[][price]");
    assertThat(unreadable.message()).contains(StripeFormGrammar.AN_APPENDED_ELEMENT_ENDS_THE_NAME);
  }

  @Test
  void refuses_a_list_sent_in_both_encodings_at_once_whichever_arrived_first() {
    assertRefusedInTwoShapesAt(
        "expand", pair("expand[]", "customer"), pair("expand[0]", "customer"));
    assertRefusedInTwoShapesAt(
        "expand", pair("expand[0]", "customer"), pair("expand[]", "customer"));
  }

  @Test
  void refuses_one_name_sent_both_as_a_value_and_as_a_hash_or_a_list() {
    assertRefusedInTwoShapesAt("metadata", pair("metadata", ""), pair("metadata[k]", "v"));
    assertRefusedInTwoShapesAt("metadata", pair("metadata[k]", "v"), pair("metadata", ""));
    assertRefusedInTwoShapesAt("expand", pair("expand", ""), pair("expand[]", "customer"));
    assertRefusedInTwoShapesAt("expand", pair("expand[]", "customer"), pair("expand", ""));
  }

  @Test
  void refuses_a_conflict_inside_a_hash_naming_the_nested_key_where_it_sits() {
    assertRefusedInTwoShapesAt(
        "card[exp]", pair("card[exp]", "12"), pair("card[exp][month]", "12"));
    assertRefusedInTwoShapesAt(
        "card[exp]", pair("card[exp][]", "12"), pair("card[exp][month]", "12"));
  }

  @Test
  void refuses_a_value_sent_twice_under_one_name_whatever_its_depth() {
    assertRefusedAsSentTwiceAt("amount", pair("amount", "1099", "2000"));
    assertRefusedAsSentTwiceAt("metadata[k]", pair("metadata[k]", "a", "b"));
    assertRefusedAsSentTwiceAt("expand[0]", pair("expand[0]", "customer", "customer"));
  }

  @Test
  void keeps_an_element_appended_twice_as_two_elements_rather_than_refusing_it() {
    StripeForm form = formOf(pair("expand[]", "customer", "customer"));

    assertThat(form.unreadable()).isNull();
    assertThat(form.listOf("expand")).containsExactly("customer", "customer");
  }

  @Test
  void a_refused_form_reads_as_carrying_no_parameter_at_all() {
    StripeForm form = formOf(pair("amount", "1099"), pair("expand[]", "a"), pair("expand[0]", "b"));

    assertThat(form.parameters()).isEmpty();
    assertThat(form.first("amount")).isNull();
  }

  @Test
  void
      the_refusal_is_an_invalid_request_with_no_code_that_names_where_the_form_stopped_being_readable() {
    StripeError unreadable = formOf(pair("expand[]", "a"), pair("expand[0]", "b")).unreadable();

    assertThat(StripeJsonWriter.toText(unreadable.body()))
        .isEqualTo(
            "{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"The request encodes a"
                + " parameter in a way this API cannot read: "
                + StripeFormGrammar.ONE_PARAMETER_IN_TWO_SHAPES
                + ".\",\"param\":\"expand\"}}");
    assertThat(unreadable.httpStatus()).isEqualTo(400);
  }

  @Test
  void compares_a_list_by_its_elements_and_their_order_whichever_encoding_carried_it() {
    SequencedMap<String, List<String>> appended =
        formOf(pair("expand[]", "customer", "payment_method")).compared();
    SequencedMap<String, List<String>> indexed =
        formOf(pair("expand[1]", "payment_method"), pair("expand[0]", "customer")).compared();
    SequencedMap<String, List<String>> reversed =
        formOf(pair("expand[]", "payment_method", "customer")).compared();

    assertThat(appended)
        .containsExactly(
            Map.entry("expand[0]", List.of("customer")),
            Map.entry("expand[1]", List.of("payment_method")));
    assertThat(indexed).containsExactlyInAnyOrderEntriesOf(appended);
    assertThat(reversed).isNotEqualTo(appended);
  }

  @Test
  void compares_a_nested_hash_and_a_value_by_the_bracketed_path_that_reaches_each_value() {
    assertThat(
            formOf(
                    pair("amount", "1099"),
                    pair("metadata[order][id]", "o-1"),
                    pair("off_session", ""))
                .compared())
        .containsExactly(
            Map.entry("amount", List.of("1099")),
            Map.entry("metadata[order][id]", List.of("o-1")),
            Map.entry("off_session", List.of("")));
  }

  @Test
  void keeps_for_the_trail_every_name_exactly_as_the_client_spelled_it() {
    StripeForm form = formOf(pair("expand[]", "customer"), pair("metadata[0]", "zero"));

    assertThat(form.forAudit())
        .containsExactly(
            Map.entry("expand[]", List.of("customer")), Map.entry("metadata[0]", List.of("zero")));
    assertThat(formOf(pair("expand[]", "a"), pair("expand[0]", "b")).forAudit())
        .containsOnlyKeys("expand[]", "expand[0]");
  }

  private static void assertRefusedInTwoShapesAt(String param, Sent first, Sent second) {
    StripeError unreadable = formOf(first, second).unreadable();

    assertThat(unreadable).isNotNull();
    assertThat(unreadable.param()).isEqualTo(param);
    assertThat(unreadable.message()).contains(StripeFormGrammar.ONE_PARAMETER_IN_TWO_SHAPES);
  }

  private static void assertRefusedAsSentTwiceAt(String param, Sent sent) {
    StripeError unreadable = formOf(sent).unreadable();

    assertThat(unreadable).isNotNull();
    assertThat(unreadable.param()).isEqualTo(param);
    assertThat(unreadable.message()).contains(StripeFormGrammar.ONE_VALUE_SENT_TWICE);
  }

  private static StripeForm formOf(Sent... pairs) {
    return formOf(List.of(pairs));
  }

  private static StripeForm formOf(List<Sent> pairs) {
    LinkedHashMap<String, List<String>> sent = new LinkedHashMap<>();
    pairs.forEach(pair -> sent.put(pair.name(), pair.values()));
    return StripeForm.of(sent);
  }

  private static Sent pair(String name, String... values) {
    return new Sent(name, List.of(values));
  }

  private record Sent(String name, List<String> values) {}

  private static SequencedMap<String, StripeParameter> sequenced(
      List<Map.Entry<String, StripeParameter>> entries) {
    LinkedHashMap<String, StripeParameter> sequenced = new LinkedHashMap<>();
    for (Map.Entry<String, StripeParameter> entry : entries) {
      sequenced.put(entry.getKey(), entry.getValue());
    }
    return sequenced;
  }
}
