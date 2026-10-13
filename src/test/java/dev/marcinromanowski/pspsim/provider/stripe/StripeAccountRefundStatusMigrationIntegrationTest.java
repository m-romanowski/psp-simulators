package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class StripeAccountRefundStatusMigrationIntegrationTest extends PostgresBackedTest {

  private static final String SCHEMA = "before_a_stripe_account_refunded_anything";

  private static final String RUN = "before-a-stripe-account-refunded";

  private static final String OLDER_THAN_REFUNDS = "merchant-older-than-refunds";

  private static final MigrationVersion BEFORE_IT_REFUNDED = MigrationVersion.fromVersion("31");

  private static final MigrationVersion WHEN_IT_REFUNDED = MigrationVersion.fromVersion("32");

  @Autowired private DataSource dataSource;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void migrateOverADatabaseWhoseAccountsCouldNotRefund() {
    jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
    flyway(BEFORE_IT_REFUNDED).migrate();
    accountOfTheOldShape(OLDER_THAN_REFUNDS);
    flyway(WHEN_IT_REFUNDED).migrate();
  }

  @Test
  void an_account_configured_before_refunds_existed_settles_them_the_way_a_card_refund_settles() {
    Map<String, Object> kept = theAccount(OLDER_THAN_REFUNDS);

    assertThat(kept.get("refund_status"))
        .isEqualTo(StripeRefundOutcome.THE_ONE_A_CARD_REFUND_REACHES.name());
    assertThat(kept.get("secret_key")).isEqualTo("sk_test_51Simulator");
    assertThat(kept.get("capture_window")).isEqualTo("PT72H");
  }

  @Test
  void the_store_keeps_every_refund_status_the_vendor_publishes_and_refuses_the_rest() {
    assertThatCode(() -> anAccountSettling("merchant-pending", "PENDING"))
        .doesNotThrowAnyException();
    assertThatCode(() -> anAccountSettling("merchant-awaiting", "REQUIRES_ACTION"))
        .doesNotThrowAnyException();
    assertThatCode(() -> anAccountSettling("merchant-failed", "FAILED")).doesNotThrowAnyException();
    assertThatCode(() -> anAccountSettling("merchant-cancelled", "CANCELED"))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> anAccountSettling("merchant-invented", "DISPUTED"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void anAccountSettling(String accountId, String refundStatus) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
            + " webhook_secret, webhook_signature, webhook_timestamp_age, refund_status)"
            + " values (?, ?, ?, 'PT72H', null, 'VALID', 'PT0S', ?)",
        RUN,
        accountId,
        "sk_test_" + accountId,
        refundStatus);
  }

  private void accountOfTheOldShape(String accountId) {
    jdbc.update(
        "insert into "
            + SCHEMA
            + ".stripe_account (test_run_id, account_id, secret_key, capture_window,"
            + " webhook_secret, webhook_signature, webhook_timestamp_age)"
            + " values (?, ?, 'sk_test_51Simulator', 'PT72H', null, 'VALID', 'PT0S')",
        RUN,
        accountId);
  }

  private Map<String, Object> theAccount(String accountId) {
    return jdbc.queryForMap(
        "select * from " + SCHEMA + ".stripe_account where test_run_id = ? and account_id = ?",
        RUN,
        accountId);
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
