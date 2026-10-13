package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.util.List;
import java.util.Map;

final class GrowHostedPage {

  static final String PATH = "/far";

  static final String LINK = GrowFields.HOSTED_LINK;

  static final String OUTCOME = "outcome";

  static final String CARD = "card";

  static final String CONTENT_TYPE = "text/html; charset=utf-8";

  static final String WHAT_THIS_PAGE_PERMITS =
      WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE;

  static final Map<String, String> DEFENCE_HEADERS =
      WhatAPayerPageMayLoad.onADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  static final Map<String, String> NOTHING_A_REDIRECT_DECLARES = Map.of();

  static final String TITLE = "Pay";

  static final String CARD_LABEL = "Card";

  static final String NO_SUCH_LINK =
      "This payment link does not belong to any payment process this simulator holds. A link is"
          + " minted by createPaymentProcess and is forgotten when its test run is reset.";

  private static final String STYLE =
      "body{margin:0;background:#f5f4f2;color:#1a1a1a;font:16px/1.5 system-ui,sans-serif}"
          + "main{box-sizing:border-box;max-width:28rem;margin:0 auto;padding:1rem}"
          + "h1{font-size:1.25rem;margin:0 0 .75rem}"
          + "p{margin:0 0 .5rem;overflow-wrap:anywhere}"
          + "label,input,select,button{display:block;box-sizing:border-box;width:100%}"
          + "label{margin:.75rem 0 .25rem}"
          + "input,select,button{min-height:44px;margin:.5rem 0;font:inherit}"
          + "button{border:0;border-radius:.375rem;background:#1f6f4a;color:#fff}"
          + "button.secondary{background:#e4e2de;color:#1a1a1a}";

  private static final Map<PayerOutcome, String> WHAT_A_BUTTON_SAYS =
      Map.of(
          PayerOutcome.PAID,
          "paid on the page",
          PayerOutcome.DECLINED,
          "met a decline on the page",
          PayerOutcome.ABANDONED,
          "left the page without paying");

  private GrowHostedPage() {}

  static String urlFor(String publicBaseUrl, String hostedLink) {
    return publicBaseUrl + PATH + "?" + LINK + "=" + GrowCallbackForm.encode(hostedLink);
  }

  static String offering(GrowPaymentProcess process, List<GrowPayerCard> cards) {
    StringBuilder options = new StringBuilder();
    for (GrowPayerCard card : cards) {
      options
          .append("<option value=\"")
          .append(escaped(card.alias()))
          .append("\">")
          .append(escaped(card.describedForAPayer()))
          .append("</option>");
    }
    return page(
        escaped(process.description()),
        "<h1>"
            + TITLE
            + "</h1>"
            + "<p>"
            + escaped(process.description())
            + "</p>"
            + "<p>"
            + escaped(process.chargeSum())
            + "</p>"
            + "<p>"
            + escaped(process.payerFullName())
            + "</p>"
            + "<form method=\"post\" action=\""
            + PATH
            + "\">"
            + "<input type=\"hidden\" name=\""
            + LINK
            + "\" value=\""
            + escaped(process.hostedLink())
            + "\">"
            + "<label for=\""
            + CARD
            + "\">"
            + CARD_LABEL
            + "</label>"
            + "<select id=\""
            + CARD
            + "\" name=\""
            + CARD
            + "\">"
            + options
            + "</select>"
            + outcomeButtons()
            + "</form>");
  }

  private static String outcomeButtons() {
    StringBuilder buttons = new StringBuilder();
    for (PayerOutcome outcome : GrowHostedCompletionOffer.THE_PAYER_STEPS.theStepsAPayerMayTake()) {
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
                  GrowHostedCompletionOffer.THE_PAYER_STEPS.asItsOwnPageSaysIt(
                      WHAT_A_BUTTON_SAYS, outcome)))
          .append("</button>");
    }
    return buttons.toString();
  }

  static String refusing(String inItsOwnWords) {
    return page(TITLE, "<h1>" + TITLE + "</h1><p>" + escaped(inItsOwnWords) + "</p>");
  }

  static String redirectAfter(GrowPaymentProcess process, PayerOutcome outcome) {
    String target = outcome == PayerOutcome.PAID ? process.successUrl() : process.cancelUrl();
    StringBuilder redirect = new StringBuilder(target);
    char separator = target.contains("?") ? '&' : '?';
    Map<String, String> customFields = process.customFields();
    for (String name : GrowFields.CUSTOM_FIELDS) {
      String value = customFields.get(name);
      if (value != null) {
        redirect
            .append(separator)
            .append(GrowCallbackForm.encode(name))
            .append('=')
            .append(GrowCallbackForm.encode(value));
        separator = '&';
      }
    }
    return redirect.toString();
  }

  private static String page(String title, String body) {
    return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        + "<title>"
        + title
        + "</title><style>"
        + STYLE
        + "</style></head><body><main>"
        + body
        + "</main></body></html>";
  }

  static String escaped(String text) {
    StringBuilder safe = new StringBuilder();
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      switch (character) {
        case '&' -> safe.append("&amp;");
        case '<' -> safe.append("&lt;");
        case '>' -> safe.append("&gt;");
        case '"' -> safe.append("&quot;");
        case '\'' -> safe.append("&#39;");
        default -> safe.append(character);
      }
    }
    return safe.toString();
  }
}
