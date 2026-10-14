package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AzericardObservationTest {

  private static final AzericardObservedRequest REQUEST =
      new AzericardObservedRequest(
          TestRunId.of("azericard-observation"),
          "merchant-1",
          AzericardKeyMechanisms.RRN,
          "123456789012",
          "TRTYPE=21",
          "a-fingerprint",
          ExchangeVerdict.CORRELATED_RETRY,
          200,
          "text/html",
          "<response/>",
          "INTREF01",
          false,
          Instant.parse("2026-09-10T12:00:00Z"));

  @Test
  void a_write_that_lands_at_once_is_sent_once() {
    List<AzericardObservedRequest> sent = new ArrayList<>();

    AzericardObservation.recorded(
        request -> {
          sent.add(request);
          return 1;
        },
        REQUEST);

    assertThat(sent).containsExactly(REQUEST);
  }

  @Test
  void a_write_that_lost_its_sequence_is_sent_again_until_it_lands() {
    int[] sent = {0};

    AzericardObservation.recorded(request -> ++sent[0] < 3 ? 0 : 1, REQUEST);

    assertThat(sent[0]).isEqualTo(3);
  }

  @Test
  void a_write_landing_on_the_last_attempt_is_recorded_without_a_failure() {
    int[] sent = {0};

    AzericardObservation.recorded(request -> ++sent[0] < 100 ? 0 : 1, REQUEST);

    assertThat(sent[0]).isEqualTo(100);
  }

  @Test
  void a_write_that_never_lands_fails_after_the_hundredth_attempt() {
    int[] sent = {0};

    assertThatThrownBy(
            () ->
                AzericardObservation.recorded(
                    request -> {
                      sent[0]++;
                      return 0;
                    },
                    REQUEST))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "the exchange under RRN could not be recorded in 100 attempts, each of which lost"
                + " its sequence number to another request");
    assertThat(sent[0]).isEqualTo(100);
  }
}
