package dev.marcinromanowski.pspsim.kernel.repro;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CarriedEvidenceTest {

  private static final String A_SIGNATURE = "t=1,v1=deadbeef";

  @Test
  void a_callback_carrying_no_payload_and_no_attempt_answers_two_empty_things_rather_than_null() {
    RecordedCallback callback =
        new RecordedCallback(
            "whd_1", "evt_1", null, null, "PENDING", null, 0, 3, null, null, null, null, null,
            null);

    assertThat(callback.carriedPayload()).isEmpty();
    assertThat(callback.carriedAttempts()).isEmpty();
  }

  @Test
  void a_callback_does_not_change_when_the_payload_it_was_built_from_changes_afterwards() {
    Map<String, List<String>> payload = new LinkedHashMap<>();
    payload.put("id", List.of("evt_1"));
    RecordedCallback callback =
        new RecordedCallback(
            "whd_1",
            "evt_1",
            null,
            null,
            "PENDING",
            null,
            0,
            3,
            null,
            null,
            null,
            "application/json",
            payload,
            List.of());
    payload.put("smuggled", List.of("later"));

    assertThat(callback.carriedPayload())
        .containsExactlyInAnyOrderEntriesOf(Map.of("id", List.of("evt_1")));
  }

  @Test
  void an_attempt_whose_wire_was_never_recorded_says_so_and_is_not_one_that_sent_no_header() {
    RecordedAttempt attempt = new RecordedAttempt(1, null, null, "REFUSED", null, null, null, null);

    assertThat(attempt.theWireWasRecorded()).isFalse();
    assertThat(attempt.carriedHeaders()).isEmpty();
  }

  @Test
  void an_attempt_that_recorded_the_wire_says_so_and_carries_what_it_posted_under() {
    RecordedAttempt attempt =
        new RecordedAttempt(
            1,
            null,
            null,
            "ACKNOWLEDGED",
            200,
            null,
            null,
            Map.of("Content-Type", List.of("application/json")));

    assertThat(attempt.theWireWasRecorded()).isTrue();
    assertThat(attempt.carriedHeaders())
        .containsExactlyInAnyOrderEntriesOf(Map.of("Content-Type", List.of("application/json")));
  }

  @Test
  void an_attempt_does_not_change_when_the_headers_it_was_built_from_change_afterwards() {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    headers.put("Signature", List.of(A_SIGNATURE));
    RecordedAttempt attempt =
        new RecordedAttempt(1, null, null, "REFUSED", 500, null, null, headers);
    headers.put("smuggled", List.of("later"));

    assertThat(attempt.carriedHeaders())
        .containsExactlyInAnyOrderEntriesOf(Map.of("Signature", List.of(A_SIGNATURE)));
  }

  @Test
  void an_exchange_carrying_neither_headers_nor_a_body_answers_two_empty_maps_rather_than_null() {
    RecordedExchange exchange =
        new RecordedExchange(
            0,
            "aud_1",
            "UI",
            "INBOUND_REQUEST",
            "GET",
            "/ui",
            200,
            null,
            null,
            null,
            null,
            1,
            null,
            null,
            null);

    assertThat(exchange.carriedHeaders()).isEmpty();
    assertThat(exchange.carriedBody()).isEmpty();
    assertThat(exchange.saysWhatWasOnTheWire()).isFalse();
  }

  @Test
  void an_exchange_says_what_was_on_the_wire_when_either_half_carries_anything() {
    assertThat(
            anExchangeCarrying(Map.of("Content-Type", List.of("application/json")), Map.of())
                .saysWhatWasOnTheWire())
        .isTrue();
    assertThat(
            anExchangeCarrying(Map.of(), Map.of("amount", List.of("1000"))).saysWhatWasOnTheWire())
        .isTrue();
    assertThat(anExchangeCarrying(Map.of(), Map.of()).saysWhatWasOnTheWire()).isFalse();
  }

  @Test
  void an_exchange_does_not_change_when_the_body_it_was_built_from_changes_afterwards() {
    Map<String, List<String>> body = new LinkedHashMap<>();
    body.put("amount", List.of("1000"));
    RecordedExchange exchange = anExchangeCarrying(Map.of(), body);
    body.put("smuggled", List.of("later"));

    assertThat(exchange.carriedBody())
        .containsExactlyInAnyOrderEntriesOf(Map.of("amount", List.of("1000")));
  }

  @Test
  void an_object_carrying_no_identity_no_related_object_and_no_ledger_answers_empty_things() {
    RecordedObject object =
        new RecordedObject(
            null, null, null, null, null, null, false, false, false, null, null, null, null);

    assertThat(object.carriedIdentifiers()).isEmpty();
    assertThat(object.carriedRelatedObjects()).isEmpty();
    assertThat(object.carriedLedger()).isEmpty();
  }

  @Test
  void evidence_carrying_no_trail_and_no_callback_counts_nothing_rather_than_failing() {
    ReproductionEvidence evidence =
        new ReproductionEvidence("ch_1", null, null, null, null, null, false);

    assertThat(evidence.carriedTrail()).isEmpty();
    assertThat(evidence.carriedCallbacks()).isEmpty();
    assertThat(evidence.theExchangesThatSayWhatWasOnTheWire()).isZero();
    assertThat(evidence.theAttemptsItCarries()).isZero();
    assertThat(evidence.theOperationsItCarries()).isZero();
  }

  private static RecordedExchange anExchangeCarrying(
      Map<String, List<String>> headers, Map<String, List<String>> body) {
    return new RecordedExchange(
        0,
        "aud_1",
        "UI",
        "INBOUND_REQUEST",
        "GET",
        "/ui",
        200,
        null,
        null,
        null,
        null,
        1,
        null,
        headers,
        body);
  }
}
