package com.example.checkout.e2e;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class MerchantEndpoint implements AutoCloseable {

  private static final String PATH = "/callbacks";

  private final HttpServer server;
  private final List<Received> received = new CopyOnWriteArrayList<>();
  private final AtomicInteger answerWith = new AtomicInteger(200);

  record Received(Map<String, List<String>> headers, String body, Map<String, String> form) {

    Optional<String> field(String name) {
      return Optional.ofNullable(form.get(name));
    }

    Optional<String> header(String name) {
      return headers.entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(name))
          .flatMap(entry -> entry.getValue().stream())
          .findFirst();
    }
  }

  private MerchantEndpoint(HttpServer server) {
    this.server = server;
  }

  static MerchantEndpoint listening() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      MerchantEndpoint endpoint = new MerchantEndpoint(server);
      server.createContext(PATH, endpoint::answer);
      server.setExecutor(Executors.newFixedThreadPool(4));
      server.start();
      return endpoint;
    } catch (IOException notListening) {
      throw new IllegalStateException(
          "the shop's own callback endpoint would not listen", notListening);
    }
  }

  String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
  }

  List<Received> everythingReceived() {
    return List.copyOf(received);
  }

  Received theOnlyThingReceived() {
    List<Received> all = everythingReceived();
    if (all.size() != 1) {
      throw new AssertionError("the shop expected exactly one callback and received " + all.size());
    }
    return all.getFirst();
  }

  void answerEveryCallbackWith(int status) {
    answerWith.set(status);
  }

  void forgetEverythingReceived() {
    received.clear();
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void answer(HttpExchange exchange) throws IOException {
    try (InputStream incoming = exchange.getRequestBody()) {
      String body = new String(incoming.readAllBytes(), StandardCharsets.UTF_8);
      received.add(new Received(copyOf(exchange), body, formOf(body)));
    }
    byte[] answer = "ok".getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(answerWith.get(), answer.length);
    exchange.getResponseBody().write(answer);
    exchange.close();
  }

  private static Map<String, List<String>> copyOf(HttpExchange exchange) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    exchange
        .getRequestHeaders()
        .forEach(
            (name, values) -> headers.put(name.toLowerCase(Locale.ROOT), new ArrayList<>(values)));
    return headers;
  }

  private static Map<String, String> formOf(String body) {
    Map<String, String> fields = new LinkedHashMap<>();
    if (body.isBlank()) {
      return fields;
    }
    for (String pair : body.split("&")) {
      int equals = pair.indexOf('=');
      String name = equals < 0 ? pair : pair.substring(0, equals);
      String value = equals < 0 ? "" : pair.substring(equals + 1);
      fields.put(decoded(name), decoded(value));
    }
    return fields;
  }

  private static String decoded(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
