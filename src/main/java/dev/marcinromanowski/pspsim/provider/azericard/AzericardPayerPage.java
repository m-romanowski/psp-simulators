package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class AzericardPayerPage {

  static final String PATH = "/azericard/payer";

  static final String CONTENT_TYPE = "text/html; charset=utf-8";

  static final String CACHE_CONTROL = WhatAPayerPageMayLoad.CACHE_CONTROL;

  static final String NOTHING_IS_STORED = WhatAPayerPageMayLoad.NOTHING_IS_STORED;

  static final String CONTENT_TYPE_OPTIONS = WhatAPayerPageMayLoad.CONTENT_TYPE_OPTIONS;

  static final String NOTHING_IS_SNIFFED = WhatAPayerPageMayLoad.NOTHING_IS_SNIFFED;

  static final String REFERRER_POLICY = WhatAPayerPageMayLoad.REFERRER_POLICY;

  static final String NOTHING_IS_REFERRED = WhatAPayerPageMayLoad.NOTHING_IS_REFERRED;

  static final String CONTENT_SECURITY_POLICY = WhatAPayerPageMayLoad.CONTENT_SECURITY_POLICY;

  static final String WHAT_THIS_PAGE_PERMITS =
      WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE
          + "; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";

  static final Map<String, List<String>> HEADERS =
      WhatAPayerPageMayLoad.eachOnADocumentDeclaring(WHAT_THIS_PAGE_PERMITS);

  static void defencesOnto(HttpServletResponse outbound) {
    WhatAPayerPageMayLoad.onADocumentDeclaring(WHAT_THIS_PAGE_PERMITS).forEach(outbound::setHeader);
  }

  static final String SESSION = "session";

  static final String CARD_NUMBER = "cardNumber";

  static final String EXPIRY = "expiry";

  static final String CVC = "cvc";

  static final String ACTION = "action";

  static final String PAY = "pay";

  static final String CANCEL = "cancel";

  static final String SUCCESSFUL = "Payment successful";

  static final String FAILED = "Payment failed";

  static final String IN_PROGRESS = "Payment in progress";

  static final String RETURN_TO_THE_MERCHANT = "Return to the merchant";

  static final String NO_SUCH_PAGE =
      "This payment page does not belong to any payment this simulator holds. A page is opened by"
          + " the merchant's signed request and is forgotten when its test run is reset.";

  private static final String STYLE =
      "*{box-sizing:border-box}"
          + "body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;"
          + "background:#f3f4f6;color:#111827}"
          + "main{max-width:28rem;margin:0 auto;padding:1rem}"
          + "section{background:#fff;border-radius:.75rem;padding:1.25rem;"
          + "box-shadow:0 1px 3px rgba(0,0,0,.12)}"
          + "h1{font-size:1.25rem;margin:0 0 1rem}"
          + "dl{display:grid;grid-template-columns:auto 1fr;gap:.25rem .75rem;margin:0 0 1rem}"
          + "dt{color:#4b5563}dd{margin:0;font-weight:600;overflow-wrap:anywhere}"
          + "label{display:block;margin:.75rem 0 .25rem;font-size:.9rem;color:#374151}"
          + "input{width:100%;font-size:1.1rem;padding:.7rem;border:1px solid #9ca3af;"
          + "border-radius:.5rem}"
          + ".row{display:flex;gap:.75rem}.row>div{flex:1}"
          + "button{width:100%;margin-top:1.25rem;font-size:1.1rem;padding:.8rem;border:0;"
          + "border-radius:.5rem;background:#1d4ed8;color:#fff}"
          + "button.cancel{background:none;color:#1d4ed8;margin-top:.5rem;"
          + "text-decoration:underline}"
          + "a.back{display:block;text-align:center;margin-top:1.25rem;padding:.8rem;"
          + "border-radius:.5rem;background:#1d4ed8;color:#fff;text-decoration:none}";

  record CardForm(
      String sessionId,
      String merchantName,
      String description,
      String amount,
      String currency,
      String order) {}

  record Result(
      boolean successful,
      String amount,
      String currency,
      String order,
      String rrn,
      String merchantName,
      @Nullable String returnUrl) {}

  private AzericardPayerPage() {}

  static AzericardBody offering(CardForm form) {
    String body =
        "<section><h1>"
            + escaped(form.merchantName())
            + "</h1><dl>"
            + line("Description", form.description())
            + line("Amount", form.amount() + " " + form.currency())
            + line("Order ID", form.order())
            + "</dl><form method=\"post\" action=\""
            + PATH
            + "\">"
            + hidden(SESSION, form.sessionId())
            + field(CARD_NUMBER, "Card number", "cc-number", "0000 0000 0000 0000")
            + "<div class=\"row\"><div>"
            + field(EXPIRY, "Expiry (MM/YY)", "cc-exp", "MM/YY")
            + "</div><div>"
            + field(CVC, "CVC", "cc-csc", "123")
            + "</div></div>"
            + "<button type=\"submit\" name=\""
            + ACTION
            + "\" value=\""
            + PAY
            + "\">Pay</button>"
            + "<button class=\"cancel\" type=\"submit\" name=\""
            + ACTION
            + "\" value=\""
            + CANCEL
            + "\">Cancel payment</button>"
            + "</form></section>";
    return rendered(form.merchantName(), body, Map.of(SESSION, List.of(form.sessionId())));
  }

  static AzericardBody reporting(Result result) {
    String heading = result.successful() ? SUCCESSFUL : FAILED;
    String returnUrl = result.returnUrl();
    String back =
        returnUrl == null
            ? "<p>The merchant sent no web site address to return to.</p>"
            : "<a class=\"back\" href=\""
                + escaped(returnUrl)
                + "\">"
                + RETURN_TO_THE_MERCHANT
                + "</a>";
    String body =
        "<section><h1>"
            + heading
            + "</h1><dl>"
            + line("Amount", result.amount())
            + line("Currency", result.currency())
            + line("Order ID", result.order())
            + line("RRN", result.rrn())
            + line("Merchant name", result.merchantName())
            + "</dl>"
            + back
            + "</section>";
    return rendered(heading, body, Map.of("result", List.of(heading)));
  }

  static AzericardBody inProgress(String inItsOwnWords) {
    return rendered(
        IN_PROGRESS,
        "<section><h1>" + IN_PROGRESS + "</h1><p>" + escaped(inItsOwnWords) + "</p></section>",
        Map.of());
  }

  static AzericardBody refusing(String inItsOwnWords) {
    return rendered(
        FAILED,
        "<section><h1>" + FAILED + "</h1><p>" + escaped(inItsOwnWords) + "</p></section>",
        Map.of());
  }

  private static AzericardBody rendered(
      String title, String body, Map<String, List<String>> fields) {
    String page =
        "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>"
            + escaped(title)
            + "</title><style>"
            + STYLE
            + "</style></head><body><main>"
            + body
            + "</main></body></html>";
    return new AzericardBody(CONTENT_TYPE, page, fields, HEADERS);
  }

  private static String line(String label, String value) {
    return "<dt>" + escaped(label) + "</dt><dd>" + escaped(value) + "</dd>";
  }

  private static String hidden(String name, String value) {
    return "<input type=\"hidden\" name=\"" + name + "\" value=\"" + escaped(value) + "\">";
  }

  private static String field(String name, String label, String autocomplete, String hint) {
    return "<label for=\""
        + name
        + "\">"
        + label
        + "</label><input id=\""
        + name
        + "\" name=\""
        + name
        + "\" inputmode=\"numeric\" autocomplete=\""
        + autocomplete
        + "\" placeholder=\""
        + hint
        + "\">";
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
