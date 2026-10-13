package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.webhook.Acknowledgement;
import dev.marcinromanowski.pspsim.kernel.webhook.DeliveryState;

public record StatusBadge(String text, Tone tone) {

  public enum Tone {
    SUCCESS("success"),
    FAILURE("failure"),
    PENDING("pending"),
    NEUTRAL("neutral"),
    ATTENTION("attention");

    private final String cssClass;

    Tone(String cssClass) {
      this.cssClass = cssClass;
    }

    public String cssClass() {
      return cssClass;
    }
  }

  public static StatusBadge of(NormalizedStatus status) {
    return new StatusBadge(status.name(), toneOf(status));
  }

  public static StatusBadge of(ProviderOutcome outcome) {
    return new StatusBadge(outcome.name(), toneOf(outcome));
  }

  public static StatusBadge of(DeliveryState state) {
    return new StatusBadge(state.name(), toneOf(state));
  }

  public static StatusBadge of(Acknowledgement verdict) {
    return new StatusBadge(verdict.name(), toneOf(verdict));
  }

  static Tone toneOf(NormalizedStatus status) {
    return switch (status) {
      case SUCCEEDED, PARTIALLY_CAPTURED -> Tone.SUCCESS;
      case FAILED -> Tone.FAILURE;
      case NOT_INITIALIZED, PROCESSING, READY_FOR_CAPTURE -> Tone.PENDING;
      case UNKNOWN -> Tone.ATTENTION;
      case REFUNDED, PARTIALLY_REFUNDED, CANCELLED -> Tone.NEUTRAL;
    };
  }

  static Tone toneOf(ProviderOutcome outcome) {
    return switch (outcome) {
      case SUCCESS -> Tone.SUCCESS;
      case FAILURE -> Tone.FAILURE;
      case PROCESSING -> Tone.PENDING;
      case UNKNOWN -> Tone.ATTENTION;
    };
  }

  static Tone toneOf(DeliveryState state) {
    return switch (state) {
      case DELIVERED -> Tone.SUCCESS;
      case EXHAUSTED, UNDELIVERABLE -> Tone.FAILURE;
      case PENDING -> Tone.PENDING;
      case DROPPED -> Tone.NEUTRAL;
    };
  }

  static Tone toneOf(Acknowledgement verdict) {
    return switch (verdict) {
      case ACKNOWLEDGED -> Tone.SUCCESS;
      case REFUSED -> Tone.FAILURE;
      case AWAITING_SEPARATE_CALL -> Tone.PENDING;
    };
  }
}
