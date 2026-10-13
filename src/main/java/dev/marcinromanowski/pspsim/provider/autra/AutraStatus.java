package dev.marcinromanowski.pspsim.provider.autra;

import java.util.List;

final class AutraStatus {

  private AutraStatus() {}

  static final String ACCEPTED = "ACCEPTED";

  static final String PENDING = "PENDING";

  static final String AUTHORIZED = "AUTHORIZED";

  static final String SETTLED = "SETTLED";

  static final String EXPIRED = "EXPIRED";

  static final String CAPTURED = "CAPTURED";

  static final String DECLINED = "DECLINED";

  static final String VOIDED = "VOIDED";

  static final String PARTIALLY_VOIDED = "PARTIALLY_VOIDED";

  static final String REFUND_PENDING = "REFUND_PENDING";

  static final String PARTIAL_REFUND_PENDING = "PARTIAL_REFUND_PENDING";

  static final String THE_VENDOR_LEAVES_THIS_LIST_OPEN =
      "provider autra publishes its payment statuses as an open set and says so where it publishes"
          + " the widest of the three lists it publishes them in: \"Trate valores desconhecidos"
          + " como \"outro\" (a lista pode crescer)\". This simulator therefore carries a status as"
          + " the text the provider wrote and never as a closed Java enumeration, so a value Autra"
          + " adds after this build is stored, echoed and read back rather than refused at the"
          + " boundary";

  static final List<String> THE_VALUES_THE_LOOKUP_CALLS_USUAL =
      List.of(
          PENDING,
          ACCEPTED,
          AUTHORIZED,
          "APPROVED",
          CAPTURED,
          VOIDED,
          PARTIALLY_VOIDED,
          REFUND_PENDING,
          PARTIAL_REFUND_PENDING,
          "REFUNDED",
          SETTLED,
          "PAID",
          "FAILED",
          "ERROR",
          EXPIRED,
          DECLINED,
          "NOT_FOUND");

  static final List<String> THE_PIX_STATE_MACHINE = List.of(PENDING, AUTHORIZED, SETTLED, EXPIRED);

  static final List<String> THE_TERMINAL_PIX_STATES = List.of(SETTLED, EXPIRED);

  static boolean isATerminalPixState(String status) {
    return THE_TERMINAL_PIX_STATES.contains(status);
  }

  static boolean isOneTheVendorHasPublished(String status) {
    return THE_VALUES_THE_LOOKUP_CALLS_USUAL.contains(status);
  }

  static String describedAsTheVendorWouldHaveAReaderTreatIt(String status) {
    return isOneTheVendorHasPublished(status)
        ? "holds " + status
        : "holds "
            + status
            + ", which provider autra publishes nowhere: "
            + THE_VENDOR_LEAVES_THIS_LIST_OPEN;
  }

  static boolean releasesTheOrderForReuse(String status) {
    return "FAILED".equals(status)
        || "ERROR".equals(status)
        || EXPIRED.equals(status)
        || DECLINED.equals(status);
  }
}
