package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class TheParameterATestersValueArrivesUnderTest {

  private static final int THE_CONTROLS_A_FORM_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_POSTS = 12;

  private static final String
      THE_FIRST_VALUE_A_PORT_DECLARES_NOTHING_FOR_IS_THE_ONE_NAMED_IN_THE_REFUSAL_A_TESTER_READS =
          "a port walks the values a form posted in their own order and refuses the first one the"
              + " way or the read declares no field for, so the field named in the refusal drawn"
              + " on the screen is the one the form posted first. Twelve values leave 479001600"
              + " orders an unordered copy could draw from the keys' hashes and the salt"
              + " java.util.ImmutableCollections draws once per JVM, so this assertion cannot"
              + " pass by coincidence on a build that copies them unordered";

  private static final String
      AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT =
          "Map.of, Map.copyOf and HashMap iterate in an order drawn once per JVM, and once the"
              + " values are in such a map there is no way left to tell it from an ordered one,"
              + " so the values are handed to a port in a map whose type carries an order on"
              + " every run rather than one that happened to print in the right order on this one";

  @Test
  void
      the_values_a_form_posted_reach_a_port_in_the_order_the_form_posted_them_and_twelve_values_have_one_chance_in_479001600_of_landing_in_that_order_by_luck() {
    Map<String, String> posted = new LinkedHashMap<>();
    List<String> inTheOrderTheFormPostedThem = new ArrayList<>();
    for (int at = THE_CONTROLS_A_FORM_WIDE_ENOUGH_TO_RULE_OUT_A_LUCKY_ORDER_POSTS; at >= 1; at--) {
      String key = "key" + at;
      posted.put(
          TheParameterATestersValueArrivesUnder.onAFormControlFor(key),
          "the value typed " + at + "th");
      inTheOrderTheFormPostedThem.add(key);
    }

    SequencedMap<String, String> values =
        TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(posted);

    assertThat(List.copyOf(values.keySet()))
        .as(
            THE_FIRST_VALUE_A_PORT_DECLARES_NOTHING_FOR_IS_THE_ONE_NAMED_IN_THE_REFUSAL_A_TESTER_READS)
        .isEqualTo(List.copyOf(inTheOrderTheFormPostedThem));
    assertThat(values)
        .as(AN_ORDER_A_TYPE_CARRIES_HOLDS_ON_EVERY_RUN_AND_ONE_A_RUN_HAPPENED_TO_PRINT_DOES_NOT)
        .isInstanceOf(SequencedMap.class);
  }

  @Test
  void a_form_control_is_named_after_the_key_the_provider_declared_the_field_under() {
    assertThat(TheParameterATestersValueArrivesUnder.onAFormControlFor("sum"))
        .isEqualTo("field.sum");
    assertThat(TheParameterATestersValueArrivesUnder.onAFormControlFor("pageField[fullName]"))
        .as(
            "a key a provider spells with brackets of its own travels under the one parameter"
                + " name and is never reshaped on the way")
        .isEqualTo("field.pageField[fullName]");
  }

  @Test
  void the_name_a_control_is_rendered_under_is_the_name_the_key_is_read_back_out_of() {
    Map<String, String> posted = new LinkedHashMap<>();
    posted.put(TheParameterATestersValueArrivesUnder.onAFormControlFor("sum"), "10.00");
    posted.put(
        TheParameterATestersValueArrivesUnder.onAFormControlFor("pageField[fullName]"),
        "Dana Levi");

    assertThat(TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(posted))
        .as(
            "one declaration names the parameter a control is drawn under and names the parameter"
                + " the key is read back out of, so a screen cannot render a name no port parses")
        .containsOnly(Map.entry("sum", "10.00"), Map.entry("pageField[fullName]", "Dana Levi"));
  }

  @Test
  void only_the_parameters_a_field_control_sent_are_read_as_values_for_the_provider() {
    Map<String, String> posted = new LinkedHashMap<>();
    posted.put("provider", "aprovider");
    posted.put("testRunId", "a-run");
    posted.put("startedAs", "aWay");
    posted.put("fieldless", "not a value of a field");
    posted.put("field.sum", "10.00");

    assertThat(TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(posted))
        .containsOnly(Map.entry("sum", "10.00"));
  }

  @Test
  void a_parameter_named_exactly_the_prefix_arrives_as_a_value_for_no_key_at_all() {
    assertThat(
            TheParameterATestersValueArrivesUnder.theValuesAFormPostedUnderIt(
                Map.of(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL, "10.00")))
        .as(
            "the keyless parameter is carried through to the port rather than dropped here,"
                + " because a provider that declares no such field refuses it in its own words")
        .containsOnly(Map.entry("", "10.00"));
  }

  @Test
  void the_name_is_the_one_word_and_the_key_follows_it_after_a_separator_of_its_own() {
    assertThat(TheParameterATestersValueArrivesUnder.IS_NAMED_FIELD).isEqualTo("field");
    assertThat(TheParameterATestersValueArrivesUnder.ON_A_FORM_CONTROL)
        .as(
            "the control API spells the same name with a separator of its own, so the name is"
                + " shared and the separator is each surface's to declare")
        .isEqualTo(
            TheParameterATestersValueArrivesUnder.IS_NAMED_FIELD
                + TheParameterATestersValueArrivesUnder
                    .AND_A_FORM_CONTROL_NAMES_THE_KEY_AFTER_A_DOT);
  }
}
