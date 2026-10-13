package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.AnsweredValue;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.Fact;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead.EmptyAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadFormTest {

  private static final boolean A_RULE_OF_THIS_RUN_COULD_DECIDE_IT = true;

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final String RUN = "read-form";

  private static final String ACCOUNT = "merchant-1";

  private static final String A_RETRACE = "aRetrace";

  private static final String A_LOOK_UP = "aLookUp";

  private static final String NOTHING_FOUND =
      "this provider holds no charge of yours under those identifiers.";

  private static final String IT_ANSWERS_THE_PAYMENT_OR_REFUSES =
      "this provider answers the whole payment or refuses the call with PAYMENT_NOT_FOUND.";

  private static final String IT_PUBLISHES_NONE =
      "this provider answers every read off an id it gave you itself";

  private static final AValueAFieldOffers THE_CARD_THIS_RUN_SAVED =
      new AValueAFieldOffers("tok_1", "the card Dana Levi saved on obj_1");

  private static final List<AFieldATesterFillsIn> WHAT_IT_ASKS_FOR =
      List.of(
          AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
              "cardToken", "the saved card", true, "what this run minted."),
          AFieldATesterFillsIn.typed(
              "myOwnIdentifier", "your own identifier", true, "what it finds it by.", "1001"));

  private static final DeclaredRead THE_RETRACE =
      new DeclaredRead(
          A_RETRACE,
          "Find the charge",
          "which charge it holds under a merchant's own identifiers.",
          EmptyAnswer.whatItMeans(NOTHING_FOUND),
          WHAT_IT_ASKS_FOR);

  private static final String THE_MERCHANT_IS_THE_ACCOUNTS_OWN =
      "the credential of the account this question is asked on, which this simulator sends with"
          + " every read and never asks a tester to type";

  private static final DeclaredRead THE_RETRACE_THAT_SENDS_THE_MERCHANT_ITSELF =
      new DeclaredRead(
          A_RETRACE,
          "Find the charge",
          "which charge it holds under a merchant's own identifiers.",
          EmptyAnswer.whatItMeans(NOTHING_FOUND),
          WHAT_IT_ASKS_FOR,
          new LinkedHashMap<>(Map.of("userId", THE_MERCHANT_IS_THE_ACCOUNTS_OWN)));

  private static final DeclaredRead THE_LOOK_UP_THAT_ANSWERS_OR_REFUSES =
      new DeclaredRead(
          A_LOOK_UP,
          "Read one payment back",
          "everything it holds about one payment of yours.",
          EmptyAnswer.itNeverAnswersNothingBecause(IT_ANSWERS_THE_PAYMENT_OR_REFUSES),
          WHAT_IT_ASKS_FOR);

  private static final ProviderReads DECLARED =
      ProviderReads.theOnesItPublishes(APROVIDER, List.of(THE_RETRACE));

  @Test
  void a_form_with_no_question_picked_offers_every_question_this_provider_publishes() {
    ReadForm.View view = theFormWith(null, Map.of(), null);

    assertThat(view.panel()).isEqualTo(ReadForm.Panel.WHICH_QUESTION);
    assertThat(view.questions())
        .containsExactly(
            new ReadForm.Question(
                A_RETRACE,
                "Find the charge",
                "which charge it holds under a merchant's own identifiers.",
                NOTHING_FOUND,
                ReadForm.WHAT_IT_MEANS_WHEN_THIS_ONE_ANSWERS_NOTHING,
                false));
    assertThat(view.fields()).isEmpty();
  }

  @Test
  void a_question_whose_read_never_answers_nothing_is_offered_with_the_sentence_that_says_so() {
    ReadForm.View view =
        ReadForm.of(
            APROVIDER.value(),
            RUN,
            ACCOUNT,
            A_LOOK_UP,
            ProviderReads.theOnesItPublishes(
                APROVIDER, List.of(THE_LOOK_UP_THAT_ANSWERS_OR_REFUSES)),
            List.of(ACCOUNT),
            Map.of(),
            Map.of(),
            null,
            A_RULE_OF_THIS_RUN_COULD_DECIDE_IT);

    assertThat(view.questions())
        .singleElement()
        .satisfies(
            question -> {
              assertThat(question.theLeadInToWhatAnEmptyAnswerWouldMean())
                  .as(
                      "a read whose provider answers or refuses never answers nothing, and a"
                          + " tester reading a paragraph about an empty answer of it would be"
                          + " reading about a case this provider cannot produce")
                  .isEqualTo(ReadForm.THIS_ONE_NEVER_ANSWERS_NOTHING);
              assertThat(question.whatAnEmptyAnswerWouldMean())
                  .isEqualTo(IT_ANSWERS_THE_PAYMENT_OR_REFUSES);
            });
  }

  @Test
  void a_question_picked_draws_its_fields_and_marks_the_question_as_the_chosen_one() {
    ReadForm.View view = theFormWith(A_RETRACE, Map.of("myOwnIdentifier", "1001"), null);

    assertThat(view.panel()).isEqualTo(ReadForm.Panel.WHAT_THIS_READ_ASKS_FOR);
    assertThat(view.questions()).singleElement().satisfies(q -> assertThat(q.chosen()).isTrue());
    assertThat(view.chosenQuestion()).isNotNull();
    assertThat(view.fields())
        .extracting(TheFieldsAFormDraws.Field::key)
        .containsExactly("cardToken", "myOwnIdentifier");
    assertThat(view.fields().get(0).choices())
        .as("the cards this run minted are what the box suggests, under the words beside each")
        .containsExactly(
            new TheFieldsAFormDraws.Choice("tok_1", THE_CARD_THIS_RUN_SAVED.label(), false));
    assertThat(view.fields().get(0).isTypedIn())
        .as(
            "a read forwards this token to the provider, which is what answers for it, so the"
                + " read screen draws it the way the wizard does: a box with the run's own values"
                + " suggested on it")
        .isTrue();
    assertThat(view.fields().get(0).itSuggestsTheOffersBesideItWithoutImposingThem()).isTrue();
    assertThat(view.fields().get(1).value())
        .as("what a tester typed is still in the box when the answer is drawn beside it")
        .isEqualTo("1001");
  }

  @Test
  void a_run_that_has_minted_no_value_for_a_field_says_so_beside_a_box_that_still_takes_one() {
    ReadForm.View view =
        ReadForm.of(
            APROVIDER.value(),
            RUN,
            ACCOUNT,
            A_RETRACE,
            DECLARED,
            List.of(ACCOUNT),
            Map.of(),
            Map.of(),
            null,
            A_RULE_OF_THIS_RUN_COULD_DECIDE_IT);

    assertThat(view.fields().get(0).thisRunHasProducedNoneOfThese()).isTrue();
    assertThat(view.fields().get(0).isTypedIn()).isTrue();
    assertThat(view.fields().get(0).whatHasToHappenBeforeThisRunSuggestsOne())
        .isEqualTo(TheFieldsAFormDraws.THIS_RUN_HAS_PRODUCED_NO_VALUE_THIS_FIELD_COULD_SUGGEST);
  }

  @Test
  void
      what_a_query_string_carried_with_spaces_around_it_still_names_the_account_and_the_question() {
    ReadForm.View view = theFormWith(" " + A_RETRACE + " ", Map.of(), null);

    assertThat(view.readNamed()).isEqualTo(A_RETRACE);
    assertThat(view.chosenQuestion())
        .as("a question named with a space around it is the question, not one nothing publishes")
        .isNotNull();
    assertThat(
            ReadForm.of(
                    APROVIDER.value(),
                    RUN,
                    " " + ACCOUNT + " ",
                    A_RETRACE,
                    DECLARED,
                    List.of(ACCOUNT),
                    Map.of(),
                    Map.of(),
                    null,
                    A_RULE_OF_THIS_RUN_COULD_DECIDE_IT)
                .accountId())
        .isEqualTo(ACCOUNT);
  }

  @Test
  void a_run_configuring_no_account_is_told_to_configure_one_before_asking_anything() {
    ReadForm.View view =
        ReadForm.of(
            APROVIDER.value(),
            RUN,
            null,
            null,
            DECLARED,
            List.of(),
            Map.of(),
            Map.of(),
            null,
            A_RULE_OF_THIS_RUN_COULD_DECIDE_IT);

    assertThat(view.panel()).isEqualTo(ReadForm.Panel.NO_ACCOUNT_IS_CONFIGURED);
    assertThat(view.whatToDoWithNoAccount()).isEqualTo(ReadForm.THE_RUN_CONFIGURES_NO_ACCOUNT);
  }

  @Test
  void a_provider_publishing_no_read_says_in_its_own_words_what_it_has_instead() {
    ReadForm.View view =
        ReadForm.of(
            APROVIDER.value(),
            RUN,
            ACCOUNT,
            null,
            ProviderReads.noneOfThem(APROVIDER, IT_PUBLISHES_NONE),
            List.of(ACCOUNT),
            Map.of(),
            Map.of(),
            null,
            A_RULE_OF_THIS_RUN_COULD_DECIDE_IT);

    assertThat(view.panel()).isEqualTo(ReadForm.Panel.THIS_PROVIDER_PUBLISHES_NO_READ);
    assertThat(view.whatThisProviderPublishesInstead()).isEqualTo(IT_PUBLISHES_NONE);
  }

  @Test
  void a_question_this_provider_does_not_publish_leaves_the_form_on_the_list_of_the_ones_it_does() {
    ReadForm.View view = theFormWith("somethingElse", Map.of(), null);

    assertThat(view.panel()).isEqualTo(ReadForm.Panel.WHICH_QUESTION);
    assertThat(view.chosenQuestion()).isNull();
  }

  @Test
  void an_answer_with_rows_carries_them_as_the_provider_answered_them() {
    AnsweredRow row = new AnsweredRow("100003", List.of(new Fact("status", "paid")));

    ReadForm.Answer answer = ReadForm.answerOf(THE_RETRACE, new ReadOutcome.Answered(List.of(row)));

    assertThat(answer.rows())
        .containsExactly(
            new ReadForm.PrintedRow(
                "100003",
                List.of(new ReadForm.PrintedFact("status", true, "paid", null, List.of()))));
    assertThat(answer.itFoundNothing()).isFalse();
    assertThat(answer.whatItMeansThatNothingWasFound()).isNull();
    assertThat(answer.refusal()).isNull();
    assertThat(answer.whatItAnsweredAboutTheQuery()).isEmpty();
  }

  @Test
  void a_member_answered_under_a_member_is_drawn_under_the_name_the_provider_answered_it_under() {
    AnsweredRow row =
        new AnsweredRow(
            "pay_1",
            List.of(
                new Fact(
                    "events",
                    new AnsweredValue.TheElementsUnderIt(
                        List.of(
                            new AnsweredValue.TheMembersUnderIt(List.of(new Fact("type", "SALE"))),
                            new AnsweredValue.TheMembersUnderIt(
                                List.of(new Fact("type", "CAPTURE"))))))));

    ReadForm.Answer answer = ReadForm.answerOf(THE_RETRACE, new ReadOutcome.Answered(List.of(row)));

    ReadForm.PrintedFact events = answer.rows().getFirst().facts().getFirst();
    assertThat(events.asTheProviderNamesIt())
        .as(
            "a nested member is printed under the name its provider answers it under, and never"
                + " under a name this screen made out of it and the place it was answered in")
        .isEqualTo("events");
    assertThat(events.theProviderNamedIt()).isTrue();
    assertThat(events.value()).isNull();
    assertThat(events.under())
        .as("each step is drawn under the place the provider answered it in, counted from the one")
        .extracting(
            ReadForm.PrintedFact::asTheProviderNamesIt, ReadForm.PrintedFact::theProviderNamedIt)
        .containsExactly(
            tuple(ReadForm.THE_PLACE_A_LIST_ANSWERED_IT_IN + 1, false),
            tuple(ReadForm.THE_PLACE_A_LIST_ANSWERED_IT_IN + 2, false));
    assertThat(events.under().getFirst().under())
        .containsExactly(new ReadForm.PrintedFact("type", true, "SALE", null, List.of()));
    assertThat(events.under().getLast().under())
        .containsExactly(new ReadForm.PrintedFact("type", true, "CAPTURE", null, List.of()));
  }

  @Test
  void a_member_answered_with_no_value_an_empty_list_or_no_member_is_named_rather_than_dropped() {
    AnsweredRow row =
        new AnsweredRow(
            "pay_1",
            List.of(
                new Fact("maskedCard", new AnsweredValue.NoValueAtAll()),
                new Fact("events", new AnsweredValue.TheElementsUnderIt(List.of())),
                new Fact("acquirerTransaction", new AnsweredValue.TheMembersUnderIt(List.of()))));

    ReadForm.Answer answer = ReadForm.answerOf(THE_RETRACE, new ReadOutcome.Answered(List.of(row)));

    assertThat(answer.rows().getFirst().facts())
        .as("a fact dropped from a row reads as a member the provider never answered")
        .extracting(
            ReadForm.PrintedFact::asTheProviderNamesIt,
            ReadForm.PrintedFact::whatItAnsweredInsteadOfAValue)
        .containsExactly(
            tuple("maskedCard", ReadForm.ANSWERED_THIS_WITH_NO_VALUE_AT_ALL),
            tuple("events", ReadForm.ANSWERED_THIS_LIST_EMPTY),
            tuple("acquirerTransaction", ReadForm.ANSWERED_THIS_WITH_NO_MEMBER_OF_ITS_OWN));
  }

  @Test
  void a_fact_drawing_a_value_and_what_came_in_place_of_one_at_once_is_refused() {
    assertThatThrownBy(
            () -> new ReadForm.PrintedFact("status", true, "paid", "and nothing", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ReadForm.A_FACT_IS_ONE_VALUE_OR_WHAT_CAME_IN_PLACE_OF_ONE);
    assertThatThrownBy(
            () ->
                new ReadForm.PrintedFact(
                    "events",
                    true,
                    "paid",
                    null,
                    List.of(new ReadForm.PrintedFact("type", true, "SALE", null, List.of()))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ReadForm.A_FACT_IS_ONE_VALUE_OR_WHAT_CAME_IN_PLACE_OF_ONE);
    assertThatThrownBy(() -> new ReadForm.PrintedFact("status", true, null, null, List.of()))
        .as("a fact drawing none of the three prints as a name with nothing beside it")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ReadForm.A_FACT_IS_ONE_VALUE_OR_WHAT_CAME_IN_PLACE_OF_ONE);
  }

  @Test
  void an_answer_with_no_row_prints_what_this_provider_says_an_empty_answer_means() {
    ReadForm.Answer answer = ReadForm.answerOf(THE_RETRACE, new ReadOutcome.Answered(List.of()));

    assertThat(answer.itFoundNothing())
        .as(
            "nothing found is this provider's answer and not a failed read, so the screen says"
                + " what that means instead of leaving a tester with an empty panel")
        .isTrue();
    assertThat(answer.whatItMeansThatNothingWasFound()).isEqualTo(NOTHING_FOUND);
    assertThat(answer.refusal()).isNull();
  }

  @Test
  void
      what_a_provider_answered_about_the_query_is_drawn_beside_the_rows_and_never_as_one_of_them() {
    ReadForm.Answer answer =
        ReadForm.answerOf(
            THE_RETRACE,
            new ReadOutcome.Answered(
                List.of(), List.of(new Fact("hasMore", "false"), new Fact("limit", "50")), null));

    assertThat(answer.whatItAnsweredAboutTheQuery())
        .containsExactly(
            new ReadForm.PrintedFact("hasMore", true, "false", null, List.of()),
            new ReadForm.PrintedFact("limit", true, "50", null, List.of()));
    assertThat(answer.whatItMeansThatNothingWasFound())
        .as(
            "the window a provider echoes is no payment, so a tester reads both the window it"
                + " applied and the sentence that says what an empty answer of it means")
        .isEqualTo(NOTHING_FOUND);
    assertThat(answer.itFoundNothing()).isTrue();
  }

  @Test
  void a_read_that_never_answers_nothing_answering_no_row_is_a_defect_of_its_own_module() {
    ReadOutcome.Answered nothing = new ReadOutcome.Answered(List.of());

    assertThatThrownBy(() -> ReadForm.answerOf(THE_LOOK_UP_THAT_ANSWERS_OR_REFUSES, nothing))
        .as(
            "a module whose read declares that it answers or refuses, and then answers neither,"
                + " leaves a screen with no true sentence to print under the empty panel")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares that it never answers nothing at all");
  }

  @Test
  void a_refusal_is_drawn_in_the_providers_own_words_and_is_not_an_empty_answer() {
    ReadForm.Answer answer =
        ReadForm.answerOf(
            THE_RETRACE, new ReadOutcome.RefusedByTheProvider("140 unknown token", "rule-1"));

    assertThat(answer.refusal()).isEqualTo("140 unknown token");
    assertThat(answer.itFoundNothing())
        .as("a read this provider would not answer found nothing out, which is not nothing found")
        .isFalse();
    assertThat(answer.decidedBy()).isEqualTo("rule-1");
  }

  @Test
  void an_answer_a_scenario_gave_instead_names_the_rule_that_gave_it() {
    ReadForm.Answer answer =
        ReadForm.answerOf(THE_RETRACE, new ReadOutcome.AnsweredByAScenarioInstead("rule-2"));

    assertThat(answer.refusal()).isEqualTo(ReadForm.A_SCENARIO_ANSWERED_IT_INSTEAD + "rule-2");
    assertThat(answer.decidedBy()).isEqualTo("rule-2");
  }

  @Test
  void a_read_a_module_does_not_offer_is_drawn_as_what_that_provider_has_instead() {
    ReadForm.Answer answer =
        ReadForm.answerOf(THE_RETRACE, new ReadOutcome.NotOffered(IT_PUBLISHES_NONE));

    assertThat(answer.refusal()).isEqualTo(IT_PUBLISHES_NONE);
    assertThat(answer.decidedBy()).isNull();
  }

  @Test
  void an_account_the_run_no_longer_configures_is_reported_as_the_account_being_gone() {
    ReadForm.Answer answer = ReadForm.answerOf(THE_RETRACE, new ReadOutcome.NoSuchAccount());

    assertThat(answer.refusal()).isEqualTo(ReadForm.THE_ACCOUNT_IS_GONE);
  }

  @Test
  void every_question_on_this_screen_says_that_pressing_it_changes_no_object() {
    assertThat(theFormWith(null, Map.of(), null).whatAReadMoves())
        .isEqualTo(ReadForm.A_READ_MOVES_NOTHING);
    assertThat(ReadForm.A_READ_MOVES_NOTHING)
        .as(
            "a read a rule of this test run decides moves the story that rule carries, so the"
                + " sentence every question carries claims nothing about asking one twice")
        .doesNotContain("twice");
  }

  @Test
  void a_question_a_rule_of_this_run_may_decide_says_that_asking_it_twice_can_answer_twice() {
    assertThat(theFormAQuestionOfThisRunCouldBeDecidedOn(true).whatAskingThisQuestionAgainMoves())
        .isEqualTo(ReadForm.A_RULE_OF_THIS_RUN_MAY_DECIDE_THIS_QUESTION);
  }

  @Test
  void a_question_no_rule_of_this_run_is_matched_on_says_that_asking_it_twice_moves_nothing() {
    assertThat(theFormAQuestionOfThisRunCouldBeDecidedOn(false).whatAskingThisQuestionAgainMoves())
        .isEqualTo(ReadForm.NO_RULE_OF_THIS_RUN_IS_MATCHED_ON_THIS_QUESTION);
    assertThat(ReadForm.NO_RULE_OF_THIS_RUN_IS_MATCHED_ON_THIS_QUESTION)
        .contains("leaves this test run exactly as it was");
  }

  private static ReadForm.View theFormAQuestionOfThisRunCouldBeDecidedOn(
      boolean aRuleOfThisRunCouldDecideIt) {
    return ReadForm.of(
        APROVIDER.value(),
        RUN,
        ACCOUNT,
        null,
        DECLARED,
        List.of(ACCOUNT),
        Map.of("cardToken", List.of(THE_CARD_THIS_RUN_SAVED)),
        Map.of(),
        null,
        aRuleOfThisRunCouldDecideIt);
  }

  private static ReadForm.View theFormWith(
      String readNamed, Map<String, String> submitted, ReadForm.Answer answer) {
    return ReadForm.of(
        APROVIDER.value(),
        RUN,
        ACCOUNT,
        readNamed,
        DECLARED,
        List.of(ACCOUNT),
        Map.of("cardToken", List.of(THE_CARD_THIS_RUN_SAVED)),
        submitted,
        answer,
        A_RULE_OF_THIS_RUN_COULD_DECIDE_IT);
  }

  @Test
  void what_the_simulator_fills_in_belongs_to_the_question_chosen_and_not_to_the_provider() {
    ProviderReads publishing =
        ProviderReads.theOnesItPublishes(
            APROVIDER, List.of(THE_RETRACE_THAT_SENDS_THE_MERCHANT_ITSELF));

    ReadForm.View chosen =
        ReadForm.of(
            APROVIDER.value(),
            RUN,
            ACCOUNT,
            A_RETRACE,
            publishing,
            List.of(ACCOUNT),
            Map.of(),
            Map.of(),
            null,
            A_RULE_OF_THIS_RUN_COULD_DECIDE_IT);
    ReadForm.View nothingChosen =
        ReadForm.of(
            APROVIDER.value(),
            RUN,
            ACCOUNT,
            null,
            publishing,
            List.of(ACCOUNT),
            Map.of(),
            Map.of(),
            null,
            A_RULE_OF_THIS_RUN_COULD_DECIDE_IT);

    assertThat(chosen.filledIn())
        .as(
            "a read declares what this simulator sends for a tester, and the sentence saying where"
                + " the value comes from reaches the tester on the screen rather than staying in"
                + " the declaration")
        .containsExactly(
            new TheFieldsAFormDraws.FilledIn("userId", THE_MERCHANT_IS_THE_ACCOUNTS_OWN));
    assertThat(nothingChosen.filledIn())
        .as(
            "the list belongs to the question a tester chose and not to the provider, so a screen"
                + " with no question chosen promises nothing about what is sent")
        .isEmpty();
  }

  @Test
  void a_question_no_rule_of_this_run_can_decide_is_not_said_to_be_what_a_rule_is_matched_on() {
    assertThat(
            ReadForm.of(
                    APROVIDER.value(),
                    RUN,
                    ACCOUNT,
                    THE_RETRACE.name(),
                    DECLARED,
                    List.of(ACCOUNT),
                    Map.of(),
                    Map.of(),
                    null,
                    false)
                .aRuleOfThisRunCouldDecideIt())
        .as(
            "a provider may publish a read outside the operations its vocabulary names, and a"
                + " screen telling a tester their rule is matched on a question no rule can name"
                + " sends them looking for a rule that never fires")
        .isFalse();
    assertThat(theFormWith(THE_RETRACE.name(), Map.of(), null).aRuleOfThisRunCouldDecideIt())
        .isTrue();
  }
}
