package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObjectActionTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");

  private static final TestRunId RUN = TestRunId.of("object-actions");

  private static final String NO_CANCEL = "this provider ends a hold by capturing then refunding";

  @Test
  void the_three_actions_a_payment_is_offered_are_the_three_this_simulator_knows() {
    assertThat(ObjectAction.declared("CAPTURE")).isEqualTo(ObjectAction.CAPTURE);
    assertThat(ObjectAction.declared("CANCEL")).isEqualTo(ObjectAction.CANCEL);
    assertThat(ObjectAction.declared("REFUND")).isEqualTo(ObjectAction.REFUND);
  }

  @ParameterizedTest(name = "an action named {0} is answered with the ones that exist")
  @ValueSource(strings = {"VOID", "capture", "FORCE_STATUS", ""})
  void an_action_no_surface_of_this_simulator_offers_is_answered_with_the_ones_it_does(
      String named) {
    assertThatThrownBy(() -> ObjectAction.declared(named))
        .hasMessageContaining("CAPTURE, CANCEL, REFUND");
  }

  @Test
  void a_capture_and_a_refund_name_an_amount_and_a_cancellation_names_none() {
    assertThat(ObjectAction.CAPTURE.takesAnAmount()).isTrue();
    assertThat(ObjectAction.REFUND.takesAnAmount()).isTrue();
    assertThat(ObjectAction.CANCEL.takesAnAmount()).isFalse();
  }

  @Test
  void a_cancel_and_a_refund_undo_money_already_taken_or_held_and_a_capture_does_not() {
    assertThat(ObjectAction.CAPTURE.undoesMoneyAlreadyTakenOrHeld()).isFalse();
    assertThat(ObjectAction.CANCEL.undoesMoneyAlreadyTakenOrHeld()).isTrue();
    assertThat(ObjectAction.REFUND.undoesMoneyAlreadyTakenOrHeld()).isTrue();
  }

  @Test
  void every_action_says_in_plain_words_what_a_tester_is_asking_the_provider_for() {
    for (ObjectAction action : ObjectAction.values()) {
      assertThat(action.asked()).isNotBlank();
    }
  }

  @Test
  void a_provider_that_publishes_all_three_operations_offers_all_three() {
    ObjectActions actions = new ObjectActions(PROVIDER, Map.of());

    for (ObjectAction action : ObjectAction.values()) {
      assertThat(actions.offers(action)).isTrue();
    }
  }

  @Test
  void a_provider_that_publishes_no_cancellation_says_what_its_contract_has_in_place_of_one() {
    ObjectActions actions = new ObjectActions(PROVIDER, Map.of(ObjectAction.CANCEL, NO_CANCEL));

    assertThat(actions.offers(ObjectAction.CANCEL)).isFalse();
    assertThat(actions.offers(ObjectAction.CAPTURE)).isTrue();
    assertThat(actions.whatItHasInsteadOf(ObjectAction.CANCEL)).isEqualTo(NO_CANCEL);
  }

  @ParameterizedTest(name = "an absence explained as {0} is refused where it is declared")
  @ValueSource(strings = {"", " "})
  void an_absence_a_provider_will_not_explain_is_refused_where_it_is_declared(String said) {
    assertThatThrownBy(() -> new ObjectActions(PROVIDER, Map.of(ObjectAction.REFUND, said)))
        .hasMessageContaining("grow")
        .hasMessageContaining(ObjectAction.REFUND.asked());
  }

  @Test
  void asking_what_stands_in_for_an_operation_a_provider_does_offer_is_a_programming_error() {
    ObjectActions actions = new ObjectActions(PROVIDER, Map.of());

    assertThatThrownBy(() -> actions.whatItHasInsteadOf(ObjectAction.CAPTURE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CAPTURE");
  }

  @Test
  void a_capture_a_tester_asked_for_from_a_screen_carries_the_amount_and_the_surface_that_asked() {
    ObjectActionRequest request =
        ObjectActionRequest.of(RUN, " trx_1 ", ObjectAction.CAPTURE, " 40.00 ", ObjectOrigin.UI);

    assertThat(request.providerObjectId()).isEqualTo("trx_1");
    assertThat(request.theAmountAsked()).isEqualTo(Amount.of("40.00"));
    assertThat(request.askedBy()).isEqualTo(ObjectOrigin.UI);
  }

  @Test
  void a_cancellation_carries_no_amount_and_asking_it_for_one_is_a_programming_error() {
    ObjectActionRequest request =
        ObjectActionRequest.of(RUN, "trx_1", ObjectAction.CANCEL, null, ObjectOrigin.CONTROL_API);

    assertThat(request.amount()).isNull();
    assertThatThrownBy(request::theAmountAsked).isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest(name = "a capture asked for with the amount {0} is refused")
  @ValueSource(strings = {"", " "})
  void a_capture_that_names_no_amount_is_refused_rather_than_guessed_at(String amount) {
    assertThatThrownBy(
            () ->
                ObjectActionRequest.of(RUN, "trx_1", ObjectAction.CAPTURE, amount, ObjectOrigin.UI))
        .hasMessage(ObjectActionRequest.AN_AMOUNT_IS_REQUIRED);
  }

  @Test
  void a_refund_that_names_no_amount_is_refused_rather_than_taken_in_full() {
    assertThatThrownBy(
            () -> ObjectActionRequest.of(RUN, "trx_1", ObjectAction.REFUND, null, ObjectOrigin.UI))
        .hasMessage(ObjectActionRequest.AN_AMOUNT_IS_REQUIRED);
  }

  @Test
  void a_cancellation_that_names_an_amount_is_refused_because_nothing_would_read_it() {
    assertThatThrownBy(
            () ->
                ObjectActionRequest.of(RUN, "trx_1", ObjectAction.CANCEL, "40.00", ObjectOrigin.UI))
        .hasMessageContaining(ObjectAction.CANCEL.asked());
  }

  @ParameterizedTest(name = "an action on the object named {0} names no object")
  @ValueSource(strings = {"", " "})
  void an_action_that_names_no_object_is_refused_by_the_property_that_broke_it(String named) {
    assertThatThrownBy(
            () ->
                ObjectActionRequest.of(
                    RUN, named, ObjectAction.CANCEL, null, ObjectOrigin.CONTROL_API))
        .hasMessageContaining("names none");
  }

  @Test
  void a_clients_own_request_is_never_asked_for_through_this_port() {
    assertThatThrownBy(
            () ->
                ObjectActionRequest.of(
                    RUN, "trx_1", ObjectAction.CANCEL, null, ObjectOrigin.PROVIDER_API))
        .hasMessageContaining("the provider's own route");
  }

  @Test
  void the_delivery_queue_moves_callbacks_and_never_money_so_it_asks_for_no_action() {
    assertThatThrownBy(
            () ->
                ObjectActionRequest.of(
                    RUN, "trx_1", ObjectAction.CANCEL, null, ObjectOrigin.SCHEDULED_JOB))
        .hasMessageContaining("never money");
  }

  @Test
  void a_refund_reports_the_object_it_brought_into_existence_beside_the_one_it_was_taken_from() {
    ObjectActionOutcome.Applied applied = new ObjectActionOutcome.Applied("trx_1", "trx_9");

    assertThat(applied.providerObjectId()).isEqualTo("trx_1");
    assertThat(applied.createdObjectId()).isEqualTo("trx_9");
  }

  @Test
  void a_capture_creates_no_second_object_and_reports_none() {
    assertThat(new ObjectActionOutcome.Applied("trx_1", null).createdObjectId()).isNull();
  }

  @Test
  void an_action_reporting_the_object_it_acted_on_as_a_new_one_is_refused_where_it_is_built() {
    assertThatThrownBy(() -> new ObjectActionOutcome.Applied("trx_1", "trx_1"))
        .hasMessageContaining("already looking at");
  }

  @ParameterizedTest(name = "a refusal worded {0} tells a tester nothing")
  @ValueSource(strings = {"", " "})
  void a_refusal_a_provider_will_not_word_is_refused_where_it_is_built(String words) {
    assertThatThrownBy(() -> new ObjectActionOutcome.RefusedByTheProvider(words))
        .hasMessageContaining("empty refusal");
  }

  @Test
  void an_operation_a_provider_does_not_publish_is_answered_with_that_providers_own_sentence() {
    ObjectActions actions = new ObjectActions(PROVIDER, Map.of(ObjectAction.CANCEL, NO_CANCEL));

    assertThat(ObjectActionOutcome.notOffered(actions, ObjectAction.CANCEL))
        .isEqualTo(new ObjectActionOutcome.NotOffered(NO_CANCEL));
  }
}
