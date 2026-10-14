package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TranzilaHostedFieldsCharge {

  static final String AN_ENTERED_CVV_NOT_CARRIED = "000";

  static final String AN_ENTERED_ID_NOT_CARRIED = "000000000";

  static final Set<String> THE_KEYS_NO_SLOT_KEEPS =
      Set.of(
          TranzilaFields.DC_DISABLE,
          TranzilaHostedFieldsRequest.NOTIFY_URL,
          "pdesc",
          "json_purchase_data",
          "force_txn_on_3ds_fail",
          "force_challenge");

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
    List<TranzilaUserField> userDefined =
        TranzilaPageCharge.theUserDefinedFieldsOf(
            request.dcDisable(), request.userFormData(), THE_KEYS_NO_SLOT_KEEPS);
    if (!userDefined.isEmpty()) {
      body.put(TranzilaFields.USER_DEFINED_FIELDS, TranzilaPageCharge.asSent(userDefined));
    }
    return TranzilaApiCall.of(
        TranzilaObjectActions.WIRE_METHOD,
        TranzilaHostedFieldsPage.PROCESS_ROUTE,
        Map.of(),
        TranzilaJsonWriter.toText(TranzilaJson.object(body)));
  }
}
