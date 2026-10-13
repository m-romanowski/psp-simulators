package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcStripeAccounts implements StripeAccounts {

  static final ClaimConstraints CLAIMS =
      new ClaimConstraints(Set.of("stripe_account_by_secret_key"));

  private final JdbcClient jdbc;

  JdbcStripeAccounts(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void merge(TestRunId testRunId, List<StripeAccount> accounts) {
    jdbc.sql("delete from stripe_account where test_run_id = ? and account_id = any(?)")
        .params(
            testRunId.value(),
            accounts.stream().map(StripeAccount::accountId).toArray(String[]::new))
        .update();
    for (StripeAccount account : accounts) {
      jdbc.sql(
              """
              insert into stripe_account
                  (test_run_id, account_id, secret_key, capture_window, webhook_secret,
                   webhook_signature, webhook_timestamp_age, refund_status)
              values (?, ?, ?, ?, ?, ?, ?, ?)
              on conflict (test_run_id, account_id) do update set
                  secret_key = excluded.secret_key,
                  capture_window = excluded.capture_window,
                  webhook_secret = excluded.webhook_secret,
                  webhook_signature = excluded.webhook_signature,
                  webhook_timestamp_age = excluded.webhook_timestamp_age,
                  refund_status = excluded.refund_status
              """)
          .param(testRunId.value())
          .param(account.accountId())
          .param(account.secretKey())
          .param(account.captureWindow().storedForm())
          .param(account.webhookSigning().secret())
          .param(account.webhookSigning().emission().name())
          .param(account.webhookSigning().storedAge())
          .param(account.refundOutcome().storedForm())
          .update();
    }
  }

  @Override
  public List<StripeAccount> bySecretKey(String secretKey) {
    return jdbc.sql(
            """
            select * from stripe_account
            where secret_key = ?
            order by test_run_id, account_id
            """)
        .param(secretKey)
        .query(JdbcStripeAccounts::accountOf)
        .list();
  }

  @Override
  public void remove(TestRunId testRunId, String accountId) {
    jdbc.sql("delete from stripe_account where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .update();
  }

  @Override
  public Optional<StripeAccount> ofTheRunHolding(TestRunId testRunId, String secretKey) {
    return jdbc.sql("select * from stripe_account where test_run_id = ? and secret_key = ?")
        .params(testRunId.value(), secretKey)
        .query(JdbcStripeAccounts::accountOf)
        .optional();
  }

  @Override
  public Optional<StripeAccount> find(TestRunId testRunId, String accountId) {
    return jdbc.sql("select * from stripe_account where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .query(JdbcStripeAccounts::accountOf)
        .optional();
  }

  private static StripeAccount accountOf(ResultSet rs, int row) throws SQLException {
    return new StripeAccount(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("account_id"),
        rs.getString("secret_key"),
        StripeCaptureWindow.stored(rs.getString("capture_window")),
        StripeWebhookSigning.stored(
            rs.getString("webhook_secret"),
            rs.getString("webhook_signature"),
            rs.getString("webhook_timestamp_age")),
        StripeRefundOutcome.stored(rs.getString("refund_status")));
  }
}
