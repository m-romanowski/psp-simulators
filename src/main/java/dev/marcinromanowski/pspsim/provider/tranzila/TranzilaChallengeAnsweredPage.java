package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

final class TranzilaChallengeAnsweredPage {

  static final String THE_SCRIPT =
      "(function () {"
          + "var told = JSON.stringify({type: \"challengeResult\", track_id: document.body.getAttribute(\"data-track-id\") || \"\"});"
          + "if (parent === window) {"
          + "return;"
          + "}"
          + "for (var at = 0; at < parent.frames.length; at++) {"
          + "if (parent.frames[at] !== window) {"
          + "parent.frames[at].postMessage(told, location.origin);"
          + "}"
          + "}"
          + "})();";

  static final String WHAT_THIS_PAGE_PERMITS =
      WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE
          + "; script-src 'sha256-E3IReQae+CUjgO0MER6cFqfFAwEvPuxJ6hVgfkQhLjo='";

  static final Map<String, List<String>> DEFENCE_HEADERS =
      WhatAPayerPageMayLoad.eachOnADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  private static final String STYLE =
      "body{margin:0;background:#fff;color:#1b1b1b;font:16px/1.5 system-ui,sans-serif}"
          + "main{box-sizing:border-box;max-width:28rem;margin:0 auto;padding:1rem}"
          + "h1{font-size:1.25rem;margin:0 0 .5rem}";

  private TranzilaChallengeAnsweredPage() {}

  static String telling(String trackId, boolean passed) {
    return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        + "<title>"
        + TranzilaChallengePage.TITLE
        + "</title><style>"
        + STYLE
        + "</style></head><body data-track-id=\""
        + HtmlUtils.htmlEscape(trackId)
        + "\"><main><h1>"
        + TranzilaChallengePage.TITLE
        + "</h1><p>"
        + HtmlUtils.htmlEscape(passed ? TranzilaChallengePage.PASSED : TranzilaChallengePage.FAILED)
        + "</p></main><script>"
        + THE_SCRIPT
        + "</script></body></html>";
  }
}
