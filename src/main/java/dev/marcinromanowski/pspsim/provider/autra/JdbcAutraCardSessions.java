package dev.marcinromanowski.pspsim.provider.autra;

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

final class JdbcAutraCardSessions implements AutraCardSessions {

  private final JdbcClient jdbc;

  JdbcAutraCardSessions(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfAbsent(AutraCardSession session) {
    AutraStoredCard card = session.storedCard();
    return jdbc.sql(
                """
                insert into autra_card_session
                    (test_run_id, card_session_id, account_id, document_id, nickname,
                     native_status, page_token, slug_token, slug_stored_card,
                     token_expiration_date, brand, first4, last4, expiration_month,
                     expiration_year, expires_at, origin, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                """)
            .param(session.testRunId().value())
            .param(session.cardSessionId())
            .param(session.accountId())
            .param(session.documentId())
            .param(session.nickname())
            .param(session.nativeStatus())
            .param(session.pageToken())
            .param(card == null ? null : card.slugToken())
            .param(card == null ? null : card.slugStoredCard())
            .param(card == null ? null : card.tokenExpirationDate())
            .param(card == null ? null : card.brand())
            .param(card == null ? null : card.first4())
            .param(card == null ? null : card.last4())
            .param(card == null ? null : card.expirationMonth())
            .param(card == null ? null : card.expirationYear())
            .param(session.expiresAt().atOffset(ZoneOffset.UTC))
            .param(session.origin().name())
            .param(session.createdAt().atOffset(ZoneOffset.UTC))
            .param(session.updatedAt().atOffset(ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<AutraCardSession> findById(TestRunId testRunId, String cardSessionId) {
    return jdbc.sql(
            "select * from autra_card_session where test_run_id = ? and card_session_id = ?")
        .params(testRunId.value(), cardSessionId)
        .query(JdbcAutraCardSessions::toSession)
        .optional();
  }

  @Override
  public List<AutraCardSession> allById(TestRunId testRunId, List<String> cardSessionIds) {
    return jdbc.sql(
            """
            select * from autra_card_session
            where test_run_id = ? and card_session_id = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), cardSessionIds.toArray(String[]::new))
        .query(JdbcAutraCardSessions::toSession)
        .list();
  }

  @Override
  public Optional<AutraCardSession> findByPage(String cardSessionId, String pageToken) {
    return jdbc.sql("select * from autra_card_session where page_token = ? and card_session_id = ?")
        .params(pageToken, cardSessionId)
        .query(JdbcAutraCardSessions::toSession)
        .optional();
  }

  @Override
  public List<AutraStoredCard> tokenizedIn(TestRunId testRunId) {
    return jdbc.sql("select * from autra_card_session where test_run_id = ? and native_status = ?")
        .params(testRunId.value(), AutraCardSession.TOKENIZED)
        .query((rs, row) -> aStoredCardIn(rs))
        .list();
  }

  @Override
  public boolean moveFromPendingTo(AutraCardSession moved) {
    AutraStoredCard card = moved.storedCard();
    return jdbc.sql(
                """
                update autra_card_session set native_status = ?, nickname = ?, slug_token = ?,
                    slug_stored_card = ?, token_expiration_date = ?, brand = ?, first4 = ?,
                    last4 = ?, expiration_month = ?, expiration_year = ?, updated_at = ?
                where test_run_id = ? and card_session_id = ? and native_status = ?
                """)
            .param(moved.nativeStatus())
            .param(moved.nickname())
            .param(card == null ? null : card.slugToken())
            .param(card == null ? null : card.slugStoredCard())
            .param(card == null ? null : card.tokenExpirationDate())
            .param(card == null ? null : card.brand())
            .param(card == null ? null : card.first4())
            .param(card == null ? null : card.last4())
            .param(card == null ? null : card.expirationMonth())
            .param(card == null ? null : card.expirationYear())
            .param(moved.updatedAt().atOffset(ZoneOffset.UTC))
            .param(moved.testRunId().value())
            .param(moved.cardSessionId())
            .param(AutraStatus.PENDING)
            .update()
        == 1;
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String cardSessionId) {
    return jdbc.sql(
            "select arrival from autra_card_session where test_run_id = ? and card_session_id = ?")
        .params(testRunId.value(), cardSessionId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, cardSessionId));
  }

  @Override
  public List<ArrivalOf<AutraCardSession>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from autra_card_session where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(JdbcAutraCardSessions::toArrivedSession)
        .list();
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from autra_card_session where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from autra_card_session").update();
  }

  private static ArrivalOf<AutraCardSession> toArrivedSession(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toSession(rs, row));
  }

  private static AutraCardSession toSession(ResultSet rs, int row) throws SQLException {
    return new AutraCardSession(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("card_session_id"),
        rs.getString("account_id"),
        rs.getString("document_id"),
        rs.getString("nickname"),
        rs.getString("native_status"),
        rs.getString("page_token"),
        toStoredCard(rs),
        rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static @Nullable AutraStoredCard toStoredCard(ResultSet rs) throws SQLException {
    String slugToken = rs.getString("slug_token");
    String slugStoredCard = rs.getString("slug_stored_card");
    if (slugToken == null && slugStoredCard == null) {
      return null;
    }
    return aStoredCardIn(rs);
  }

  private static AutraStoredCard aStoredCardIn(ResultSet rs) throws SQLException {
    return new AutraStoredCard(
        rs.getString("slug_token"),
        rs.getString("slug_stored_card"),
        rs.getString("token_expiration_date"),
        rs.getString("brand"),
        rs.getString("first4"),
        rs.getString("last4"),
        rs.getObject("expiration_month", Integer.class),
        rs.getObject("expiration_year", Integer.class));
  }
}
