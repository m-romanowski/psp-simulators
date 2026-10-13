package dev.marcinromanowski.pspsim.ui;

import static dev.marcinromanowski.pspsim.TheObjectsAnObjectNames.theObjectsNamed;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.money.OperationKind;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.AKindOfObjectATestNames;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.spi.TestKeyClaims;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.kernel.webhook.RetryPlan;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class TimelineScreenTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");
  private static final TestRunId RUN = TestRunId.of("run-timeline");
  private static final Instant PINNED = Instant.parse("2026-09-07T12:00:00Z");
  private static final String CHARGE = "txn_1";
  private static final String PROCESS = "prc_1";
  private static final String RENAMED = "txn_2";
  private static final String CHARGE_PATH = "/Api/Api/Payment/CreatePaymentProcess";

  @Test
  void a_blank_key_is_the_same_as_none_so_the_screen_asks_rather_than_reporting_nothing_found() {
    assertThat(TimelineScreen.keyAsked("   ")).isNull();
    assertThat(TimelineScreen.keyAsked(null)).isNull();
    assertThat(TimelineScreen.keyAsked("  txn_1 ")).isEqualTo(CHARGE);
  }

  @Test
  void a_key_that_is_a_provider_object_id_traces_that_object_before_any_other_store_is_searched() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            CHARGE,
            AddressedProviderObject.canonically(anObject(CHARGE, Map.of())),
            List.of(anObject(PROCESS, Map.of("transactionId", CHARGE))),
            List.of(),
            KeyedExchangePage.empty());

    assertThat(subject)
        .isEqualTo(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"));
  }

  @Test
  void
      a_key_no_object_is_addressed_by_traces_the_one_object_that_names_it_and_says_by_which_field() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            "order-42",
            null,
            List.of(anObject(CHARGE, theObjectsNamed("merchantId", "m1", "order", "order-42"))),
            List.of(),
            KeyedExchangePage.empty());

    assertThat(subject)
        .isEqualTo(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.RELATED_FIELD, "order"));
  }

  @Test
  void a_key_two_objects_of_the_run_name_picks_out_no_payment_and_is_answered_with_both() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            "order-42",
            null,
            List.of(
                anObject(CHARGE, Map.of("order", "order-42")),
                anObject("txn_2", Map.of("order", "order-42"))),
            List.of(),
            KeyedExchangePage.empty());

    assertThat(subject)
        .isEqualTo(
            new TimelineScreen.Subject.Ambiguous(
                List.of(
                    new TimelineScreen.Candidate(CHARGE, "order"),
                    new TimelineScreen.Candidate("txn_2", "order"))));
  }

  @Test
  void a_callback_event_id_traces_the_object_the_callback_reports_on() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            "evt_9",
            null,
            List.of(),
            List.of(aCallback("whjob_1", "evt_9", CHARGE, DeliveryState.DELIVERED, 1, null)),
            KeyedExchangePage.empty());

    assertThat(subject)
        .isEqualTo(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.CALLBACK_EVENT, "eventId"));
  }

  @Test
  void
      a_callback_event_id_of_a_callback_that_names_no_object_traces_nothing_rather_than_guessing() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            "evt_9",
            null,
            List.of(),
            List.of(aCallback("whjob_1", "evt_9", null, DeliveryState.PENDING, 0, PINNED)),
            KeyedExchangePage.empty());

    assertThat(subject)
        .isEqualTo(
            new TimelineScreen.Subject.CarriedButNamesNoObject("a callback of this test run"));
    assertThat(TimelineScreen.unresolvedBy(subject))
        .isEqualTo(
            TimelineScreen.CARRIED_BY_A_ROW_THAT_NAMES_NO_OBJECT + "a callback of this test run");
  }

  @Test
  void a_value_sent_under_a_key_this_provider_compares_repeats_against_traces_the_object_it_made() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            "uid-7",
            null,
            List.of(),
            List.of(),
            anExchangePageUnder("transactionUniqueIdentifier", "uid-7", CHARGE));

    assertThat(subject)
        .isEqualTo(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.KEYED_REQUEST, "transactionUniqueIdentifier"));
  }

  @Test
  void
      a_key_nothing_of_this_run_carries_is_reported_as_carried_by_nothing_and_not_as_an_empty_trail() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            "never-sent",
            null,
            List.of(anObject(CHARGE, Map.of("order", "order-42"))),
            List.of(aCallback("whjob_1", "evt_9", CHARGE, DeliveryState.DELIVERED, 1, null)),
            anExchangePageUnder("transactionUniqueIdentifier", "uid-7", CHARGE));

    assertThat(subject).isEqualTo(new TimelineScreen.Subject.NotFound());
  }

  @Test
  void a_screen_asked_for_no_key_at_all_traces_nothing_and_reports_no_failure_to_find_one() {
    assertThat(
            TimelineScreen.subjectOf(null, null, List.of(), List.of(), KeyedExchangePage.empty()))
        .isEqualTo(new TimelineScreen.Subject.NotAsked());
  }

  @Test
  void
      the_response_that_names_the_object_carries_the_request_that_asked_for_it_onto_the_timeline() {
    List<AuditRecord> trail =
        List.of(
            inbound("aud_1", "POST", CHARGE_PATH),
            outbound("aud_2", "POST", CHARGE_PATH, CHARGE, 200));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries).extracting(TimelineScreen.Entry::auditId).containsExactly("aud_1", "aud_2");
    assertThat(entries.get(0).kind()).isEqualTo("provider-compatible request received");
    assertThat(entries.get(0).pairedByPosition())
        .isEqualTo(TimelineScreen.A_REQUEST_IS_PAIRED_BY_POSITION);
    assertThat(entries.get(1).pairedByPosition()).isNull();
  }

  @Test
  void
      a_request_of_another_path_is_not_carried_onto_the_timeline_by_the_response_it_never_answered() {
    List<AuditRecord> trail =
        List.of(
            inbound("aud_1", "POST", "/Api/Api/Payment/GetTransactionInfo"),
            outbound("aud_2", "POST", CHARGE_PATH, CHARGE, 200));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries).extracting(TimelineScreen.Entry::auditId).containsExactly("aud_2");
  }

  @Test
  void
      two_calls_of_one_path_do_not_share_a_request_so_the_second_response_does_not_steal_the_first() {
    List<AuditRecord> trail =
        List.of(
            inbound("aud_1", "POST", CHARGE_PATH),
            outbound("aud_2", "POST", CHARGE_PATH, CHARGE, 200),
            inbound("aud_3", "POST", CHARGE_PATH),
            outbound("aud_4", "POST", CHARGE_PATH, CHARGE, 200));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries)
        .extracting(TimelineScreen.Entry::auditId)
        .containsExactly("aud_1", "aud_2", "aud_3", "aud_4");
  }

  @Test
  void a_request_answered_for_another_object_stays_off_this_object_timeline() {
    List<AuditRecord> trail =
        List.of(
            inbound("aud_1", "POST", CHARGE_PATH),
            outbound("aud_2", "POST", CHARGE_PATH, "txn_other", 200),
            inbound("aud_3", "POST", CHARGE_PATH),
            outbound("aud_4", "POST", CHARGE_PATH, CHARGE, 200));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries).extracting(TimelineScreen.Entry::auditId).containsExactly("aud_3", "aud_4");
  }

  @Test
  void the_timeline_keeps_the_order_the_trail_stored_and_not_the_order_of_the_pinned_clock() {
    List<AuditRecord> trail =
        List.of(
            inbound("aud_1", "POST", CHARGE_PATH),
            outbound("aud_2", "POST", CHARGE_PATH, CHARGE, 200),
            webhookAttempt("aud_3", CHARGE, 1),
            webhookAttempt("aud_4", CHARGE, 2));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries).extracting(TimelineScreen.Entry::at).containsOnly(PINNED);
    assertThat(entries).extracting(TimelineScreen.Entry::place).containsExactly(0, 1, 2, 3);
    assertThat(entries).extracting(TimelineScreen.Entry::attempt).containsExactly(1, 1, 1, 2);
  }

  @Test
  void a_fault_that_landed_after_the_operation_says_the_change_was_made_and_the_answer_was_lost() {
    List<AuditRecord> trail =
        List.of(
            faulted(
                "aud_1",
                CHARGE,
                new AppliedFault(
                    TransportFaultKind.DROPPED_CONNECTION, FaultPlacement.AFTER_THE_OPERATION)));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries.get(0).lostResponse())
        .isEqualTo(TimelineScreen.THE_OPERATION_RAN_AND_THE_ANSWER_DID_NOT_ARRIVE);
  }

  @Test
  void a_fault_that_replaced_the_operation_says_the_call_changed_nothing_before_it_broke() {
    List<AuditRecord> trail =
        List.of(
            faulted(
                "aud_1",
                CHARGE,
                new AppliedFault(
                    TransportFaultKind.DROPPED_CONNECTION,
                    FaultPlacement.INSTEAD_OF_THE_OPERATION)));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries.get(0).lostResponse())
        .isEqualTo(TimelineScreen.NOTHING_RAN_BEFORE_THE_FAULT);
  }

  @Test
  void a_response_that_reached_its_caller_whole_is_not_flagged_as_a_lost_answer() {
    assertThat(TimelineScreen.lostResponseOf(outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200)))
        .isNull();
    assertThat(TimelineScreen.lostResponseOf(faulted("aud_2", CHARGE, null))).isNull();
  }

  @Test
  void every_surface_that_can_write_a_row_is_named_on_the_timeline_by_the_surface_that_acted() {
    assertThat(TimelineScreen.kindOf(action("aud_1", ObjectOrigin.UI)))
        .isEqualTo("manual action on this simulator's own screens");
    assertThat(TimelineScreen.kindOf(action("aud_2", ObjectOrigin.CONTROL_API)))
        .isEqualTo("control API action");
    assertThat(TimelineScreen.kindOf(action("aud_3", ObjectOrigin.SCHEDULED_JOB)))
        .isEqualTo("action of this simulator's own scheduler");
    assertThat(TimelineScreen.kindOf(inbound("aud_4", "POST", CHARGE_PATH)))
        .isEqualTo("provider-compatible request received");
    assertThat(TimelineScreen.kindOf(outbound("aud_5", "POST", CHARGE_PATH, CHARGE, 200)))
        .isEqualTo("provider-compatible response sent");
    assertThat(TimelineScreen.kindOf(webhookAttempt("aud_6", CHARGE, 1)))
        .isEqualTo("callback delivery attempt");
    assertThat(TimelineScreen.kindOf(webhookRow("aud_7", AuditDirection.OUTBOUND_WEBHOOK_DROPPED)))
        .isEqualTo("callback withheld by a scenario rule");
    assertThat(TimelineScreen.kindOf(webhookRow("aud_8", AuditDirection.OUTBOUND_WEBHOOK_NOT_SENT)))
        .isEqualTo("callback this provider would not send");
  }

  @Test
  void only_the_entry_a_tester_opened_puts_the_fields_of_its_exchange_on_the_page() {
    List<AuditRecord> trail =
        List.of(
            outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200),
            outbound("aud_2", "POST", CHARGE_PATH, CHARGE, 200));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, "aud_2", false);

    assertThat(entries.get(0).body()).isEmpty();
    assertThat(entries.get(0).headers()).isEmpty();
    assertThat(entries.get(1).body()).isNotEmpty();
    assertThat(entries.get(1).headers()).isNotEmpty();
    assertThat(entries.get(1).open()).isTrue();
  }

  @Test
  void a_value_redacted_where_it_was_stored_is_never_put_back_on_the_page_by_asking_for_values() {
    List<AuditRecord> trail = List.of(outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200));

    List<TimelineScreen.Entry> shown =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, "aud_1", true);

    assertThat(shown.get(0).body())
        .filteredOn(field -> "client_secret".equals(field.name()))
        .singleElement()
        .satisfies(
            field -> {
              assertThat(field.visibility()).isEqualTo(Disclosure.Visibility.REDACTED_ON_WRITE);
              assertThat(field.values()).isEmpty();
            });
  }

  @Test
  void a_callback_of_another_object_stays_off_this_object_list_of_callbacks() {
    List<TimelineScreen.Callback> owned =
        TimelineScreen.theCallbacksOf(
            List.of(CHARGE),
            List.of(
                aCallback("whjob_1", "evt_1", CHARGE, DeliveryState.PENDING, 1, PINNED),
                aCallback("whjob_2", "evt_2", "txn_other", DeliveryState.DELIVERED, 1, null)));

    assertThat(owned).extracting(TimelineScreen.Callback::deliveryId).containsExactly("whjob_1");
  }

  @Test
  void
      a_callback_still_owed_an_attempt_shows_the_attempts_made_the_cap_and_when_the_next_one_is_due() {
    List<TimelineScreen.Callback> owned =
        TimelineScreen.theCallbacksOf(
            List.of(CHARGE),
            List.of(aCallback("whjob_1", "evt_1", CHARGE, DeliveryState.PENDING, 2, PINNED)));

    TimelineScreen.Callback only = owned.get(0);
    assertThat(only.attemptsMade()).isEqualTo(2);
    assertThat(only.attemptCap()).isEqualTo(3);
    assertThat(only.nextAttemptAt()).isEqualTo(PINNED);
  }

  @Test
  void a_fact_this_provider_never_carries_is_named_on_the_screen_in_this_provider_own_words() {
    List<TimelineScreen.Absent> absent =
        TimelineScreen.theProviderHasNoSuchThing(aVocabularyWithoutAnOrderId(), growLikeKeys());

    assertThat(absent)
        .extracting(TimelineScreen.Absent::what)
        .contains("match.orderId", "true idempotency key");
    assertThat(absent)
        .filteredOn(named -> "match.orderId".equals(named.what()))
        .singleElement()
        .extracting(TimelineScreen.Absent::why)
        .isEqualTo("provider grow publishes no order identifier on any endpoint of this contract");
  }

  @Test
  void a_fact_this_provider_does_carry_is_not_listed_among_the_things_the_screen_cannot_show() {
    List<TimelineScreen.Absent> absent =
        TimelineScreen.theProviderHasNoSuchThing(aVocabularyWithoutAnOrderId(), growLikeKeys());

    assertThat(absent)
        .extracting(TimelineScreen.Absent::what)
        .contains("match.orderId", "true idempotency key")
        .doesNotContain("match.providerObjectId", "duplicate-detection key", "correlation key");
  }

  @Test
  void an_event_this_build_records_nowhere_is_named_on_the_screen_rather_than_left_as_a_gap() {
    List<TimelineScreen.Absent> absent =
        TimelineScreen.thisBuildRecordsNoSuchThing(KeyedExchangePage.empty());

    assertThat(absent)
        .extracting(TimelineScreen.Absent::what)
        .containsExactly(
            "a callback that was scheduled",
            "a state transition of its own",
            "who took a manual action",
            "the delivery an attempt on the trail belongs to",
            "a replay or a conflict under a key");
  }

  @Test
  void a_run_that_recorded_a_keyed_request_is_told_the_request_has_no_place_on_the_trail() {
    List<TimelineScreen.Absent> recorded =
        TimelineScreen.thisBuildRecordsNoSuchThing(
            anExchangePageUnder("transactionUniqueIdentifier", "uid-7", CHARGE));

    assertThat(recorded)
        .filteredOn(named -> "a replay or a conflict under a key".equals(named.what()))
        .singleElement()
        .extracting(TimelineScreen.Absent::why)
        .isEqualTo(TimelineScreen.NO_KEYED_REQUEST_NAMES_ITS_TRAIL_ROW);
  }

  @Test
  void a_run_that_recorded_no_keyed_request_is_not_told_a_recorded_one_is_missing_a_link() {
    List<TimelineScreen.Absent> none =
        TimelineScreen.thisBuildRecordsNoSuchThing(KeyedExchangePage.empty());

    assertThat(none)
        .filteredOn(named -> "a replay or a conflict under a key".equals(named.what()))
        .singleElement()
        .extracting(TimelineScreen.Absent::why)
        .isEqualTo(TimelineScreen.NO_KEYED_REQUEST_IS_RECORDED_FOR_THIS_RUN);
  }

  @Test
  void a_traced_object_reports_the_provider_native_status_and_the_normalized_status_together() {
    TimelineScreen.View view =
        viewOf(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"),
            aCapturedCharge(),
            List.of(outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200)),
            false);

    assertThat(view.nativeStatus()).isEqualTo("3");
    assertThat(view.normalizedStatus())
        .isEqualTo(new StatusBadge("SUCCEEDED", StatusBadge.Tone.SUCCESS));
    assertThat(view.namesAnObject()).isTrue();
    assertThat(view.foundBy()).isEqualTo("the id the provider answered with, providerObjectId");
  }

  @Test
  void the_objects_a_traced_object_names_are_on_the_page_so_a_tester_can_trace_one_of_them_next() {
    TimelineScreen.View view =
        viewOf(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"),
            aCapturedCharge(),
            List.of(),
            false);

    assertThat(view.relatedObjects()).containsEntry("processId", PROCESS);
  }

  @Test
  void the_objects_a_traced_object_names_are_on_the_page_in_the_order_the_provider_named_them() {
    Map<String, String> names = new LinkedHashMap<>();
    names.put("merchantId", "m1");
    names.put("processId", PROCESS);
    names.put("transactionType", "CREDIT");
    names.put("orderId", "pedido-1");
    TimelineScreen.View view =
        viewOf(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"),
            anObject(CHARGE, names),
            List.of(),
            false);

    assertThat(view.relatedObjects().keySet())
        .as(
            "a read, a screen and a control answer all print the objects an object names in the"
                + " order the provider named them, and this page and the object's own screen are"
                + " two screens a tester moves between on one list")
        .containsExactly("merchantId", "processId", "transactionType", "orderId");
  }

  @Test
  void every_way_a_key_can_be_found_is_described_on_the_page_in_words_a_tester_can_read() {
    assertThat(TimelineScreen.Found.PROVIDER_OBJECT_ID.describedAs())
        .isEqualTo("the id the provider answered with");
    assertThat(TimelineScreen.Found.RELATED_FIELD.describedAs())
        .isEqualTo("a field an object of this run names another object by");
    assertThat(TimelineScreen.Found.CALLBACK_EVENT.describedAs()).isEqualTo("a callback event id");
    assertThat(TimelineScreen.Found.KEYED_REQUEST.describedAs())
        .isEqualTo("a value sent under a key this provider compares repeats against");
  }

  @Test
  void the_money_a_traced_object_moved_is_shown_in_the_order_its_ledger_numbered_the_operations() {
    TimelineScreen.View view =
        viewOf(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"),
            aCapturedCharge(),
            List.of(),
            false);

    assertThat(view.transitions())
        .extracting(TimelineScreen.Transition::sequence)
        .containsExactly(1, 2);
    assertThat(view.transitions())
        .extracting(TimelineScreen.Transition::kind)
        .containsExactly(OperationKind.CAPTURE.name(), OperationKind.REFUND.name());
  }

  @Test
  void a_key_that_traced_nothing_puts_no_timeline_on_the_page_and_says_nothing_carries_it() {
    TimelineScreen.View view =
        viewOf(
            new TimelineScreen.Subject.NotFound(),
            null,
            List.of(outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200)),
            false);

    assertThat(view.unresolved()).isEqualTo(TimelineScreen.NOTHING_OF_THIS_RUN_CARRIES_IT);
    assertThat(view.namesAnObject()).isFalse();
    assertThat(view.entries()).isEmpty();
    assertThat(view.callbacks()).isEmpty();
    assertThat(view.transitions()).isEmpty();
    assertThat(view.foundBy()).isNull();
  }

  @Test
  void a_key_more_than_one_object_carries_puts_both_on_the_page_and_no_timeline_of_either() {
    TimelineScreen.View view =
        viewOf(
            new TimelineScreen.Subject.Ambiguous(
                List.of(
                    new TimelineScreen.Candidate(CHARGE, "order"),
                    new TimelineScreen.Candidate("txn_2", "order"))),
            null,
            List.of(outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200)),
            false);

    assertThat(view.candidates()).hasSize(2);
    assertThat(view.entries()).isEmpty();
    assertThat(view.unresolved()).isNull();
  }

  @Test
  void a_trail_longer_than_one_timeline_read_says_so_rather_than_reading_as_the_end_of_the_story() {
    TimelineScreen.View cut =
        viewOf(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"),
            aCapturedCharge(),
            List.of(outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200)),
            true);
    TimelineScreen.View whole =
        viewOf(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"),
            aCapturedCharge(),
            List.of(outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200)),
            false);

    assertThat(cut.truncated())
        .isEqualTo(TimelineScreen.THE_TRAIL_IS_LONGER_THAN_THIS_TIMELINE_READ);
    assertThat(whole.truncated()).isNull();
  }

  @Test
  void the_first_store_that_carries_the_key_decides_and_a_later_store_does_not_overrule_it() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            "shared-value",
            null,
            List.of(),
            List.of(aCallback("whjob_1", "shared-value", null, DeliveryState.PENDING, 0, PINNED)),
            anExchangePageUnder("transactionUniqueIdentifier", "shared-value", CHARGE));

    assertThat(subject)
        .isEqualTo(
            new TimelineScreen.Subject.CarriedButNamesNoObject("a callback of this test run"));
  }

  @Test
  void a_keyed_request_that_named_no_object_traces_nothing_rather_than_guessing_at_one() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            "uid-7",
            null,
            List.of(),
            List.of(),
            anExchangePageUnder("transactionUniqueIdentifier", "uid-7", null));

    assertThat(subject)
        .isEqualTo(
            new TimelineScreen.Subject.CarriedButNamesNoObject(
                "a request recorded under key transactionUniqueIdentifier"));
    assertThat(TimelineScreen.unresolvedBy(subject))
        .isEqualTo(
            TimelineScreen.CARRIED_BY_A_ROW_THAT_NAMES_NO_OBJECT
                + "a request recorded under key transactionUniqueIdentifier");
  }

  @Test
  void a_response_whose_request_the_trail_no_longer_holds_is_not_labelled_as_paired_with_itself() {
    List<AuditRecord> trail = List.of(outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries).extracting(TimelineScreen.Entry::auditId).containsExactly("aud_1");
    assertThat(entries.get(0).pairedByPosition()).isNull();
  }

  @Test
  void a_response_does_not_reach_back_past_the_exchange_before_it_to_claim_that_exchange_request() {
    List<AuditRecord> trail =
        List.of(
            inbound("aud_1", "POST", CHARGE_PATH),
            outbound("aud_2", "POST", CHARGE_PATH, "txn_other", 200),
            outbound("aud_3", "POST", CHARGE_PATH, CHARGE, 200));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries).extracting(TimelineScreen.Entry::auditId).containsExactly("aud_3");
    assertThat(entries.get(0).pairedByPosition()).isNull();
  }

  @Test
  void the_callbacks_a_traced_object_owns_are_on_its_page_beside_the_calls_that_produced_them() {
    TimelineScreen.View view =
        TimelineScreen.of(
            PROVIDER.value(),
            RUN.value(),
            CHARGE,
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"),
            aCapturedCharge(),
            List.of(),
            false,
            List.of(aCallback("whjob_1", "evt_1", CHARGE, DeliveryState.PENDING, 1, PINNED)),
            KeyedExchangePage.empty(),
            aVocabularyWithoutAnOrderId(),
            growLikeKeys(),
            null,
            false);

    assertThat(view.callbacks())
        .extracting(TimelineScreen.Callback::deliveryId)
        .containsExactly("whjob_1");
  }

  @Test
  void a_callback_attempt_is_never_read_as_the_request_half_of_a_provider_exchange() {
    List<AuditRecord> trail =
        List.of(
            anAttemptOnATargetSpeltAsTheProviderRoute("aud_1", "txn_other"),
            outbound("aud_2", "POST", CHARGE_PATH, CHARGE, 200));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false);

    assertThat(entries).extracting(TimelineScreen.Entry::auditId).containsExactly("aud_2");
  }

  @Test
  void
      a_key_that_traced_a_payment_and_a_screen_asked_for_no_key_both_report_no_failure_to_find_one() {
    assertThat(TimelineScreen.unresolvedBy(new TimelineScreen.Subject.NotAsked())).isNull();
    assertThat(
            TimelineScreen.unresolvedBy(
                new TimelineScreen.Subject.Resolved(
                    CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId")))
        .isNull();
    assertThat(
            TimelineScreen.unresolvedBy(
                new TimelineScreen.Subject.Ambiguous(
                    List.of(new TimelineScreen.Candidate(CHARGE, "order")))))
        .isNull();
    assertThat(TimelineScreen.unresolvedBy(new TimelineScreen.Subject.NotFound()))
        .isEqualTo(TimelineScreen.NOTHING_OF_THIS_RUN_CARRIES_IT);
  }

  @Test
  void a_timeline_of_a_renamed_object_holds_the_rows_carrying_either_identifier_it_answered_to() {
    List<AuditRecord> trail =
        List.of(
            outbound("aud_1", "POST", CHARGE_PATH, CHARGE, 200),
            outbound("aud_2", "POST", CHARGE_PATH, RENAMED, 200),
            outbound("aud_3", "POST", CHARGE_PATH, PROCESS, 200));

    List<TimelineScreen.Entry> entries =
        TimelineScreen.theTimelineOf(List.of(CHARGE, RENAMED), trail, null, false);

    assertThat(entries).extracting(TimelineScreen.Entry::auditId).containsExactly("aud_1", "aud_2");
  }

  @Test
  void the_callbacks_of_a_renamed_object_hold_the_ones_sent_under_either_identifier() {
    List<WebhookDelivery> callbacks =
        List.of(
            aCallback("dlv_1", "evt_1", CHARGE, DeliveryState.DELIVERED, 1, null),
            aCallback("dlv_2", "evt_2", RENAMED, DeliveryState.DELIVERED, 1, null),
            aCallback("dlv_3", "evt_3", PROCESS, DeliveryState.DELIVERED, 1, null));

    assertThat(TimelineScreen.theCallbacksOf(List.of(CHARGE, RENAMED), callbacks))
        .extracting(TimelineScreen.Callback::deliveryId)
        .containsExactly("dlv_1", "dlv_2");
  }

  @Test
  void a_row_that_names_no_object_at_all_belongs_to_no_object_s_timeline() {
    List<AuditRecord> trail = List.of(inbound("aud_1", "POST", CHARGE_PATH));

    assertThat(TimelineScreen.theTimelineOf(List.of(CHARGE), trail, null, false)).isEmpty();
    assertThat(TimelineScreen.namesOneOf(List.of(CHARGE), null)).isFalse();
  }

  @Test
  void a_timeline_says_which_identifiers_the_object_it_opened_answers_to() {
    TimelineScreen.View view =
        viewOf(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"),
            aRenamedCharge(),
            List.of(),
            false);

    assertThat(view.answersTo()).containsExactly(CHARGE, RENAMED);
    assertThat(view.answersToMoreThanOneIdentifier()).isTrue();
  }

  @Test
  void a_timeline_of_an_object_that_kept_one_identifier_names_that_identifier_alone() {
    TimelineScreen.View view =
        viewOf(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"),
            aCapturedCharge(),
            List.of(),
            false);

    assertThat(view.answersTo()).containsExactly(CHARGE);
    assertThat(view.answersToMoreThanOneIdentifier()).isFalse();
  }

  @Test
  void a_timeline_that_resolved_no_object_names_no_identifier_it_answers_to() {
    TimelineScreen.View view =
        viewOf(new TimelineScreen.Subject.NotFound(), null, List.of(), false);

    assertThat(view.answersTo()).isEmpty();
    assertThat(view.answersToMoreThanOneIdentifier()).isFalse();
  }

  @Test
  void a_timeline_of_an_object_this_run_no_longer_holds_answers_to_the_identifier_asked_alone() {
    assertThat(TimelineScreen.theIdentifiersRowsOfThatObjectCarry(CHARGE, null))
        .containsExactly(CHARGE);
    assertThat(TimelineScreen.theIdentifiersRowsOfThatObjectCarry(null, null)).isEmpty();
  }

  @Test
  void a_key_that_is_a_later_identifier_of_an_object_resolves_to_the_object_it_was_created_under() {
    TimelineScreen.Subject subject =
        TimelineScreen.subjectOf(
            RENAMED,
            new AddressedProviderObject(RENAMED, aRenamedCharge()),
            List.of(),
            List.of(),
            KeyedExchangePage.empty());

    assertThat(subject)
        .isEqualTo(
            new TimelineScreen.Subject.Resolved(
                CHARGE, TimelineScreen.Found.PROVIDER_OBJECT_ID, "providerObjectId"));
  }

  private static ProviderObjectState aRenamedCharge() {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(CHARGE).answeringAlsoTo(RENAMED),
        AKindOfObjectATestNames.ONE,
        "3",
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        PINNED);
  }

  private static TimelineScreen.View viewOf(
      TimelineScreen.Subject subject,
      @Nullable ProviderObjectState state,
      List<AuditRecord> trail,
      boolean truncated) {
    return TimelineScreen.of(
        PROVIDER.value(),
        RUN.value(),
        CHARGE,
        subject,
        state,
        trail,
        truncated,
        List.of(),
        KeyedExchangePage.empty(),
        aVocabularyWithoutAnOrderId(),
        growLikeKeys(),
        null,
        false);
  }

  private static ProviderObjectState anObject(String id, Map<String, String> related) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(id),
        AKindOfObjectATestNames.ONE,
        "0",
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        related,
        ObjectOrigin.PROVIDER_API,
        PINNED);
  }

  private static ProviderObjectState aCapturedCharge() {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(CHARGE),
        AKindOfObjectATestNames.ONE,
        "3",
        NormalizedStatus.SUCCEEDED,
        Amount.of("10.00"),
        null,
        new Ledger(
            List.of(
                new MonetaryOperation(
                    1,
                    OperationKind.CAPTURE,
                    Amount.of("10.00"),
                    true,
                    null,
                    "3",
                    NormalizedStatus.SUCCEEDED,
                    PINNED),
                new MonetaryOperation(
                    2,
                    OperationKind.REFUND,
                    Amount.of("4.00"),
                    null,
                    "txn_refund",
                    "3",
                    NormalizedStatus.SUCCEEDED,
                    PINNED))),
        false,
        false,
        true,
        theObjectsNamed("merchantId", "m1", "processId", PROCESS),
        ObjectOrigin.PROVIDER_API,
        PINNED);
  }

  private static WebhookDelivery aCallback(
      String deliveryId,
      String eventId,
      @Nullable String providerObjectId,
      DeliveryState state,
      int attemptsMade,
      @Nullable Instant nextAttemptAt) {
    return new WebhookDelivery(
        deliveryId,
        PROVIDER,
        RUN,
        "m1",
        eventId,
        "transaction.approved",
        providerObjectId,
        "https://merchant.test/hook",
        new WebhookPayload(
            "application/json",
            Map.of(),
            "{}".getBytes(StandardCharsets.UTF_8),
            Map.of("id", List.of(eventId))),
        new RetryPlan(List.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))),
        state,
        false,
        attemptsMade,
        nextAttemptAt,
        PINNED,
        PINNED,
        null,
        null);
  }

  private static KeyedExchangePage anExchangePageUnder(
      String keyName, String keyValue, @Nullable String providerObjectId) {
    return new KeyedExchangePage(
        List.of(
            new KeyedExchange(
                PROVIDER,
                RUN,
                "m1",
                keyName,
                KeyRole.DUPLICATE_DETECTION_KEY,
                keyValue,
                7L,
                List.of(
                    new KeyedRequest(
                        0,
                        ExchangeVerdict.FIRST_REQUEST,
                        "fp",
                        "createPaymentProcess",
                        200,
                        "application/json",
                        "{}",
                        providerObjectId,
                        true,
                        null,
                        PINNED)))));
  }

  private static ProviderVocabulary aVocabularyWithoutAnOrderId() {
    return new ProviderVocabulary(
        PROVIDER,
        List.of(
            new ProviderOperation(
                "createPaymentProcess",
                Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID),
                "the payment process",
                StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
        Map.of(
            MatchFact.ORDER_ID,
            "provider grow publishes no order identifier on any endpoint of this contract",
            MatchFact.CUSTOMER_ID,
            "provider grow publishes no customer or owner id on any endpoint of this contract",
            MatchFact.CURRENCY,
            "provider grow publishes no currency parameter",
            MatchFact.EVENT_TYPE,
            "this double names no event type"),
        new FailureCodes("[0-9]+", "a numeric err.id"),
        ProcessingStates.noneOfThem("provider grow publishes no processing status"));
  }

  private static ProviderKeyMechanisms growLikeKeys() {
    return new ProviderKeyMechanisms(
        PROVIDER,
        List.of(
            new DeclaredKey(
                "transactionUniqueIdentifier",
                KeyRole.DUPLICATE_DETECTION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "every submission",
                TestKeyClaims.NEVER_HELD),
            new DeclaredKey(
                "transactionId",
                KeyRole.CORRELATION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "the transaction",
                TestKeyClaims.NEVER_HELD)),
        Map.of(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            "provider grow publishes no idempotency header anywhere in this contract"));
  }

  private static AuditRecord inbound(String auditId, String method, String path) {
    return record(
        auditId,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.INBOUND_REQUEST,
        null,
        method,
        path,
        null,
        null,
        1,
        null);
  }

  private static AuditRecord outbound(
      String auditId, String method, String path, String providerObjectId, int status) {
    return record(
        auditId,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.SUCCESS,
        method,
        path,
        status,
        providerObjectId,
        1,
        null);
  }

  private static AuditRecord faulted(
      String auditId, String providerObjectId, @Nullable AppliedFault fault) {
    return record(
        auditId,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.SUCCESS,
        "POST",
        CHARGE_PATH,
        200,
        providerObjectId,
        1,
        new DeliveredResponse(fault, 12L, 12L, Duration.ZERO));
  }

  private static AuditRecord webhookAttempt(String auditId, String providerObjectId, int attempt) {
    return record(
        auditId,
        ObjectOrigin.SCHEDULED_JOB,
        AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT,
        ProviderOutcome.SUCCESS,
        "POST",
        "https://merchant.test/hook",
        200,
        providerObjectId,
        attempt,
        null);
  }

  private static AuditRecord anAttemptOnATargetSpeltAsTheProviderRoute(
      String auditId, String providerObjectId) {
    return record(
        auditId,
        ObjectOrigin.SCHEDULED_JOB,
        AuditDirection.OUTBOUND_WEBHOOK_ATTEMPT,
        ProviderOutcome.SUCCESS,
        "POST",
        CHARGE_PATH,
        200,
        providerObjectId,
        1,
        null);
  }

  private static AuditRecord webhookRow(String auditId, AuditDirection direction) {
    return record(
        auditId,
        ObjectOrigin.SCHEDULED_JOB,
        direction,
        null,
        "POST",
        "https://merchant.test/hook",
        null,
        CHARGE,
        0,
        null);
  }

  private static AuditRecord action(String auditId, ObjectOrigin origin) {
    return record(
        auditId,
        origin,
        AuditDirection.INBOUND_REQUEST,
        null,
        "POST",
        "/ui/objects/act",
        302,
        CHARGE,
        1,
        null);
  }

  private static AuditRecord record(
      String auditId,
      ObjectOrigin origin,
      AuditDirection direction,
      @Nullable ProviderOutcome outcome,
      String method,
      String path,
      @Nullable Integer status,
      @Nullable String providerObjectId,
      int attempt,
      @Nullable DeliveredResponse delivered) {
    return new AuditRecord(
        auditId,
        RUN,
        null,
        PROVIDER,
        null,
        origin,
        direction,
        outcome,
        method,
        path,
        status,
        providerObjectId,
        null,
        null,
        attempt,
        PINNED,
        Map.of("Content-Type", List.of("application/json")),
        aBodyCarryingARedactedSecret(),
        delivered);
  }

  private static Map<String, List<String>> aBodyCarryingARedactedSecret() {
    Map<String, List<String>> body = new LinkedHashMap<>();
    body.put("id", List.of(CHARGE));
    body.put("client_secret", List.of(Redaction.REDACTED));
    return Map.copyOf(body);
  }
}
