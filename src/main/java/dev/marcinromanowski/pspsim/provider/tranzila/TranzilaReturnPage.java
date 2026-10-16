package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

final class TranzilaReturnPage {

  static final String THE_SCRIPT = "HTMLFormElement.prototype.submit.call(document.forms[0]);";

  static final String WHAT_THIS_PAGE_PERMITS =
      WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE
          + "; script-src 'sha256-Zd9Ppkm6KJvfEle1ogzptKkD+DOonO1DYBPhi02doj8='";

  static final Map<String, List<String>> DEFENCE_HEADERS =
      WhatAPayerPageMayLoad.eachOnADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  static final String TITLE = "Returning to the shop";

  static final String WHAT_HAPPENS =
      "The payment page is sending the result back to the shop. If nothing happens, continue"
          + " below.";

  static final String CONTINUE = "Continue to the shop";

  private static final String STYLE =
      "body{margin:0;background:#fff;color:#1b1b1b;font:16px/1.5 system-ui,sans-serif}"
          + "main{box-sizing:border-box;max-width:28rem;margin:0 auto;padding:1rem}"
          + "h1{font-size:1.25rem;margin:0 0 .5rem}"
          + "button{display:block;box-sizing:border-box;width:100%;min-height:44px;margin:.5rem 0;"
          + "font:inherit;border:0;border-radius:.375rem;background:#1f4e8c;color:#fff}";

  private TranzilaReturnPage() {}

  static String posting(String address, Map<String, String> fields) {
    StringBuilder inputs = new StringBuilder();
    fields.forEach(
        (name, value) ->
            inputs
                .append("<input type=\"hidden\" name=\"")
                .append(escaped(name))
                .append("\" value=\"")
                .append(escaped(value))
                .append("\">"));
    return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        + "<title>"
        + TITLE
        + "</title><style>"
        + STYLE
        + "</style></head><body><main><h1>"
        + TITLE
        + "</h1><p>"
        + WHAT_HAPPENS
        + "</p><form method=\"post\" action=\""
        + escaped(address)
        + "\">"
        + inputs
        + "<button type=\"submit\">"
        + CONTINUE
        + "</button></form></main><script>"
        + THE_SCRIPT
        + "</script></body></html>";
  }

  static String escaped(String text) {
    return HtmlUtils.htmlEscape(text);
  }
}
