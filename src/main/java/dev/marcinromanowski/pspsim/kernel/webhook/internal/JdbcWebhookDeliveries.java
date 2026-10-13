package dev.marcinromanowski.pspsim.kernel.webhook.internal;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.PendingDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.TransportFailure;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookAttempt;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public final class JdbcWebhookDeliveries implements WebhookDeliveries {

  private static final TypeReference<Map<String, List<String>>> MULTI_VALUED =
      new TypeReference<>() {};
  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

  private final JdbcClient jdbc;
  private final ObjectMapper json;

  public JdbcWebhookDeliveries(JdbcClient jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void add(WebhookDelivery delivery) {
    jdbc.sql(
            """
            insert into simulator_webhook_delivery
                (delivery_id, provider, test_run_id, merchant_id, event_id, event_type,
                 provider_object_id, target_url, content_type, headers, body, sanitized_body,
                 retry_delays, state, acknowledgement_owed, attempts_made, next_attempt_at,
                 created_at, updated_at, shaped_by, not_sent_because)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(delivery.deliveryId())
        .param(delivery.provider().value())
        .param(delivery.testRunId().value())
        .param(delivery.merchantId())
        .param(delivery.eventId())
        .param(delivery.eventType())
        .param(delivery.providerObjectId())
        .param(delivery.targetUrl())
        .param(delivery.payload().contentType())
        .param(json.writeValueAsString(delivery.payload().providerHeaders()))
        .param(delivery.payload().body())
        .param(json.writeValueAsString(delivery.payload().sanitizedBody()))
        .param(json.writeValueAsString(isoDelaysOf(delivery.retryPlan())))
        .param(delivery.state().name())
        .param(delivery.acknowledgementOwed())
        .param(delivery.attemptsMade())
        .param(offset(delivery.nextAttemptAt()))
        .param(offset(delivery.createdAt()))
        .param(offset(delivery.updatedAt()))
        .param(delivery.shapedBy())
        .param(delivery.notSentBecause())
        .update();
  }

  @Override
  public Optional<WebhookDelivery> find(
      ProviderId provider, TestRunId testRunId, String deliveryId) {
    return jdbc.sql(
            """
            select * from simulator_webhook_delivery
            where provider = ? and test_run_id = ? and delivery_id = ?
            """)
        .params(provider.value(), testRunId.value(), deliveryId)
        .query(this::toDelivery)
        .optional();
  }

  @Override
  public List<WebhookDelivery> of(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql(
            """
            select * from simulator_webhook_delivery
            where provider = ? and test_run_id = ?
            order by seq desc
            """)
        .params(provider.value(), testRunId.value())
        .query(this::toDelivery)
        .list();
  }

  @Override
  public List<PendingDelivery> pending(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql(
            """
            select provider, test_run_id, delivery_id, next_attempt_at
            from simulator_webhook_delivery
            where provider = ? and test_run_id = ?
              and state = 'PENDING' and next_attempt_at is not null
            order by next_attempt_at, seq
            """)
        .params(provider.value(), testRunId.value())
        .query(JdbcWebhookDeliveries::toPending)
        .list();
  }

  @Override
  public List<PendingDelivery> pendingEverywhere() {
    return jdbc.sql(
            """
            select provider, test_run_id, delivery_id, next_attempt_at
            from simulator_webhook_delivery
            where state = 'PENDING' and next_attempt_at is not null
            order by next_attempt_at, seq
            """)
        .query(JdbcWebhookDeliveries::toPending)
        .list();
  }

  @Override
  public boolean claim(WebhookDelivery settled, int expectedAttemptsMade) {
    return took(
        """
        update simulator_webhook_delivery
        set state = ?, acknowledgement_owed = ?, attempts_made = ?, next_attempt_at = ?,
            updated_at = ?, not_sent_because = ?
        where delivery_id = ? and attempts_made = ? and state = 'PENDING'
        """,
        settled,
        expectedAttemptsMade);
  }

  @Override
  public boolean reclaim(WebhookDelivery settled, int expectedAttemptsMade) {
    return took(
        """
        update simulator_webhook_delivery
        set state = ?, acknowledgement_owed = ?, attempts_made = ?, next_attempt_at = ?,
            updated_at = ?, not_sent_because = ?
        where delivery_id = ? and attempts_made = ?
        """,
        settled,
        expectedAttemptsMade);
  }

  private boolean took(String sql, WebhookDelivery settled, int expectedAttemptsMade) {
    return jdbc.sql(sql)
            .param(settled.state().name())
            .param(settled.acknowledgementOwed())
            .param(settled.attemptsMade())
            .param(offset(settled.nextAttemptAt()))
            .param(offset(settled.updatedAt()))
            .param(settled.notSentBecause())
            .param(settled.deliveryId())
            .param(expectedAttemptsMade)
            .update()
        == 1;
  }

  @Override
  public void settle(WebhookDelivery delivery) {
    jdbc.sql(
            """
            update simulator_webhook_delivery
            set state = ?, acknowledgement_owed = ?, attempts_made = ?, next_attempt_at = ?,
                updated_at = ?, not_sent_because = ?
            where delivery_id = ? and attempts_made = ? and state <> 'DELIVERED'
            """)
        .param(delivery.state().name())
        .param(delivery.acknowledgementOwed())
        .param(delivery.attemptsMade())
        .param(offset(delivery.nextAttemptAt()))
        .param(offset(delivery.updatedAt()))
        .param(delivery.notSentBecause())
        .param(delivery.deliveryId())
        .param(delivery.attemptsMade())
        .update();
  }

  @Override
  public List<String> owedAnAcknowledgement(
      ProviderId provider, TestRunId testRunId, List<String> answersTo) {
    if (answersTo.isEmpty()) {
      return List.of();
    }
    StringBuilder sql =
        new StringBuilder(
            """
            select delivery_id from simulator_webhook_delivery
            where provider = ? and test_run_id = ? and acknowledgement_owed
              and provider_object_id in (""");
    List<Object> parameters = new ArrayList<>();
    parameters.add(provider.value());
    parameters.add(testRunId.value());
    for (int member = 0; member < answersTo.size(); member++) {
      sql.append(member == 0 ? "?" : ", ?");
      parameters.add(answersTo.get(member));
    }
    sql.append(") order by seq");
    return jdbc.sql(sql.toString())
        .params(parameters)
        .query((rs, row) -> rs.getString("delivery_id"))
        .list();
  }

  @Override
  public boolean acknowledge(String deliveryId, Instant acknowledgedAt) {
    return jdbc.sql(
                """
                update simulator_webhook_delivery
                set state = 'DELIVERED', acknowledgement_owed = false, next_attempt_at = null,
                    updated_at = ?
                where delivery_id = ? and state <> 'DELIVERED'
                """)
            .param(offset(acknowledgedAt))
            .param(deliveryId)
            .update()
        == 1;
  }

  @Override
  public void recordAttempt(WebhookAttempt attempt) {
    jdbc.sql(
            """
            insert into simulator_webhook_delivery_attempt
                (delivery_id, attempt_number, attempted_at, target_url, sent_headers, verdict,
                 http_status, transport_failure, response_preview)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .param(attempt.deliveryId())
        .param(attempt.number())
        .param(offset(attempt.attemptedAt()))
        .param(attempt.targetUrl())
        .param(StoredAttemptHeaders.storedForm(attempt.sentHeaders(), json))
        .param(attempt.verdict().name())
        .param(attempt.httpStatus())
        .param(nameOf(attempt.failure()))
        .param(attempt.responsePreview())
        .update();
  }

  @Override
  public List<WebhookAttempt> attemptsOf(String deliveryId) {
    return jdbc.sql(
            """
            select * from simulator_webhook_delivery_attempt
            where delivery_id = ?
            order by attempt_number
            """)
        .param(deliveryId)
        .query(this::toAttempt)
        .list();
  }

  @Override
  public int clear(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql("delete from simulator_webhook_delivery where provider = ? and test_run_id = ?")
        .params(provider.value(), testRunId.value())
        .update();
  }

  @Override
  public int clearAll(ProviderId provider) {
    return jdbc.sql("delete from simulator_webhook_delivery where provider = ?")
        .param(provider.value())
        .update();
  }

  private WebhookDelivery toDelivery(ResultSet rs, int row) throws SQLException {
    return new WebhookDelivery(
        rs.getString("delivery_id"),
        ProviderId.of(rs.getString("provider")),
        TestRunId.of(rs.getString("test_run_id")),
        rs.getString("merchant_id"),
        rs.getString("event_id"),
        rs.getString("event_type"),
        rs.getString("provider_object_id"),
        rs.getString("target_url"),
        new WebhookPayload(
            rs.getString("content_type"),
            json.readValue(rs.getString("headers"), MULTI_VALUED),
            rs.getBytes("body"),
            json.readValue(rs.getString("sanitized_body"), MULTI_VALUED)),
        planOf(json.readValue(rs.getString("retry_delays"), STRINGS)),
        DeliveryState.valueOf(rs.getString("state")),
        rs.getBoolean("acknowledgement_owed"),
        rs.getInt("attempts_made"),
        instant(rs.getObject("next_attempt_at", OffsetDateTime.class)),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
        rs.getString("shaped_by"),
        rs.getString("not_sent_because"));
  }

  private static PendingDelivery toPending(ResultSet rs, int row) throws SQLException {
    return new PendingDelivery(
        ProviderId.of(rs.getString("provider")),
        TestRunId.of(rs.getString("test_run_id")),
        rs.getString("delivery_id"),
        rs.getObject("next_attempt_at", OffsetDateTime.class).toInstant());
  }

  private WebhookAttempt toAttempt(ResultSet rs, int row) throws SQLException {
    String failure = rs.getString("transport_failure");
    Integer httpStatus = (Integer) rs.getObject("http_status");
    return new WebhookAttempt(
        rs.getString("delivery_id"),
        rs.getInt("attempt_number"),
        rs.getObject("attempted_at", OffsetDateTime.class).toInstant(),
        rs.getString("target_url"),
        StoredAttemptHeaders.theWireOf(rs.getString("sent_headers"), json),
        Acknowledgement.valueOf(rs.getString("verdict")),
        httpStatus,
        failure == null ? null : TransportFailure.valueOf(failure),
        rs.getString("response_preview"));
  }

  private static List<String> isoDelaysOf(RetryPlan plan) {
    List<String> iso = new ArrayList<>();
    for (Duration delay : plan.delays()) {
      iso.add(delay.toString());
    }
    return iso;
  }

  private static RetryPlan planOf(List<String> iso) {
    List<Duration> delays = new ArrayList<>();
    for (String delay : iso) {
      delays.add(Duration.parse(delay));
    }
    return new RetryPlan(delays);
  }

  private static @Nullable String nameOf(@Nullable TransportFailure failure) {
    return failure == null ? null : failure.name();
  }

  private static @Nullable OffsetDateTime offset(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }
}
