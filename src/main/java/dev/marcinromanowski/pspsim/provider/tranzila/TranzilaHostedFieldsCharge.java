package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TranzilaHostedFieldsCharge {

  static final String AN_ENTERED_CVV_NOT_CARRIED = "000";

  static final String AN_ENTERED_ID_NOT_CARRIED = "000000000";

  private TranzilaHostedFieldsCharge() {}

  static TranzilaApiCall callOf(TranzilaHostedFieldsRequest request, String token) {
    TranzilaTranmode tranmode = request.tranmode();
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TranzilaFields.TERMINAL_NAME, TranzilaJson.text(request.terminalName()));
    body.put(
        TranzilaFields.TXN_TYPE,
        TranzilaJson.text(tranmode.type().orElseThrow(IllegalStateException::new).wire()));
    Integer verifyMode = tranmode.verifyMode();
    if (verifyMode != null) {
      body.put(TranzilaFields.VERIFY_MODE, TranzilaJson.number(verifyMode.toString()));
    }
    body.put(TranzilaFields.CARD_NUMBER, TranzilaJson.text(token));
    body.put(
        TranzilaFields.EXPIRE_MONTH, TranzilaJson.number(Integer.toString(request.expiryMonth())));
    body.put(
        TranzilaFields.EXPIRE_YEAR, TranzilaJson.number(Integer.toString(request.expiryYear())));
    if (request.card().cvvEntered()) {
      body.put(TranzilaFields.CVV, TranzilaJson.text(AN_ENTERED_CVV_NOT_CARRIED));
    }
    if (request.card().idEntered()) {
      body.put(TranzilaFields.CARD_HOLDER_ID, TranzilaJson.text(AN_ENTERED_ID_NOT_CARRIED));
    }
    body.put(TranzilaFields.TXN_CURRENCY_CODE, TranzilaJson.text(request.currency().alpha()));
    body.put(TranzilaFields.ITEMS, TranzilaObjectActions.itemsOf(request.amount()));
    String dcDisable = request.dcDisable();
    if (dcDisable != null) {
      Map<String, TranzilaJson> field = new LinkedHashMap<>();
      field.put(TranzilaFields.NAME, TranzilaJson.text(TranzilaFields.DC_DISABLE));
      field.put(TranzilaFields.VALUE, TranzilaJson.text(dcDisable));
      body.put(
          TranzilaFields.USER_DEFINED_FIELDS,
          TranzilaJson.array(List.of(TranzilaJson.object(field))));
    }
    TranzilaThreeDSecure secure = request.threeDSecure();
    Map<String, TranzilaJson> settings = new LinkedHashMap<>();
    settings.put(
        TranzilaThreeDSecure.FORCE_TXN_ON_3DS_FAIL,
        TranzilaJson.text(
            secure.forceTxnOnFail() ? TranzilaThreeDSecure.YES : TranzilaThreeDSecure.NO));
    settings.put(
        TranzilaThreeDSecure.FORCE_CHALLENGE,
        TranzilaJson.number(secure.forceChallenge() ? "1" : "0"));
    body.put(TranzilaFields.ACTIVATE_3DS, TranzilaJson.text(TranzilaThreeDSecure.YES));
    body.put(TranzilaThreeDSecure.THREE_DS_SETTINGS, TranzilaJson.object(settings));
    return TranzilaApiCall.of(
        TranzilaObjectActions.WIRE_METHOD,
        TranzilaHostedFieldsPage.PROCESS_ROUTE,
        Map.of(),
        TranzilaJsonWriter.toText(TranzilaJson.object(body)));
  }
}
