package dev.marcinromanowski.pspsim.provider.autra;

final class AutraDeclineMessages {

  static final String THE_PUBLISHED_INSUFFICIENT_FUNDS_MESSAGE = "insufficient funds";

  static final String THE_CARD_WAS_DECLINED =
      "the card was declined, and provider autra publishes no msg for CARD_DECLINED anywhere: it"
          + " glosses the code as \"Recusa generica. Pode ser fraude, dados errados, banco"
          + " bloqueando\" in its own error table and prints no wire example of the string, so"
          + " this sentence is the simulator's and not a quotation";

  static final String TOKEN_IS_NO_LONGER_ELIGIBLE =
      "the card token this call named is no longer eligible";

  static final String BRAND_IS_NOT_SUPPORTED = "the card brand is not supported";

  static final String TRANSIENT_PROCESSING_ERROR = "502 : badGateway";

  static final String TRANSIENT_AUTHENTICATION_ERROR =
      "authentication failed while processing, and the reservation is untouched";

  static final String HIGHER_THAN_THE_REMAINING_BALANCE =
      "the amount this call names is higher than the remaining balance of the payment";

  static final String FROM_TODAY = "the original transaction is from today and cannot be refunded";

  static final String TOO_OLD = "the original transaction date is too old to be reversed";

  static final String CYCLE_NOT_ACCEPTED = "the original cycle is not accepted for reversal";

  private static final String NO_DECLINE_OF_THIS_CONTRACT_CARRIES_THE_CODE_HANDED_HERE =
      "a decline of this contract is an AutraDecline, and an AutraDecline cannot be declared"
          + " without the msg its code carries, so a code no AutraDecline holds is carried by no"
          + " decline this contract publishes and there is no msg to put on the wire beside it: ";

  private AutraDeclineMessages() {}

  static String of(String code, AutraMerchant merchant) {
    return AutraDecline.of(code)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    NO_DECLINE_OF_THIS_CONTRACT_CARRIES_THE_CODE_HANDED_HERE + code))
        .theMsgNoDeclineCanBeDeclaredWithout(merchant);
  }
}
