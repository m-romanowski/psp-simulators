package dev.marcinromanowski.pspsim.ui;

import static dev.marcinromanowski.pspsim.TheObjectsAnObjectNames.theObjectsNamed;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.spi.AKindOfObjectATestNames;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectAction;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectKind;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectScreenTest {

  @Test
  void the_amount_a_refused_action_was_given_is_drawn_again_in_that_action_s_own_box() {
    ObjectScreen.Offered capture =
        new ObjectScreen.Offered("CAPTURE", "capture", true, "80.50", false);

    assertThat(capture.amountDrawnFrom(ObjectScreen.theAmountTypedFor("CAPTURE", "12.34")))
        .isEqualTo("12.34");
  }

  @Test
  void the_amount_typed_into_one_action_s_box_is_not_drawn_into_another_action_s() {
    ObjectScreen.Offered refund =
        new ObjectScreen.Offered("REFUND", "refund", true, "80.50", false);

    assertThat(refund.amountDrawnFrom(ObjectScreen.theAmountTypedFor("CAPTURE", "12.34")))
        .as(
            "one payment offers a box per action under one field name, so an amount carried onto"
                + " all of them would show a tester a figure they typed against another action")
        .isEqualTo("80.50");
  }

  @Test
  void an_action_box_nobody_has_typed_into_is_drawn_from_the_balance_it_suggests() {
    ObjectScreen.Offered capture =
        new ObjectScreen.Offered("CAPTURE", "capture", true, "80.50", false);

    assertThat(capture.amountDrawnFrom(WhatWasTyped.nothing())).isEqualTo("80.50");
    assertThat(capture.amountDrawnFrom(ObjectScreen.theAmountTypedFor("CAPTURE", null)))
        .isEqualTo("80.50");
  }

  @Test
  void an_action_no_form_named_carries_its_amount_under_a_name_no_action_answers_to() {
    ObjectScreen.Offered capture =
        new ObjectScreen.Offered("CAPTURE", "capture", true, "80.50", false);

    assertThat(capture.amountDrawnFrom(ObjectScreen.theAmountTypedFor(null, "12.34")))
        .as("a post naming no action names no box to draw that amount back into")
        .isEqualTo("80.50");
  }

  private static final Instant AT = Instant.parse("2026-09-07T12:00:00Z");
  private static final TestRunId RUN = TestRunId.of("ui-objects");

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final ObjectActions EVERY_ACTION = new ObjectActions(GROW, Map.of());

  private static final String NO_CANCEL =
      "provider grow publishes no cancel and no void for a card transaction";

  private static final ObjectActions NO_CANCELLATION =
      new ObjectActions(GROW, Map.of(ObjectAction.CANCEL, NO_CANCEL));

  private static final Instant CREATED = Instant.parse("2026-09-15T10:00:00Z");

  private static final Instant EXPIRES = Instant.parse("2026-09-15T13:00:00Z");

  private static final String NO_PARTY_ON_A_TOKENIZATION =
      "provider worldline publishes no customer object on the hosted tokenization routes";

  private static final String PROCESS = "proc_1";

  private static final List<FollowUpForm.Offered> NO_FOLLOW_UP = List.of();

  private static final List<FollowUpForm.Offered> THE_SETTLE_FOLLOW_UP =
      List.of(new FollowUpForm.Offered("settle", "settle (payout)", "AUTHORIZED to SETTLED"));

  private static final HostedCompletionForm.View A_PAYER_STEP_IS_PENDING =
      new HostedCompletionForm.View(
          "grow",
          "ui-objects",
          "trx_1",
          List.of(),
          List.of(),
          List.of(),
          null,
          null,
          "holds the money on the card the payer picks");

  private static final HostedCompletionForm.View NO_PAYER_STEP_IS_LEFT =
      new HostedCompletionForm.View(
          "grow",
          "ui-objects",
          "trx_1",
          List.of(),
          List.of(),
          List.of(),
          "this hosted process has already reached an outcome",
          null,
          null);

  private static final String NO_DEADLINE_ON_A_PROCESS =
      "provider grow publishes no deadline on a payment process at all";

  @Test
  void a_hold_the_provider_will_still_settle_is_offered_as_capturable() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.capturable()).isTrue();
    assertThat(view.refundable()).isFalse();
  }

  @Test
  void a_hold_the_provider_has_closed_is_not_offered_as_capturable_however_the_sums_add_up() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", false, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.capturable()).isFalse();
    assertThat(view.balances()).isNotNull();
    assertThat(view.balances().capturableRemainder()).isEqualTo("0");
  }

  @Test
  void a_hold_no_capture_can_take_is_not_offered_although_it_still_holds_what_it_never_captured() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldNoCaptureCanTake("120.50")),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.capturesAccepted()).isFalse();
    assertThat(view.capturable()).isFalse();
    assertThat(view.holdsMoneyNoCaptureCanTake()).isTrue();
    assertThat(view.balances().capturableRemainder()).isEqualTo("120.50");
    assertThat(view.offered())
        .extracting(ObjectScreen.Offered::action)
        .doesNotContain(ObjectAction.CAPTURE.name());
  }

  @Test
  void a_payment_whose_payer_has_not_finished_is_one_the_payer_step_alone_still_moves() {
    ObjectScreen.WhatStillMovesIt left =
        ObjectScreen.whatStillMovesIt(aClosedHold(), NO_FOLLOW_UP, A_PAYER_STEP_IS_PENDING);

    assertThat(aClosedHold().offered()).isEmpty();
    assertThat(left.somethingStillMovesIt()).isTrue();
    assertThat(left.nothingStillMovesIt()).isFalse();
    assertThat(left.onlyThePayerStepStillMovesIt()).isTrue();
  }

  @Test
  void a_payment_with_nothing_left_on_it_anywhere_is_one_no_step_still_moves() {
    ObjectScreen.WhatStillMovesIt left =
        ObjectScreen.whatStillMovesIt(aClosedHold(), NO_FOLLOW_UP, NO_PAYER_STEP_IS_LEFT);

    assertThat(left.nothingStillMovesIt()).isTrue();
    assertThat(left.somethingStillMovesIt()).isFalse();
    assertThat(left.onlyThePayerStepStillMovesIt()).isFalse();
  }

  @Test
  void a_payment_an_operation_of_this_provider_applies_to_is_one_a_step_still_moves() {
    ObjectScreen.WhatStillMovesIt left =
        ObjectScreen.whatStillMovesIt(anOpenHold(), NO_FOLLOW_UP, NO_PAYER_STEP_IS_LEFT);

    assertThat(anOpenHold().offered()).isNotEmpty();
    assertThat(left.somethingStillMovesIt()).isTrue();
    assertThat(left.nothingStillMovesIt()).isFalse();
    assertThat(left.onlyThePayerStepStillMovesIt()).isFalse();
  }

  @Test
  void a_payment_a_follow_up_of_this_provider_names_is_one_a_step_still_moves() {
    ObjectScreen.WhatStillMovesIt left =
        ObjectScreen.whatStillMovesIt(aClosedHold(), THE_SETTLE_FOLLOW_UP, NO_PAYER_STEP_IS_LEFT);

    assertThat(left.somethingStillMovesIt()).isTrue();
    assertThat(left.nothingStillMovesIt()).isFalse();
    assertThat(left.onlyThePayerStepStillMovesIt()).isFalse();
  }

  @Test
  void a_payer_step_beside_an_operation_is_not_the_payer_step_alone() {
    ObjectScreen.WhatStillMovesIt left =
        ObjectScreen.whatStillMovesIt(anOpenHold(), NO_FOLLOW_UP, A_PAYER_STEP_IS_PENDING);

    assertThat(left.onlyThePayerStepStillMovesIt()).isFalse();
    assertThat(left.somethingStillMovesIt()).isTrue();
  }

  @Test
  void a_payer_step_beside_a_follow_up_is_not_the_payer_step_alone() {
    ObjectScreen.WhatStillMovesIt left =
        ObjectScreen.whatStillMovesIt(aClosedHold(), THE_SETTLE_FOLLOW_UP, A_PAYER_STEP_IS_PENDING);

    assertThat(left.onlyThePayerStepStillMovesIt()).isFalse();
    assertThat(left.somethingStillMovesIt()).isTrue();
  }

  @Test
  void an_identifier_this_run_holds_an_object_under_is_named_with_that_objects_own_status() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    List<ObjectScreen.Named> named =
        ObjectScreen.theObjectsItNames(view, _ -> List.of(anObjectAnsweringTo(PROCESS)));

    assertThat(named)
        .singleElement()
        .satisfies(related -> assertThat(related.field()).isEqualTo("processId"));
    assertThat(named.get(0).id()).isEqualTo(PROCESS);
    assertThat(named.get(0).namesAnObjectThisRunHolds()).isTrue();
    assertThat(named.get(0).nativeStatus()).isEqualTo("3");
    assertThat(named.get(0).normalizedStatus())
        .isEqualTo(StatusBadge.of(NormalizedStatus.SUCCEEDED));
  }

  @Test
  void an_identifier_that_is_a_value_and_no_object_of_this_run_is_named_without_a_status() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    List<ObjectScreen.Named> named = ObjectScreen.theObjectsItNames(view, _ -> List.of());

    assertThat(named)
        .singleElement()
        .satisfies(
            related -> {
              assertThat(related.namesAnObjectThisRunHolds()).isFalse();
              assertThat(related.nativeStatus()).isNull();
              assertThat(related.normalizedStatus()).isNull();
            });
  }

  @Test
  void an_object_that_names_nothing_against_itself_names_no_identifier_at_all() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", true)),
            EVERY_ACTION,
            List.of(),
            AT);

    List<List<String>> asked = new ArrayList<>();

    assertThat(
            ObjectScreen.theObjectsItNames(
                view,
                ids -> {
                  asked.add(ids);
                  return List.of();
                }))
        .isEmpty();
    assertThat(asked)
        .as(
            "an object that names nothing has nothing to label, and asking the store for an empty"
                + " list of identifiers is a read of every store this provider keeps for an"
                + " answer that is empty before it is asked")
        .isEmpty();
  }

  @Test
  void a_screen_asks_the_store_once_for_every_identifier_one_object_names() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(anObjectNaming(FOUR_NAMED_OBJECTS)),
            EVERY_ACTION,
            List.of(),
            AT);
    List<List<String>> asked = new ArrayList<>();

    ObjectScreen.theObjectsItNames(
        view,
        ids -> {
          asked.add(ids);
          return List.of();
        });

    assertThat(asked)
        .as(
            "a screen that asks once per entry issues one store lookup per field an object names,"
                + " so a provider naming a dozen fields costs a dozen reads to label one page")
        .hasSize(1);
    assertThat(asked.get(0)).containsExactly("proc_1", "settle_1", "CREDIT", "pedido-1");
  }

  @Test
  void the_identifiers_one_object_names_are_labelled_in_the_order_it_named_them() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(anObjectNaming(FOUR_NAMED_OBJECTS)),
            EVERY_ACTION,
            List.of(),
            AT);

    List<ObjectScreen.Named> named =
        ObjectScreen.theObjectsItNames(
            view, _ -> List.of(anObjectAnsweringTo("settle_1"), aHoldAnsweringTo("pedido-1")));

    assertThat(named)
        .as(
            "a read, a screen and a control answer all print the objects an object names in the"
                + " order the provider named them, so a batched lookup that reordered them would"
                + " change what a tester reads")
        .extracting(ObjectScreen.Named::field)
        .containsExactly("processId", "settlementId", "transactionType", "orderId");
    assertThat(named)
        .extracting(ObjectScreen.Named::id)
        .containsExactly("proc_1", "settle_1", "CREDIT", "pedido-1");
    assertThat(named)
        .extracting(ObjectScreen.Named::namesAnObjectThisRunHolds)
        .containsExactly(false, true, false, true);
    assertThat(named)
        .extracting(ObjectScreen.Named::nativeStatus)
        .containsExactly(null, "3", null, "2");
  }

  @Test
  void an_identifier_an_object_only_later_answered_to_is_labelled_with_that_objects_own_status() {
    Map<String, String> names = new LinkedHashMap<>();
    names.put("settlementId", "settle_1-00002");
    ObjectScreen.View view =
        ObjectScreen.of(
            "worldline",
            RUN,
            AddressedProviderObject.canonically(anObjectNaming(names)),
            EVERY_ACTION,
            List.of(),
            AT);

    List<ObjectScreen.Named> named =
        ObjectScreen.theObjectsItNames(
            view,
            _ ->
                List.of(
                    anObjectWhoseIdentityIs(
                        ProviderObjectIdentity.of("settle_1").answeringAlsoTo("settle_1-00002"))));

    assertThat(named)
        .singleElement()
        .satisfies(
            related -> {
              assertThat(related.id()).isEqualTo("settle_1-00002");
              assertThat(related.namesAnObjectThisRunHolds())
                  .as(
                      "a provider that mints a further identifier for every maintenance operation"
                          + " reads one object back under any of them, and a screen that matched"
                          + " only the canonical one would print a payment this run holds as one"
                          + " it holds nothing under")
                  .isTrue();
              assertThat(related.nativeStatus()).isEqualTo("3");
            });
  }

  @Test
  void an_operation_says_whether_it_closed_the_authorization_in_the_words_of_this_screen() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", true)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.operations())
        .extracting(ObjectScreen.Operation::closesTheAuthorizationAsThisScreenReadsIt)
        .containsExactly(ObjectScreen.THIS_PROVIDER_STATES_NEITHER, ObjectScreen.YES);
  }

  @Test
  void an_operation_that_left_the_authorization_open_says_no_and_not_the_word_false() {
    ObjectScreen.Operation partial =
        new ObjectScreen.Operation(
            1,
            OperationKind.CAPTURE.name(),
            "60.00",
            Boolean.FALSE,
            null,
            "2",
            StatusBadge.of(NormalizedStatus.PARTIALLY_CAPTURED),
            AT);

    assertThat(partial.closesTheAuthorizationAsThisScreenReadsIt())
        .isEqualTo(ObjectScreen.NO)
        .isNotEqualTo(String.valueOf(false));
  }

  private static ObjectScreen.View anOpenHold() {
    return ObjectScreen.of(
        "grow",
        RUN,
        AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
        EVERY_ACTION,
        List.of(),
        AT);
  }

  private static ObjectScreen.View aClosedHold() {
    return ObjectScreen.of(
        "grow",
        RUN,
        AddressedProviderObject.canonically(aHoldOf("120.50", false, false)),
        EVERY_ACTION,
        List.of(),
        AT);
  }

  @Test
  void a_hold_a_capture_would_still_be_taken_on_is_offered_for_the_amount_it_holds() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.capturesAccepted()).isTrue();
    assertThat(view.holdsMoneyNoCaptureCanTake()).isFalse();
    assertThat(view.offered())
        .filteredOn(offered -> ObjectAction.CAPTURE.name().equals(offered.action()))
        .singleElement()
        .satisfies(offered -> assertThat(offered.suggestedAmount()).isEqualTo("120.50"));
  }

  @Test
  void an_object_with_nothing_left_to_capture_holds_no_money_a_capture_is_kept_from() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", true)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.capturesAccepted()).isFalse();
    assertThat(view.balances().capturableRemainder()).isEqualTo("0");
    assertThat(view.holdsMoneyNoCaptureCanTake()).isFalse();
  }

  @Test
  void a_settled_transaction_the_provider_still_refunds_is_offered_as_refundable() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", true)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.refundable()).isTrue();
  }

  @Test
  void a_settlement_no_refund_can_return_is_not_offered_although_it_still_holds_what_it_took() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.refundsAccepted()).isFalse();
    assertThat(view.refundable()).isFalse();
    assertThat(view.holdsMoneyNoRefundCanReturn()).isTrue();
    assertThat(view.balances().refundableRemainder()).isEqualTo("120.50");
    assertThat(view.offered())
        .extracting(ObjectScreen.Offered::action)
        .doesNotContain(ObjectAction.REFUND.name());
  }

  @Test
  void a_settlement_a_refund_would_still_return_is_offered_for_the_amount_it_never_returned() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", true)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.refundsAccepted()).isTrue();
    assertThat(view.holdsMoneyNoRefundCanReturn()).isFalse();
    assertThat(view.offered())
        .filteredOn(offered -> ObjectAction.REFUND.name().equals(offered.action()))
        .singleElement()
        .satisfies(offered -> assertThat(offered.suggestedAmount()).isEqualTo("120.50"));
  }

  @Test
  void a_hold_a_reversal_would_release_is_offered_no_refund_because_it_has_returned_nothing() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, true)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.refundsAccepted()).isTrue();
    assertThat(view.balances().refundableRemainder()).isEqualTo("0");
    assertThat(view.refundable()).isFalse();
    assertThat(view.holdsMoneyNoRefundCanReturn()).isFalse();
  }

  @Test
  void an_object_that_returned_everything_it_took_holds_no_money_a_refund_is_kept_from() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlementRefundedInFull("120.50")),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.refundsAccepted()).isFalse();
    assertThat(view.balances().refundableRemainder()).isEqualTo("0.00");
    assertThat(view.holdsMoneyNoRefundCanReturn()).isFalse();
  }

  @Test
  void a_settled_transaction_the_provider_will_not_refund_again_is_offered_neither() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.refundable()).isFalse();
    assertThat(view.capturable()).isFalse();
    assertThat(view.offered()).isEmpty();
  }

  @Test
  void an_object_whose_amounts_the_provider_never_validated_reports_no_balance_rather_than_zeros() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("not a number", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.balances()).isNull();
    assertThat(view.capturable()).isFalse();
    assertThat(view.operations()).hasSize(1);
  }

  @Test
  void the_operation_list_is_reported_in_the_order_the_operations_happened() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", true)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.operations())
        .extracting(ObjectScreen.Operation::kind)
        .containsExactly(OperationKind.AUTHORIZATION.name(), OperationKind.CAPTURE.name());
    assertThat(view.operations().get(0).sequence()).isEqualTo(1);
  }

  @Test
  void the_providers_own_word_for_the_state_stands_beside_the_normalized_one() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.nativeStatus()).isEqualTo("2");
    assertThat(view.normalizedStatus())
        .isEqualTo(new StatusBadge("READY_FOR_CAPTURE", StatusBadge.Tone.PENDING));
    assertThat(view.provider()).isEqualTo("grow");
    assertThat(view.testRunId()).isEqualTo(RUN.value());
  }

  @Test
  void an_operation_that_created_no_object_reports_none_of_its_own() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.operations().get(0).createdObjectId()).isNull();
  }

  @Test
  void an_object_the_provider_recorded_no_amount_for_reports_none_rather_than_a_zero() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(anObjectWithNoAmount()),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.amount()).isNull();
    assertThat(view.currency()).isNull();
    assertThat(view.offered()).isEmpty();
  }

  @Test
  void a_row_carries_the_word_its_own_provider_names_that_kind_of_object_by_and_not_a_neighbours() {
    ProviderObjectState pix = anObjectNamed("pix_1", AKindOfObjectATestNames.named("a Pix"));
    ProviderObjectState session =
        anObjectNamed("sess_2", AKindOfObjectATestNames.named("a hosted card page"));

    ObjectScreen.Listing listing = ObjectScreen.pageOf(List.of(pix, session), 10, AT);

    assertThat(listing.rows())
        .extracting(row -> row.kind().asThisProviderNamesIt())
        .containsExactly("a Pix", "a hosted card page");
  }

  @Test
  void an_objects_own_screen_reads_back_the_word_and_the_sentence_the_provider_handed_over() {
    ProviderObjectState pix = anObjectNamed("pix_1", AKindOfObjectATestNames.named("a Pix"));

    ObjectScreen.View view =
        ObjectScreen.of(
            "grow", RUN, AddressedProviderObject.canonically(pix), EVERY_ACTION, List.of(), AT);

    assertThat(view.kind().asThisProviderNamesIt()).isEqualTo("a Pix");
    assertThat(view.kind().whatItIsInItsOwnWords())
        .isEqualTo(AKindOfObjectATestNames.WHAT_A_PROVIDER_WOULD_SAY_ONE_IS);
  }

  @Test
  void a_tester_reading_the_list_is_told_which_of_two_objects_was_seeded_through_the_control_api() {
    ObjectScreen.Listing listing =
        ObjectScreen.pageOf(
            List.of(anObjectCreatedBy(ObjectOrigin.CONTROL_API), aHoldOf("120.50", true, false)),
            10,
            AT);

    assertThat(listing.rows().get(0).origin()).isEqualTo(ObjectOrigin.CONTROL_API.name());
    assertThat(listing.rows().get(0).placedByATester()).isTrue();
    assertThat(listing.rows().get(1).origin()).isEqualTo(ObjectOrigin.PROVIDER_API.name());
    assertThat(listing.rows().get(1).placedByATester()).isFalse();
    assertThat(
            ObjectScreen.of(
                    "grow",
                    RUN,
                    AddressedProviderObject.canonically(anObjectCreatedBy(ObjectOrigin.UI)),
                    EVERY_ACTION,
                    List.of(),
                    AT)
                .origin())
        .isEqualTo(ObjectOrigin.UI.name());
  }

  @Test
  void a_page_that_filled_its_page_size_offers_the_object_the_next_page_carries_on_from() {
    ObjectScreen.Listing listing =
        ObjectScreen.pageOf(
            List.of(anObjectCreatedBy(ObjectOrigin.CONTROL_API), aHoldOf("120.50", true, false)),
            2,
            AT);

    assertThat(listing.nextCursor()).isEqualTo("trx_1");
    assertThat(listing.limit()).isEqualTo(2);
  }

  @Test
  void a_page_shorter_than_its_page_size_is_the_end_of_the_list_and_offers_no_next_page() {
    assertThat(ObjectScreen.pageOf(List.of(aHoldOf("120.50", true, false)), 10, AT).nextCursor())
        .isNull();
    assertThat(ObjectScreen.pageOf(List.of(), 10, AT).nextCursor()).isNull();
    assertThat(ObjectScreen.pageOf(List.of(), 10, AT).rows()).isEmpty();
  }

  @Test
  void a_listed_row_carries_what_a_tester_scans_a_list_of_payments_for() {
    ObjectScreen.Row row =
        ObjectScreen.pageOf(List.of(aHoldOf("120.50", true, false)), 10, AT).rows().get(0);

    assertThat(row.providerObjectId()).isEqualTo("trx_1");
    assertThat(row.nativeStatus()).isEqualTo("2");
    assertThat(row.normalizedStatus())
        .isEqualTo(new StatusBadge("READY_FOR_CAPTURE", StatusBadge.Tone.PENDING));
    assertThat(row.amount()).isEqualTo("120.50");
    assertThat(row.currency()).isEqualTo("ILS");
    assertThat(row.updatedAt()).isEqualTo(AT);
  }

  @Test
  void a_tester_looking_at_an_open_hold_is_offered_the_capture_the_provider_would_take() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.offered())
        .extracting(ObjectScreen.Offered::action)
        .containsExactly(ObjectAction.CAPTURE.name(), ObjectAction.CANCEL.name());
    assertThat(view.offered().get(0).suggestedAmount()).isEqualTo("120.50");
    assertThat(view.offered().get(0).takesAnAmount()).isTrue();
    assertThat(view.offered().get(1).takesAnAmount()).isFalse();
    assertThat(view.offered().get(1).suggestedAmount()).isNull();
  }

  @Test
  void a_tester_looking_at_a_settled_payment_is_offered_the_refund_and_not_the_capture() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", true)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.offered())
        .extracting(ObjectScreen.Offered::action)
        .containsExactly(ObjectAction.REFUND.name());
    assertThat(view.offered().get(0).suggestedAmount()).isEqualTo("120.50");
  }

  @Test
  void a_capture_is_offered_as_safe_while_a_cancel_and_a_refund_are_offered_as_destructive() {
    ObjectScreen.View onAHold =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(onAHold.offered().get(0).action()).isEqualTo(ObjectAction.CAPTURE.name());
    assertThat(onAHold.offered().get(0).undoesMoneyAlreadyTakenOrHeld()).isFalse();
    assertThat(onAHold.offered().get(1).action()).isEqualTo(ObjectAction.CANCEL.name());
    assertThat(onAHold.offered().get(1).undoesMoneyAlreadyTakenOrHeld()).isTrue();

    ObjectScreen.View onASettlement =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aSettlement("120.50", true)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(onASettlement.offered().get(0).action()).isEqualTo(ObjectAction.REFUND.name());
    assertThat(onASettlement.offered().get(0).undoesMoneyAlreadyTakenOrHeld()).isTrue();
  }

  @Test
  void an_action_that_no_longer_applies_to_a_payment_in_this_state_is_not_offered_at_all() {
    assertThat(
            ObjectScreen.of(
                    "grow",
                    RUN,
                    AddressedProviderObject.canonically(aSettlement("120.50", false)),
                    EVERY_ACTION,
                    List.of(),
                    AT)
                .offered())
        .isEmpty();
    assertThat(
            ObjectScreen.of(
                    "grow",
                    RUN,
                    AddressedProviderObject.canonically(aHoldOf("120.50", false, false)),
                    EVERY_ACTION,
                    List.of(),
                    AT)
                .offered())
        .isEmpty();
  }

  @Test
  void an_object_whose_amounts_the_provider_never_validated_is_offered_no_action_at_all() {
    assertThat(
            ObjectScreen.of(
                    "grow",
                    RUN,
                    AddressedProviderObject.canonically(aHoldOf("not a number", true, false)),
                    EVERY_ACTION,
                    List.of(),
                    AT)
                .offered())
        .isEmpty();
  }

  @Test
  void a_provider_that_publishes_no_cancellation_offers_none_and_says_what_it_has_instead() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            NO_CANCELLATION,
            List.of(),
            AT);

    assertThat(view.offered())
        .extracting(ObjectScreen.Offered::action)
        .containsExactly(ObjectAction.CAPTURE.name());
    assertThat(view.unoffered())
        .singleElement()
        .satisfies(
            absent -> {
              assertThat(absent.action()).isEqualTo(ObjectAction.CANCEL.name());
              assertThat(absent.asked()).isEqualTo(ObjectAction.CANCEL.asked());
              assertThat(absent.whatThisProviderHasInstead()).isEqualTo(NO_CANCEL);
            });
  }

  @Test
  void a_provider_that_publishes_all_three_operations_reports_nothing_as_missing() {
    assertThat(
            ObjectScreen.of(
                    "grow",
                    RUN,
                    AddressedProviderObject.canonically(aSettlement("120.50", true)),
                    EVERY_ACTION,
                    List.of(),
                    AT)
                .unoffered())
        .isEmpty();
  }

  @Test
  void an_action_the_provider_refused_reaches_the_tester_in_the_providers_own_words() {
    assertThat(
            ObjectScreen.refusalOf(
                new ObjectActionOutcome.RefusedByTheProvider(
                    "808 no release can be made on a released transaction")))
        .isEqualTo("808 no release can be made on a released transaction");
  }

  @Test
  void an_action_a_scenario_rule_answered_instead_of_running_says_the_payment_did_not_move() {
    String refusal =
        String.valueOf(
            ObjectScreen.refusalOf(new ObjectActionOutcome.AnsweredByAScenarioInstead("scn_7")));

    assertThat(refusal).contains("scn_7").contains("this payment did not move");
  }

  @Test
  void an_action_this_provider_does_not_publish_is_refused_with_what_it_publishes_instead() {
    assertThat(ObjectScreen.refusalOf(new ObjectActionOutcome.NotOffered(NO_CANCEL)))
        .isEqualTo(NO_CANCEL);
  }

  @Test
  void a_rule_narrowing_on_a_header_is_named_on_the_screen_as_one_no_action_here_can_fire() {
    List<ObjectScreen.Unreachable> named =
        ObjectScreen.theRulesNoActionHereCanFire(
            List.of(aRuleNarrowingOn(Map.of("X-Force", "decline"))));

    assertThat(named).hasSize(1);
    assertThat(named.get(0).scenarioId()).isEqualTo("scn_header");
    assertThat(named.get(0).headers()).containsExactly("X-Force: decline");
  }

  @Test
  void a_rule_narrowing_on_no_header_is_not_named_because_an_action_here_can_fire_it() {
    List<ObjectScreen.Unreachable> named =
        ObjectScreen.theRulesNoActionHereCanFire(List.of(aRuleNarrowingOn(Map.of())));

    assertThat(named).isEmpty();
  }

  @Test
  void a_rule_that_shapes_a_delivery_is_not_named_because_no_action_here_sends_one() {
    List<ObjectScreen.Unreachable> named =
        ObjectScreen.theRulesNoActionHereCanFire(
            List.of(
                aRule(
                    "scn_delivery",
                    new ScenarioAction.DeliveryDropped(),
                    Map.of("X-Force", "decline"))));

    assertThat(named).isEmpty();
  }

  @Test
  void a_capture_a_scenario_refused_carries_a_refusing_verdict_rather_than_a_success_status() {
    assertThat(
            ObjectScreen.theRefusalItReports(
                new ObjectActionOutcome.RefusedByTheProvider("813 declined", "scn_1"),
                "trx_1",
                true))
        .isEqualTo(ActionVerdict.refused(RefusalSubject.theObject("trx_1")));
    assertThat(
            ObjectScreen.theRefusalItReports(
                new ObjectActionOutcome.AnsweredByAScenarioInstead("scn_2"), "trx_1", true))
        .isEqualTo(ActionVerdict.refused(RefusalSubject.theObject("trx_1")));
  }

  @Test
  void a_capture_the_provider_itself_refused_carries_the_same_refusing_verdict_as_a_rules() {
    assertThat(
            ObjectScreen.theRefusalItReports(
                new ObjectActionOutcome.RefusedByTheProvider("808 already released"),
                "trx_1",
                true))
        .isEqualTo(ActionVerdict.refused(RefusalSubject.theObject("trx_1")));
  }

  @Test
  void an_action_over_an_id_this_run_holds_no_object_under_names_the_field_that_named_it() {
    assertThat(
            ObjectScreen.theRefusalItReports(
                new ObjectActionOutcome.NoSuchObject(), "trx_gone", false))
        .isEqualTo(
            ActionVerdict.notFound(
                RefusalSubject.noObjectAnsweredTo(ObjectScreen.PROVIDER_OBJECT_ID)));
  }

  @Test
  void an_action_this_provider_does_not_offer_names_no_object_when_the_run_holds_none() {
    assertThat(
            ObjectScreen.theRefusalItReports(
                new ObjectActionOutcome.NotOffered("no cancel"), "trx_1", false))
        .isEqualTo(
            ActionVerdict.refused(
                RefusalSubject.noObjectWasNamed(
                    ObjectScreen.THE_ACTION_IT_ASKED_THIS_PROVIDER_TO_PERFORM)));
  }

  @Test
  void an_applied_capture_reports_no_refusal_whether_or_not_a_rule_let_it_through() {
    assertThat(
            ObjectScreen.theRefusalItReports(
                new ObjectActionOutcome.Applied("trx_1", null), "trx_1", true))
        .isNull();
    assertThat(
            ObjectScreen.theRefusalItReports(
                new ObjectActionOutcome.Applied("trx_1", null, "scn_3"), "trx_1", true))
        .isNull();
  }

  @Test
  void an_unconfirmed_change_and_an_unreadable_action_are_both_rejected_naming_the_action() {
    assertThat(ObjectScreen.theRefusalOfAnUnconfirmedChange())
        .isEqualTo(
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(
                    ObjectScreen.THE_ACTION_IT_ASKED_THIS_PROVIDER_TO_PERFORM)));
    assertThat(ObjectScreen.theRefusalOfAnActionThisSimulatorCannotRead())
        .isEqualTo(
            ActionVerdict.rejected(
                RefusalSubject.noObjectWasNamed(
                    ObjectScreen.THE_ACTION_IT_ASKED_THIS_PROVIDER_TO_PERFORM)));
  }

  @Test
  void
      the_trail_of_a_capture_a_scenario_refused_names_the_action_and_the_words_it_was_refused_in() {
    Map<String, List<String>> facts =
        ObjectScreen.factsOf(
            ObjectActionRequest.of(RUN, "trx_1", ObjectAction.CAPTURE, "40.00", ObjectOrigin.UI),
            "813 the payment was declined");

    assertThat(facts)
        .containsEntry("action", List.of(ObjectAction.CAPTURE.name()))
        .containsEntry("amount", List.of("40.00"))
        .containsEntry(ActionFacts.REFUSED_BECAUSE, List.of("813 the payment was declined"));
  }

  private static ScenarioRule aRuleNarrowingOn(Map<String, String> header) {
    return aRule("scn_header", new ScenarioAction.TerminalFailure("813"), header);
  }

  private static ScenarioRule aRule(
      String scenarioId, ScenarioAction action, Map<String, String> header) {
    return new ScenarioRule(
        scenarioId,
        GROW,
        RUN,
        0,
        1,
        new ScenarioMatch(
            null, null, null, null, null, null, null, "settleSuspendedTransaction", null, header),
        action,
        null,
        AT);
  }

  @Test
  void an_action_on_an_object_this_run_never_held_is_refused_as_the_lookup_already_is() {
    assertThat(ObjectScreen.refusalOf(new ObjectActionOutcome.NoSuchObject()))
        .isEqualTo(ObjectScreen.THE_RUN_HOLDS_NO_SUCH_OBJECT);
  }

  @Test
  void an_action_the_provider_applied_is_not_a_refusal() {
    assertThat(ObjectScreen.refusalOf(new ObjectActionOutcome.Applied("trx_1", null))).isNull();
  }

  @Test
  void the_trail_of_a_capture_a_tester_took_names_the_action_and_the_amount_it_moved() {
    Map<String, List<String>> facts =
        ObjectScreen.factsOf(
            ObjectActionRequest.of(RUN, "trx_1", ObjectAction.CAPTURE, "40.00", ObjectOrigin.UI),
            new ObjectActionOutcome.Applied("trx_1", null));

    assertThat(facts)
        .containsEntry("action", List.of(ObjectAction.CAPTURE.name()))
        .containsEntry("amount", List.of("40.00"))
        .doesNotContainKey("createdObjectId");
  }

  @Test
  void the_trail_of_a_refund_a_tester_took_names_the_object_the_refund_brought_into_existence() {
    Map<String, List<String>> facts =
        ObjectScreen.factsOf(
            ObjectActionRequest.of(RUN, "trx_1", ObjectAction.REFUND, "40.00", ObjectOrigin.UI),
            new ObjectActionOutcome.Applied("trx_1", "trx_9"));

    assertThat(facts).containsEntry("createdObjectId", List.of("trx_9"));
  }

  @Test
  void the_trail_of_a_cancellation_names_no_amount_because_a_cancellation_carries_none() {
    Map<String, List<String>> facts =
        ObjectScreen.factsOf(
            ObjectActionRequest.of(RUN, "trx_1", ObjectAction.CANCEL, null, ObjectOrigin.UI),
            new ObjectActionOutcome.Applied("trx_1", null));

    assertThat(facts).containsEntry("action", List.of(ObjectAction.CANCEL.name()));
    assertThat(facts).doesNotContainKey("amount");
  }

  @Test
  void
      an_object_opened_by_a_later_identifier_names_that_identifier_beside_the_one_it_was_created_under() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            new AddressedProviderObject("trx_9", aHoldRenamedTo("trx_9")),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.providerObjectId()).isEqualTo("trx_1");
    assertThat(view.addressedBy()).isEqualTo("trx_9");
    assertThat(view.answersTo()).containsExactly("trx_1", "trx_9");
    assertThat(view.wasAddressedByALaterIdentifier()).isTrue();
    assertThat(view.answersToMoreThanOneIdentifier()).isTrue();
  }

  @Test
  void an_object_that_kept_one_identifier_is_addressed_by_it_and_names_no_other() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.addressedBy()).isEqualTo("trx_1");
    assertThat(view.answersTo()).containsExactly("trx_1");
    assertThat(view.wasAddressedByALaterIdentifier()).isFalse();
    assertThat(view.answersToMoreThanOneIdentifier()).isFalse();
  }

  @Test
  void an_object_no_payer_finishes_carries_no_session_on_its_listed_row() {
    ObjectScreen.Row row =
        ObjectScreen.pageOf(List.of(aHoldOf("120.50", true, false)), 10, AT).rows().get(0);

    assertThat(row.session()).isNull();
  }

  @Test
  void an_object_no_payer_finishes_carries_no_session_on_the_object_it_opens() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aHoldOf("120.50", true, false)),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.session()).isNull();
  }

  @Test
  void an_object_a_payer_finishes_carries_the_account_the_party_and_the_operation_on_its_row() {
    ObjectScreen.Row row =
        ObjectScreen.pageOf(List.of(aProcessNamingItsPayer()), 10, AT).rows().get(0);

    assertThat(row.session()).isNotNull();
    assertThat(row.session().account()).isEqualTo("merchant-1");
    assertThat(row.session().party()).isEqualTo("Dana Levi");
    assertThat(row.session().operation()).isEqualTo("createPaymentProcess:chargeType=2");
  }

  @Test
  void an_object_a_payer_finishes_carries_when_it_was_created_on_its_row() {
    ObjectScreen.Row row =
        ObjectScreen.pageOf(List.of(aProcessNamingItsPayer()), 10, AT).rows().get(0);

    assertThat(row.session()).isNotNull();
    assertThat(row.session().createdAt()).isEqualTo(CREATED);
  }

  @Test
  void an_object_a_payer_finishes_carries_the_same_facts_on_the_object_it_opens() {
    ObjectScreen.View view =
        ObjectScreen.of(
            "grow",
            RUN,
            AddressedProviderObject.canonically(aProcessNamingItsPayer()),
            EVERY_ACTION,
            List.of(),
            AT);

    assertThat(view.session()).isNotNull();
    assertThat(view.session().account()).isEqualTo("merchant-1");
    assertThat(view.session().party()).isEqualTo("Dana Levi");
  }

  @Test
  void a_provider_that_names_no_party_has_its_own_words_in_that_cell_and_not_a_blank() {
    ObjectScreen.Row row =
        ObjectScreen.pageOf(List.of(aSessionNamingNoParty()), 10, AT).rows().get(0);

    assertThat(row.session()).isNotNull();
    assertThat(row.session().party()).isEqualTo(NO_PARTY_ON_A_TOKENIZATION);
    assertThat(row.session().aPartyIsNamed()).isFalse();
  }

  @Test
  void a_provider_that_publishes_no_deadline_has_its_own_words_in_that_cell_and_not_a_blank() {
    ObjectScreen.Row row =
        ObjectScreen.pageOf(List.of(aProcessNamingItsPayer()), 10, AT).rows().get(0);

    assertThat(row.session()).isNotNull();
    assertThat(row.session().expires()).isEqualTo(NO_DEADLINE_ON_A_PROCESS);
    assertThat(row.session().itExpiresAtAKnownTime()).isFalse();
  }

  @Test
  void a_session_the_clock_of_this_run_has_not_reached_is_not_reported_as_past_its_deadline() {
    ObjectScreen.Row row =
        ObjectScreen.pageOf(List.of(aSessionNamingNoParty()), 10, EXPIRES.minusSeconds(1))
            .rows()
            .get(0);

    assertThat(row.session()).isNotNull();
    assertThat(row.session().itsDeadlineHasPassed()).isFalse();
    assertThat(row.session().expires()).isEqualTo("2026-09-15T13:00:00Z");
  }

  @Test
  void a_session_the_clock_of_this_run_has_passed_is_reported_as_past_its_deadline() {
    ObjectScreen.Row row =
        ObjectScreen.pageOf(List.of(aSessionNamingNoParty()), 10, EXPIRES.plusSeconds(1))
            .rows()
            .get(0);

    assertThat(row.session()).isNotNull();
    assertThat(row.session().itsDeadlineHasPassed()).isTrue();
  }

  @Test
  void the_hosted_sessions_of_a_page_are_the_objects_a_payer_finishes_and_no_others() {
    ObjectScreen.Listing listing =
        ObjectScreen.pageOf(
            List.of(aHoldOf("120.50", true, false), aProcessNamingItsPayer()), 10, AT);

    assertThat(listing.rows()).hasSize(2);
    assertThat(listing.theObjectsAPayerFinishes())
        .singleElement()
        .extracting(ObjectScreen.Row::providerObjectId)
        .isEqualTo("proc_1");
  }

  @Test
  void a_page_holding_no_object_a_payer_finishes_offers_an_empty_list_of_hosted_sessions() {
    ObjectScreen.Listing listing =
        ObjectScreen.pageOf(List.of(aHoldOf("120.50", true, false)), 10, AT);

    assertThat(listing.theObjectsAPayerFinishes()).isEmpty();
  }

  private static ProviderObjectState aProcessNamingItsPayer() {
    return anObjectFinishedBy(
        "proc_1",
        new HostedSessionFacts.AnObjectAPayerFinishes(
            "merchant-1",
            new HostedSessionFacts.Party.NamedBy("Dana Levi"),
            "createPaymentProcess:chargeType=2",
            CREATED,
            new HostedSessionFacts.Deadline.NoDeadlineThisProviderPublishes(
                NO_DEADLINE_ON_A_PROCESS)));
  }

  private static ProviderObjectState aSessionNamingNoParty() {
    return anObjectFinishedBy(
        "sess_1",
        new HostedSessionFacts.AnObjectAPayerFinishes(
            "merchant-1",
            new HostedSessionFacts.Party.NoPartyThisProviderPublishes(NO_PARTY_ON_A_TOKENIZATION),
            "POST /v2/{merchantId}/hostedtokenizations",
            CREATED,
            new HostedSessionFacts.Deadline.ItExpiresAt(EXPIRES)));
  }

  private static ProviderObjectState anObjectNamed(String id, ProviderObjectKind kind) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(id),
        kind,
        "11",
        NormalizedStatus.PROCESSING,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState anObjectFinishedBy(String id, HostedSessionFacts facts) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(id),
        AKindOfObjectATestNames.ONE,
        "11",
        NormalizedStatus.PROCESSING,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT,
        facts);
  }

  private static ProviderObjectState aHoldRenamedTo(String renamed) {
    ProviderObjectState kept = aHoldOf("120.50", true, false);
    return new ProviderObjectState(
        ProviderObjectIdentity.of(kept.providerObjectId()).answeringAlsoTo(renamed),
        AKindOfObjectATestNames.ONE,
        kept.nativeStatus(),
        kept.normalizedStatus(),
        kept.amount(),
        kept.currency(),
        kept.ledger(),
        kept.authorizationOpen(),
        kept.capturesAccepted(),
        kept.refundsAccepted(),
        kept.relatedObjects(),
        kept.origin(),
        kept.updatedAt());
  }

  private static ProviderObjectState anObjectCreatedBy(ObjectOrigin origin) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("seeded_1"),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        Amount.of("120.50"),
        "ILS",
        Ledger.empty(),
        true,
        true,
        false,
        Map.of(),
        origin,
        AT);
  }

  private static ProviderObjectState anObjectWithNoAmount() {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "11",
        NormalizedStatus.PROCESSING,
        null,
        null,
        Ledger.empty(),
        true,
        true,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static final Map<String, String> FOUR_NAMED_OBJECTS = fourNamedObjects();

  private static Map<String, String> fourNamedObjects() {
    return theObjectsNamed(
        "processId", "proc_1",
        "settlementId", "settle_1",
        "transactionType", "CREDIT",
        "orderId", "pedido-1");
  }

  private static ProviderObjectState anObjectNaming(Map<String, String> related) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        Amount.of("120.50"),
        "ILS",
        new Ledger(List.of(anOperation(1, OperationKind.AUTHORIZATION, "120.50", "2"))),
        true,
        true,
        false,
        related,
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState aHoldOf(
      String amount, boolean authorizationOpen, boolean refundsAccepted) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        Amount.of(amount),
        "ILS",
        new Ledger(List.of(anOperation(1, OperationKind.AUTHORIZATION, amount, "2"))),
        authorizationOpen,
        authorizationOpen,
        refundsAccepted,
        Map.of("processId", "proc_1"),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState aHoldNoCaptureCanTake(String amount) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        Amount.of(amount),
        "ILS",
        new Ledger(List.of(anOperation(1, OperationKind.AUTHORIZATION, amount, "2"))),
        true,
        false,
        false,
        Map.of("processId", "proc_1"),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState anObjectAnsweringTo(String providerObjectId) {
    return anObjectWhoseIdentityIs(ProviderObjectIdentity.of(providerObjectId));
  }

  private static ProviderObjectState anObjectWhoseIdentityIs(ProviderObjectIdentity identity) {
    return new ProviderObjectState(
        identity,
        AKindOfObjectATestNames.ONE,
        "3",
        NormalizedStatus.SUCCEEDED,
        Amount.of("120.50"),
        "ILS",
        new Ledger(
            List.of(
                anOperation(1, OperationKind.AUTHORIZATION, "120.50", "2"),
                anOperation(2, OperationKind.CAPTURE, "120.50", "3"))),
        false,
        false,
        true,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState aHoldAnsweringTo(String providerObjectId) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(providerObjectId),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        Amount.of("90.00"),
        "ILS",
        new Ledger(List.of(anOperation(1, OperationKind.AUTHORIZATION, "90.00", "2"))),
        true,
        true,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState aSettlement(String amount, boolean refundsAccepted) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "3",
        NormalizedStatus.SUCCEEDED,
        Amount.of(amount),
        "ILS",
        new Ledger(
            List.of(
                anOperation(1, OperationKind.AUTHORIZATION, amount, "2"),
                anOperation(2, OperationKind.CAPTURE, amount, "3"))),
        false,
        false,
        refundsAccepted,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState aSettlementRefundedInFull(String amount) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of("trx_1"),
        AKindOfObjectATestNames.ONE,
        "3",
        NormalizedStatus.REFUNDED,
        Amount.of(amount),
        "ILS",
        new Ledger(
            List.of(
                anOperation(1, OperationKind.AUTHORIZATION, amount, "2"),
                anOperation(2, OperationKind.CAPTURE, amount, "3"),
                anOperation(3, OperationKind.REFUND, amount, "3"))),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static MonetaryOperation anOperation(
      int sequence, OperationKind kind, String amount, String nativeStatus) {
    return new MonetaryOperation(
        sequence,
        kind,
        Amount.of(amount),
        kind == OperationKind.CAPTURE ? Boolean.TRUE : null,
        null,
        nativeStatus,
        kind == OperationKind.AUTHORIZATION
            ? NormalizedStatus.READY_FOR_CAPTURE
            : NormalizedStatus.SUCCEEDED,
        AT);
  }
}
