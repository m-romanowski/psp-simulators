package dev.marcinromanowski.pspsim.provider.tranzila;

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

class TranzilaBrokerMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_tranzila_kept_a_broker";

  private static final String RUN = "before-wallets";

  private static final MigrationVersion BEFORE_THE_WALLETS = MigrationVersion.fromVersion("89");

  private static final MigrationVersion WHEN_THE_WALLETS_LANDED =
      MigrationVersion.fromVersion("90");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverTransactionsMadeBeforeAnyWalletPaid() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_THE_WALLETS).migrate();
    jdbc.update(anOldTransaction(10000001, "debit", "null", "'t000000000000014245'"));
    jdbc.update(anOldTransaction(10000002, "verify", "5", "null"));
    flyway(WHEN_THE_WALLETS_LANDED).migrate();
  }

  @Test
  void a_transaction_made_before_the_wallets_names_no_broker_and_reads_as_a_card_payment() {
    List<TranzilaTransaction> read =
        jdbc.query(
            "select * from " + SCHEMA + ".tranzila_transaction order by transaction_id",
            JdbcTranzilaTransactions::toTransaction);

    assertThat(read).hasSize(2).allSatisfy(made -> assertThat(made.broker()).isNull());
    assertThat(read.get(0).token()).isEqualTo("t000000000000014245");
    assertThat(read.get(1).isAJ5()).isTrue();
    assertThat(TranzilaLookupAnswers.theBrokerOf(read.get(0))).isEmpty();
  }

  @Test
  void a_broker_is_one_the_reports_publish_and_nothing_else() {
    assertThatCode(() -> jdbc.update(aBroker(10000001, "'apple_pay'"))).doesNotThrowAnyException();
    assertThatCode(() -> jdbc.update(aBroker(10000001, "'GOOGLE_PAY'"))).doesNotThrowAnyException();
    assertThatCode(() -> jdbc.update(aBroker(10000001, "null"))).doesNotThrowAnyException();
    for (String refused : List.of("'APPLE_PAY'", "'google_pay'", "'ENDEAVOUR'", "''")) {
      assertThatThrownBy(() -> jdbc.update(aBroker(10000001, refused)))
          .as(refused)
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  private String aBroker(long transactionId, String broker) {
    return "update "
        + SCHEMA
        + ".tranzila_transaction set broker = "
        + broker
        + " where transaction_id = "
        + transactionId;
  }

  private String anOldTransaction(
      long transactionId, String type, String verifyMode, String token) {
    boolean aJ5 = "5".equals(verifyMode);
    return "insert into "
        + SCHEMA
        + ".tranzila_transaction (test_run_id, transaction_id, root_txn_id, account_id,"
        + " terminal_name, txn_type, verify_mode, processor_response_code, amount, currency_code,"
        + " auth_number, token, card_mask, card_type, expiry_month, expiry_year, response_cvv,"
        + " response_id, hold_expires_at, origin, created_at) values ('"
        + RUN
        + "', "
        + transactionId
        + ", "
        + transactionId
        + ", 'a-1', 'myterminal', '"
        + type
        + "', "
        + verifyMode
        + ", '"
        + (aJ5 ? "777" : "000")
        + "', '10.53', 'ILS', '1000001', "
        + token
        + ", '458021xxxxxx4245', 2, 7, 30, '0', '0', "
        + (aJ5 ? "timestamptz '2026-10-20T12:00:00Z'" : "null")
        + ", 'PROVIDER_API', timestamptz '2026-10-13T12:00:00Z')";
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
