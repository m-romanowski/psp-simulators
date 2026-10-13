package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

final class JdbcTranzilaTransactions implements TranzilaTransactions {

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  JdbcTranzilaTransactions(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @Override
  public TranzilaInsert insert(TranzilaTransaction transaction) {
    return TranzilaInsert.ofTheRowsWritten(written(transaction), holding(transaction));
  }

  @Override
  public <T> T oneAtATimeUnder(TranzilaDcDisableKey key, Supplier<T> decision) {
    return Objects.requireNonNull(
        transactions.execute(
            status -> {
              jdbc.sql(
                      """
                      select count(*) from (select pg_advisory_xact_lock(?, ?)) as taken
                      """)
                  .params(TranzilaDcDisableKey.LOCK_SPACE, key.lock())
                  .query(Long.class)
                  .single();
              return decision.get();
            }),
        "the unit of work answered nothing where its callback answers on every path");
  }

  private int written(TranzilaTransaction transaction) {
    return jdbc.sql(
            """
            insert into tranzila_transaction
                (test_run_id, transaction_id, root_txn_id, account_id, terminal_name, txn_type,
                 verify_mode, reference_txn_id, processor_response_code, amount, currency_code,
                 auth_number, token, card_mask, card_type, expiry_month, expiry_year,
                 response_cvv, response_id, dc_disable, hold_expires_at, origin, created_at,
                 card_fingerprint)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict do nothing
            """)
        .param(transaction.testRunId().value())
        .param(Long.valueOf(transaction.transactionId()))
        .param(Long.valueOf(transaction.rootTxnId()))
        .param(transaction.accountId())
        .param(transaction.terminalName())
        .param(transaction.type().wire())
        .param(transaction.verifyMode())
        .param(transaction.referenceTxnId())
        .param(transaction.processorResponseCode())
        .param(transaction.amount())
        .param(transaction.currencyCode())
        .param(transaction.authNumber())
        .param(transaction.token())
        .param(transaction.card().cardMask())
        .param(Integer.valueOf(transaction.card().cardType()))
        .param(Integer.valueOf(transaction.card().expiryMonth()))
        .param(Integer.valueOf(transaction.card().expiryYear()))
        .param(transaction.responseCvv())
        .param(transaction.responseId())
        .param(transaction.dcDisable())
        .param(TranzilaStoredForm.timestampOf(transaction.holdExpiresAt()))
        .param(transaction.origin().name())
        .param(transaction.createdAt().atOffset(ZoneOffset.UTC))
        .param(transaction.card().fingerprint())
        .update();
  }

  private int holding(TranzilaTransaction transaction) {
    return jdbc.sql(
            """
            select count(*) from tranzila_transaction
            where test_run_id = ? and transaction_id = ?
            """)
        .params(transaction.testRunId().value(), Long.valueOf(transaction.transactionId()))
        .query(Integer.class)
        .single()
        .intValue();
  }

  @Override
  public Optional<TranzilaPayment> paymentHolding(TestRunId testRunId, long transactionId) {
    return TranzilaGroups.theOnePaymentOf(
        jdbc.sql(
                """
                select * from tranzila_transaction
                where test_run_id = ? and root_txn_id in
                    (select root_txn_id from tranzila_transaction
                     where test_run_id = ? and transaction_id = ?)
                order by arrival
                """)
            .params(testRunId.value(), testRunId.value(), Long.valueOf(transactionId))
            .query(JdbcTranzilaTransactions::toTransaction)
            .list());
  }

  @Override
  public List<TranzilaPayment> paymentsHolding(TestRunId testRunId, List<Long> transactionIds) {
    return TranzilaGroups.paymentsOf(
        jdbc.sql(
                """
                select * from tranzila_transaction
                where test_run_id = ? and root_txn_id in
                    (select root_txn_id from tranzila_transaction
                     where test_run_id = ? and transaction_id = any(?))
                order by arrival
                """)
            .params(testRunId.value(), testRunId.value(), transactionIds.toArray(Long[]::new))
            .query(JdbcTranzilaTransactions::toTransaction)
            .list());
  }

  @Override
  public Optional<TranzilaTransaction> carryingTheToken(
      TestRunId testRunId, String accountId, String token) {
    return jdbc.sql(
            """
            select * from tranzila_transaction
            where test_run_id = ? and account_id = ? and token = ?
            order by arrival desc limit 1
            """)
        .params(testRunId.value(), accountId, token)
        .query(JdbcTranzilaTransactions::toTransaction)
        .optional();
  }

  @Override
  public Optional<TranzilaTransaction> approvedUnderDcDisable(
      TranzilaDcDisableKey key, Instant since) {
    return jdbc.sql(
            """
            select * from tranzila_transaction
            where test_run_id = ? and account_id = ? and dc_disable = ? and txn_type = ?
              and verify_mode is not distinct from ?
              and processor_response_code in ('000', '777') and created_at > ?
            order by arrival limit 1
            """)
        .param(key.testRunId().value())
        .param(key.accountId())
        .param(key.dcDisable())
        .param(key.type().wire())
        .param(key.verifyMode())
        .param(since.atOffset(ZoneOffset.UTC))
        .query(JdbcTranzilaTransactions::toTransaction)
        .optional();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, long transactionId) {
    return jdbc.sql(
            """
            select arrival from tranzila_transaction
            where test_run_id = ? and transaction_id = ?
            """)
        .params(testRunId.value(), Long.valueOf(transactionId))
        .query(Long.class)
        .optional()
        .map(
            arrival -> new ProviderObjectCursor(arrival.longValue(), Long.toString(transactionId)));
  }

  @Override
  public List<ArrivalOf<TranzilaObject>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    List<ArrivalOf<Long>> listed =
        jdbc.sql(
                """
                select arrival, transaction_id from tranzila_transaction
                where test_run_id = ? and arrival < ?
                order by arrival desc limit ?
                """)
            .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
            .query(JdbcTranzilaTransactions::toListed)
            .list();
    return TranzilaGroups.inTheOrderOf(
        listed,
        jdbc.sql(
                """
                select * from tranzila_transaction
                where test_run_id = ? and root_txn_id in
                    (select root_txn_id from tranzila_transaction
                     where test_run_id = ? and transaction_id = any(?))
                order by arrival
                """)
            .params(
                testRunId.value(),
                testRunId.value(),
                listed.stream().map(ArrivalOf::row).toArray(Long[]::new))
            .query(JdbcTranzilaTransactions::toTransaction)
            .list());
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from tranzila_transaction where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from tranzila_transaction").update();
  }

  private static ArrivalOf<Long> toListed(ResultSet rs, int row) throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), Long.valueOf(rs.getLong("transaction_id")));
  }

  private static TranzilaTransaction toTransaction(ResultSet rs, int row) throws SQLException {
    return new TranzilaTransaction(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getLong("transaction_id"),
        rs.getLong("root_txn_id"),
        rs.getString("account_id"),
        rs.getString("terminal_name"),
        TranzilaStoredForm.typeOf(rs.getString("txn_type")),
        rs.getObject("verify_mode", Integer.class),
        rs.getObject("reference_txn_id", Long.class),
        rs.getString("processor_response_code"),
        rs.getString("amount"),
        rs.getString("currency_code"),
        rs.getString("auth_number"),
        rs.getString("token"),
        new TranzilaCard(
            rs.getString("card_mask"),
            rs.getInt("card_type"),
            rs.getInt("expiry_month"),
            rs.getInt("expiry_year"),
            rs.getString("card_fingerprint")),
        rs.getString("response_cvv"),
        rs.getString("response_id"),
        rs.getString("dc_disable"),
        TranzilaStoredForm.instantOf(rs.getObject("hold_expires_at", OffsetDateTime.class)),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }
}
