package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class GrowStoredCardChargeMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_card_could_be_charged";

  private static final String RUN = "before-a-card-could-be-charged";

  private static final String MERCHANT = "merchant-1";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWrittenBeforeAStoredCardCouldBeCharged() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("20")).migrate();
    aHoldOfTheOldShape("500001", "tok-500001", "900001");
    aRefundOfTheOldShape("500002", "500001");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void every_transaction_written_before_the_claim_table_existed_holds_its_id_against_a_new_one() {
    assertThat(
            jdbc.queryForList(
                "select object_id from "
                    + SCHEMA
                    + ".grow_object_id_claim where kind = 'TRANSACTION'"
                    + " order by object_id",
                String.class))
        .containsExactly("500001", "500002");
  }

  @Test
  void a_charge_cannot_draw_the_id_of_a_transaction_written_before_the_claim_table_existed() {
    assertThatThrownBy(() -> claim("500001")).isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_migrated_table_still_holds_the_hold_and_the_refund_it_was_migrated_with() {
    assertThat(
            jdbc.queryForList(
                "select kind from " + SCHEMA + ".grow_transaction order by transaction_id",
                String.class))
        .containsExactly("PAYMENT", "REFUND");
  }

  @Test
  void the_migrated_table_admits_a_stored_card_charge_that_belongs_to_no_hosted_process() {
    aStoredCardChargeOfTheNewShape("500003", "tok-500003");

    assertThat(
            jdbc.queryForObject(
                "select process_id from " + SCHEMA + ".grow_transaction where transaction_id = ?",
                String.class,
                "500003"))
        .isNull();
  }

  @Test
  void the_migrated_table_refuses_a_stored_card_charge_with_no_token_to_address_it_by() {
    assertThatThrownBy(() -> aStoredCardChargeOfTheNewShape("500004", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_migrated_table_still_refuses_a_hold_that_belongs_to_no_hosted_process() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".grow_transaction set process_id = null"
                        + " where transaction_id = '500001'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_submission_table_refuses_a_second_charge_carrying_one_group_identifier() {
    aStoredCardChargeOfTheNewShape("500005", "tok-500005");
    aStoredCardChargeOfTheNewShape("500006", "tok-500006");
    aSubmission("first-attempt", "basket-7", "500005");

    assertThatThrownBy(() -> aSubmission("second-attempt", "basket-7", "500006"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_submission_table_admits_one_group_identifier_across_submissions_that_charged_nothing() {
    aSubmission("first-attempt", "basket-7", null);
    aSubmission("second-attempt", "basket-7", null);

    assertThat(
            jdbc.queryForList(
                "select unique_identifier from "
                    + SCHEMA
                    + ".grow_stored_card_charge"
                    + " order by unique_identifier",
                String.class))
        .containsExactly("first-attempt", "second-attempt");
  }

  @Test
  void the_submission_table_refuses_a_repeat_of_one_unique_identifier_for_one_business() {
    aSubmission("first-attempt", null, null);

    assertThatThrownBy(() -> aSubmission("first-attempt", null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void claim(String transactionId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_object_id_claim (test_run_id, object_id, kind)"
            + " values (?, ?, 'TRANSACTION')",
        RUN,
        transactionId);
  }

  private void aSubmission(String uniqueIdentifier, String groupIdentifier, String transactionId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_stored_card_charge (test_run_id, merchant_id, unique_identifier,"
            + " group_identifier, transaction_id, custom_fields, created_at)"
            + " values (?, ?, ?, ?, ?, '{}', timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        MERCHANT,
        uniqueIdentifier,
        groupIdentifier,
        transactionId);
  }

  private void aStoredCardChargeOfTheNewShape(String transactionId, String transactionToken) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction (test_run_id, kind, transaction_id, transaction_token,"
            + " merchant_id, process_id, refunded_transaction_id, asmachta, payment_date,"
            + " description, payer_full_name, payer_phone, card_suffix, card_type,"
            + " card_type_code, card_brand, card_brand_code, card_exp, amount, origin,"
            + " created_at, updated_at)"
            + " values (?, 'TOKEN_PAYMENT', ?, ?, ?, null, null, '89475113', '07/09/26',"
            + " 'A stored-card charge', 'Israel Israeli', '0505503144', '4580', 'Foreign', '2',"
            + " 'Visa', '3', '0424', '89.90', 'PROVIDER_API',"
            + " timestamptz '2026-09-07T12:00:00Z', timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        transactionId,
        transactionToken,
        MERCHANT);
  }

  private void aHoldOfTheOldShape(String transactionId, String transactionToken, String processId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction (test_run_id, kind, transaction_id, transaction_token,"
            + " merchant_id, process_id, refunded_transaction_id, asmachta, payment_date,"
            + " description, payer_full_name, payer_phone, amount, origin, created_at, updated_at)"
            + " values (?, 'PAYMENT', ?, ?, ?, ?, null, '89474280', '07/09/26', 'A hold',"
            + " 'Israel Israeli', '0505503144', '120.50', 'PROVIDER_API',"
            + " timestamptz '2026-09-07T12:00:00Z', timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        transactionId,
        transactionToken,
        MERCHANT,
        processId);
  }

  private void aRefundOfTheOldShape(String transactionId, String refundedTransactionId) {
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
        transactionId,
        MERCHANT,
        refundedTransactionId);
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
