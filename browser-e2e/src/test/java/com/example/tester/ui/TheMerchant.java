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
import java.util.concurrent.CopyOnWriteArrayList;

public final class TheMerchant implements AutoCloseable {

  public static final String THANKS = "Thank you, the shop has your payment";

  public static final String NEVER_MIND = "The shop knows you left without paying";

  private static final String PAID = "/paid";

  private static final String ABANDONED = "/abandoned";

  private static final String CALLBACKS = "/callbacks";

  private static final String ANOTHER_TARGET = "/another-callback-target";

  private static final String CHECKOUT = "/checkout";

  private static final String FRAMED = "src";

  private static final Duration POLL = Duration.ofMillis(100);

  private final HttpServer server;
  private final List<String> callbacks = new CopyOnWriteArrayList<>();
  private final List<String> callbacksAtTheOtherTarget = new CopyOnWriteArrayList<>();
  private final List<String> returns = new CopyOnWriteArrayList<>();

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
