package dev.marcinromanowski.pspsim.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.EnumSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AShapeSaidInWordsTest {

  @Test
  void the_shape_a_test_run_is_named_in_is_said_in_words_a_tester_who_reads_no_regex_can_act_on() {
    assertThat(TestRunId.SHAPE.saidInWords())
        .as(
            "every character a run id may carry is named, so a tester who typed a space reads"
                + " that a space is not among them without being handed the pattern to parse")
        .isEqualTo(
            "at most 64 characters, the first a letter or a digit and the rest a letter, a digit,"
                + " a dot, a hyphen or an underscore, for example checkout-smoke");
  }

  @Test
  void a_refusal_names_the_value_it_refused_the_pattern_it_enforces_and_the_shape_in_words() {
    assertThat(TestRunId.SHAPE.theRefusalOf("testRunId"))
        .as(
            "the pattern stays for the suite author who matches on it and the words follow for"
                + " the tester beside the box, in one sentence both audiences are answered in")
        .isEqualTo(
            "testRunId must match [A-Za-z0-9][A-Za-z0-9._-]{0,63}, which is at most 64"
                + " characters, the first a letter or a digit and the rest a letter, a digit, a"
                + " dot, a hyphen or an underscore, for example checkout-smoke");
  }

  @ParameterizedTest(name = "{0} is said as {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "[a-z][a-z0-9-]{0,31}|grow|at most 32 characters, the first a lowercase letter and the"
            + " rest a lowercase letter, a digit or a hyphen",
        "[0-9]+|12345678909|one character or more, each a digit",
        "[0-9]{11}|12345678909|exactly 11 characters, each a digit",
        "[0-9A-Za-z]{1,2}|05|at most 2 characters, each a letter or a digit",
        "[A-Z]{3}|EUR|exactly 3 characters, each an uppercase letter",
        "[a-z]|a|one character, each a lowercase letter",
        "[a-z]{3,8}|abcd|between 3 and 8 characters, each a lowercase letter",
        "[a-z][a-z ]+|a b|at least 2 characters, the first a lowercase letter and the rest a"
            + " lowercase letter or a space",
        "[a-z._-]+|a.b_c-d|one character or more, each a lowercase letter, a dot, a hyphen or an"
            + " underscore"
      })
  void a_length_and_a_character_set_are_both_read_off_the_pattern_and_never_declared_beside_it(
      String shape, String aValueItAccepts, String expected) {
    assertThat(AShapeSaidInWords.of(shape, aValueItAccepts).theLengthAndCharactersInWords())
        .isEqualTo(expected);
  }

  @ParameterizedTest(name = "refuses to say {0} in words")
  @ValueSource(
      strings = {
        "-?[0-9A-Za-z]{1,2}",
        "[0-9]{2} - .+",
        "[^/?#]+",
        "[a-z]+[0-9]+",
        "[0-9]{2}|[0-9]{4}",
        "[0-9]{1,3}|[0-9]{5}",
        "[0-9]*",
        "(foo|bar)"
      })
  void a_shape_these_words_would_misstate_is_refused_rather_than_said_wrongly(String shape) {
    assertThatThrownBy(() -> AShapeSaidInWords.of(shape, anExampleOf(shape)))
        .as(
            "a grammar, an alternation or a set carrying a character these words have no name for"
                + " is the publisher's own to say: the core refuses to put a sentence beside a"
                + " pattern it cannot describe, which is what keeps the sentences it does put"
                + " there true")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is not one this simulator can say in words")
        .hasMessageContaining(shape)
        .hasMessageContaining("Say it in the words of whoever published it instead");
  }

  @Test
  void a_shape_is_measured_over_the_lengths_it_accepts_and_not_only_over_the_short_values() {
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-9]{1,3}|[0-9]{5}", "12345"))
        .as(
            "the gap at four digits is longer than any value the character sweep builds, so the"
                + " lengths are walked one by one as well and the run of them is what is stated")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("disagree over a value of 4 characters");
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-9]*", "7"))
        .as(
            "a shape accepting the empty value is not one any count of characters states, and"
                + " the empty value is in the sweep for exactly that")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("disagree over the value \"\"")
        .as(
            "the refusal says which of the two took the value and which turned it away, and a"
                + " message that had those two the wrong way round would send whoever reads it"
                + " looking at the wrong half of the disagreement")
        .hasMessageContaining("which the shape accepts and the words refuse");
  }

  @Test
  void the_value_the_words_offer_a_tester_to_copy_is_one_the_pattern_accepts() {
    assertThatThrownBy(() -> AShapeSaidInWords.of("[a-z]+", "Not Lowercase"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("which that shape does not accept")
        .hasMessageContaining("would be refused the moment they typed it");
    assertThatThrownBy(() -> AShapeSaidInWords.of("[a-z]+", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("which that shape does not accept");
  }

  @Test
  void a_shape_no_value_of_the_characters_it_admits_matches_is_refused() {
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-9]{2} - .+", "05 - Do not honor"))
        .as(
            "the characters are measured one position at a time, so a shape no value built out of"
                + " them matches at any length is one these words cannot state the length of")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "no value built from the characters it was measured to accept matches it at any"
                + " length up to ");
  }

  @Test
  void the_only_value_a_shape_is_asked_about_is_whether_the_pattern_itself_admits_it() {
    AShapeSaidInWords shape = AShapeSaidInWords.of("[a-z]{2,4}", "abc");

    assertThat(shape.saidInWords())
        .isEqualTo("between 2 and 4 characters, each a lowercase letter, for example abc");
    assertThat(shape.admits("ab")).isTrue();
    assertThat(shape.admits("abcd")).isTrue();
    assertThat(shape.admits("a")).isFalse();
    assertThat(shape.admits("abcde")).isFalse();
    assertThat(shape.admits("abC")).isFalse();
  }

  @Test
  void a_shape_with_no_longest_it_states_says_so_rather_than_naming_the_ceiling_it_was_probed_to() {
    AShapeSaidInWords unbounded = AShapeSaidInWords.of("[0-9]+", "7");

    assertThat(unbounded.saidInWords())
        .as(
            "the length this measurement walks to is its own and no bound the publisher stated,"
                + " so a shape still accepting there says nothing about a longest")
        .isEqualTo("one character or more, each a digit, for example 7")
        .doesNotContain(
            String.valueOf(AShapeSaidInWords.AS_FAR_AS_THE_COUNTS_A_SHAPE_WRITES_MAY_MULTIPLY));
    assertThat(
            unbounded.admits(
                "7".repeat(AShapeSaidInWords.AS_FAR_AS_THE_COUNTS_A_SHAPE_WRITES_MAY_MULTIPLY + 1)))
        .isTrue();
  }

  @ParameterizedTest(name = "{0} names the character kind it is")
  @CsvSource({
    "a,A_LOWERCASE_LETTER",
    "z,A_LOWERCASE_LETTER",
    "A,AN_UPPERCASE_LETTER",
    "Z,AN_UPPERCASE_LETTER",
    "0,A_DIGIT",
    "9,A_DIGIT",
    ".,A_DOT",
    "-,A_HYPHEN",
    "_,AN_UNDERSCORE"
  })
  void every_character_these_words_can_name_is_named_whichever_one_of_its_kind_it_is(
      char character, ACharacterAValueIsMadeOf kind) {
    assertThat(ACharacterAValueIsMadeOf.of(character)).contains(kind);
  }

  @Test
  void a_space_is_named_and_a_character_outside_the_vocabulary_is_named_by_nothing() {
    assertThat(ACharacterAValueIsMadeOf.of(' ')).contains(ACharacterAValueIsMadeOf.A_SPACE);
    for (char unnameable :
        AShapeSaidInWords.CHARACTERS_THESE_WORDS_HAVE_NO_NAME_FOR.toCharArray()) {
      assertThat(ACharacterAValueIsMadeOf.of(unnameable))
          .as("the probe alphabet a shape is measured against carries %s", unnameable)
          .isEqualTo(Optional.empty());
    }
  }

  @Test
  void naming_no_character_at_all_is_refused_because_such_a_shape_accepts_nothing() {
    assertThatThrownBy(
            () ->
                ACharacterAValueIsMadeOf.theWordsOfEachOf(
                    EnumSet.noneOf(ACharacterAValueIsMadeOf.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("no character at all was named");
  }

  @Test
  void one_character_kind_is_said_without_a_list_and_two_are_joined_by_or() {
    assertThat(
            ACharacterAValueIsMadeOf.theWordsOfEachOf(EnumSet.of(ACharacterAValueIsMadeOf.A_DOT)))
        .isEqualTo("a dot");
    assertThat(
            ACharacterAValueIsMadeOf.theWordsOfEachOf(
                EnumSet.of(ACharacterAValueIsMadeOf.A_DIGIT, ACharacterAValueIsMadeOf.A_DOT)))
        .isEqualTo("a digit or a dot");
    assertThat(
            ACharacterAValueIsMadeOf.theWordsOfEachOf(
                EnumSet.of(ACharacterAValueIsMadeOf.AN_UPPERCASE_LETTER)))
        .as("one case alone is said as that case, because collapsing it would widen the shape")
        .isEqualTo("an uppercase letter");
  }

  @Test
  void a_class_admitting_part_of_a_kind_is_refused_rather_than_said_as_the_whole_kind() {
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-5]{4}", "0123"))
        .as(
            "one representative per kind said \"exactly 4 characters, each a digit\" of a shape"
                + " that refuses 9999, so every character of a kind is tried before the kind is"
                + " named and a kind only half admitted is refused instead")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("[0-5]{4}")
        .hasMessageContaining("accepts only some of what these words call a digit")
        .hasMessageContaining("in the position a value begins with");
    assertThatThrownBy(() -> AShapeSaidInWords.of("[a-c]{1,3}", "abc"))
        .as("and \"each a lowercase letter\" of a shape that refuses zzz")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accepts only some of what these words call a lowercase letter");
    assertThatThrownBy(() -> AShapeSaidInWords.of("[a-z][A-M]{2}", "aAB"))
        .as("a kind is measured in the position a value is otherwise made of as well")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accepts only some of what these words call an uppercase letter")
        .hasMessageContaining("in the position a value is otherwise made of");
  }

  @Test
  void a_bound_a_pattern_writes_is_stated_from_a_walk_that_reaches_past_every_count_it_writes() {
    AShapeSaidInWords theWidestCountItReads = AShapeSaidInWords.of("[0-9]{1,512}", "7");

    assertThat(theWidestCountItReads.saidInWords())
        .as(
            "the walk runs to a length no count this pattern writes can reach rather than to a"
                + " ceiling of its own, so a bound past where a fixed ceiling sat is measured"
                + " instead of read as no bound at all")
        .isEqualTo("at most 512 characters, each a digit, for example 7");
    assertThat(theWidestCountItReads.admits("7".repeat(512))).isTrue();
    assertThat(theWidestCountItReads.admits("7".repeat(513))).isFalse();
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-9]{1,513}", "7"))
        .as("and one count wider than the widest it multiplies to is refused, not stated")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the counts it writes multiply to more than 512");
  }

  @Test
  void a_longest_a_second_branch_of_a_shape_reaches_is_measured_and_not_walked_past() {
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-9]{1,3}|[0-9]{5}", "12345"))
        .as(
            "a walk to a fixed ceiling would have called this \"at most 3 characters\" had the"
                + " second branch sat past the ceiling; every count a shape writes is inside the"
                + " walk, so the gap is met")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("disagree over a value of 4 characters");
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-9]{1,3}|[0-9]{5,}", "12345"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("disagree over a value of 4 characters");
  }

  @Test
  void counts_multiplying_past_the_widest_this_measurement_walks_to_are_refused() {
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-9]{1,1000}[0-9]{1,1000}", "12"))
        .as(
            "the walk runs to a length no count the pattern writes can reach, and counts"
                + " multiplying this far put that length past where this measurement walks, so"
                + " not every length the shape accepts would have been tried")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the counts it writes multiply to more than 512");
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-9]{2000}", "0".repeat(2000)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the counts it writes multiply to more than 512");
    assertThatThrownBy(() -> AShapeSaidInWords.of("[0-9]{1,2000}", "7"))
        .as(
            "a walk that stopped at a fixed ceiling of 1024 read this bound as no bound and said"
                + " \"one character or more, each a digit\" of a shape refusing 2001 digits; a"
                + " bound no walk of this measurement reaches is refused rather than misstated")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the counts it writes multiply to more than 512");
  }

  @Test
  void a_bound_written_in_the_pattern_is_the_one_the_words_state_and_the_one_a_value_meets() {
    AShapeSaidInWords asWideAsAStoreHolds = AShapeSaidInWords.of("[0-9]{1,32}", "12345678000190");

    assertThat(asWideAsAStoreHolds.saidInWords())
        .as(
            "a bound enforced beside a pattern is one these words cannot see, so it belongs in"
                + " the pattern, where the same measurement that names the characters states it")
        .isEqualTo("at most 32 characters, each a digit, for example 12345678000190");
    assertThat(asWideAsAStoreHolds.admits("1".repeat(32))).isTrue();
    assertThat(asWideAsAStoreHolds.admits("1".repeat(33))).isFalse();
  }

  @Test
  void a_clause_whoever_published_a_shape_adds_qualifies_the_shape_and_not_the_worked_example() {
    AShapeSaidInWords aPresetName =
        AShapeSaidInWords.of("[a-z][a-z0-9-]{0,63}", "a-customer-with-a-stored-card");

    assertThat(aPresetName.theRefusalOf("a persona name", "the shape a preset name already has"))
        .as(
            "a clause appended after the whole refusal landed behind the worked example and"
                + " qualified it; composed into the words it sits where the shape is described")
        .isEqualTo(
            "a persona name must match [a-z][a-z0-9-]{0,63}, which is at most 64 characters, the"
                + " first a lowercase letter and the rest a lowercase letter, a digit or a hyphen,"
                + " the shape a preset name already has, for example a-customer-with-a-stored-card");
  }

  @Test
  void a_blank_clause_is_refused_because_it_would_leave_two_commas_around_no_words() {
    AShapeSaidInWords shape = AShapeSaidInWords.of("[a-z]{2,4}", "abc");

    assertThatThrownBy(() -> shape.theRefusalOf("a name", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a blank one says nothing");
    assertThatThrownBy(() -> shape.theRefusalOf("a name", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a blank one says nothing");
  }

  private static String anExampleOf(String shape) {
    return switch (shape) {
      case "-?[0-9A-Za-z]{1,2}" -> "-1";
      case "[0-9]{2} - .+" -> "05 - Do not honor";
      case "[^/?#]+" -> "merchant-1";
      case "[a-z]+[0-9]+" -> "abc123";
      case "[0-9]{2}|[0-9]{4}" -> "0123";
      case "[0-9]{1,3}|[0-9]{5}" -> "12345";
      case "[0-9]*" -> "7";
      default -> "foo";
    };
  }
}
