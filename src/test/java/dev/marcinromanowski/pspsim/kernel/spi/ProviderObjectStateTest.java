package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ProviderObjectStateTest {

  private static final Instant AT = Instant.parse("2026-09-10T12:00:00Z");

  private static final String PAYMENT = "500001";

  private static final String RENAMED_BY_A_CAPTURE = "500009";

  private static final boolean NOTHING_WAS_RETURNED = false;

  private static final boolean THE_WHOLE_AMOUNT_WAS_RETURNED = true;

  private static final boolean THE_PROVIDER_REVERSES_AN_UNCAPTURED_HOLD_TOO = true;

  @Test
  void refuses_an_operation_that_names_the_object_a_reader_is_already_looking_at() {
    Ledger ledger =
        Ledger.empty()
            .with(operation(1, OperationKind.CAPTURE, "120.50", null))
            .with(operation(2, OperationKind.REFUND, "120.50", PAYMENT));

    assertThatThrownBy(() -> objectOf(ledger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("createdObjectId")
        .hasMessageContaining("operation 2");
  }

  @Test
  void admits_a_refund_that_names_the_separate_object_the_refund_brought_into_existence() {
    Ledger ledger =
        Ledger.empty()
            .with(operation(1, OperationKind.CAPTURE, "120.50", null))
            .with(operation(2, OperationKind.REFUND, "120.50", "500002"));

    ProviderObjectState state = objectOf(ledger);

    assertThat(state.ledger().operations())
        .extracting(MonetaryOperation::createdObjectId)
        .containsExactly(null, "500002");
  }

  @Test
  void admits_an_operation_that_created_nothing_a_reader_could_go_and_look_up() {
    Ledger ledger = Ledger.empty().with(operation(1, OperationKind.AUTHORIZATION, "120.50", null));

    assertThat(objectOf(ledger).ledger().operations())
        .singleElement()
        .satisfies(operation -> assertThat(operation.createdObjectId()).isNull());
  }

  @Test
  void refuses_an_operation_that_names_a_later_identifier_of_the_object_it_is_already_on() {
    Ledger ledger =
        Ledger.empty()
            .with(operation(1, OperationKind.CAPTURE, "120.50", null))
            .with(operation(2, OperationKind.REFUND, "120.50", RENAMED_BY_A_CAPTURE));

    ProviderObjectIdentity renamed =
        ProviderObjectIdentity.of(PAYMENT).answeringAlsoTo(RENAMED_BY_A_CAPTURE);

    assertThatThrownBy(() -> objectWith(renamed, ledger))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("createdObjectId")
        .hasMessageContaining("operation 2");
  }

  @Test
  void an_object_that_answers_to_several_identifiers_reports_the_canonical_one_as_its_id() {
    ProviderObjectState state =
        objectWith(
            ProviderObjectIdentity.of(PAYMENT).answeringAlsoTo(RENAMED_BY_A_CAPTURE),
            Ledger.empty());

    assertThat(state.providerObjectId()).isEqualTo(PAYMENT);
    assertThat(state.identity().answersTo(RENAMED_BY_A_CAPTURE)).isTrue();
  }

  @Test
  void refuses_a_state_that_accepts_a_capture_on_an_authorization_it_reports_as_closed() {
    assertThatThrownBy(() -> aHoldWhose(false, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ProviderObjectState.A_CAPTURE_WOULD_BE_TAKEN_ON_A_CLOSED_AUTHORIZATION);
  }

  @Test
  void admits_a_hold_that_is_open_and_still_accepts_a_capture() {
    ProviderObjectState held = aHoldWhose(true, true);

    assertThat(held.authorizationOpen()).isTrue();
    assertThat(held.capturesAccepted()).isTrue();
  }

  @Test
  void admits_a_hold_that_is_open_and_accepts_no_capture_any_more() {
    ProviderObjectState held = aHoldWhose(true, false);

    assertThat(held.authorizationOpen()).isTrue();
    assertThat(held.capturesAccepted()).isFalse();
  }

  @Test
  void admits_a_closed_authorization_that_accepts_no_capture() {
    ProviderObjectState closed = aHoldWhose(false, false);

    assertThat(closed.authorizationOpen()).isFalse();
    assertThat(closed.capturesAccepted()).isFalse();
  }

  @Test
  void refuses_a_state_that_accepts_a_refund_of_money_its_operation_list_does_not_record() {
    assertThatThrownBy(() -> aSettlementWhose(THE_WHOLE_AMOUNT_WAS_RETURNED, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ProviderObjectState.A_REFUND_WOULD_RETURN_MONEY_THE_OBJECT_DOES_NOT_HAVE);
  }

  @Test
  void refuses_a_state_that_accepts_a_refund_of_amounts_it_cannot_add_up() {
    assertThatThrownBy(
            () ->
                aClosedObjectWhose(
                    Ledger.empty().with(operation(1, OperationKind.CAPTURE, "not a number", null)),
                    true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ProviderObjectState.A_REFUND_WOULD_RETURN_MONEY_THE_OBJECT_DOES_NOT_HAVE);
  }

  @Test
  void admits_a_state_that_accepts_a_refund_of_money_it_took_and_has_not_returned() {
    ProviderObjectState settled = aSettlementWhose(NOTHING_WAS_RETURNED, true);

    assertThat(settled.authorizationOpen()).isFalse();
    assertThat(settled.refundsAccepted()).isTrue();
    assertThat(settled.derivedBalances().orElseThrow().refundableRemainder())
        .isEqualTo(Amount.of("120.50"));
  }

  @Test
  void admits_a_state_that_accepts_a_reversal_of_a_hold_it_has_captured_nothing_of() {
    ProviderObjectState held =
        aHoldWhose(true, false, THE_PROVIDER_REVERSES_AN_UNCAPTURED_HOLD_TOO);

    assertThat(held.refundsAccepted()).isTrue();
    assertThat(held.derivedBalances().orElseThrow().refundableRemainder())
        .isEqualTo(Amount.of("0"));
  }

  @Test
  void reports_money_it_took_and_has_not_returned_where_no_refund_would_be_taken_either() {
    ProviderObjectState settled = aSettlementWhose(NOTHING_WAS_RETURNED, false);

    assertThat(settled.refundsAccepted()).isFalse();
    assertThat(settled.derivedBalances().orElseThrow().refundableRemainder())
        .isEqualTo(Amount.of("120.50"));
  }

  private static ProviderObjectState aSettlementWhose(boolean returnedInFull, boolean refunds) {
    Ledger ledger =
        Ledger.empty()
            .with(operation(1, OperationKind.AUTHORIZATION, "120.50", null))
            .with(operation(2, OperationKind.CAPTURE, "120.50", null));
    return aClosedObjectWhose(
        returnedInFull ? ledger.with(operation(3, OperationKind.REFUND, "120.50", null)) : ledger,
        refunds);
  }

  private static ProviderObjectState aClosedObjectWhose(Ledger ledger, boolean refunds) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(PAYMENT),
        AKindOfObjectATestNames.ONE,
        "3",
        NormalizedStatus.SUCCEEDED,
        Amount.of("120.50"),
        null,
        ledger,
        false,
        false,
        refunds,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState aHoldWhose(
      boolean authorizationOpen, boolean capturesAccepted) {
    return aHoldWhose(authorizationOpen, capturesAccepted, false);
  }

  private static ProviderObjectState aHoldWhose(
      boolean authorizationOpen, boolean capturesAccepted, boolean refundsAccepted) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(PAYMENT),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.READY_FOR_CAPTURE,
        Amount.of("120.50"),
        null,
        Ledger.empty().with(operation(1, OperationKind.AUTHORIZATION, "120.50", null)),
        authorizationOpen,
        capturesAccepted,
        refundsAccepted,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  @Test
  void the_objects_a_state_names_are_read_back_in_the_order_the_provider_named_them() {
    Map<String, String> named = new LinkedHashMap<>();
    for (int at = 1; at <= 12; at++) {
      named.put("step." + at, "the " + at + "th thing that happened");
    }

    ProviderObjectState state = objectNaming(named);

    assertThat(state.relatedObjects().keySet())
        .as(
            "a screen and a control answer print these in the order they come back, so a copy"
                + " that loses the order a provider wrote them in prints a timeline out of order"
                + " and nothing anywhere else notices")
        .containsExactlyElementsOf(named.keySet());
  }

  @Test
  void a_second_named_object_handed_over_in_a_map_with_no_order_is_refused_rather_than_printed() {
    assertThatCode(() -> objectNaming(Map.of("account", "acct_1", "customer", "cus_1")))
        .as(
            "Map.of, Map.copyOf, HashMap and Collectors.toMap iterate in an order drawn from the"
                + " keys' hashes and a per-JVM salt, so a provider that hands its names over in"
                + " one of those prints them in a different order on each run of this simulator,"
                + " and a test written on the order it happened to see fails intermittently"
                + " rather than loudly. Refusing the map is what stops the next construction site"
                + " reintroducing that, and there is no other way to tell an ordered map from an"
                + " unordered one once the entries are in it")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ProviderObjectState.A_SECOND_NAMED_OBJECT_NEEDS_AN_ORDER_TO_PRINT_IT_IN);
    assertThatThrownBy(() -> objectNaming(new HashMap<>(Map.of("a", "1", "b", "2"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(ProviderObjectState.A_SECOND_NAMED_OBJECT_NEEDS_AN_ORDER_TO_PRINT_IT_IN);
  }

  @Test
  void one_named_object_and_none_at_all_are_admitted_from_a_map_that_carries_no_order() {
    assertThat(objectNaming(Map.of("account", "acct_1")).relatedObjects())
        .as("an object naming one other has one order to print it in, whatever map carries it")
        .containsExactly(Map.entry("account", "acct_1"));
    assertThat(objectNaming(Map.of()).relatedObjects()).isEmpty();
  }

  @Test
  void the_objects_a_state_names_are_handed_on_in_a_map_a_second_state_can_be_built_from() {
    Map<String, String> named = new LinkedHashMap<>();
    named.put("account", "acct_1");
    named.put("customer", "cus_1");

    ProviderObjectState read = objectNaming(objectNaming(named).relatedObjects());

    assertThat(read.relatedObjects())
        .as(
            "a screen, a reproduction bundle and a control answer all pass the objects a state"
                + " names on, and a copy that refuses to be read back by the same constructor"
                + " makes the check on the way in a trap for its own output")
        .containsExactly(Map.entry("account", "acct_1"), Map.entry("customer", "cus_1"));
  }

  @Test
  void an_object_named_with_no_name_or_no_value_at_all_is_refused_rather_than_printed_as_null() {
    Map<String, String> noName = new LinkedHashMap<>();
    noName.put(null, "pay_1");
    Map<String, String> noValue = new LinkedHashMap<>();
    noValue.put("processId", null);

    assertThatThrownBy(() -> objectNaming(noName))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(ProviderObjectState.A_NAMED_OBJECT_IS_A_NAME_AND_A_VALUE);
    assertThatThrownBy(() -> objectNaming(noValue))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(ProviderObjectState.A_NAMED_OBJECT_IS_A_NAME_AND_A_VALUE);
  }

  @Test
  void the_objects_a_state_names_cannot_be_changed_through_the_map_it_was_given() {
    Map<String, String> named = new LinkedHashMap<>();
    named.put("processId", "proc_1");
    ProviderObjectState state = objectNaming(named);

    named.put("transactionId", "txn_1");

    assertThat(state.relatedObjects()).containsExactlyEntriesOf(Map.of("processId", "proc_1"));
    assertThatThrownBy(() -> state.relatedObjects().put("transactionId", "txn_1"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static ProviderObjectState objectNaming(Map<String, String> relatedObjects) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(PAYMENT),
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.SUCCEEDED,
        Amount.of("120.50"),
        null,
        Ledger.empty(),
        false,
        false,
        false,
        relatedObjects,
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static ProviderObjectState objectOf(Ledger ledger) {
    return objectWith(ProviderObjectIdentity.of(PAYMENT), ledger);
  }

  private static ProviderObjectState objectWith(ProviderObjectIdentity identity, Ledger ledger) {
    return new ProviderObjectState(
        identity,
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.SUCCEEDED,
        Amount.of("120.50"),
        null,
        ledger,
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }

  private static MonetaryOperation operation(
      int sequence, OperationKind kind, String amount, @Nullable String created) {
    return new MonetaryOperation(
        sequence, kind, Amount.of(amount), null, created, "2", NormalizedStatus.SUCCEEDED, AT);
  }
}
