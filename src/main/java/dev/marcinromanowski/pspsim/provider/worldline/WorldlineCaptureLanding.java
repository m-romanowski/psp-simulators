package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum WorldlineCaptureLanding {
  CAPTURED_9(
      WorldlineStatus.CAPTURED, WorldlineStatus.CATEGORY_COMPLETED, WorldlineStatus.CODE_CAPTURED),

  CAPTURE_REQUESTED_91(
      WorldlineStatus.CAPTURE_REQUESTED,
      WorldlineStatus.CATEGORY_PENDING_MERCHANT,
      WorldlineStatus.CODE_CAPTURE_REQUESTED),

  PENDING_CAPTURE_9(
      WorldlineStatus.PENDING_CAPTURE,
      WorldlineStatus.CATEGORY_PENDING_MERCHANT,
      WorldlineStatus.CODE_CAPTURED);

  static final String SETTING = "worldline.capture-landing";

  private final String nativeStatus;
  private final String statusCategory;
  private final int statusCode;

  WorldlineCaptureLanding(String nativeStatus, String statusCategory, int statusCode) {
    this.nativeStatus = nativeStatus;
    this.statusCategory = statusCategory;
    this.statusCode = statusCode;
  }

  String nativeStatus() {
    return nativeStatus;
  }

  String statusCategory() {
    return statusCategory;
  }

  int statusCode() {
    return statusCode;
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            CAPTURED_9.name(),
            "a successful capture comes to rest on status CAPTURED, in category COMPLETED, with"
                + " statusCode 9"),
        new AcceptedValue(
            CAPTURE_REQUESTED_91.name(),
            "a successful capture comes to rest on status CAPTURE_REQUESTED, in category"
                + " PENDING_MERCHANT, with statusCode 91"),
        new AcceptedValue(
            PENDING_CAPTURE_9.name(),
            "a successful capture comes to rest on status PENDING_CAPTURE, in category"
                + " PENDING_MERCHANT, with statusCode 9"));
  }

  static WorldlineCaptureLanding of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return CAPTURED_9;
    }
    for (WorldlineCaptureLanding configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names none of the three landings provider worldline publishes for a successful"
            + " capture. "
            + WorldlineStatus.A_SUCCESSFUL_CAPTURE_IS_PUBLISHED_FOUR_WAYS
            + ". Two of the four publications agree, so the three values are "
            + CAPTURED_9.name()
            + ", the default and the reading of both the GetPayment/CreatePayment outcome table"
            + " and the GetHostedCheckoutStatus sample, "
            + CAPTURE_REQUESTED_91.name()
            + ", the reading of the CapturePayment/GetCapture table and of the prose for code 5,"
            + " and "
            + PENDING_CAPTURE_9.name()
            + ", the reading of the authorisation-and-direct-sale page");
  }
}
