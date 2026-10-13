package dev.marcinromanowski.pspsim.kernel.money;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class LedgersTest {

  private static final Instant AT = Instant.parse("2026-10-08T10:00:00Z");

  @Test
  void one_answer_covering_several_objects_is_split_per_object_in_the_order_it_arrived() {
    Map<String, Ledger> keyed =
        Ledgers.keyedByObject(
            List.of(
                an(1, "pay-1", OperationKind.AUTHORIZATION, "100.00"),
                an(2, "pay-1", OperationKind.CAPTURE, "40.00"),
                an(1, "pay-2", OperationKind.AUTHORIZATION, "7.50")));

    assertThat(keyed.keySet())
        .as(
            "a listing places each object's operations against that object, and a key order the"
                + " runtime picks would make the same answer read back differently in another JVM")
        .containsExactly("pay-1", "pay-2");
    assertThat(Ledgers.theLedgerOf(keyed, "pay-1").operations())
        .extracting(MonetaryOperation::sequence, MonetaryOperation::kind)
        .containsExactly(
            Tuple.tuple(Integer.valueOf(1), OperationKind.AUTHORIZATION),
            Tuple.tuple(Integer.valueOf(2), OperationKind.CAPTURE));
    assertThat(Ledgers.theLedgerOf(keyed, "pay-2").operations())
        .extracting(MonetaryOperation::amount)
        .containsExactly(Amount.of("7.50"));
  }

  @Test
  void the_operations_of_one_object_keep_the_order_the_one_answer_carried_them_in() {
    Map<String, Ledger> keyed =
        Ledgers.keyedByObject(
            List.of(
                an(1, "pay-1", OperationKind.AUTHORIZATION, "100.00"),
                an(2, "pay-1", OperationKind.CAPTURE, "60.00"),
                an(3, "pay-1", OperationKind.REFUND, "20.00")));

    assertThat(Ledgers.theLedgerOf(keyed, "pay-1").operations())
        .extracting(MonetaryOperation::sequence)
        .containsExactly(Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3));
    assertThat(Ledgers.theLedgerOf(keyed, "pay-1").refundableRemainder())
        .contains(new BigDecimal("40.00"));
  }

  @Test
  void an_object_the_one_answer_carried_no_operation_for_has_an_empty_ledger_and_not_a_null_one() {
    Map<String, Ledger> keyed =
        Ledgers.keyedByObject(List.of(an(1, "pay-1", OperationKind.CAPTURE, "1.00")));

    assertThat(Ledgers.theLedgerOf(keyed, "pay-2")).isEqualTo(Ledger.empty());
    assertThat(Ledgers.theLedgerOf(keyed, "pay-2").operations()).isEmpty();
  }

  @Test
  void no_operation_at_all_is_no_ledger_at_all_rather_than_an_entry_without_operations() {
    assertThat(Ledgers.keyedByObject(List.of())).isEmpty();
  }

  private static OperationOf an(int sequence, String objectId, OperationKind kind, String amount) {
    return new OperationOf(
        objectId,
        new MonetaryOperation(
            sequence,
            kind,
            Amount.of(amount),
            null,
            null,
            "approved",
            NormalizedStatus.SUCCEEDED,
            AT));
  }
}
