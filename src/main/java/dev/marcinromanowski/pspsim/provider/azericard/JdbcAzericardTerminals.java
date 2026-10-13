package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcAzericardTerminals implements AzericardTerminals {

  private static final String COLUMNS =
      """
      select test_run_id, account_id, terminal_id, merchant_key, gateway_key,
             duplicate_response_code, partial_completion, callback_signature,
             settlement_mac, stored_card_source_terminal_ids
      from azericard_terminal
      """;

  static final ClaimConstraints CLAIMS =
      new ClaimConstraints(Set.of("azericard_terminal_by_terminal_id"));

  private final JdbcClient jdbc;

  JdbcAzericardTerminals(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void merge(TestRunId testRunId, List<AzericardTerminal> terminals) {
    jdbc.sql("delete from azericard_terminal where test_run_id = ? and account_id = any(?)")
        .params(
            testRunId.value(),
            terminals.stream().map(AzericardTerminal::accountId).toArray(String[]::new))
        .update();
    for (AzericardTerminal terminal : terminals) {
      jdbc.sql(
              """
              insert into azericard_terminal
                  (test_run_id, account_id, terminal_id, merchant_key, gateway_key,
                   duplicate_response_code, partial_completion, callback_signature,
                   settlement_mac, stored_card_source_terminal_ids)
              values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              on conflict (test_run_id, account_id) do update set
                  terminal_id = excluded.terminal_id,
                  merchant_key = excluded.merchant_key,
                  gateway_key = excluded.gateway_key,
                  duplicate_response_code = excluded.duplicate_response_code,
                  partial_completion = excluded.partial_completion,
                  callback_signature = excluded.callback_signature,
                  settlement_mac = excluded.settlement_mac,
                  stored_card_source_terminal_ids = excluded.stored_card_source_terminal_ids
              """)
          .param(testRunId.value())
          .param(terminal.accountId())
          .param(terminal.terminalId())
          .param(terminal.merchantKeyPem())
          .param(terminal.gatewayKeyPem())
          .param(terminal.duplicateResponseCode())
          .param(terminal.partialCompletion().name())
          .param(terminal.callbackSignature().name())
          .param(terminal.settlementMac().name())
          .param(terminal.storedCardSourceTerminalIds().toArray(String[]::new))
          .update();
    }
  }

  @Override
  public List<AzericardTerminal> byTerminalId(String terminalId) {
    return jdbc.sql(COLUMNS + " where terminal_id = ? order by test_run_id, account_id")
        .param(terminalId)
        .query(JdbcAzericardTerminals::terminalOf)
        .list();
  }

  @Override
  public void remove(TestRunId testRunId, String accountId) {
    jdbc.sql("delete from azericard_terminal where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .update();
  }

  @Override
  public Optional<AzericardTerminal> ofTheRunHolding(TestRunId testRunId, String terminalId) {
    return jdbc.sql(COLUMNS + " where test_run_id = ? and terminal_id = ?")
        .params(testRunId.value(), terminalId)
        .query(JdbcAzericardTerminals::terminalOf)
        .optional();
  }

  @Override
  public Optional<AzericardTerminal> find(TestRunId testRunId, String accountId) {
    return jdbc.sql(COLUMNS + " where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .query(JdbcAzericardTerminals::terminalOf)
        .optional();
  }

  private static AzericardTerminal terminalOf(ResultSet rs, int row) throws SQLException {
    return new AzericardTerminal(
        TestRunId.of(rs.getString("test_run_id")),
        rs.getString("account_id"),
        rs.getString("terminal_id"),
        rs.getString("merchant_key"),
        rs.getString("gateway_key"),
        rs.getString("duplicate_response_code"),
        AzericardPartialCompletion.of(
            AzericardPartialCompletion.SETTING, rs.getString("partial_completion")),
        AzericardCallbackSignature.of(
            AzericardCallbackSignature.SETTING, rs.getString("callback_signature")),
        AzericardSettlementMac.of(AzericardSettlementMac.SETTING, rs.getString("settlement_mac")),
        List.of((String[]) rs.getArray("stored_card_source_terminal_ids").getArray()));
  }
}
