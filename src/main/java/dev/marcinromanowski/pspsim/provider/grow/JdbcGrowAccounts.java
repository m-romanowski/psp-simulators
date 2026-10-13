package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcGrowAccounts implements GrowAccounts {

  private static final String COLUMNS =
      """
      select test_run_id, account_id, user_id, page_code, callback_acknowledgement,
             settlement_ceiling, refund_allowance, refund_excess_rejection, merchant_balance,
             process_lookup_rejection, wire_form, capture_window, automatic_void_after
      from grow_account
      """;

  static final ClaimConstraints CLAIMS =
      new ClaimConstraints(Set.of("grow_account_by_user_id", "grow_account_by_page_code"));

  private final JdbcClient jdbc;

  JdbcGrowAccounts(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void merge(TestRunId testRunId, List<GrowAccount> accounts) {
    jdbc.sql("delete from grow_account where test_run_id = ? and account_id = any(?)")
        .params(
            testRunId.value(), accounts.stream().map(GrowAccount::accountId).toArray(String[]::new))
        .update();
    for (GrowAccount account : accounts) {
      jdbc.sql(
              """
              insert into grow_account
                  (test_run_id, account_id, user_id, page_code, callback_acknowledgement,
                   settlement_ceiling, refund_allowance, refund_excess_rejection,
                   merchant_balance, process_lookup_rejection, wire_form, capture_window,
                   automatic_void_after)
              values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              on conflict (test_run_id, account_id) do update set
                  user_id = excluded.user_id,
                  page_code = excluded.page_code,
                  callback_acknowledgement = excluded.callback_acknowledgement,
                  settlement_ceiling = excluded.settlement_ceiling,
                  refund_allowance = excluded.refund_allowance,
                  refund_excess_rejection = excluded.refund_excess_rejection,
                  merchant_balance = excluded.merchant_balance,
                  process_lookup_rejection = excluded.process_lookup_rejection,
                  wire_form = excluded.wire_form,
                  capture_window = excluded.capture_window,
                  automatic_void_after = excluded.automatic_void_after
              """)
          .param(testRunId.value())
          .param(account.accountId())
          .param(account.userId())
          .param(account.pageCode())
          .param(account.acknowledgement().name())
          .param(account.settlementCeiling().name())
          .param(account.refundAllowance().name())
          .param(account.refundExcessRejection().name())
          .param(account.merchantBalance().name())
          .param(account.processLookupRejection().name())
          .param(account.wireForm().name())
          .param(account.holdWindow().captureWindow().toString())
          .param(account.holdWindow().automaticVoidAfter().toString())
          .update();
    }
  }

  @Override
  public List<GrowAccount> byUserIdAndPageCode(String userId, String pageCode) {
    return jdbc.sql(
            COLUMNS + " where user_id = ? and page_code = ? order by test_run_id," + " account_id")
        .params(userId, pageCode)
        .query(JdbcGrowAccounts::accountOf)
        .list();
  }

  @Override
  public List<GrowAccount> byUserId(String userId) {
    return jdbc.sql(COLUMNS + " where user_id = ? order by test_run_id, account_id")
        .param(userId)
        .query(JdbcGrowAccounts::accountOf)
        .list();
  }

  @Override
  public List<GrowAccount> byPageCode(String pageCode) {
    return jdbc.sql(COLUMNS + " where page_code = ? order by test_run_id, account_id")
        .param(pageCode)
        .query(JdbcGrowAccounts::accountOf)
        .list();
  }

  @Override
  public List<GrowAccount> byEitherCredential(@Nullable String userId, @Nullable String pageCode) {
    if (userId == null && pageCode == null) {
      return List.of();
    }
    if (userId == null) {
      return byPageCode(String.valueOf(pageCode));
    }
    if (pageCode == null) {
      return byUserId(userId);
    }
    return byUserIdAndPageCode(userId, pageCode);
  }

  @Override
  public void remove(TestRunId testRunId, String accountId) {
    jdbc.sql("delete from grow_account where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .update();
  }

  @Override
  public List<GrowAccount> ofTheRunHoldingEither(
      TestRunId testRunId, String userId, String pageCode) {
    return jdbc.sql(
            COLUMNS
                + " where test_run_id = ? and (user_id = ? or page_code = ?) order by"
                + " account_id")
        .params(testRunId.value(), userId, pageCode)
        .query(JdbcGrowAccounts::accountOf)
        .list();
  }

  @Override
  public List<GrowAccount> allById(TestRunId testRunId, List<String> accountIds) {
    return jdbc.sql(COLUMNS + " where test_run_id = ? and account_id = any(?) order by account_id")
        .params(testRunId.value(), accountIds.toArray(String[]::new))
        .query(JdbcGrowAccounts::accountOf)
        .list();
  }

  @Override
  public Optional<GrowAccount> find(TestRunId testRunId, String accountId) {
    return jdbc.sql(COLUMNS + " where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .query(JdbcGrowAccounts::accountOf)
        .optional();
  }

  private static GrowAccount accountOf(ResultSet rs, int row) throws SQLException {
    return new GrowAccount(
        TestRunId.of(rs.getString("test_run_id")),
        rs.getString("account_id"),
        rs.getString("user_id"),
        rs.getString("page_code"),
        GrowAcknowledgementMode.of(rs.getString("callback_acknowledgement")),
        GrowSettlementCeiling.of(rs.getString("settlement_ceiling")),
        GrowRefundAllowance.of(rs.getString("refund_allowance")),
        GrowRefundExcessRejection.of(rs.getString("refund_excess_rejection")),
        GrowMerchantBalance.of(rs.getString("merchant_balance")),
        GrowProcessLookupRejection.of(rs.getString("process_lookup_rejection")),
        GrowWireForm.of(rs.getString("wire_form")),
        new GrowHoldWindow(
            Duration.parse(rs.getString("capture_window")),
            Duration.parse(rs.getString("automatic_void_after"))));
  }
}
