package dev.marcinromanowski.pspsim.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

class FingerprintKeyMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_fingerprint_was_keyed";

  private static final String A_RUN = "a-run-before-the-key";

  private static final String A_RUN_WITH_NO_CLOCK = "a-run-with-no-clock";

  private static final String AN_UNKEYED_DIGEST =
      "8da55c04add0b4c0dbe9b719ca8ac84f7019fd799404a2f181e62d14307273af";

  private static final String ANOTHER_UNKEYED_DIGEST =
      "78d892f2e92544575c80e3653972303dee23ae714171ebd7770669ce8ce4e3f9";

  private static final String A_MINTED_KEY =
      "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatKeptUnkeyedFingerprints() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("85")).migrate();
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_test_run (test_run_id, fixed_now) values (?,"
            + " timestamptz '2026-09-10T12:00:00Z')",
        A_RUN);
    jdbc.update(
        "insert into " + SCHEMA + ".simulator_test_run (test_run_id) values (?)",
        A_RUN_WITH_NO_CLOCK);
    anExchangeFingerprintedAs(0, AN_UNKEYED_DIGEST);
    anExchangeFingerprintedAs(1, AN_UNKEYED_DIGEST);
    anExchangeFingerprintedAs(2, ANOTHER_UNKEYED_DIGEST);
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_idempotency_key (test_run_id, account_id, idempotency_key, fingerprint,"
            + " in_progress, created_at) values (?, 'merchant-1', 'k-1', ?, true,"
            + " timestamptz '2026-09-10T12:00:00Z')",
        A_RUN,
        AN_UNKEYED_DIGEST);
    flyway(MigrationVersion.fromVersion("86")).migrate();
  }

  @Test
  void every_test_run_survives_with_its_clock_and_holds_no_key_until_one_is_minted_for_it() {
    assertThat(
            jdbc.queryForList(
                "select test_run_id, fixed_now is not null as pinned, fingerprint_key from "
                    + SCHEMA
                    + ".simulator_test_run order by test_run_id"))
        .extracting(
            row ->
                List.of(
                    row.get("test_run_id"),
                    row.get("pinned"),
                    String.valueOf(row.get("fingerprint_key"))))
        .containsExactly(List.of(A_RUN, true, "null"), List.of(A_RUN_WITH_NO_CLOCK, false, "null"));
  }

  @Test
  void a_fingerprint_kept_before_the_key_no_longer_reads_as_the_digest_a_guess_could_recompute() {
    List<String> exchanges =
        jdbc.queryForList(
            "select fingerprint from " + SCHEMA + ".grow_keyed_exchange order by seq",
            String.class);
    String claimed =
        jdbc.queryForObject(
            "select fingerprint from " + SCHEMA + ".stripe_idempotency_key", String.class);

    assertThat(exchanges)
        .hasSize(3)
        .allMatch(fingerprint -> fingerprint.matches("[0-9a-f]{64}"))
        .doesNotContain(AN_UNKEYED_DIGEST, ANOTHER_UNKEYED_DIGEST);
    assertThat(claimed).matches("[0-9a-f]{64}").isNotEqualTo(AN_UNKEYED_DIGEST);
  }

  @Test
  void two_requests_that_fingerprinted_alike_before_the_key_still_compare_alike_after_it() {
    List<String> exchanges =
        jdbc.queryForList(
            "select fingerprint from " + SCHEMA + ".grow_keyed_exchange order by seq",
            String.class);

    assertThat(exchanges.get(1)).isEqualTo(exchanges.get(0));
    assertThat(exchanges.get(2)).isNotEqualTo(exchanges.get(0));
  }

  @Test
  void the_store_keeps_only_a_key_of_the_shape_this_simulator_mints() {
    assertThatCode(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".simulator_test_run set fingerprint_key = ?"
                        + " where test_run_id = ?",
                    A_MINTED_KEY,
                    A_RUN))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update "
                        + SCHEMA
                        + ".simulator_test_run set fingerprint_key = ?"
                        + " where test_run_id = ?",
                    "not-a-minted-key",
                    A_RUN_WITH_NO_CLOCK))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void anExchangeFingerprintedAs(int seq, String fingerprint) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".grow_keyed_exchange (test_run_id, merchant_id, key_name, key_value, seq,"
            + " operation, fingerprint, verdict, http_status, content_type, body,"
            + " provider_object_id, state_changed, at) values (?, 'merchant-1',"
            + " 'transactionId', '7001', ?, 'getTransactionInfo', ?, ?, 200,"
            + " 'application/json', '{}', '7001', false, timestamptz '2026-09-10T12:00:00Z')",
        A_RUN,
        seq,
        fingerprint,
        seq == 0 ? "FIRST_REQUEST" : "CORRELATED_RETRY");
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
