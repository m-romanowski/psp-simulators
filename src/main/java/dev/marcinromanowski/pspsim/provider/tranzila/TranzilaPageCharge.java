package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class TranzilaPageCharge {

  static final Set<String> THE_PARAMETERS_THE_PAGE_READS =
      Set.of(
          TranzilaFields.SUM,
          TranzilaFields.CURRENCY,
          TranzilaFields.TRANMODE,
          TranzilaFields.CRED_TYPE,
          TranzilaFields.SUCCESS_URL_ADDRESS,
          TranzilaFields.FAIL_URL_ADDRESS,
          TranzilaFields.NOTIFY_URL_ADDRESS,
          TranzilaFields.DC_DISABLE,
          TranzilaFields.THTK,
          TranzilaFields.MYID);

  private TranzilaPageCharge() {}

  static List<TranzilaUserField> theUserDefinedFieldsOf(
      @Nullable String dcDisable, Map<String, String> sent, Set<String> read) {
    List<TranzilaUserField> fields = new ArrayList<>();
    if (dcDisable != null) {
      fields.add(new TranzilaUserField(TranzilaFields.DC_DISABLE, dcDisable));
    }
    sent.forEach(
        (name, value) -> {
          if (!read.contains(name)) {
            fields.add(new TranzilaUserField(name, value));
          }
        });
    return List.copyOf(fields);
  }

  static TranzilaJson asSent(List<TranzilaUserField> fields) {
    List<TranzilaJson> sent = new ArrayList<>();
    for (TranzilaUserField field : fields) {
      Map<String, TranzilaJson> named = new LinkedHashMap<>();
      named.put(TranzilaFields.NAME, TranzilaJson.text(field.name()));
      named.put(TranzilaFields.VALUE, TranzilaJson.text(field.value()));
      sent.add(TranzilaJson.object(named));
    }
    return TranzilaJson.array(sent);
  }

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
    List<TranzilaUserField> userDefined =
        theUserDefinedFieldsOf(page.dcDisable(), page.echoed(), THE_PARAMETERS_THE_PAGE_READS);
    if (!userDefined.isEmpty()) {
      body.put(TranzilaFields.USER_DEFINED_FIELDS, asSent(userDefined));
    }
    return TranzilaApiCall.of(
        TranzilaObjectActions.WIRE_METHOD,
        TranzilaDirectNgPage.payerPathOf(page.terminalName(), page.pageId()),
        Map.of(),
        TranzilaJsonWriter.toText(TranzilaJson.object(body)));
  }
}
