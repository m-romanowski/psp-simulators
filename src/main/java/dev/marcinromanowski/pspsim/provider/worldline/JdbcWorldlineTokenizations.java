package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcWorldlineTokenizations implements WorldlineTokenizations {

  private final JdbcClient jdbc;

  JdbcWorldlineTokenizations(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfAbsent(WorldlineTokenization session) {
    return jdbc.sql(
                """
                insert into worldline_hosted_tokenization
                    (test_run_id, hosted_tokenization_id, account_id, merchant_id, native_status,
                     token, card_number, expiry_date, expires_at, origin, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (hosted_tokenization_id) do nothing
                """)
            .param(session.testRunId().value())
            .param(session.hostedTokenizationId())
            .param(session.accountId())
            .param(session.merchantId())
            .param(session.nativeStatus())
            .param(session.token())
            .param(session.cardNumber())
            .param(session.expiryDate())
            .param(session.expiresAt().atOffset(ZoneOffset.UTC))
            .param(session.origin().name())
            .param(session.createdAt().atOffset(ZoneOffset.UTC))
            .param(session.updatedAt().atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<WorldlineTokenization> findById(
      TestRunId testRunId, String hostedTokenizationId) {
    return jdbc.sql(
            """
            select * from worldline_hosted_tokenization
            where test_run_id = ? and hosted_tokenization_id = ?
            """)
        .params(testRunId.value(), hostedTokenizationId)
        .query(JdbcWorldlineTokenizations::toSession)
        .optional();
  }

  @Override
  public List<WorldlineTokenization> allById(
      TestRunId testRunId, List<String> hostedTokenizationIds) {
    return jdbc.sql(
            """
            select * from worldline_hosted_tokenization
            where test_run_id = ? and hosted_tokenization_id = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), hostedTokenizationIds.toArray(String[]::new))
        .query(JdbcWorldlineTokenizations::toSession)
        .list();
  }

  @Override
  public Optional<WorldlineTokenization> findByIdInAnyRun(String hostedTokenizationId) {
    return jdbc.sql("select * from worldline_hosted_tokenization where hosted_tokenization_id = ?")
        .param(hostedTokenizationId)
        .query(JdbcWorldlineTokenizations::toSession)
        .optional();
  }

  @Override
  public Optional<WorldlineTokenization> holdingTheToken(TestRunId testRunId, String token) {
    return jdbc.sql(
            "select * from worldline_hosted_tokenization where test_run_id = ? and token = ?")
        .params(testRunId.value(), token)
        .query(JdbcWorldlineTokenizations::toSession)
        .optional();
  }

  @Override
  public boolean moveFromAwaitingThePayerTo(WorldlineTokenization moved) {
    return jdbc.sql(
                """
                update worldline_hosted_tokenization set native_status = ?, token = ?,
                    card_number = ?, expiry_date = ?, updated_at = ?
                where test_run_id = ? and hosted_tokenization_id = ? and native_status = ?
                """)
            .param(moved.nativeStatus())
            .param(moved.token())
            .param(moved.cardNumber())
            .param(moved.expiryDate())
            .param(moved.updatedAt().atOffset(ZoneOffset.UTC))
            .param(moved.testRunId().value())
            .param(moved.hostedTokenizationId())
            .param(WorldlineStatus.AWAITING_THE_PAYER)
            .update()
        == 1;
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String hostedTokenizationId) {
    return jdbc.sql(
            """
            select arrival from worldline_hosted_tokenization
            where test_run_id = ? and hosted_tokenization_id = ?
            """)
        .params(testRunId.value(), hostedTokenizationId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, hostedTokenizationId));
  }

  @Override
  public List<ArrivalOf<WorldlineTokenization>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from worldline_hosted_tokenization where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(JdbcWorldlineTokenizations::toArrivedSession)
        .list();
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from worldline_hosted_tokenization where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from worldline_hosted_tokenization").update();
  }

  private static ArrivalOf<WorldlineTokenization> toArrivedSession(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toSession(rs, row));
  }

  private static WorldlineTokenization toSession(ResultSet rs, int row) throws SQLException {
    return new WorldlineTokenization(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("hosted_tokenization_id"),
        rs.getString("account_id"),
        rs.getString("merchant_id"),
        rs.getString("native_status"),
        rs.getString("token"),
        rs.getString("card_number"),
        rs.getString("expiry_date"),
        rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }
}
