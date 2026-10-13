package dev.marcinromanowski.pspsim.kernel.scenario;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum ScenarioPreset {
  CARD_ADD_SUCCESS("card-add-success"),
  CARD_ADD_FAILED("card-add-failed"),
  CARD_ADD_CALLBACK_DELAYED("card-add-callback-delayed"),
  PREAUTH_SUCCESS_CAPTURE_SUCCESS("preauth-success-capture-success"),
  PREAUTH_SUCCESS_CAPTURE_DECLINE("preauth-success-capture-decline"),
  PREAUTH_SUCCESS_CAPTURE_TIMEOUT_THEN_SUCCESS("preauth-success-capture-timeout-then-success"),
  CAPTURE_ACCEPTED_RESPONSE_LOST("capture-accepted-response-lost"),
  CANCEL_HOLD_SUCCESS("cancel-hold-success"),
  REFUND_FULL_SUCCESS("refund-full-success"),
  REFUND_PARTIAL_THEN_FULL("refund-partial-then-full"),
  PSP_5XX_THEN_SUCCESS("psp-5xx-then-success"),
  WEBHOOK_DELAYED("webhook-delayed"),
  WEBHOOK_DUPLICATE("webhook-duplicate"),
  WEBHOOK_OUT_OF_ORDER("webhook-out-of-order"),
  WEBHOOK_INVALID_SIGNATURE("webhook-invalid-signature"),
  STATUS_LOOKUP_PROCESSING_THEN_SUCCESS("status-lookup-processing-then-success"),
  STATUS_LOOKUP_PROCESSING_THEN_FAILED("status-lookup-processing-then-failed");

  public static final Duration DELIVERY_HELD_BACK_BY = Duration.ofSeconds(5);

  public static final Duration THE_OUTCOME_SETTLES_AFTER = Duration.ofSeconds(5);

  private final String publishedName;

  ScenarioPreset(String publishedName) {
    this.publishedName = publishedName;
  }

  public String publishedName() {
    return publishedName;
  }

  public static Optional<ScenarioPreset> of(String publishedName) {
    for (ScenarioPreset preset : values()) {
      if (preset.publishedName.equals(publishedName)) {
        return Optional.of(preset);
      }
    }
    return Optional.empty();
  }

  public static String thePublishedNames() {
    List<String> names = new ArrayList<>();
    for (ScenarioPreset preset : values()) {
      names.add(preset.publishedName);
    }
    return String.join(", ", names);
  }
}
