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
  public TranzilaInsert insert(
      TranzilaTransaction transaction,
      List<TranzilaUserField> userDefinedFields,
      List<TranzilaItem> items) {
    jdbc.sql(
            """
            select count(*) from (select pg_advisory_xact_lock(?, ?)) as taken
            """)
        .params(
            TranzilaUserSlot.LOCK_SPACE,
            TranzilaUserSlot.lockOf(transaction.testRunId(), transaction.accountId()))
        .query(Long.class)
        .single();
    List<TranzilaUserField> slotted = TranzilaUserField.theOnesASlotKeeps(userDefinedFields);
    return TranzilaInsert.ofTheRowsWritten(
        written(transaction, slotted, items), holding(transaction));
  }

  @Override
  public <T> T together(Supplier<T> work) {
    return Objects.requireNonNull(
        transactions.execute(status -> work.get()),
        "the unit of work answered nothing where its callback answers on every path");
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

  private int written(
      TranzilaTransaction transaction, List<TranzilaUserField> fields, List<TranzilaItem> items) {
    return jdbc.sql(
            """
            with stored as (
                insert into tranzila_transaction
                    (test_run_id, transaction_id, root_txn_id, account_id, terminal_name, txn_type,
                     verify_mode, reference_txn_id, processor_response_code, amount, currency_code,
                     auth_number, token, card_mask, card_type, expiry_month, expiry_year,
                     response_cvv, response_id, dc_disable, hold_expires_at, origin, created_at,
                     broker)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                returning test_run_id, transaction_id, account_id, created_at
            ),
            listed as (
                select sent.name, sent.value, sent.position
                from unnest(?::text[], ?::text[]) with ordinality as sent (name, value, position)
            ),
            held as (
                select slot.name, slot.slot
                from tranzila_user_slot slot, stored
                where slot.test_run_id = stored.test_run_id and slot.account_id = stored.account_id
            ),
            fresh as (
                select news.name, row_number() over (order by news.first_position) as nth
                from (
                    select listed.name, min(listed.position) as first_position
                    from listed
                    where listed.name not in (select held.name from held)
                    group by listed.name
                ) news
            ),
            claimed as (
                insert into tranzila_user_slot
                    (test_run_id, account_id, slot, name, first_recorded_at)
                select stored.test_run_id, stored.account_id,
                       (select coalesce(max(held.slot), 0) from held) + fresh.nth, fresh.name,
                       stored.created_at
                from stored, fresh
                where (select coalesce(max(held.slot), 0) from held) + fresh.nth <= ?
                on conflict do nothing
                returning name
            ),
            kept as (
                insert into tranzila_transaction_user_field
                    (test_run_id, transaction_id, position, name, value)
                select stored.test_run_id, stored.transaction_id, listed.position - 1, listed.name,
                       listed.value
                from stored, listed
                where listed.name in (select held.name from held)
                   or listed.name in (select claimed.name from claimed)
                returning 1
            ),
            itemized as (
                insert into tranzila_transaction_item
                    (test_run_id, transaction_id, position, name, units_number, unit_price,
                     item_type)
                select stored.test_run_id, stored.transaction_id, sent.position - 1, sent.name,
                       sent.units_number, sent.unit_price, sent.item_type
                from stored,
                     unnest(?::text[], ?::bigint[], ?::text[], ?::text[]) with ordinality
                         as sent (name, units_number, unit_price, item_type, position)
                returning 1
            )
            select count(*) from stored
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
        .param(TranzilaStoredForm.brokerNameOf(transaction.broker()))
        .param(fields.stream().map(TranzilaUserField::name).toArray(String[]::new))
        .param(fields.stream().map(TranzilaUserField::value).toArray(String[]::new))
        .param(Integer.valueOf(TranzilaUserSlot.THE_MOST_SLOTS))
        .param(items.stream().map(TranzilaItem::name).toArray(String[]::new))
        .param(items.stream().map(item -> Long.valueOf(item.unitsNumber())).toArray(Long[]::new))
        .param(items.stream().map(TranzilaItem::unitPrice).toArray(String[]::new))
        .param(items.stream().map(TranzilaItem::itemType).toArray(String[]::new))
        .query(Integer.class)
        .single()
        .intValue();
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
  public Optional<TranzilaWallet> theWalletThatMinted(
      TestRunId testRunId, String accountId, String token) {
    return jdbc.sql(
            """
            select broker from tranzila_transaction
            where test_run_id = ? and account_id = ? and token = ?
              and transaction_id = root_txn_id and broker is not null
            order by arrival limit 1
            """)
        .params(testRunId.value(), accountId, token)
        .query(String.class)
        .optional()
        .flatMap(TranzilaWallet::ofTheBroker);
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
    jdbc.sql("delete from tranzila_user_slot where test_run_id = ?")
        .param(testRunId.value())
        .update();
    return jdbc.sql("delete from tranzila_transaction where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from tranzila_user_slot").update();
    return jdbc.sql("delete from tranzila_transaction").update();
  }

  private static ArrivalOf<Long> toListed(ResultSet rs, int row) throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), Long.valueOf(rs.getLong("transaction_id")));
  }

  static TranzilaTransaction toTransaction(ResultSet rs, int row) throws SQLException {
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
            rs.getInt("expiry_year")),
        rs.getString("response_cvv"),
        rs.getString("response_id"),
        rs.getString("dc_disable"),
        TranzilaStoredForm.instantOf(rs.getObject("hold_expires_at", OffsetDateTime.class)),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        TranzilaStoredForm.brokerOf(rs.getString("broker")));
  }
}
