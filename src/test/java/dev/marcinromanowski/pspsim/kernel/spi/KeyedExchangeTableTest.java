package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class KeyedExchangeTableTest {

  private static final Instant AT = Instant.parse("2026-10-14T10:00:00Z");
  private static final OffsetDateTime AT_UTC = AT.atOffset(ZoneOffset.UTC);

  @Test
  void a_table_kept_under_a_named_key_inserts_its_answer_and_names_its_primary_key() {
    assertThat(KeyedExchangeTable.underANamedKey("x_exchange", "merchant_id", false).insert())
        .isEqualTo(
            "insert into x_exchange (test_run_id, merchant_id, key_name, key_value, seq,"
                + " verdict, operation, fingerprint, http_status, content_type, body,"
                + " provider_object_id, state_changed, at) select ?, ?, ?, ?,"
                + " coalesce(max(seq) + 1, 0), case when coalesce(bool_or(verdict in (?, ?, ?)),"
                + " false) then ? else ? end, ?, ?, ?, ?, ?, ?, ?, ? from x_exchange where"
                + " test_run_id = ? and merchant_id = ? and key_name = ? and key_value = ? on"
                + " conflict (test_run_id, merchant_id, key_name, key_value, seq) do nothing");
  }

  @Test
  void a_table_that_keeps_the_fault_inserts_its_two_columns_after_the_answer() {
    assertThat(
            KeyedExchangeTable.underItsOnlyKey("y_exchange", "account_id", "idempotency_key", true)
                .insert())
        .isEqualTo(
            "insert into y_exchange (test_run_id, account_id, idempotency_key, seq, verdict,"
                + " operation, fingerprint, http_status, content_type, body, provider_object_id,"
                + " state_changed, at, fault_kind, fault_placement) select ?, ?, ?,"
                + " coalesce(max(seq) + 1, 0), case when coalesce(bool_or(verdict in (?, ?, ?)),"
                + " false) then ? else ? end, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? from y_exchange where"
                + " test_run_id = ? and account_id = ? and idempotency_key = ? on conflict"
                + " (test_run_id, account_id, idempotency_key, seq) do nothing");
  }

  @Test
  void a_named_key_binds_its_name_and_its_value_in_the_order_the_insert_reads_them() {
    assertThat(
            KeyedExchangeTable.underANamedKey("x_exchange", "merchant_id", false)
                .parametersOf(aRow(null)))
        .containsExactly(
            "run-1",
            "merchant-1",
            "order",
            "o-1",
            "FIRST_REQUEST",
            "REPEAT_ADMITTED",
            "CORRELATED_RETRY",
            "REPEAT_ADMITTED",
            "FIRST_REQUEST",
            "charge",
            "fp",
            200,
            "application/json",
            "{}",
            "pi_1",
            true,
            AT_UTC,
            "run-1",
            "merchant-1",
            "order",
            "o-1");
  }

  @Test
  void its_only_key_binds_the_value_alone_and_a_kept_fault_binds_its_kind_and_placement() {
    AppliedFault fault =
        new AppliedFault(TransportFaultKind.SERVER_ERROR, FaultPlacement.AFTER_THE_OPERATION);

    assertThat(
            KeyedExchangeTable.underItsOnlyKey("y", "account_id", "idempotency_key", true)
                .parametersOf(aRow(fault)))
        .containsExactly(
            "run-1",
            "merchant-1",
            "o-1",
            "FIRST_REQUEST",
            "REPEAT_ADMITTED",
            "CORRELATED_RETRY",
            "REPEAT_ADMITTED",
            "FIRST_REQUEST",
            "charge",
            "fp",
            200,
            "application/json",
            "{}",
            "pi_1",
            true,
            AT_UTC,
            "SERVER_ERROR",
            "AFTER_THE_OPERATION",
            "run-1",
            "merchant-1",
            "o-1");
  }

  @Test
  void a_kept_fault_that_never_landed_binds_two_nulls() {
    assertThat(
            KeyedExchangeTable.underItsOnlyKey("y", "account_id", "idempotency_key", true)
                .parametersOf(aRow(null)))
        .containsExactlyElementsOf(
            Arrays.asList(
                "run-1",
                "merchant-1",
                "o-1",
                "FIRST_REQUEST",
                "REPEAT_ADMITTED",
                "CORRELATED_RETRY",
                "REPEAT_ADMITTED",
                "FIRST_REQUEST",
                "charge",
                "fp",
                200,
                "application/json",
                "{}",
                "pi_1",
                true,
                AT_UTC,
                null,
                null,
                "run-1",
                "merchant-1",
                "o-1"));
  }

  private static KeyedExchangeRow aRow(@Nullable AppliedFault fault) {
    return new KeyedExchangeRow(
        TestRunId.of("run-1"),
        "merchant-1",
        "order",
        "o-1",
        "charge",
        "fp",
        ExchangeVerdict.FIRST_REQUEST,
        ExchangeVerdict.REPEAT_ADMITTED,
        200,
        "application/json",
        "{}",
        "pi_1",
        true,
        fault,
        AT);
  }
}
