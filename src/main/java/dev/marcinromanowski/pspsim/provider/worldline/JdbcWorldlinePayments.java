package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.LedgerClaim;
import dev.marcinromanowski.pspsim.kernel.money.Ledgers;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.money.OperationOf;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

final class JdbcWorldlinePayments implements WorldlinePayments {

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;

  JdbcWorldlinePayments(JdbcClient jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
  }

  @Override
  public boolean claimIdentifier(
      TestRunId testRunId, String identifier, String paymentId, int payIdSub, Instant at) {
    return jdbc.sql(
                """
                insert into worldline_payment_identifier
                    (test_run_id, identifier, payment_id, payid_sub, minted_at)
                values (?, ?, ?, ?, ?)
                on conflict (test_run_id, identifier) do nothing
                """)
            .param(testRunId.value())
            .param(identifier)
            .param(paymentId)
            .param(payIdSub)
            .param(at.atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public boolean insertIfAbsent(WorldlinePayment payment) {
    return Boolean.TRUE.equals(transactions.execute(status -> insertInOneTransaction(payment)));
  }

  private boolean insertInOneTransaction(WorldlinePayment payment) {
    int inserted =
        jdbc.sql(
                """
                insert into worldline_payment
                    (test_run_id, payment_id, account_id, merchant_id, merchant_reference,
                     merchant_customer_id, token, hosted_tokenization_id, card_number,
                     expiry_date, amount, currency_code, authorization_mode, native_status,
                     status_category, status_code, authorization_closed, origin, created_at,
                     updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (test_run_id, payment_id) do nothing
                """)
            .param(payment.testRunId().value())
            .param(payment.paymentId())
            .param(payment.accountId())
            .param(payment.merchantId())
            .param(payment.merchantReference())
            .param(payment.merchantCustomerId())
            .param(payment.token())
            .param(payment.hostedTokenizationId())
            .param(payment.cardNumber())
            .param(payment.expiryDate())
            .param(Long.valueOf(WorldlineAmounts.minorUnitsOf(payment.amount())))
            .param(payment.currencyCode())
            .param(payment.authorizationMode().name())
            .param(payment.nativeStatus())
            .param(payment.statusCategory())
            .param(payment.statusCode())
            .param(payment.authorizationClosed())
            .param(payment.origin().name())
            .param(payment.createdAt().atOffset(ZoneOffset.UTC))
            .param(payment.updatedAt().atOffset(ZoneOffset.UTC))
            .update();
    if (inserted != 1) {
      return false;
    }
    claimIdentifier(
        payment.testRunId(),
        payment.paymentId(),
        payment.paymentId(),
        WorldlineIdentifiers.THE_FIRST_PAYIDSUB,
        payment.createdAt());
    for (MonetaryOperation operation : payment.ledger().operations()) {
      insert(payment.testRunId(), payment.paymentId(), operation);
    }
    return true;
  }

  private int insert(TestRunId testRunId, String paymentId, MonetaryOperation operation) {
    return jdbc.sql(
            """
            insert into worldline_payment_operation
                (test_run_id, payment_id, seq, kind, amount, closes_authorization,
                 created_object_id, native_status, normalized_status, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (test_run_id, payment_id, seq) do nothing
            """)
        .param(testRunId.value())
        .param(paymentId)
        .param(operation.sequence())
        .param(operation.kind().name())
        .param(operation.amount().value())
        .param(operation.closesAuthorization())
        .param(operation.createdObjectId())
        .param(operation.nativeStatus())
        .param(operation.normalizedStatus().name())
        .param(operation.at().atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public Optional<WorldlinePayment> findById(TestRunId testRunId, String anyIdentifier) {
    Optional<String> canonical =
        jdbc.sql(
                """
                select payment_id from worldline_payment_identifier
                where test_run_id = ? and identifier = ?
                """)
            .params(testRunId.value(), anyIdentifier)
            .query(String.class)
            .optional();
    if (canonical.isEmpty()) {
      return Optional.empty();
    }
    return jdbc.sql("select * from worldline_payment where test_run_id = ? and payment_id = ?")
        .params(testRunId.value(), canonical.get())
        .query(JdbcWorldlinePayments::toPayment)
        .optional()
        .map(this::withWhatItHasSinceBeenCalledAndDone);
  }

  @Override
  public List<WorldlinePayment> allById(TestRunId testRunId, List<String> anyIdentifiers) {
    List<String> canonical =
        jdbc.sql(
                """
                select distinct payment_id from worldline_payment_identifier
                where test_run_id = ? and identifier = any(?)
                """)
            .params(testRunId.value(), anyIdentifiers.toArray(String[]::new))
            .query(JdbcWorldlinePayments::toPaymentId)
            .list();
    List<WorldlinePayment> held =
        jdbc.sql(
                """
                select * from worldline_payment
                where test_run_id = ? and payment_id = any(?)
                order by arrival desc
                """)
            .params(testRunId.value(), canonical.toArray(String[]::new))
            .query(JdbcWorldlinePayments::toPayment)
            .list();
    return withWhatTheyHaveSinceBeenCalledAndDone(testRunId, held);
  }

  @Override
  public LedgerClaim applyMaintenance(WorldlinePayment payment, WorldlineMaintenance maintenance) {
    return Objects.requireNonNull(
        transactions.execute(
            status ->
                LedgerClaim.of(
                    () -> stillStores(payment),
                    () -> maintain(payment, maintenance),
                    () -> touch(payment, maintenance.at()))),
        "the unit of work answered nothing where its callback answers a LedgerClaim on every path");
  }

  private int maintain(WorldlinePayment payment, WorldlineMaintenance maintenance) {
    if (!claimIdentifier(
        payment.testRunId(),
        maintenance.mintedIdentifier(),
        payment.paymentId(),
        maintenance.payIdSub(),
        maintenance.at())) {
      return 0;
    }
    MonetaryOperation operation = maintenance.operation();
    if (operation != null && insert(payment.testRunId(), payment.paymentId(), operation) != 1) {
      return 0;
    }
    return jdbc.sql(
            """
            update worldline_payment set native_status = ?, status_category = ?, status_code = ?,
                authorization_closed = ?
            where test_run_id = ? and payment_id = ?
            """)
        .param(maintenance.nativeStatus())
        .param(maintenance.statusCategory())
        .param(maintenance.statusCode())
        .param(maintenance.authorizationClosed())
        .param(payment.testRunId().value())
        .param(payment.paymentId())
        .update();
  }

  private boolean stillStores(WorldlinePayment payment) {
    return jdbc.sql("select 1 from worldline_payment where test_run_id = ? and payment_id = ?")
        .params(payment.testRunId().value(), payment.paymentId())
        .query(Integer.class)
        .optional()
        .isPresent();
  }

  private void touch(WorldlinePayment payment, Instant at) {
    jdbc.sql("update worldline_payment set updated_at = ? where test_run_id = ? and payment_id = ?")
        .params(at.atOffset(ZoneOffset.UTC), payment.testRunId().value(), payment.paymentId())
        .update();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String paymentId) {
    return jdbc.sql(
            "select arrival from worldline_payment where test_run_id = ? and payment_id = ?")
        .params(testRunId.value(), paymentId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, paymentId));
  }

  @Override
  public List<ArrivalOf<WorldlinePayment>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    List<ArrivalOf<WorldlinePayment>> found =
        jdbc.sql(
                """
                select * from worldline_payment where test_run_id = ? and arrival < ?
                order by arrival desc limit ?
                """)
            .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
            .query(JdbcWorldlinePayments::toArrivedPayment)
            .list();
    List<String> paymentIds = found.stream().map(arrived -> arrived.row().paymentId()).toList();
    Map<String, List<String>> minted = theIdentifiersMintedFor(testRunId, paymentIds);
    Map<String, Ledger> ledgers = theLedgersOf(testRunId, paymentIds);
    List<ArrivalOf<WorldlinePayment>> whole = new ArrayList<>();
    for (ArrivalOf<WorldlinePayment> arrived : found) {
      WorldlinePayment payment = arrived.row();
      whole.add(
          new ArrivalOf<>(
              arrived.arrival(),
              with(
                  payment,
                  WorldlineMintedIdentifiers.theOnesMintedFor(minted, payment.paymentId()),
                  Ledgers.theLedgerOf(ledgers, payment.paymentId()))));
    }
    return List.copyOf(whole);
  }

  @Override
  public int clear(TestRunId testRunId) {
    jdbc.sql("delete from worldline_payment_identifier where test_run_id = ?")
        .param(testRunId.value())
        .update();
    return jdbc.sql("delete from worldline_payment where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from worldline_payment_identifier").update();
    return jdbc.sql("delete from worldline_payment").update();
  }

  private WorldlinePayment withWhatItHasSinceBeenCalledAndDone(WorldlinePayment payment) {
    TestRunId testRunId = payment.testRunId();
    List<String> named = List.of(payment.paymentId());
    return with(
        payment,
        WorldlineMintedIdentifiers.theOnesMintedFor(
            theIdentifiersMintedFor(testRunId, named), payment.paymentId()),
        Ledgers.theLedgerOf(theLedgersOf(testRunId, named), payment.paymentId()));
  }

  private List<WorldlinePayment> withWhatTheyHaveSinceBeenCalledAndDone(
      TestRunId testRunId, List<WorldlinePayment> held) {
    List<String> paymentIds = held.stream().map(WorldlinePayment::paymentId).toList();
    Map<String, List<String>> minted = theIdentifiersMintedFor(testRunId, paymentIds);
    Map<String, Ledger> ledgers = theLedgersOf(testRunId, paymentIds);
    return held.stream()
        .map(
            payment ->
                with(
                    payment,
                    WorldlineMintedIdentifiers.theOnesMintedFor(minted, payment.paymentId()),
                    Ledgers.theLedgerOf(ledgers, payment.paymentId())))
        .toList();
  }

  private Map<String, List<String>> theIdentifiersMintedFor(
      TestRunId testRunId, List<String> paymentIds) {
    return WorldlineMintedIdentifiers.keyedByPayment(
        jdbc.sql(
                """
                select payment_id, identifier from worldline_payment_identifier
                where test_run_id = ? and payment_id = any(?) and payid_sub > ?
                order by payment_id, payid_sub
                """)
            .params(
                testRunId.value(),
                paymentIds.toArray(String[]::new),
                WorldlineIdentifiers.THE_FIRST_PAYIDSUB)
            .query(JdbcWorldlinePayments::toMintedIdentifier)
            .list());
  }

  private Map<String, Ledger> theLedgersOf(TestRunId testRunId, List<String> paymentIds) {
    return Ledgers.keyedByObject(
        jdbc.sql(
                """
                select * from worldline_payment_operation
                where test_run_id = ? and payment_id = any(?)
                order by payment_id, seq
                """)
            .params(testRunId.value(), paymentIds.toArray(String[]::new))
            .query(JdbcWorldlinePayments::toStoredOperation)
            .list());
  }

  private static String toPaymentId(ResultSet rs, int row) throws SQLException {
    return rs.getString("payment_id");
  }

  private static WorldlineMintedIdentifiers.MintedFor toMintedIdentifier(ResultSet rs, int row)
      throws SQLException {
    return new WorldlineMintedIdentifiers.MintedFor(
        rs.getString("payment_id"), rs.getString("identifier"));
  }

  private static OperationOf toStoredOperation(ResultSet rs, int row) throws SQLException {
    return new OperationOf(rs.getString("payment_id"), toOperation(rs));
  }

  private static WorldlinePayment with(
      WorldlinePayment payment, List<String> maintenanceIds, Ledger ledger) {
    return new WorldlinePayment(
        payment.testRunId(),
        payment.paymentId(),
        maintenanceIds,
        payment.accountId(),
        payment.merchantId(),
        payment.merchantReference(),
        payment.merchantCustomerId(),
        payment.token(),
        payment.hostedTokenizationId(),
        payment.cardNumber(),
        payment.expiryDate(),
        payment.amount(),
        payment.currencyCode(),
        payment.authorizationMode(),
        payment.nativeStatus(),
        payment.statusCategory(),
        payment.statusCode(),
        payment.authorizationClosed(),
        ledger,
        payment.origin(),
        payment.createdAt(),
        payment.updatedAt());
  }

  private static ArrivalOf<WorldlinePayment> toArrivedPayment(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toPayment(rs, row));
  }

  private static WorldlinePayment toPayment(ResultSet rs, int row) throws SQLException {
    return new WorldlinePayment(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("payment_id"),
        List.of(),
        rs.getString("account_id"),
        rs.getString("merchant_id"),
        rs.getString("merchant_reference"),
        rs.getString("merchant_customer_id"),
        rs.getString("token"),
        rs.getString("hosted_tokenization_id"),
        rs.getString("card_number"),
        rs.getString("expiry_date"),
        WorldlineAmounts.asAnAmount(rs.getLong("amount")),
        rs.getString("currency_code"),
        WorldlineAuthorizationMode.valueOf(rs.getString("authorization_mode")),
        rs.getString("native_status"),
        rs.getString("status_category"),
        rs.getInt("status_code"),
        rs.getBoolean("authorization_closed"),
        Ledger.empty(),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static MonetaryOperation toOperation(ResultSet rs) throws SQLException {
    return new MonetaryOperation(
        rs.getInt("seq"),
        OperationKind.valueOf(rs.getString("kind")),
        Amount.of(rs.getString("amount")),
        rs.getObject("closes_authorization", Boolean.class),
        rs.getString("created_object_id"),
        rs.getString("native_status"),
        NormalizedStatus.valueOf(rs.getString("normalized_status")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }
}
