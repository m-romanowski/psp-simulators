package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

final class TranzilaHostedFieldsRefusalPage {

  static final String WHAT_IT_PERMITS = WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE;

  static final Map<String, String> DEFENCES =
      WhatAPayerPageMayLoad.onADocumentDeclaring(WHAT_IT_PERMITS);

  static final String TITLE = "Tranzila hosted fields";

  static final String STYLE =
      "body{margin:0;background:#fff;color:#1b1b1b;font:16px/1.5 system-ui,sans-serif}"
          + "main{box-sizing:border-box;max-width:28rem;margin:0 auto;padding:1rem}"
          + "h1{font-size:1.25rem;margin:0 0 .5rem}";

  static final String NO_SUCH_ADDRESS =
      "No page of provider tranzila's hosted fields is served at this address. The script is"
          + " /tranzila/hf/assets/js/thostedf.js and the charge is posted to"
          + " /tranzila/hf/process/ajax_process_transaction.";

  static final String NO_SUCH_METHOD =
      "This address of provider tranzila's hosted fields does not answer this HTTP method. The"
          + " script and the frames take GET, and the charge takes POST only.";

  private TranzilaHostedFieldsRefusalPage() {}

  static String refusing(String inItsOwnWords) {
    return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
        + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
        + "<title>"
        + TITLE
        + "</title><style>"
        + STYLE
        + "</style></head><body><main><h1>"
        + TITLE
        + "</h1><p>"
        + HtmlUtils.htmlEscape(inItsOwnWords)
        + "</p></main></body></html>";
  }
}
