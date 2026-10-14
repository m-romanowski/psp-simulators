package dev.marcinromanowski.pspsim.platform;

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

  private static final String A_TOKEN_THE_TRAIL_WITHHOLDS = "a-token-the-trail-withholds";

  private static final String A_WITHHELD_BODY = "{\"token\":[\"[redacted]\"]}";

  private static final Map<String, String> KEPT_UNWITHHELD =
      Map.of(
          "grow_keyed_exchange", "merchant_id",
          "azericard_keyed_exchange", "account_id",
          "autra_keyed_exchange", "account_id",
          "worldline_keyed_exchange", "account_id");

  private static final Map<String, String> KEPT_WITHHELD =
      Map.of("tranzila_keyed_exchange", "account_id");

  private static final String AT = "timestamptz '2026-09-10T12:00:00Z'";

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatKeptUnkeyedFingerprintsAndUnwithheldAnswers() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(MigrationVersion.fromVersion("85")).migrate();
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_test_run (test_run_id, fixed_now) values (?, "
            + AT
            + ")",
        A_RUN);
    jdbc.update(
        "insert into " + SCHEMA + ".simulator_test_run (test_run_id) values (?)",
        A_RUN_WITH_NO_CLOCK);
    KEPT_UNWITHHELD.forEach(
        (table, account) ->
            threeExchanges(table, account, "{\"token\":\"" + A_TOKEN_THE_TRAIL_WITHHOLDS + "\"}"));
    KEPT_WITHHELD.forEach((table, account) -> threeExchanges(table, account, A_WITHHELD_BODY));
    for (int place = 0; place < 3; place++) {
      jdbc.update(
          "insert into "
              + SCHEMA
              + ".stripe_idempotency_exchange (test_run_id, account_id, idempotency_key, seq,"
              + " operation, fingerprint, verdict, http_status, content_type, body,"
              + " provider_object_id, state_changed, at) values (?, 'merchant-1', ?, 0, 'op', ?,"
              + " 'FIRST_REQUEST', 200, 'application/json', ?, null, false, "
              + AT
              + ")",
          A_RUN,
          "k-" + place,
          fingerprintAt(place),
          A_WITHHELD_BODY);
    }
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_idempotency_key (test_run_id, account_id, idempotency_key, fingerprint,"
            + " in_progress, created_at) values (?, 'merchant-1', 'k-1', ?, true, "
            + AT
            + ")",
        A_RUN,
        AN_UNKEYED_DIGEST);
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_idempotence_key (test_run_id, account_id, key_value, header_name,"
            + " operation, fingerprint, claim_state, claimed_at) values (?, 'account-1', 'k-1',"
            + " 'X-GCS-Idempotence-Key', 'op', ?, 'RUNNING', "
            + AT
            + ")",
        A_RUN,
        AN_UNKEYED_DIGEST);
    flyway(MigrationVersion.fromVersion("86")).migrate();
  }

  @Test
  void every_test_run_survives_with_its_clock_and_the_migration_registers_no_key_for_it() {
    assertThat(
            jdbc.queryForList(
                "select test_run_id from " + SCHEMA + ".simulator_test_run order by test_run_id",
                String.class))
        .containsExactly(A_RUN, A_RUN_WITH_NO_CLOCK);
    assertThat(
            jdbc.queryForObject(
                "select to_char(fixed_now at time zone 'UTC', 'YYYY-MM-DD HH24:MI:SS') from "
                    + SCHEMA
                    + ".simulator_test_run where test_run_id = ?",
                String.class,
                A_RUN))
        .startsWith("2026-09-10 12:00:00");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from " + SCHEMA + ".simulator_fingerprint_key", Integer.class))
        .isZero();
  }

  @Test
  void no_fingerprint_kept_before_the_key_reads_as_the_digest_a_guess_could_recompute() {
    for (String table : everyTableThatKeepsAFingerprint()) {
      assertThat(fingerprintsIn(table))
          .as(table)
          .isNotEmpty()
          .allMatch(fingerprint -> fingerprint.matches("[0-9a-f]{64}"))
          .doesNotContain(AN_UNKEYED_DIGEST, ANOTHER_UNKEYED_DIGEST);
    }
  }

  @Test
  void two_requests_that_fingerprinted_alike_before_the_key_still_compare_alike_after_it() {
    for (String table : everyExchangeTable()) {
      List<String> fingerprints = fingerprintsIn(table);
      assertThat(fingerprints.get(1)).as(table).isEqualTo(fingerprints.get(0));
      assertThat(fingerprints.get(2)).as(table).isNotEqualTo(fingerprints.get(0));
    }
  }

  @Test
  void an_answer_kept_with_nothing_withheld_is_emptied_and_one_kept_withheld_is_kept() {
    for (String table : KEPT_UNWITHHELD.keySet()) {
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from "
                      + SCHEMA
                      + "."
                      + table
                      + " where http_status is not null or content_type is not null"
                      + " or body is not null",
                  Integer.class))
          .as(table)
          .isZero();
    }
    for (String table : List.of("tranzila_keyed_exchange", "stripe_idempotency_exchange")) {
      assertThat(jdbc.queryForList("select body from " + SCHEMA + "." + table, String.class))
          .as(table)
          .hasSize(3)
          .containsOnly(A_WITHHELD_BODY);
    }
  }

  @Test
  void the_store_keeps_one_key_per_run_and_only_of_the_shape_this_simulator_mints() {
    assertThatCode(
            () ->
                jdbc.update(
                    "insert into " + SCHEMA + ".simulator_fingerprint_key values (?, ?)",
                    A_RUN,
                    A_MINTED_KEY))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into " + SCHEMA + ".simulator_fingerprint_key values (?, ?)",
                    A_RUN,
                    A_MINTED_KEY))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into " + SCHEMA + ".simulator_fingerprint_key values (?, ?)",
                    A_RUN_WITH_NO_CLOCK,
                    "not-a-minted-key"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void threeExchanges(String table, String account, String body) {
    for (int place = 0; place < 3; place++) {
      jdbc.update(
          "insert into "
              + SCHEMA
              + "."
              + table
              + " (test_run_id, "
              + account
              + ", key_name, key_value, seq, operation, fingerprint, verdict, http_status,"
              + " content_type, body, provider_object_id, state_changed, at) values (?,"
              + " 'merchant-1', 'a-key', ?, 0, 'op', ?, 'FIRST_REQUEST', 200, 'application/json',"
              + " ?, null, false, "
              + AT
              + ")",
          A_RUN,
          "v-" + place,
          fingerprintAt(place),
          body);
    }
  }

  private static String fingerprintAt(int place) {
    return place == 2 ? ANOTHER_UNKEYED_DIGEST : AN_UNKEYED_DIGEST;
  }

  private static List<String> everyExchangeTable() {
    return List.of(
        "grow_keyed_exchange",
        "azericard_keyed_exchange",
        "autra_keyed_exchange",
        "worldline_keyed_exchange",
        "tranzila_keyed_exchange",
        "stripe_idempotency_exchange");
  }

  private static List<String> everyTableThatKeepsAFingerprint() {
    return List.of(
        "grow_keyed_exchange",
        "azericard_keyed_exchange",
        "autra_keyed_exchange",
        "worldline_keyed_exchange",
        "tranzila_keyed_exchange",
        "stripe_idempotency_exchange",
        "stripe_idempotency_key",
        "worldline_idempotence_key");
  }

  private List<String> fingerprintsIn(String table) {
    String ordering =
        table.endsWith("_key")
            ? ""
            : table.startsWith("stripe") ? " order by idempotency_key" : " order by key_value";
    return jdbc.queryForList(
        "select fingerprint from " + SCHEMA + "." + table + ordering, String.class);
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
