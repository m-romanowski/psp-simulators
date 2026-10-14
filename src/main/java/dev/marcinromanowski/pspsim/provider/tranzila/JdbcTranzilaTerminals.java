package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcTranzilaTerminals implements TranzilaTerminals {

  static final ClaimConstraints CLAIMS =
      new ClaimConstraints(
          Set.of("tranzila_terminal_by_terminal_name", "tranzila_terminal_by_app_key"));

  private final JdbcClient jdbc;

  JdbcTranzilaTerminals(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void merge(TestRunId testRunId, List<TranzilaTerminal> configured) {
    jdbc.sql("delete from tranzila_terminal where test_run_id = ? and account_id = any(?)")
        .params(
            testRunId.value(),
            configured.stream().map(TranzilaTerminal::accountId).toArray(String[]::new))
        .update();
    configured.forEach(this::insert);
  }

  private void insert(TranzilaTerminal terminal) {
    jdbc.sql(
            """
            insert into tranzila_terminal
                (test_run_id, account_id, terminal_name, app_key, app_secret, access_token,
                 authentication, verification_approval_code, j5_validity)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(terminal.testRunId().value())
        .param(terminal.accountId())
        .param(terminal.terminalName())
        .param(terminal.appKey())
        .param(terminal.appSecret())
        .param(terminal.accessToken().name())
        .param(terminal.authentication().name())
        .param(terminal.verificationApprovalCode().code())
        .param(terminal.j5Validity().toString())
        .update();
  }

  @Override
  public void remove(TestRunId testRunId, String accountId) {
    jdbc.sql("delete from tranzila_terminal where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .update();
  }

  @Override
  public Optional<TranzilaTerminal> find(TestRunId testRunId, String accountId) {
    return jdbc.sql("select * from tranzila_terminal where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .query(JdbcTranzilaTerminals::toTerminal)
        .optional();
  }

  @Override
  public List<TranzilaTerminal> ofTheRun(TestRunId testRunId) {
    return jdbc.sql("select * from tranzila_terminal where test_run_id = ? order by account_id")
        .param(testRunId.value())
        .query(JdbcTranzilaTerminals::toTerminal)
        .list();
  }

  @Override
  public List<TranzilaTerminal> byAppKey(String appKey) {
    return jdbc.sql("select * from tranzila_terminal where app_key = ?")
        .param(appKey)
        .query(JdbcTranzilaTerminals::toTerminal)
        .list();
  }

  @Override
  public List<TranzilaTerminal> named(String terminalName) {
    return jdbc.sql("select * from tranzila_terminal where terminal_name = ? order by test_run_id")
        .param(terminalName)
        .query(JdbcTranzilaTerminals::toTerminal)
        .list();
  }

  private static TranzilaTerminal toTerminal(ResultSet rs, int row) throws SQLException {
    return new TranzilaTerminal(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("account_id"),
        rs.getString("terminal_name"),
        rs.getString("app_key"),
        rs.getString("app_secret"),
        TranzilaAccessToken.valueOf(rs.getString("access_token")),
        TranzilaAuthentication.valueOf(rs.getString("authentication")),
        TranzilaVerificationApprovalCode.stored(rs.getString("verification_approval_code")),
        Duration.parse(rs.getString("j5_validity")));
  }
}
