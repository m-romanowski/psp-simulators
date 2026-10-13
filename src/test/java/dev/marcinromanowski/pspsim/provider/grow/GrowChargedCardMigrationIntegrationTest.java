package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class GrowChargedCardMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_transaction_named_its_card";

  private static final String RUN = "before-a-transaction-named-its-card";

  private static final String MERCHANT = "merchant-1";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWrittenWhileEveryTransactionRenderedOneConstant() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("49")).migrate();
    aHoldOfTheOldShape("500001", "900500001");
    aRefundOfTheOldShape("500002", "500001");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void a_payment_written_before_the_columns_existed_keeps_describing_the_card_it_used_to_render() {
    assertThat(cardOf("500001"))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "card_suffix", GrowSandboxCard.SUFFIX,
                "card_type", GrowSandboxCard.TYPE,
                "card_type_code", GrowSandboxCard.TYPE_CODE,
                "card_brand", GrowSandboxCard.BRAND,
                "card_brand_code", GrowSandboxCard.BRAND_CODE,
                "card_exp", GrowSandboxCard.EXPIRY));
  }

  @Test
  void a_refund_written_before_the_columns_existed_describes_the_same_card_as_the_payment() {
    assertThat(cardOf("500002")).isEqualTo(cardOf("500001"));
  }

  @Test
  void no_migrated_transaction_is_left_without_a_card() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".grow_transaction where card_suffix is null or card_type is null"
                    + " or card_type_code is null or card_brand is null"
                    + " or card_brand_code is null or card_exp is null",
                Integer.class))
        .isZero();
  }

  @Test
  void a_transaction_written_after_the_migration_must_name_the_card_it_charged() {
    assertThatThrownBy(() -> aHoldOfTheOldShape("500003", "900500003"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Map<String, Object> cardOf(String transactionId) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "select card_suffix, card_type, card_type_code, card_brand, card_brand_code, card_exp"
                + " from "
                + SCHEMA
                + ".grow_transaction where transaction_id = ?",
            transactionId);
    assertThat(rows).hasSize(1);
    return rows.get(0);
  }

  private void aHoldOfTheOldShape(String transactionId, String processId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction (test_run_id, kind, transaction_id, transaction_token,"
            + " merchant_id, process_id, refunded_transaction_id, asmachta, payment_date,"
            + " description, payer_full_name, payer_phone, payer_email, amount, origin,"
            + " created_at, updated_at)"
            + " values (?, 'PAYMENT', ?, ?, ?, ?, null, '89474280', '07/09/26', 'A hold',"
            + " 'Israel Israeli', '0505503144', 'payer@merchant.example', '120.50',"
            + " 'PROVIDER_API', timestamptz '2026-09-07T12:00:00Z',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        transactionId,
        "token-" + transactionId,
        MERCHANT,
        processId);
  }

  private void aRefundOfTheOldShape(String transactionId, String refundedTransactionId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_transaction (test_run_id, kind, transaction_id, transaction_token,"
            + " merchant_id, process_id, refunded_transaction_id, asmachta, payment_date,"
            + " description, payer_full_name, payer_phone, payer_email, amount, origin,"
            + " created_at, updated_at)"
            + " values (?, 'REFUND', ?, null, ?, null, ?, '89474281', '07/09/26', 'A refund',"
            + " 'Israel Israeli', '0505503144', 'payer@merchant.example', '120.50',"
            + " 'PROVIDER_API', timestamptz '2026-09-07T12:00:00Z',"
            + " timestamptz '2026-09-07T12:00:00Z')",
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
