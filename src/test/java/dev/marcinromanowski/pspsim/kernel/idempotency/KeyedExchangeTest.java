package dev.marcinromanowski.pspsim.kernel.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeyedExchangeTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");
  private static final TestRunId RUN = TestRunId.of("inspector-run");
  private static final Instant NOON = Instant.parse("2026-09-15T12:00:00Z");
  private static final String ACCOUNT = "merchant-1";
  private static final String FIRST_FINGERPRINT = "aaaa1111";
  private static final String OTHER_FINGERPRINT = "bbbb2222";
  private static final String CHARGE = "createTransactionWithToken";
  private static final String LOOKUP = "getTransactionInfo";

  @Test
  void a_tester_sees_the_first_request_and_the_time_of_every_replay_of_it() {
    KeyedExchange exchange =
        exchange(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, true),
            repeat(1, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY, FIRST_FINGERPRINT, 1),
            repeat(2, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY, FIRST_FINGERPRINT, 2));

    assertThat(exchange.firstRequestAt()).isEqualTo(NOON);
    assertThat(exchange.replayTimestamps())
        .containsExactly(NOON.plus(Duration.ofMinutes(1)), NOON.plus(Duration.ofMinutes(2)));
    assertThat(exchange.fingerprint()).isEqualTo(FIRST_FINGERPRINT);
    assertThat(exchange.operation()).isEqualTo(CHARGE);
    assertThat(exchange.repeats()).hasSize(2);
    assertThat(exchange.stateChanged()).isTrue();
  }

  @Test
  void a_key_whose_repeats_were_all_refused_shows_no_replay_timestamp_at_all() {
    KeyedExchange exchange =
        exchange(
            KeyRole.DUPLICATE_DETECTION_KEY,
            first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, true),
            repeat(1, ExchangeVerdict.DUPLICATE_KEY_REFUSAL, FIRST_FINGERPRINT, 1));

    assertThat(exchange.replayTimestamps()).isEmpty();
    assertThat(exchange.repeats()).hasSize(1);
  }

  @Test
  void a_duplicate_check_can_never_be_recorded_as_having_replayed_an_answer() {
    KeyedRequest claimant = first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, true);
    KeyedRequest replay = repeat(1, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY, FIRST_FINGERPRINT, 1);

    assertThatThrownBy(() -> exchange(KeyRole.DUPLICATE_DETECTION_KEY, claimant, replay))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate-detection key")
        .hasMessageContaining("TRUE_IDEMPOTENT_REPLAY");
  }

  @Test
  void an_idempotency_key_can_never_be_recorded_as_having_refused_a_repeat_as_a_duplicate() {
    KeyedRequest claimant = first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, true);
    KeyedRequest refusedRepeat =
        repeat(1, ExchangeVerdict.DUPLICATE_KEY_REFUSAL, FIRST_FINGERPRINT, 1);

    assertThatThrownBy(() -> exchange(KeyRole.TRUE_IDEMPOTENCY_KEY, claimant, refusedRepeat))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("true idempotency key")
        .hasMessageContaining("DUPLICATE_KEY_REFUSAL");
  }

  @Test
  void a_correlation_key_can_never_be_recorded_as_having_admitted_a_repeat_of_a_duplicate_check() {
    KeyedRequest claimant = first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, false);
    KeyedRequest admittedRepeat = repeat(1, ExchangeVerdict.REPEAT_ADMITTED, FIRST_FINGERPRINT, 1);

    assertThatThrownBy(() -> exchange(KeyRole.CORRELATION_KEY, claimant, admittedRepeat))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("correlation key");
  }

  @Test
  void a_correlation_key_records_a_later_call_addressing_the_object_it_names() {
    KeyedExchange exchange =
        new KeyedExchange(
            PROVIDER,
            RUN,
            ACCOUNT,
            "transactionId",
            KeyRole.CORRELATION_KEY,
            "123456",
            7L,
            List.of(
                new KeyedRequest(
                    0,
                    ExchangeVerdict.FIRST_REQUEST,
                    FIRST_FINGERPRINT,
                    LOOKUP,
                    200,
                    "application/json",
                    "{}",
                    "123456",
                    false,
                    null,
                    NOON),
                new KeyedRequest(
                    1,
                    ExchangeVerdict.CORRELATED_RETRY,
                    FIRST_FINGERPRINT,
                    LOOKUP,
                    200,
                    "application/json",
                    "{}",
                    "123456",
                    false,
                    null,
                    NOON.plus(Duration.ofMinutes(1)))));

    assertThat(exchange.stateChanged()).isFalse();
    assertThat(exchange.replayTimestamps()).isEmpty();
  }

  @Test
  void a_repeat_that_sent_other_parameters_names_both_fingerprints_as_the_conflict() {
    KeyedRequest conflicting = repeat(1, ExchangeVerdict.CONFLICTING_PAYLOAD, OTHER_FINGERPRINT, 1);
    KeyedExchange exchange =
        exchange(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, true),
            conflicting);

    assertThat(exchange.conflictReasonOf(conflicting))
        .contains(OTHER_FINGERPRINT)
        .contains(FIRST_FINGERPRINT)
        .contains("parameters the first request did not");
  }

  @Test
  void a_repeat_that_asked_for_another_endpoint_says_so_rather_than_naming_fingerprints() {
    KeyedRequest elsewhere =
        new KeyedRequest(
            1,
            ExchangeVerdict.CONFLICTING_PAYLOAD,
            OTHER_FINGERPRINT,
            LOOKUP,
            400,
            "application/json",
            "{}",
            null,
            false,
            null,
            NOON.plus(Duration.ofMinutes(1)));
    KeyedExchange exchange =
        exchange(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, true),
            elsewhere);

    assertThat(exchange.conflictReasonOf(elsewhere))
        .contains(LOOKUP)
        .contains(CHARGE)
        .contains("not the same request under one key");
  }

  @Test
  void a_request_that_was_not_refused_as_a_conflict_carries_no_conflict_reason() {
    KeyedRequest replay = repeat(1, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY, FIRST_FINGERPRINT, 1);
    KeyedExchange exchange =
        exchange(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, true),
            replay);

    assertThat(exchange.conflictReasonOf(replay)).isNull();
    assertThat(exchange.conflictReasonOf(exchange.firstRequest())).isNull();
  }

  @Test
  void a_key_nothing_was_ever_sent_under_is_refused_rather_than_shown_as_an_empty_row() {
    assertThatThrownBy(
            () ->
                new KeyedExchange(
                    PROVIDER,
                    RUN,
                    ACCOUNT,
                    "Idempotency-Key",
                    KeyRole.TRUE_IDEMPOTENCY_KEY,
                    "k-1",
                    1L,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a key nothing was ever sent under");
  }

  @Test
  void an_exchange_that_begins_with_a_replay_says_the_request_that_ran_was_not_recorded() {
    KeyedRequest replay = repeat(0, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY, FIRST_FINGERPRINT, 0);

    KeyedExchange exchange = exchange(KeyRole.TRUE_IDEMPOTENCY_KEY, replay);

    assertThat(exchange.whatThisLogDidNotRecord())
        .isEqualTo(KeyedExchange.THE_REQUEST_THAT_RAN_WAS_NOT_RECORDED);
    assertThat(exchange.firstRequest()).isEqualTo(replay);
  }

  @Test
  void a_conflict_recorded_before_any_request_that_ran_says_the_same() {
    KeyedRequest conflict = repeat(0, ExchangeVerdict.CONFLICTING_PAYLOAD, OTHER_FINGERPRINT, 0);

    assertThat(exchange(KeyRole.TRUE_IDEMPOTENCY_KEY, conflict).whatThisLogDidNotRecord())
        .isEqualTo(KeyedExchange.THE_REQUEST_THAT_RAN_WAS_NOT_RECORDED);
  }

  @Test
  void an_exchange_whose_first_request_was_recorded_is_missing_nothing() {
    KeyedRequest held =
        repeat(0, ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD, FIRST_FINGERPRINT, 0);
    KeyedRequest claiming = claimant(1, FIRST_FINGERPRINT, 1);
    KeyedRequest replay = repeat(2, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY, FIRST_FINGERPRINT, 2);

    assertThat(
            exchange(KeyRole.TRUE_IDEMPOTENCY_KEY, held, claiming, replay)
                .whatThisLogDidNotRecord())
        .isNull();
    assertThat(exchange(KeyRole.TRUE_IDEMPOTENCY_KEY, held).whatThisLogDidNotRecord()).isNull();
  }

  @Test
  void a_value_a_provider_scopes_is_found_by_the_value_a_client_sent() {
    KeyedExchange scoped =
        new KeyedExchange(
            PROVIDER,
            RUN,
            ACCOUNT,
            "ORDER",
            KeyRole.DUPLICATE_DETECTION_KEY,
            "2026-09-10 084800",
            1L,
            List.of(claimant(0, FIRST_FINGERPRINT, 0)));

    assertThat(scoped.isKeyedBy("084800")).isTrue();
    assertThat(scoped.isKeyedBy("2026-09-10 084800")).isTrue();
    assertThat(scoped.isKeyedBy("84800")).isFalse();
    assertThat(scoped.isKeyedBy("2026-09-10")).isFalse();
  }

  @Test
  void requests_recorded_out_of_the_order_they_were_answered_in_are_refused() {
    KeyedRequest claimant = first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, true);
    KeyedRequest repeatNumberedTwo =
        repeat(2, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY, FIRST_FINGERPRINT, 1);

    assertThatThrownBy(() -> exchange(KeyRole.TRUE_IDEMPOTENCY_KEY, claimant, repeatNumberedTwo))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("which answer came first");
  }

  @Test
  void an_exchange_whose_first_request_was_refused_without_effect_reports_no_state_change() {
    KeyedExchange exchange =
        exchange(
            KeyRole.DUPLICATE_DETECTION_KEY,
            first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, false),
            repeat(1, ExchangeVerdict.DUPLICATE_KEY_REFUSAL, FIRST_FINGERPRINT, 1));

    assertThat(exchange.stateChanged()).isFalse();
  }

  @Test
  void a_request_a_fault_replaced_says_the_operation_never_ran_before_it_broke() {
    KeyedRequest broken =
        new KeyedRequest(
            0,
            ExchangeVerdict.FIRST_REQUEST,
            FIRST_FINGERPRINT,
            CHARGE,
            null,
            null,
            null,
            null,
            false,
            new AppliedFault(
                TransportFaultKind.DROPPED_CONNECTION, FaultPlacement.INSTEAD_OF_THE_OPERATION),
            NOON);

    assertThat(broken.whenTheFaultLanded()).contains("changed nothing before it broke");
    assertThat(broken.carriesAResponse()).isFalse();
  }

  @Test
  void a_request_a_fault_landed_on_afterwards_says_the_operation_had_already_taken_effect() {
    KeyedRequest lost =
        new KeyedRequest(
            0,
            ExchangeVerdict.FIRST_REQUEST,
            FIRST_FINGERPRINT,
            CHARGE,
            200,
            "application/json",
            "{}",
            "pi_1",
            true,
            new AppliedFault(TransportFaultKind.TRUNCATED_BODY, FaultPlacement.AFTER_THE_OPERATION),
            NOON);

    assertThat(lost.whenTheFaultLanded()).contains("already run and taken effect");
    assertThat(lost.carriesAResponse()).isTrue();
  }

  @Test
  void a_request_nothing_broke_names_no_fault_at_all() {
    assertThat(first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, true).whenTheFaultLanded())
        .isNull();
  }

  @Test
  void a_request_that_carries_no_fingerprint_is_refused_because_nothing_could_be_compared() {
    assertThatThrownBy(
            () ->
                new KeyedRequest(
                    0,
                    ExchangeVerdict.FIRST_REQUEST,
                    " ",
                    CHARGE,
                    200,
                    "application/json",
                    "{}",
                    null,
                    true,
                    null,
                    NOON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fingerprint");
  }

  @Test
  void a_request_that_names_no_operation_is_refused_because_a_key_reused_elsewhere_is_a_conflict() {
    assertThatThrownBy(
            () ->
                new KeyedRequest(
                    0,
                    ExchangeVerdict.FIRST_REQUEST,
                    FIRST_FINGERPRINT,
                    "",
                    200,
                    "application/json",
                    "{}",
                    null,
                    true,
                    null,
                    NOON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names the operation");
  }

  @Test
  void a_request_numbered_before_the_first_one_is_refused() {
    assertThatThrownBy(
            () ->
                new KeyedRequest(
                    -1,
                    ExchangeVerdict.FIRST_REQUEST,
                    FIRST_FINGERPRINT,
                    CHARGE,
                    200,
                    "application/json",
                    "{}",
                    null,
                    true,
                    null,
                    NOON))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("came before it");
  }

  private static KeyedExchange exchange(KeyRole role, KeyedRequest... requests) {
    return new KeyedExchange(
        PROVIDER,
        RUN,
        ACCOUNT,
        "transactionUniqueIdentifier",
        role,
        "9001",
        42L,
        List.of(requests));
  }

  @Test
  void a_repeat_answered_without_running_anything_cannot_be_recorded_as_changing_state() {
    for (ExchangeVerdict answeredWithoutRunning :
        List.of(
            ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY,
            ExchangeVerdict.DUPLICATE_KEY_REFUSAL,
            ExchangeVerdict.CONFLICTING_PAYLOAD,
            ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD)) {
      assertThatThrownBy(() -> changedState(answeredWithoutRunning))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(answeredWithoutRunning.name())
          .hasMessageContaining(answeredWithoutRunning.describedAs())
          .hasMessageContaining("stateChanged cannot be true");
    }
  }

  @Test
  void a_retry_correlated_to_an_object_reports_the_change_the_operation_it_ran_made() {
    KeyedExchange exchange =
        exchange(
            KeyRole.CORRELATION_KEY,
            first(ExchangeVerdict.FIRST_REQUEST, FIRST_FINGERPRINT, false),
            changedState(ExchangeVerdict.CORRELATED_RETRY));

    assertThat(exchange.firstRequest().stateChanged()).isFalse();
    assertThat(exchange.repeats().get(0).stateChanged()).isTrue();
    assertThat(exchange.stateChanged()).isTrue();
  }

  private static KeyedRequest changedState(ExchangeVerdict verdict) {
    return new KeyedRequest(
        1,
        verdict,
        FIRST_FINGERPRINT,
        CHARGE,
        200,
        "application/json",
        "{\"ok\":true}",
        "123456",
        true,
        null,
        NOON.plus(Duration.ofMinutes(1)));
  }

  private static KeyedRequest first(
      ExchangeVerdict verdict, String fingerprint, boolean stateChanged) {
    return new KeyedRequest(
        0,
        verdict,
        fingerprint,
        CHARGE,
        200,
        "application/json",
        "{\"ok\":true}",
        stateChanged ? "123456" : null,
        stateChanged,
        null,
        NOON);
  }

  private static KeyedRequest repeat(
      int sequence, ExchangeVerdict verdict, String fingerprint, int minutesLater) {
    return new KeyedRequest(
        sequence,
        verdict,
        fingerprint,
        CHARGE,
        200,
        "application/json",
        "{\"ok\":true}",
        null,
        false,
        null,
        NOON.plus(Duration.ofMinutes(minutesLater)));
  }

  @Test
  void
      a_refusal_recorded_before_the_request_holding_the_key_answered_is_admitted_as_the_first_row() {
    KeyedExchange exchange =
        exchange(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            heldAt(0, OTHER_FINGERPRINT, 0),
            claimant(1, FIRST_FINGERPRINT, 1));

    assertThat(exchange.requests()).hasSize(2);
    assertThat(exchange.requests().get(0).verdict())
        .isEqualTo(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD);
  }

  @Test
  void
      the_first_request_of_a_key_is_the_one_that_claimed_it_and_not_one_refused_while_it_was_held() {
    KeyedExchange exchange =
        exchange(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            heldAt(0, OTHER_FINGERPRINT, 0),
            claimant(1, FIRST_FINGERPRINT, 1));

    assertThat(exchange.firstRequest().sequence()).isEqualTo(1);
    assertThat(exchange.fingerprint()).isEqualTo(FIRST_FINGERPRINT);
    assertThat(exchange.firstRequestAt()).isEqualTo(NOON.plus(Duration.ofMinutes(1)));
  }

  @Test
  void a_request_refused_while_the_key_was_held_is_counted_among_the_repeats_of_that_key() {
    KeyedExchange exchange =
        exchange(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            heldAt(0, OTHER_FINGERPRINT, 0),
            claimant(1, FIRST_FINGERPRINT, 1));

    assertThat(exchange.repeats()).hasSize(1);
    assertThat(exchange.repeats().get(0).sequence()).isZero();
  }

  @Test
  void a_key_whose_only_record_is_a_refusal_while_it_was_held_reports_that_refusal_as_its_first() {
    KeyedExchange exchange =
        exchange(KeyRole.TRUE_IDEMPOTENCY_KEY, heldAt(0, OTHER_FINGERPRINT, 0));

    assertThat(exchange.firstRequest().sequence()).isZero();
    assertThat(exchange.fingerprint()).isEqualTo(OTHER_FINGERPRINT);
    assertThat(exchange.repeats()).isEmpty();
  }

  @Test
  void a_conflict_is_compared_against_the_request_that_claimed_the_key_and_not_against_a_refusal() {
    KeyedRequest conflicting = repeat(2, ExchangeVerdict.CONFLICTING_PAYLOAD, OTHER_FINGERPRINT, 2);
    KeyedExchange exchange =
        exchange(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            heldAt(0, OTHER_FINGERPRINT, 0),
            claimant(1, FIRST_FINGERPRINT, 1),
            conflicting);

    assertThat(exchange.conflictReasonOf(conflicting))
        .contains("the first request under this key fingerprinted as " + FIRST_FINGERPRINT);
  }

  @Test
  void a_replay_recorded_before_the_request_that_ran_is_kept_and_named_as_missing_its_original() {
    KeyedRequest replayFirst =
        repeat(0, ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY, FIRST_FINGERPRINT, 0);
    KeyedRequest claiming = claimant(1, FIRST_FINGERPRINT, 1);

    KeyedExchange exchange = exchange(KeyRole.TRUE_IDEMPOTENCY_KEY, replayFirst, claiming);

    assertThat(exchange.firstRequest()).isEqualTo(claiming);
    assertThat(exchange.whatThisLogDidNotRecord())
        .isEqualTo(KeyedExchange.THE_REQUEST_THAT_RAN_WAS_NOT_RECORDED);
  }

  @Test
  void
      a_refusal_as_a_duplicate_answered_before_the_request_that_claimed_the_key_is_kept_before_it() {
    KeyedRequest refusedFirst =
        repeat(0, ExchangeVerdict.DUPLICATE_KEY_REFUSAL, FIRST_FINGERPRINT, 0);
    KeyedRequest claiming = claimant(1, FIRST_FINGERPRINT, 1);

    KeyedExchange exchange = exchange(KeyRole.DUPLICATE_DETECTION_KEY, refusedFirst, claiming);

    assertThat(exchange.firstRequest()).isEqualTo(claiming);
    assertThat(exchange.repeats()).containsExactly(refusedFirst);
  }

  @Test
  void a_key_whose_only_record_is_a_refusal_as_a_duplicate_reports_that_refusal_as_its_first() {
    KeyedRequest refused = repeat(0, ExchangeVerdict.DUPLICATE_KEY_REFUSAL, FIRST_FINGERPRINT, 0);

    assertThat(exchange(KeyRole.DUPLICATE_DETECTION_KEY, refused).firstRequest())
        .isEqualTo(refused);
  }

  @Test
  void a_repeat_that_ran_recorded_before_any_first_request_is_refused() {
    KeyedRequest admittedFirst = repeat(0, ExchangeVerdict.REPEAT_ADMITTED, FIRST_FINGERPRINT, 0);

    assertThatThrownBy(() -> exchange(KeyRole.DUPLICATE_DETECTION_KEY, admittedFirst))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a repeat with nothing to repeat");
  }

  @Test
  void a_refusal_while_the_key_was_held_is_admitted_under_every_role_a_provider_can_declare() {
    for (KeyRole role : KeyRole.values()) {
      assertThat(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD.isAdmittedUnder(role)).isTrue();
    }
  }

  @Test
  void a_correlated_retry_is_still_refused_under_a_true_idempotency_key() {
    assertThat(ExchangeVerdict.CORRELATED_RETRY.isAdmittedUnder(KeyRole.TRUE_IDEMPOTENCY_KEY))
        .isFalse();
  }

  private static KeyedRequest heldAt(int sequence, String fingerprint, int minutesLater) {
    return new KeyedRequest(
        sequence,
        ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD,
        fingerprint,
        CHARGE,
        409,
        "application/json",
        "{\"error\":{\"code\":\"idempotency_key_in_use\"}}",
        null,
        false,
        null,
        NOON.plus(Duration.ofMinutes(minutesLater)));
  }

  private static KeyedRequest claimant(int sequence, String fingerprint, int minutesLater) {
    return new KeyedRequest(
        sequence,
        ExchangeVerdict.FIRST_REQUEST,
        fingerprint,
        CHARGE,
        200,
        "application/json",
        "{\"ok\":true}",
        "123456",
        true,
        null,
        NOON.plus(Duration.ofMinutes(minutesLater)));
  }
}
