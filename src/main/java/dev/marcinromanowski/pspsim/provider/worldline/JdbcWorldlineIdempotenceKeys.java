package dev.marcinromanowski.pspsim.provider.worldline;

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
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcWorldlineIdempotenceKeys implements WorldlineIdempotenceKeys {

  static final String RUNNING = "RUNNING";

  static final String COMPLETED = "COMPLETED";

  static final KeyedExchangeTable EXCHANGES =
      KeyedExchangeTable.underANamedKey("worldline_keyed_exchange", "account_id", false);

  private final JdbcClient jdbc;
  private final KeyedExchangeLog exchanges;

  JdbcWorldlineIdempotenceKeys(JdbcClient jdbc, KeyedExchangeLog exchanges) {
    this.jdbc = jdbc;
    this.exchanges = exchanges;
  }

  @Override
  public WorldlineIdempotenceClaim claim(
      TestRunId testRunId,
      String accountId,
      String key,
      String headerName,
      String operation,
      String fingerprint,
      Instant now) {
    int taken =
        jdbc.sql(
                """
                insert into worldline_idempotence_key
                    (test_run_id, account_id, key_value, header_name, operation, fingerprint,
                     claim_state, http_status, content_type, body, provider_object_id, claimed_at)
                values (?, ?, ?, ?, ?, ?, ?, null, null, null, null, ?)
                on conflict (test_run_id, account_id, key_value) do nothing
                """)
            .param(testRunId.value())
            .param(accountId)
            .param(key)
            .param(headerName)
            .param(operation)
            .param(fingerprint)
            .param(RUNNING)
            .param(now.atOffset(ZoneOffset.UTC))
            .update();
    if (taken == 1) {
      return new WorldlineIdempotenceClaim.Fresh();
    }
    Optional<WorldlineIdempotenceClaim> held =
        jdbc.sql(
                """
                select claim_state, http_status, content_type, body, provider_object_id, claimed_at
                from worldline_idempotence_key
                where test_run_id = ? and account_id = ? and key_value = ?
                """)
            .params(testRunId.value(), accountId, key)
            .query(JdbcWorldlineIdempotenceKeys::toClaim)
            .optional();
    return held.orElseGet(WorldlineIdempotenceClaim.Fresh::new);
  }

  private static WorldlineIdempotenceClaim toClaim(ResultSet rs, int row) throws SQLException {
    String body = rs.getString("body");
    String contentType = rs.getString("content_type");
    if (!COMPLETED.equals(rs.getString("claim_state")) || body == null || contentType == null) {
      return new WorldlineIdempotenceClaim.HeldByARequestStillRunning();
    }
    return new WorldlineIdempotenceClaim.Replayed(
        new WorldlineStoredAnswer(
            rs.getInt("http_status"), contentType, body, rs.getString("provider_object_id")),
        rs.getObject("claimed_at", OffsetDateTime.class).toInstant());
  }

  @Override
  public void completed(
      TestRunId testRunId, String accountId, String key, WorldlineStoredAnswer answer) {
    jdbc.sql(
            """
            update worldline_idempotence_key set claim_state = ?, http_status = ?,
                content_type = ?, body = ?, provider_object_id = ?
            where test_run_id = ? and account_id = ? and key_value = ?
            """)
        .param(COMPLETED)
        .param(answer.httpStatus())
        .param(answer.contentType())
        .param(answer.body())
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
            delete from worldline_idempotence_key
            where test_run_id = ? and account_id = ? and key_value = ? and claim_state = ?
            """)
        .params(testRunId.value(), accountId, key, RUNNING)
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
                select e.account_id, e.key_name, e.key_value, e.arrival, e.seq, e.operation,
                       e.fingerprint, e.verdict, e.http_status, e.content_type, e.body,
                       e.provider_object_id, e.state_changed, e.answer_kept, e.at
                from worldline_keyed_exchange e
                join (select account_id, key_name, key_value, arrival
                      from worldline_keyed_exchange
                      where test_run_id = ? and seq = 0 and arrival < ?
                      order by arrival desc
                      limit ?) first
                  on first.account_id = e.account_id
                 and first.key_name = e.key_name
                 and first.key_value = e.key_value
                where e.test_run_id = ?
                order by first.arrival desc, e.seq
                """)
            .param(query.testRunId().value())
            .param(query.after())
            .param(query.limit())
            .param(query.testRunId().value())
            .query(JdbcWorldlineIdempotenceKeys::toRecordedRequest)
            .list();
    return KeyedExchanges.pageOf(WorldlineKeyMechanisms.MECHANISMS, query.testRunId(), rows);
  }

  @Override
  public int clear(TestRunId testRunId) {
    jdbc.sql("delete from worldline_keyed_exchange where test_run_id = ?")
        .param(testRunId.value())
        .update();
    return jdbc.sql("delete from worldline_idempotence_key where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from worldline_keyed_exchange").update();
    return jdbc.sql("delete from worldline_idempotence_key").update();
  }

  private static RecordedKeyedRequest toRecordedRequest(ResultSet rs, int row) throws SQLException {
    return new RecordedKeyedRequest(
        rs.getString("account_id"),
        rs.getString("key_name"),
        rs.getString("key_value"),
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
        null,
        rs.getObject("at", OffsetDateTime.class).toInstant(),
        rs.getBoolean("answer_kept"));
  }
}
