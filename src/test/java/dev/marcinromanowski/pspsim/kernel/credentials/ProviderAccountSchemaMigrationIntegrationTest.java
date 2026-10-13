package dev.marcinromanowski.pspsim.kernel.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class ProviderAccountSchemaMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_provider_owned_its_account_schema";

  private static final String RUN = "before-the-split";

  private static final MigrationVersion BEFORE_THE_SPLIT = MigrationVersion.fromVersion("26");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatKeptEveryProvidersCredentialsInOneTable() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_THE_SPLIT).migrate();
    growAccountOfTheOldShape(
        "merchant-1",
        "grow-user-1",
        "grow-page-1",
        "88888888",
        "{\"captureWindow\":\"P3D\",\"callbackAcknowledgement\":\"APPROVE_TRANSACTION\","
            + "\"refundAllowance\":\"TWO_REFUNDS\"}");
    growAccountOfTheOldShape("merchant-2", "grow-user-2", "grow-page-2", null, "{}");
    azericardAccountOfTheOldShape(
        "merchant-3",
        "77777777",
        "{\"azericard.partial-completion\":\"LEAVES_THE_REMAINDER_CAPTURABLE\"}");
    webhookTargetOfTheOldShape("merchant-1", "https://merchant.example/hook");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void a_grow_account_keeps_the_credentials_it_authenticated_on_as_columns_of_its_own_table() {
    Map<String, Object> account = growAccount("merchant-1");

    assertThat(account)
        .containsEntry("user_id", "grow-user-1")
        .containsEntry("page_code", "grow-page-1");
  }

  @Test
  void a_grow_account_keeps_the_settings_it_was_configured_with_as_typed_columns() {
    Map<String, Object> account = growAccount("merchant-1");

    assertThat(account)
        .containsEntry("capture_window", "P3D")
        .containsEntry("callback_acknowledgement", "APPROVE_TRANSACTION")
        .containsEntry("refund_allowance", "TWO_REFUNDS");
  }

  @Test
  void a_grow_account_that_configured_no_setting_carries_the_defaults_grow_publishes() {
    Map<String, Object> account = growAccount("merchant-2");

    assertThat(account)
        .containsEntry("callback_acknowledgement", "HTTP_2XX")
        .containsEntry("settlement_ceiling", "FRAME_CAPTURE")
        .containsEntry("refund_allowance", "ONE_REFUND")
        .containsEntry("refund_excess_rejection", "ORIGINAL_AMOUNT")
        .containsEntry("merchant_balance", "AVAILABLE")
        .containsEntry("process_lookup_rejection", "PUBLISHED_EXAMPLE")
        .containsEntry("capture_window", "PT168H")
        .containsEntry("automatic_void_after", "PT240H");
  }

  @Test
  void an_azericard_account_keeps_its_terminal_its_key_pair_and_its_setting() {
    Map<String, Object> terminal =
        jdbc.queryForMap(
            "select * from " + SCHEMA + ".azericard_terminal where account_id = 'merchant-3'");

    assertThat(terminal)
        .containsEntry("terminal_id", "77777777")
        .containsEntry("merchant_key", "a-merchant-public-key")
        .containsEntry("gateway_key", "a-gateway-private-key")
        .containsEntry("partial_completion", "LEAVES_THE_REMAINDER_CAPTURABLE")
        .containsEntry("duplicate_response_code", "-1");
  }

  @Test
  void the_terminal_a_grow_account_carried_and_never_read_is_carried_into_no_provider_table() {
    assertThat(
            jdbc.queryForList(
                "select account_id from " + SCHEMA + ".azericard_terminal order by account_id",
                String.class))
        .containsExactly("merchant-3");
  }

  @Test
  void the_core_keeps_every_identity_it_was_configured_with_and_none_of_the_credentials() {
    assertThat(
            jdbc.queryForList(
                "select account_id from "
                    + SCHEMA
                    + ".simulator_provider_account order by account_id",
                String.class))
        .containsExactly("merchant-1", "merchant-2", "merchant-3");
    assertThat(columnsOf("simulator_provider_account"))
        .containsExactlyInAnyOrder("provider", "test_run_id", "account_id", "updated_at");
  }

  @Test
  void a_webhook_target_keeps_the_account_it_belongs_to_and_loses_the_signing_fields() {
    assertThat(
            jdbc.queryForList(
                "select account_id from " + SCHEMA + ".simulator_webhook_target", String.class))
        .containsExactly("merchant-1");
    assertThat(columnsOf("simulator_webhook_target"))
        .containsExactlyInAnyOrder("provider", "test_run_id", "account_id", "url", "updated_at");
  }

  @Test
  void the_grow_table_refuses_a_settlement_ceiling_no_grow_account_could_read() {
    assertThatCode(() -> aGrowAccountWith("merchant-4", "FRAME_CAPTURE"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> aGrowAccountWith("merchant-5", "AS_HIGH_AS_YOU_LIKE"))
        .isInstanceOf(DataAccessException.class);
  }

  private void aGrowAccountWith(String accountId, String settlementCeiling) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_account (test_run_id, account_id, user_id, page_code,"
            + " callback_acknowledgement, settlement_ceiling, refund_allowance,"
            + " refund_excess_rejection, merchant_balance, process_lookup_rejection, wire_form,"
            + " capture_window, automatic_void_after)"
            + " values (?, ?, ?, ?, 'HTTP_2XX', ?, 'ONE_REFUND', 'ORIGINAL_AMOUNT', 'AVAILABLE',"
            + " 'PUBLISHED_EXAMPLE', 'LIVE_WIRE', 'PT168H', 'PT240H')",
        RUN,
        accountId,
        "user-" + accountId,
        "page-" + accountId,
        settlementCeiling);
  }

  private Map<String, Object> growAccount(String accountId) {
    return jdbc.queryForMap(
        "select * from " + SCHEMA + ".grow_account where account_id = ?", accountId);
  }

  private List<String> columnsOf(String table) {
    return jdbc.queryForList(
        "select column_name from information_schema.columns"
            + " where table_schema = ? and table_name = ?",
        String.class,
        SCHEMA,
        table);
  }

  private void growAccountOfTheOldShape(
      String merchantId, String userId, String pageCode, String terminalId, String settings) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_provider_account"
            + " (provider, test_run_id, merchant_id, user_id, page_code, terminal_id, settings,"
            + " updated_at)"
            + " values ('grow', ?, ?, ?, ?, ?, ?, timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        merchantId,
        userId,
        pageCode,
        terminalId,
        settings);
  }

  private void azericardAccountOfTheOldShape(
      String merchantId, String terminalId, String settings) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_provider_account"
            + " (provider, test_run_id, merchant_id, terminal_id, api_key, api_secret, settings,"
            + " updated_at)"
            + " values ('azericard', ?, ?, ?, 'a-merchant-public-key', 'a-gateway-private-key', ?,"
            + " timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        merchantId,
        terminalId,
        settings);
  }

  private void webhookTargetOfTheOldShape(String merchantId, String url) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_webhook_target"
            + " (provider, test_run_id, merchant_id, url, secret, key_id, updated_at)"
            + " values ('grow', ?, ?, ?, 'webhook-secret', 'webhook-key-id',"
            + " timestamptz '2026-09-10T12:00:00Z')",
        RUN,
        merchantId,
        url);
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
