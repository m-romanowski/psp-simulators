package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.marcinromanowski.pspsim.RecordedActions;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AnsweredFactView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AnsweredRowView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AnsweredValueView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlError;
import dev.marcinromanowski.pspsim.control.ControlPayloads.DeclaredFieldView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.DeclaredReadView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ReadAnswerResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ReadsResponse;
import dev.marcinromanowski.pspsim.control.TheReadsAProviderPublishes.Answer;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.AnsweredValue;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.Fact;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ReadControl;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import dev.marcinromanowski.pspsim.kernel.spi.TheTrailRowAReadLeaves;
import dev.marcinromanowski.pspsim.kernel.spi.WhereAFieldsValuesComeFrom;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TheReadsAProviderPublishesTest {

  private static final String PROVIDER = "aprovider";

  private static final String ANOTHER_PROVIDER = "anotherprovider";

  private static final String RUN = "a-named-run";

  private static final String ACCOUNT = "merchant-1";

  private static final String THE_RETRACE = "theRetraceOfACharge";

  private static final String THE_CARD = "cardToken";

  private static final String THE_TICKET = "ticket";

  private static final String THE_GROUP = "group";

  private static final String WHAT_IT_MEANS_THAT_NOTHING_WAS_FOUND =
      "this provider holds no charge under those identifiers, which is its answer and not a"
          + " failed read";

  private static final String WHAT_THIS_ONE_PUBLISHES_INSTEAD =
      "this provider module publishes no read of its own and lists what it holds on the"
          + " Payments screen instead";

  private static final String THE_QUESTION_IT_ANSWERS =
      "a merchant whose own call never answered asks this provider this one.";

  private static final AFieldATesterFillsIn THE_CARD_FIELD =
      AFieldATesterFillsIn.theValuesThisTestRunHasAlreadyProduced(
          THE_CARD, "the saved card the charge was taken on", true, "the cards this run saved.");

  private static final AFieldATesterFillsIn THE_TICKET_FIELD =
      AFieldATesterFillsIn.typed(
          THE_TICKET, "your own identifier for the charge", true, "the one you sent.", "1001");

  private static final AFieldATesterFillsIn THE_GROUP_FIELD =
      AFieldATesterFillsIn.typed(
          THE_GROUP, "your own identifier for the group", false, "the group it belonged to.", null);

  private static final ProviderReads THE_ONE_IT_PUBLISHES =
      ProviderReads.theOnesItPublishes(
          ProviderId.of(PROVIDER),
          List.of(
              new DeclaredRead(
                  THE_RETRACE,
                  "Find out whether a charge went through",
                  THE_QUESTION_IT_ANSWERS,
                  DeclaredRead.EmptyAnswer.whatItMeans(WHAT_IT_MEANS_THAT_NOTHING_WAS_FOUND),
                  List.of(THE_CARD_FIELD, THE_TICKET_FIELD, THE_GROUP_FIELD))));

  private static final ProviderReads NONE_OF_THEM =
      ProviderReads.noneOfThem(ProviderId.of(PROVIDER), WHAT_THIS_ONE_PUBLISHES_INSTEAD);

  @Test
  void a_suite_discovers_every_question_a_provider_publishes_with_the_words_it_describes_it_by() {
    Answer answered = published(aModulePublishing(THE_ONE_IT_PUBLISHES), null, null, null);

    assertThat(answered.status()).isEqualTo(200);
    ReadsResponse body = readsIn(answered);
    assertThat(body.testRunId()).isEqualTo("default");
    assertThat(body.provider()).isEqualTo(PROVIDER);
    assertThat(body.whatThisProviderPublishesInstead()).isNull();
    assertThat(body.reads()).hasSize(1);
    DeclaredReadView read = body.reads().getFirst();
    assertThat(read.readNamed()).isEqualTo(THE_RETRACE);
    assertThat(read.label()).isEqualTo("Find out whether a charge went through");
    assertThat(read.theQuestionItAnswers()).isEqualTo(THE_QUESTION_IT_ANSWERS);
    assertThat(read.whatAnEmptyAnswerWouldMean()).isEqualTo(WHAT_IT_MEANS_THAT_NOTHING_WAS_FOUND);
    assertThat(read.itNeverAnswersNothingAtAll()).isFalse();
  }

  @Test
  void the_fields_a_read_asks_for_are_answered_in_the_order_the_provider_declared_them() {
    Answer answered = published(aModulePublishing(THE_ONE_IT_PUBLISHES), RUN, null, null);

    assertThat(readsIn(answered).reads().getFirst().fields())
        .extracting(DeclaredFieldView::key)
        .containsExactly(THE_CARD, THE_TICKET, THE_GROUP);
  }

  @Test
  void a_field_is_answered_with_its_own_sentence_and_the_query_parameter_a_value_is_sent_in() {
    Answer answered = published(aModulePublishing(THE_ONE_IT_PUBLISHES), RUN, null, null);

    DeclaredFieldView ticket = fieldOf(answered, THE_TICKET);
    assertThat(ticket.label()).isEqualTo("your own identifier for the charge");
    assertThat(ticket.whatItIsFor()).isEqualTo("the one you sent.");
    assertThat(ticket.required()).isTrue();
    assertThat(ticket.theValueItSuggests()).isEqualTo("1001");
    assertThat(ticket.theQueryParameterItIsSentIn()).isEqualTo("field=ticket=<value>");
    assertThat(ticket.whereItsValuesComeFrom())
        .isEqualTo(WhereAFieldsValuesComeFrom.WHATEVER_THE_TESTER_TYPES);
    assertThat(fieldOf(answered, THE_GROUP).required()).isFalse();
    assertThat(fieldOf(answered, THE_GROUP).theValueItSuggests()).isNull();
  }

  @Test
  void a_field_whose_values_this_run_produced_is_answered_with_them_once_an_account_is_named() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES)
            .havingAlreadyProduced(
                THE_CARD, List.of(new AValueAFieldOffers("tok_1", "the card saved at noon")));

    Answer withoutAnAccount = published(module, RUN, null, null);
    Answer withOne = published(module, RUN, ACCOUNT, THE_RETRACE);

    assertThat(fieldOf(withoutAnAccount, THE_CARD).theValuesItOffers())
        .as("a run's own values are resolved for the account and the read a caller named")
        .isEmpty();
    assertThat(readsIn(withoutAnAccount).accountId()).isNull();
    assertThat(fieldOf(withOne, THE_CARD).theValuesItOffers())
        .extracting(offered -> offered.value() + " (" + offered.whatItNames() + ")")
        .containsExactly("tok_1 (the card saved at noon)");
    assertThat(readsIn(withOne).accountId()).isEqualTo(ACCOUNT);
    assertThat(fieldOf(withOne, THE_CARD).whereItsValuesComeFrom())
        .isEqualTo(WhereAFieldsValuesComeFrom.THE_VALUES_THIS_TEST_RUN_HAS_ALREADY_PRODUCED);
  }

  @Test
  void a_provider_that_publishes_no_read_says_so_in_its_own_words_and_is_not_an_error() {
    Answer answered = published(aModulePublishing(NONE_OF_THEM), RUN, null, null);

    assertThat(answered.status()).isEqualTo(200);
    assertThat(readsIn(answered).reads()).isEmpty();
    assertThat(readsIn(answered).whatThisProviderPublishesInstead())
        .isEqualTo(WHAT_THIS_ONE_PUBLISHES_INSTEAD);
  }

  @Test
  void a_provider_this_simulator_does_not_know_is_answered_four_hundred_and_four_on_both_routes() {
    TheReadsAProviderPublishes reads = reads(aModulePublishing(THE_ONE_IT_PUBLISHES));

    Answer discovery = reads.whatThisProviderPublishes("nosuchprovider", RUN, null, null);
    Answer performing = reads.theAnswerTo("nosuchprovider", RUN, ACCOUNT, THE_RETRACE, List.of());

    assertThat(discovery.status()).isEqualTo(404);
    assertThat(performing.status()).isEqualTo(404);
    assertThat(messageIn(discovery)).contains(PROVIDER).contains("unknown provider");
    assertThat(messageIn(performing)).isEqualTo(messageIn(discovery));
  }

  @Test
  void a_test_run_id_nothing_can_read_is_answered_four_hundred_on_both_routes() {
    TheReadsAProviderPublishes reads = reads(aModulePublishing(THE_ONE_IT_PUBLISHES));

    Answer discovery = reads.whatThisProviderPublishes(PROVIDER, "not a run id", null, null);
    Answer performing =
        reads.theAnswerTo(PROVIDER, "not a run id", ACCOUNT, THE_RETRACE, List.of());

    assertThat(discovery.status()).isEqualTo(400);
    assertThat(performing.status()).isEqualTo(400);
    assertThat(messageIn(discovery)).contains("testRunId");
    assertThat(messageIn(performing)).contains("testRunId");
  }

  @Test
  void a_read_answers_the_rows_the_provider_answered_and_the_facts_under_their_own_names() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES).answering(theChargeItHolds());

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    assertThat(answered.status()).isEqualTo(200);
    ReadAnswerResponse body = answerIn(answered);
    assertThat(body.testRunId()).isEqualTo(RUN);
    assertThat(body.provider()).isEqualTo(PROVIDER);
    assertThat(body.accountId()).isEqualTo(ACCOUNT);
    assertThat(body.readNamed()).isEqualTo(THE_RETRACE);
    assertThat(body.whatItMeansThatItAnsweredNothingAtAll()).isNull();
    assertThat(body.theRuleThatDecidedIt()).isEqualTo("scn_1");
    assertThat(body.rows()).hasSize(1);
    AnsweredRowView row = body.rows().getFirst();
    assertThat(row.providerObjectId()).isEqualTo("txn_1");
    assertThat(row.facts())
        .extracting(AnsweredFactView::asTheProviderNamesIt)
        .containsExactly("status", "settledAt", "card", "errors", "cardsOnFile", "history");
  }

  @Test
  void a_value_a_provider_answered_and_the_absence_of_one_are_not_the_same_answer() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES).answering(theChargeItHolds());

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    AnsweredValueView status = factOf(answered, "status");
    assertThat(status.theValueItAnswered()).isEqualTo("APPROVED");
    assertThat(status.itAnsweredNoValueAtAll()).isFalse();
    assertThat(status.theMembersUnderIt()).isNull();
    assertThat(status.theElementsUnderIt()).isNull();
    AnsweredValueView settledAt = factOf(answered, "settledAt");
    assertThat(settledAt.itAnsweredNoValueAtAll()).isTrue();
    assertThat(settledAt.theValueItAnswered()).isNull();
  }

  @Test
  void the_members_under_a_fact_are_answered_under_their_own_names_and_not_flattened() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES).answering(theChargeItHolds());

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    AnsweredValueView card = factOf(answered, "card");
    assertThat(card.theValueItAnswered()).isNull();
    assertThat(card.theElementsUnderIt()).isNull();
    assertThat(card.theMembersUnderIt())
        .extracting(
            member -> member.asTheProviderNamesIt() + "=" + member.value().theValueItAnswered())
        .containsExactly("brand=VISA", "last4=4242");
  }

  @Test
  void the_elements_under_a_fact_keep_their_order_and_carry_the_members_under_each_of_them() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES).answering(theChargeItHolds());

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    assertThat(factOf(answered, "cardsOnFile").theElementsUnderIt())
        .extracting(AnsweredValueView::theValueItAnswered)
        .containsExactly("tok_1", "tok_2");
    List<AnsweredValueView> history = factOf(answered, "history").theElementsUnderIt();
    assertThat(history).hasSize(1);
    assertThat(history.getFirst().theMembersUnderIt())
        .extracting(AnsweredFactView::asTheProviderNamesIt)
        .containsExactly("step");
  }

  @Test
  void a_list_answered_empty_and_an_object_with_no_member_are_told_apart_from_one_another() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES)
            .answering(
                new ReadOutcome.Answered(
                    List.of(
                        new AnsweredRow(
                            "txn_1",
                            List.of(
                                new Fact("errors", new AnsweredValue.TheElementsUnderIt(List.of())),
                                new Fact(
                                    "card", new AnsweredValue.TheMembersUnderIt(List.of())))))));

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    assertThat(factOf(answered, "errors").theElementsUnderIt()).isEmpty();
    assertThat(factOf(answered, "errors").theMembersUnderIt()).isNull();
    assertThat(factOf(answered, "card").theMembersUnderIt()).isEmpty();
    assertThat(factOf(answered, "card").theElementsUnderIt()).isNull();
  }

  @Test
  void what_a_provider_answered_about_the_query_is_answered_beside_the_rows_and_not_among_them() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES)
            .answering(
                new ReadOutcome.Answered(
                    List.of(), List.of(new Fact("page", "1"), new Fact("hasMore", "false")), null));

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    assertThat(answered.status()).isEqualTo(200);
    assertThat(answerIn(answered).rows()).isEmpty();
    assertThat(answerIn(answered).whatItAnsweredAboutTheQuery())
        .extracting(fact -> fact.asTheProviderNamesIt() + "=" + fact.value().theValueItAnswered())
        .containsExactly("page=1", "hasMore=false");
  }

  @Test
  void a_read_that_found_nothing_is_answered_two_hundred_with_what_the_provider_says_that_means() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    assertThat(answered.status())
        .as("a provider that holds no such object answered the question rather than failing it")
        .isEqualTo(200);
    assertThat(answerIn(answered).rows()).isEmpty();
    assertThat(answerIn(answered).whatItMeansThatItAnsweredNothingAtAll())
        .isEqualTo(WHAT_IT_MEANS_THAT_NOTHING_WAS_FOUND);
  }

  @Test
  void a_read_that_never_answers_nothing_says_so_rather_than_what_an_empty_answer_would_mean() {
    ProviderReads neverEmpty =
        ProviderReads.theOnesItPublishes(
            ProviderId.of(PROVIDER),
            List.of(
                new DeclaredRead(
                    THE_RETRACE,
                    "Read one payment back",
                    THE_QUESTION_IT_ANSWERS,
                    DeclaredRead.EmptyAnswer.itNeverAnswersNothingBecause(
                        "this provider answers the whole payment or refuses the call"),
                    List.of(THE_TICKET_FIELD))));

    Answer answered = published(aModulePublishing(neverEmpty), RUN, null, null);

    DeclaredReadView read = readsIn(answered).reads().getFirst();
    assertThat(read.itNeverAnswersNothingAtAll()).isTrue();
    assertThat(read.whatAnEmptyAnswerWouldMean())
        .isEqualTo("this provider answers the whole payment or refuses the call");
  }

  @Test
  void the_values_a_caller_sent_reach_the_module_under_the_keys_the_provider_reads_them_under() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    answerTo(module, "ticket=1001", "cardToken=tok_1", "group=");

    assertThat(module.theReadsItWasAsked()).hasSize(1);
    ReadRequest asked = module.theReadsItWasAsked().getFirst();
    assertThat(asked.accountId()).isEqualTo(ACCOUNT);
    assertThat(asked.readNamed()).isEqualTo(THE_RETRACE);
    assertThat(asked.askedBy()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(asked.values())
        .as(
            "a read is asked with the fields in the order it declares them, and a blank is no value")
        .containsExactly(Map.entry(THE_CARD, "tok_1"), Map.entry(THE_TICKET, "1001"));
  }

  @Test
  void a_field_sent_without_the_key_it_belongs_to_is_refused_before_the_module_is_asked() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    Answer answered = answerTo(module, "cardToken tok_1");

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered))
        .isEqualTo(
            TheReadsAProviderPublishes.A_FIELD_CARRIES_THE_KEY_IT_IS_SENT_FOR_AND_THE_VALUE_AFTER_IT
                + "cardToken tok_1");
    assertThat(module.theReadsItWasAsked()).isEmpty();
  }

  @Test
  void a_field_that_names_no_key_at_all_is_refused_before_the_module_is_asked() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    Answer answered = answerTo(module, " =tok_1");

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered))
        .isEqualTo(
            TheFieldParameterBothRoutesRead.A_FIELD_NAMES_THE_KEY_IT_IS_SENT_FOR + " =tok_1");
    assertThat(module.theReadsItWasAsked()).isEmpty();

    Answer nothingBeforeTheSeparator = answerTo(aModulePublishing(THE_ONE_IT_PUBLISHES), "=tok_1");

    assertThat(nothingBeforeTheSeparator.status()).isEqualTo(400);
    assertThat(messageIn(nothingBeforeTheSeparator))
        .as(
            "a pair whose separator stands first carries a value and no key, which is a field that"
                + " names nothing rather than a pair with no separator at all, and a tester reads"
                + " which of the two they wrote")
        .isEqualTo(TheFieldParameterBothRoutesRead.A_FIELD_NAMES_THE_KEY_IT_IS_SENT_FOR + "=tok_1");
  }

  @Test
  void one_field_sent_twice_is_refused_rather_than_one_of_the_two_values_being_dropped() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    Answer answered = answerTo(module, "ticket=1001", "cardToken=tok_1", "ticket=1002");

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered))
        .isEqualTo(
            TheReadsAProviderPublishes.ONE_READ_ASKS_EACH_OF_ITS_FIELDS_ONCE
                + THE_TICKET
                + TheFieldParameterBothRoutesRead.AND_ONE_OF_THEM_WOULD_BE_DROPPED);
    assertThat(module.theReadsItWasAsked()).isEmpty();
  }

  @Test
  void a_value_carrying_an_equals_sign_of_its_own_reaches_the_module_whole() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    answerTo(module, "cardToken=tok_1", "ticket=a=b=c");

    assertThat(module.theReadsItWasAsked().getFirst().values()).containsEntry(THE_TICKET, "a=b=c");
  }

  @Test
  void a_read_asked_without_the_field_it_cannot_be_answered_without_is_refused_in_its_own_words() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    Answer answered = answerTo(module, "cardToken=tok_1");

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered))
        .contains("your own identifier for the charge")
        .contains("the one you sent.");
    assertThat(module.theReadsItWasAsked()).isEmpty();
  }

  @Test
  void a_field_no_read_of_this_provider_asks_for_is_refused_rather_than_dropped() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    Answer answered = answerTo(module, "cardToken=tok_1", "invented=1");

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered)).contains("invented").contains(THE_TICKET);
    assertThat(module.theReadsItWasAsked()).isEmpty();
  }

  @Test
  void a_read_asked_on_no_account_is_refused_because_nothing_could_sign_the_call() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    Answer answered =
        reads(module).theAnswerTo(PROVIDER, RUN, null, THE_RETRACE, List.of("ticket=1001"));

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered)).isEqualTo(ReadControl.AN_ACCOUNT_IS_REQUIRED);
    assertThat(module.theReadsItWasAsked()).isEmpty();
  }

  @Test
  void a_question_this_provider_does_not_publish_is_refused_naming_the_ones_it_does() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    Answer answered = reads(module).theAnswerTo(PROVIDER, RUN, ACCOUNT, "somethingElse", List.of());

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered)).contains(THE_RETRACE);
    assertThat(module.theReadsItWasAsked()).isEmpty();
  }

  @Test
  void a_read_asked_without_naming_one_at_all_is_refused_naming_the_questions_it_could_name() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(THE_ONE_IT_PUBLISHES);

    Answer answered = reads(module).theAnswerTo(PROVIDER, RUN, ACCOUNT, null, List.of());

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered)).contains(THE_RETRACE);
    assertThat(module.theReadsItWasAsked()).isEmpty();
  }

  @Test
  void a_field_whose_values_the_provider_declares_is_answered_with_each_of_them_and_its_label() {
    ProviderReads askingForOneOfTwoValues =
        ProviderReads.theOnesItPublishes(
            ProviderId.of(PROVIDER),
            List.of(
                new DeclaredRead(
                    THE_RETRACE,
                    "Find the charges of one kind",
                    THE_QUESTION_IT_ANSWERS,
                    DeclaredRead.EmptyAnswer.whatItMeans(WHAT_IT_MEANS_THAT_NOTHING_WAS_FOUND),
                    List.of(
                        AFieldATesterFillsIn.chosenFrom(
                            "kind",
                            "the kind of charge",
                            true,
                            "the kinds this provider names.",
                            List.of(
                                new AValueAFieldOffers("SALE", "a charge taken in one call"),
                                new AValueAFieldOffers("PREAUTH", "a reservation")))))));

    Answer answered = published(aModulePublishing(askingForOneOfTwoValues), RUN, null, null);

    DeclaredFieldView kind = fieldOf(answered, "kind");
    assertThat(kind.whereItsValuesComeFrom())
        .isEqualTo(WhereAFieldsValuesComeFrom.THE_VALUES_THE_PROVIDER_DECLARES);
    assertThat(kind.theValuesItOffers())
        .extracting(offered -> offered.value() + " (" + offered.whatItNames() + ")")
        .containsExactly("SALE (a charge taken in one call)", "PREAUTH (a reservation)");
  }

  @Test
  void a_provider_that_publishes_no_read_refuses_one_in_its_own_words_and_not_in_the_cores() {
    AProviderModuleAReadIsAskedOf module = aModulePublishing(NONE_OF_THEM);

    Answer answered = reads(module).theAnswerTo(PROVIDER, RUN, ACCOUNT, THE_RETRACE, List.of());

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered)).isEqualTo(WHAT_THIS_ONE_PUBLISHES_INSTEAD);
    assertThat(module.theReadsItWasAsked()).isEmpty();
  }

  @Test
  void a_provider_that_refused_the_read_is_answered_with_its_own_refusal_and_not_with_rows() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES)
            .answering(
                new ReadOutcome.RefusedByTheProvider(
                    "err.id 140: this business never saved that card", "scn_2"));

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    assertThat(answered.status()).isEqualTo(422);
    assertThat(messageIn(answered)).isEqualTo("err.id 140: this business never saved that card");
  }

  @Test
  void a_rule_of_this_run_that_answered_instead_of_the_provider_is_named_in_the_refusal() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES)
            .answering(new ReadOutcome.AnsweredByAScenarioInstead("scn_3"));

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    assertThat(answered.status()).isEqualTo(422);
    assertThat(messageIn(answered))
        .as(
            "the rule that answered is named in the same words the row of this call carries, and"
                + " this route adds what it does with a rule's answer, which is its own and not"
                + " the row's")
        .isEqualTo(
            TheReadsAProviderPublishes.A_RULE_OF_THIS_RUN_ANSWERED_IT_INSTEAD
                + "scn_3"
                + TheReadsAProviderPublishes.AND_THIS_ROUTE_ANSWERS_THE_ROWS_A_PROVIDER_ANSWERED);
  }

  @Test
  void an_account_this_test_run_does_not_configure_is_answered_four_hundred_and_four() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES).answering(new ReadOutcome.NoSuchAccount());

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    assertThat(answered.status()).isEqualTo(404);
    assertThat(messageIn(answered))
        .isEqualTo(
            "no account with that accountId in test run " + RUN + " of provider " + PROVIDER);
  }

  @Test
  void a_module_that_answers_no_read_of_this_kind_is_reported_in_the_words_it_answered_with() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES)
            .answering(new ReadOutcome.NotOffered("this module answers no read of that kind"));

    Answer answered = answerTo(module, "cardToken=tok_1", "ticket=1001");

    assertThat(answered.status()).isEqualTo(400);
    assertThat(messageIn(answered)).isEqualTo("this module answers no read of that kind");
  }

  @Test
  void one_providers_reads_are_never_answered_for_another_of_the_same_simulator() {
    TheReadsAProviderPublishes reads =
        new TheReadsAProviderPublishes(
            new ProviderRegistry(
                List.of(
                    aModulePublishing(THE_ONE_IT_PUBLISHES),
                    new AProviderModuleAReadIsAskedOf(ANOTHER_PROVIDER)
                        .publishing(
                            ProviderReads.noneOfThem(
                                ProviderId.of(ANOTHER_PROVIDER),
                                WHAT_THIS_ONE_PUBLISHES_INSTEAD)))),
            new RecordedActions().asTrail(),
            "default");

    Answer mine = reads.whatThisProviderPublishes(PROVIDER, RUN, null, null);
    Answer theirs = reads.whatThisProviderPublishes(ANOTHER_PROVIDER, RUN, null, null);

    assertThat(readsIn(mine).reads()).hasSize(1);
    assertThat(readsIn(theirs).reads()).isEmpty();
    assertThat(readsIn(theirs).provider()).isEqualTo(ANOTHER_PROVIDER);
  }

  @Test
  void a_read_nothing_was_sent_for_is_asked_for_with_no_value_at_all_rather_than_refused_here() {
    ProviderReads askingForNothingRequired =
        ProviderReads.theOnesItPublishes(
            ProviderId.of(PROVIDER),
            List.of(
                new DeclaredRead(
                    THE_RETRACE,
                    "Find the charges of one business",
                    THE_QUESTION_IT_ANSWERS,
                    DeclaredRead.EmptyAnswer.whatItMeans(WHAT_IT_MEANS_THAT_NOTHING_WAS_FOUND),
                    List.of(THE_GROUP_FIELD))));
    AProviderModuleAReadIsAskedOf module = aModulePublishing(askingForNothingRequired);

    Answer answered = reads(module).theAnswerTo(PROVIDER, RUN, ACCOUNT, THE_RETRACE, null);

    assertThat(answered.status()).isEqualTo(200);
    assertThat(module.theReadsItWasAsked().getFirst().values()).isEmpty();
  }

  @Test
  void the_grammar_of_a_sent_field_is_the_cores_own_and_names_no_field_of_any_provider() {
    assertThatCode(
            () ->
                TheFieldParameterBothRoutesRead.theValuesItWasSent(
                    List.of(), TheReadsAProviderPublishes.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .as("the core reads a key and a value and knows the meaning of neither")
        .doesNotThrowAnyException();
    assertThat(
            TheFieldParameterBothRoutesRead.theValuesItWasSent(
                List.of("anything=at all"),
                TheReadsAProviderPublishes.THE_WORDS_THIS_ROUTE_TAKES_ITS_VALUES_IN))
        .containsExactly(Map.entry("anything", "at all"));
  }

  @Test
  void an_answered_read_is_written_to_the_trail_as_an_action_of_the_control_api() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES).answering(theChargeItHolds());
    RecordedActions trail = new RecordedActions();

    Answer answered =
        reads(module, trail)
            .theAnswerTo(
                PROVIDER, RUN, ACCOUNT, THE_RETRACE, List.of("cardToken=tok_1", "ticket=1001"));

    assertThat(trail.written())
        .as(
            "a read reaches the provider's own operation, where a rule of this test run can"
                + " answer it and move the story it stands on, so the call a suite made is a"
                + " thing that happened to this run and not a question it asked of nothing")
        .hasSize(1);
    AuditRecord written = trail.written().getFirst();
    assertThat(written.provider()).isEqualTo(ProviderId.of(PROVIDER));
    assertThat(written.testRunId()).isEqualTo(TestRunId.of(RUN));
    assertThat(written.origin()).isEqualTo(ObjectOrigin.CONTROL_API);
    assertThat(written.method()).isEqualTo("GET");
    assertThat(written.path()).isEqualTo("/__simulator/" + PROVIDER + "/reads/answer");
    assertThat(written.httpStatus())
        .as("the row carries the status the route answered the same call with")
        .isEqualTo(answered.status());
    assertThat(written.accountId()).isEqualTo(ACCOUNT);
    assertThat(written.scenarioId()).isEqualTo("scn_1");
    assertThat(written.providerObjectId()).isEqualTo("txn_1");
    assertThat(written.sanitizedBody())
        .containsEntry(TheTrailRowAReadLeaves.READ_NAMED, List.of(THE_RETRACE))
        .containsEntry(THE_CARD, List.of("tok_1"))
        .containsEntry(TheTrailRowAReadLeaves.ROWS_ANSWERED, List.of("1"));
  }

  @Test
  void a_read_a_provider_refused_is_written_to_the_trail_with_the_status_the_route_answered() {
    AProviderModuleAReadIsAskedOf module =
        aModulePublishing(THE_ONE_IT_PUBLISHES)
            .answering(new ReadOutcome.RefusedByTheProvider("err.id 140", "scn_9"));
    RecordedActions trail = new RecordedActions();

    Answer refused =
        reads(module, trail)
            .theAnswerTo(
                PROVIDER, RUN, ACCOUNT, THE_RETRACE, List.of("cardToken=tok_1", "ticket=1001"));

    AuditRecord written = trail.written().getFirst();
    assertThat(written.httpStatus()).isEqualTo(refused.status()).isEqualTo(422);
    assertThat(written.scenarioId()).isEqualTo("scn_9");
    assertThat(written.sanitizedBody())
        .containsEntry(TheTrailRowAReadLeaves.REFUSED_BECAUSE, List.of("err.id 140"));
  }

  @Test
  void a_question_this_core_could_not_read_is_written_to_the_trail_as_a_rejected_action() {
    RecordedActions trail = new RecordedActions();

    Answer refused =
        reads(aModulePublishing(THE_ONE_IT_PUBLISHES), trail)
            .theAnswerTo(PROVIDER, RUN, ACCOUNT, THE_RETRACE, List.of("invented=1"));

    AuditRecord written = trail.written().getFirst();
    assertThat(written.httpStatus()).isEqualTo(refused.status()).isEqualTo(400);
    assertThat(written.accountId()).isEqualTo(ACCOUNT);
    assertThat(written.sanitizedBody())
        .containsEntry(TheTrailRowAReadLeaves.READ_NAMED, List.of(THE_RETRACE))
        .hasEntrySatisfying(
            TheTrailRowAReadLeaves.REFUSED_BECAUSE,
            refusal -> assertThat(refusal.getFirst()).contains("invented"));
  }

  @Test
  void a_question_asked_under_a_run_this_simulator_cannot_read_is_written_under_the_reserved_run() {
    RecordedActions trail = new RecordedActions();

    reads(aModulePublishing(THE_ONE_IT_PUBLISHES), trail)
        .theAnswerTo(
            PROVIDER, "a run no namespace can hold", ACCOUNT, THE_RETRACE, List.of("cardToken=1"));

    assertThat(trail.written()).hasSize(1);
    assertThat(trail.written().getFirst().testRunId()).isEqualTo(TestRunId.UNKNOWN);
  }

  @Test
  void a_question_asked_of_a_provider_publishing_none_is_written_to_the_trail_all_the_same() {
    RecordedActions trail = new RecordedActions();

    Answer refused =
        reads(aModulePublishing(NONE_OF_THEM), trail)
            .theAnswerTo(PROVIDER, RUN, ACCOUNT, THE_RETRACE, null);

    AuditRecord written = trail.written().getFirst();
    assertThat(written.httpStatus()).isEqualTo(refused.status()).isEqualTo(400);
    assertThat(written.sanitizedBody())
        .containsEntry(
            TheTrailRowAReadLeaves.REFUSED_BECAUSE, List.of(WHAT_THIS_ONE_PUBLISHES_INSTEAD));
  }

  @Test
  void a_discovery_of_the_questions_a_provider_publishes_writes_nothing_to_the_trail() {
    RecordedActions trail = new RecordedActions();

    reads(aModulePublishing(THE_ONE_IT_PUBLISHES), trail)
        .whatThisProviderPublishes(PROVIDER, RUN, ACCOUNT, THE_RETRACE);

    assertThat(trail.written())
        .as("reading what a provider publishes moves nothing, so the trail records no action")
        .isEmpty();
  }

  @Test
  void a_question_asked_of_a_provider_this_build_does_not_carry_is_written_nowhere() {
    RecordedActions trail = new RecordedActions();

    reads(aModulePublishing(THE_ONE_IT_PUBLISHES), trail)
        .theAnswerTo(ANOTHER_PROVIDER, RUN, ACCOUNT, THE_RETRACE, null);

    assertThat(trail.written())
        .as("the trail files every row under the provider it happened on, and this one has none")
        .isEmpty();
  }

  private static ReadOutcome theChargeItHolds() {
    return new ReadOutcome.Answered(
        List.of(
            new AnsweredRow(
                "txn_1",
                List.of(
                    new Fact("status", "APPROVED"),
                    new Fact("settledAt", new AnsweredValue.NoValueAtAll()),
                    new Fact(
                        "card",
                        new AnsweredValue.TheMembersUnderIt(
                            List.of(new Fact("brand", "VISA"), new Fact("last4", "4242")))),
                    new Fact("errors", new AnsweredValue.TheElementsUnderIt(List.of())),
                    new Fact(
                        "cardsOnFile",
                        new AnsweredValue.TheElementsUnderIt(
                            List.of(
                                new AnsweredValue.TheValueItAnswered("tok_1"),
                                new AnsweredValue.TheValueItAnswered("tok_2")))),
                    new Fact(
                        "history",
                        new AnsweredValue.TheElementsUnderIt(
                            List.of(
                                new AnsweredValue.TheMembersUnderIt(
                                    List.of(new Fact("step", "AUTHORIZED"))))))))),
        "scn_1");
  }

  private static AProviderModuleAReadIsAskedOf aModulePublishing(ProviderReads reads) {
    return new AProviderModuleAReadIsAskedOf(PROVIDER).publishing(reads);
  }

  private static Answer published(
      AProviderModuleAReadIsAskedOf module,
      @Nullable String run,
      @Nullable String accountId,
      @Nullable String readNamed) {
    return reads(module).whatThisProviderPublishes(PROVIDER, run, accountId, readNamed);
  }

  private static TheReadsAProviderPublishes reads(AProviderModuleAReadIsAskedOf module) {
    return reads(module, new RecordedActions());
  }

  private static TheReadsAProviderPublishes reads(
      AProviderModuleAReadIsAskedOf module, RecordedActions trail) {
    return new TheReadsAProviderPublishes(
        new ProviderRegistry(List.of(module)), trail.asTrail(), "default");
  }

  private static Answer answerTo(AProviderModuleAReadIsAskedOf module, String... field) {
    return reads(module).theAnswerTo(PROVIDER, RUN, ACCOUNT, THE_RETRACE, List.of(field));
  }

  private static ReadsResponse readsIn(Answer answered) {
    return (ReadsResponse) answered.body();
  }

  private static ReadAnswerResponse answerIn(Answer answered) {
    return (ReadAnswerResponse) answered.body();
  }

  private static String messageIn(Answer answered) {
    return ((ControlError) answered.body()).message();
  }

  private static DeclaredFieldView fieldOf(Answer answered, String key) {
    return readsIn(answered).reads().getFirst().fields().stream()
        .filter(field -> field.key().equals(key))
        .findFirst()
        .orElseThrow();
  }

  private static AnsweredValueView factOf(Answer answered, String named) {
    return answerIn(answered).rows().getFirst().facts().stream()
        .filter(fact -> fact.asTheProviderNamesIt().equals(named))
        .findFirst()
        .orElseThrow()
        .value();
  }
}
