package dev.marcinromanowski.pspsim.provider.autra;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

enum AutraDecline {
  AUTHORIZER_REJECTED(AutraErrors.AUTHORIZER_REJECTED),

  INSUFFICIENT_FUNDS(AutraErrors.INSUFFICIENT_FUNDS),

  CARD_DECLINED(AutraErrors.CARD_DECLINED),

  TOKEN_NOT_AVAILABLE(AutraErrors.TOKEN_NOT_AVAILABLE),

  INVALID_CARD_BRAND(AutraErrors.INVALID_CARD_BRAND),

  MSG_GENERAL_ERROR(AutraErrors.MSG_GENERAL_ERROR),

  UNAUTHORIZED(AutraErrors.UNAUTHORIZED),

  AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE(
      AutraErrors.AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE),

  CANT_REFUND_TRANSACTION_FROM_TODAY(AutraErrors.CANT_REFUND_TRANSACTION_FROM_TODAY),

  ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD(AutraErrors.ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD),

  CARD_BRAND_NOT_SUPPORTED(AutraErrors.CARD_BRAND_NOT_SUPPORTED),

  ORIGINAL_CYCLE_NOT_ACCEPTED(AutraErrors.ORIGINAL_CYCLE_NOT_ACCEPTED);

  private final String code;

  AutraDecline(String code) {
    this.code = code;
  }

  String code() {
    return code;
  }

  String theMsgNoDeclineCanBeDeclaredWithout(AutraMerchant merchant) {
    return switch (this) {
      case AUTHORIZER_REJECTED -> merchant.authorizerRejectedMessage();
      case INSUFFICIENT_FUNDS -> AutraDeclineMessages.THE_PUBLISHED_INSUFFICIENT_FUNDS_MESSAGE;
      case CARD_DECLINED -> AutraDeclineMessages.THE_CARD_WAS_DECLINED;
      case TOKEN_NOT_AVAILABLE -> AutraDeclineMessages.TOKEN_IS_NO_LONGER_ELIGIBLE;
      case INVALID_CARD_BRAND -> AutraDeclineMessages.BRAND_IS_NOT_SUPPORTED;
      case MSG_GENERAL_ERROR -> AutraDeclineMessages.TRANSIENT_PROCESSING_ERROR;
      case UNAUTHORIZED -> AutraDeclineMessages.TRANSIENT_AUTHENTICATION_ERROR;
      case AMOUNT_IS_HIGHER_THAN_THE_REMAINING_BALANCE ->
          AutraDeclineMessages.HIGHER_THAN_THE_REMAINING_BALANCE;
      case CANT_REFUND_TRANSACTION_FROM_TODAY -> AutraDeclineMessages.FROM_TODAY;
      case ORIGINAL_TRANSACTION_DATE_IS_TOO_OLD -> AutraDeclineMessages.TOO_OLD;
      case CARD_BRAND_NOT_SUPPORTED -> AutraDeclineMessages.BRAND_IS_NOT_SUPPORTED;
      case ORIGINAL_CYCLE_NOT_ACCEPTED -> AutraDeclineMessages.CYCLE_NOT_ACCEPTED;
    };
  }

  static Optional<AutraDecline> of(String code) {
    for (AutraDecline decline : values()) {
      if (decline.code.equals(code)) {
        return Optional.of(decline);
      }
    }
    return Optional.empty();
  }

  static List<String> theCodesADeclineOfThisContractCarries() {
    List<String> codes = new ArrayList<>();
    for (AutraDecline decline : values()) {
      codes.add(decline.code());
    }
    return List.copyOf(codes);
  }
}
