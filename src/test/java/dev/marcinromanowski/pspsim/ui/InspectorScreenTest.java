package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.AKindOfObjectATestNames;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectIdentity;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.TestKeyClaims;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectorScreenTest {

  private static final ProviderId GROW = ProviderId.of("grow");
  private static final TestRunId RUN = TestRunId.of("inspector-run");
  private static final Instant NOON = Instant.parse("2026-09-15T12:00:00Z");
  private static final String MERCHANT = "merchant-1";
  private static final String UNIQUE = "transactionUniqueIdentifier";
  private static final String CHARGE = "createTransactionWithToken";
  private static final String NO_REPLAY =
      "provider grow publishes no idempotency header anywhere in this contract";
  private static final String REPLAYED_KEY = "Idempotency-Key";
  private static final String CHARGED = "123456";
  private static final String REFUND = "654321";
  private static final String RENAMED = "123457";
  private static final String PROCESS = "process-1";

  private static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          GROW,
          List.of(
              new DeclaredKey(
                  UNIQUE,
                  KeyRole.DUPLICATE_DETECTION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  "every submission of this business, successful or failed",
                  TestKeyClaims.NEVER_HELD)),
          Map.of(
              KeyRole.TRUE_IDEMPOTENCY_KEY,
              NO_REPLAY,
              KeyRole.CORRELATION_KEY,
              "provider grow correlates nothing on this contract"),
          answered -> answered);

  private static final ProviderKeyMechanisms REPLAYING =
      new ProviderKeyMechanisms(
          GROW,
          List.of(
              new DeclaredKey(
                  REPLAYED_KEY,
                  KeyRole.TRUE_IDEMPOTENCY_KEY,
                  KeyCarriage.HEADER,
                  "the account of the key, for twenty-four hours",
                  TestKeyClaims.CLAIMED)),
          Map.of(
              KeyRole.DUPLICATE_DETECTION_KEY,
              "this contract refuses no repeat of a merchant's own reference",
              KeyRole.CORRELATION_KEY,
              "this contract correlates nothing"),
          answered -> answered);

  @Test
  void the_screen_prints_what_a_repeat_does_beside_every_key_the_provider_declares() {
    InspectorScreen.View view =
        InspectorScreen.of(RUN.value(), MECHANISMS, KeyedExchangePage.empty(), 50, List.of());

    assertThat(view.provider()).isEqualTo("grow");
    assertThat(view.testRunId()).isEqualTo(RUN.value());
    assertThat(view.mechanisms())
        .singleElement()
        .satisfies(
            key -> {
              assertThat(key.name()).isEqualTo(UNIQUE);
              assertThat(key.role()).isEqualTo("duplicate-detection key");
              assertThat(key.roleMeans()).contains("refused and never replayed");
              assertThat(key.carriedIn()).isEqualTo("sent as a field of the request itself");
              assertThat(key.scopedBy()).contains("successful or failed");
            });
  }

  @Test
  void a_role_the_provider_has_no_mechanism_for_is_printed_in_its_own_words_and_never_blank() {
    InspectorScreen.View view =
        InspectorScreen.of(RUN.value(), MECHANISMS, KeyedExchangePage.empty(), 50, List.of());

    assertThat(view.absences())
        .extracting(InspectorScreen.Absence::role)
        .containsExactly("true idempotency key", "correlation key");
    assertThat(view.absences().get(0).instead()).isEqualTo(NO_REPLAY);
    assertThat(view.absences().get(0).roleMeans()).contains("returns the original response");
    assertThat(view.rows()).isEmpty();
    assertThat(view.nextCursor()).isNull();
  }

  @Test
  void a_refused_repeat_is_printed_with_its_verdict_and_never_as_a_replay_timestamp() {
    InspectorScreen.View view =
        InspectorScreen.of(RUN.value(), MECHANISMS, aPageOf(anExchange()), 50, List.of());

    InspectorScreen.Row row = view.rows().get(0);
    assertThat(row.keyName()).isEqualTo(UNIQUE);
    assertThat(row.keyValue()).isEqualTo("9001");
    assertThat(row.role()).isEqualTo("duplicate-detection key");
    assertThat(row.accountId()).isEqualTo(MERCHANT);
    assertThat(row.operation()).isEqualTo(CHARGE);
    assertThat(row.firstRequestAt()).isEqualTo(NOON);
    assertThat(row.wasReplayed()).isFalse();
    assertThat(row.wasRepeated()).isTrue();
    assertThat(row.stateChanged()).isTrue();
    assertThat(row.cursor()).isEqualTo("7");
    assertThat(row.repeats())
        .singleElement()
        .satisfies(
            repeat -> {
              assertThat(repeat.verdict()).isEqualTo(ExchangeVerdict.DUPLICATE_KEY_REFUSAL.name());
              assertThat(repeat.verdictMeans()).contains("no response replayed");
              assertThat(repeat.carriesAResponse()).isTrue();
              assertThat(repeat.namesAConflict()).isFalse();
              assertThat(repeat.namesAFault()).isFalse();
              assertThat(repeat.stateChanged()).isFalse();
            });
    assertThat(row.firstRequest().namesAFault()).isTrue();
    assertThat(row.firstRequest().timeout()).contains("already run and taken effect");
  }

  @Test
  void a_full_page_offers_the_cursor_the_next_page_carries_on_from() {
    assertThat(
            InspectorScreen.of(RUN.value(), MECHANISMS, aPageOf(anExchange()), 1, List.of())
                .nextCursor())
        .isEqualTo("7");
    assertThat(
            InspectorScreen.of(RUN.value(), MECHANISMS, aPageOf(anExchange()), 2, List.of())
                .nextCursor())
        .isNull();
  }

  @Test
  void a_key_sent_once_is_shown_as_one_request_and_never_as_a_repeat_of_itself() {
    InspectorScreen.View view =
        InspectorScreen.of(RUN.value(), MECHANISMS, aPageOf(oneRequestThatBroke()), 50, List.of());

    InspectorScreen.Row row = view.rows().get(0);
    assertThat(row.wasRepeated()).isFalse();
    assertThat(row.repeats()).isEmpty();
    assertThat(row.wasReplayed()).isFalse();
  }

  @Test
  void a_request_this_simulator_answered_nothing_under_says_so_rather_than_printing_a_blank() {
    InspectorScreen.View view =
        InspectorScreen.of(RUN.value(), MECHANISMS, aPageOf(oneRequestThatBroke()), 50, List.of());

    InspectorScreen.Request only = view.rows().get(0).firstRequest();
    assertThat(only.carriesAResponse()).isFalse();
    assertThat(only.status()).isNull();
    assertThat(only.body()).isNull();
    assertThat(only.namesAFault()).isTrue();
    assertThat(only.timeout()).contains("changed nothing before it broke");
  }

  @Test
  void the_row_names_the_object_the_earliest_request_under_that_key_reached() {
    InspectorScreen.Row row =
        InspectorScreen.of(RUN.value(), MECHANISMS, aPageOf(anExchange()), 50, List.of())
            .rows()
            .get(0);

    assertThat(row.reachedAnObject()).isTrue();
    assertThat(row.providerObjectId()).isEqualTo(CHARGED);
  }

  @Test
  void a_key_whose_first_request_reached_no_object_names_the_one_a_later_request_did() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(anExchangeThatReachedNothingAtFirst()),
                50,
                List.of())
            .rows()
            .get(0);

    assertThat(row.reachedAnObject()).isTrue();
    assertThat(row.providerObjectId()).isEqualTo(CHARGED);
    assertThat(row.firstRequest().providerObjectId()).isNull();
  }

  @Test
  void a_key_whose_requests_named_no_object_says_it_reached_none_rather_than_a_blank() {
    InspectorScreen.Row row =
        InspectorScreen.of(RUN.value(), MECHANISMS, aPageOf(oneRequestThatBroke()), 50, List.of())
            .rows()
            .get(0);

    assertThat(row.reachedAnObject()).isFalse();
    assertThat(row.providerObjectId()).isNull();
    assertThat(row.linksToAnything()).isFalse();
  }

  @Test
  void the_row_carries_the_fields_by_which_the_object_it_reached_names_other_objects() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(anExchange()),
                50,
                List.of(theChargeNaming(Map.of("processId", PROCESS))))
            .rows()
            .get(0);

    assertThat(row.objectsItNames())
        .containsExactly(new InspectorScreen.NamedObject("processId", PROCESS));
    assertThat(row.linksToAnything()).isTrue();
  }

  @Test
  void the_row_carries_the_refund_that_names_the_charge_this_key_reached_and_is_not_keyed_itself() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(anExchange()),
                50,
                List.of(
                    theChargeNaming(Map.of()),
                    theRefundNaming(Map.of("refundedTransactionId", CHARGED))))
            .rows()
            .get(0);

    assertThat(row.objectsThatNameIt())
        .containsExactly(new InspectorScreen.NamedObject("refundedTransactionId", REFUND));
    assertThat(row.objectsItNames()).isEmpty();
  }

  @Test
  void an_object_of_this_run_that_names_nothing_of_this_row_is_left_off_the_row() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(anExchange()),
                50,
                List.of(
                    theChargeNaming(Map.of()),
                    theRefundNaming(Map.of("refundedTransactionId", "999999"))))
            .rows()
            .get(0);

    assertThat(row.objectsThatNameIt()).isEmpty();
    assertThat(row.linksToAnything()).isFalse();
  }

  @Test
  void a_row_of_a_key_that_refuses_its_repeat_offers_no_response_as_a_replay_of_the_original() {
    InspectorScreen.Row row =
        InspectorScreen.of(RUN.value(), MECHANISMS, aPageOf(anExchange()), 50, List.of())
            .rows()
            .get(0);

    assertThat(row.replayResponses()).isEmpty();
    assertThat(row.firstRequest().replaysTheOriginal()).isFalse();
    assertThat(row.repeats().get(0).replaysTheOriginal()).isFalse();
  }

  @Test
  void a_row_of_a_key_that_replays_its_repeat_offers_that_repeat_as_the_replay_response() {
    InspectorScreen.Row row =
        InspectorScreen.of(RUN.value(), REPLAYING, aPageOf(aReplayedExchange()), 50, List.of())
            .rows()
            .get(0);

    assertThat(row.replayResponses())
        .singleElement()
        .satisfies(
            replay -> {
              assertThat(replay.sequence()).isEqualTo(1);
              assertThat(replay.body()).isEqualTo(row.firstRequest().body());
              assertThat(replay.replaysTheOriginal()).isTrue();
            });
  }

  @Test
  void a_key_that_reached_an_object_under_an_identifier_a_later_call_replaced_still_finds_it() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(anExchange()),
                50,
                List.of(theChargeRenamedTo(RENAMED, Map.of("processId", PROCESS))))
            .rows()
            .get(0);

    assertThat(row.objectsItNames())
        .containsExactly(new InspectorScreen.NamedObject("processId", PROCESS));
  }

  @Test
  void a_refund_that_names_a_later_identifier_of_the_charge_still_names_the_charge() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(anExchange()),
                50,
                List.of(
                    theChargeRenamedTo(RENAMED, Map.of()),
                    theRefundNaming(Map.of("refundedTransactionId", RENAMED))))
            .rows()
            .get(0);

    assertThat(row.objectsThatNameIt())
        .containsExactly(new InspectorScreen.NamedObject("refundedTransactionId", REFUND));
  }

  @Test
  void an_object_the_run_holds_under_no_identifier_of_this_row_answers_to_the_row_s_own_id() {
    assertThat(InspectorScreen.theIdentifiersThatObjectAnswersTo(CHARGED, List.of()))
        .containsExactly(CHARGED);
    assertThat(InspectorScreen.theIdentifiersThatObjectAnswersTo(null, List.of())).isEmpty();
    assertThat(
            InspectorScreen.theIdentifiersThatObjectAnswersTo(
                RENAMED, List.of(theChargeRenamedTo(RENAMED, Map.of()))))
        .containsExactly(CHARGED, RENAMED);
  }

  @Test
  void a_key_that_reached_no_object_at_all_names_no_object_and_is_named_by_none() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(oneRequestThatBroke()),
                50,
                List.of(theRefundNaming(Map.of("refundedTransactionId", CHARGED))))
            .rows()
            .get(0);

    assertThat(row.objectsItNames()).isEmpty();
    assertThat(row.objectsThatNameIt()).isEmpty();
  }

  private static ProviderObjectState theChargeRenamedTo(
      String renamed, Map<String, String> related) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(CHARGED).answeringAlsoTo(renamed),
        AKindOfObjectATestNames.ONE,
        "1",
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        related,
        ObjectOrigin.PROVIDER_API,
        NOON);
  }

  private static ProviderObjectState theChargeNaming(Map<String, String> related) {
    return anObject(CHARGED, related);
  }

  private static ProviderObjectState theRefundNaming(Map<String, String> related) {
    return anObject(REFUND, related);
  }

  private static ProviderObjectState anObject(String id, Map<String, String> related) {
    return new ProviderObjectState(
        ProviderObjectIdentity.of(id),
        AKindOfObjectATestNames.ONE,
        "1",
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        related,
        ObjectOrigin.PROVIDER_API,
        NOON);
  }

  private static KeyedExchange anExchangeThatReachedNothingAtFirst() {
    return new KeyedExchange(
        GROW,
        RUN,
        MERCHANT,
        UNIQUE,
        KeyRole.DUPLICATE_DETECTION_KEY,
        "9004",
        6L,
        List.of(
            new KeyedRequest(
                0,
                ExchangeVerdict.FIRST_REQUEST,
                "fingerprint-four",
                CHARGE,
                200,
                "application/json",
                "{\"status\":0}",
                null,
                false,
                null,
                NOON),
            new KeyedRequest(
                1,
                ExchangeVerdict.REPEAT_ADMITTED,
                "fingerprint-four",
                CHARGE,
                200,
                "application/json",
                "{\"status\":1}",
                CHARGED,
                true,
                null,
                NOON.plus(Duration.ofMinutes(1)))));
  }

  private static KeyedExchange aReplayedExchange() {
    return new KeyedExchange(
        GROW,
        RUN,
        MERCHANT,
        REPLAYED_KEY,
        KeyRole.TRUE_IDEMPOTENCY_KEY,
        "9003",
        9L,
        List.of(
            new KeyedRequest(
                0,
                ExchangeVerdict.FIRST_REQUEST,
                "fingerprint-three",
                CHARGE,
                200,
                "application/json",
                "{\"status\":1}",
                CHARGED,
                true,
                null,
                NOON),
            new KeyedRequest(
                1,
                ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY,
                "fingerprint-three",
                CHARGE,
                200,
                "application/json",
                "{\"status\":1}",
                CHARGED,
                false,
                null,
                NOON.plus(Duration.ofMinutes(1)))));
  }

  private static KeyedExchange oneRequestThatBroke() {
    return new KeyedExchange(
        GROW,
        RUN,
        MERCHANT,
        UNIQUE,
        KeyRole.DUPLICATE_DETECTION_KEY,
        "9002",
        8L,
        List.of(
            new KeyedRequest(
                0,
                ExchangeVerdict.FIRST_REQUEST,
                "fingerprint-two",
                CHARGE,
                null,
                null,
                null,
                null,
                false,
                new AppliedFault(
                    TransportFaultKind.DROPPED_CONNECTION, FaultPlacement.INSTEAD_OF_THE_OPERATION),
                NOON)));
  }

  @Test
  void the_first_request_shown_is_the_one_that_ran_even_after_a_refusal_answered_before_it() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(
                    underOneKey(
                        UNIQUE,
                        KeyRole.DUPLICATE_DETECTION_KEY,
                        aRequest(0, ExchangeVerdict.DUPLICATE_KEY_REFUSAL),
                        aRequest(1, ExchangeVerdict.FIRST_REQUEST))),
                50,
                List.of())
            .rows()
            .get(0);

    assertThat(row.firstRequest().sequence()).isEqualTo(1);
    assertThat(row.firstRequest().ranTheOperation()).isTrue();
    assertThat(row.theRequestThatRanIsMissing()).isFalse();
    assertThat(row.notRecorded()).isNull();
  }

  @Test
  void a_key_no_request_ran_under_shows_its_first_answer_that_reached_the_record() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(
                    underOneKey(
                        UNIQUE,
                        KeyRole.DUPLICATE_DETECTION_KEY,
                        aRequest(0, ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD),
                        aRequest(1, ExchangeVerdict.DUPLICATE_KEY_REFUSAL))),
                50,
                List.of())
            .rows()
            .get(0);

    assertThat(row.firstRequest().sequence()).isEqualTo(1);
    assertThat(row.firstRequest().ranTheOperation()).isFalse();
  }

  @Test
  void the_first_request_shown_is_the_one_whose_time_the_row_names_when_none_ran() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                MECHANISMS,
                aPageOf(
                    underOneKey(
                        UNIQUE,
                        KeyRole.DUPLICATE_DETECTION_KEY,
                        aRequest(0, ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD),
                        aRequest(1, ExchangeVerdict.DUPLICATE_KEY_REFUSAL))),
                50,
                List.of())
            .rows()
            .get(0);

    assertThat(row.firstRequestAt()).isEqualTo(NOON.plus(Duration.ofMinutes(1)));
    assertThat(row.firstRequest().at()).isEqualTo(row.firstRequestAt());
    assertThat(row.repeats()).extracting(InspectorScreen.Request::sequence).containsExactly(0);
    assertThat(row.aRequestClaimedThisKey()).isTrue();
  }

  @Test
  void a_conflict_recorded_before_the_request_that_ran_names_no_comparison_with_itself() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                REPLAYING,
                aPageOf(
                    underOneKey(
                        REPLAYED_KEY,
                        KeyRole.TRUE_IDEMPOTENCY_KEY,
                        aRequest(0, ExchangeVerdict.CONFLICTING_PAYLOAD))),
                50,
                List.of())
            .rows()
            .get(0);

    assertThat(row.requests().get(0).conflictReason())
        .isEqualTo(KeyedExchange.THE_REQUEST_IT_CONFLICTS_WITH_WAS_NOT_RECORDED);
  }

  @Test
  void a_key_whose_request_that_ran_was_not_recorded_is_shown_as_missing_it() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(),
                REPLAYING,
                aPageOf(
                    underOneKey(
                        REPLAYED_KEY,
                        KeyRole.TRUE_IDEMPOTENCY_KEY,
                        aRequest(0, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY))),
                50,
                List.of())
            .rows()
            .get(0);

    assertThat(row.theRequestThatRanIsMissing()).isTrue();
    assertThat(row.notRecorded()).isEqualTo(KeyedExchange.THE_REQUEST_THAT_RAN_WAS_NOT_RECORDED);
  }

  private static KeyedExchange underOneKey(String keyName, KeyRole role, KeyedRequest... requests) {
    return new KeyedExchange(GROW, RUN, MERCHANT, keyName, role, "9009", 12L, List.of(requests));
  }

  private static KeyedRequest aRequest(int sequence, ExchangeVerdict verdict) {
    return new KeyedRequest(
        sequence,
        verdict,
        "fingerprint-nine",
        CHARGE,
        200,
        "application/json",
        "{\"status\":1}",
        null,
        false,
        null,
        NOON.plus(Duration.ofMinutes(sequence)));
  }

  private static KeyedExchangePage aPageOf(KeyedExchange exchange) {
    return new KeyedExchangePage(List.of(exchange));
  }

  private static KeyedExchange anExchange() {
    return new KeyedExchange(
        GROW,
        RUN,
        MERCHANT,
        UNIQUE,
        KeyRole.DUPLICATE_DETECTION_KEY,
        "9001",
        7L,
        List.of(
            new KeyedRequest(
                0,
                ExchangeVerdict.FIRST_REQUEST,
                "fingerprint-one",
                CHARGE,
                200,
                "application/json",
                "{\"status\":1}",
                "123456",
                true,
                new AppliedFault(
                    TransportFaultKind.TRUNCATED_BODY, FaultPlacement.AFTER_THE_OPERATION),
                NOON),
            new KeyedRequest(
                1,
                ExchangeVerdict.DUPLICATE_KEY_REFUSAL,
                "fingerprint-one",
                CHARGE,
                200,
                "application/json",
                "{\"status\":0}",
                null,
                false,
                null,
                NOON.plus(Duration.ofMinutes(1)))));
  }

  @Test
  void the_screen_prints_what_holding_a_key_means_beside_the_key_its_provider_claims() {
    InspectorScreen.View view =
        InspectorScreen.of(RUN.value(), REPLAYING, KeyedExchangePage.empty(), 50, List.of());

    assertThat(view.mechanisms())
        .singleElement()
        .satisfies(
            key -> {
              assertThat(key.canBeHeld()).isTrue();
              assertThat(key.whileItIsHeld())
                  .contains("refuses a request that arrives while it is" + " held");
            });
  }

  @Test
  void the_screen_prints_why_a_key_its_provider_never_claims_can_never_be_found_held() {
    InspectorScreen.View view =
        InspectorScreen.of(RUN.value(), MECHANISMS, KeyedExchangePage.empty(), 50, List.of());

    assertThat(view.mechanisms())
        .singleElement()
        .satisfies(
            key -> {
              assertThat(key.canBeHeld()).isFalse();
              assertThat(key.whileItIsHeld()).contains("claims nothing before an operation runs");
            });
  }

  @Test
  void the_original_response_of_a_key_is_the_holder_answer_and_not_a_refusal_recorded_before_it() {
    InspectorScreen.Row row =
        InspectorScreen.of(RUN.value(), REPLAYING, aPageOf(aRacedExchange()), 50, List.of())
            .rows()
            .get(0);

    assertThat(row.firstRequest().sequence()).isEqualTo(1);
    assertThat(row.firstRequest().status()).isEqualTo(200);
    assertThat(row.firstRequest().body()).isEqualTo("{\"id\":\"pi_1\"}");
    assertThat(row.requestFingerprint()).isEqualTo("fingerprint-one");
  }

  @Test
  void the_refusal_recorded_before_the_holder_answered_is_listed_among_that_key_repeats() {
    InspectorScreen.Row row =
        InspectorScreen.of(RUN.value(), REPLAYING, aPageOf(aRacedExchange()), 50, List.of())
            .rows()
            .get(0);

    assertThat(row.repeats())
        .singleElement()
        .satisfies(
            refused -> {
              assertThat(refused.sequence()).isZero();
              assertThat(refused.verdict())
                  .isEqualTo(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD.name());
              assertThat(refused.neverReachedTheKeysOwnRecord()).isTrue();
              assertThat(refused.status()).isEqualTo(409);
            });
    assertThat(row.wasReplayed()).isFalse();
  }

  private static KeyedExchange aRacedExchange() {
    return new KeyedExchange(
        GROW,
        RUN,
        MERCHANT,
        REPLAYED_KEY,
        KeyRole.TRUE_IDEMPOTENCY_KEY,
        "k-1",
        9L,
        List.of(
            new KeyedRequest(
                0,
                ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD,
                "fingerprint-one",
                CHARGE,
                409,
                "application/json",
                "{\"error\":{\"code\":\"idempotency_key_in_use\"}}",
                null,
                false,
                null,
                NOON),
            new KeyedRequest(
                1,
                ExchangeVerdict.FIRST_REQUEST,
                "fingerprint-one",
                CHARGE,
                200,
                "application/json",
                "{\"id\":\"pi_1\"}",
                CHARGED,
                true,
                null,
                NOON.plus(Duration.ofMinutes(1)))));
  }

  @Test
  void a_key_whose_every_request_was_refused_while_it_was_held_reports_the_earliest_as_its_first() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(), REPLAYING, aPageOf(anExchangeNoRequestClaimed()), 50, List.of())
            .rows()
            .get(0);

    assertThat(row.firstRequest().sequence()).isZero();
    assertThat(row.firstRequest().verdict())
        .isEqualTo(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD.name());
    assertThat(row.firstRequest().at()).isEqualTo(NOON);
  }

  @Test
  void a_key_no_request_ever_claimed_shows_no_original_response_rather_than_the_refusal_it_got() {
    InspectorScreen.Row row =
        InspectorScreen.of(
                RUN.value(), REPLAYING, aPageOf(anExchangeNoRequestClaimed()), 50, List.of())
            .rows()
            .get(0);

    assertThat(row.aRequestClaimedThisKey()).isFalse();
    assertThat(InspectorScreen.NO_REQUEST_UNDER_THIS_KEY_CLAIMED_IT)
        .contains("there is no original response to show");
  }

  @Test
  void a_key_one_request_claimed_reports_that_it_has_an_original_response_to_show() {
    InspectorScreen.Row raced =
        InspectorScreen.of(RUN.value(), REPLAYING, aPageOf(aRacedExchange()), 50, List.of())
            .rows()
            .get(0);
    InspectorScreen.Row plain =
        InspectorScreen.of(RUN.value(), MECHANISMS, aPageOf(anExchange()), 50, List.of())
            .rows()
            .get(0);

    assertThat(raced.aRequestClaimedThisKey()).isTrue();
    assertThat(plain.aRequestClaimedThisKey()).isTrue();
  }

  private static KeyedExchange anExchangeNoRequestClaimed() {
    return new KeyedExchange(
        GROW,
        RUN,
        MERCHANT,
        REPLAYED_KEY,
        KeyRole.TRUE_IDEMPOTENCY_KEY,
        "k-2",
        10L,
        List.of(refusedWhileHeld(0, NOON), refusedWhileHeld(1, NOON.plus(Duration.ofSeconds(1)))));
  }

  private static KeyedRequest refusedWhileHeld(int sequence, Instant at) {
    return new KeyedRequest(
        sequence,
        ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD,
        "fingerprint-one",
        CHARGE,
        409,
        "application/json",
        "{\"error\":{\"code\":\"idempotency_key_in_use\"}}",
        null,
        false,
        null,
        at);
  }
}
