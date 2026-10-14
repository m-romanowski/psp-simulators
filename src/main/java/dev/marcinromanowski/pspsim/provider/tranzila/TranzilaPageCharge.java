package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TranzilaPageCharge {

  private TranzilaPageCharge() {}

  static TranzilaApiCall callOf(TranzilaPage page, TranzilaPageCards.Offered card) {
    TranzilaTxnType type =
        page.tranmode()
            .type()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "tranmode "
                            + page.tranmode().wire()
                            + " records no transaction, so the page charges nothing for it"));
    Map<String, TranzilaJson> body = new LinkedHashMap<>();
    body.put(TranzilaFields.TERMINAL_NAME, TranzilaJson.text(page.terminalName()));
    body.put(TranzilaFields.TXN_TYPE, TranzilaJson.text(type.wire()));
    Integer verifyMode = page.tranmode().verifyMode();
    if (verifyMode != null) {
      body.put(TranzilaFields.VERIFY_MODE, TranzilaJson.number(verifyMode.toString()));
    }
    body.put(TranzilaFields.CARD_NUMBER, TranzilaJson.text(card.token()));
    body.put(
        TranzilaFields.EXPIRE_MONTH, TranzilaJson.number(Integer.toString(card.expiryMonth())));
    body.put(TranzilaFields.EXPIRE_YEAR, TranzilaJson.number(Integer.toString(card.expiryYear())));
    body.put(TranzilaFields.TXN_CURRENCY_CODE, TranzilaJson.text(page.currency().alpha()));
    body.put(TranzilaFields.ITEMS, TranzilaObjectActions.itemsOf(page.sum()));
    String dcDisable = page.dcDisable();
    if (dcDisable != null) {
      Map<String, TranzilaJson> field = new LinkedHashMap<>();
      field.put(TranzilaFields.NAME, TranzilaJson.text(TranzilaFields.DC_DISABLE));
      field.put(TranzilaFields.VALUE, TranzilaJson.text(dcDisable));
      body.put(
          TranzilaFields.USER_DEFINED_FIELDS,
          TranzilaJson.array(List.of(TranzilaJson.object(field))));
    }
    return TranzilaApiCall.of(
        TranzilaObjectActions.WIRE_METHOD,
        TranzilaDirectNgPage.payerPathOf(page.terminalName(), page.pageId()),
        Map.of(),
        TranzilaJsonWriter.toText(TranzilaJson.object(body)));
  }
}
