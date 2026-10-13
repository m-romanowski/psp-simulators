package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TheFieldParameterBothRoutesReadTest {

  private static final String THE_MAIN_SOURCES = "src/main/java";

  private static final String THE_ONE_FILE_THE_SEPARATOR_IS_SPELLED_IN =
      "TheFieldParameterBothRoutesRead.java";

  private static final String THE_SEPARATOR_AS_A_SOURCE_SPELLS_IT = "BETWEEN_THE_KEY_AND_THE_VALUE";

  private static final String THE_SOURCES_OF_THE_CONTROL_API =
      "src/main/java/dev/marcinromanowski/pspsim/control";

  private static final List<String> THE_WAYS_A_SOURCE_CUTS_A_PAIR_ITSELF =
      List.of(
          "indexOf(\"=\")",
          "indexOf('=')",
          "split(\"=\")",
          "lastIndexOf(\"=\")",
          "lastIndexOf('=')");

  @Test
  void the_reads_route_refuses_a_pair_without_a_separator_in_the_words_it_published_before() {
    assertThat(
            TheReadsAProviderPublishes
                .A_FIELD_CARRIES_THE_KEY_IT_IS_SENT_FOR_AND_THE_VALUE_AFTER_IT)
        .isEqualTo(
            "every value of a read is sent as one field parameter spelling the key the provider"
                + " reads it under, then =, then the value, as field=<key>=<value>. The keys one"
                + " read asks for are answered by the reads route of this provider, each with the"
                + " sentence its own contract describes it by, and this one carries no = at all: ");
  }

  @Test
  void the_originations_route_refuses_a_pair_without_a_separator_in_its_own_words() {
    assertThat(
            ThePaymentsAProviderStarts
                .A_FIELD_CARRIES_THE_KEY_IT_IS_SENT_FOR_AND_THE_VALUE_AFTER_IT)
        .isEqualTo(
            "every value a payment is started with is sent as one field parameter spelling the key"
                + " the provider reads it under, then =, then the value, as field=<key>=<value>."
                + " The keys one way of starting a payment asks for are answered by the"
                + " originations route of this provider, each with the sentence its own contract"
                + " describes it by, and this one carries no = at all: ");
  }

  @Test
  void each_route_says_in_its_own_words_that_it_asks_each_field_once() {
    assertThat(TheReadsAProviderPublishes.ONE_READ_ASKS_EACH_OF_ITS_FIELDS_ONCE)
        .isEqualTo("one read asks each of its fields once and this request sends two values for ");
    assertThat(
            ThePaymentsAProviderStarts.ONE_WAY_OF_STARTING_A_PAYMENT_ASKS_EACH_OF_ITS_FIELDS_ONCE)
        .isEqualTo(
            "one way of starting a payment asks each of its fields once and this request sends two"
                + " values for ");
  }

  @Test
  void a_value_sent_with_nothing_before_the_separator_is_refused_the_same_way_on_both_routes() {
    assertThat(TheFieldParameterBothRoutesRead.A_FIELD_NAMES_THE_KEY_IT_IS_SENT_FOR)
        .isEqualTo(
            "a value sent with nothing before the = names no key the provider reads it under, so"
                + " nothing could be sent anywhere: ");
    assertThat(TheFieldParameterBothRoutesRead.AND_ONE_OF_THEM_WOULD_BE_DROPPED)
        .isEqualTo(
            ", so one of the two would be dropped without a word, which is why it is refused here"
                + " instead. Send the field once.");
  }

  @Test
  void one_pair_is_published_spelled_out_and_one_field_is_published_with_its_key_filled_in() {
    assertThat(TheFieldParameterBothRoutesRead.theSpellingOfOnePair())
        .isEqualTo("field=<key>=<value>");
    assertThat(TheFieldParameterBothRoutesRead.theQueryParameterAValueIsSentIn("paymentId"))
        .isEqualTo("field=paymentId=<value>");
  }

  @Test
  void the_values_a_request_sent_are_read_in_the_order_they_were_sent_and_keep_a_value_whole() {
    assertThat(
            TheFieldParameterBothRoutesRead.theValuesItWasSent(
                List.of("zeta=last", "alpha=first", "returnUrl=https://back?a=1&b=2"),
                TheReadsAProviderPublishes.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .containsExactly(
            entry("zeta", "last"),
            entry("alpha", "first"),
            entry("returnUrl", "https://back?a=1&b=2"));
  }

  @Test
  void a_request_sending_nothing_at_all_sent_no_value() {
    assertThat(
            TheFieldParameterBothRoutesRead.theValuesItWasSent(
                null, ThePaymentsAProviderStarts.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .isEmpty();
    assertThat(
            TheFieldParameterBothRoutesRead.theValuesItWasSent(
                List.of(), ThePaymentsAProviderStarts.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .isEmpty();
  }

  @Test
  void one_parse_refuses_in_the_words_of_whichever_route_asked_it_to_parse() {
    assertThatThrownBy(
            () ->
                TheFieldParameterBothRoutesRead.theValuesItWasSent(
                    List.of("paymentId"),
                    TheReadsAProviderPublishes.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .hasMessage(
            TheReadsAProviderPublishes.A_FIELD_CARRIES_THE_KEY_IT_IS_SENT_FOR_AND_THE_VALUE_AFTER_IT
                + "paymentId");
    assertThatThrownBy(
            () ->
                TheFieldParameterBothRoutesRead.theValuesItWasSent(
                    List.of("amount"),
                    ThePaymentsAProviderStarts.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .hasMessage(
            ThePaymentsAProviderStarts.A_FIELD_CARRIES_THE_KEY_IT_IS_SENT_FOR_AND_THE_VALUE_AFTER_IT
                + "amount");
    assertThatThrownBy(
            () ->
                TheFieldParameterBothRoutesRead.theValuesItWasSent(
                    List.of(" =tok_1"),
                    TheReadsAProviderPublishes.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .hasMessage(
            TheFieldParameterBothRoutesRead.A_FIELD_NAMES_THE_KEY_IT_IS_SENT_FOR + " =tok_1");
    assertThatThrownBy(
            () ->
                TheFieldParameterBothRoutesRead.theValuesItWasSent(
                    List.of("amount=10.00", "amount=20.00"),
                    ThePaymentsAProviderStarts.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .hasMessage(
            ThePaymentsAProviderStarts.ONE_WAY_OF_STARTING_A_PAYMENT_ASKS_EACH_OF_ITS_FIELDS_ONCE
                + "amount"
                + TheFieldParameterBothRoutesRead.AND_ONE_OF_THEM_WOULD_BE_DROPPED);
  }

  @Test
  void a_route_taking_values_under_this_parameter_that_says_nothing_about_them_is_refused() {
    assertThatThrownBy(
            () ->
                new TheFieldParameterBothRoutesRead.TheWordsARouteTakesItsValuesIn(
                    "  ", "one read", "reads"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            TheFieldParameterBothRoutesRead
                .A_ROUTE_READING_THIS_PARAMETER_SAYS_WHAT_IT_TAKES_AND_WHAT_ANSWERS_ITS_KEYS);
    assertThatThrownBy(
            () ->
                new TheFieldParameterBothRoutesRead.TheWordsARouteTakesItsValuesIn(
                    "every value of a read", "  ", "reads"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new TheFieldParameterBothRoutesRead.TheWordsARouteTakesItsValuesIn(
                    "every value of a read", "one read", "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void the_separator_between_a_key_and_a_value_is_named_in_one_source_of_this_build() {
    List<Path> sources = theJavaSourcesUnder(Path.of(THE_MAIN_SOURCES));
    List<String> namingIt = new ArrayList<>();
    for (Path source : sources) {
      if (theTextOf(source).contains(THE_SEPARATOR_AS_A_SOURCE_SPELLS_IT)) {
        namingIt.add(String.valueOf(source.getFileName()));
      }
    }

    assertThat(sources)
        .as("the sources this sweep read, which is the denominator of what it found")
        .hasSizeGreaterThan(400);
    assertThat(namingIt)
        .as(
            "two routes take a tester's values under one published parameter and a second parse of"
                + " that grammar is a second grammar nobody published")
        .containsExactly(THE_ONE_FILE_THE_SEPARATOR_IS_SPELLED_IN);
  }

  @Test
  void no_other_source_of_this_control_api_cuts_a_pair_apart_on_an_equals_sign_of_its_own() {
    List<Path> sources = theJavaSourcesUnder(Path.of(THE_SOURCES_OF_THE_CONTROL_API));
    List<String> cuttingItThemselves = new ArrayList<>();
    for (Path source : sources) {
      String text = theTextOf(source);
      for (String way : THE_WAYS_A_SOURCE_CUTS_A_PAIR_ITSELF) {
        if (text.contains(way)
            && !String.valueOf(source.getFileName())
                .equals(THE_ONE_FILE_THE_SEPARATOR_IS_SPELLED_IN)) {
          cuttingItThemselves.add(String.valueOf(source.getFileName()) + " by " + way);
        }
      }
    }

    assertThat(sources)
        .as("the sources of this control api, which is the denominator of what it found")
        .hasSizeGreaterThan(5);
    assertThat(cuttingItThemselves)
        .as(
            "a source beside these two routes that cuts a pair apart on an equals sign of its own"
                + " parses this grammar a second time without naming it, which is how the two"
                + " copies this file replaced came to be. A provider reading its own"
                + " form-urlencoded wire body is a different grammar and lives outside this"
                + " package")
        .isEmpty();
  }

  private static List<Path> theJavaSourcesUnder(Path root) {
    try (Stream<Path> found = Files.walk(root)) {
      return found.filter(it -> String.valueOf(it.getFileName()).endsWith(".java")).toList();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static String theTextOf(Path source) {
    try {
      return Files.readString(source);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
