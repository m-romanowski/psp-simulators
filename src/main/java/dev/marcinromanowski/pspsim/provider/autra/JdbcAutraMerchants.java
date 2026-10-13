package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimConstraints;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcAutraMerchants implements AutraMerchants {

  static final ClaimConstraints CLAIMS =
      new ClaimConstraints(Set.of("autra_merchant_by_client_id", "autra_merchant_by_document_id"));

  private final JdbcClient jdbc;

  JdbcAutraMerchants(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void merge(TestRunId testRunId, List<AutraMerchant> configured) {
    jdbc.sql("delete from autra_merchant where test_run_id = ? and account_id = any(?)")
        .params(
            testRunId.value(),
            configured.stream().map(AutraMerchant::accountId).toArray(String[]::new))
        .update();
    for (AutraMerchant merchant : configured) {
      jdbc.sql(
              """
              insert into autra_merchant
                  (test_run_id, account_id, client_id, client_secret, document_id, oauth_failure,
                   oauth_field_casing, pre_authorization_shape, void_shape,
                   authorizer_rejected_message, merchant_not_found_status, sale_decline_status,
                   acquirer_transaction_identifier, webhook_secret)
              values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              on conflict (test_run_id, account_id) do update set
                  client_id = excluded.client_id,
                  client_secret = excluded.client_secret,
                  document_id = excluded.document_id,
                  oauth_failure = excluded.oauth_failure,
                  oauth_field_casing = excluded.oauth_field_casing,
                  pre_authorization_shape = excluded.pre_authorization_shape,
                  void_shape = excluded.void_shape,
                  authorizer_rejected_message = excluded.authorizer_rejected_message,
                  merchant_not_found_status = excluded.merchant_not_found_status,
                  sale_decline_status = excluded.sale_decline_status,
                  acquirer_transaction_identifier = excluded.acquirer_transaction_identifier,
                  webhook_secret = excluded.webhook_secret
              """)
          .param(testRunId.value())
          .param(merchant.accountId())
          .param(merchant.clientId())
          .param(merchant.clientSecret())
          .param(merchant.documentId())
          .param(merchant.oauthFailure().name())
          .param(merchant.oauthFieldCasing().name())
          .param(merchant.preAuthorizationShape().name())
          .param(merchant.voidShape().name())
          .param(merchant.authorizerRejectedMessage())
          .param(merchant.merchantNotFoundStatus().name())
          .param(merchant.saleDecline().name())
          .param(merchant.acquirerReport().name())
          .param(merchant.webhookSecret())
          .update();
    }
  }

  @Override
  public void remove(TestRunId testRunId, String accountId) {
    jdbc.sql("delete from autra_merchant where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .update();
  }

  @Override
  public Optional<AutraMerchant> find(TestRunId testRunId, String accountId) {
    return jdbc.sql("select * from autra_merchant where test_run_id = ? and account_id = ?")
        .params(testRunId.value(), accountId)
        .query(JdbcAutraMerchants::toMerchant)
        .optional();
  }

  @Override
  public Optional<AutraMerchant> ofTheRunHolding(TestRunId testRunId, String documentId) {
    return jdbc.sql("select * from autra_merchant where test_run_id = ? and document_id = ?")
        .params(testRunId.value(), documentId)
        .query(JdbcAutraMerchants::toMerchant)
        .optional();
  }

  @Override
  public List<AutraMerchant> byClientId(String clientId) {
    return jdbc.sql("select * from autra_merchant where client_id = ?")
        .param(clientId)
        .query(JdbcAutraMerchants::toMerchant)
        .list();
  }

  private static AutraMerchant toMerchant(ResultSet rs, int row) throws SQLException {
    return new AutraMerchant(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("account_id"),
        rs.getString("client_id"),
        rs.getString("client_secret"),
        rs.getString("document_id"),
        AutraOauthFailure.valueOf(rs.getString("oauth_failure")),
        AutraOauthFieldCasing.valueOf(rs.getString("oauth_field_casing")),
        AutraRequestShape.valueOf(rs.getString("pre_authorization_shape")),
        AutraRequestShape.valueOf(rs.getString("void_shape")),
        rs.getString("authorizer_rejected_message"),
        AutraMerchantNotFound.valueOf(rs.getString("merchant_not_found_status")),
        AutraSaleDecline.valueOf(rs.getString("sale_decline_status")),
        AutraAcquirerReport.valueOf(rs.getString("acquirer_transaction_identifier")),
        rs.getString("webhook_secret"));
  }
}
