package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import java.util.List;

final class WorldlineStatus {

  private WorldlineStatus() {}

  static final String CREATED = "CREATED";

  static final String PENDING_CAPTURE = "PENDING_CAPTURE";

  static final String CAPTURE_REQUESTED = "CAPTURE_REQUESTED";

  static final String CAPTURED = "CAPTURED";

  static final String REJECTED = "REJECTED";

  static final String REJECTED_CAPTURE = "REJECTED_CAPTURE";

  static final String CANCELLED = "CANCELLED";

  static final String CANCELLATION_REJECTED = "CANCELLATION_REJECTED";

  static final String CATEGORY_CREATED = "CREATED";

  static final String CATEGORY_PENDING_MERCHANT = "PENDING_MERCHANT";

  static final String CATEGORY_UNSUCCESSFUL = "UNSUCCESSFUL";

  static final String CATEGORY_COMPLETED = "COMPLETED";

  static final int CODE_CREATED = 0;

  static final int CODE_REJECTED = 2;

  static final int CODE_PENDING_CAPTURE = 5;

  static final int CODE_CANCELLED = 6;

  static final int CODE_CAPTURED = 9;

  static final int CODE_CANCELLED_WAITING = 61;

  static final int CODE_CANCELLATION_REJECTED = 63;

  static final int CODE_CAPTURE_REQUESTED = 91;

  static final int CODE_REJECTED_CAPTURE = 93;

  static final String AWAITING_THE_PAYER = "AWAITING_THE_PAYER";

  static final String EXPIRED = "EXPIRED";

  static final String TOKEN_CREATED = "CREATED";

  static final String TOKEN_UPDATED = "UPDATED";

  static final String TOKEN_UNCHANGED = "UNCHANGED";

  static final List<String> THE_PUBLISHED_TOKEN_STATUSES =
      List.of(TOKEN_UNCHANGED, TOKEN_CREATED, TOKEN_UPDATED);

  static final String NO_STATUS_IS_PUBLISHED_FOR_A_TOKENIZATION_SESSION =
      "provider worldline publishes no status vocabulary for a hosted tokenization session"
          + " itself. The create answers hostedTokenizationId, hostedTokenizationUrl and"
          + " partialRedirectUrl and no status field, and the only value the read carries is"
          + " tokenStatus, which describes the token and has three published values —"
          + " \"UNCHANGED - The token has not changed\", \"CREATED - The token has been created\""
          + " and \"UPDATED - The token has been updated\". The two other values this simulator"
          + " carries are therefore its own: "
          + AWAITING_THE_PAYER
          + " for a session no payer has finished, and "
          + EXPIRED
          + " for one past the lifetime the vendor does publish, \"Sessions have a maximum life"
          + " span of 3 hours\"";

  static final String A_SUCCESSFUL_CAPTURE_IS_PUBLISHED_FOUR_WAYS =
      "provider worldline publishes the status a successful capture lands in four times and the"
          + " four do not agree. The GetPayment/CreatePayment outcome table pairs CAPTURED with"
          + " statusCategory COMPLETED and statusCode 9. The GetHostedCheckoutStatus sample pairs"
          + " the same three values the same way. The CapturePayment/GetCapture table publishes"
          + " no successful capture status at all and lists CAPTURE_REQUESTED at 4, 91, 92 and 99"
          + " beside REJECTED_CAPTURE at 93, with the prose for code 5 stating the transition"
          + " outright: \"The subsequent status will be statusOutput.statusCode=91\". The"
          + " authorisation-and-direct-sale page pairs code 9 with PENDING_CAPTURE instead:"
          + " \"Successful transactions will have status=\"PENDING_CAPTURE\" /"
          + " statusOutput.statusCategory=\"PENDING_MERCHANT\" / statusOutput.statusCode=9\"."
          + " Two of the four give a different status string to one statusCode, so no simulator"
          + " can satisfy them all";

  static NormalizedStatus normalizedPaymentStatus(String nativeStatus) {
    return switch (nativeStatus) {
      case CREATED -> NormalizedStatus.PROCESSING;
      case PENDING_CAPTURE -> NormalizedStatus.READY_FOR_CAPTURE;
      case CAPTURE_REQUESTED -> NormalizedStatus.PROCESSING;
      case CAPTURED -> NormalizedStatus.SUCCEEDED;
      case REJECTED -> NormalizedStatus.FAILED;
      case REJECTED_CAPTURE -> NormalizedStatus.READY_FOR_CAPTURE;
      case CANCELLED -> NormalizedStatus.CANCELLED;
      case CANCELLATION_REJECTED -> NormalizedStatus.READY_FOR_CAPTURE;
      default -> NormalizedStatus.UNKNOWN;
    };
  }

  static NormalizedStatus normalizedTokenizationStatus(String nativeStatus) {
    return switch (nativeStatus) {
      case AWAITING_THE_PAYER -> NormalizedStatus.PROCESSING;
      case TOKEN_CREATED -> NormalizedStatus.SUCCEEDED;
      case EXPIRED -> NormalizedStatus.CANCELLED;
      default -> NormalizedStatus.UNKNOWN;
    };
  }
}
