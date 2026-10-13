package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TheOrderOneAnswerIsInTest {

  private static ProviderObjectState anObject(String canonical, String... alsoAnsweringTo) {
    return anObjectReporting("1", canonical, alsoAnsweringTo);
  }

  private static ProviderObjectState anObjectReporting(
      String nativeStatus, String canonical, String... alsoAnsweringTo) {
    ProviderObjectIdentity identity = ProviderObjectIdentity.of(canonical);
    for (String further : alsoAnsweringTo) {
      identity = identity.answeringAlsoTo(further);
    }
    return new ProviderObjectState(
        identity,
        AKindOfObjectATestNames.ONE,
        nativeStatus,
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        Instant.EPOCH);
  }

  @Test
  void objects_read_in_the_order_their_stores_keep_them_are_answered_in_the_order_asked() {
    ProviderObjectState newest = anObject("pay-3");
    ProviderObjectState middle = anObject("pay-2");
    ProviderObjectState oldest = anObject("pay-1");

    List<ProviderObjectState> answer =
        TheOrderOneAnswerIsIn.theObjectsAskedFor(
            List.of("pay-1", "pay-2", "pay-3"), List.of(newest, middle, oldest));

    assertThat(answer)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(oldest, middle, newest);
  }

  @Test
  void an_object_the_run_holds_under_two_asked_identifiers_is_answered_once_at_the_earlier_one() {
    ProviderObjectState captured = anObject("pay-1", "pay-1-00002");
    ProviderObjectState other = anObject("pay-2");

    List<ProviderObjectState> answer =
        TheOrderOneAnswerIsIn.theObjectsAskedFor(
            List.of("pay-1", "pay-2", "pay-1-00002"), List.of(captured, captured, other));

    assertThat(answer)
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(captured, other);
  }

  @Test
  void one_identifier_asked_for_twice_answers_its_object_once_where_it_was_first_asked() {
    ProviderObjectState first = anObject("pay-1");
    ProviderObjectState second = anObject("pay-2");

    List<ProviderObjectState> answer =
        TheOrderOneAnswerIsIn.theObjectsAskedFor(
            List.of("pay-2", "pay-1", "pay-2"), List.of(first, second));

    assertThat(answer).containsExactly(second, first);
  }

  @Test
  void two_objects_answering_to_one_asked_identifier_keep_the_order_the_provider_read_them_in() {
    ProviderObjectState theOneASingleReadAnswers =
        anObjectReporting("the store this provider reads first", "one-id-two-stores-hold");
    ProviderObjectState theOneBehindIt =
        anObjectReporting("the store it reads after that", "one-id-two-stores-hold");

    List<ProviderObjectState> answer =
        TheOrderOneAnswerIsIn.theObjectsAskedFor(
            List.of("one-id-two-stores-hold"), List.of(theOneASingleReadAnswers, theOneBehindIt));

    assertThat(answer)
        .as(
            "a provider that holds one string for two of its objects answers both of them at the"
                + " place that string was asked, and the one its own read resolves first is first,"
                + " so the label a tester reads for that string is the label a single read of it"
                + " gives")
        .containsExactly(theOneASingleReadAnswers, theOneBehindIt);
  }

  @Test
  void an_identifier_a_second_object_answers_to_is_answered_whichever_way_round_it_is_asked() {
    ProviderObjectState answeringBoth = anObject("pay-1", "one-id-two-stores-hold");
    ProviderObjectState theOneNamedOnlyByTheShared = anObject("one-id-two-stores-hold");
    List<ProviderObjectState> read = List.of(answeringBoth, theOneNamedOnlyByTheShared);

    assertThat(
            TheOrderOneAnswerIsIn.theObjectsAskedFor(
                List.of("pay-1", "one-id-two-stores-hold"), read))
        .as(
            "the same two objects asked for under the same two identifiers answer the same two"
                + " objects whichever of the identifiers comes first, so no orientation of the"
                + " asked list turns an answer this kernel can order into one it cannot")
        .containsExactly(answeringBoth, theOneNamedOnlyByTheShared);
    assertThat(
            TheOrderOneAnswerIsIn.theObjectsAskedFor(
                List.of("one-id-two-stores-hold", "pay-1"), read))
        .as(TheOrderOneAnswerIsIn.ONE_ANSWER_IS_IN_THE_ORDER_IT_WAS_ASKED_IN)
        .containsExactly(answeringBoth, theOneNamedOnlyByTheShared);
  }

  @Test
  void an_identifier_only_an_already_placed_object_answers_to_adds_nothing_further() {
    ProviderObjectState captured = anObject("pay-1", "pay-1-00002");

    assertThat(
            TheOrderOneAnswerIsIn.theObjectsAskedFor(
                List.of("pay-1", "pay-1-00002"), List.of(captured)))
        .containsExactly(captured);
  }

  @Test
  void an_object_already_read_answers_to_the_rest_of_its_names_and_one_it_does_not_hold() {
    ProviderObjectState captured = anObject("pay-1", "pay-1-00002");

    assertThat(TheOrderOneAnswerIsIn.oneOfTheseAlreadyAnswersTo(List.of(captured), "pay-1-00002"))
        .as(TheOrderOneAnswerIsIn.ONE_OBJECT_IS_READ_ONCE_HOWEVER_MANY_OF_ITS_NAMES_ARE_ASKED)
        .isTrue();
    assertThat(TheOrderOneAnswerIsIn.oneOfTheseAlreadyAnswersTo(List.of(captured), "pay-1"))
        .isTrue();
    assertThat(TheOrderOneAnswerIsIn.oneOfTheseAlreadyAnswersTo(List.of(captured), "pay-2"))
        .isFalse();
    assertThat(TheOrderOneAnswerIsIn.oneOfTheseAlreadyAnswersTo(List.of(), "pay-1")).isFalse();
  }

  @Test
  void an_object_read_under_none_of_the_asked_identifiers_is_left_out_of_the_answer() {
    ProviderObjectState asked = anObject("pay-1");
    ProviderObjectState neverAsked = anObject("pay-9");

    assertThat(
            TheOrderOneAnswerIsIn.theObjectsAskedFor(List.of("pay-1"), List.of(asked, neverAsked)))
        .containsExactly(asked);
  }

  @Test
  void nothing_asked_for_and_nothing_read_answer_nothing() {
    assertThat(TheOrderOneAnswerIsIn.theObjectsAskedFor(List.of(), List.of(anObject("pay-1"))))
        .isEmpty();
    assertThat(TheOrderOneAnswerIsIn.theObjectsAskedFor(List.of("pay-1"), List.of())).isEmpty();
  }
}
