package dev.marcinromanowski.pspsim.provider.azericard;

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

final class JdbcAzericardStoredCards implements AzericardStoredCards {

  private final JdbcClient jdbc;

  JdbcAzericardStoredCards(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean claim(AzericardStoredCard card) {
    return jdbc.sql(
                """
                insert into azericard_stored_card
                    (test_run_id, token, terminal_id, merchant_id, stored_by_int_ref, order_id,
                     fixture_id, origin, created_at, ext_net_ref)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (test_run_id, token) do nothing
                """)
            .param(card.testRunId().value())
            .param(card.token())
            .param(card.terminalId())
            .param(card.merchantId())
            .param(card.storedByIntRef())
            .param(card.order())
            .param(card.fixtureId())
            .param(card.origin().name())
            .param(card.createdAt().atOffset(ZoneOffset.UTC))
            .param(card.extNetRef())
            .update()
        == 1;
  }

  @Override
  public int forgetSeeded(TestRunId testRunId, String fixtureId) {
    return jdbc.sql("delete from azericard_stored_card where test_run_id = ? and fixture_id = ?")
        .params(testRunId.value(), fixtureId)
        .update();
  }

  @Override
  public Optional<AzericardStoredCard> findByToken(TestRunId testRunId, String token) {
    return jdbc.sql("select * from azericard_stored_card where test_run_id = ? and token = ?")
        .params(testRunId.value(), token)
        .query(JdbcAzericardStoredCards::cardOf)
        .optional();
  }

  @Override
  public List<AzericardStoredCard> allByToken(TestRunId testRunId, List<String> tokens) {
    return jdbc.sql(
            """
            select * from azericard_stored_card
            where test_run_id = ? and token = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), tokens.toArray(String[]::new))
        .query(JdbcAzericardStoredCards::cardOf)
        .list();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String token) {
    return jdbc.sql("select arrival from azericard_stored_card where test_run_id = ? and token = ?")
        .params(testRunId.value(), token)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, token));
  }

  @Override
  public List<ArrivalOf<AzericardStoredCard>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from azericard_stored_card where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(JdbcAzericardStoredCards::arrivedCardOf)
        .list();
  }

  private static ArrivalOf<AzericardStoredCard> arrivedCardOf(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), cardOf(rs, row));
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from azericard_stored_card where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from azericard_stored_card").update();
  }

  private static AzericardStoredCard cardOf(ResultSet rs, int row) throws SQLException {
    return new AzericardStoredCard(
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        rs.getString("token"),
        rs.getString("terminal_id"),
        rs.getString("merchant_id"),
        rs.getString("stored_by_int_ref"),
        rs.getString("order_id"),
        rs.getString("fixture_id"),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getString("ext_net_ref"));
  }
}
