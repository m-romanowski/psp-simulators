package dev.marcinromanowski.pspsim.provider.grow;

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

class GrowHostedLinkMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_link_identified_a_process";

  private static final String RUN = "before-a-link-identified-a-process";

  private static final String DECORATIVE = "89a71a421b8e0686024fbb762dab61e1";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWrittenWhileTheLinkIdentifiedNothing() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("47")).migrate();
    aProcessOfTheOldShape("700001", "https://sandbox.example/far?l=" + DECORATIVE);
    aProcessOfTheOldShape("700002", "https://sandbox.example/far?l=" + "b".repeat(32));
    aProcessOfTheOldShape("700003", "https://sandbox.example/far");
    flyway(MigrationVersion.LATEST).migrate();
  }

  @Test
  void a_process_written_before_the_column_existed_keeps_the_link_its_url_already_carried() {
    assertThat(hostedLinkOf("700001")).isEqualTo(DECORATIVE);
    assertThat(hostedLinkOf("700002")).isEqualTo("b".repeat(32));
  }

  @Test
  void the_url_the_link_was_taken_out_of_is_no_column_of_a_migrated_process_any_more() {
    assertThat(theColumnsOfAMigratedProcess())
        .as(
            "the address of the payer page is built from the link beside it and the base url this"
                + " simulator publishes itself under, so once this backfill had taken the link out"
                + " of it the column held a value nothing read back. It is dropped, and a row"
                + " written while it was the only place the link lived still carries that link")
        .contains("hosted_link")
        .doesNotContain("hosted_page_url");
    assertThat(hostedLinkOf("700001")).isEqualTo(DECORATIVE);
  }

  private List<String> theColumnsOfAMigratedProcess() {
    return jdbc.queryForList(
        "select column_name from information_schema.columns"
            + " where table_schema = ? and table_name = 'grow_payment_process'",
        String.class,
        SCHEMA);
  }

  @Test
  void a_process_whose_url_carried_no_link_at_all_is_still_given_one_of_its_own() {
    String link = hostedLinkOf("700003");

    assertThat(link).isNotBlank();
    assertThat(link).contains("700003");
  }

  @Test
  void no_migrated_process_is_left_without_a_link() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".grow_payment_process where hosted_link is null",
                Integer.class))
        .isZero();
  }

  @Test
  void two_processes_cannot_claim_one_link_once_the_migration_has_run() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".grow_payment_process set hosted_link = ?"
                        + " where process_id = '700002'",
                    DECORATIVE))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_process_written_after_the_migration_must_carry_a_link() {
    assertThatThrownBy(() -> aProcessOfTheNewShape("700004", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private String hostedLinkOf(String processId) {
    return jdbc.queryForObject(
        "select hosted_link from " + SCHEMA + ".grow_payment_process where process_id = ?",
        String.class,
        processId);
  }

  private void aProcessOfTheOldShape(String processId, String hostedPageUrl) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_payment_process (test_run_id, process_id, process_token, merchant_id,"
            + " charge_type, charge_sum, description, save_card_token, success_url, cancel_url,"
            + " notify_url, payer_full_name, payer_phone, payer_email, custom_fields,"
            + " hosted_page_url, native_status_code, native_status, normalized_status, origin,"
            + " created_at, updated_at)"
            + " values (?, ?, ?, 'merchant-1', '2', '120.50', 'A hold', true,"
            + " 'https://merchant.example/thanks', 'https://merchant.example/cancel',"
            + " 'https://merchant.example/callbacks/grow', 'Israel Israeli', '0505503144',"
            + " 'payer@merchant.example', '{}', ?, '0', 'not paid', 'NOT_INITIALIZED',"
            + " 'PROVIDER_API', timestamptz '2026-09-07T12:00:00Z',"
            + " timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        processId,
        "token-" + processId,
        hostedPageUrl);
  }

  private void aProcessOfTheNewShape(String processId, String hostedLink) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_payment_process (test_run_id, process_id, process_token, merchant_id,"
            + " charge_type, charge_sum, description, save_card_token, success_url, cancel_url,"
            + " notify_url, payer_full_name, payer_phone, payer_email, custom_fields,"
            + " hosted_link, native_status_code, native_status,"
            + " normalized_status, origin, created_at, updated_at)"
            + " values (?, ?, ?, 'merchant-1', '2', '120.50', 'A hold', true,"
            + " 'https://merchant.example/thanks', 'https://merchant.example/cancel',"
            + " 'https://merchant.example/callbacks/grow', 'Israel Israeli', '0505503144',"
            + " 'payer@merchant.example', '{}', ?, '0',"
            + " 'not paid', 'NOT_INITIALIZED', 'PROVIDER_API',"
            + " timestamptz '2026-09-07T12:00:00Z', timestamptz '2026-09-07T12:00:00Z')",
        RUN,
        processId,
        "token-" + processId,
        hostedLink);
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
