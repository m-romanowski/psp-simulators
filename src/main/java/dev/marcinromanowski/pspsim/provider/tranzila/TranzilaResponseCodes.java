package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.LinkedHashMap;
import java.util.Map;
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

  static final SequencedMap<String, String> THE_DECLINES_A_SCENARIO_MAY_NAME = declines();

  static final String THE_DECLINE_SHAPE = "00[1-6]|01[2567]";

  static final String THE_DECLINE_CODES =
      "a processor_response_code of the published code table that declines a card: 001 blocked,"
          + " 002 stolen, 003 call the credit company, 004 refusal, 005 forged, 006 incorrect ID"
          + " or CVV, 012 card not authorised for the terminal, 015 expired, 016 unauthorised"
          + " currency or 017 unauthorised credit type. Three digits, as the table writes them";

  private static SequencedMap<String, String> declines() {
    SequencedMap<String, String> published = new LinkedHashMap<>();
    published.put("001", "Blocked card.");
    published.put("002", "Stolen card.");
    published.put("003", "Call the credit company.");
    published.put("004", "Refusal.");
    published.put("005", "Forged card.");
    published.put("006", "Incorrect ID or CVV.");
    published.put("012", "The card is not authorised for this terminal.");
    published.put("015", "Expired card.");
    published.put("016", "Unauthorised currency.");
    published.put("017", "Unauthorised credit type.");
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
      default -> Optional.ofNullable(THE_DECLINES_A_SCENARIO_MAY_NAME.get(code)).orElse(code);
    };
  }

  static int asAnErrorCode(String code) {
    return Integer.parseInt(code);
  }

  static Map<String, String> declinesInOrder() {
    return THE_DECLINES_A_SCENARIO_MAY_NAME;
  }
}
