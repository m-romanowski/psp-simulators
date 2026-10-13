package dev.marcinromanowski.pspsim.kernel.audit.internal;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditPage;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.audit.TestRunActivity;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class JdbcAuditTrail implements AuditTrail, AuditReads {

  private static final TypeReference<Map<String, List<String>>> MULTI_VALUED =
      new TypeReference<>() {};

  private static final TypeReference<List<String>> STORED_SUBJECT = new TypeReference<>() {};

  private static final String TRAIL_OF_ONE_RUN =
      "select * from simulator_audit_record where provider = ? and test_run_id = ?";

  private static final String WHEN_EACH_RUN_WAS_LAST_TOUCHED =
      """
      select test_run_id, last_touched_at, audit_rows
      from (select test_run_id,
                   created_at as last_touched_at,
                   seq,
                   count(*) over (partition by test_run_id) as audit_rows,
                   row_number() over (partition by test_run_id order by seq desc) as newest
            from simulator_audit_record
            where test_run_id <> ?) trail
      where newest = 1
      order by seq desc
      """;

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public JdbcAuditTrail(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void record(AuditRecord record) {
    jdbc.sql(
            """
            insert into simulator_audit_record
                (audit_id, provider, account_id, test_run_id, resolution_rule, origin,
                 direction, outcome, method, path, http_status, provider_object_id,
                 refusal_subject, scenario_id,
                 attempt, created_at, sanitized_headers, sanitized_body,
                 delivered_fault, delivered_fault_placement, delivered_declared_length,
                 delivered_body_bytes, delivered_held_for_ms)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(record.auditId())
        .param(record.provider().value())
        .param(record.accountId())
        .param(record.testRunId().value())
        .param(nameOf(record.resolutionRule()))
        .param(record.origin().name())
        .param(record.direction().name())
        .param(nameOf(record.outcome()))
        .param(record.method())
        .param(record.path())
        .param(record.httpStatus())
        .param(record.providerObjectId())
        .param(writeSubject(record.refusal()))
        .param(record.scenarioId())
        .param(record.attempt())
        .param(record.createdAt().atOffset(ZoneOffset.UTC))
        .param(write(record.sanitizedHeaders()))
        .param(write(record.sanitizedBody()))
        .param(faultOf(record.delivered()))
        .param(placementOf(record.delivered()))
        .param(declaredLengthOf(record.delivered()))
        .param(writtenBytesOf(record.delivered()))
        .param(heldForOf(record.delivered()))
        .update();
  }

  @Override
  public List<AuditRecord> of(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql(TRAIL_OF_ONE_RUN + " order by seq")
        .params(provider.value(), testRunId.value())
        .query(this::toRecord)
        .list();
  }

  @Override
  public AuditPage pageOf(ProviderId provider, AuditQuery query) {
    StringBuilder sql = new StringBuilder(TRAIL_OF_ONE_RUN);
    List<Object> parameters = new ArrayList<>();
    parameters.add(provider.value());
    parameters.add(query.testRunId().value());

    String cursor = query.after();
    if (cursor != null) {
      Long seq = seqOf(provider, query.testRunId(), cursor);
      if (seq == null) {
        return new AuditPage.NoSuchCursor();
      }
      narrow(sql, parameters, " and seq > ?", seq);
    }
    narrow(sql, parameters, " and audit_id = ?", query.auditId());
    narrow(sql, parameters, " and account_id = ?", query.accountId());
    narrow(sql, parameters, " and origin = ?", nameOf(query.origin()));
    narrow(sql, parameters, " and direction = ?", nameOf(query.direction()));
    narrow(sql, parameters, " and upper(method) = ?", query.method());
    narrow(sql, parameters, " and http_status = ?", query.httpStatus());
    narrowToAnyOf(sql, parameters, query.providerObjectIds());
    narrow(sql, parameters, " and scenario_id = ?", query.scenarioId());
    narrow(sql, parameters, " and created_at >= ?", utcOf(query.from()));
    narrow(sql, parameters, " and created_at < ?", utcOf(query.until()));
    sql.append(" order by seq limit ?");
    parameters.add(query.limit());

    return new AuditPage.Listed(
        jdbc.sql(sql.toString()).params(parameters).query(this::toRecord).list());
  }

  @Override
  public List<AuditRecord> latestOf(
      ProviderId provider, TestRunId testRunId, @Nullable ObjectOrigin origin, int limit) {
    if (limit < 1 || limit > AuditQuery.LARGEST_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and " + AuditQuery.LARGEST_LIMIT);
    }
    StringBuilder sql = new StringBuilder(TRAIL_OF_ONE_RUN);
    List<Object> parameters = new ArrayList<>();
    parameters.add(provider.value());
    parameters.add(testRunId.value());
    narrow(sql, parameters, " and origin = ?", nameOf(origin));
    sql.append(" order by seq desc limit ?");
    parameters.add(limit);
    return jdbc.sql(sql.toString()).params(parameters).query(this::toRecord).list();
  }

  @Override
  public List<TestRunActivity> theRunsNewestTouchFirst() {
    return jdbc.sql(WHEN_EACH_RUN_WAS_LAST_TOUCHED)
        .param(TestRunId.UNKNOWN.value())
        .query(JdbcAuditTrail::toActivity)
        .list();
  }

  private static TestRunActivity toActivity(ResultSet rs, int row) throws SQLException {
    return new TestRunActivity(
        TestRunId.of(rs.getString("test_run_id")),
        rs.getObject("last_touched_at", OffsetDateTime.class).toInstant(),
        rs.getInt("audit_rows"));
  }

  private @Nullable Long seqOf(ProviderId provider, TestRunId testRunId, String auditId) {
    return jdbc.sql(
            """
            select seq from simulator_audit_record
            where provider = ? and test_run_id = ? and audit_id = ?
            """)
        .params(provider.value(), testRunId.value(), auditId)
        .query(Long.class)
        .optional()
        .orElse(null);
  }

  private static void narrowToAnyOf(
      StringBuilder sql, List<Object> parameters, List<String> providerObjectIds) {
    if (providerObjectIds.isEmpty()) {
      return;
    }
    sql.append(" and provider_object_id in (");
    for (int named = 0; named < providerObjectIds.size(); named++) {
      sql.append(named == 0 ? "?" : ", ?");
      parameters.add(providerObjectIds.get(named));
    }
    sql.append(')');
  }

  private static void narrow(
      StringBuilder sql, List<Object> parameters, String clause, @Nullable Object value) {
    if (value == null) {
      return;
    }
    sql.append(clause);
    parameters.add(value);
  }

  private static @Nullable OffsetDateTime utcOf(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  private static @Nullable String nameOf(@Nullable AuditDirection direction) {
    return direction == null ? null : direction.name();
  }

  private static @Nullable String nameOf(@Nullable ObjectOrigin origin) {
    return origin == null ? null : origin.name();
  }

  @Override
  public int clear(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql("delete from simulator_audit_record where provider = ? and test_run_id = ?")
        .params(provider.value(), testRunId.value())
        .update();
  }

  @Override
  public int clearAll(ProviderId provider) {
    return jdbc.sql("delete from simulator_audit_record where provider = ?")
        .param(provider.value())
        .update();
  }

  private AuditRecord toRecord(ResultSet rs, int row) throws SQLException {
    String outcome = rs.getString("outcome");
    Integer httpStatus = (Integer) rs.getObject("http_status");
    return new AuditRecord(
        rs.getString("audit_id"),
        TestRunId.storedNamespace(rs.getString("test_run_id")),
        resolutionRuleOf(rs.getString("resolution_rule")),
        ProviderId.of(rs.getString("provider")),
        rs.getString("account_id"),
        ObjectOrigin.valueOf(rs.getString("origin")),
        AuditDirection.valueOf(rs.getString("direction")),
        outcome == null ? null : ProviderOutcome.valueOf(outcome),
        rs.getString("method"),
        rs.getString("path"),
        httpStatus,
        rs.getString("provider_object_id"),
        readSubject(rs.getString("refusal_subject")),
        rs.getString("scenario_id"),
        rs.getInt("attempt"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        read(rs.getString("sanitized_headers")),
        read(rs.getString("sanitized_body")),
        deliveredOf(rs));
  }

  private static @Nullable String faultOf(@Nullable DeliveredResponse delivered) {
    AppliedFault fault = delivered == null ? null : delivered.transportFault();
    return fault == null ? null : fault.kind().name();
  }

  private static @Nullable String placementOf(@Nullable DeliveredResponse delivered) {
    AppliedFault fault = delivered == null ? null : delivered.transportFault();
    return fault == null ? null : fault.placement().name();
  }

  private static @Nullable Long declaredLengthOf(@Nullable DeliveredResponse delivered) {
    return delivered == null ? null : delivered.declaredContentLength();
  }

  private static @Nullable Long writtenBytesOf(@Nullable DeliveredResponse delivered) {
    return delivered == null ? null : delivered.writtenBodyBytes();
  }

  private static @Nullable Long heldForOf(@Nullable DeliveredResponse delivered) {
    return delivered == null ? null : delivered.heldFor().toMillis();
  }

  private static @Nullable DeliveredResponse deliveredOf(ResultSet rs) throws SQLException {
    Long declared = (Long) rs.getObject("delivered_declared_length");
    if (declared == null) {
      return null;
    }
    return new DeliveredResponse(
        storedFaultOf(rs),
        declared,
        rs.getLong("delivered_body_bytes"),
        Duration.ofMillis(rs.getLong("delivered_held_for_ms")));
  }

  private static @Nullable AppliedFault storedFaultOf(ResultSet rs) throws SQLException {
    String fault = rs.getString("delivered_fault");
    if (fault == null) {
      return null;
    }
    return new AppliedFault(
        TransportFaultKind.valueOf(fault),
        FaultPlacement.valueOf(rs.getString("delivered_fault_placement")));
  }

  private static @Nullable String nameOf(@Nullable ProviderOutcome outcome) {
    return outcome == null ? null : outcome.name();
  }

  private static @Nullable String nameOf(@Nullable TestRunResolutionRule rule) {
    return rule == null ? null : rule.name();
  }

  private static @Nullable TestRunResolutionRule resolutionRuleOf(@Nullable String rule) {
    return rule == null ? null : TestRunResolutionRule.valueOf(rule);
  }

  private @Nullable String writeSubject(@Nullable RefusalSubject refusal) {
    return refusal == null ? null : json.writeValueAsString(StoredRefusalSubject.of(refusal));
  }

  private @Nullable RefusalSubject readSubject(@Nullable String stored) {
    return stored == null
        ? null
        : StoredRefusalSubject.from(json.readValue(stored, STORED_SUBJECT));
  }

  private String write(Map<String, List<String>> values) {
    return json.writeValueAsString(values);
  }

  private Map<String, List<String>> read(String value) {
    return json.readValue(value, MULTI_VALUED);
  }
}
