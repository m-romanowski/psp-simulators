package dev.marcinromanowski.pspsim.kernel.webhook;

import static dev.marcinromanowski.pspsim.TheSpellingsOfAHeaderName.aHeaderOfThisNameUnderAnySpelling;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.DeliveryShaping;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.OutsideAnyUnitOfWork;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer.Answer;
import dev.marcinromanowski.pspsim.kernel.webhook.internal.HttpMerchantEndpoint;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebhookDeliveryTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");
  private static final TestRunId RUN = TestRunId.of("e2e-run-123");
  private static final Instant START = Instant.parse("2026-09-07T12:00:00Z");

  private static final String OBJECT = "734754";

  private static final RetryPlan DOCUMENTED_PLAN =
      new RetryPlan(
          List.of(
              Duration.ZERO,
              Duration.ofSeconds(1),
              Duration.ofSeconds(5),
              Duration.ofSeconds(30),
              Duration.ofMinutes(2)));

  private final LocalMerchantServer merchant = new LocalMerchantServer();
  private final InMemoryWebhookDeliveries deliveries = new InMemoryWebhookDeliveries();
  private final RecordingAudit audit = new RecordingAudit();
  private final MovableClock clock = new MovableClock(START);
  private final CountingIds ids = new CountingIds();

  @AfterEach
  void stopTheMerchant() {
    merchant.close();
  }

  @Test
  void a_delivery_the_queue_has_not_reached_the_moment_of_is_not_due_yet() {
    PendingDelivery waiting = new PendingDelivery(PROVIDER, RUN, "whk_1", START.plusSeconds(30));

    assertThat(waiting.isDueAt(START))
        .as(
            "this is the moment the queue picks candidates by before it loads any of them, so"
                + " one reported due before its own moment is loaded and dropped by the guard"
                + " beside it, and one reported not due is never loaded and never attempted")
        .isFalse();
    assertThat(waiting.isDueAt(START.plusSeconds(29))).isFalse();
    assertThat(waiting.isDueAt(START.plusSeconds(30)))
        .as(
            "the moment itself is due, because the plan names when it may be attempted and not"
                + " when it may not")
        .isTrue();
    assertThat(waiting.isDueAt(START.plusSeconds(31))).isTrue();
  }

  @Test
  void posts_the_bytes_the_provider_rendered_with_the_content_type_it_chose() {
    schedule(dispatcher(), request());

    LocalMerchantServer.ReceivedCallback received = merchant.received().get(0);
    assertThat(received.method()).isEqualTo("POST");
    assertThat(received.contentType()).isEqualTo("application/x-www-form-urlencoded");
    assertThat(received.body()).isEqualTo("status=1&data%5BprocessId%5D=734754");
  }

  @Test
  void a_delivery_recorded_without_a_target_is_undeliverable_from_birth_and_never_due() {
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery recorded =
        dispatcher.recordUnaddressable(
            new UnaddressableDelivery(
                PROVIDER, RUN, "merchant-1", null, OBJECT, payload(), "no target is configured"));
    dispatcher.deliverDue(PROVIDER, RUN);

    assertThat(merchant.received()).isEmpty();
    assertThat(dispatcher.find(PROVIDER, RUN, recorded.deliveryId()))
        .hasValueSatisfying(
            held -> {
              assertThat(held.state()).isEqualTo(DeliveryState.UNDELIVERABLE);
              assertThat(held.targetUrl()).isNull();
              assertThat(held.notSentBecause()).isEqualTo("no target is configured");
              assertThat(held.attemptsMade()).isZero();
              assertThat(held.nextAttemptAt()).isNull();
            });
    assertThat(dispatcher.attemptsOf(recorded.deliveryId())).isEmpty();
  }

  @Test
  void a_delivery_recorded_without_a_target_refuses_a_retry_with_or_without_a_target_named() {
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery recorded =
        dispatcher.recordUnaddressable(
            new UnaddressableDelivery(
                PROVIDER, RUN, "merchant-1", null, OBJECT, payload(), "no target is configured"));

    assertThatThrownBy(() -> dispatcher.retry(PROVIDER, RUN, recorded.deliveryId()))
        .isInstanceOf(DeliveryHasNoTargetException.class)
        .hasMessage(DeliveryHasNoTargetException.IT_CANNOT_BE_ATTEMPTED);
    assertThatThrownBy(() -> dispatcher.retry(PROVIDER, RUN, recorded.deliveryId(), merchant.url()))
        .isInstanceOf(DeliveryHasNoTargetException.class);
    assertThat(merchant.received()).isEmpty();
    assertThat(dispatcher.find(PROVIDER, RUN, recorded.deliveryId()))
        .hasValueSatisfying(
            held -> assertThat(held.state()).isEqualTo(DeliveryState.UNDELIVERABLE));
  }

  @Test
  void a_delivery_recorded_without_a_target_is_on_the_trail_as_a_callback_not_sent() {
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery recorded =
        dispatcher.recordUnaddressable(
            new UnaddressableDelivery(
                PROVIDER, RUN, "merchant-1", null, OBJECT, payload(), "no target is configured"));

    assertThat(audit.of(PROVIDER, RUN))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.direction()).isEqualTo(AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT);
              assertThat(row.origin()).isEqualTo(ObjectOrigin.SCHEDULED_JOB);
              assertThat(row.outcome()).isEqualTo(ProviderOutcome.FAILURE);
              assertThat(row.refusal()).isNull();
              assertThat(row.path()).isNull();
              assertThat(row.providerObjectId()).isEqualTo(recorded.providerObjectId());
            });
  }

  @Test
  void only_an_undeliverable_delivery_may_have_no_target() {
    WebhookDelivery recorded =
        dispatcher()
            .recordUnaddressable(
                new UnaddressableDelivery(
                    PROVIDER, RUN, "merchant-1", null, OBJECT, payload(), "none"));

    for (DeliveryState state : DeliveryState.values()) {
      if (state == DeliveryState.UNDELIVERABLE) {
        continue;
      }
      assertThatThrownBy(() -> inState(recorded, state))
          .isInstanceOf(TargetlessDeliveryIsTerminalException.class)
          .hasMessage(
              TargetlessDeliveryIsTerminalException.ONLY_AN_UNDELIVERABLE_DELIVERY_HAS_NO_TARGET);
    }
    assertThat(recorded.canBeAttempted()).isFalse();
    assertThatThrownBy(() -> recorded.theTargetOfAnAttempt("https://elsewhere.example/"))
        .isInstanceOf(DeliveryHasNoTargetException.class);
  }

  @Test
  void a_delivery_with_a_target_is_attempted_at_it_unless_an_attempt_names_another() {
    WebhookDelivery scheduled = schedule(dispatcherWith(new TwoHundredAcknowledges()), request());

    assertThat(scheduled.canBeAttempted()).isTrue();
    assertThat(scheduled.theTargetOfAnAttempt(null)).isEqualTo(merchant.url());
    assertThat(scheduled.theTargetOfAnAttempt("https://elsewhere.example/"))
        .isEqualTo("https://elsewhere.example/");
  }

  @Test
  void a_rendering_is_handed_the_target_the_attempt_posts_to() {
    List<String> seen = new ArrayList<>();
    WebhookDispatcher dispatcher =
        dispatcherRendering(
            new AttemptRendering() {
              @Override
              public ProviderId provider() {
                return PROVIDER;
              }

              @Override
              public Rendering renderFor(
                  WebhookDelivery delivery, String targetUrl, Instant attemptedAt) {
                seen.add(targetUrl);
                return new Rendering.Sending(delivery.payload());
              }
            });
    WebhookDelivery scheduled = schedule(dispatcher, request());

    dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId(), "http://127.0.0.1:9/elsewhere");

    assertThat(seen).containsExactly(merchant.url(), "http://127.0.0.1:9/elsewhere");
  }

  private static WebhookDelivery inState(WebhookDelivery delivery, DeliveryState state) {
    return new WebhookDelivery(
        delivery.deliveryId(),
        delivery.provider(),
        delivery.testRunId(),
        delivery.merchantId(),
        delivery.eventId(),
        delivery.eventType(),
        delivery.providerObjectId(),
        delivery.targetUrl(),
        delivery.payload(),
        delivery.retryPlan(),
        state,
        delivery.acknowledgementOwed(),
        delivery.attemptsMade(),
        delivery.nextAttemptAt(),
        delivery.createdAt(),
        delivery.updatedAt(),
        delivery.shapedBy(),
        delivery.notSentBecause());
  }

  @Test
  void the_attempt_records_the_content_type_the_merchant_received_rather_than_no_header_at_all() {
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());

    LocalMerchantServer.ReceivedCallback received = merchant.received().get(0);
    WebhookAttempt attempt = dispatcher.attemptsOf(scheduled.deliveryId()).get(0);
    assertThat(attempt.theWireWasRecorded()).isTrue();
    assertThat(attempt.contentTypeSent())
        .isEqualTo("application/x-www-form-urlencoded")
        .isEqualTo(received.contentType());
    assertThat(attempt.sentHeaders()).isNotEmpty();
  }

  @Test
  void every_header_an_attempt_recorded_reached_the_merchant_under_that_name_and_that_value() {
    WebhookDispatcher dispatcher = dispatcherSigningEachAttemptWithItsOwnMoment();
    WebhookDelivery scheduled = schedule(dispatcher, request());

    LocalMerchantServer.ReceivedCallback received = merchant.received().get(0);
    Map<String, List<String>> recorded =
        dispatcher.attemptsOf(scheduled.deliveryId()).get(0).sentHeaders();
    assertThat(recorded).containsKeys("Content-Type", SIGNATURE_HEADER);
    assertThat(recorded)
        .allSatisfy((name, values) -> assertThat(received.header(name)).isEqualTo(values));
  }

  @Test
  void attempts_a_delivery_with_no_delay_the_moment_it_is_scheduled() {
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery scheduled = schedule(dispatcher, request());

    WebhookDelivery delivery = only(dispatcher);
    assertThat(delivery.deliveryId()).isEqualTo(scheduled.deliveryId());
    assertThat(delivery.state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(delivery.attemptsMade()).isEqualTo(1);
    assertThat(delivery.nextAttemptAt()).isNull();
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void a_delayed_delivery_waits_for_the_simulator_clock_rather_than_for_a_wall_clock() {
    WebhookDispatcher dispatcher = dispatcher();

    schedule(dispatcher, requestDelayedBy(Duration.ofMinutes(5)));

    assertThat(merchant.received()).isEmpty();
    assertThat(only(dispatcher).nextAttemptAt()).isEqualTo(START.plus(Duration.ofMinutes(5)));

    clock.moveTo(START.plus(Duration.ofMinutes(5)).minusMillis(1));
    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isZero();

    clock.moveTo(START.plus(Duration.ofMinutes(5)));
    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isEqualTo(1);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void follows_the_documented_retry_schedule_on_the_simulator_clock() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery delivery = schedule(dispatcher, request());
    for (Duration due :
        List.of(
            Duration.ofSeconds(1),
            Duration.ofSeconds(6),
            Duration.ofSeconds(36),
            Duration.ofSeconds(156))) {
      assertThat(only(dispatcher).nextAttemptAt()).isEqualTo(START.plus(due));
      clock.moveTo(START.plus(due));
      assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isEqualTo(1);
    }

    assertThat(dispatcher.attemptsOf(delivery.deliveryId()))
        .extracting(WebhookAttempt::number, WebhookAttempt::attemptedAt)
        .containsExactly(
            tuple(1, START),
            tuple(2, START.plusSeconds(1)),
            tuple(3, START.plusSeconds(6)),
            tuple(4, START.plusSeconds(36)),
            tuple(5, START.plusSeconds(156)));
    assertThat(merchant.received()).hasSize(5);
  }

  @Test
  void a_sequence_that_runs_out_of_attempts_ends_visibly_exhausted() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();

    schedule(dispatcher, request());
    runOutThePlan(dispatcher);

    WebhookDelivery delivery = only(dispatcher);
    assertThat(delivery.state()).isEqualTo(DeliveryState.EXHAUSTED);
    assertThat(delivery.attemptsMade()).isEqualTo(DOCUMENTED_PLAN.attemptCap());
    assertThat(delivery.nextAttemptAt()).isNull();
    assertThat(dispatcher.attemptsOf(delivery.deliveryId())).hasSize(5);
  }

  @Test
  void
      a_delivery_already_queued_runs_out_the_plan_it_left_on_when_a_harness_shortens_the_setting() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    SwappablePlans plans = new SwappablePlans(DOCUMENTED_PLAN);
    WebhookDispatcher dispatcher = dispatcherPlanning(plans);

    schedule(dispatcher, request());
    plans.nowAnswering(new RetryPlan(List.of(Duration.ZERO)));
    runOutThePlan(dispatcher);

    WebhookDelivery delivery = only(dispatcher);
    assertThat(delivery.attemptsMade()).isEqualTo(DOCUMENTED_PLAN.attemptCap());
    assertThat(delivery.state()).isEqualTo(DeliveryState.EXHAUSTED);
    assertThat(merchant.received()).hasSize(5);
  }

  @Test
  void a_delivery_scheduled_after_a_harness_shortens_the_setting_leaves_on_the_shorter_plan() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    SwappablePlans plans = new SwappablePlans(DOCUMENTED_PLAN);
    WebhookDispatcher dispatcher = dispatcherPlanning(plans);
    plans.nowAnswering(new RetryPlan(List.of(Duration.ZERO)));

    schedule(dispatcher, request());
    runOutThePlan(dispatcher);

    WebhookDelivery delivery = only(dispatcher);
    assertThat(delivery.attemptsMade()).isEqualTo(1);
    assertThat(delivery.state()).isEqualTo(DeliveryState.EXHAUSTED);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void makes_no_further_attempt_once_a_sequence_is_exhausted() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    schedule(dispatcher, request());
    runOutThePlan(dispatcher);

    clock.moveTo(START.plus(Duration.ofDays(1)));

    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isZero();
    assertThat(merchant.received()).hasSize(5);
  }

  @Test
  void an_exhausted_delivery_can_be_retried_and_the_ledger_keeps_the_whole_history() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());
    runOutThePlan(dispatcher);

    merchant.alwaysAnswering(new Answer.Respond(204, ""));
    clock.moveTo(START.plus(Duration.ofHours(1)));
    WebhookDelivery retried = dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId()).orElseThrow();

    assertThat(retried.state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .extracting(WebhookAttempt::number, WebhookAttempt::verdict)
        .containsExactly(
            tuple(1, Acknowledgement.REFUSED),
            tuple(2, Acknowledgement.REFUSED),
            tuple(3, Acknowledgement.REFUSED),
            tuple(4, Acknowledgement.REFUSED),
            tuple(5, Acknowledgement.REFUSED),
            tuple(6, Acknowledgement.ACKNOWLEDGED));
  }

  @Test
  void one_attempt_can_be_posted_to_another_target_while_the_delivery_keeps_its_own() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());
    try (LocalMerchantServer second = new LocalMerchantServer()) {
      second.alwaysAnswering(new Answer.Respond(204, ""));

      WebhookDelivery retried =
          dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId(), second.url()).orElseThrow();

      assertThat(second.received()).hasSize(1);
      assertThat(retried.state()).isEqualTo(DeliveryState.DELIVERED);
      assertThat(retried.targetUrl()).isEqualTo(merchant.url());
    }
  }

  @Test
  void the_ledger_names_the_target_each_attempt_went_to_rather_than_the_delivery_s() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());
    try (LocalMerchantServer second = new LocalMerchantServer()) {
      second.alwaysAnswering(new Answer.Respond(204, ""));
      dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId(), second.url());

      assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
          .extracting(WebhookAttempt::number, WebhookAttempt::targetUrl)
          .containsExactly(tuple(1, merchant.url()), tuple(2, second.url()));
    }
  }

  @Test
  void an_attempt_posted_elsewhere_is_audited_at_the_address_it_was_posted_to() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());
    try (LocalMerchantServer second = new LocalMerchantServer()) {
      second.alwaysAnswering(new Answer.Respond(204, ""));
      dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId(), second.url());

      assertThat(audit.records)
          .filteredOn(record -> record.direction() == AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT)
          .extracting(AuditRecord::attempt, AuditRecord::path)
          .containsExactly(tuple(1, merchant.url()), tuple(2, second.url()));
    }
  }

  @Test
  void a_retry_naming_no_target_is_posted_to_the_one_the_delivery_was_scheduled_for() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());

    dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId(), null);

    assertThat(merchant.received()).hasSize(2);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .extracting(WebhookAttempt::targetUrl)
        .containsExactly(merchant.url(), merchant.url());
  }

  @Test
  void each_attempt_records_the_signature_that_attempt_was_rendered_with_and_not_the_stored_one() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcherSigningEachAttemptWithItsOwnMoment();
    WebhookDelivery scheduled = schedule(dispatcher, request());
    clock.moveTo(START.plusSeconds(1));
    dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId());

    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .extracting(attempt -> attempt.sentHeaders().get(SIGNATURE_HEADER))
        .containsExactly(
            List.of("t=" + START.getEpochSecond()),
            List.of("t=" + START.plusSeconds(1).getEpochSecond()));
  }

  @Test
  void a_delivery_of_a_provider_that_renders_nothing_records_the_wire_its_payload_was_stored_for() {
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .singleElement()
        .satisfies(
            attempt ->
                assertThat(attempt.sentHeaders()).isEqualTo(scheduled.payload().wireHeaders()));
  }

  @Test
  void retrying_a_delivery_that_does_not_exist_answers_nothing_rather_than_failing() {
    assertThat(dispatcher().retry(PROVIDER, RUN, "whjob_nothing")).isEmpty();
  }

  @Test
  void a_second_drainer_makes_no_attempt_while_one_is_already_in_flight() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    AtomicInteger attemptedWhileInFlight = new AtomicInteger(-1);
    merchant.onceWhileHandling(
        () -> attemptedWhileInFlight.compareAndSet(-1, dispatcher.deliverDue(PROVIDER, RUN)));

    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThat(attemptedWhileInFlight).hasValue(0);
    assertThat(merchant.received()).hasSize(1);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId())).hasSize(1);
  }

  @Test
  void two_drainers_that_read_the_same_due_delivery_make_one_attempt_between_them()
      throws Exception {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, requestDelayedBy(Duration.ofSeconds(1)));
    clock.moveTo(START.plusSeconds(1));

    CyclicBarrier bothHoldTheSameDueDelivery = new CyclicBarrier(2);
    deliveries.afterEveryReadOfOne(() -> waitFor(bothHoldTheSameDueDelivery));
    ExecutorService drainers = Executors.newFixedThreadPool(2);
    int attempted;
    try {
      Future<Integer> poll = drainers.submit(() -> dispatcher.deliverDue(PROVIDER, RUN));
      Future<Integer> controlCall = drainers.submit(() -> dispatcher.deliverDue(PROVIDER, RUN));
      attempted = poll.get() + controlCall.get();
    } finally {
      drainers.shutdownNow();
    }

    assertThat(attempted).isEqualTo(1);
    assertThat(merchant.received()).hasSize(1);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId())).hasSize(1);
  }

  @Test
  void a_manual_retry_that_loses_the_race_makes_no_attempt_of_its_own() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());
    clock.moveTo(START.plusSeconds(1));
    deliveries.afterReadingOne(() -> new WebhookDeliveryPoll(dispatcher).deliverDue());

    dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId());

    assertThat(merchant.received()).hasSize(2);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .extracting(WebhookAttempt::number)
        .containsExactly(1, 2);
    assertThat(only(dispatcher).attemptsMade()).isEqualTo(2);
  }

  @Test
  void an_attempt_settling_after_a_later_one_was_claimed_does_not_take_the_count_back() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());
    clock.moveTo(START.plusSeconds(1));
    merchant.onceWhileHandling(() -> dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId()));

    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isEqualTo(1);

    assertThat(merchant.received()).hasSize(3);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .extracting(WebhookAttempt::number)
        .containsExactly(1, 2, 3);
    assertThat(only(dispatcher).attemptsMade()).isEqualTo(3);

    dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId());

    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .extracting(WebhookAttempt::number)
        .containsExactly(1, 2, 3, 4);
  }

  @Test
  void a_dropped_connection_is_recorded_as_a_transport_failure_and_retried() {
    merchant.alwaysAnswering(new Answer.DropTheConnection());
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery scheduled = schedule(dispatcher, request());

    WebhookAttempt attempt = dispatcher.attemptsOf(scheduled.deliveryId()).get(0);
    assertThat(attempt.failure()).isEqualTo(TransportFailure.CONNECTION_ERROR);
    assertThat(attempt.httpStatus()).isNull();
    assertThat(attempt.responsePreview()).isNull();
    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.PENDING);
    assertThat(only(dispatcher).nextAttemptAt()).isEqualTo(START.plusSeconds(1));
  }

  @Test
  void does_not_follow_a_redirect_the_merchant_answers_with() {
    merchant.alwaysAnswering(new Answer.Respond(302, ""));
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()).get(0).httpStatus()).isEqualTo(302);
    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.PENDING);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void keeps_only_the_configured_amount_of_the_merchant_response() {
    merchant.alwaysAnswering(new Answer.Respond(500, "x".repeat(5000)));
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()).get(0).responsePreview())
        .isEqualTo("x".repeat(64));
  }

  @Test
  void a_target_that_is_not_a_usable_url_is_a_connection_error_rather_than_a_crash() {
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery scheduled =
        schedule(
            dispatcher,
            new WebhookDeliveryRequest(
                PROVIDER, RUN, "merchant-1", null, null, "not a url", payload(), null, null, null));

    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()).get(0).failure())
        .isEqualTo(TransportFailure.CONNECTION_ERROR);
  }

  @Test
  void a_merchant_that_accepts_the_callback_and_never_answers_is_a_read_timeout() {
    merchant.alwaysAnswering(new Answer.NeverAnswer());
    WebhookDispatcher dispatcher =
        dispatcherWith(new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofMillis(250), 64));

    WebhookDelivery scheduled = schedule(dispatcher, request());

    WebhookAttempt attempt = dispatcher.attemptsOf(scheduled.deliveryId()).get(0);
    assertThat(attempt.failure()).isEqualTo(TransportFailure.READ_TIMEOUT);
    assertThat(attempt.httpStatus()).isNull();
    assertThat(merchant.received()).hasSize(1);
    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.PENDING);
  }

  @Test
  void a_merchant_whose_address_never_completes_a_connection_is_a_connect_timeout() {
    try (UnreachableMerchant unreachable = new UnreachableMerchant()) {
      WebhookDispatcher dispatcher =
          dispatcherWith(
              new HttpMerchantEndpoint(Duration.ofMillis(250), Duration.ofSeconds(5), 64));

      WebhookDelivery scheduled = schedule(dispatcher, requestTo(unreachable.url()));

      WebhookAttempt attempt = dispatcher.attemptsOf(scheduled.deliveryId()).get(0);
      assertThat(attempt.failure()).isEqualTo(TransportFailure.CONNECT_TIMEOUT);
      assertThat(attempt.httpStatus()).isNull();
      assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.PENDING);
    }
  }

  @Test
  void an_interrupted_attempt_is_a_transport_failure_and_hands_the_interrupt_back() {
    Thread delivering = Thread.currentThread();
    merchant.alwaysAnswering(new Answer.NeverAnswer()).onceWhileHandling(delivering::interrupt);
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThat(Thread.interrupted()).isTrue();
    WebhookAttempt attempt = dispatcher.attemptsOf(scheduled.deliveryId()).get(0);
    assertThat(attempt.failure()).isEqualTo(TransportFailure.CONNECTION_ERROR);
    assertThat(attempt.httpStatus()).isNull();
    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.PENDING);
  }

  @Test
  void keeps_a_single_byte_of_the_merchant_response_when_that_is_all_that_is_configured() {
    merchant.alwaysAnswering(new Answer.Respond(500, "refused"));
    WebhookDispatcher dispatcher =
        dispatcherWith(new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofSeconds(5), 1));

    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()).get(0).responsePreview())
        .isEqualTo("r");
  }

  @Test
  void reads_a_merchant_that_will_not_stop_talking_only_as_far_as_the_preview() {
    try (UnendingMerchant unending = new UnendingMerchant()) {
      WebhookDispatcher dispatcher =
          dispatcherWith(new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofSeconds(2), 8));

      WebhookDelivery scheduled = schedule(dispatcher, requestTo(unending.url()));

      WebhookAttempt attempt = dispatcher.attemptsOf(scheduled.deliveryId()).get(0);
      assertThat(attempt.httpStatus()).isEqualTo(200);
      assertThat(attempt.responsePreview()).isEqualTo("xxxxxxxx");
      assertThat(attempt.failure()).isNull();
      assertThat(unending.awaitAbandonedMidResponse())
          .as("read as far as the preview and stopped, but %s", unending.outcome())
          .isTrue();
    }
  }

  @Test
  void a_closed_transport_releases_its_client_and_reaches_no_merchant() {
    HttpMerchantEndpoint endpoint =
        new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofSeconds(2), 64);

    endpoint.close();

    assertThat(endpoint.post(merchant.url(), payload()))
        .isEqualTo(new MerchantResponse.NotAnswered(TransportFailure.CONNECTION_ERROR));
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void refuses_a_transport_configured_with_no_usable_timeout() {
    Duration oneSecond = Duration.ofSeconds(1);
    Duration negative = Duration.ofSeconds(-1);

    assertThatThrownBy(() -> new HttpMerchantEndpoint(Duration.ZERO, oneSecond, 64))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("simulator.webhook.connect-timeout");
    assertThatThrownBy(() -> new HttpMerchantEndpoint(oneSecond, negative, 64))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("simulator.webhook.read-timeout");
    assertThatThrownBy(() -> new HttpMerchantEndpoint(oneSecond, oneSecond, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("simulator.webhook.max-response-preview-bytes");
  }

  @Test
  void the_provider_decides_acknowledgement_and_the_engine_reads_no_status() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcherWith(policy(response -> Acknowledgement.ACKNOWLEDGED));

    schedule(dispatcher, request());

    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(only(dispatcher).attemptsMade()).isEqualTo(1);
  }

  @Test
  void a_provider_that_awaits_a_separate_call_leaves_the_delivery_unfinished_and_retried() {
    WebhookDispatcher dispatcher =
        dispatcherWith(policy(response -> Acknowledgement.AWAITING_SEPARATE_CALL));

    WebhookDelivery scheduled = schedule(dispatcher, request());

    WebhookAttempt attempt = dispatcher.attemptsOf(scheduled.deliveryId()).get(0);
    assertThat(attempt.verdict()).isEqualTo(Acknowledgement.AWAITING_SEPARATE_CALL);
    assertThat(attempt.httpStatus()).isEqualTo(200);
    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.PENDING);
    assertThat(only(dispatcher).nextAttemptAt()).isEqualTo(START.plusSeconds(1));
    assertThat(audit.of(PROVIDER, RUN))
        .singleElement()
        .satisfies(record -> assertThat(record.outcome()).isEqualTo(ProviderOutcome.PROCESSING));
  }

  @Test
  void refuses_to_deliver_for_a_provider_no_policy_answers_for() {
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDeliveryRequest ofAProviderNoPolicyAnswersFor =
        new WebhookDeliveryRequest(
            ProviderId.of("stripe"),
            RUN,
            "merchant-1",
            null,
            null,
            merchant.url(),
            payload(),
            null,
            null,
            null);

    assertThatThrownBy(() -> schedule(dispatcher, ofAProviderNoPolicyAnswersFor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stripe");
  }

  @Test
  void refuses_two_policies_claiming_one_provider() {
    MerchantEndpoint endpoint = endpoint();
    DeliveryShaping shaping = new DeliveryShaping(scenarioRules);
    List<AcknowledgementPolicy> twoPoliciesForOneProvider =
        List.of(
            policy(response -> Acknowledgement.ACKNOWLEDGED),
            policy(response -> Acknowledgement.REFUSED));
    List<AttemptRendering> noRenderings = List.of();

    assertThatThrownBy(
            () ->
                new WebhookDispatcher(
                    deliveries,
                    endpoint,
                    audit,
                    clock,
                    ids,
                    DOCUMENTED_PLAN,
                    shaping,
                    twoPoliciesForOneProvider,
                    noRenderings,
                    OutsideAnyUnitOfWork.RUNS_AT_ONCE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("grow");
  }

  @Test
  void audits_every_attempt_with_the_provider_verdict_beside_the_status_it_travelled_under() {
    merchant.thenAnswering(new Answer.Respond(500, "no thanks"));
    merchant.alwaysAnswering(new Answer.Respond(204, ""));
    WebhookDispatcher dispatcher = dispatcher();

    schedule(dispatcher, request());
    clock.moveTo(START.plusSeconds(1));
    dispatcher.deliverDue(PROVIDER, RUN);

    assertThat(audit.records())
        .extracting(
            AuditRecord::direction,
            AuditRecord::attempt,
            AuditRecord::httpStatus,
            AuditRecord::outcome)
        .containsExactly(
            tuple(AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT, 1, 500, ProviderOutcome.FAILURE),
            tuple(AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT, 2, 204, ProviderOutcome.SUCCESS));
  }

  @Test
  void an_outbound_attempt_carries_no_test_run_resolution_rule() {
    schedule(dispatcher(), request());

    AuditRecord record = audit.records().get(0);
    assertThat(record.resolutionRule()).isNull();
    assertThat(record.testRunId()).isEqualTo(RUN);
    assertThat(record.path()).isEqualTo(merchant.url());
    assertThat(record.providerObjectId()).isEqualTo("734754");
    assertThat(record.createdAt()).isEqualTo(START);
  }

  @Test
  void audits_the_sanitized_payload_and_never_the_bytes_the_merchant_received() {
    schedule(dispatcher(), request());

    AuditRecord record = audit.records().get(0);
    assertThat(record.sanitizedBody())
        .containsEntry("status", List.of("1"))
        .containsEntry("data[processId]", List.of("734754"))
        .containsEntry("data[processToken]", List.of("[redacted]"));
  }

  @Test
  void sends_the_headers_the_provider_asked_for_and_audits_exactly_those() {
    Map<String, List<String>> signed =
        Map.of("X-GCS-Signature", List.of("dGhlIHNpZ25hdHVyZQ=="), "X-GCS-KeyId", List.of("key-1"));
    WebhookDispatcher dispatcher = dispatcher();

    schedule(
        dispatcher,
        new WebhookDeliveryRequest(
            PROVIDER,
            RUN,
            "merchant-1",
            null,
            "734754",
            merchant.url(),
            payload(signed),
            null,
            null,
            null));

    LocalMerchantServer.ReceivedCallback received = merchant.received().get(0);
    assertThat(received.header("X-GCS-Signature")).containsExactly("dGhlIHNpZ25hdHVyZQ==");
    assertThat(received.header("X-GCS-KeyId")).containsExactly("key-1");
    assertThat(received.header("Content-Type"))
        .containsExactly("application/x-www-form-urlencoded");

    Map<String, List<String>> audited = audit.records().get(0).sanitizedHeaders();
    assertThat(audited)
        .containsOnlyKeys("Content-Type", "X-GCS-Signature", "X-GCS-KeyId")
        .allSatisfy((name, values) -> assertThat(received.header(name)).isEqualTo(values));

    WebhookPayload queued = only(dispatcher).payload();
    assertThat(queued.contentType()).isEqualTo("application/x-www-form-urlencoded");
    assertThat(queued.providerHeaders()).containsOnlyKeys("X-GCS-Signature", "X-GCS-KeyId");
  }

  @Test
  void an_attempt_that_reached_nobody_audits_no_header_it_did_not_send() {
    merchant.alwaysAnswering(new Answer.DropTheConnection());
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThat(audit.records().get(0).sanitizedHeaders())
        .containsOnlyKeys("Content-Type")
        .doesNotHave(aHeaderOfThisNameUnderAnySpelling("X-Simulator-Transport-Failure"));
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()).get(0).failure())
        .isEqualTo(TransportFailure.CONNECTION_ERROR);
  }

  @Test
  void refuses_a_payload_that_states_its_content_type_twice() {
    Map<String, List<String>> statingTheContentTypeAgain =
        Map.of("content-type", List.of("text/plain"));
    byte[] body = "{}".getBytes(UTF_8);
    Map<String, List<String>> noSanitizedBody = Map.of();

    assertThatThrownBy(
            () ->
                new WebhookPayload(
                    "application/json", statingTheContentTypeAgain, body, noSanitizedBody))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("contentType");
  }

  @Test
  void carries_a_form_callback_with_a_repeated_parameter_to_a_merchant_that_answers_a_page() {
    merchant.alwaysAnswering(new Answer.Respond(200, "<html><body>ACTION=0 RC=00</body></html>"));
    WebhookPayload azericard =
        new WebhookPayload(
            "application/x-www-form-urlencoded",
            Map.of(),
            "ORDER=123&AMOUNT=10.00&AMOUNT=99.00&P_SIGN=ab12".getBytes(UTF_8),
            Map.of(
                "ORDER",
                List.of("123"),
                "AMOUNT",
                List.of("10.00", "99.00"),
                "P_SIGN",
                List.of("[redacted]")));
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery scheduled =
        schedule(
            dispatcher,
            new WebhookDeliveryRequest(
                PROVIDER,
                RUN,
                "merchant-1",
                null,
                "123",
                merchant.url(),
                azericard,
                null,
                null,
                null));

    assertThat(merchant.received().get(0).body())
        .isEqualTo("ORDER=123&AMOUNT=10.00&AMOUNT=99.00&P_SIGN=ab12");
    assertThat(audit.records().get(0).sanitizedBody())
        .containsEntry("AMOUNT", List.of("10.00", "99.00"))
        .containsEntry("P_SIGN", List.of("[redacted]"));
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()).get(0).responsePreview())
        .startsWith("<html>");
  }

  @Test
  void a_separate_inbound_call_finishes_a_delivery_that_was_waiting_for_it() {
    WebhookDispatcher dispatcher =
        dispatcherWith(policy(response -> Acknowledgement.AWAITING_SEPARATE_CALL));
    WebhookDelivery scheduled = schedule(dispatcher, request());
    clock.moveTo(START.plusSeconds(90));

    assertThat(dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT))).isEqualTo(1);

    WebhookDelivery acknowledged = only(dispatcher);
    assertThat(acknowledged.state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(acknowledged.nextAttemptAt()).isNull();
    assertThat(acknowledged.attemptsMade()).isEqualTo(1);
    assertThat(acknowledged.updatedAt()).isEqualTo(START.plusSeconds(90));
    assertThat(merchant.received()).hasSize(1);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .extracting(WebhookAttempt::verdict)
        .containsExactly(Acknowledgement.AWAITING_SEPARATE_CALL);
    assertThat(audit.records()).hasSize(1);
  }

  @Test
  void the_resends_stop_once_the_separate_call_has_acknowledged_the_delivery() {
    WebhookDispatcher dispatcher =
        dispatcherWith(policy(response -> Acknowledgement.AWAITING_SEPARATE_CALL));
    schedule(dispatcher, request());

    dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT));

    clock.moveTo(START.plusSeconds(600));
    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isZero();
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void an_acknowledgement_still_lands_after_a_resend_the_merchant_refused() {
    WebhookDispatcher dispatcher =
        dispatcherWith(
            policy(
                response ->
                    response instanceof MerchantResponse.Answered
                        ? Acknowledgement.AWAITING_SEPARATE_CALL
                        : Acknowledgement.REFUSED));
    WebhookDelivery scheduled = schedule(dispatcher, request());

    merchant.thenAnswering(new Answer.DropTheConnection());
    clock.moveTo(START.plusSeconds(1));
    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isEqualTo(1);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .extracting(WebhookAttempt::verdict)
        .containsExactly(Acknowledgement.AWAITING_SEPARATE_CALL, Acknowledgement.REFUSED);

    assertThat(dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT))).isEqualTo(1);

    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.DELIVERED);
    clock.moveTo(START.plusSeconds(600));
    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isZero();
    assertThat(merchant.received()).hasSize(2);
  }

  @Test
  void an_acknowledgement_lands_when_the_ledger_and_the_attempt_count_disagree() {
    WebhookDispatcher dispatcher =
        dispatcherWith(
            policy(
                response ->
                    response instanceof MerchantResponse.Answered
                        ? Acknowledgement.AWAITING_SEPARATE_CALL
                        : Acknowledgement.REFUSED));
    WebhookDelivery scheduled = schedule(dispatcher, request());
    merchant.thenAnswering(new Answer.DropTheConnection());
    clock.moveTo(START.plusSeconds(1));
    dispatcher.deliverDue(PROVIDER, RUN);

    assertThat(deliveries.claim(refusedNext(only(dispatcher)), 2)).isTrue();
    assertThat(only(dispatcher).attemptsMade()).isEqualTo(3);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId())).hasSize(2);

    assertThat(dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT))).isEqualTo(1);

    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void one_call_settles_every_delivery_of_the_object_that_was_waiting() {
    WebhookDispatcher dispatcher =
        dispatcherWith(policy(response -> Acknowledgement.AWAITING_SEPARATE_CALL));
    schedule(dispatcher, request());
    schedule(dispatcher, request());

    assertThat(dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT))).isEqualTo(2);

    assertThat(dispatcher.of(PROVIDER, RUN))
        .extracting(WebhookDelivery::state)
        .containsExactly(DeliveryState.DELIVERED, DeliveryState.DELIVERED);
    assertThat(merchant.received()).hasSize(2);
  }

  @Test
  void a_repeated_acknowledgement_leaves_the_delivery_exactly_as_the_first_one_left_it() {
    WebhookDispatcher dispatcher =
        dispatcherWith(policy(response -> Acknowledgement.AWAITING_SEPARATE_CALL));
    schedule(dispatcher, request());
    clock.moveTo(START.plusSeconds(10));
    assertThat(dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT))).isEqualTo(1);

    clock.moveTo(START.plusSeconds(20));
    assertThat(dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT))).isZero();

    WebhookDelivery again = only(dispatcher);
    assertThat(again.state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(again.updatedAt()).isEqualTo(START.plusSeconds(10));
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void leaves_a_delivery_that_was_never_waiting_exactly_as_it_stands() {
    merchant.alwaysAnswering(new Answer.Respond(503, "later"));
    WebhookDispatcher dispatcher = dispatcher();
    schedule(dispatcher, request());

    assertThat(dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT))).isZero();

    WebhookDelivery unchanged = only(dispatcher);
    assertThat(unchanged.state()).isEqualTo(DeliveryState.PENDING);
    assertThat(unchanged.nextAttemptAt()).isEqualTo(START.plusSeconds(1));
    assertThat(unchanged.attemptsMade()).isEqualTo(1);
  }

  @Test
  void leaves_a_delivery_that_has_not_been_attempted_yet_alone() {
    WebhookDispatcher dispatcher = dispatcher();
    schedule(dispatcher, requestDelayedBy(Duration.ofMinutes(5)));

    assertThat(dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT))).isZero();

    WebhookDelivery unchanged = only(dispatcher);
    assertThat(unchanged.state()).isEqualTo(DeliveryState.PENDING);
    assertThat(unchanged.attemptsMade()).isZero();
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void settles_nothing_for_an_object_it_holds_no_delivery_of() {
    WebhookDispatcher dispatcher =
        dispatcherWith(policy(response -> Acknowledgement.AWAITING_SEPARATE_CALL));
    schedule(dispatcher, request());

    assertThat(dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of("another-object")))
        .isZero();

    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.PENDING);
  }

  @Test
  void an_acknowledgement_that_lands_mid_attempt_is_not_undone_by_that_attempt() {
    WebhookDispatcher dispatcher =
        dispatcherWith(policy(response -> Acknowledgement.AWAITING_SEPARATE_CALL));
    WebhookDelivery scheduled = schedule(dispatcher, request());
    merchant.onceWhileHandling(
        () -> dispatcher.acknowledgeTheDeliveriesOf(PROVIDER, RUN, List.of(OBJECT)));

    clock.moveTo(START.plusSeconds(1));
    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isEqualTo(1);

    WebhookDelivery delivery = only(dispatcher);
    assertThat(delivery.state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(delivery.nextAttemptAt()).isNull();
    assertThat(delivery.attemptsMade()).isEqualTo(2);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId()))
        .extracting(WebhookAttempt::number)
        .containsExactly(1, 2);
    assertThat(merchant.received()).hasSize(2);

    clock.moveTo(START.plusSeconds(600));
    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isZero();
    assertThat(merchant.received()).hasSize(2);
  }

  @Test
  void a_replay_keeps_the_event_id_and_is_a_delivery_of_its_own() {
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery first = schedule(dispatcher, request());

    WebhookDelivery replay =
        schedule(
            dispatcher,
            new WebhookDeliveryRequest(
                PROVIDER,
                RUN,
                "merchant-1",
                null,
                "734754",
                merchant.url(),
                payload(),
                first.eventId(),
                null,
                null));

    assertThat(first.eventId()).startsWith("evt_");
    assertThat(replay.eventId()).isEqualTo(first.eventId());
    assertThat(replay.deliveryId()).isNotEqualTo(first.deliveryId());
    assertThat(dispatcher.of(PROVIDER, RUN)).hasSize(2);
    assertThat(merchant.received()).hasSize(2);
  }

  @Test
  void finds_one_delivery_by_the_job_id_the_schedule_answered_with() {
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThat(dispatcher.find(PROVIDER, RUN, scheduled.deliveryId()))
        .hasValueSatisfying(
            found -> {
              assertThat(found.deliveryId()).isEqualTo(scheduled.deliveryId());
              assertThat(found.eventId()).isEqualTo(scheduled.eventId());
              assertThat(found.state()).isEqualTo(DeliveryState.DELIVERED);
            });
    assertThat(dispatcher.find(PROVIDER, RUN, "whjob_nothing")).isEmpty();
    assertThat(dispatcher.find(PROVIDER, TestRunId.of("another-run"), scheduled.deliveryId()))
        .isEmpty();
  }

  @Test
  void keeps_the_deliveries_of_one_test_run_out_of_another() {
    WebhookDispatcher dispatcher = dispatcher();
    schedule(dispatcher, request());

    assertThat(dispatcher.of(PROVIDER, TestRunId.of("another-run"))).isEmpty();
    assertThat(dispatcher.clear(PROVIDER, TestRunId.of("another-run"))).isZero();
    assertThat(dispatcher.clear(PROVIDER, RUN)).isEqualTo(1);
    assertThat(dispatcher.of(PROVIDER, RUN)).isEmpty();
  }

  @Test
  void clears_the_deliveries_of_every_test_run_of_one_provider() {
    WebhookDispatcher dispatcher = dispatcher();
    schedule(dispatcher, request());

    assertThat(dispatcher.clearAll(PROVIDER)).isEqualTo(1);
    assertThat(dispatcher.of(PROVIDER, RUN)).isEmpty();
  }

  @Test
  void a_plan_describes_at_least_one_attempt() {
    assertThatThrownBy(() -> new RetryPlan(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("one delay per attempt");
  }

  @Test
  void a_delivery_can_carry_a_schedule_of_its_own_without_moving_anything_elses() {
    merchant.alwaysAnswering(new Answer.Respond(503, "later"));
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery own =
        schedule(
            dispatcher, requestUnder(new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(2)))));
    WebhookDelivery common = schedule(dispatcher, request());

    clock.moveTo(START.plusSeconds(1));
    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isEqualTo(1);

    clock.moveTo(START.plusSeconds(2));
    dispatcher.deliverDue(PROVIDER, RUN);

    assertThat(stateOf(dispatcher, own)).isEqualTo(DeliveryState.EXHAUSTED);
    assertThat(stateOf(dispatcher, common)).isEqualTo(DeliveryState.PENDING);
    assertThat(dispatcher.attemptsOf(own.deliveryId())).hasSize(2);
    assertThat(DOCUMENTED_PLAN.attemptCap()).isEqualTo(5);
  }

  @Test
  void a_plan_is_a_delay_per_attempt_and_a_cap() {
    assertThat(DOCUMENTED_PLAN.attemptCap()).isEqualTo(5);
    assertThat(DOCUMENTED_PLAN.delayBefore(1)).isEqualTo(Duration.ZERO);
    assertThat(DOCUMENTED_PLAN.delayBefore(5)).isEqualTo(Duration.ofMinutes(2));
    assertThat(DOCUMENTED_PLAN.hasAttemptAfter(4)).isTrue();
    assertThat(DOCUMENTED_PLAN.hasAttemptAfter(5)).isFalse();
  }

  @Test
  void a_payload_cannot_be_edited_after_it_is_built() {
    byte[] body = "status=1".getBytes(UTF_8);
    WebhookPayload payload = new WebhookPayload("text/plain", Map.of(), body, Map.of());

    body[0] = 'X';
    payload.body()[1] = 'X';

    assertThat(new String(payload.body(), UTF_8)).isEqualTo("status=1");
    assertThat(payload.toString()).isEqualTo("WebhookPayload[contentType=text/plain, bytes=8]");
  }

  @Test
  void the_scheduled_poll_drains_what_is_due_in_every_test_run() {
    merchant.thenAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    schedule(dispatcher, request());
    WebhookDeliveryPoll poll = new WebhookDeliveryPoll(dispatcher);

    poll.deliverDue();
    assertThat(merchant.received()).hasSize(1);

    clock.moveTo(START.plusSeconds(1));
    poll.deliverDue();

    assertThat(merchant.received()).hasSize(2);
    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void a_drain_of_every_test_run_reports_how_many_attempts_it_made() {
    merchant.alwaysAnswering(new Answer.Respond(500, "no thanks"));
    WebhookDispatcher dispatcher = dispatcher();
    schedule(dispatcher, request());
    schedule(dispatcher, requestIn(TestRunId.of("another-run")));

    clock.moveTo(START.plusSeconds(1));
    assertThat(dispatcher.deliverDueEverywhere()).isEqualTo(2);
    assertThat(merchant.received()).hasSize(4);

    assertThat(dispatcher.deliverDueEverywhere()).isZero();
    assertThat(merchant.received()).hasSize(4);
  }

  @Test
  void renders_the_attempt_the_provider_is_about_to_make_rather_than_the_one_it_scheduled() {
    merchant.thenAnswering(new Answer.Respond(500, "not now"));
    WebhookDispatcher dispatcher = dispatcherRendering(new StampsTheAttemptTime());
    schedule(dispatcher, request());

    clock.moveTo(START.plusSeconds(1));
    dispatcher.deliverDue(PROVIDER, RUN);

    assertThat(merchant.received())
        .extracting(received -> received.header("X-Attempt-Time"))
        .containsExactly(List.of(START.toString()), List.of(START.plusSeconds(1).toString()));
    assertThat(merchant.received().get(0).body()).isEqualTo(merchant.received().get(1).body());
  }

  @Test
  void audits_the_headers_the_attempt_travelled_under_rather_than_the_ones_it_was_scheduled_with() {
    WebhookDispatcher dispatcher = dispatcherRendering(new StampsTheAttemptTime());
    schedule(dispatcher, request());

    assertThat(audit.of(PROVIDER, RUN))
        .singleElement()
        .satisfies(
            record ->
                assertThat(record.sanitizedHeaders())
                    .containsEntry("X-Attempt-Time", List.of(START.toString())));
    assertThat(only(dispatcher).payload().providerHeaders()).isEmpty();
  }

  @Test
  void refuses_two_renderings_claiming_one_provider() {
    MerchantEndpoint endpoint = endpoint();
    DeliveryShaping shaping = new DeliveryShaping(scenarioRules);
    List<AcknowledgementPolicy> onePolicy = List.of(new TwoHundredAcknowledges());
    List<AttemptRendering> twoRenderingsForOneProvider =
        List.of(new StampsTheAttemptTime(), new StampsTheAttemptTime());

    assertThatThrownBy(
            () ->
                new WebhookDispatcher(
                    deliveries,
                    endpoint,
                    audit,
                    clock,
                    ids,
                    DOCUMENTED_PLAN,
                    shaping,
                    onePolicy,
                    twoRenderingsForOneProvider,
                    OutsideAnyUnitOfWork.RUNS_AT_ONCE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("two attempt renderings claim the provider id grow");
  }

  @Test
  void a_delivery_the_provider_will_not_render_reaches_no_merchant_and_says_so_on_the_row() {
    WebhookDispatcher dispatcher = dispatcherRendering(new RefusesToRender());

    WebhookDelivery scheduled = schedule(dispatcher, request());

    WebhookDelivery row = only(dispatcher);
    assertThat(merchant.received()).isEmpty();
    assertThat(row.deliveryId()).isEqualTo(scheduled.deliveryId());
    assertThat(row.state()).isEqualTo(DeliveryState.UNDELIVERABLE);
    assertThat(row.attemptsMade()).isZero();
    assertThat(row.nextAttemptAt()).isNull();
    assertThat(row.notSentBecause()).isEqualTo(RefusesToRender.REASON);
    assertThat(dispatcher.attemptsOf(scheduled.deliveryId())).isEmpty();
  }

  @Test
  void a_delivery_the_provider_will_not_render_is_no_longer_due_and_counts_as_nothing_attempted() {
    WebhookDispatcher dispatcher = dispatcherRendering(new RefusesToRender());
    schedule(dispatcher, requestDelayedBy(Duration.ofSeconds(5)));
    clock.moveTo(START.plusSeconds(5));

    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isZero();
    assertThat(dispatcher.deliverDue(PROVIDER, RUN)).isZero();

    assertThat(merchant.received()).isEmpty();
    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.UNDELIVERABLE);
  }

  @Test
  void a_delivery_the_provider_would_not_send_is_written_to_the_trail_as_one_that_was_not_sent() {
    WebhookDispatcher dispatcher = dispatcherRendering(new RefusesToRender());

    schedule(dispatcher, request());

    assertThat(audit.records()).hasSize(1);
    AuditRecord notSent = audit.records().get(0);
    assertThat(notSent.direction()).isEqualTo(AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT);
    assertThat(notSent.outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(notSent.httpStatus()).isNull();
    assertThat(notSent.method()).isEqualTo("POST");
    assertThat(notSent.path()).isEqualTo(merchant.url());
    assertThat(notSent.providerObjectId()).isEqualTo(OBJECT);
    assertThat(notSent.attempt()).isZero();
  }

  @Test
  void
      a_retry_to_a_target_the_provider_withholds_is_refused_and_leaves_a_delivered_callback_delivered() {
    WebhookDispatcher dispatcher = dispatcherRendering(new SendsOnlyTo(merchant.url()));
    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThatThrownBy(() -> dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId(), ELSEWHERE))
        .isInstanceOf(AttemptWithheldException.class)
        .hasMessage(AttemptWithheldException.THE_PROVIDER_WITHHELD_IT + SendsOnlyTo.REASON);

    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.DELIVERED);
    assertThat(only(dispatcher).attemptsMade()).isEqualTo(1);
    assertThat(merchant.received()).hasSize(1);
    assertThat(audit.records())
        .filteredOn(row -> row.direction() == AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT)
        .singleElement()
        .satisfies(row -> assertThat(row.path()).isEqualTo(ELSEWHERE));
  }

  @Test
  void a_retry_to_a_target_the_provider_withholds_leaves_a_pending_callback_on_its_schedule() {
    WebhookDispatcher dispatcher = dispatcherRendering(new SendsOnlyTo(merchant.url()));
    WebhookDelivery scheduled = schedule(dispatcher, requestDelayedBy(Duration.ofSeconds(5)));

    assertThatThrownBy(() -> dispatcher.retry(PROVIDER, RUN, scheduled.deliveryId(), ELSEWHERE))
        .isInstanceOf(AttemptWithheldException.class);

    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.PENDING);
    assertThat(only(dispatcher).nextAttemptAt()).isEqualTo(scheduled.nextAttemptAt());
    assertThat(only(dispatcher).attemptsMade()).isZero();
    assertThat(merchant.received()).isEmpty();
  }

  @ParameterizedTest(name = "a retry naming {0}")
  @ValueSource(strings = {"no target", "its own target"})
  void a_retry_to_the_scheduled_target_the_provider_withholds_makes_the_callback_undeliverable(
      String named) {
    WebhookDispatcher dispatcher = dispatcherRendering(new SendsOnlyTo(ELSEWHERE));
    WebhookDelivery scheduled = schedule(dispatcher, requestDelayedBy(Duration.ofSeconds(5)));
    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.PENDING);

    dispatcher.retry(
        PROVIDER, RUN, scheduled.deliveryId(), "no target".equals(named) ? null : merchant.url());

    assertThat(only(dispatcher).state()).isEqualTo(DeliveryState.UNDELIVERABLE);
    assertThat(only(dispatcher).notSentBecause()).isEqualTo(SendsOnlyTo.REASON);
    assertThat(audit.records())
        .filteredOn(row -> row.direction() == AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT)
        .singleElement()
        .satisfies(row -> assertThat(row.path()).isEqualTo(merchant.url()));
  }

  @Test
  void a_refused_attempt_names_the_provider_object_its_job_reports_or_says_it_reports_none() {
    assertThat(new AttemptWithheldException(OBJECT, "r").theRefusalItReports())
        .isEqualTo(ActionVerdict.conflicting(RefusalSubject.theObject(OBJECT)));
    assertThat(new DeliveryHasNoTargetException(null).theRefusalItReports())
        .isEqualTo(
            ActionVerdict.conflicting(
                RefusalSubject.noObjectWasNamed(
                    AttemptRefusedException.THE_JOB_IT_ASKED_TO_ATTEMPT)));
  }

  private static final String ELSEWHERE = "http://127.0.0.1:9/elsewhere";

  private static final class SendsOnlyTo implements AttemptRendering {

    static final String REASON = "that target is not one this provider posts to";

    private final String allowed;

    SendsOnlyTo(String allowed) {
      this.allowed = allowed;
    }

    @Override
    public ProviderId provider() {
      return PROVIDER;
    }

    @Override
    public Rendering renderFor(WebhookDelivery delivery, String targetUrl, Instant attemptedAt) {
      return allowed.equals(targetUrl)
          ? new Rendering.Sending(delivery.payload())
          : new Rendering.NothingThisProviderWouldSend(REASON);
    }
  }

  private static final class RefusesToRender implements AttemptRendering {

    static final String REASON = "the account this event belongs to is no longer configured";

    @Override
    public ProviderId provider() {
      return PROVIDER;
    }

    @Override
    public Rendering renderFor(WebhookDelivery delivery, String targetUrl, Instant attemptedAt) {
      return new Rendering.NothingThisProviderWouldSend(REASON);
    }
  }

  private WebhookDispatcher dispatcherRendering(AttemptRendering rendering) {
    return new WebhookDispatcher(
        deliveries,
        endpoint(),
        audit,
        clock,
        ids,
        DOCUMENTED_PLAN,
        new DeliveryShaping(scenarioRules),
        List.of(new TwoHundredAcknowledges()),
        List.of(rendering),
        OutsideAnyUnitOfWork.RUNS_AT_ONCE);
  }

  private static final class StampsTheAttemptTime implements AttemptRendering {

    @Override
    public ProviderId provider() {
      return PROVIDER;
    }

    @Override
    public Rendering renderFor(WebhookDelivery delivery, String targetUrl, Instant attemptedAt) {
      WebhookPayload stored = delivery.payload();
      return new Rendering.Sending(
          new WebhookPayload(
              stored.contentType(),
              Map.of("X-Attempt-Time", List.of(attemptedAt.toString())),
              stored.body(),
              stored.sanitizedBody()));
    }
  }

  @Test
  void a_rule_that_holds_a_delivery_back_keeps_the_merchant_waiting_until_the_hold_has_passed() {
    shapeEveryDelivery(new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5)));
    WebhookDispatcher dispatcher = dispatcher();

    schedule(dispatcher, request());

    assertThat(merchant.received()).isEmpty();
    clock.moveTo(START.plusSeconds(5));
    dispatcher.deliverDue(PROVIDER, RUN);
    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void a_rule_that_duplicates_a_delivery_posts_the_same_event_to_the_merchant_twice() {
    shapeEveryDelivery(new ScenarioAction.DeliveryDuplicated());
    WebhookDispatcher dispatcher = dispatcher();

    List<WebhookDelivery> scheduled = dispatcher.schedule(request());

    assertThat(scheduled).hasSize(2);
    assertThat(scheduled)
        .extracting(WebhookDelivery::eventId)
        .containsOnly(scheduled.get(0).eventId());
    assertThat(scheduled.get(0).deliveryId()).isNotEqualTo(scheduled.get(1).deliveryId());
    assertThat(merchant.received()).hasSize(2);
    assertThat(merchant.received().get(1).body()).isEqualTo(merchant.received().get(0).body());
  }

  @Test
  void a_rule_that_drops_a_delivery_keeps_the_record_and_posts_nothing_ever() {
    shapeEveryDelivery(new ScenarioAction.DeliveryDropped());
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery dropped = schedule(dispatcher, request());

    assertThat(merchant.received()).isEmpty();
    assertThat(stateOf(dispatcher, dropped)).isEqualTo(DeliveryState.DROPPED);
    clock.moveTo(START.plus(Duration.ofMinutes(10)));
    dispatcher.deliverDue(PROVIDER, RUN);
    assertThat(merchant.received()).isEmpty();
  }

  @Test
  void a_rule_about_another_object_leaves_the_deliveries_of_this_one_alone() {
    scenarioRules.add(
        deliveryRule(
            new ScenarioMatch(
                null, null, "another-process", null, null, null, null, null, null, Map.of()),
            new ScenarioAction.DeliveryDropped()));

    schedule(dispatcher(), request());

    assertThat(merchant.received()).hasSize(1);
  }

  @Test
  void a_rule_naming_an_event_type_shapes_only_the_delivery_that_reports_it() {
    scenarioRules.add(
        deliveryRule(
            new ScenarioMatch(
                null, null, null, null, null, null, null, null, "payment_process.failed", Map.of()),
            new ScenarioAction.DeliveryDropped()));
    WebhookDispatcher dispatcher = dispatcher();

    schedule(dispatcher, requestReporting("payment_process.completed"));
    schedule(dispatcher, requestReporting("payment_process.failed"));

    assertThat(merchant.received()).hasSize(1);
    assertThat(merchant.received().get(0).body()).contains("payment_process.completed");
  }

  @Test
  void two_events_of_one_object_reach_the_merchant_in_the_reverse_order_of_their_scheduling() {
    scenarioRules.add(
        deliveryRule(
            new ScenarioMatch(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "payment_process.completed",
                Map.of()),
            new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5))));
    WebhookDispatcher dispatcher = dispatcher();

    schedule(dispatcher, requestReporting("payment_process.completed"));
    schedule(dispatcher, requestReporting("payment_process.failed"));
    clock.moveTo(START.plusSeconds(5));
    dispatcher.deliverDue(PROVIDER, RUN);

    assertThat(merchant.received())
        .extracting(LocalMerchantServer.ReceivedCallback::body)
        .containsExactly("event=payment_process.failed", "event=payment_process.completed");
  }

  @Test
  void a_rule_that_decides_a_request_shapes_no_delivery_of_the_same_test_run() {
    scenarioRules.add(
        new NewScenarioRule(
            PROVIDER, RUN, 0, ScenarioMatch.anyRequest(), new ScenarioAction.FixedSuccess(), null));
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery scheduled = schedule(dispatcher, request());

    assertThat(merchant.received()).hasSize(1);
    assertThat(stateOf(dispatcher, scheduled)).isEqualTo(DeliveryState.DELIVERED);
  }

  @Test
  void an_attempt_of_a_delivery_a_rule_held_back_names_the_rule_that_held_it() {
    ScenarioRule holding =
        shapeEveryDelivery(new ScenarioAction.DeliveryDelayed(Duration.ofSeconds(5)));
    WebhookDispatcher dispatcher = dispatcher();
    WebhookDelivery held = schedule(dispatcher, request());

    clock.moveTo(START.plusSeconds(5));
    dispatcher.deliverDue(PROVIDER, RUN);

    assertThat(held.shapedBy()).isEqualTo(holding.scenarioId());
    assertThat(audit.records())
        .extracting(AuditRecord::direction, AuditRecord::scenarioId)
        .containsExactly(tuple(AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT, holding.scenarioId()));
  }

  @Test
  void both_attempts_of_a_duplicated_delivery_name_the_one_rule_that_duplicated_it() {
    ScenarioRule duplicating = shapeEveryDelivery(new ScenarioAction.DeliveryDuplicated());

    dispatcher().schedule(request());

    assertThat(audit.records())
        .extracting(AuditRecord::direction, AuditRecord::scenarioId)
        .containsExactly(
            tuple(AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT, duplicating.scenarioId()),
            tuple(AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT, duplicating.scenarioId()));
  }

  @Test
  void a_delivery_a_rule_dropped_is_written_to_the_trail_once_although_it_is_never_attempted() {
    ScenarioRule dropping = shapeEveryDelivery(new ScenarioAction.DeliveryDropped());
    WebhookDispatcher dispatcher = dispatcher();

    WebhookDelivery dropped = schedule(dispatcher, request());
    clock.moveTo(START.plus(Duration.ofMinutes(10)));
    dispatcher.deliverDue(PROVIDER, RUN);

    assertThat(dropped.shapedBy()).isEqualTo(dropping.scenarioId());
    assertThat(audit.records()).hasSize(1);
    AuditRecord withheld = audit.records().get(0);
    assertThat(withheld.direction()).isEqualTo(AuditDirection.OUTBOUND_WEBHOOK_DROPPED);
    assertThat(withheld.scenarioId()).isEqualTo(dropping.scenarioId());
    assertThat(withheld.attempt()).isZero();
    assertThat(withheld.httpStatus()).isNull();
    assertThat(withheld.outcome()).isNull();
    assertThat(withheld.method()).isEqualTo("POST");
    assertThat(withheld.path()).isEqualTo(merchant.url());
    assertThat(withheld.providerObjectId()).isEqualTo("734754");
    assertThat(withheld.sanitizedBody()).containsKey("data[processId]");
  }

  @Test
  void a_delivery_no_rule_shaped_names_no_rule_on_the_row_or_on_the_trail() {
    schedule(dispatcher(), request());

    assertThat(only(dispatcher()).shapedBy()).isNull();
    assertThat(audit.records()).singleElement().extracting(AuditRecord::scenarioId).isNull();
  }

  private ScenarioRule shapeEveryDelivery(ScenarioAction.OnTheDelivery action) {
    return scenarioRules.add(deliveryRule(ScenarioMatch.anyRequest(), action));
  }

  private static NewScenarioRule deliveryRule(
      ScenarioMatch match, ScenarioAction.OnTheDelivery action) {
    return new NewScenarioRule(PROVIDER, RUN, 0, match, action, null);
  }

  private WebhookDeliveryRequest requestReporting(String eventType) {
    return new WebhookDeliveryRequest(
        PROVIDER,
        RUN,
        "merchant-1",
        eventType,
        "734754",
        merchant.url(),
        payloadOf("event=" + eventType),
        null,
        null,
        null);
  }

  private static WebhookPayload payloadOf(String body) {
    return new WebhookPayload(
        "application/x-www-form-urlencoded",
        Map.of(),
        body.getBytes(UTF_8),
        Map.of("event", List.of(body)));
  }

  private final InMemoryScenarioRules scenarioRules = new InMemoryScenarioRules();

  private static WebhookDelivery schedule(
      WebhookDispatcher dispatcher, WebhookDeliveryRequest request) {
    List<WebhookDelivery> scheduled = dispatcher.schedule(request);
    assertThat(scheduled).hasSize(1);
    return scheduled.get(0);
  }

  private WebhookDispatcher dispatcher() {
    return dispatcherWith(new TwoHundredAcknowledges());
  }

  private static final String SIGNATURE_HEADER = "X-Test-Signature";

  private WebhookDispatcher dispatcherSigningEachAttemptWithItsOwnMoment() {
    return new WebhookDispatcher(
        deliveries,
        endpoint(),
        audit,
        clock,
        ids,
        DOCUMENTED_PLAN,
        new DeliveryShaping(scenarioRules),
        List.of(new TwoHundredAcknowledges()),
        List.of(new SignsEachAttemptAfresh()),
        OutsideAnyUnitOfWork.RUNS_AT_ONCE);
  }

  private static final class SignsEachAttemptAfresh implements AttemptRendering {

    @Override
    public ProviderId provider() {
      return PROVIDER;
    }

    @Override
    public Rendering renderFor(WebhookDelivery delivery, String targetUrl, Instant attemptedAt) {
      return new Rendering.Sending(
          new WebhookPayload(
              delivery.payload().contentType(),
              Map.of(SIGNATURE_HEADER, List.of("t=" + attemptedAt.getEpochSecond())),
              delivery.payload().body(),
              delivery.payload().sanitizedBody()));
    }
  }

  private WebhookDispatcher dispatcherWith(AcknowledgementPolicy policy) {
    return new WebhookDispatcher(
        deliveries,
        endpoint(),
        audit,
        clock,
        ids,
        DOCUMENTED_PLAN,
        new DeliveryShaping(scenarioRules),
        List.of(policy),
        List.of(),
        OutsideAnyUnitOfWork.RUNS_AT_ONCE);
  }

  private WebhookDispatcher dispatcherPlanning(CallbackRetryPlans plans) {
    return new WebhookDispatcher(
        deliveries,
        endpoint(),
        audit,
        clock,
        ids,
        plans,
        new DeliveryShaping(scenarioRules),
        List.of(new TwoHundredAcknowledges()),
        List.of(),
        OutsideAnyUnitOfWork.RUNS_AT_ONCE);
  }

  private static final class SwappablePlans implements CallbackRetryPlans {

    private RetryPlan plan;

    SwappablePlans(RetryPlan plan) {
      this.plan = plan;
    }

    void nowAnswering(RetryPlan plan) {
      this.plan = plan;
    }

    @Override
    public RetryPlan of(ProviderId provider, TestRunId testRunId) {
      return plan;
    }
  }

  private WebhookDispatcher dispatcherWith(MerchantEndpoint transport) {
    return new WebhookDispatcher(
        deliveries,
        transport,
        audit,
        clock,
        ids,
        DOCUMENTED_PLAN,
        new DeliveryShaping(scenarioRules),
        List.of(new TwoHundredAcknowledges()),
        List.of(),
        OutsideAnyUnitOfWork.RUNS_AT_ONCE);
  }

  private static void waitFor(CyclicBarrier barrier) {
    try {
      barrier.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (BrokenBarrierException | TimeoutException neverMet) {
      throw new IllegalStateException(neverMet);
    }
  }

  private static MerchantEndpoint endpoint() {
    return new HttpMerchantEndpoint(Duration.ofSeconds(2), Duration.ofSeconds(5), 64);
  }

  private void runOutThePlan(WebhookDispatcher dispatcher) {
    for (Duration due :
        List.of(
            Duration.ofSeconds(1),
            Duration.ofSeconds(6),
            Duration.ofSeconds(36),
            Duration.ofSeconds(156))) {
      clock.moveTo(START.plus(due));
      dispatcher.deliverDue(PROVIDER, RUN);
    }
  }

  private WebhookDeliveryRequest request() {
    return new WebhookDeliveryRequest(
        PROVIDER, RUN, "merchant-1", null, "734754", merchant.url(), payload(), null, null, null);
  }

  private WebhookDeliveryRequest requestDelayedBy(Duration delay) {
    return new WebhookDeliveryRequest(
        PROVIDER, RUN, "merchant-1", null, "734754", merchant.url(), payload(), null, null, delay);
  }

  private WebhookDeliveryRequest requestIn(TestRunId testRunId) {
    return new WebhookDeliveryRequest(
        PROVIDER,
        testRunId,
        "merchant-1",
        null,
        "734754",
        merchant.url(),
        payload(),
        null,
        null,
        null);
  }

  private WebhookDeliveryRequest requestUnder(RetryPlan plan) {
    return new WebhookDeliveryRequest(
        PROVIDER, RUN, "merchant-1", null, "734754", merchant.url(), payload(), null, plan, null);
  }

  private static DeliveryState stateOf(WebhookDispatcher dispatcher, WebhookDelivery delivery) {
    return dispatcher.find(PROVIDER, RUN, delivery.deliveryId()).orElseThrow().state();
  }

  private static WebhookDeliveryRequest requestTo(String targetUrl) {
    return new WebhookDeliveryRequest(
        PROVIDER, RUN, "merchant-1", null, "734754", targetUrl, payload(), null, null, null);
  }

  private static WebhookPayload payload() {
    return payload(Map.of());
  }

  private static WebhookPayload payload(Map<String, List<String>> headers) {
    return new WebhookPayload(
        "application/x-www-form-urlencoded",
        headers,
        "status=1&data%5BprocessId%5D=734754".getBytes(UTF_8),
        Map.of(
            "status",
            List.of("1"),
            "data[processId]",
            List.of("734754"),
            "data[processToken]",
            List.of("[redacted]")));
  }

  private static WebhookDelivery refusedNext(WebhookDelivery delivery) {
    return new WebhookDelivery(
        delivery.deliveryId(),
        delivery.provider(),
        delivery.testRunId(),
        delivery.merchantId(),
        delivery.eventId(),
        delivery.eventType(),
        delivery.providerObjectId(),
        delivery.targetUrl(),
        delivery.payload(),
        delivery.retryPlan(),
        DeliveryState.PENDING,
        delivery.acknowledgementOwed(),
        delivery.attemptsMade() + 1,
        START.plusSeconds(300),
        delivery.createdAt(),
        START.plusSeconds(300),
        null,
        null);
  }

  private WebhookDelivery only(WebhookDispatcher dispatcher) {
    List<WebhookDelivery> all = dispatcher.of(PROVIDER, RUN);
    assertThat(all).hasSize(1);
    return all.get(0);
  }

  private static AcknowledgementPolicy policy(Function<MerchantResponse, Acknowledgement> verdict) {
    return new AcknowledgementPolicy() {
      @Override
      public ProviderId provider() {
        return PROVIDER;
      }

      @Override
      public Acknowledgement judge(WebhookDelivery delivery, MerchantResponse response) {
        return verdict.apply(response);
      }
    };
  }

  private static final class TwoHundredAcknowledges implements AcknowledgementPolicy {

    @Override
    public ProviderId provider() {
      return PROVIDER;
    }

    @Override
    public Acknowledgement judge(WebhookDelivery delivery, MerchantResponse response) {
      return response instanceof MerchantResponse.Answered answered
              && answered.httpStatus() >= 200
              && answered.httpStatus() < 300
          ? Acknowledgement.ACKNOWLEDGED
          : Acknowledgement.REFUSED;
    }
  }

  private static final class MovableClock implements SimulatorClock {

    private volatile Instant now;

    MovableClock(Instant now) {
      this.now = now;
    }

    void moveTo(Instant instant) {
      this.now = instant;
    }

    @Override
    public Instant now(TestRunId testRunId) {
      return now;
    }
  }

  private static final class RecordingAudit implements AuditTrail {

    private final List<AuditRecord> records = new ArrayList<>();

    @Override
    public synchronized void record(AuditRecord record) {
      records.add(record);
    }

    @Override
    public synchronized List<AuditRecord> of(ProviderId provider, TestRunId testRunId) {
      return records.stream()
          .filter(r -> r.provider().equals(provider) && r.testRunId().equals(testRunId))
          .toList();
    }

    @Override
    public synchronized int clear(ProviderId provider, TestRunId testRunId) {
      List<AuditRecord> doomed = of(provider, testRunId);
      records.removeAll(doomed);
      return doomed.size();
    }

    @Override
    public synchronized int clearAll(ProviderId provider) {
      int size = records.size();
      records.clear();
      return size;
    }

    synchronized List<AuditRecord> records() {
      return List.copyOf(records);
    }
  }

  private static final class CountingIds implements IdGenerator {

    private final Map<Integer, Integer> issued = new HashMap<>();

    @Override
    public synchronized String hex(int length) {
      return pad(Integer.toHexString(next(length)), length);
    }

    @Override
    public synchronized String digits(int length) {
      return "1" + pad(Integer.toString(next(length)), length - 1);
    }

    private int next(int length) {
      return issued.merge(length, 1, Integer::sum);
    }

    private static String pad(String value, int length) {
      return "0".repeat(Math.max(0, length - value.length())) + value;
    }
  }
}
