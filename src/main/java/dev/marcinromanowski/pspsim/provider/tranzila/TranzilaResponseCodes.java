package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;

final class TranzilaResponseCodes {

  private TranzilaResponseCodes() {}

  static final String APPROVED = "000";

  static final String OPERATION_COMPLETED = "777";

  static final String HIGHER_THAN_THE_APPROVED_AMOUNT = "507";

  static final String APPROVED_TEXT = "Transaction approved.";

  static final String OPERATION_COMPLETED_TEXT =
      "operation completed (success code for operations in which no transaction is recorded,"
          + " including J2 and J5)";

  static final String HIGHER_THAN_THE_APPROVED_AMOUNT_TEXT =
      "actual transaction amount higher than the approved amount";

  static final String THREE_D_SECURE_FAILED = "900";

  static final String THREE_D_SECURE_FAILED_TEXT = "Transaction failed during 3D Secure validation";

  static final SequencedMap<String, String> THE_DECLINES_A_SCENARIO_MAY_NAME = declines();

  static final SequencedMap<String, String> THE_3DS_FAILURES = threeDSecureFailures();

  static final String A_PROCESSOR_DECLINE = "00[1-6]|01[2567]";

  static final String A_3DS_FAILURE = "9[01][0-9]|92[0-8]|930";

  static final String THE_DECLINE_SHAPE = A_PROCESSOR_DECLINE + "|" + A_3DS_FAILURE;

  static final String THE_PROCESSOR_DECLINES =
      "a processor_response_code of the published code table that declines a card: 001 blocked,"
          + " 002 stolen, 003 call the credit company, 004 refusal, 005 forged, 006 incorrect ID"
          + " or CVV, 012 card not authorised for the terminal, 015 expired, 016 unauthorised"
          + " currency or 017 unauthorised credit type. Three digits, as the table writes them";

  static final String THE_3DS_FAILURE_CODES =
      "a code of the 3DS table, 900 to 928 or 930, which fails the card holder's authentication"
          + " and is answered as error_code. Three digits, as the table writes them";

  static final String THE_DECLINE_CODES =
      "a processor_response_code of the published code table that declines a card: 001 blocked,"
          + " 002 stolen, 003 call the credit company, 004 refusal, 005 forged, 006 incorrect ID"
          + " or CVV, 012 card not authorised for the terminal, 015 expired, 016 unauthorised"
          + " currency or 017 unauthorised credit type; or a code of the 3DS table, 900 to 928 or"
          + " 930, which fails the card holder's authentication and is answered as error_code."
          + " Three digits, as the tables write them";

  static final String BLOCKED =
      "Blocked confiscate card. Please check and enter the credit number again.";

  static final String STOLEN =
      "Stolen confiscate card. Please check and enter the credit number again.";

  static final String REFUSAL =
      "Refusal. Please contact the card owner to check the reason with his credit company.";

  static final String EXPIRED = "Expired card. Please check the expiration date again.";

  private static SequencedMap<String, String> declines() {
    SequencedMap<String, String> published = new LinkedHashMap<>();
    published.put("001", BLOCKED);
    published.put("002", STOLEN);
    published.put("003", "Contact credit company to approve the transaction.");
    published.put("004", REFUSAL);
    published.put("005", "Forged confiscate card.");
    published.put("006", "Incorrect identity number or CVV.");
    published.put("012", "Unauthorized card for this terminal.");
    published.put("015", EXPIRED);
    published.put("016", "Unauthorized currency.");
    published.put("017", "Unauthorized credit type for this transaction.");
    return published;
  }

  private static SequencedMap<String, String> threeDSecureFailures() {
    SequencedMap<String, String> published = new LinkedHashMap<>();
    published.put(THREE_D_SECURE_FAILED, THREE_D_SECURE_FAILED_TEXT);
    published.put("901", "Card authentication failed.");
    published.put("902", "Unrecognized device.");
    published.put("903", "Unsupported device.");
    published.put("904", "Authentication frequency limit exceeded.");
    published.put("905", "Expired card.");
    published.put("906", "Invalid card number.");
    published.put("907", "Invalid transaction.");
    published.put("908", "Card registration record not found.");
    published.put("909", "Security failure.");
    published.put("910", "Stolen card.");
    published.put("911", "Suspected fraud.");
    published.put("912", "Transaction not authorized for the cardholder.");
    published.put("913", "Cardholder not enrolled in the service.");
    published.put("914", "ACS transaction timeout expired.");
    published.put("915", "Low trust level.");
    published.put("916", "Medium trust level.");
    published.put("917", "High trust level.");
    published.put("918", "Very high trust level.");
    published.put("919", "Maximum ACS challenge count exceeded.");
    published.put("920", "Non-payment transaction not supported.");
    published.put("921", "3RI transaction not supported.");
    published.put("922", "Technical issue at ACS.");
    published.put(
        "923", "Decoupled authentication required by ACS but not requested by 3DS requestor.");
    published.put("924", "Maximum decoupled 3DS requestor expiry time exceeded.");
    published.put(
        "925",
        "Decoupled authentication did not have sufficient time to authenticate the cardholder.");
    published.put("926", "Authentication was attempted but not completed by the cardholder.");
    published.put("927", "Card authentication cancelled by user.");
    published.put("928", "Card authentication cancelled by user.");
    published.put("930", "Authentication was attempted but not completed by the cardholder.");
    return published;
  }

  static boolean approves(String code) {
    return APPROVED.equals(code) || OPERATION_COMPLETED.equals(code);
  }

  static String theTextOf(String code) {
    return switch (code) {
      case APPROVED -> APPROVED_TEXT;
      case OPERATION_COMPLETED -> OPERATION_COMPLETED_TEXT;
      case HIGHER_THAN_THE_APPROVED_AMOUNT -> HIGHER_THAN_THE_APPROVED_AMOUNT_TEXT;
      default ->
          Optional.ofNullable(THE_DECLINES_A_SCENARIO_MAY_NAME.get(code))
              .or(() -> Optional.ofNullable(THE_3DS_FAILURES.get(code)))
              .orElse(code);
    };
  }

  static int asAnErrorCode(String code) {
    return Integer.parseInt(code);
  }
}
