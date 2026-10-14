package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcTranzilaHandshakes implements TranzilaHandshakes {

  private final JdbcClient jdbc;

  JdbcTranzilaHandshakes(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public int insert(TranzilaHandshake handshake) {
    return jdbc.sql(
            """
            insert into tranzila_handshake
                (thtk, test_run_id, account_id, terminal_name, sum, request_params,
                 created_at, expires_at)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """)
        .param(handshake.thtk())
        .param(handshake.testRunId().value())
        .param(handshake.accountId())
        .param(handshake.terminalName())
        .param(handshake.sum())
        .param(handshake.requestParams())
        .param(handshake.createdAt().atOffset(ZoneOffset.UTC))
        .param(handshake.expiresAt().atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public Optional<TranzilaHandshake> find(TestRunId testRunId, String thtk) {
    return jdbc.sql("select * from tranzila_handshake where test_run_id = ? and thtk = ?")
        .params(testRunId.value(), thtk)
        .query(JdbcTranzilaHandshakes::toHandshake)
        .optional();
  }

  @Override
  public void retainUntil(TestRunId testRunId, String thtk, Instant expiresAt) {
    jdbc.sql("update tranzila_handshake set expires_at = ? where test_run_id = ? and thtk = ?")
        .params(expiresAt.atOffset(ZoneOffset.UTC), testRunId.value(), thtk)
        .update();
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from tranzila_handshake where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from tranzila_handshake").update();
  }

  private static TranzilaHandshake toHandshake(ResultSet rs, int row) throws SQLException {
    return new TranzilaHandshake(
        rs.getString("thtk"),
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("account_id"),
        rs.getString("terminal_name"),
        rs.getString("sum"),
        rs.getString("request_params"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("expires_at", OffsetDateTime.class).toInstant());
  }
}
