package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.util.List;
import java.util.Map;

final class AzericardErrorPage {

  static final String CONTENT_TYPE = "text/html";

  static final String WHAT_THIS_PAGE_PERMITS = AzericardPayerPage.WHAT_THIS_PAGE_PERMITS;

  static final Map<String, List<String>> HEADERS =
      WhatAPayerPageMayLoad.eachOnADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  static final String OPENING = "Sorry, due to technical fault your request cannot be processed";

  static final String CLOSING = "Please try once again or visit us later";

  private AzericardErrorPage() {}

  static AzericardBody naming(AzericardRequiredField field) {
    String page =
        "<html><body><p>"
            + OPENING
            + "</p><p>Error message: "
            + messageFor(field)
            + "</p><p>"
            + CLOSING
            + "</p></body></html>";
    return new AzericardBody(CONTENT_TYPE, page, Map.of(), HEADERS);
  }

  static String messageFor(AzericardRequiredField field) {
    return "Error in " + field.diagnosticPhrase() + " field";
  }
}
