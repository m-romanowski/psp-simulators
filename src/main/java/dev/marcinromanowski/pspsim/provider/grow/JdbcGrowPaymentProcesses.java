package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectCursor;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

final class JdbcGrowPaymentProcesses implements GrowPaymentProcesses {

  private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  JdbcGrowPaymentProcesses(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public boolean insertIfAbsent(GrowPaymentProcess process) {
    int inserted =
        jdbc.sql(
                """
                insert into grow_payment_process
                    (test_run_id, process_id, process_token, merchant_id, charge_type, charge_sum,
                     description, save_card_token, success_url, cancel_url, notify_url,
                     payer_full_name, payer_phone, payer_email, custom_fields,
                     hosted_link, native_status_code, native_status, normalized_status, outcome,
                     origin, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (test_run_id, process_id) do nothing
                """)
            .param(process.testRunId().value())
            .param(process.processId())
            .param(process.processToken())
            .param(process.merchantId())
            .param(process.chargeType())
            .param(process.chargeSum())
            .param(process.description())
            .param(process.saveCardToken())
            .param(process.successUrl())
            .param(process.cancelUrl())
            .param(process.notifyUrl())
            .param(process.payerFullName())
            .param(process.payerPhone())
            .param(process.payerEmail())
            .param(write(process.customFields()))
            .param(process.hostedLink())
            .param(process.nativeStatusCode())
            .param(process.nativeStatus())
            .param(process.normalizedStatus().name())
            .param(writeOutcome(process.outcome()))
            .param(process.origin().name())
            .param(process.createdAt().atOffset(ZoneOffset.UTC))
            .param(process.updatedAt().atOffset(ZoneOffset.UTC))
            .update();
    return inserted == 1;
  }

  @Override
  public boolean claimProcessId(TestRunId testRunId, String processId) {
    return jdbc.sql(
                """
                insert into grow_object_id_claim (test_run_id, object_id, kind)
                values (?, ?, ?)
                on conflict (test_run_id, object_id) do nothing
                """)
            .params(testRunId.value(), processId, GrowObjectId.PROCESS)
            .update()
        == 1;
  }

  @Override
  public Optional<GrowPaymentProcess> find(
      TestRunId testRunId, String processId, String processToken) {
    return jdbc.sql(
            """
            select * from grow_payment_process
            where test_run_id = ? and process_id = ? and process_token = ?
            """)
        .params(testRunId.value(), processId, processToken)
        .query(this::toProcess)
        .optional();
  }

  @Override
  public Optional<GrowPaymentProcess> findByProcessId(TestRunId testRunId, String processId) {
    return jdbc.sql("select * from grow_payment_process where test_run_id = ? and process_id = ?")
        .params(testRunId.value(), processId)
        .query(this::toProcess)
        .optional();
  }

  @Override
  public List<GrowPaymentProcess> allByProcessId(TestRunId testRunId, List<String> processIds) {
    return jdbc.sql(
            """
            select * from grow_payment_process
            where test_run_id = ? and process_id = any(?)
            order by arrival desc
            """)
        .params(testRunId.value(), processIds.toArray(String[]::new))
        .query(this::toProcess)
        .list();
  }

  @Override
  public Optional<GrowPaymentProcess> findByHostedLink(String hostedLink) {
    return jdbc.sql("select * from grow_payment_process where hosted_link = ?")
        .param(hostedLink)
        .query(this::toProcess)
        .optional();
  }

  @Override
  public Optional<GrowPaymentProcess> findByCardToken(
      TestRunId testRunId, String merchantId, String cardToken) {
    return jdbc.sql(
            """
            select * from grow_payment_process
            where test_run_id = ? and merchant_id = ?
              and outcome::jsonb ->> 'cardToken' = ?
            order by process_id
            limit 1
            """)
        .params(testRunId.value(), merchantId, cardToken)
        .query(this::toProcess)
        .optional();
  }

  @Override
  public List<GrowPaymentProcess> theProcessesThatSavedACard(
      TestRunId testRunId, String merchantId) {
    return jdbc.sql(
            """
            select * from grow_payment_process
            where test_run_id = ? and merchant_id = ?
              and outcome::jsonb ->> 'cardToken' is not null
            order by arrival desc
            """)
        .params(testRunId.value(), merchantId)
        .query(this::toProcess)
        .list();
  }

  @Override
  public Optional<GrowPaymentProcess> findByTransaction(
      TestRunId testRunId, String merchantId, String transactionId, String transactionToken) {
    return jdbc.sql(
            """
            select * from grow_payment_process
            where test_run_id = ? and merchant_id = ?
              and outcome::jsonb ->> 'transactionId' = ?
              and outcome::jsonb ->> 'transactionToken' = ?
            order by process_id
            limit 1
            """)
        .params(testRunId.value(), merchantId, transactionId, transactionToken)
        .query(this::toProcess)
        .optional();
  }

  @Override
  public Optional<ProviderObjectCursor> placeOf(TestRunId testRunId, String processId) {
    return jdbc.sql(
            "select arrival from grow_payment_process where test_run_id = ? and process_id = ?")
        .params(testRunId.value(), processId)
        .query(Long.class)
        .optional()
        .map(arrival -> new ProviderObjectCursor(arrival, processId));
  }

  @Override
  public List<ArrivalOf<GrowPaymentProcess>> newestFirst(
      TestRunId testRunId, @Nullable ProviderObjectCursor after, int limit) {
    return jdbc.sql(
            """
            select * from grow_payment_process where test_run_id = ? and arrival < ?
            order by arrival desc limit ?
            """)
        .params(testRunId.value(), ProviderObjectCursor.walkingBackFrom(after), limit)
        .query(this::toArrivedProcess)
        .list();
  }

  private ArrivalOf<GrowPaymentProcess> toArrivedProcess(ResultSet rs, int row)
      throws SQLException {
    return new ArrivalOf<>(rs.getLong("arrival"), toProcess(rs, row));
  }

  @Override
  public boolean reachOutcome(GrowPaymentProcess process) {
    return jdbc.sql(
                """
                update grow_payment_process
                set native_status_code = ?, native_status = ?, normalized_status = ?,
                    outcome = ?, updated_at = ?
                where test_run_id = ? and process_id = ? and outcome is null
                """)
            .param(process.nativeStatusCode())
            .param(process.nativeStatus())
            .param(process.normalizedStatus().name())
            .param(writeOutcome(process.outcome()))
            .param(process.updatedAt().atOffset(ZoneOffset.UTC))
            .param(process.testRunId().value())
            .param(process.processId())
            .update()
        == 1;
  }

  @Override
  public int clear(TestRunId testRunId) {
    jdbc.sql("delete from grow_object_id_claim where test_run_id = ? and kind = ?")
        .params(testRunId.value(), GrowObjectId.PROCESS)
        .update();
    return jdbc.sql("delete from grow_payment_process where test_run_id = ?")
        .param(testRunId.value())
        .update();
  }

  @Override
  public int clearAll() {
    jdbc.sql("delete from grow_object_id_claim where kind = ?")
        .param(GrowObjectId.PROCESS)
        .update();
    return jdbc.sql("delete from grow_payment_process").update();
  }

  private GrowPaymentProcess toProcess(ResultSet rs, int row) throws SQLException {
    return new GrowPaymentProcess(
        TestRunId.of(rs.getString("test_run_id")),
        rs.getString("process_id"),
        rs.getString("process_token"),
        rs.getString("merchant_id"),
        rs.getString("charge_type"),
        rs.getString("charge_sum"),
        rs.getString("description"),
        rs.getBoolean("save_card_token"),
        rs.getString("success_url"),
        rs.getString("cancel_url"),
        rs.getString("notify_url"),
        rs.getString("payer_full_name"),
        rs.getString("payer_phone"),
        rs.getString("payer_email"),
        read(rs.getString("custom_fields")),
        rs.getString("hosted_link"),
        rs.getString("native_status_code"),
        rs.getString("native_status"),
        NormalizedStatus.valueOf(rs.getString("normalized_status")),
        readOutcome(rs.getString("outcome")),
        ObjectOrigin.valueOf(rs.getString("origin")),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private String write(Map<String, String> customFields) {
    return json.writeValueAsString(customFields);
  }

  private Map<String, String> read(String value) {
    return json.readValue(value, STRING_MAP);
  }

  private @Nullable String writeOutcome(@Nullable GrowProcessOutcome outcome) {
    return outcome == null ? null : json.writeValueAsString(outcome);
  }

  private @Nullable GrowProcessOutcome readOutcome(@Nullable String value) {
    return value == null ? null : json.readValue(value, GrowProcessOutcome.class);
  }
}
