package dev.marcinromanowski.pspsim.provider.grow;

final class GrowErrors {

  private GrowErrors() {}

  static final String ERROR_ID_SHAPE =
      "two to four digits with no sign and no leading zero, the shape of every err.id provider grow"
          + " publishes or has been observed answering";

  static final String ERROR_ID_PATTERN = "[1-9][0-9]{1,3}";

  static GrowError missingRequiredField(String parameterName) {
    return new GrowError(54, MISSING_REQUIRED_FIELDS + parameterName);
  }

  static GrowError invalidCredentials() {
    return new GrowError(701, INVALID_CREDENTIALS);
  }

  static GrowError unknownCardToken() {
    return new GrowError(140, UNKNOWN_CARD_TOKEN);
  }

  static GrowError alreadyCarriedOut() {
    return new GrowError(712, ALREADY_CARRIED_OUT);
  }

  static GrowError recordNotFound() {
    return new GrowError(180, RECORD_NOT_FOUND);
  }

  static GrowError invalidLookupCredentials() {
    return new GrowError(716, INVALID_LOOKUP_CREDENTIALS);
  }

  static GrowError invalidProcessLookupCredentials() {
    return new GrowError(730, INVALID_PROCESS_LOOKUP_CREDENTIALS);
  }

  static GrowError noBusinessForTheUserId() {
    return new GrowError(743, NO_BUSINESS_FOR_THE_USER_ID);
  }

  static GrowError noBusinessForTheUserIdOfAStoredCardCharge() {
    return new GrowError(880, NO_MATCHING_BUSINESS_FOR_THE_USER_ID);
  }

  static GrowError theUserDoesNotExist() {
    return new GrowError(104, USER_DOES_NOT_EXIST);
  }

  static GrowError invalidUniqueIdentifier() {
    return new GrowError(1013, INVALID_UNIQUE_IDENTIFIER);
  }

  static GrowError invalidBusinessIdentificationCode() {
    return new GrowError(801, INVALID_BUSINESS_IDENTIFICATION_CODE);
  }

  static GrowError eitherUserIdOrPageCodeIsRequired() {
    return new GrowError(724, EITHER_USER_ID_OR_PAGE_CODE);
  }

  static GrowError holdExpired() {
    return new GrowError(803, HOLD_EXPIRED);
  }

  static GrowError aboveTheFrameCapture() {
    return new GrowError(804, ABOVE_THE_FRAME_CAPTURE);
  }

  static GrowError alreadyReleased() {
    return new GrowError(808, ALREADY_RELEASED);
  }

  static GrowError notOfAReleasableType() {
    return new GrowError(810, NOT_OF_A_RELEASABLE_TYPE);
  }

  static GrowError notCarriedOut() {
    return new GrowError(813, NOT_CARRIED_OUT);
  }

  static GrowError aboveThirtyPercentOfTheTransaction() {
    return new GrowError(814, ABOVE_THIRTY_PERCENT);
  }

  static GrowError aboveTheOriginalAmount() {
    return new GrowError(105, ABOVE_THE_ORIGINAL_AMOUNT);
  }

  static GrowError aboveTheChargeAmount() {
    return new GrowError(190, ABOVE_THE_CHARGE_AMOUNT);
  }

  static GrowError aboveTheTransactionAmount() {
    return new GrowError(218, ABOVE_THE_TRANSACTION_AMOUNT);
  }

  static GrowError noBalanceToFundTheRefund() {
    return new GrowError(115, NO_BALANCE_FOR_THE_REFUND);
  }

  static GrowError partialRefundOfATransactionMadeToday() {
    return new GrowError(130, PARTIAL_REFUND_TODAY);
  }

  static GrowError alreadyRefunded() {
    return new GrowError(210, ALREADY_REFUNDED);
  }

  static GrowError refundAmountNotValid() {
    return new GrowError(215, REFUND_AMOUNT_NOT_VALID);
  }

  static GrowError bothInstalmentFields() {
    return new GrowError(736, BOTH_INSTALMENT_FIELDS);
  }

  static GrowError amountNotValid() {
    return new GrowError(707, AMOUNT_NOT_VALID);
  }

