package dev.marcinromanowski.pspsim.kernel.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationCreatedObjectMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_an_operation_named_what_it_created";

  private static final String RUN = "before-an-operation-named-what-it-created";

  private static final String MERCHANT = "merchant-1";

  private static final String TERMINAL = "77777777";

  private static final String INT_REF = "INTREF0000000001";

  private static final String PAYMENT = "500001";

  private static final String REFUND = "500002";

  private static final MigrationVersion BEFORE_AN_OPERATION_NAMED_WHAT_IT_CREATED =
      MigrationVersion.fromVersion("24");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseOperationsCarriedBothMeanings() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_AN_OPERATION_NAMED_WHAT_IT_CREATED).migrate();
    aGrowPaymentOfTheOldShape();
    aGrowRefundRowOfTheOldShape();
    aGrowOperationOfTheOldShape(1, "AUTHORIZATION", "120.50", null);
    aGrowOperationOfTheOldShape(2, "CAPTURE", "120.50", PAYMENT);
    aGrowOperationOfTheOldShape(3, "REFUND", "120.50", REFUND);
    anAzericardPaymentOfTheOldShape();
    anAzericardOperationOfTheOldShape(1, "AUTHORIZATION", "11.48", INT_REF);
    anAzericardOperationOfTheOldShape(2, "CAPTURE", "11.48", INT_REF);
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void an_azericard_operation_that_repeated_the_payments_own_reference_now_names_no_object() {
    assertThat(azericardCreatedObjectIds()).containsExactly(null, null);
  }

  @Test
  void a_grow_refund_operation_keeps_the_id_of_the_refund_object_that_refund_created() {
    assertThat(growCreatedObjectIds()).containsExactly(null, null, REFUND);
  }

  @Test
  void the_refund_object_a_kept_id_names_is_still_there_to_be_looked_up() {
    assertThat(
            jdbc.queryForObject(
                "select refunded_transaction_id from "
                    + SCHEMA
                    + ".grow_transaction where transaction_id = ?",
                String.class,
                REFUND))
        .isEqualTo(PAYMENT);
  }

  @Test
  void a_grow_operation_that_repeated_its_own_transactions_id_now_names_no_object_either() {
    assertThat(growCreatedObjectIds().get(1)).isNull();
  }

  @Test
  void the_grow_table_refuses_an_operation_naming_the_transaction_whose_ledger_it_is_on() {
    assertThatThrownBy(() -> aGrowOperationOfTheMigratedTable(4, PAYMENT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_azericard_table_refuses_an_operation_naming_the_payment_whose_ledger_it_is_on() {
    assertThatThrownBy(() -> anAzericardOperationOfTheMigratedTable(3, INT_REF))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void both_tables_admit_an_operation_naming_a_second_object_it_brought_into_existence() {
    aGrowOperationOfTheMigratedTable(4, "500003");
    anAzericardOperationOfTheMigratedTable(3, "INTREF0000000002");

    assertThat(growCreatedObjectIds()).containsExactly(null, null, REFUND, "500003");
    assertThat(azericardCreatedObjectIds()).containsExactly(null, null, "INTREF0000000002");
  }

  private List<String> growCreatedObjectIds() {
    return jdbc.queryForList(
        "select created_object_id from "
            + SCHEMA
            + ".grow_transaction_operation where transaction_id = ? order by seq",
        String.class,
        PAYMENT);
  }

  private List<String> azericardCreatedObjectIds() {
    return jdbc.queryForList(
        "select created_object_id from "
            + SCHEMA
            + ".azericard_payment_operation where int_ref = ? order by seq",
        String.class,
        INT_REF);
  }

  private void aGrowPaymentOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction (test_run_id, kind, transaction_id, transaction_token,"
            + " merchant_id, process_id, refunded_transaction_id, asmachta, payment_date,"
            + " description, payer_full_name, payer_phone, amount, origin, created_at, updated_at)"
            + " values (?, 'PAYMENT', ?, 'tok-500001', ?, '900001', null, '89474280', '07/09/26',"
            + " 'A hold', 'Israel Israeli', '0505503144', '120.50', 'PROVIDER_API',"
            + " timestamptz '2026-09-07T12:00:00Z', timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        PAYMENT,
        MERCHANT);
  }

  private void aGrowRefundRowOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction (test_run_id, kind, transaction_id, transaction_token,"
            + " merchant_id, process_id, refunded_transaction_id, asmachta, payment_date,"
            + " description, payer_full_name, payer_phone, amount, origin, created_at, updated_at)"
            + " values (?, 'REFUND', ?, null, ?, null, ?, '89474281', '07/09/26', 'A refund',"
            + " 'Israel Israeli', '0505503144', '120.50', 'PROVIDER_API',"
            + " timestamptz '2026-09-07T12:00:00Z', timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        REFUND,
        MERCHANT,
        PAYMENT);
  }

  private void aGrowOperationOfTheOldShape(int seq, String kind, String amount, String named) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction_operation (test_run_id, transaction_id, seq, kind, amount,"
            + " closes_authorization, provider_object_id, native_status, normalized_status,"
            + " created_at)"
            + " values (?, ?, ?, ?, ?, null, ?, '2', 'SUCCEEDED',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        PAYMENT,
        seq,
        kind,
        amount,
        named);
  }

  private void aGrowOperationOfTheMigratedTable(int seq, String named) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction_operation (test_run_id, transaction_id, seq, kind, amount,"
            + " closes_authorization, created_object_id, native_status, normalized_status,"
            + " created_at)"
            + " values (?, ?, ?, 'REFUND', '0.00', null, ?, '3', 'SUCCEEDED',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        PAYMENT,
        seq,
        named);
  }

  private void anAzericardPaymentOfTheOldShape() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_payment (test_run_id, int_ref, terminal_id, merchant_id, order_id,"
            + " trtype, amount, currency, approval, rrn, action_code, response_code, origin,"
            + " created_at, updated_at)"
            + " values (?, ?, ?, ?, '20220628084800', '1', '11.48', 'AZN', '111111',"
            + " '112676199769', '0', '00', 'PROVIDER_API', timestamptz '2026-09-07T12:00:00Z',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        INT_REF,
        TERMINAL,
        MERCHANT);
  }

  private void anAzericardOperationOfTheOldShape(
      int seq, String kind, String amount, String named) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_payment_operation (test_run_id, int_ref, seq, kind, amount,"
            + " closes_authorization, provider_object_id, native_status, normalized_status,"
            + " created_at)"
            + " values (?, ?, ?, ?, ?, null, ?, '0/00', 'SUCCEEDED',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        INT_REF,
        seq,
        kind,
        amount,
        named);
  }

  private void anAzericardOperationOfTheMigratedTable(int seq, String named) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".azericard_payment_operation (test_run_id, int_ref, seq, kind, amount,"
            + " closes_authorization, created_object_id, native_status, normalized_status,"
            + " created_at)"
            + " values (?, ?, ?, 'REFUND', '0.00', null, ?, '0/00', 'SUCCEEDED',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        INT_REF,
        seq,
        named);
  }

  private Flyway flyway(MigrationVersion target) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .schemas(SCHEMA)
        .target(target)
        .load();
  }
}
