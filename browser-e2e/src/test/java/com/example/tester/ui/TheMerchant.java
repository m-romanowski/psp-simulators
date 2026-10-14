package com.example.tester.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TheMerchant implements AutoCloseable {

  public static final String THANKS = "Thank you, the shop has your payment";

  public static final String NEVER_MIND = "The shop knows you left without paying";

  public static final String NOT_MADE = "The payment was not made";

  private static final String PAID = "/paid";

  private static final String ABANDONED = "/abandoned";

  private static final String CALLBACKS = "/callbacks";

  private static final String ANOTHER_TARGET = "/another-callback-target";

  private static final String CHECKOUT = "/checkout";

  private static final String FRAMED = "src";

  private static final String HOSTED_FIELDS_CHECKOUT = "/hosted-fields-checkout";

  private static final String HOSTED_FIELDS_ANSWER = "/hosted-fields-answer";

  private static final String THIRD_PARTY = "/a-third-party-widget";

  private static final Duration POLL = Duration.ofMillis(100);

  private final HttpServer server;
  private final List<String> callbacks = new CopyOnWriteArrayList<>();
  private final List<String> callbacksAtTheOtherTarget = new CopyOnWriteArrayList<>();
  private final List<String> returns = new CopyOnWriteArrayList<>();
  private final List<String> hostedFieldsAnswers = new CopyOnWriteArrayList<>();

  public TheMerchant() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException notBound) {
      throw new UncheckedIOException("the shop's own endpoint could not be bound", notBound);
    }
    server.createContext(PAID, exchange -> writeAPage(exchange, THANKS, returns));
    server.createContext(ABANDONED, exchange -> writeAPage(exchange, NEVER_MIND, returns));
    server.createContext(CALLBACKS, exchange -> recordACallback(exchange, callbacks));
    server.createContext(
        ANOTHER_TARGET, exchange -> recordACallback(exchange, callbacksAtTheOtherTarget));
    server.createContext(CHECKOUT, TheMerchant::frameThePageItWasGiven);
    server.createContext(HOSTED_FIELDS_CHECKOUT, TheMerchant::checkOutWithHostedFields);
    server.createContext(
        HOSTED_FIELDS_ANSWER, exchange -> recordACallback(exchange, hostedFieldsAnswers));
    server.createContext(THIRD_PARTY, TheMerchant::aWidgetThatListens);
    server.setExecutor(null);
    server.start();
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public String whereAPayerLandsHavingPaid() {
    return url() + PAID;
  }

  public String whereAPayerLandsHavingGivenUp() {
    return url() + ABANDONED;
  }

  public String whereCallbacksGo() {
    return url() + CALLBACKS;
  }

  public String anotherCallbackTargetOfTheSameAccount() {
    return url() + ANOTHER_TARGET;
  }

  public String whereTheShopFrames(String aPageOfTheProvider) {
    return url()
        + CHECKOUT
        + "?"
        + FRAMED
        + "="
        + URLEncoder.encode(aPageOfTheProvider, StandardCharsets.UTF_8);
  }

  public String whereTheShopChecksOutWithHostedFields(
      String hostedFieldsBase, String terminal, String thtk, String amount) {
    return url()
        + HOSTED_FIELDS_CHECKOUT
        + "?"
        + query(
            Map.of(
                "hf",
                hostedFieldsBase,
                "terminal",
                terminal,
                "thtk",
                thtk,
                "amount",
                amount,
                "notify",
                whereCallbacksGo(),
                "spy",
                aThirdPartyFrame()));
  }

  public String aThirdPartyFrame() {
    return "http://localhost:" + server.getAddress().getPort() + THIRD_PARTY;
  }

  public List<String> hostedFieldsAnswersReceived() {
    return List.copyOf(hostedFieldsAnswers);
  }

  public String theLatestHostedFieldsAnswer() {
    if (hostedFieldsAnswers.isEmpty()) {
      throw new IllegalStateException(
          "the shop's checkout posted no answer of the hosted fields to its server");
    }
    return hostedFieldsAnswers.get(hostedFieldsAnswers.size() - 1);
  }

  public List<String> callbacksReceived() {
    List<String> everyCallback = new ArrayList<>(callbacks);
    everyCallback.addAll(callbacksAtTheOtherTarget);
    return List.copyOf(everyCallback);
  }

  public List<String> returnsReceived() {
    return List.copyOf(returns);
  }

  public Map<String, String> theOnlyReturn() {
    if (returns.size() != 1) {
      throw new IllegalStateException(
          "the shop's return pages were posted " + returns.size() + " bodies, not one");
    }
    return fieldsOf(returns.get(0));
  }

  public List<String> callbacksReceivedAtTheOtherTarget() {
    return List.copyOf(callbacksAtTheOtherTarget);
  }

  public Map<String, String> theCallbackNaming(String field, String value, Duration within) {
    Instant deadline = Instant.now().plus(within);
    while (Instant.now().isBefore(deadline)) {
      for (String callback : callbacks) {
        Map<String, String> fields = fieldsOf(callback);
        if (value.equals(fields.get(field))) {
          return fields;
        }
      }
      waitForTheNextAttempt();
    }
    throw new IllegalStateException(
        "the shop's endpoint at "
            + whereCallbacksGo()
            + " was sent no callback with "
            + field
            + "="
            + value
            + " within "
            + within
            + "; it holds "
            + callbacks.size()
            + " callbacks for anything else");
  }

  private static Map<String, String> fieldsOf(String urlencoded) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (String pair : urlencoded.split("&", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      int separator = pair.indexOf('=');
      fields.put(
          URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
          URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
    }
    return fields;
  }

  private static void waitForTheNextAttempt() {
    try {
      Thread.sleep(POLL.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for a callback");
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private static void recordACallback(HttpExchange exchange, List<String> received)
      throws IOException {
    try (exchange) {
      received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  private static void frameThePageItWasGiven(HttpExchange exchange) throws IOException {
    String query = exchange.getRequestURI().getRawQuery();
    String framed =
        URLDecoder.decode(query.substring(query.indexOf('=') + 1), StandardCharsets.UTF_8);
    try (exchange) {
      byte[] body =
          ("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>the shop</title>"
                  + "</head><body><h1>Pay the shop</h1><iframe title=\"card form\" src=\""
                  + framed.replace("&", "&amp;").replace("\"", "&quot;")
                  + "\" width=\"360\" height=\"480\"></iframe></body></html>")
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  private static void checkOutWithHostedFields(HttpExchange exchange) throws IOException {
    Map<String, String> asked = fieldsOf(exchange.getRequestURI().getRawQuery());
    try (exchange) {
      byte[] body =
          ("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                  + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                  + "<title>the shop</title><script src=\""
                  + attribute(asked.get("hf") + "/assets/js/thostedf.js")
                  + "\"></script><style>.field{height:48px;border:1px solid #767676;"
                  + "margin:0 0 .75rem}.hosted-fields-invalid{border-color:#b00020}</style></head>"
                  + "<body><h1>Pay the shop</h1><p>"
                  + attribute(asked.get("amount"))
                  + " ILS</p><form id=\"payment_form\">"
                  + "<label for=\"credit_card_number\">Card number</label>"
                  + "<div class=\"field\" id=\"credit_card_number\"></div>"
                  + "<p id=\"errors_for_credit_card_number\"></p>"
                  + "<label for=\"expiry\">Expiry</label>"
                  + "<div class=\"field\" id=\"expiry\"></div>"
                  + "<label for=\"cvv\">CVV</label><div class=\"field\" id=\"cvv\"></div>"
                  + "<button type=\"submit\">Pay</button></form><p id=\"outcome\"></p>"
                  + "<iframe title=\"a third party widget\" src=\""
                  + attribute(asked.get("spy"))
                  + "\" width=\"300\" height=\"40\"></iframe>"
                  + "<script>"
                  + "var shop = "
                  + aScriptString(asked)
                  + ";"
                  + THE_CHECKOUT_SCRIPT
                  + "</script></body></html>")
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  private static final String THE_CHECKOUT_SCRIPT =
      "window.shopSaw = [];"
          + "window.shopEvents = [];"
          + "window.addEventListener('message', function (e) {"
          + "window.shopSaw.push(typeof e.data === 'string' ? e.data : JSON.stringify(e.data));});"
          + "var fields = TzlaHostedFields.create({sandbox: false, fields: {"
          + "credit_card_number: {selector: '#credit_card_number', placeholder: 'Card number',"
          + " tabindex: 1},"
          + "expiry: {selector: '#expiry', placeholder: 'MM/YY', version: '1'},"
          + "cvv: {selector: '#cvv', placeholder: 'CVV', tabindex: 3}},"
          + "styles: {input: {'font-size': '16px'}, 'input.hosted-fields-invalid': {color: '#b00020'}}});"
          + "['notEmpty', 'empty', 'validityChange', 'cardTypeChange', 'focus', 'blur']"
          + ".forEach(function (type) { fields.onEvent(type, function (e) {"
          + "window.shopEvents.push(e.type + ':' + (e.cardType || e.field || ''));});});"
          + "fields.onEvent('ready', function () {"
          + "document.body.setAttribute('data-hosted-fields', 'ready');});"
          + "document.getElementById('payment_form').addEventListener('submit', function (e) {"
          + "e.preventDefault();"
          + "fields.charge({terminal_name: shop.terminal, amount: shop.amount, thtk: shop.thtk,"
          + " tran_mode: 'V', tokenize: true, contact: 'Dana Levi', notify_url: shop.notify},"
          + " function (err, response) {"
          + "var answered = err ? 'refused ' + JSON.stringify(err) : JSON.stringify(response);"
          + "fetch('"
          + HOSTED_FIELDS_ANSWER
          + "', {method: 'POST', body: answered}).then(function () {"
          + "document.getElementById('outcome').textContent ="
          + " response && response.transaction_response && response.transaction_response.success"
          + " ? '"
          + THANKS
          + "' : '"
          + NOT_MADE
          + "';});});});";

  private static void aWidgetThatListens(HttpExchange exchange) throws IOException {
    try (exchange) {
      byte[] body =
          ("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                  + "<title>a widget</title></head><body><pre id=\"heard\"></pre><script>"
                  + "window.addEventListener('message', function (e) {"
                  + "document.getElementById('heard').textContent += String(e.data) + ' ';});"
                  + "</script></body></html>")
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  private static String aScriptString(Map<String, String> asked) {
    StringBuilder object = new StringBuilder("{");
    for (String key : List.of("terminal", "thtk", "amount", "notify")) {
      object
          .append(key)
          .append(": '")
          .append(asked.get(key).replace("\\", "").replace("'", "").replace("<", ""))
          .append("', ");
    }
    return object.append("}").toString();
  }

  private static String attribute(String value) {
    return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
  }

  private static String query(Map<String, String> values) {
    StringBuilder query = new StringBuilder();
    for (Map.Entry<String, String> value : new TreeMap<>(values).entrySet()) {
      if (!query.isEmpty()) {
        query.append('&');
      }
      query
          .append(URLEncoder.encode(value.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(value.getValue(), StandardCharsets.UTF_8));
    }
    return query.toString();
  }

  private static void writeAPage(HttpExchange exchange, String sentence, List<String> returns)
      throws IOException {
    try (exchange) {
      if ("POST".equals(exchange.getRequestMethod())) {
        returns.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      }
      byte[] body =
          ("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>the shop</title>"
                  + "</head><body><h1>"
                  + sentence
                  + "</h1></body></html>")
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    }
  }
}