  static GrowError chosenByScenario(String providerCode) {
    return GrowDecline.of(providerCode)
        .map(GrowDecline::error)
        .orElseGet(() -> new GrowError(Integer.parseInt(providerCode), NO_WIRE_TEXT_RECORDED));
  }

  private static final String MISSING_REQUIRED_FIELDS = "חסרים נתונים:";

  private static final String INVALID_CREDENTIALS = "פרמטר קוד זיהוי אינו תקין userId";

  private static final String UNKNOWN_CARD_TOKEN =
      "לא ניתן לבצע תשלום עם טוקן, קוד מזהה טוקן אינו תקין";

  private static final String ALREADY_CARRIED_OUT = "העסקה כבר בוצעה";

  private static final String RECORD_NOT_FOUND = "פעולת עידכון לא בוצעה, רשומה לא נמצאה";

  private static final String INVALID_LOOKUP_CREDENTIALS = "פרמטר קוד עסקה או תוקן אינו תקין";

  private static final String INVALID_PROCESS_LOOKUP_CREDENTIALS =
      "פרמטר קוד תהליך או תוקן אינו תקין";

  private static final String BOTH_INSTALMENT_FIELDS =
      "לא ניתן לשלוח את הפרמטרים paymentNum ו-maxPaymentNum באותה קריאה";

  private static final String AMOUNT_NOT_VALID = "הסכום אינו תקין";

  private static final String ABOVE_THE_ORIGINAL_AMOUNT =
      "לא ניתן לבצע זיכוי על סכום הגדול מהסכום המקורי";

  private static final String ABOVE_THE_CHARGE_AMOUNT =
      "לא ניתן לבצע זיכוי, סכום הזיכוי גדול מסכום החיוב";

  private static final String ABOVE_THE_TRANSACTION_AMOUNT = "סכום הזיכוי גדול מסכום העסקה";

  private static final String NO_BALANCE_FOR_THE_REFUND =
      "אין אשראי מספק לביצוע הזיכוי. אנא פנה לשירות הלקוחות בטלפון 3144*.";

  private static final String PARTIAL_REFUND_TODAY = "לא ניתן לבצע זיכוי חלקי על עסקה שבוצעה היום";

  private static final String ALREADY_REFUNDED = "בוצע זיכוי לעסקה זו, לא ניתן לזכות בשנית.";

  private static final String REFUND_AMOUNT_NOT_VALID = "הסכום לזיכוי אינו תקין";

  private static final String NO_WIRE_TEXT_RECORDED = "";

  private static final String NO_BUSINESS_FOR_THE_USER_ID = "אין עסק תואם ל userId שנשלח";

  private static final String NO_MATCHING_BUSINESS_FOR_THE_USER_ID = "לא נמצא עסק מתאים לUserId";

  private static final String USER_DOES_NOT_EXIST = "משתמש זה אינו קיים במערכת";

  private static final String INVALID_UNIQUE_IDENTIFIER =
      "מזהה לא תקין. על המזהה להיות מספר שלם חיובי, ללא אפסים מובילים ושניתן לייצוג ב32 ביט (כלומר,"
          + " קטן מ 4,294,967,295)";

  private static final String INVALID_BUSINESS_IDENTIFICATION_CODE = "קוד זיהוי עסק אינו תקין";

  private static final String EITHER_USER_ID_OR_PAGE_CODE = "נדרש שדה userId או pageCode";

  private static final String HOLD_EXPIRED = "J5 פג תוקף";

  private static final String ABOVE_THE_FRAME_CAPTURE =
      "לא ניתן לשחרר עסקה בסכום הגבוה מתפיסת המסגרת";

  private static final String ALREADY_RELEASED = "לא ניתן לבצע שחרור לעסקה ששוחררה";

  private static final String NOT_OF_A_RELEASABLE_TYPE = "לא ניתן לבצע שחרור לסוג עסקה זה";

  private static final String NOT_CARRIED_OUT = "לא ניתן לבצע חיוב לעסקה שלא בוצעה";

  private static final String ABOVE_THIRTY_PERCENT =
      "הסכום המירבי לביצוע החיוב הוא עד 30% יותר מסכום העסקה.";

  static final GrowError CALLBACK_FAILURE = new GrowError(701, INVALID_CREDENTIALS);
}
