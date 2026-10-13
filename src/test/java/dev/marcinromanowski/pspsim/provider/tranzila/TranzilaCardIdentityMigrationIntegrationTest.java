package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class TranzilaCardIdentityMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_tranzila_knew_a_card";

  private static final String RUN = "before-card-identity";

  private static final String MASK = "458021xxxxxx4245";

  private static final MigrationVersion BEFORE = MigrationVersion.fromVersion("85");

  private static final MigrationVersion AFTER = MigrationVersion.fromVersion("86");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverTransactionsOfTheOldShape() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE).migrate();
    jdbc.update(row(1, 1, "debit", null, "000", "1000001", null));
    jdbc.update(row(2, 1, "credit", 1L, "000", "1000002", null));
    jdbc.update(row(3, 1, "cancel", 1L, "000", "1000003", null));
    flyway(AFTER).migrate();
  }

  @Test
  void every_transaction_of_the_old_shape_survives_with_the_fingerprint_of_its_mask() {
    assertThat(
            jdbc.queryForList(
                "select card_fingerprint from "
                    + SCHEMA
                    + ".tranzila_transaction order by transaction_id",
                String.class))
        .containsExactly(
            sha256("mask:" + RUN + ":" + MASK),
            sha256("mask:" + RUN + ":" + MASK),
            sha256("mask:" + RUN + ":" + MASK));
  }

  @Test
  void a_credit_and_a_cancel_of_one_original_made_before_the_migration_both_stay() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from "
                    + SCHEMA
                    + ".tranzila_transaction where reference_txn_id = 1",
                Integer.class))
        .isEqualTo(2);
  }

  @Test
  void a_new_original_is_settled_once_by_a_credit_or_a_cancel_and_never_by_both() {
    jdbc.update(row(10, 10, "debit", null, "000", "1000010", fingerprint()));
    jdbc.update(row(11, 10, "credit", 10L, "000", "1000011", fingerprint()));
    String aCancelOfTheCreditedDebit = row(12, 10, "cancel", 10L, "000", "1000012", fingerprint());
    String aSecondCredit = row(13, 10, "credit", 10L, "000", "1000013", fingerprint());

    assertThatThrownBy(() -> jdbc.update(aCancelOfTheCreditedDebit))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("tranzila_transaction_settles_an_original_once");
    assertThatThrownBy(() -> jdbc.update(aSecondCredit)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void a_declined_credit_settles_nothing() {
    jdbc.update(row(20, 20, "debit", null, "000", "1000020", fingerprint()));
    jdbc.update(row(21, 20, "credit", 20L, "004", null, fingerprint()));

    assertThatCode(() -> jdbc.update(row(22, 20, "cancel", 20L, "000", "1000022", fingerprint())))
        .doesNotThrowAnyException();
  }

  @Test
  void a_j5_is_closed_once_whichever_approval_code_closed_it() {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".tranzila_transaction (test_run_id, transaction_id, root_txn_id, account_id,"
            + " terminal_name, txn_type, verify_mode, processor_response_code, amount,"
            + " currency_code, auth_number, card_mask, card_type, expiry_month, expiry_year,"
            + " response_cvv, response_id, hold_expires_at, origin, created_at, card_fingerprint)"
            + " values ('"
            + RUN
            + "', 30, 30, 'a-1', 'myterminal', 'verify', 5, '777', '100', 'ILS', '1000030', '"
            + MASK
            + "', 2, 7, 30, '0', '0', timestamptz '2026-10-20T12:00:00Z', 'PROVIDER_API',"
            + " timestamptz '2026-10-13T12:00:00Z', '"
            + fingerprint()
            + "')");
    jdbc.update(row(31, 30, "force", 30L, "777", "1000031", fingerprint()));
    String aReversalAfterIt = row(32, 30, "reversal", 30L, "000", "1000032", fingerprint());

    assertThatThrownBy(() -> jdbc.update(aReversalAfterIt))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("tranzila_transaction_closes_a_hold_once");
  }

  @Test
  void a_fingerprint_is_sixty_four_lowercase_hex_characters() {
    String notADigest = row(40, 40, "debit", null, "000", "1000040", "4580210000004245");

    assertThatThrownBy(() -> jdbc.update(notADigest))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("tranzila_transaction_card_fingerprint_is_sha256_hex");
  }

  private String row(
      long id,
      long root,
      String type,
      @Nullable Long reference,
      String code,
      @Nullable String auth,
      @Nullable String fingerprint) {
    return "insert into "
        + SCHEMA
        + ".tranzila_transaction (test_run_id, transaction_id, root_txn_id, account_id,"
        + " terminal_name, txn_type, reference_txn_id, processor_response_code, amount,"
        + " currency_code, auth_number, card_mask, card_type, expiry_month, expiry_year,"
        + " response_cvv, response_id, origin, created_at"
        + theFingerprintColumn(fingerprint)
        + ") values ('"
        + RUN
        + "', "
        + id
        + ", "
        + root
        + ", 'a-1', 'myterminal', '"
        + type
        + "', "
        + reference
        + ", '"
        + code
        + "', '1', 'ILS', "
        + (auth == null ? "null" : "'" + auth + "'")
        + ", '"
        + MASK
        + "', 2, 7, 30, '0', '0', 'PROVIDER_API', timestamptz '2026-10-13T12:00:00Z'"
        + theFingerprintValue(fingerprint)
        + ")";
  }

  private static String theFingerprintColumn(@Nullable String fingerprint) {
    return fingerprint == null ? " " : ", card_fingerprint";
  }

  private static String theFingerprintValue(@Nullable String fingerprint) {
    return fingerprint == null ? " " : ", '" + fingerprint + "'";
  }

  private static String fingerprint() {
    return sha256("tranzila-unit:4580210000004245");
  }

  private static String sha256(String material) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(material.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new AssertionError(unavailable);
    }
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
