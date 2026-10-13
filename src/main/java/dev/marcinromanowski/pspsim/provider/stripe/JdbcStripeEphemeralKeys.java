package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcStripeEphemeralKeys implements StripeEphemeralKeys {

  private final JdbcClient jdbc;

  JdbcStripeEphemeralKeys(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfAbsent(StripeEphemeralKey key) {
    try {
      return jdbc.sql(
                  """
                  insert into stripe_ephemeral_key
                      (test_run_id, ephemeral_key_id, account_id, customer_id, secret, created_at,
                       expires_at)
                  values (?, ?, ?, ?, ?, ?, ?)
                  on conflict (test_run_id, ephemeral_key_id) do nothing
                  """)
              .param(key.testRunId().value())
              .param(key.ephemeralKeyId())
              .param(key.accountId())
              .param(key.customerId())
              .param(key.secret())
              .param(key.createdAt().atOffset(ZoneOffset.UTC))
              .param(key.expiresAt().atOffset(ZoneOffset.UTC))
              .update()
          == 1;
    } catch (DataIntegrityViolationException secretAlreadyMinted) {
      return false;
    }
  }

  @Override
  public List<StripeEphemeralKey> bySecret(String secret) {
    return jdbc.sql("select * from stripe_ephemeral_key where secret = ?")
        .param(secret)
        .query(JdbcStripeEphemeralKeys::toEphemeralKey)
        .list();
  }

  @Override
  public void clear(TestRunId testRunId) {
    jdbc.sql("delete from stripe_ephemeral_key where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public void clearAll() {
    jdbc.sql("delete from stripe_ephemeral_key").update();
  }

  private static StripeEphemeralKey toEphemeralKey(ResultSet rs, int row) throws SQLException {
    return new StripeEphemeralKey(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("ephemeral_key_id"),
        rs.getString("account_id"),
        rs.getString("customer_id"),
        rs.getString("secret"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("expires_at", OffsetDateTime.class).toInstant());
  }
}
