package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead.EmptyAnswer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class DeclaredReadTest {

  private static final String A_RETRACE = "aRetrace";

  private static final String THE_QUESTION =
      "which charge this provider holds under the identifiers a merchant sent itself.";

  private static final String NOTHING_FOUND =
      "this provider holds no charge of yours under those identifiers.";

  private static final String IT_ANSWERS_THE_PAYMENT_OR_REFUSES =
      "this provider answers the whole payment or refuses the call with PAYMENT_NOT_FOUND.";

  private static final EmptyAnswer WHAT_AN_EMPTY_ANSWER_MEANS =
      EmptyAnswer.whatItMeans(NOTHING_FOUND);

  private static final AFieldATesterFillsIn AN_IDENTIFIER =
      AFieldATesterFillsIn.typed(
          "myOwnIdentifier",
          "your own identifier for the charge",
          true,
          "what this provider finds the charge by.",
          "1001");

  @Test
  void a_read_carries_the_question_it_answers_and_the_fields_it_asks_for() {
    DeclaredRead read =
        new DeclaredRead(
            A_RETRACE, "Find the charge", THE_QUESTION, WHAT_AN_EMPTY_ANSWER_MEANS, aFieldAndOne());

    assertThat(read.keys()).containsExactly("myOwnIdentifier", "aSecondOne");
    assertThat(read.field("aSecondOne")).isPresent();
    assertThat(read.field("neverDeclared")).isEmpty();
  }

  @Test
  void a_read_with_no_name_is_refused_because_a_screen_sends_the_name_back() {
    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    "",
                    "Find it",
                    THE_QUESTION,
                    WHAT_AN_EMPTY_ANSWER_MEANS,
                    List.of(AN_IDENTIFIER)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a read with no name cannot be asked for");
  }

  @Test
  void a_read_with_no_label_is_refused_because_a_tester_picks_by_the_question() {
    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    A_RETRACE,
                    " ",
                    THE_QUESTION,
                    WHAT_AN_EMPTY_ANSWER_MEANS,
                    List.of(AN_IDENTIFIER)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must carry a label");
  }

  @Test
  void a_read_that_does_not_say_which_question_it_answers_is_refused() {
    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    A_RETRACE, "Find it", "", WHAT_AN_EMPTY_ANSWER_MEANS, List.of(AN_IDENTIFIER)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("which question a merchant asks it");
  }

  @Test
  void a_read_that_does_not_say_what_an_empty_answer_means_is_refused() {
    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    A_RETRACE,
                    "Find it",
                    THE_QUESTION,
                    EmptyAnswer.whatItMeans(""),
                    List.of(AN_IDENTIFIER)))
        .as(
            "an empty answer is the answer a merchant asks a retrace for most often, and a screen"
                + " that printed nothing beside it would read as a read that failed")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say what it means when it answers nothing at all");
  }

  @Test
  void a_read_that_never_answers_nothing_and_says_nothing_of_what_it_answers_instead_is_refused() {
    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    A_RETRACE,
                    "Find it",
                    THE_QUESTION,
                    EmptyAnswer.itNeverAnswersNothingBecause(" "),
                    List.of(AN_IDENTIFIER)))
        .as(
            "a read that cannot answer nothing is excused the sentence about an empty answer and"
                + " not the sentence about what it answers when the object is not there")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say what it answers in place of nothing");
  }

  @Test
  void a_read_that_can_answer_nothing_says_in_its_providers_own_words_what_that_means() {
    assertThat(
            new DeclaredRead(
                    A_RETRACE,
                    "Find it",
                    THE_QUESTION,
                    WHAT_AN_EMPTY_ANSWER_MEANS,
                    List.of(AN_IDENTIFIER))
                .whatItMeansThatThisOneAnsweredNothing())
        .isEqualTo(NOTHING_FOUND);
  }

  @Test
  void a_read_that_never_answers_nothing_has_no_sentence_for_an_answer_it_cannot_give() {
    DeclaredRead itAnswersOrRefuses =
        new DeclaredRead(
            A_RETRACE,
            "Find it",
            THE_QUESTION,
            EmptyAnswer.itNeverAnswersNothingBecause(IT_ANSWERS_THE_PAYMENT_OR_REFUSES),
            List.of(AN_IDENTIFIER));

    assertThatThrownBy(itAnswersOrRefuses::whatItMeansThatThisOneAnsweredNothing)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares that it never answers nothing at all")
        .hasMessageContaining(IT_ANSWERS_THE_PAYMENT_OR_REFUSES);
  }

  @Test
  void a_read_that_asks_a_tester_for_nothing_is_refused() {
    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    A_RETRACE, "Find it", THE_QUESTION, WHAT_AN_EMPTY_ANSWER_MEANS, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("asks a tester for nothing");
  }

  @Test
  void one_field_declared_twice_by_a_read_is_refused() {
    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    A_RETRACE,
                    "Find it",
                    THE_QUESTION,
                    WHAT_AN_EMPTY_ANSWER_MEANS,
                    List.of(AN_IDENTIFIER, AN_IDENTIFIER)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("is declared twice by the read");
  }

  @Test
  void a_read_that_offers_the_callback_targets_of_the_run_is_refused() {
    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    A_RETRACE,
                    "Find it",
                    THE_QUESTION,
                    WHAT_AN_EMPTY_ANSWER_MEANS,
                    List.of(
                        AFieldATesterFillsIn.theCallbackTarget(
                            "notifyUrl",
                            "where the callback goes",
                            "where this provider posts the callback."))))
        .as(
            "a read answers its caller and posts nothing anywhere, so a tester picking a delivery"
                + " target would be choosing where an answer that never leaves the response goes")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a read posts no callback anywhere");
  }

  @Test
  void a_read_that_asks_a_tester_for_a_value_it_also_takes_from_the_account_is_refused() {
    SequencedMap<String, String> theAccountsOwn =
        new LinkedHashMap<>(
            Map.of("myOwnIdentifier", "taken from the account this question is asked on"));

    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    A_RETRACE,
                    "Find it",
                    THE_QUESTION,
                    WHAT_AN_EMPTY_ANSWER_MEANS,
                    List.of(AN_IDENTIFIER),
                    theAccountsOwn))
        .as(
            "a tester who types a value the simulator overwrites before the provider sees it is"
                + " answering a question nothing reads")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("put a credential of the account on a screen");
  }

  @Test
  void a_read_that_fills_a_field_in_itself_without_saying_where_from_is_refused() {
    SequencedMap<String, String> nothingSaid = new LinkedHashMap<>(Map.of("userId", "  "));

    assertThatThrownBy(
            () ->
                new DeclaredRead(
                    A_RETRACE,
                    "Find it",
                    THE_QUESTION,
                    WHAT_AN_EMPTY_ANSWER_MEANS,
                    List.of(AN_IDENTIFIER),
                    nothingSaid))
        .as(
            "a field drawn with no sentence beside it reads as a value this simulator failed to"
                + " record rather than as one no tester is ever asked for")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("where the value comes from");
  }

  @Test
  void what_a_read_fills_in_itself_is_a_copy_in_the_order_the_provider_declared_it() {
    LinkedHashMap<String, String> declared = new LinkedHashMap<>();
    declared.put("userId", "the merchant the account names, sent with the call.");
    declared.put("X-Merchant-Document", "the establishment the account names, sent as a header.");
    DeclaredRead read =
        new DeclaredRead(
            A_RETRACE,
            "Find it",
            THE_QUESTION,
            WHAT_AN_EMPTY_ANSWER_MEANS,
            List.of(AN_IDENTIFIER),
            declared);

    declared.put("aFieldDeclaredAfterwards", "nothing a form drawn from this read prints.");

    assertThat(read.fieldsItFillsInItself().keySet())
        .as(
            "a form prints these in the order the provider wrote them, and what it prints is the"
                + " copy this read took rather than a map its provider still holds")
        .containsExactly("userId", "X-Merchant-Document");
    assertThatThrownBy(() -> read.fieldsItFillsInItself().put("anotherOne", "a."))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void a_read_that_asks_a_tester_for_every_value_it_sends_fills_nothing_in_itself() {
    assertThat(
            new DeclaredRead(
                    A_RETRACE,
                    "Find it",
                    THE_QUESTION,
                    WHAT_AN_EMPTY_ANSWER_MEANS,
                    List.of(AN_IDENTIFIER))
                .fieldsItFillsInItself())
        .as(
            "a read whose every value a tester types fills nothing in, and that is a declaration"
                + " of this contract rather than a sentence a provider forgot to write")
        .isEmpty();
  }

  private static List<AFieldATesterFillsIn> aFieldAndOne() {
    return List.of(
        AN_IDENTIFIER,
        AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
            "aSecondOne", "the card it was taken on", true, "what this run has already minted."));
  }
}
