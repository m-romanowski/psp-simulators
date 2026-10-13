package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;
import dev.marcinromanowski.pspsim.ui.StatusBadge.Tone;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatusBadgeTest {

  private static final Map<NormalizedStatus, Tone> THE_TONE_A_TESTER_READS =
      new EnumMap<>(
          Map.of(
              NormalizedStatus.NOT_INITIALIZED, Tone.PENDING,
              NormalizedStatus.READY_FOR_CAPTURE, Tone.PENDING,
              NormalizedStatus.PARTIALLY_CAPTURED, Tone.SUCCESS,
              NormalizedStatus.PROCESSING, Tone.PENDING,
              NormalizedStatus.SUCCEEDED, Tone.SUCCESS,
              NormalizedStatus.PARTIALLY_REFUNDED, Tone.NEUTRAL,
              NormalizedStatus.REFUNDED, Tone.NEUTRAL,
              NormalizedStatus.FAILED, Tone.FAILURE,
              NormalizedStatus.CANCELLED, Tone.NEUTRAL,
              NormalizedStatus.UNKNOWN, Tone.ATTENTION));

  private static final Map<ProviderOutcome, Tone> THE_TONE_AN_OUTCOME_IS_READ_IN =
      new EnumMap<>(
          Map.of(
              ProviderOutcome.SUCCESS, Tone.SUCCESS,
              ProviderOutcome.FAILURE, Tone.FAILURE,
              ProviderOutcome.PROCESSING, Tone.PENDING,
              ProviderOutcome.UNKNOWN, Tone.ATTENTION));

  private static final Map<DeliveryState, Tone> THE_TONE_A_DELIVERY_IS_READ_IN =
      new EnumMap<>(
          Map.of(
              DeliveryState.PENDING, Tone.PENDING,
              DeliveryState.DELIVERED, Tone.SUCCESS,
              DeliveryState.EXHAUSTED, Tone.FAILURE,
              DeliveryState.DROPPED, Tone.NEUTRAL,
              DeliveryState.UNDELIVERABLE, Tone.FAILURE));

  private static final Map<Acknowledgement, Tone> THE_TONE_AN_ATTEMPT_IS_READ_IN =
      new EnumMap<>(
          Map.of(
              Acknowledgement.ACKNOWLEDGED, Tone.SUCCESS,
              Acknowledgement.REFUSED, Tone.FAILURE,
              Acknowledgement.AWAITING_SEPARATE_CALL, Tone.PENDING));

  private static final Map<Tone, String> THE_CLASS_THE_STYLESHEET_PAINTS =
      new EnumMap<>(
          Map.of(
              Tone.SUCCESS, "success",
              Tone.FAILURE, "failure",
              Tone.PENDING, "pending",
              Tone.NEUTRAL, "neutral",
              Tone.ATTENTION, "attention"));

  @Test
  void the_expected_tones_name_every_value_of_every_vocabulary_a_badge_is_made_from() {
    assertThat(THE_TONE_A_TESTER_READS.keySet()).containsExactly(NormalizedStatus.values());
    assertThat(THE_TONE_AN_OUTCOME_IS_READ_IN.keySet()).containsExactly(ProviderOutcome.values());
    assertThat(THE_TONE_A_DELIVERY_IS_READ_IN.keySet()).containsExactly(DeliveryState.values());
    assertThat(THE_TONE_AN_ATTEMPT_IS_READ_IN.keySet()).containsExactly(Acknowledgement.values());
  }

  @Test
  void every_normalized_status_is_shown_with_its_own_name_in_the_tone_a_tester_reads_it_in() {
    for (NormalizedStatus status : NormalizedStatus.values()) {
      assertThat(StatusBadge.of(status))
          .as(status.name())
          .isEqualTo(new StatusBadge(status.name(), THE_TONE_A_TESTER_READS.get(status)));
    }
  }

  @Test
  void every_outcome_of_an_exchange_is_shown_with_its_own_name_in_its_tone() {
    for (ProviderOutcome outcome : ProviderOutcome.values()) {
      assertThat(StatusBadge.of(outcome))
          .as(outcome.name())
          .isEqualTo(new StatusBadge(outcome.name(), THE_TONE_AN_OUTCOME_IS_READ_IN.get(outcome)));
    }
  }

  @Test
  void every_state_of_a_delivery_is_shown_with_its_own_name_in_its_tone() {
    for (DeliveryState state : DeliveryState.values()) {
      assertThat(StatusBadge.of(state))
          .as(state.name())
          .isEqualTo(new StatusBadge(state.name(), THE_TONE_A_DELIVERY_IS_READ_IN.get(state)));
    }
  }

  @Test
  void every_verdict_on_an_attempt_is_shown_with_its_own_name_in_its_tone() {
    for (Acknowledgement verdict : Acknowledgement.values()) {
      assertThat(StatusBadge.of(verdict))
          .as(verdict.name())
          .isEqualTo(new StatusBadge(verdict.name(), THE_TONE_AN_ATTEMPT_IS_READ_IN.get(verdict)));
    }
  }

  @Test
  void every_tone_names_the_class_the_stylesheet_paints_it_with() {
    assertThat(THE_CLASS_THE_STYLESHEET_PAINTS.keySet()).containsExactly(Tone.values());
    for (Tone tone : Tone.values()) {
      assertThat(tone.cssClass()).isEqualTo(THE_CLASS_THE_STYLESHEET_PAINTS.get(tone));
    }
  }
}
