package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class GrowPaymentProcessIsAnObjectMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_grow_process_was_an_object";

  private static final String RUN = "before-a-process-was-an-object";

  private static final String MERCHANT = "merchant-1";

  private static final MigrationVersion TWO_NAMESPACES = MigrationVersion.fromVersion("50");

  private static final MigrationVersion ONE_NAMESPACE = MigrationVersion.fromVersion("51");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseProcessesAndTransactionsHadNamespacesOfTheirOwn() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(TWO_NAMESPACES).migrate();

    aProcessThatNamesNoArrival("900002", "2026-09-07T12:00:10Z");
    aProcessIdClaimOfTheOldShape("900002");
    aProcessThatNamesNoArrival("900001", "2026-09-07T12:00:00Z");
    aProcessIdClaimOfTheOldShape("900001");
    aProcessThatNamesNoArrival("900003", "2026-09-07T12:00:20Z");
    aProcessThatNamesNoArrival("700001", "2026-09-07T12:00:05Z");
    aProcessIdClaimOfTheOldShape("700001");

    aPaymentOfTheOldShape("100001", "900001");
    aTransactionIdClaimOfTheOldShape("100001");
    aPaymentOfTheOldShape("700001", "700001");
    aTransactionIdClaimOfTheOldShape("700001");
    aPaymentOfTheOldShape("100002", "900002");
    aTransactionIdClaimOfTheOldShape("100002");
    aPaymentOfTheOldShape("100003", "900003");

    flyway(ONE_NAMESPACE).migrate();
  }

  @Test
  void every_id_a_run_already_held_is_claimed_under_the_kind_of_the_object_that_holds_it() {
    assertThat(theKindClaiming("900001")).isEqualTo(GrowObjectId.PROCESS);
    assertThat(theKindClaiming("900002")).isEqualTo(GrowObjectId.PROCESS);
    assertThat(theKindClaiming("100001")).isEqualTo(GrowObjectId.TRANSACTION);
    assertThat(theKindClaiming("100002")).isEqualTo(GrowObjectId.TRANSACTION);
  }

  @Test
  void an_object_the_old_claim_tables_never_held_is_claimed_from_the_row_that_holds_its_id() {
    assertThat(theKindClaiming("900003")).isEqualTo(GrowObjectId.PROCESS);
    assertThat(theKindClaiming("100003")).isEqualTo(GrowObjectId.TRANSACTION);
  }

  @Test
  void an_id_two_namespaces_let_name_two_objects_is_claimed_once_and_by_the_process() {
    assertThat(
            jdbc.queryForList(
                "select kind from " + SCHEMA + ".grow_object_id_claim where object_id = ?",
                String.class,
                "700001"))
        .containsExactly(GrowObjectId.PROCESS);
  }

  @Test
  void an_upgrade_deletes_neither_of_the_two_objects_one_id_named_before_it() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".grow_payment_process where process_id = ?",
                Long.class,
                "700001"))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".grow_transaction where transaction_id = ?",
                Long.class,
                "700001"))
        .isEqualTo(1L);
  }

  @Test
  void the_upgraded_run_refuses_a_transaction_claim_on_an_id_a_payment_process_already_holds() {
    assertThatThrownBy(() -> aClaim("900001", GrowObjectId.TRANSACTION))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void every_process_a_run_already_held_keeps_a_place_of_its_own_in_the_list() {
    List<Long> arrivals =
        jdbc.queryForList(
            "select arrival from " + SCHEMA + ".grow_payment_process where test_run_id = ?",
            Long.class,
            RUN);

    assertThat(arrivals).hasSize(4);
    assertThat(arrivals).doesNotHaveDuplicates();
  }

  @Test
  void a_process_a_run_already_held_lists_below_the_transaction_its_completion_created() {
    assertThat(arrivalOfProcess("900001")).isLessThan(arrivalOfTransaction("100001"));
    assertThat(arrivalOfProcess("900002")).isLessThan(arrivalOfTransaction("100002"));
    assertThat(arrivalOfProcess("900003")).isLessThan(arrivalOfTransaction("100003"));
  }

  @Test
  void processes_a_run_already_held_keep_the_order_they_were_created_in() {
    assertThat(
            jdbc.queryForList(
                "select process_id from "
                    + SCHEMA
                    + ".grow_payment_process where test_run_id = ? order by arrival desc",
                String.class,
                RUN))
        .containsExactly("900003", "900002", "700001", "900001");
  }

  @Test
  void a_process_created_after_the_upgrade_lists_above_every_object_that_was_there_before_it() {
    aProcessThatNamesNoArrival("900004", "2026-09-07T13:00:00Z");

    assertThat(arrivalOfProcess("900004")).isGreaterThan(arrivalOfProcess("900003"));
    assertThat(arrivalOfProcess("900004")).isGreaterThan(arrivalOfTransaction("100003"));
  }

  private String theKindClaiming(String objectId) {
    return jdbc.queryForObject(
        "select kind from "
            + SCHEMA
            + ".grow_object_id_claim where test_run_id = ?"
            + " and object_id = ?",
        String.class,
        RUN,
        objectId);
  }

  private long arrivalOfProcess(String processId) {
    Long arrival =
        jdbc.queryForObject(
            "select arrival from "
                + SCHEMA
                + ".grow_payment_process where test_run_id = ? and process_id = ?",
            Long.class,
            RUN,
            processId);
    assertThat(arrival).isNotNull();
    return arrival;
  }

  private long arrivalOfTransaction(String transactionId) {
    Long arrival =
        jdbc.queryForObject(
            "select arrival from "
                + SCHEMA
                + ".grow_transaction where test_run_id = ? and transaction_id = ?",
            Long.class,
            RUN,
            transactionId);
    assertThat(arrival).isNotNull();
    return arrival;
  }

  private void aClaim(String objectId, String kind) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_object_id_claim (test_run_id, object_id, kind) values (?, ?, ?)",
        RUN,
        objectId,
        kind);
  }

  private void aProcessIdClaimOfTheOldShape(String processId) {
    jdbc.update(
        "insert into " + SCHEMA + ".grow_process_id_claim (test_run_id, process_id) values (?, ?)",
        RUN,
        processId);
  }

  private void aTransactionIdClaimOfTheOldShape(String transactionId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction_id_claim (test_run_id, transaction_id) values (?, ?)",
        RUN,
        transactionId);
  }

  private void aProcessThatNamesNoArrival(String processId, String createdAt) {
    OffsetDateTime when = OffsetDateTime.parse(createdAt);
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_payment_process (test_run_id, process_id, process_token, merchant_id,"
            + " charge_type, charge_sum, description, save_card_token, success_url, cancel_url,"
            + " notify_url, payer_full_name, payer_phone, payer_email, custom_fields,"
            + " hosted_page_url, hosted_link, native_status_code, native_status,"
            + " normalized_status, origin, created_at, updated_at)"
            + " values (?, ?, ?, ?, '2', '120.50', 'A hold', true,"
            + " 'https://merchant.example/thanks', 'https://merchant.example/cancel',"
            + " 'https://merchant.example/hook', 'Israel Israeli', '0505503144',"
            + " 'payer@merchant.example', '{}', ?, ?, '0', 'Not Paid', 'NOT_INITIALIZED',"
            + " 'PROVIDER_API', ?, ?)",
        RUN,
        processId,
        "token-" + processId,
        MERCHANT,
        "https://simulator.internal/far?l=link-" + processId,
        "link-" + processId,
        when,
        when);
  }

  private void aPaymentOfTheOldShape(String transactionId, String processId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction (test_run_id, kind, transaction_id, transaction_token,"
            + " merchant_id, process_id, refunded_transaction_id, asmachta, payment_date,"
            + " description, payer_full_name, payer_phone, card_suffix, card_type,"
            + " card_type_code, card_brand, card_brand_code, card_exp, amount, origin,"
            + " created_at, updated_at)"
            + " values (?, 'PAYMENT', ?, ?, ?, ?, null, '89474280', '07/09/26', 'A payment',"
            + " 'Israel Israeli', '0505503144', '4580', 'Foreign', '2', 'Visa', '3', '0424',"
            + " '120.50', 'PROVIDER_API', timestamptz '2026-09-07T12:30:00Z',"
            + " timestamptz '2026-09-07T12:30:00Z')",
        RUN,
        transactionId,
        "token-" + transactionId,
        MERCHANT,
        processId);
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
