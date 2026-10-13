package dev.marcinromanowski.pspsim.provider.tranzila;

final class TranzilaErrors {

  private TranzilaErrors() {}

  static final String AUTHENTICATION_FAILED = "20000";

  static final String INVALID_JSON_FORMAT = "20001";

  static final String AUTHORIZATION_FAILED = "20002";

  static final String JSON_SCHEMA_MISMATCH = "20004";

  static final String RESOURCE_NOT_FOUND = "404";

  static final String METHOD_NOT_ALLOWED = "405";

  static final String NOT_SERVED = "501";

  static final String THREE_D_SECURE_IS_NOT_SERVED =
      "activate_3ds Y asks for 3-D Secure, which this simulator does not perform on this route;"
          + " nothing was charged";

  static final String ALREADY_CREDITED_TEXT = "Already Credited this transaction id";

  static final String SUM_IS_LARGER_TEXT = "Sum is larger than the original debit transaction";

  static final String AUTHENTICATION_FAILED_TEXT = "Authentication failed";

  static final String INVALID_JSON_FORMAT_TEXT = "Invalid JSON Format";

  static final String AUTHORIZATION_FAILED_TEXT = "Authorization failed";

  static final String JSON_SCHEMA_MISMATCH_TEXT = "JSON Schema mismatch";

  static final String RESOURCE_NOT_FOUND_TEXT = "The specified resource was not found";

  static final int TOKEN_CHECK_FAILURE = 20111;

  static final int ORIGINAL_NOT_FOUND = 20112;

  static final int INDEX_WAS_EMPTY = 21101;

  static final int AUTHORIZATION_NUMBER_DIFFERS = 22100;

  static final int AUTHORIZATION_NUMBER_EMPTY = 22101;

  static final int INVALID_DC_DISABLE = 22103;

  static final int ALREADY_CANCELED = 23001;

  static final int ALREADY_CREDITED = 23002;

  static final String DUPLICATE_PAYMENT =
      "This payment has already been made, please contact the business";

  static String theTextOf(int applicationErrorCode) {
    return switch (applicationErrorCode) {
      case TOKEN_CHECK_FAILURE -> "Provided token check failure";
      case ORIGINAL_NOT_FOUND -> "Original transaction not found";
      case INDEX_WAS_EMPTY -> "Provided index was empty";
      case AUTHORIZATION_NUMBER_DIFFERS ->
          "Authorization number is not equal to the transaction value";
      case AUTHORIZATION_NUMBER_EMPTY -> "Empty authorization number";
      case INVALID_DC_DISABLE -> "Invalid DCdisable";
      case ALREADY_CANCELED -> "Attempting to credit a transaction that has already been canceled";
      case ALREADY_CREDITED -> SUM_IS_LARGER_TEXT;
      default ->
          throw new IllegalArgumentException(
              applicationErrorCode + " is no application error code this simulator answers");
    };
  }
}
