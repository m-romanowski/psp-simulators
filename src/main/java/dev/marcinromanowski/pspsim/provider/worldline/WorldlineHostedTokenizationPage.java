package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.PayerCard;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.util.Map;

final class WorldlineHostedTokenizationPage {

  static final String ID = "hostedTokenizationId";

  static final String ROUTE = CreateHostedTokenization.THE_PUBLISHED_FORM_PATH + "{" + ID + "}";

  static final String OUTCOME = "outcome";

  static final String CARD = "card";

  static final String CONTENT_TYPE = "text/html; charset=utf-8";

  static final String WHAT_THIS_PAGE_PERMITS =
      WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE + "; form-action 'self'";

  static final Map<String, String> DEFENCE_HEADERS =
      WhatAPayerPageMayLoad.onADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  static final String TITLE = "Store a card";

  static final String CARD_LABEL = "Test card";

  static final String NO_SUCH_SESSION =
      "This page belongs to no hosted tokenization session this simulator holds. A session is"
          + " opened by POST /v2/{merchantId}/hostedtokenizations and is forgotten when its test"
          + " run is reset.";

  static final String NO_CARD_NUMBER_IS_ASKED_FOR =
      "Pick the test card the payer stores. This simulated page never asks for a card number:"
          + " the cards are the one the published samples print and the fixtures seeded into this"
          + " test run.";

  static final Map<PayerOutcome, String> WHAT_A_BUTTON_SAYS =
      Map.of(
          PayerOutcome.PAID, "Store this card",
          PayerOutcome.ABANDONED, "Leave without storing a card");

  static final Map<PayerOutcome, String> WHAT_THE_PAYER_IS_TOLD_AFTERWARDS =
      Map.of(
          PayerOutcome.PAID,
          "The card is stored, and the shop can now read its token.",
          PayerOutcome.ABANDONED,
          "No card was stored and this session has ended.");

  private static final String STYLE =
      "body{margin:0;background:#fff;color:#1b1b1b;font:16px/1.5 system-ui,sans-serif}"
          + "main{box-sizing:border-box;max-width:28rem;margin:0 auto;padding:1rem}"
          + "h1{font-size:1.25rem;margin:0 0 .5rem}"
          + "label,select,button{display:block;box-sizing:border-box;width:100%}"
          + "select,button{min-height:44px;margin:.5rem 0;font:inherit}"
          + "button{border:0;border-radius:.375rem;background:#1f4e8c;color:#fff}"
          + "button.secondary{background:#e6e6e6;color:#1b1b1b}";

  private WorldlineHostedTokenizationPage() {}

  static String pathOf(String hostedTokenizationId) {
    return CreateHostedTokenization.THE_PUBLISHED_FORM_PATH + hostedTokenizationId;
  }

  static String offering(String hostedTokenizationId, HostedCompletionOffer.Offered offer) {
    StringBuilder options = new StringBuilder();
    for (PayerCard card : offer.cardsAPayerMayName()) {
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
            + "</h1><p>"
            + escaped(NO_CARD_NUMBER_IS_ASKED_FOR)
            + "</p><form method=\"post\" action=\""
            + escaped(pathOf(hostedTokenizationId))
            + "\"><label for=\""
            + CARD
            + "\">"
            + CARD_LABEL
            + "</label><select id=\""
            + CARD
            + "\" name=\""
            + CARD
            + "\">"
            + options
            + "</select>"
            + buttons()
            + "</form>");
  }

  private static String buttons() {
    StringBuilder buttons = new StringBuilder();
    for (PayerOutcome outcome : TokenizeOnTheHostedPage.THE_PAYER_STEPS.theStepsAPayerMayTake()) {
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
                  TokenizeOnTheHostedPage.THE_PAYER_STEPS.asItsOwnPageSaysIt(
                      WHAT_A_BUTTON_SAYS, outcome)))
          .append("</button>");
    }
    return buttons.toString();
  }

  static String finished(PayerOutcome outcome) {
    return page(
        "<h1>"
            + TITLE
            + "</h1><p>"
            + escaped(
                TokenizeOnTheHostedPage.THE_PAYER_STEPS.asItsOwnPageSaysIt(
                    WHAT_THE_PAYER_IS_TOLD_AFTERWARDS, outcome))
            + "</p>");
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
