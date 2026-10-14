package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcTranzilaRecords implements TranzilaRecords {

  private final JdbcClient jdbc;

  JdbcTranzilaRecords(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<TranzilaRecorded> byIndex(
      TestRunId testRunId, String accountId, long transactionId) {
    return described(
            testRunId,
            jdbc.sql(
                    """
                    select * from tranzila_transaction
                    where test_run_id = ? and account_id = ? and transaction_id = ?
                    """)
                .params(testRunId.value(), accountId, Long.valueOf(transactionId))
                .query(JdbcTranzilaTransactions::toTransaction)
                .list())
        .stream()
        .findFirst();
  }

  @Override
  public List<TranzilaRecorded> between(
      TestRunId testRunId, String accountId, TranzilaLookup.Window window) {
    return described(
        testRunId,
        jdbc.sql(
                """
                select * from tranzila_transaction
                where test_run_id = ? and account_id = ?
                  and (created_at at time zone 'UTC')::date between ? and ?
                order by extract(epoch from created_at) * ?, arrival * ?
                offset ? limit ?
                """)
            .param(testRunId.value())
            .param(accountId)
            .param(window.start())
            .param(window.end())
            .param(Integer.valueOf(window.direction()))
            .param(Integer.valueOf(window.direction()))
            .param(Long.valueOf(window.offset()))
            .param(Integer.valueOf(window.limit()))
            .query(JdbcTranzilaTransactions::toTransaction)
            .list());
  }

  @Override
  public long countedBetween(
      TestRunId testRunId, String accountId, LocalDate start, LocalDate end) {
    return jdbc.sql(
            """
            select count(*) from tranzila_transaction
            where test_run_id = ? and account_id = ?
              and (created_at at time zone 'UTC')::date between ? and ?
            """)
        .params(testRunId.value(), accountId, start, end)
        .query(Long.class)
        .single()
        .longValue();
  }

  @Override
  public List<TranzilaUserSlot> slotsOf(TestRunId testRunId, String accountId) {
    return jdbc.sql(
            """
            select slot, name, first_recorded_at from tranzila_user_slot
            where test_run_id = ? and account_id = ?
            order by slot
            """)
        .params(testRunId.value(), accountId)
        .query(JdbcTranzilaRecords::toSlot)
        .list();
  }

  private List<TranzilaRecorded> described(
      TestRunId testRunId, List<TranzilaTransaction> transactions) {
    Long[] ids =
        transactions.stream()
            .map(transaction -> Long.valueOf(transaction.transactionId()))
            .toArray(Long[]::new);
    return TranzilaRecorded.assembled(
        transactions,
        jdbc.sql(
                """
                select transaction_id, name, value from tranzila_transaction_user_field
                where test_run_id = ? and transaction_id = any(?)
                order by transaction_id, position
                """)
            .params(testRunId.value(), ids)
            .query(JdbcTranzilaRecords::toField)
            .list(),
        jdbc.sql(
                """
                select transaction_id, item_id, name, units_number, unit_price, item_type
                from tranzila_transaction_item
                where test_run_id = ? and transaction_id = any(?)
                order by transaction_id, position
                """)
            .params(testRunId.value(), ids)
            .query(JdbcTranzilaRecords::toItem)
            .list());
  }

  private static TranzilaRecorded.Owned<TranzilaUserField> toField(ResultSet rs, int row)
      throws SQLException {
    return new TranzilaRecorded.Owned<>(
        rs.getLong("transaction_id"),
        new TranzilaUserField(rs.getString("name"), rs.getString("value")));
  }

  private static TranzilaRecorded.Owned<TranzilaRecorded.Item> toItem(ResultSet rs, int row)
      throws SQLException {
    return new TranzilaRecorded.Owned<>(
        rs.getLong("transaction_id"),
        new TranzilaRecorded.Item(
            rs.getLong("item_id"),
            new TranzilaItem(
                rs.getString("name"),
                rs.getLong("units_number"),
                rs.getString("unit_price"),
                rs.getString("item_type"))));
  }

  private static TranzilaUserSlot toSlot(ResultSet rs, int row) throws SQLException {
    return new TranzilaUserSlot(
        rs.getInt("slot"),
        rs.getString("name"),
        rs.getObject("first_recorded_at", OffsetDateTime.class).toInstant());
  }
}
