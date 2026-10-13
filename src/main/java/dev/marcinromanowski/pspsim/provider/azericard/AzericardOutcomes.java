package dev.marcinromanowski.pspsim.provider.azericard;

final class AzericardOutcomes {

  private AzericardOutcomes() {}

  static final String ACTION_SUCCEEDED = "0";

  static final String ACTION_DUPLICATE = "1";

  static final String ACTION_DECLINED = "2";

  static final String ACTION_ERROR = "3";

  static final String RC_APPROVED = "00";

  static final String RC_ACCESS_DENIED = "-17";

  static final String RC_TRANSACTION_CONTEXT_MISMATCH = "-24";

  static final String RC_NO_SUCH_TRANSACTION = "-2";

  static final String RC_NOT_ADMITTED = "-3";

  static final String RC_NO_SUCH_TOKEN = "-4";

  static final String RC_OUTSIDE_THE_STATUS_WINDOW = "-5";

  static final String RC_MORE_THAN_ONE_TRANSACTION = "-6";

  static final String RC_CARD_NOT_ACCEPTED = "ZZ";

  static final String DESC_APPROVED = "Approved";

  static final String DESC_ACCESS_DENIED = "Access denied";

  static final String DESC_TRANSACTION_CONTEXT_MISMATCH = "Transaction context mismatch";

  static final String NOTHING_PUBLISHED = "";

  static final String ABSENT = "";
}
