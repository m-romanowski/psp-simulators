package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcWorldlineMerchants implements WorldlineMerchants {

  static final ClaimConstraints CLAIMS =
      new ClaimConstraints(
          Set.of("worldline_merchant_by_merchant_id", "worldline_merchant_by_api_key"));

  private final JdbcClient jdbc;

  JdbcWorldlineMerchants(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void merge(TestRunId testRunId, List<WorldlineMerchant> configured) {
    jdbc.sql("delete from worldline_merchant where test_run_id = ? and account_id = any(?)")
        .params(
            testRunId.value(),
            configured.stream().map(WorldlineMerchant::accountId).toArray(String[]::new))
        .update();
    for (WorldlineMerchant merchant : configured) {
      jdbc.sql(
              """
              insert into worldline_merchant
                  (test_run_id, account_id, merchant_id, api_key, api_secret,
                   signed_path_version, capture_landing, authentication)
              values (?, ?, ?, ?, ?, ?, ?, ?)
              on conflict (test_run_id, account_id) do update set
                  merchant_id = excluded.merchant_id,
                  api_key = excluded.api_key,
                  api_secret = excluded.api_secret,
                  signed_path_version = excluded.signed_path_version,
                  capture_landing = excluded.capture_landing,
                  authentication = excluded.authentication
              """)
          .param(testRunId.value())
          .param(merchant.accountId())
          .param(merchant.merchantId())
          .param(merchant.apiKey())
          .param(merchant.apiSecret())
          .param(merchant.signedPathVersion().name())
          .param(merchant.captureLanding().name())
          .param(merchant.authentication().name())
          .update();
    }
  }

  @Override
  public void remove(TestRunId testRunId, String accountId) {
    jdbc.sql("delete from worldline_merchant where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .update();
  }

  @Override
  public Optional<WorldlineMerchant> find(TestRunId testRunId, String accountId) {
    return jdbc.sql("select * from worldline_merchant where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .query(JdbcWorldlineMerchants::toMerchant)
        .optional();
  }

  @Override
  public Optional<WorldlineMerchant> ofTheRunHolding(TestRunId testRunId, String merchantId) {
    return jdbc.sql("select * from worldline_merchant where test_run_id = ? and merchant_id = ?")
        .params(testRunId.value(), merchantId)
        .query(JdbcWorldlineMerchants::toMerchant)
        .optional();
  }

  @Override
  public List<WorldlineMerchant> byApiKey(String apiKey) {
    return jdbc.sql("select * from worldline_merchant where api_key = ?")
        .param(apiKey)
        .query(JdbcWorldlineMerchants::toMerchant)
        .list();
  }

  private static WorldlineMerchant toMerchant(ResultSet rs, int row) throws SQLException {
    return new WorldlineMerchant(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("account_id"),
        rs.getString("merchant_id"),
        rs.getString("api_key"),
        rs.getString("api_secret"),
        WorldlineSignedPathVersion.valueOf(rs.getString("signed_path_version")),
        WorldlineCaptureLanding.valueOf(rs.getString("capture_landing")),
        WorldlineAuthentication.valueOf(rs.getString("authentication")));
  }
}
