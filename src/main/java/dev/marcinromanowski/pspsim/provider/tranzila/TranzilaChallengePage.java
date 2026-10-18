package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

final class TranzilaChallengePage {

  static final String HOST = "/tranzila/3ds";

  static final String TRACK = "trackId";

  static final String ROUTE = HOST + "/challenge/{" + TRACK + "}";

  static final ThePayerStep THE_CHALLENGE_A_PAYER_ANSWERS =
      new ThePayerStep.APageThisSimulatorServes(ROUTE);

  static final String CODE = "code";

  static final String CONTENT_TYPE = "text/html; charset=utf-8";

  static final String WHAT_THIS_PAGE_PERMITS =
      WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE + "; form-action 'self'";

  static final Map<String, List<String>> DEFENCE_HEADERS =
      WhatAPayerPageMayLoad.eachOnADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  static final Map<String, String> DEFENCES =
      WhatAPayerPageMayLoad.onADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  static final String TITLE = "3-D Secure verification";

  static final String WHAT_TO_ENTER =
      "Enter the code your card issuer sent you. This simulated challenge passes with 555, as"
          + " tranzila's test card publishes it, and fails with any other code.";

  static final String SUBMIT = "Submit";

  static final String PASSED = "The card holder was authenticated. You can return to the shop.";

  static final String FAILED = "The card holder was not authenticated. You can return to the shop.";

  static final String NO_SUCH_CHALLENGE =
      "This challenge belongs to no 3-D Secure challenge this simulator holds. A challenge is"
          + " opened by a create with activate_3ds Y whose answer names it in challengeUrl, and it"
          + " is forgotten when its test run is reset.";

  static final String ALREADY_ANSWERED =
      "This challenge was already answered, and a payer answers a challenge once.";

  static final String NO_SUCH_ADDRESS =
      "No page of provider tranzila is served at this address. A 3-D Secure challenge is served"
          + " at the challengeUrl a create with activate_3ds Y answers.";

  static final String NO_SUCH_METHOD =
      "This address of provider tranzila does not answer this HTTP method. A challenge takes GET"
          + " to show it and POST to answer it.";

  private static final String STYLE =
      "body{margin:0;background:#fff;color:#1b1b1b;font:16px/1.5 system-ui,sans-serif}"
          + "main{box-sizing:border-box;max-width:28rem;margin:0 auto;padding:1rem}"
          + "h1{font-size:1.25rem;margin:0 0 .5rem}"
          + "dl{display:grid;grid-template-columns:auto 1fr;gap:.25rem 1rem;margin:0 0 1rem}"
          + "dt{font-weight:600}dd{margin:0;overflow-wrap:anywhere}"
          + "label,input,button{display:block;box-sizing:border-box;width:100%}"
          + "input,button{min-height:44px;margin:.5rem 0;font:inherit}"
          + "input{padding:0 .5rem;border:1px solid #6b6b6b;border-radius:.375rem}"
          + "button{border:0;border-radius:.375rem;background:#1f4e8c;color:#fff}";

  private TranzilaChallengePage() {}

  static String pathOf(String trackId) {
    return HOST + "/challenge/" + trackId;
  }

  static String addressOf(String publicBaseUrl, String trackId) {
    return publicBaseUrl + pathOf(trackId);
  }

  static String asking(TranzilaChallenge challenge) {
    return page(
        "<h1>"
            + TITLE
            + "</h1><dl><dt>Amount</dt><dd>"
            + escaped(challenge.amount())
            + " "
            + escaped(challenge.currencyCode())
            + "</dd><dt>Card</dt><dd>"
            + escaped(challenge.card().cardTypeName())
            + " ending "
            + escaped(challenge.card().last4())
            + "</dd></dl><p>"
            + escaped(WHAT_TO_ENTER)
            + "</p><form method=\"post\" action=\""
            + escaped(pathOf(challenge.trackId()))
            + "\"><label for=\""
            + CODE
            + "\">Code</label><input id=\""
            + CODE
            + "\" name=\""
            + CODE
            + "\" inputmode=\"numeric\" autocomplete=\"one-time-code\"><button type=\"submit\">"
            + SUBMIT
            + "</button></form>");
  }

  static String reporting(boolean passed) {
    return refusing(passed ? PASSED : FAILED);
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
