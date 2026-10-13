package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

final class JdbcAzericardPayerSessions implements AzericardPayerSessions {

  private static final TypeReference<Map<String, List<String>>> FIELDS = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  JdbcAzericardPayerSessions(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public boolean open(AzericardPayerSession session) {
    return jdbc.sql(
            """
                insert into azericard_payer_session
                    (session_id, test_run_id, route, terminal_id, merchant_id, form, headers,
                     outcome, int_ref, origin, created_at, settled_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                returning true
                """)
        .param(session.sessionId())
        .param(session.testRunId().value())
        .param(session.route().name())
        .param(session.terminalId())
        .param(session.merchantId())
        .param(json.writeValueAsString(session.form()))
        .param(json.writeValueAsString(session.headers()))
        .param(nameOf(session.outcome()))
        .param(session.intRef())
        .param(session.origin().name())
        .param(session.createdAt().atOffset(ZoneOffset.UTC))
        .param(atOf(session.settledAt()))
        .query(Boolean.class)
        .optional()
        .isPresent();
  }

  @Override
  public Optional<AzericardPayerSession> find(String sessionId) {
    return jdbc.sql("select * from azericard_payer_session where session_id = ?")
        .param(sessionId)
        .query(this::sessionOf)
        .optional();
  }

  @Override
  public Optional<AzericardPayerSession> find(TestRunId testRunId, String sessionId) {
    return jdbc.sql(
            "select * from azericard_payer_session where test_run_id = ? and session_id = ?")
        .params(testRunId.value(), sessionId)
        .query(this::sessionOf)
        .optional();
  }

  @Override
  public List<AzericardPayerSession> allById(TestRunId testRunId, List<String> sessionIds) {
    return jdbc.sql(
            """
            select * from azericard_payer_session
            where test_run_id = ? and session_id = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), sessionIds.toArray(String[]::new))
        .query(this::sessionOf)
        .list();
  }

  @Override
  public Optional<AzericardPayerSession> lockedWhileOpen(TestRunId testRunId, String sessionId) {
    return jdbc.sql(
            """
            select * from azericard_payer_session
            where test_run_id = ? and session_id = ? and outcome is null
            for update skip locked
            """)
        .params(testRunId.value(), sessionId)
        .query(this::sessionOf)
        .optional();
  }

  @Override
  public void settle(
      TestRunId testRunId,
      String sessionId,
      PayerOutcome outcome,
      @Nullable String intRef,
      Instant at) {
    jdbc.sql(
            """
            update azericard_payer_session set outcome = ?, int_ref = ?, settled_at = ?
            where test_run_id = ? and session_id = ? and outcome is null
            """)
        .param(outcome.name())
        .param(intRef)
        .param(at.atOffset(ZoneOffset.UTC))
        .param(testRunId.value())
        .param(sessionId)
        .update();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String sessionId) {
    return jdbc.sql(
            "select arrival from azericard_payer_session where test_run_id = ? and session_id = ?")
        .params(testRunId.value(), sessionId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, sessionId));
  }

  @Override
  public List<ArrivalOf<AzericardPayerSession>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from azericard_payer_session where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(
            (rs, row) ->
                new ArrivalOf<AzericardPayerSession>(rs.getLong("arrival"), sessionOf(rs, row)))
        .list();
  }

  @Override
  public int clear(TestRunId testRunId) {
    return jdbc.sql("delete from azericard_payer_session where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    return jdbc.sql("delete from azericard_payer_session").update();
  }

  private AzericardPayerSession sessionOf(ResultSet rs, int row) throws SQLException {
    return new AzericardPayerSession(
        rs.getString("session_id"),
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        AzericardRoute.valueOf(rs.getString("route")),
        rs.getString("terminal_id"),
        rs.getString("merchant_id"),
        json.readValue(rs.getString("form"), FIELDS),
        json.readValue(rs.getString("headers"), FIELDS),
        Optional.ofNullable(rs.getString("outcome")).map(PayerOutcome::valueOf).orElse(null),
        rs.getString("int_ref"),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        Optional.ofNullable(rs.getObject("settled_at", OffsetDateTime.class))
            .map(OffsetDateTime::toInstant)
            .orElse(null));
  }

  private static @Nullable String nameOf(@Nullable PayerOutcome outcome) {
    return Optional.ofNullable(outcome).map(PayerOutcome::name).orElse(null);
  }

  private static @Nullable OffsetDateTime atOf(@Nullable Instant at) {
    return Optional.ofNullable(at).map(when -> when.atOffset(ZoneOffset.UTC)).orElse(null);
  }
}
