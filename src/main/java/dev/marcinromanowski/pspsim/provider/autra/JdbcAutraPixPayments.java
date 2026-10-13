package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcAutraPixPayments implements AutraPixPayments {

  private final JdbcClient jdbc;

  JdbcAutraPixPayments(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfAbsent(AutraPixPayment payment) {
    return jdbc.sql(
                """
                insert into autra_pix_payment
                    (test_run_id, pix_id, account_id, document_id, order_id, description, amount,
                     currency, native_status, qr_code, slug, muid, expires_at, origin, created_at,
                     updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (test_run_id, pix_id) do nothing
                """)
            .param(payment.testRunId().value())
            .param(payment.pixId())
            .param(payment.accountId())
            .param(payment.documentId())
            .param(payment.orderId())
            .param(payment.description())
            .param(payment.amount().value())
            .param(payment.currency())
            .param(payment.nativeStatus())
            .param(payment.qrCode())
            .param(payment.slug())
            .param(payment.muid())
            .param(payment.expiresAt().atOffset(ZoneOffset.UTC))
            .param(payment.origin().name())
            .param(payment.createdAt().atOffset(ZoneOffset.UTC))
            .param(payment.updatedAt().atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<AutraPixPayment> findById(TestRunId testRunId, String pixId) {
    return jdbc.sql("select * from autra_pix_payment where test_run_id = ? and pix_id = ?")
        .params(testRunId.value(), pixId)
        .query(JdbcAutraPixPayments::toPix)
        .optional();
  }

  @Override
  public List<AutraPixPayment> allById(TestRunId testRunId, List<String> pixIds) {
    return jdbc.sql(
            """
            select * from autra_pix_payment
            where test_run_id = ? and pix_id = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), pixIds.toArray(String[]::new))
        .query(JdbcAutraPixPayments::toPix)
        .list();
  }

  @Override
  public boolean moveFromPendingTo(AutraPixPayment payment, String nativeStatus, Instant at) {
    return move(payment, AutraStatus.PENDING, nativeStatus, at);
  }

  @Override
  public boolean moveFromAuthorizedToSettled(AutraPixPayment payment, Instant at) {
    return move(payment, AutraStatus.AUTHORIZED, AutraStatus.SETTLED, at);
  }

  private boolean move(AutraPixPayment payment, String from, String to, Instant at) {
    return jdbc.sql(
                """
                update autra_pix_payment set native_status = ?, updated_at = ?
                where test_run_id = ? and pix_id = ? and native_status = ?
                """)
            .param(to)
            .param(at.atOffset(ZoneOffset.UTC))
            .param(payment.testRunId().value())
            .param(payment.pixId())
            .param(from)
            .update()
        == 1;
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String pixId) {
    return jdbc.sql("select arrival from autra_pix_payment where test_run_id = ? and pix_id = ?")
        .params(testRunId.value(), pixId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, pixId));
  }

  @Override
  public List<ArrivalOf<AutraPixPayment>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from autra_pix_payment where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(JdbcAutraPixPayments::toArrivedPix)
        .list();
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from autra_pix_payment where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from autra_pix_payment").update();
  }

  private static ArrivalOf<AutraPixPayment> toArrivedPix(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toPix(rs, row));
  }

  private static AutraPixPayment toPix(ResultSet rs, int row) throws SQLException {
    return new AutraPixPayment(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("pix_id"),
        rs.getString("account_id"),
        rs.getString("document_id"),
        rs.getString("order_id"),
        rs.getString("description"),
        Amount.of(rs.getString("amount")),
        rs.getString("currency"),
        rs.getString("native_status"),
        rs.getString("qr_code"),
        rs.getString("slug"),
        rs.getString("muid"),
        rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }
}
