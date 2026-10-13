package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

final class AutraCardPage {

  static final String SESSIONS = "/tokenize/sessions/";

  static final String SESSION = "hostedCardSessionId";

  static final String PATH = SESSIONS + "{" + SESSION + "}";

  static final String TOKEN = "token";

  static final String OUTCOME = "outcome";

  static final String CARD = "card";

  static final String RESULT = "autra-card-tokenized-result";

  static final String CONTENT_TYPE = "text/html; charset=utf-8";

  static final String WHAT_THIS_PAGE_PERMITS =
      WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE + "; form-action 'self'";

  static final Map<String, String> DEFENCE_HEADERS =
      WhatAPayerPageMayLoad.onADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  private static final Map<PayerOutcome, String> LABELS =
      Map.of(PayerOutcome.PAID, "Save this card", PayerOutcome.ABANDONED, "Leave without saving");

  static final String NO_SUCH_PAGE =
      "This card page belongs to no hosted card session this simulator holds. Its link is the"
          + " hostedCardUrl POST /v1/acquiring/card-sessions answered, and it is forgotten when"
          + " its test run is reset.";

  static final String SAVED =
      "The card is saved. The result below is what provider autra hands the merchant's app as"
          + " AutraCardTokenizedResult.";

  static final String LEFT =
      "You left without saving a card. This hosted card session has expired; the merchant's app"
          + " opens a new one to try again.";

  private static final String STYLE =
      "body{font-family:system-ui,sans-serif;margin:0;padding:16px;max-width:480px}"
          + "form{display:flex;flex-direction:column;gap:12px;margin:0 0 16px}"
          + "select,button{font-size:16px;padding:12px;width:100%;box-sizing:border-box}"
          + "pre{white-space:pre-wrap;word-break:break-all}";

  private AutraCardPage() {}

  static String urlFor(String publicBaseUrl, String cardSessionId, String pageToken) {
    return publicBaseUrl + pathOf(cardSessionId) + "?" + TOKEN + "=" + pageToken;
  }

  static String pathOf(String cardSessionId) {
    return SESSIONS + cardSessionId;
  }

  static String offering(String cardSessionId, String pageToken, List<PayerCard> cards) {
    StringBuilder forms = new StringBuilder();
    for (PayerOutcome outcome : TokenizeOnTheHostedPage.THE_PAYER_STEPS.theStepsAPayerMayTake()) {
      forms
          .append("<form method=\"post\" action=\"")
          .append(escaped(cardSessionId))
          .append("\"><input type=\"hidden\" name=\"")
          .append(TOKEN)
          .append("\" value=\"")
          .append(escaped(pageToken))
          .append("\">");
      if (outcome.namesACard()) {
        forms.append(cardsToChooseFrom(cards));
      }
      forms
          .append("<button type=\"submit\" name=\"")
          .append(OUTCOME)
          .append("\" value=\"")
          .append(outcome.name())
          .append("\">")
          .append(
              escaped(TokenizeOnTheHostedPage.THE_PAYER_STEPS.asItsOwnPageSaysIt(LABELS, outcome)))
          .append("</button></form>");
    }
    return page("<h1>Save a card</h1>" + forms);
  }

  private static String cardsToChooseFrom(List<PayerCard> cards) {
    StringBuilder options = new StringBuilder();
    for (PayerCard card : cards) {
      options
          .append("<option value=\"")
          .append(escaped(card.alias()))
          .append("\">")
          .append(escaped(card.label()))
          .append("</option>");
    }
    return "<label for=\""
        + CARD
        + "\">Card</label><select id=\""
        + CARD
        + "\" name=\""
        + CARD
        + "\">"
        + options
        + "</select>";
  }

  static String tokenized(AutraJson.JsonObject result) {
    return page(
        "<h1>Card saved</h1><p>"
            + escaped(SAVED)
            + "</p><pre id=\""
            + RESULT
            + "\">"
            + escaped(AutraJsonWriter.toText(result))
            + "</pre>");
  }

  static String left() {
    return page("<h1>No card saved</h1><p>" + escaped(LEFT) + "</p>");
  }

  static String refusing(String inItsOwnWords) {
    return page("<h1>This card page cannot go on</h1><p>" + escaped(inItsOwnWords) + "</p>");
  }

  private static String page(String body) {
    return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        + "<title>Autra card page</title><style>"
        + STYLE
        + "</style></head><body>"
        + body
        + "</body></html>";
  }

  private static String escaped(String text) {
    return HtmlUtils.htmlEscape(text);
  }
}
