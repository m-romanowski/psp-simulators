package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.idempotency.RecordedKeyedRequest;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeLog;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchangeTable;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedExchanges;
import dev.marcinromanowski.pspsim.kernel.spi.KeyedObservation;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

final class JdbcStripeIdempotencyKeys implements StripeIdempotencyKeys {

  static final KeyedExchangeTable EXCHANGES =
      KeyedExchangeTable.underItsOnlyKey(
          "stripe_idempotency_exchange", "account_id", "idempotency_key", true);

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final KeyedExchangeLog exchanges;

  JdbcStripeIdempotencyKeys(
      JdbcClient jdbc, TransactionTemplate transactions, KeyedExchangeLog exchanges) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.exchanges = exchanges;
  }

  @Override
  public StripeIdempotencyClaim claim(
      TestRunId testRunId,
      String accountId,
      String key,
      String fingerprint,
      Instant now,
      Instant prunedBefore) {
    return Objects.requireNonNull(
        transactions.execute(
            status ->
                claimInOneTransaction(testRunId, accountId, key, fingerprint, now, prunedBefore)),
        "the unit of work answered nothing where its callback answers a StripeIdempotencyClaim"
            + " on every path");
  }

  private StripeIdempotencyClaim claimInOneTransaction(
      TestRunId testRunId,
      String accountId,
      String key,
      String fingerprint,
      Instant now,
      Instant prunedBefore) {
    jdbc.sql(
            """
            delete from stripe_idempotency_key
            where test_run_id = ? and account_id = ? and idempotency_key = ? and created_at <= ?
            """)
        .params(testRunId.value(), accountId, key, prunedBefore.atOffset(ZoneOffset.UTC))
        .update();
    int claimed =
        jdbc.sql(
                """
                insert into stripe_idempotency_key
                    (test_run_id, account_id, idempotency_key, fingerprint, in_progress, created_at)
                values (?, ?, ?, ?, true, ?)
                on conflict (test_run_id, account_id, idempotency_key) do nothing
                """)
            .params(testRunId.value(), accountId, key, fingerprint, now.atOffset(ZoneOffset.UTC))
            .update();
    if (claimed == 1) {
      return new StripeIdempotencyClaim.Fresh();
    }
    return held(testRunId, accountId, key)
        .map(row -> verdictOf(row, fingerprint))
        .orElseGet(StripeIdempotencyClaim.InProgressElsewhere::new);
  }

  private static StripeIdempotencyClaim verdictOf(HeldKey row, String fingerprint) {
    if (row.inProgress()) {
      return new StripeIdempotencyClaim.InProgressElsewhere();
    }
    if (!row.fingerprint().equals(fingerprint)) {
      return new StripeIdempotencyClaim.ReusedWithOtherParameters();
    }
    Integer httpStatus = row.httpStatus();
    String contentType = row.contentType();
    String body = row.body();
    String outcome = row.outcome();
    if (httpStatus == null || contentType == null || body == null || outcome == null) {
      return new StripeIdempotencyClaim.InProgressElsewhere();
    }
    return new StripeIdempotencyClaim.Replayed(
        new StripeStoredAnswer(
            httpStatus,
            contentType,
            body,
            ProviderOutcome.valueOf(outcome),
            row.providerObjectId()));
  }

  private Optional<HeldKey> held(TestRunId testRunId, String accountId, String key) {
    return jdbc.sql(
            """
            select fingerprint, in_progress, http_status, content_type, body, outcome,
                   provider_object_id
            from stripe_idempotency_key
            where test_run_id = ? and account_id = ? and idempotency_key = ?
            """)
        .params(testRunId.value(), accountId, key)
        .query(JdbcStripeIdempotencyKeys::toHeldKey)
        .optional();
  }

  private static HeldKey toHeldKey(ResultSet rs, int row) throws SQLException {
    return new HeldKey(
        rs.getString("fingerprint"),
        rs.getBoolean("in_progress"),
        rs.getObject("http_status", Integer.class),
        rs.getString("content_type"),
        rs.getString("body"),
        rs.getString("outcome"),
        rs.getString("provider_object_id"));
  }

  @Override
  public void completed(
      TestRunId testRunId, String accountId, String key, StripeStoredAnswer answer) {
    jdbc.sql(
            """
            update stripe_idempotency_key
            set in_progress = false, http_status = ?, content_type = ?, body = ?, outcome = ?,
                provider_object_id = ?
            where test_run_id = ? and account_id = ? and idempotency_key = ? and in_progress
            """)
        .param(answer.httpStatus())
        .param(answer.contentType())
        .param(answer.body())
        .param(answer.outcome().name())
        .param(answer.providerObjectId())
        .param(testRunId.value())
        .param(accountId)
        .param(key)
        .update();
  }

  @Override
  public void released(TestRunId testRunId, String accountId, String key) {
    jdbc.sql(
            """
            delete from stripe_idempotency_key
            where test_run_id = ? and account_id = ? and idempotency_key = ? and in_progress
            """)
        .params(testRunId.value(), accountId, key)
        .update();
  }

  @Override
  public String fingerprintOf(TestRunId testRunId, FingerprintedRequest request) {
    return exchanges.fingerprintOf(testRunId, request);
  }

  @Override
  public void observed(KeyedObservation observation) {
    exchanges.record(observation);
  }

  @Override
  public KeyedExchangePage exchanges(KeyedExchangeQuery query) {
    List<RecordedKeyedRequest> rows =
        jdbc.sql(
                """
                select e.account_id, e.idempotency_key, e.arrival, e.seq, e.operation,
                       e.fingerprint, e.verdict, e.http_status, e.content_type, e.body,
                       e.provider_object_id, e.state_changed, e.fault_kind, e.fault_placement, e.at
                from stripe_idempotency_exchange e
                join (select account_id, idempotency_key, arrival
                      from stripe_idempotency_exchange
                      where test_run_id = ? and seq = 0 and arrival < ?
                      order by arrival desc
                      limit ?) first
                  on first.account_id = e.account_id
                 and first.idempotency_key = e.idempotency_key
                where e.test_run_id = ?
                order by first.arrival desc, e.seq
                """)
            .param(query.testRunId().value())
            .param(query.after())
            .param(query.limit())
            .param(query.testRunId().value())
            .query(JdbcStripeIdempotencyKeys::toRecordedRequest)
            .list();
    return KeyedExchanges.pageOf(StripeKeyMechanisms.MECHANISMS, query.testRunId(), rows);
  }

  private static RecordedKeyedRequest toRecordedRequest(ResultSet rs, int row) throws SQLException {
    String faultKind = rs.getString("fault_kind");
    String faultPlacement = rs.getString("fault_placement");
    return new RecordedKeyedRequest(
        rs.getString("account_id"),
        StripeKeyMechanisms.IDEMPOTENCY_KEY,
        rs.getString("idempotency_key"),
        rs.getLong("arrival"),
        rs.getInt("seq"),
        ExchangeVerdict.valueOf(rs.getString("verdict")),
        rs.getString("fingerprint"),
        rs.getString("operation"),
        rs.getObject("http_status", Integer.class),
        rs.getString("content_type"),
        rs.getString("body"),
        rs.getString("provider_object_id"),
        rs.getBoolean("state_changed"),
        faultKind == null || faultPlacement == null
            ? null
            : new AppliedFault(
                TransportFaultKind.valueOf(faultKind), FaultPlacement.valueOf(faultPlacement)),
        rs.getObject("at", OffsetDateTime.class).toInstant());
  }

  @Override
  public int clear(TestRunId testRunId) {
    jdbc.sql("delete from stripe_idempotency_exchange where test_run_id = ?")
        .param(testRunId.value())
        .update();
    return jdbc.sql("delete from stripe_idempotency_key where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from stripe_idempotency_exchange").update();
    return jdbc.sql("delete from stripe_idempotency_key").update();
  }

  private record HeldKey(
      String fingerprint,
      boolean inProgress,
      @Nullable Integer httpStatus,
      @Nullable String contentType,
      @Nullable String body,
      @Nullable String outcome,
      @Nullable String providerObjectId) {}
}
