package dev.marcinromanowski.pspsim.kernel.webhook;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.AfterCommit;
import dev.marcinromanowski.pspsim.kernel.transaction.InsideATransactionException;
import dev.marcinromanowski.pspsim.kernel.transaction.NoUnitOwnsThisTransactionException;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class WebhookAfterCommitIntegrationTest extends PostgresBackedTest {

  private static final ProviderId PROVIDER = ProviderId.of("after-commit-it");
  private static final TestRunId RUN = TestRunId.of("webhook-after-commit-it");
  private static final String TARGET = "https://merchant.example/after-commit";

  @Autowired private JdbcClient jdbc;
  @Autowired private UnitOfWork unitOfWork;
  @Autowired private AfterCommit afterCommit;
  @Autowired private WebhookDeliveries deliveries;
  @Autowired private AuditTrail audit;
  @Autowired private SimulatorClock clock;
  @Autowired private IdGenerator ids;
  @Autowired private DeliveryShaping shaping;
  @Autowired private PlatformTransactionManager transactionManager;

  private final List<String> posted = Collections.synchronizedList(new ArrayList<>());
  private final Queue<Integer> answers = new ConcurrentLinkedQueue<>();

  @BeforeEach
  @AfterEach
  void forgetTheRun() {
    jdbc.sql(
            """
            delete from simulator_webhook_delivery_attempt where delivery_id in
                (select delivery_id from simulator_webhook_delivery where test_run_id = ?)
            """)
        .param(RUN.value())
        .update();
    jdbc.sql("delete from simulator_webhook_delivery where test_run_id = ?")
        .param(RUN.value())
        .update();
    jdbc.sql("delete from simulator_audit_record where test_run_id = ?")
        .param(RUN.value())
        .update();
  }

  @Test
  void a_unit_that_schedules_a_due_delivery_and_then_rolls_back_sends_nothing_and_keeps_nothing() {
    WebhookDispatcher dispatcher = dispatcher();

    List<WebhookDelivery> scheduled =
        unitOfWork.keepingOnly(kept -> false, () -> dispatcher.schedule(aDueDelivery()));

    assertThat(scheduled).hasSize(1);
    assertThat(posted).isEmpty();
    assertThat(rowsOf("simulator_webhook_delivery")).isZero();
    assertThat(attemptRows()).isZero();
  }

  @Test
  void a_kept_unit_sends_the_due_delivery_once_it_has_committed() {
    WebhookDispatcher dispatcher = dispatcher();
    List<Integer> postsSeenInsideTheUnit = new ArrayList<>();

    List<WebhookDelivery> scheduled =
        unitOfWork.keepingOnly(
            kept -> true,
            () -> {
              List<WebhookDelivery> inside = dispatcher.schedule(aDueDelivery());
              postsSeenInsideTheUnit.add(posted.size());
              return inside;
            });

    assertThat(postsSeenInsideTheUnit).containsExactly(0);
    assertThat(posted).containsExactly(TARGET);
    assertThat(dispatcher.attemptsOf(scheduled.get(0).deliveryId())).hasSize(1);
    assertThat(dispatcher.find(PROVIDER, RUN, scheduled.get(0).deliveryId()).orElseThrow().state())
        .isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void outside_any_unit_the_due_delivery_is_sent_before_schedule_returns() {
    WebhookDispatcher dispatcher = dispatcher();

    List<WebhookDelivery> scheduled = dispatcher.schedule(aDueDelivery());
    List<String> postedWhenItReturned = List.copyOf(posted);

    assertThat(postedWhenItReturned).containsExactly(TARGET);
    assertThat(attemptRows()).isEqualTo(1);
    assertThat(dispatcher.find(PROVIDER, RUN, scheduled.get(0).deliveryId()).orElseThrow().state())
        .isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void a_raw_transaction_that_schedules_a_due_delivery_is_refused_and_sends_nothing() {
    WebhookDispatcher dispatcher = dispatcher();
    TransactionTemplate raw = new TransactionTemplate(transactionManager);

    assertThatThrownBy(() -> raw.execute(status -> dispatcher.schedule(aDueDelivery())))
        .isInstanceOf(NoUnitOwnsThisTransactionException.class);

    assertThat(posted).isEmpty();
    assertThat(rowsOf("simulator_webhook_delivery")).isZero();
    assertThat(attemptRows()).isZero();
  }

  @Test
  void a_unit_opened_inside_a_raw_transaction_that_schedules_a_due_delivery_is_refused() {
    WebhookDispatcher dispatcher = dispatcher();
    TransactionTemplate raw = new TransactionTemplate(transactionManager);

    assertThatThrownBy(
            () ->
                raw.execute(
                    status ->
                        unitOfWork.keepingOnly(
                            kept -> true, () -> dispatcher.schedule(aDueDelivery()))))
        .isInstanceOf(NoUnitOwnsThisTransactionException.class);

    assertThat(posted).isEmpty();
    assertThat(rowsOf("simulator_webhook_delivery")).isZero();
  }

  @Test
  void a_retry_inside_a_unit_is_refused_and_sends_nothing() {
    WebhookDispatcher dispatcher = dispatcher();
    String deliveryId = dispatcher.schedule(aDueDelivery()).get(0).deliveryId();

    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    kept -> true, () -> dispatcher.retry(PROVIDER, RUN, deliveryId)))
        .isInstanceOf(InsideATransactionException.class)
        .hasMessage(
            InsideATransactionException.WHAT_REPORTS_WHAT_IT_DID_RUNS_OUTSIDE_ANY_TRANSACTION);

    assertThat(posted).hasSize(1);
    assertThat(dispatcher.attemptsOf(deliveryId)).hasSize(1);
  }

  @Test
  void delivering_what_is_due_inside_a_unit_is_refused_and_sends_nothing() {
    answers.add(500);
    WebhookDispatcher dispatcher = dispatcher();
    String deliveryId = dispatcher.schedule(aDueDelivery()).get(0).deliveryId();

    assertThatThrownBy(
            () -> unitOfWork.keepingOnly(kept -> true, () -> dispatcher.deliverDue(PROVIDER, RUN)))
        .isInstanceOf(InsideATransactionException.class);
    assertThatThrownBy(() -> unitOfWork.keepingOnly(kept -> true, dispatcher::deliverDueEverywhere))
        .isInstanceOf(InsideATransactionException.class);

    assertThat(posted).hasSize(1);
    assertThat(dispatcher.attemptsOf(deliveryId)).hasSize(1);
  }

  @Test
  void delivering_what_is_due_inside_a_raw_transaction_is_refused_and_sends_nothing() {
    answers.add(500);
    WebhookDispatcher dispatcher = dispatcher();
    String deliveryId = dispatcher.schedule(aDueDelivery()).get(0).deliveryId();
    assertThat(dispatcher.find(PROVIDER, RUN, deliveryId).orElseThrow().state())
        .isEqualTo(DeliveryState.PENDING);
    TransactionTemplate raw = new TransactionTemplate(transactionManager);

    assertThatThrownBy(() -> raw.execute(status -> dispatcher.deliverDue(PROVIDER, RUN)))
        .isInstanceOf(InsideATransactionException.class);

    assertThat(posted).hasSize(1);
    assertThat(dispatcher.attemptsOf(deliveryId)).hasSize(1);
  }

  private WebhookDispatcher dispatcher() {
    return new WebhookDispatcher(
        deliveries,
        (url, payload) -> {
          posted.add(url);
          Integer answer = answers.poll();
          return new MerchantResponse.Answered(answer == null ? 200 : answer, "");
        },
        audit,
        clock,
        ids,
        new RetryPlan(List.of(Duration.ZERO, Duration.ZERO)),
        shaping,
        List.of(new AcknowledgingA200()),
        List.of(),
        afterCommit);
  }

  private static WebhookDeliveryRequest aDueDelivery() {
    return new WebhookDeliveryRequest(
        PROVIDER,
        RUN,
        "merchant-1",
        "payment.settled",
        "object-1",
        TARGET,
        new WebhookPayload("text/plain", Map.of(), "settled".getBytes(UTF_8), Map.of()),
        null,
        null,
        null);
  }

  private long rowsOf(String table) {
    return jdbc.sql("select count(*) from " + table + " where test_run_id = ?")
        .param(RUN.value())
        .query(Long.class)
        .single();
  }

  private long attemptRows() {
    return jdbc.sql(
            """
            select count(*) from simulator_webhook_delivery_attempt where delivery_id in
                (select delivery_id from simulator_webhook_delivery where test_run_id = ?)
            """)
        .param(RUN.value())
        .query(Long.class)
        .single();
  }

  private static final class AcknowledgingA200 implements AcknowledgementPolicy {

    @Override
    public ProviderId provider() {
      return PROVIDER;
    }

    @Override
    public Acknowledgement judge(WebhookDelivery delivery, MerchantResponse response) {
      return response instanceof MerchantResponse.Answered answered && answered.httpStatus() == 200
          ? Acknowledgement.ACKNOWLEDGED
          : Acknowledgement.REFUSED;
    }
  }
}
