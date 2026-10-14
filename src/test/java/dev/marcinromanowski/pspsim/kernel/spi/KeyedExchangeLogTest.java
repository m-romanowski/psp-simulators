package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fault.FaultPlacement;
import dev.marcinromanowski.pspsim.kernel.fault.TransportFaultKind;
import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.FingerprintKey;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class KeyedExchangeLogTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");
  private static final TestRunId RUN = TestRunId.of("keyed-exchange-log");
  private static final TestRunId ANOTHER_RUN = TestRunId.of("keyed-exchange-log-other");
  private static final String DUPLICATE = "order";
  private static final String CORRELATED = "payment";
  private static final String IDEMPOTENT = "Idempotency-Key";
  private static final Instant AT = Instant.parse("2026-10-14T10:00:00Z");
  private static final FingerprintKey KEY =
      new FingerprintKey("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
  private static final FingerprintKey ANOTHER_KEY =
      new FingerprintKey("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");
  private static final FingerprintedRequest A_CHARGE =
      new FingerprintedRequest("POST", "/v1/charges", Map.of("amount", List.of("1099")));

  private static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          PROVIDER,
          List.of(
              new DeclaredKey(
                  DUPLICATE,
                  KeyRole.DUPLICATE_DETECTION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  "every order",
                  TestKeyClaims.CLAIMED),
              new DeclaredKey(
                  CORRELATED,
                  KeyRole.CORRELATION_KEY,
                  KeyCarriage.PATH_SEGMENT,
                  "the payment",
                  TestKeyClaims.NEVER_HELD),
              new DeclaredKey(
                  IDEMPOTENT,
                  KeyRole.TRUE_IDEMPOTENCY_KEY,
                  KeyCarriage.HEADER,
                  "the account",
                  TestKeyClaims.CLAIMED)),
          Map.of(),
          answered -> Redaction.redact(answered, Set.of("secret")));

  private final List<KeyedExchangeRow> written = new ArrayList<>();
  private final List<TestRunId> keyedFor = new ArrayList<>();
  private int lostSequences;

  private final KeyedExchangeLog log =
      new KeyedExchangeLog(
          MECHANISMS,
          testRunId -> {
            keyedFor.add(testRunId);
            return RUN.equals(testRunId) ? KEY : ANOTHER_KEY;
          },
          row -> {
            if (lostSequences > 0) {
              lostSequences--;
              return 0;
            }
            written.add(row);
            return 1;
          },
          new SequencedRows(),
          JsonMapper.builder().build());

  @Test
  void a_request_that_ran_under_a_duplicate_check_is_first_only_where_no_request_claimed_the_key() {
    KeyedExchangeRow row = recorded(DUPLICATE, KeyedOutcome.RAN_THE_OPERATION);

    assertThat(row.whereNoRequestClaimedTheKey()).isEqualTo(ExchangeVerdict.FIRST_REQUEST);
    assertThat(row.afterTheRequestThatClaimedTheKey()).isEqualTo(ExchangeVerdict.REPEAT_ADMITTED);
  }

  @Test
  void a_request_that_ran_under_a_correlation_key_after_another_is_a_correlated_retry() {
    KeyedExchangeRow row = recorded(CORRELATED, KeyedOutcome.RAN_THE_OPERATION);

    assertThat(row.whereNoRequestClaimedTheKey()).isEqualTo(ExchangeVerdict.FIRST_REQUEST);
    assertThat(row.afterTheRequestThatClaimedTheKey()).isEqualTo(ExchangeVerdict.CORRELATED_RETRY);
  }

  @Test
  void a_request_that_ran_under_a_true_idempotency_key_stays_a_first_request_wherever_it_lands() {
    KeyedExchangeRow row = recorded(IDEMPOTENT, KeyedOutcome.RAN_THE_OPERATION);

    assertThat(row.whereNoRequestClaimedTheKey()).isEqualTo(ExchangeVerdict.FIRST_REQUEST);
    assertThat(row.afterTheRequestThatClaimedTheKey()).isEqualTo(ExchangeVerdict.FIRST_REQUEST);
  }

  @Test
  void a_refusal_as_a_duplicate_is_a_refusal_wherever_it_lands() {
    KeyedExchangeRow row = recorded(DUPLICATE, KeyedOutcome.REFUSED_AS_A_DUPLICATE);

    assertThat(row.whereNoRequestClaimedTheKey()).isEqualTo(ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
    assertThat(row.afterTheRequestThatClaimedTheKey())
        .isEqualTo(ExchangeVerdict.DUPLICATE_KEY_REFUSAL);
  }

  @Test
  void a_replay_is_a_replay_wherever_it_lands() {
    KeyedExchangeRow row = recorded(IDEMPOTENT, KeyedOutcome.REPLAYED);

    assertThat(row.whereNoRequestClaimedTheKey()).isEqualTo(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY);
    assertThat(row.afterTheRequestThatClaimedTheKey())
        .isEqualTo(ExchangeVerdict.TRUE_IDEMPOTENT_REPLAY);
  }

  @Test
  void a_refusal_while_the_key_was_held_is_that_refusal_wherever_it_lands() {
    KeyedExchangeRow row = recorded(DUPLICATE, KeyedOutcome.REFUSED_WHILE_THE_KEY_WAS_HELD);

    assertThat(row.whereNoRequestClaimedTheKey())
        .isEqualTo(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD);
    assertThat(row.afterTheRequestThatClaimedTheKey())
        .isEqualTo(ExchangeVerdict.REFUSED_WHILE_THE_KEY_WAS_HELD);
  }

  @Test
  void the_answer_is_kept_with_every_field_the_provider_withholds_redacted() {
    Map<String, List<String>> answered = new LinkedHashMap<>();
    answered.put("id", List.of("pi_1"));
    answered.put("secret", List.of("pi_1_secret_kept"));

    KeyedExchangeRow row =
        recordedWith(DUPLICATE, KeyedOutcome.RAN_THE_OPERATION, new KeyedAnswer(201, answered));

    assertThat(row.httpStatus()).isEqualTo(201);
    assertThat(row.contentType()).isEqualTo("application/json");
    assertThat(row.body()).isEqualTo("{\"id\":[\"pi_1\"],\"secret\":[\"[redacted]\"]}");
  }

  @Test
  void a_request_answered_with_nothing_keeps_no_status_no_content_type_and_no_body() {
    KeyedExchangeRow row = recordedWith(DUPLICATE, KeyedOutcome.RAN_THE_OPERATION, null);

    assertThat(row.httpStatus()).isNull();
    assertThat(row.contentType()).isNull();
    assertThat(row.body()).isNull();
  }

  @Test
  void the_fingerprint_is_keyed_with_the_secret_of_the_test_run_the_request_belongs_to() {
    KeyedExchangeRow row = recorded(DUPLICATE, KeyedOutcome.RAN_THE_OPERATION);

    assertThat(row.fingerprint())
        .isEqualTo("6e1c0ef8fca94b66fa10790a1aa72ea39462fc5b58671a92286432005af98f67");
    assertThat(keyedFor).containsExactly(RUN);
    assertThat(log.fingerprintOf(RUN, A_CHARGE)).isEqualTo(row.fingerprint());
    assertThat(log.fingerprintOf(ANOTHER_RUN, A_CHARGE)).isNotEqualTo(row.fingerprint());
  }

  @Test
  void every_other_part_of_the_observation_reaches_the_row_as_it_was_observed() {
    AppliedFault fault =
        new AppliedFault(TransportFaultKind.SERVER_ERROR, FaultPlacement.AFTER_THE_OPERATION);

    log.record(
        new KeyedObservation(
            RUN,
            "merchant-1",
            DUPLICATE,
            "order-1",
            "charge",
            KeyedOutcome.RAN_THE_OPERATION,
            A_CHARGE,
            null,
            "pi_1",
            true,
            fault,
            AT));

    KeyedExchangeRow row = written.get(0);
    assertThat(row.testRunId()).isEqualTo(RUN);
    assertThat(row.accountId()).isEqualTo("merchant-1");
    assertThat(row.keyName()).isEqualTo(DUPLICATE);
    assertThat(row.keyValue()).isEqualTo("order-1");
    assertThat(row.operation()).isEqualTo("charge");
    assertThat(row.providerObjectId()).isEqualTo("pi_1");
    assertThat(row.stateChanged()).isTrue();
    assertThat(row.transportFault()).isEqualTo(fault);
    assertThat(row.at()).isEqualTo(AT);
  }

  @Test
  void a_row_that_lost_its_sequence_is_written_again_until_it_is_recorded() {
    lostSequences = 3;

    recorded(DUPLICATE, KeyedOutcome.RAN_THE_OPERATION);

    assertThat(written).hasSize(1);
  }

  @Test
  void a_row_no_attempt_could_record_names_the_provider_and_the_key_it_was_under() {
    lostSequences = SequencedRows.ATTEMPTS;

    assertThatThrownBy(() -> recorded(DUPLICATE, KeyedOutcome.RAN_THE_OPERATION))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provider grow")
        .hasMessageContaining("key " + DUPLICATE);
  }

  @Test
  void a_key_the_provider_declares_nowhere_is_refused() {
    assertThatThrownBy(() -> recorded("undeclared", KeyedOutcome.RAN_THE_OPERATION))
        .isInstanceOf(IllegalStateException.class);
    assertThat(written).isEmpty();
  }

  private KeyedExchangeRow recorded(String keyName, KeyedOutcome outcome) {
    return recordedWith(keyName, outcome, new KeyedAnswer(200, Map.of()));
  }

  private KeyedExchangeRow recordedWith(
      String keyName, KeyedOutcome outcome, @Nullable KeyedAnswer answer) {
    log.record(
        new KeyedObservation(
            RUN,
            "merchant-1",
            keyName,
            "v",
            "charge",
            outcome,
            A_CHARGE,
            answer,
            null,
            false,
            null,
            AT));
    return written.get(written.size() - 1);
  }
}
