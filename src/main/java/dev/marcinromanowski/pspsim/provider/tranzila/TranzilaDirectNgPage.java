package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

final class TranzilaDirectNgPage {

  static final String HOST = "/tranzila/directng";

  static final String TERMINAL = "terminal";

  static final String PAGE = "pageId";

  static final String ROUTE = HOST + "/{" + TERMINAL + "}/iframenew.php";

  static final String PAYER_ROUTE = HOST + "/{" + TERMINAL + "}/payer/{" + PAGE + "}";

  static final ThePayerStep THE_PAYERS_OWN_SUBMISSION =
      new ThePayerStep.APageThisSimulatorServes(PAYER_ROUTE);

  static final String OUTCOME = "outcome";

  static final String CARD = "card";

  static final String CONTENT_TYPE = "text/html; charset=utf-8";

  static final String WHAT_THIS_PAGE_PERMITS =
      WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE + "; form-action 'self'";

  static final Map<String, List<String>> DEFENCE_HEADERS =
      WhatAPayerPageMayLoad.eachOnADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  static final Map<String, String> DEFENCES =
      WhatAPayerPageMayLoad.onADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  static final String TITLE = "Tranzila payment page";

  static final String NO_SUCH_TERMINAL =
      "No terminal of this name is configured in any test run of this simulator. The address is"
          + " /tranzila/directng/{terminal}/iframenew.php, where terminal is the terminal_name of"
          + " a configured account.";

  static final String NO_SUCH_PAGE =
      "This payment page belongs to no page this simulator holds. A page is opened by sending the"
          + " payment parameters to /tranzila/directng/{terminal}/iframenew.php, and it is"
          + " forgotten when its test run is reset.";

  static final String NO_SUCH_ADDRESS =
      "No page of provider tranzila is served at this address. The payment page is"
          + " /tranzila/directng/{terminal}/iframenew.php.";

  static final String NO_SUCH_METHOD =
      "This address of provider tranzila does not answer this HTTP method. The payment page takes"
          + " GET or POST, and the payer's submission on it takes POST only.";

  static final String ILLEGAL_OPERATION = "Illegal Operation 912791";

  static final String THE_DUPLICATE =
      "This payment has already been paid. Please contact customer service.";

  static final String ALREADY_REACHED =
      "This payment page has already reached an outcome, and a payer completes a page once.";

  static final String BEING_COMPLETED =
      "This payment page is being completed by another submission right now; nothing more was"
          + " charged.";

  static final String THE_CARDS_ARE_TEST_CARDS =
      "Pick the card the payer pays with. This simulated page never asks for a card number: the"
          + " cards are the one tranzila publishes and the fixtures seeded into this test run.";

  static final String APPROVED = "The payment was approved.";

  static final String DECLINED = "The payment was declined.";

  static final String LEFT = "You left without paying. Nothing was charged.";

  static final Map<PayerOutcome, String> WHAT_A_BUTTON_SAYS =
      Map.of(PayerOutcome.PAID, "Pay", PayerOutcome.ABANDONED, "Leave without paying");

  private static final String STYLE =
      "body{margin:0;background:#fff;color:#1b1b1b;font:16px/1.5 system-ui,sans-serif}"
          + "main{box-sizing:border-box;max-width:28rem;margin:0 auto;padding:1rem}"
          + "h1{font-size:1.25rem;margin:0 0 .5rem}"
          + "dl{display:grid;grid-template-columns:auto 1fr;gap:.25rem 1rem;margin:0 0 1rem}"
          + "dt{font-weight:600}dd{margin:0;overflow-wrap:anywhere}"
          + "label,select,button{display:block;box-sizing:border-box;width:100%}"
          + "select,button{min-height:44px;margin:.5rem 0;font:inherit}"
          + "button{border:0;border-radius:.375rem;background:#1f4e8c;color:#fff}"
          + "button.secondary{background:#e6e6e6;color:#1b1b1b}";

  private TranzilaDirectNgPage() {}

  static String pathOf(String terminalName) {
    return HOST + "/" + terminalName + "/iframenew.php";
  }

  static String payerPathOf(String terminalName, String pageId) {
    return HOST + "/" + terminalName + "/payer/" + pageId;
  }

  static String offering(TranzilaPage page, List<PayerCard> cards) {
    StringBuilder options = new StringBuilder();
    for (PayerCard card : cards) {
      options
          .append("<option value=\"")
          .append(escaped(card.alias()))
          .append("\">")
          .append(escaped(card.label()))
          .append("</option>");
    }
    return page(
        "<h1>"
            + TITLE
            + "</h1>"
            + theAmount(page)
            + "<p>"
            + escaped(THE_CARDS_ARE_TEST_CARDS)
            + "</p><form method=\"post\" action=\""
            + escaped(payerPathOf(page.terminalName(), page.pageId()))
            + "\"><label for=\""
            + CARD
            + "\">Card</label><select id=\""
            + CARD
            + "\" name=\""
            + CARD
            + "\">"
            + options
            + "</select>"
            + buttons()
            + "</form>");
  }

  private static String theAmount(TranzilaPage page) {
    StringBuilder shown = new StringBuilder("<dl><dt>Amount</dt><dd>");
    shown.append(escaped(page.sum())).append(' ').append(page.currency().alpha()).append("</dd>");
    String description = page.echoed().get("pdesc");
    if (description != null) {
      shown.append("<dt>For</dt><dd>").append(escaped(description)).append("</dd>");
    }
    return shown.append("</dl>").toString();
  }

  private static String buttons() {
    StringBuilder buttons = new StringBuilder();
    for (PayerOutcome outcome : TranzilaDirectNg.THE_PAYER_STEPS.theStepsAPayerMayTake()) {
      buttons
          .append("<button type=\"submit\"")
          .append(outcome.namesACard() ? "" : " class=\"secondary\"")
          .append(" name=\"")
          .append(OUTCOME)
          .append("\" value=\"")
          .append(outcome.name())
          .append("\">")
          .append(
              escaped(
                  TranzilaDirectNg.THE_PAYER_STEPS.asItsOwnPageSaysIt(WHAT_A_BUTTON_SAYS, outcome)))
          .append("</button>");
    }
    return buttons.toString();
  }

  static String reporting(TranzilaPage page, Map<String, String> fields, boolean approved) {
    Map<String, List<String>> answered = new LinkedHashMap<>();
    fields.forEach((name, value) -> answered.put(name, List.of(value)));
    StringBuilder shown = new StringBuilder("<dl>");
    Redaction.redact(answered, TranzilaFields.SENSITIVE_DELIVERY_FIELDS)
        .forEach(
            (name, values) ->
                shown
                    .append("<dt>")
                    .append(escaped(name))
                    .append("</dt><dd>")
                    .append(escaped(String.join(", ", values)))
                    .append("</dd>"));
    shown.append("</dl>");
    return page(
        "<h1>"
            + TITLE
            + "</h1><p>"
            + (approved ? APPROVED : DECLINED)
            + "</p>"
            + theAmount(page)
            + shown);
  }

  static String left() {
    return refusing(LEFT);
  }

  static String refusing(String inItsOwnWords) {
    return page("<h1>" + TITLE + "</h1><p>" + escaped(inItsOwnWords) + "</p>");
  }

  private static String page(String main) {
    return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        + "<title>"
        + TITLE
        + "</title><style>"
        + STYLE
        + "</style></head><body><main>"
        + main
        + "</main></body></html>";
  }

  static String escaped(String text) {
    return HtmlUtils.htmlEscape(text);
  }
}
