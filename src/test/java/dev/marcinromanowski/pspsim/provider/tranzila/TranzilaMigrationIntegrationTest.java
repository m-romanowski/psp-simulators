package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
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

class TranzilaMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_tranzila_served_a_call";

  private static final String RUN = "before-tranzila";

  private static final String ANOTHER_PROVIDERS_ACCOUNT = "merchant-already-here";

  private static final MigrationVersion BEFORE_TRANZILA = MigrationVersion.fromVersion("84");

  private static final MigrationVersion WHEN_TRANZILA_LANDED = MigrationVersion.fromVersion("85");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseThatAlreadyHeldARunAndAnAccountOfAnotherProvider() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_TRANZILA).migrate();
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".simulator_test_run (test_run_id, fixed_now)"
            + " values (?, timestamptz '2026-10-13T12:00:00Z')",
        RUN);
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".worldline_merchant (test_run_id, account_id, merchant_id, api_key, api_secret,"
            + " signed_path_version, capture_landing, authentication)"
            + " values (?, ?, 'pspid-1', 'key-1', 'a-secret',"
            + " 'V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT', 'CAPTURED_9',"
            + " 'THE_SIGNATURE_IS_VERIFIED')",
        RUN,
        ANOTHER_PROVIDERS_ACCOUNT);
    flyway(WHEN_TRANZILA_LANDED).migrate();
  }

  @Test
  void a_run_and_an_account_of_another_provider_survive_and_no_terminal_is_invented() {
    assertThat(count("simulator_test_run where test_run_id = '" + RUN + "'")).isEqualTo(1);
    assertThat(count("worldline_merchant where account_id = '" + ANOTHER_PROVIDERS_ACCOUNT + "'"))
        .isEqualTo(1);
    assertThat(count("tranzila_terminal")).isZero();
    assertThat(count("tranzila_transaction")).isZero();
  }

  @Test
  void the_terminal_store_takes_a_published_terminal_name_and_refuses_one_of_another_shape() {
    assertThatCode(() -> aTerminal("a-1", "myterminal", "k-1", "777", "PT168H"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> aTerminal("a-2", "My-Terminal", "k-2", "777", "PT168H"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_terminal_store_takes_the_two_approval_codes_and_a_span_a_hold_can_outlive_only() {
    assertThatThrownBy(() -> aTerminal("a-3", "terminalthree", "k-3", "001", "PT168H"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aTerminal("a-4", "terminalfour", "k-4", "000", "PT0S"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aTerminal("a-5", "terminalfive", "k-5", "000", "P366D"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> aTerminal("a-6", "terminalsix", "k-6", "000", "seven days"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatCode(() -> aTerminal("a-7", "terminalseven", "k-7", "000", "PT8760H"))
        .doesNotThrowAnyException();
  }

  @Test
  void one_terminal_name_and_one_app_key_name_one_account_of_a_run() {
    aTerminal("a-1", "myterminal", "k-1", "777", "PT168H");

    assertThatThrownBy(() -> aTerminal("a-2", "myterminal", "k-2", "777", "PT168H"))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("tranzila_terminal_by_terminal_name");
    assertThatThrownBy(() -> aTerminal("a-3", "otherterminal", "k-1", "777", "PT168H"))
        .isInstanceOf(DuplicateKeyException.class)
        .hasMessageContaining("tranzila_terminal_by_app_key");
  }

  @Test
  void the_transaction_store_takes_the_six_served_types_and_no_standing_order() {
    assertThatCode(() -> anOriginal(1, "debit", null, "000", null)).doesNotThrowAnyException();
    assertThatThrownBy(() -> anOriginal(2, "sto", null, "000", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_verify_mode_belongs_to_a_verify_and_is_2_or_5() {
    assertThatCode(() -> anOriginal(1, "verify", 2, "777", null)).doesNotThrowAnyException();
    assertThatThrownBy(() -> anOriginal(2, "verify", 6, "777", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> anOriginal(3, "verify", null, "777", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> anOriginal(4, "debit", 5, "000", null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void an_approved_j5_carries_its_expiry_and_nothing_else_does() {
    assertThatCode(() -> anOriginal(1, "verify", 5, "777", "2026-10-20T12:00:00Z"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> anOriginal(2, "verify", 5, "777", null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> anOriginal(3, "debit", null, "000", "2026-10-20T12:00:00Z"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> anOriginal(4, "verify", 5, "777", "2026-10-13T12:00:00Z"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void an_approval_and_only_an_approval_carries_an_authorization_number() {
    String statement1 = insert(6, 6, "debit", null, null, "000", null, null, "100", "ILS");
    String statement0 = insert(5, 5, "debit", null, null, "004", "1000001", null, "100", "ILS");
    assertThatThrownBy(() -> jdbc.update(statement0))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(statement1))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void an_original_references_nothing_and_a_follow_on_references_its_original() {
    String statement2 = insert(2, 1, "credit", null, 1L, "000", "1000002", null, "1", "ILS");
    String statement3 = insert(3, 1, "credit", null, null, "000", "1000003", null, "1", "ILS");
    String statement4 = insert(4, 9, "debit", null, 1L, "000", "1000004", null, "1", "ILS");
    anOriginal(1, "debit", null, "000", null);

    assertThatCode(() -> jdbc.update(statement2)).doesNotThrowAnyException();
    assertThatThrownBy(() -> jdbc.update(statement3))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(statement4))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_amount_is_a_decimal_and_the_currency_one_of_the_four_the_api_publishes() {
    String statement5 = insert(1, 1, "debit", null, null, "000", "1000001", null, "-1", "ILS");
    String statement6 = insert(2, 2, "debit", null, null, "000", "1000001", null, "1", "GPB");
    assertThatThrownBy(() -> jdbc.update(statement5))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update(statement6))
        .isInstanceOf(DataIntegrityViolationException.class);
    String statement14 = insert(3, 3, "debit", null, null, "000", "1000001", null, "10.53", "GBP");
    assertThatCode(() -> jdbc.update(statement14)).doesNotThrowAnyException();
  }

  @Test
  void a_j5_is_closed_by_one_approved_force_or_reversal_and_a_transaction_cancelled_once() {
    String statement7 = insert(4, 1, "reversal", null, 1L, "000", "1000004", null, "1", "ILS");
    String statement8 = insert(6, 1, "cancel", null, 2L, "000", "1000006", null, "1", "ILS");
    anOriginal(1, "verify", 5, "777", "2026-10-20T12:00:00Z");
    jdbc.update(insert(2, 1, "force", null, 1L, "000", "1000002", null, "1", "ILS"));
    jdbc.update(insert(3, 1, "force", null, 1L, "507", null, null, "999", "ILS"));

    assertThatThrownBy(() -> jdbc.update(statement7)).isInstanceOf(DuplicateKeyException.class);
    jdbc.update(insert(5, 1, "cancel", null, 2L, "000", "1000005", null, "1", "ILS"));
    assertThatThrownBy(() -> jdbc.update(statement8)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void an_original_is_credited_by_one_approved_credit() {
    String first = insert(2, 1, "credit", null, 1L, "000", "1000002", null, "1", "ILS");
    String declined = insert(3, 1, "credit", null, 1L, "004", null, null, "1", "ILS");
    String second = insert(4, 1, "credit", null, 1L, "000", "1000004", null, "1", "ILS");
    anOriginal(1, "debit", null, "000", null);
    jdbc.update(first);
    jdbc.update(declined);

    assertThatThrownBy(() -> jdbc.update(second)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void the_nonce_store_keys_one_nonce_per_account_of_a_run() {
    String statement9 = nonce("n-1");
    String statement10 = nonce("");
    jdbc.update(nonce("n-1"));

    assertThatThrownBy(() -> jdbc.update(statement9)).isInstanceOf(DuplicateKeyException.class);
    assertThatThrownBy(() -> jdbc.update(statement10))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_keyed_exchange_store_takes_the_four_verdicts_this_provider_records_and_no_other() {
    String statement11 = exchange("FIRST_REQUEST", 0);
    String statement12 = exchange("DUPLICATE_KEY_REFUSAL", 1);
    String statement13 = exchange("TRUE_IDEMPOTENT_REPLAY", 2);
    assertThatCode(() -> jdbc.update(statement11)).doesNotThrowAnyException();
    assertThatCode(() -> jdbc.update(statement12)).doesNotThrowAnyException();
    assertThatThrownBy(() -> jdbc.update(statement13))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void aTerminal(
      String accountId, String terminalName, String appKey, String approval, String validity) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".tranzila_terminal (test_run_id, account_id, terminal_name, app_key, app_secret,"
            + " access_token, authentication, verification_approval_code, j5_validity)"
            + " values (?, ?, ?, ?, 's', 'AS_THE_AUTHENTICATION_PAGES_PUBLISH_IT',"
            + " 'THE_ACCESS_TOKEN_IS_VERIFIED', ?, ?)",
        RUN,
        accountId,
        terminalName,
        appKey,
        approval,
        validity);
  }

  private void anOriginal(
      long id, String type, @Nullable Integer mode, String code, @Nullable String expires) {
    jdbc.update(
        insert(
            id,
            id,
            type,
            mode,
            null,
            code,
            code.equals("004") ? null : "1000001",
            expires,
            "100",
            "ILS"));
  }

  private String insert(
      long id,
      long root,
      String type,
      @Nullable Integer mode,
      @Nullable Long reference,
      String code,
      @Nullable String auth,
      @Nullable String expires,
      String amount,
      String currency) {
    return "insert into "
        + SCHEMA
        + ".tranzila_transaction (test_run_id, transaction_id, root_txn_id, account_id,"
        + " terminal_name, txn_type, verify_mode, reference_txn_id, processor_response_code,"
        + " amount, currency_code, auth_number, token, card_mask, card_type, expiry_month,"
        + " expiry_year, response_cvv, response_id, dc_disable, hold_expires_at, origin,"
        + " created_at) values ('"
        + RUN
        + "', "
        + id
        + ", "
        + root
        + ", 'a-1', 'myterminal', '"
        + type
        + "', "
        + mode
        + ", "
        + reference
        + ", '"
        + code
        + "', '"
        + amount
        + "', '"
        + currency
        + "', "
        + (auth == null ? "null" : "'" + auth + "'")
        + ", null, '458021xxxxxx4245', 2, 7, 30, '0', '0', null, "
        + (expires == null ? "null" : "timestamptz '" + expires + "'")
        + ", 'PROVIDER_API', timestamptz '2026-10-13T12:00:00Z')";
  }

  private String nonce(String value) {
    return "insert into "
        + SCHEMA
        + ".tranzila_nonce (test_run_id, account_id, nonce, seen_at) values ('"
        + RUN
        + "', 'a-1', '"
        + value
        + "', timestamptz '2026-10-13T12:00:00Z')";
  }

  private String exchange(String verdict, int seq) {
    return "insert into "
        + SCHEMA
        + ".tranzila_keyed_exchange (test_run_id, account_id, key_name, key_value, seq,"
        + " operation, fingerprint, verdict, state_changed, at) values ('"
        + RUN
        + "', 'a-1', 'DCdisable', 'v', "
        + seq
        + ", 'debit', 'fp', '"
        + verdict
        + "', true, timestamptz '2026-10-13T12:00:00Z')";
  }

  private int count(String from) {
    Integer counted =
        jdbc.queryForObject("select count(*) from " + SCHEMA + "." + from, Integer.class);
    return counted == null ? -1 : counted;
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
