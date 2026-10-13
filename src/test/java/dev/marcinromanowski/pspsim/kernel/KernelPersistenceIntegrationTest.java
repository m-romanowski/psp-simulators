package dev.marcinromanowski.pspsim.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditReads;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.PendingDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.TransportFailure;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookAttempt;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDeliveries;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class KernelPersistenceIntegrationTest extends PostgresBackedTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");
  private static final ProviderId OTHER_PROVIDER = ProviderId.of("stripe");
  private static final TestRunId RUN = TestRunId.of("kernel-persistence-it");
  private static final TestRunId OTHER_RUN = TestRunId.of("kernel-persistence-it-other");
  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");

  @Autowired private FixedTimes fixedTimes;
  @Autowired private TestRuns testRuns;
  @Autowired private SimulatorClock clock;
  @Autowired private ProviderAccounts accounts;
  @Autowired private JdbcClient jdbc;
  @Autowired private AuditTrail audit;
  @Autowired private AuditReads auditReads;
  @Autowired private WebhookDeliveries deliveries;

  @AfterEach
  void clearWhatThisTestWrote() {
    for (TestRunId run : List.of(RUN, OTHER_RUN)) {
      fixedTimes.release(run);
      forgetTheAccountsOf(run);
      audit.clear(PROVIDER, run);
      audit.clear(OTHER_PROVIDER, run);
      deliveries.clear(PROVIDER, run);
    }
  }

  private void forgetTheAccountsOf(TestRunId run) {
    jdbc.sql("delete from simulator_provider_account where test_run_id = ?")
        .param(run.value())
        .update();
    jdbc.sql("delete from simulator_webhook_target where test_run_id = ?")
        .param(run.value())
        .update();
  }

  @Test
  void a_registered_test_run_exists_and_registering_it_again_changes_nothing() {
    testRuns.register(RUN);
    fixedTimes.pin(RUN, PINNED);

    testRuns.register(RUN);

    assertThat(testRuns.exists(RUN)).isTrue();
    assertThat(fixedTimes.of(RUN)).contains(PINNED);
  }

  @Test
  void a_test_run_nothing_configured_does_not_exist() {
    assertThat(testRuns.exists(TestRunId.of("kernel-persistence-it-never-configured"))).isFalse();
  }

  @Test
  void the_default_namespace_exists_without_being_configured() {
    assertThat(testRuns.exists(TestRunId.of("default"))).isTrue();
  }

  @Test
  void pinning_a_clock_registers_the_test_run_it_pins() {
    fixedTimes.pin(OTHER_RUN, PINNED);

    assertThat(testRuns.exists(OTHER_RUN)).isTrue();
  }

  @Test
  void releasing_a_pinned_clock_leaves_the_test_run_in_place() {
    fixedTimes.pin(RUN, PINNED);

    fixedTimes.release(RUN);

    assertThat(fixedTimes.of(RUN)).isEmpty();
    assertThat(testRuns.exists(RUN)).isTrue();
  }

  @Test
  void neither_write_into_the_register_accepts_the_namespace_reserved_for_a_failed_resolution() {
    assertThatThrownBy(() -> testRuns.register(TestRunId.UNKNOWN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");
    assertThatThrownBy(() -> fixedTimes.pin(TestRunId.UNKNOWN, PINNED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved");

    assertThat(testRuns.exists(TestRunId.UNKNOWN)).isFalse();
  }

  @Test
  void a_pinned_time_survives_a_round_trip_and_a_repin() {
    fixedTimes.pin(RUN, PINNED);
    assertThat(fixedTimes.of(RUN)).contains(PINNED);
    assertThat(clock.now(RUN)).isEqualTo(PINNED);

    fixedTimes.pin(RUN, PINNED.plusSeconds(3600));

    assertThat(fixedTimes.of(RUN)).contains(PINNED.plusSeconds(3600));
  }

  @Test
  void releasing_one_test_run_leaves_another_pinned() {
    fixedTimes.pin(RUN, PINNED);
    fixedTimes.pin(OTHER_RUN, PINNED.plusSeconds(60));

    fixedTimes.release(RUN);

    assertThat(fixedTimes.of(RUN)).isEmpty();
    assertThat(fixedTimes.of(OTHER_RUN)).contains(PINNED.plusSeconds(60));
  }

  @Test
  void a_test_run_that_pinned_nothing_has_no_row_at_all() {
    assertThat(fixedTimes.of(RUN)).isEmpty();

    fixedTimes.release(RUN);

    assertThat(fixedTimes.of(RUN)).isEmpty();
  }

  @Test
  void an_identity_and_its_webhook_target_survive_a_round_trip() {
    accounts.merge(
        PROVIDER,
        RUN,
        List.of("account-1"),
        List.of(new WebhookTarget("account-1", "https://merchant.example/hook")),
        nothingOfItsOwn());

    assertThat(accounts.identitiesOf(PROVIDER, RUN)).containsExactly("account-1");
    assertThat(accounts.webhookTargetsOf(PROVIDER, RUN))
        .containsExactly(new WebhookTarget("account-1", "https://merchant.example/hook"));
  }

  @Test
  void the_provider_stores_its_own_rows_inside_the_transaction_the_core_opened() {
    List<String> callsMade = new ArrayList<>();

    accounts.merge(
        PROVIDER,
        RUN,
        List.of("account-1"),
        List.of(),
        new ProviderConfigurationPlan(Set.of(), List.of(), () -> callsMade.add("stored")));

    assertThat(callsMade).containsExactly("stored");
  }

  @Test
  void an_account_the_next_configuration_leaves_out_is_still_configured() {
    accounts.merge(PROVIDER, RUN, List.of("account-1", "account-2"), List.of(), nothingOfItsOwn());

    accounts.merge(PROVIDER, RUN, List.of("account-2"), List.of(), nothingOfItsOwn());

    assertThat(accounts.identitiesOf(PROVIDER, RUN)).containsExactly("account-1", "account-2");
  }

  @Test
  void a_configuration_that_names_no_account_leaves_every_account_of_the_run_in_place() {
    accounts.merge(
        PROVIDER,
        RUN,
        List.of("account-1"),
        List.of(new WebhookTarget("account-1", "https://merchant.example/hook")),
        nothingOfItsOwn());

    accounts.merge(PROVIDER, RUN, List.of(), List.of(), nothingOfItsOwn());

    assertThat(accounts.identitiesOf(PROVIDER, RUN)).containsExactly("account-1");
    assertThat(accounts.webhookTargetsOf(PROVIDER, RUN))
        .containsExactly(new WebhookTarget("account-1", "https://merchant.example/hook"));
  }

  @Test
  void the_targets_of_the_account_a_configuration_names_are_replaced_and_no_others_are() {
    accounts.merge(
        PROVIDER,
        RUN,
        List.of("account-1", "account-2"),
        List.of(
            new WebhookTarget("account-1", "https://merchant.example/one"),
            new WebhookTarget("account-2", "https://merchant.example/two")),
        nothingOfItsOwn());

    accounts.merge(
        PROVIDER,
        RUN,
        List.of("account-1"),
        List.of(new WebhookTarget("account-1", "https://merchant.example/moved")),
        nothingOfItsOwn());

    assertThat(accounts.webhookTargetsOf(PROVIDER, RUN))
        .containsExactly(
            new WebhookTarget("account-1", "https://merchant.example/moved"),
            new WebhookTarget("account-2", "https://merchant.example/two"));
  }

  @Test
  void the_provider_is_told_to_forget_its_own_rows_inside_the_transaction_the_core_opened() {
    List<String> callsMade = new ArrayList<>();
    accounts.merge(
        PROVIDER,
        RUN,
        List.of("account-1"),
        List.of(new WebhookTarget("account-1", "https://merchant.example/hook")),
        nothingOfItsOwn());

    boolean removed =
        accounts.remove(
            PROVIDER, RUN, "account-1", new AccountRemovalPlan(() -> callsMade.add("forgotten")));

    assertThat(removed).isTrue();
    assertThat(callsMade).containsExactly("forgotten");
    assertThat(accounts.identitiesOf(PROVIDER, RUN)).isEmpty();
    assertThat(accounts.webhookTargetsOf(PROVIDER, RUN)).isEmpty();
  }

  @Test
  void an_account_no_configuration_named_is_not_removed_and_the_provider_is_not_told() {
    List<String> callsMade = new ArrayList<>();
    accounts.merge(PROVIDER, RUN, List.of("account-1"), List.of(), nothingOfItsOwn());

    boolean removed =
        accounts.remove(
            PROVIDER, RUN, "account-9", new AccountRemovalPlan(() -> callsMade.add("forgotten")));

    assertThat(removed).isFalse();
    assertThat(callsMade).isEmpty();
    assertThat(accounts.identitiesOf(PROVIDER, RUN)).containsExactly("account-1");
  }

  @Test
  void removing_an_account_of_one_provider_leaves_the_same_identity_of_another_alone() {
    accounts.merge(PROVIDER, RUN, List.of("account-1"), List.of(), nothingOfItsOwn());
    accounts.merge(OTHER_PROVIDER, RUN, List.of("account-1"), List.of(), nothingOfItsOwn());

    accounts.remove(PROVIDER, RUN, "account-1", new AccountRemovalPlan(() -> {}));

    assertThat(accounts.identitiesOf(PROVIDER, RUN)).isEmpty();
    assertThat(accounts.identitiesOf(OTHER_PROVIDER, RUN)).containsExactly("account-1");
  }

  @Test
  void removing_an_account_of_one_test_run_leaves_the_same_identity_of_another_run_alone() {
    accounts.merge(PROVIDER, RUN, List.of("account-1"), List.of(), nothingOfItsOwn());
    accounts.merge(PROVIDER, OTHER_RUN, List.of("account-1"), List.of(), nothingOfItsOwn());

    accounts.remove(PROVIDER, RUN, "account-1", new AccountRemovalPlan(() -> {}));

    assertThat(accounts.identitiesOf(PROVIDER, RUN)).isEmpty();
    assertThat(accounts.identitiesOf(PROVIDER, OTHER_RUN)).containsExactly("account-1");
  }

  @Test
  void identities_are_kept_per_provider_and_per_test_run() {
    accounts.merge(PROVIDER, RUN, List.of("account-1"), List.of(), nothingOfItsOwn());
    accounts.merge(PROVIDER, OTHER_RUN, List.of("account-2"), List.of(), nothingOfItsOwn());
    accounts.merge(OTHER_PROVIDER, RUN, List.of("account-3"), List.of(), nothingOfItsOwn());

    assertThat(accounts.identitiesOf(PROVIDER, RUN)).containsExactly("account-1");
    assertThat(accounts.identitiesOf(PROVIDER, OTHER_RUN)).containsExactly("account-2");
    assertThat(accounts.identitiesOf(OTHER_PROVIDER, RUN)).containsExactly("account-3");
  }

  @Test
  void merging_one_test_run_leaves_the_other_configured() {
    accounts.merge(PROVIDER, RUN, List.of("account-1"), List.of(), nothingOfItsOwn());
    accounts.merge(PROVIDER, OTHER_RUN, List.of("account-2"), List.of(), nothingOfItsOwn());

    accounts.merge(PROVIDER, RUN, List.of("account-3"), List.of(), nothingOfItsOwn());

    assertThat(accounts.identitiesOf(PROVIDER, RUN)).containsExactly("account-1", "account-3");
    assertThat(accounts.identitiesOf(PROVIDER, OTHER_RUN)).containsExactly("account-2");
  }

  @Test
  void an_audit_body_keeps_every_value_of_a_field_that_was_sent_twice() {
    audit.record(
        record(
            "aud_multi",
            Map.of("Accept", List.of("application/json", "text/html")),
            Map.of("sum", List.of("120.50", "1.00"), "pageCode", List.of("[redacted]"))));

    AuditRecord stored = audit.of(PROVIDER, RUN).get(0);
    assertThat(stored.sanitizedBody()).containsEntry("sum", List.of("120.50", "1.00"));
    assertThat(stored.sanitizedHeaders())
        .containsEntry("Accept", List.of("application/json", "text/html"));
  }

  @Test
  void an_audit_record_survives_a_round_trip_whole() {
    audit.record(record("aud_whole", Map.of(), Map.of()));

    AuditRecord stored = audit.of(PROVIDER, RUN).get(0);
    assertThat(stored.auditId()).isEqualTo("aud_whole");
    assertThat(stored.testRunId()).isEqualTo(RUN);
    assertThat(stored.resolutionRule()).isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
    assertThat(stored.provider()).isEqualTo(PROVIDER);
    assertThat(stored.direction()).isEqualTo(AuditDirection.OUTBOUND_RESPONSE);
    assertThat(stored.outcome()).isEqualTo(ProviderOutcome.PROCESSING);
    assertThat(stored.method()).isEqualTo("POST");
    assertThat(stored.path()).isEqualTo("/api/light/server/1.0/createPaymentProcess");
    assertThat(stored.httpStatus()).isEqualTo(200);
    assertThat(stored.providerObjectId()).isEqualTo("734754");
    assertThat(stored.scenarioId()).isNull();
    assertThat(stored.attempt()).isEqualTo(1);
    assertThat(stored.createdAt()).isEqualTo(PINNED);
  }

  @Test
  void records_sharing_one_pinned_timestamp_are_read_back_in_arrival_order() {
    audit.record(record("aud_1", Map.of(), Map.of()));
    audit.record(record("aud_2", Map.of(), Map.of()));
    audit.record(record("aud_3", Map.of(), Map.of()));

    assertThat(audit.of(PROVIDER, RUN))
        .extracting(AuditRecord::auditId)
        .containsExactly("aud_1", "aud_2", "aud_3");
  }

  @Test
  void the_newest_exchanges_of_a_provider_are_read_back_newest_first_and_no_more_than_asked_for() {
    audit.record(record("aud_oldest", Map.of(), Map.of()));
    audit.record(record("aud_middle", Map.of(), Map.of()));
    audit.record(record("aud_newest", Map.of(), Map.of()));

    assertThat(auditReads.latestOf(PROVIDER, RUN, null, 2))
        .extracting(AuditRecord::auditId)
        .containsExactly("aud_newest", "aud_middle");
  }

  @Test
  void the_newest_exchanges_of_one_test_run_never_reach_back_into_another_run_or_provider() {
    audit.record(record("aud_here", Map.of(), Map.of()));
    audit.record(recordIn(OTHER_RUN, "aud_elsewhere"));

    assertThat(auditReads.latestOf(PROVIDER, RUN, null, 10))
        .extracting(AuditRecord::auditId)
        .containsExactly("aud_here");
    assertThat(auditReads.latestOf(OTHER_PROVIDER, RUN, null, 10)).isEmpty();
  }

  @Test
  void a_read_of_the_trail_that_bounds_itself_by_nothing_is_refused_rather_than_answered() {
    assertThatThrownBy(() -> auditReads.latestOf(PROVIDER, RUN, null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit must be between 1 and 500");
    assertThatThrownBy(() -> auditReads.latestOf(PROVIDER, RUN, null, 501))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void every_registered_test_run_is_listed_beside_the_one_a_request_naming_none_belongs_to() {
    testRuns.register(RUN);
    testRuns.register(OTHER_RUN);

    List<TestRunId> listed = testRuns.all();

    assertThat(listed).contains(RUN, OTHER_RUN, TestRunId.of("default"));
    assertThat(listed).doesNotHaveDuplicates();
    assertThat(listed.get(0)).isEqualTo(TestRunId.of("default"));
  }

  @Test
  void clearing_one_test_run_reports_what_it_removed_and_leaves_the_others() {
    audit.record(record("aud_a", Map.of(), Map.of()));
    audit.record(record("aud_b", Map.of(), Map.of()));
    audit.record(recordIn(OTHER_RUN, "aud_c"));

    assertThat(audit.clear(PROVIDER, RUN)).isEqualTo(2);
    assertThat(audit.of(PROVIDER, RUN)).isEmpty();
    assertThat(audit.of(PROVIDER, OTHER_RUN)).hasSize(1);
  }

  @Test
  void a_delivery_survives_a_round_trip_with_its_bytes_its_headers_and_its_plan() {
    deliveries.add(delivery("whjob_whole", DeliveryState.PENDING, 0, PINNED));

    WebhookDelivery stored = deliveries.find(PROVIDER, RUN, "whjob_whole").orElseThrow();
    assertThat(stored.payload().contentType()).isEqualTo("application/x-www-form-urlencoded");
    assertThat(new String(stored.payload().body(), StandardCharsets.UTF_8)).isEqualTo("status=1");
    assertThat(stored.payload().wireHeaders())
        .containsEntry("Content-Type", List.of("application/x-www-form-urlencoded"))
        .containsEntry("X-GCS-Signature", List.of("c2lnbmF0dXJl"))
        .containsEntry("X-GCS-KeyId", List.of("key-1"));
    assertThat(stored.payload().sanitizedBody()).containsEntry("status", List.of("1"));
    assertThat(stored.retryPlan().delays())
        .containsExactly(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5));
    assertThat(stored.eventId()).isEqualTo("evt_whole");
  }

  @Test
  void the_reason_a_delivery_was_not_sent_is_read_back_and_a_later_attempt_clears_it() {
    deliveries.add(delivery("whjob_unsent", DeliveryState.PENDING, 0, PINNED));
    WebhookDelivery read = deliveries.find(PROVIDER, RUN, "whjob_unsent").orElseThrow();
    assertThat(read.notSentBecause()).isNull();

    assertThat(deliveries.claim(notSent(read, "the account it belongs to is gone"), 0)).isTrue();

    WebhookDelivery withheld = deliveries.find(PROVIDER, RUN, "whjob_unsent").orElseThrow();
    assertThat(withheld.state()).isEqualTo(DeliveryState.UNDELIVERABLE);
    assertThat(withheld.notSentBecause()).isEqualTo("the account it belongs to is gone");

    assertThat(deliveries.reclaim(refused(withheld, 1), 0)).isTrue();

    WebhookDelivery attempted = deliveries.find(PROVIDER, RUN, "whjob_unsent").orElseThrow();
    assertThat(attempted.state()).isEqualTo(DeliveryState.PENDING);
    assertThat(attempted.attemptsMade()).isEqualTo(1);
    assertThat(attempted.notSentBecause()).isNull();
  }

  private static WebhookDelivery notSent(WebhookDelivery delivery, String reason) {
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
        DeliveryState.UNDELIVERABLE,
        delivery.acknowledgementOwed(),
        delivery.attemptsMade(),
        null,
        delivery.createdAt(),
        PINNED,
        delivery.shapedBy(),
        reason);
  }

  @Test
  void only_one_caller_can_claim_an_attempt_at_one_delivery() {
    deliveries.add(delivery("whjob_claim", DeliveryState.PENDING, 0, PINNED));
    WebhookDelivery read = deliveries.find(PROVIDER, RUN, "whjob_claim").orElseThrow();

    assertThat(deliveries.claim(refused(read, 1), 0)).isTrue();
    assertThat(deliveries.claim(refused(read, 1), 0)).isFalse();

    WebhookDelivery afterwards = deliveries.find(PROVIDER, RUN, "whjob_claim").orElseThrow();
    assertThat(afterwards.attemptsMade()).isEqualTo(1);
  }

  @Test
  void a_retry_claims_an_exhausted_delivery_and_only_once() {
    deliveries.add(delivery("whjob_retry", DeliveryState.EXHAUSTED, 3, null));
    WebhookDelivery read = deliveries.find(PROVIDER, RUN, "whjob_retry").orElseThrow();

    assertThat(deliveries.claim(refused(read, 4), 3)).isFalse();
    assertThat(deliveries.reclaim(refused(read, 4), 3)).isTrue();
    assertThat(deliveries.reclaim(refused(read, 4), 3)).isFalse();

    assertThat(deliveries.find(PROVIDER, RUN, "whjob_retry").orElseThrow().attemptsMade())
        .isEqualTo(4);
  }

  @Test
  void an_attempt_that_got_no_answer_keeps_its_transport_failure_and_no_status() {
    deliveries.add(delivery("whjob_failure", DeliveryState.PENDING, 1, PINNED));
    deliveries.recordAttempt(
        new WebhookAttempt(
            "whjob_failure",
            1,
            PINNED,
            "https://merchant.example/hook",
            Map.of("Content-Type", List.of("application/json")),
            Acknowledgement.REFUSED,
            null,
            TransportFailure.READ_TIMEOUT,
            null));
    deliveries.recordAttempt(
        new WebhookAttempt(
            "whjob_failure",
            2,
            PINNED,
            "https://merchant.example/hook",
            Map.of("Content-Type", List.of("application/json")),
            Acknowledgement.ACKNOWLEDGED,
            204,
            null,
            ""));

    assertThat(deliveries.attemptsOf("whjob_failure"))
        .extracting(
            WebhookAttempt::number,
            WebhookAttempt::httpStatus,
            WebhookAttempt::failure,
            WebhookAttempt::responsePreview)
        .containsExactly(
            tuple(1, null, TransportFailure.READ_TIMEOUT, null), tuple(2, 204, null, ""));
  }

  @Test
  void an_attempt_stored_before_the_wire_was_recorded_reads_apart_from_one_that_recorded_it() {
    deliveries.add(delivery("whjob_wire", DeliveryState.PENDING, 2, PINNED));
    deliveries.recordAttempt(
        new WebhookAttempt(
            "whjob_wire",
            1,
            PINNED,
            "https://merchant.example/hook",
            Map.of("Content-Type", List.of("application/x-www-form-urlencoded")),
            Acknowledgement.ACKNOWLEDGED,
            204,
            null,
            ""));
    jdbc.sql(
            "insert into simulator_webhook_delivery_attempt (delivery_id, attempt_number,"
                + " attempted_at, target_url, sent_headers, verdict, http_status,"
                + " transport_failure, response_preview) values ('whjob_wire', 2, ?, ?, null,"
                + " 'ACKNOWLEDGED', 204, null, '')")
        .param(PINNED.atOffset(ZoneOffset.UTC))
        .param("https://merchant.example/hook")
        .update();

    assertThat(deliveries.attemptsOf("whjob_wire"))
        .extracting(WebhookAttempt::theWireWasRecorded, WebhookAttempt::contentTypeSent)
        .containsExactly(
            tuple(true, "application/x-www-form-urlencoded"), tuple(false, (String) null));
  }

  @Test
  void a_delivery_due_at_no_time_is_not_a_candidate_the_queue_offers() {
    deliveries.add(delivery("whjob_due", DeliveryState.PENDING, 0, PINNED));
    deliveries.add(delivery("whjob_held", DeliveryState.PENDING, 1, null));
    deliveries.add(delivery("whjob_done", DeliveryState.DELIVERED, 1, null));

    assertThat(deliveries.pending(PROVIDER, RUN))
        .extracting(PendingDelivery::deliveryId)
        .containsExactly("whjob_due");
    assertThat(deliveries.pendingEverywhere())
        .extracting(PendingDelivery::deliveryId)
        .contains("whjob_due")
        .doesNotContain("whjob_held", "whjob_done");
  }

  @Test
  void clearing_deliveries_takes_their_attempts_with_them() {
    deliveries.add(delivery("whjob_cleared", DeliveryState.EXHAUSTED, 1, null));
    deliveries.add(deliveryIn(OTHER_RUN, "whjob_kept"));
    deliveries.recordAttempt(
        new WebhookAttempt(
            "whjob_cleared",
            1,
            PINNED,
            "https://merchant.example/hook",
            Map.of("Content-Type", List.of("application/json")),
            Acknowledgement.REFUSED,
            500,
            null,
            "no thanks"));

    assertThat(deliveries.clear(PROVIDER, RUN)).isEqualTo(1);

    assertThat(deliveries.attemptsOf("whjob_cleared")).isEmpty();
    assertThat(deliveries.of(PROVIDER, RUN)).isEmpty();
    assertThat(deliveries.of(PROVIDER, OTHER_RUN)).hasSize(1);
    assertThat(deliveries.clearAll(PROVIDER)).isEqualTo(1);
  }

  @Test
  void
      the_callbacks_owed_an_acknowledgement_are_those_naming_any_identifier_the_object_answers_to() {
    deliveries.add(owedFor("whjob_first", "734754"));
    deliveries.add(owedFor("whjob_renamed", "734755"));
    deliveries.add(owedFor("whjob_other", "999999"));

    assertThat(deliveries.owedAnAcknowledgement(PROVIDER, RUN, List.of("734754", "734755")))
        .containsExactly("whjob_first", "whjob_renamed");
    assertThat(deliveries.owedAnAcknowledgement(PROVIDER, RUN, List.of("734754")))
        .containsExactly("whjob_first");
    assertThat(deliveries.owedAnAcknowledgement(PROVIDER, RUN, List.of())).isEmpty();
  }

  private static WebhookDelivery owedFor(String deliveryId, String providerObjectId) {
    WebhookDelivery pending = delivery(deliveryId, DeliveryState.PENDING, 0, PINNED);
    return new WebhookDelivery(
        pending.deliveryId(),
        pending.provider(),
        pending.testRunId(),
        pending.merchantId(),
        pending.eventId(),
        pending.eventType(),
        providerObjectId,
        pending.targetUrl(),
        pending.payload(),
        pending.retryPlan(),
        pending.state(),
        true,
        pending.attemptsMade(),
        pending.nextAttemptAt(),
        pending.createdAt(),
        pending.updatedAt(),
        pending.shapedBy(),
        pending.notSentBecause());
  }

  private static WebhookDelivery delivery(
      String deliveryId, DeliveryState state, int attemptsMade, @Nullable Instant nextAttemptAt) {
    return delivery(RUN, deliveryId, state, attemptsMade, nextAttemptAt);
  }

  private static WebhookDelivery deliveryIn(TestRunId testRunId, String deliveryId) {
    return delivery(testRunId, deliveryId, DeliveryState.PENDING, 0, PINNED);
  }

  private static WebhookDelivery delivery(
      TestRunId testRunId,
      String deliveryId,
      DeliveryState state,
      int attemptsMade,
      @Nullable Instant nextAttemptAt) {
    return new WebhookDelivery(
        deliveryId,
        PROVIDER,
        testRunId,
        "merchant-1",
        "evt_whole",
        "payment_process.completed",
        "734754",
        "https://merchant.example/hook",
        new WebhookPayload(
            "application/x-www-form-urlencoded",
            Map.of("X-GCS-Signature", List.of("c2lnbmF0dXJl"), "X-GCS-KeyId", List.of("key-1")),
            "status=1".getBytes(StandardCharsets.UTF_8),
            Map.of("status", List.of("1"))),
        new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(5))),
        state,
        false,
        attemptsMade,
        nextAttemptAt,
        PINNED,
        PINNED,
        null,
        null);
  }

  private static WebhookDelivery refused(WebhookDelivery delivery, int attemptsMade) {
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
        attemptsMade,
        PINNED.plusSeconds(1),
        delivery.createdAt(),
        PINNED,
        null,
        null);
  }

  private static ProviderConfigurationPlan nothingOfItsOwn() {
    return new ProviderConfigurationPlan(Set.of(), List.of(), () -> {});
  }

  private static AuditRecord record(
      String auditId, Map<String, List<String>> headers, Map<String, List<String>> body) {
    return new AuditRecord(
        auditId,
        RUN,
        TestRunResolutionRule.ACCOUNT_CREDENTIALS,
        PROVIDER,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.PROCESSING,
        "POST",
        "/api/light/server/1.0/createPaymentProcess",
        200,
        "734754",
        null,
        null,
        1,
        PINNED,
        headers,
        body,
        new DeliveredResponse(
            new AppliedFault(TransportFaultKind.TRUNCATED_BODY, FaultPlacement.AFTER_THE_OPERATION),
            120,
            60,
            Duration.ofMillis(250)));
  }

  private static AuditRecord recordIn(TestRunId testRunId, String auditId) {
    AuditRecord template = record(auditId, Map.of(), Map.of());
    return new AuditRecord(
        template.auditId(),
        testRunId,
        template.resolutionRule(),
        template.provider(),
        null,
        template.origin(),
        template.direction(),
        template.outcome(),
        template.method(),
        template.path(),
        template.httpStatus(),
        template.providerObjectId(),
        template.refusal(),
        template.scenarioId(),
        template.attempt(),
        template.createdAt(),
        template.sanitizedHeaders(),
        template.sanitizedBody(),
        template.delivered());
  }
}
