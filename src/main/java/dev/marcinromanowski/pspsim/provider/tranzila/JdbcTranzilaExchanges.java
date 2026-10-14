package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
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
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcTranzilaExchanges implements TranzilaExchanges {

  static final KeyedExchangeTable EXCHANGES =
      KeyedExchangeTable.underANamedKey("tranzila_keyed_exchange", "account_id", false);

  private final JdbcClient jdbc;
  private final KeyedExchangeLog exchanges;

  JdbcTranzilaExchanges(JdbcClient jdbc, KeyedExchangeLog exchanges) {
    this.jdbc = jdbc;
    this.exchanges = exchanges;
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
                from tranzila_keyed_exchange e
                join (select account_id, key_name, key_value, arrival
                      from tranzila_keyed_exchange
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
            .query(JdbcTranzilaExchanges::toRecordedRequest)
            .list();
    return KeyedExchanges.pageOf(TranzilaKeyMechanisms.MECHANISMS, query.testRunId(), rows);
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from tranzila_keyed_exchange where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from tranzila_keyed_exchange").update();
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
