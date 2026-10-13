package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcAutraSessions implements AutraSessions {

  private final JdbcClient jdbc;

  JdbcAutraSessions(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean mint(AutraAccessToken token) {
    return jdbc.sql(
                """
                insert into autra_access_token
                    (token, test_run_id, account_id, issued_at, expires_at)
                values (?, ?, ?, ?, ?)
                on conflict (token) do nothing
                """)
            .param(token.value())
            .param(token.testRunId().value())
            .param(token.accountId())
            .param(token.issuedAt().atOffset(ZoneOffset.UTC))
            .param(token.expiresAt().atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<AutraAccessToken> accessToken(String value) {
    return jdbc.sql("select * from autra_access_token where token = ?")
        .param(value)
        .query(JdbcAutraSessions::toAccessToken)
        .optional();
  }

  @Override
  public boolean open(AutraSession session) {
    return jdbc.sql(
                """
                insert into autra_acquiring_session
                    (test_run_id, transactional_token, account_id, document_id, created_at)
                values (?, ?, ?, ?, ?)
                on conflict (test_run_id, transactional_token) do nothing
                """)
            .param(session.testRunId().value())
            .param(session.transactionalToken())
            .param(session.accountId())
            .param(session.documentId())
            .param(session.createdAt().atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<AutraSession> session(TestRunId testRunId, String transactionalToken) {
    return jdbc.sql(
            """
            select * from autra_acquiring_session
            where test_run_id = ? and transactional_token = ?
            """)
        .params(testRunId.value(), transactionalToken)
        .query(JdbcAutraSessions::toSession)
        .optional();
  }

  @Override
  public int clear(TestRunId testRunId) {
    jdbc.sql("delete from autra_access_token where test_run_id = ?")
        .param(testRunId.value())
        .update();
    return jdbc.sql("delete from autra_acquiring_session where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from autra_access_token").update();
    return jdbc.sql("delete from autra_acquiring_session").update();
  }

  private static AutraAccessToken toAccessToken(ResultSet rs, int row) throws SQLException {
    return new AutraAccessToken(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("token"),
        rs.getString("account_id"),
        rs.getObject("issued_at", OffsetDateTime.class).toInstant(),
        rs.getObject("expires_at", OffsetDateTime.class).toInstant());
  }

  private static AutraSession toSession(ResultSet rs, int row) throws SQLException {
    return new AutraSession(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("transactional_token"),
        rs.getString("account_id"),
        rs.getString("document_id"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }
}
