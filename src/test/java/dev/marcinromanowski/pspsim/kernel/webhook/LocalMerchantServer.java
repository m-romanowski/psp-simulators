package dev.marcinromanowski.pspsim.kernel.webhook;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalMerchantServer implements AutoCloseable {

  public sealed interface Answer {

    record Respond(int httpStatus, String body) implements Answer {}

    record DropTheConnection() implements Answer {}

    record NeverAnswer() implements Answer {}
  }

  public static final String PATH = "/callbacks";

  private final HttpServer server;
  private final Deque<Answer> answers = new ArrayDeque<>();
  private final List<ReceivedCallback> received = new ArrayList<>();
  private final CountDownLatch closed = new CountDownLatch(1);
  private Answer standingAnswer = new Answer.Respond(200, "OK");
  private Runnable onceWhileHandling = () -> {};
  private final ExecutorService handlers = Executors.newCachedThreadPool();

  public LocalMerchantServer() {
    try {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException cannotBind) {
      throw new UncheckedIOException(cannotBind);
    }
    server.createContext(PATH, this::handle);
    server.setExecutor(handlers);
    server.start();
  }

  public String url() {
    return "http://"
        + server.getAddress().getAddress().getHostAddress()
        + ":"
        + server.getAddress().getPort()
        + PATH;
  }

  public LocalMerchantServer alwaysAnswering(Answer answer) {
    this.standingAnswer = answer;
    return this;
  }

  public LocalMerchantServer thenAnswering(Answer answer) {
    answers.addLast(answer);
    return this;
  }

  public LocalMerchantServer onceWhileHandling(Runnable action) {
    this.onceWhileHandling = action;
    return this;
  }

  public synchronized List<ReceivedCallback> received() {
    return List.copyOf(received);
  }

  @Override
  public void close() {
    closed.countDown();
    server.stop(0);
    handlers.shutdownNow();
  }

  private void handle(HttpExchange exchange) throws IOException {
    byte[] body;
    try (InputStream in = exchange.getRequestBody()) {
      body = in.readAllBytes();
    }
    record(exchange, body);
    takeTheActionOnce().run();
    Answer answer = nextAnswer();
    switch (answer) {
      case Answer.DropTheConnection _ -> exchange.close();
      case Answer.NeverAnswer _ -> {
        try {
          closed.await();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
        }
        exchange.close();
      }
      case Answer.Respond respond -> {
        byte[] response = respond.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(respond.httpStatus(), response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
      }
    }
  }

  private synchronized void record(HttpExchange exchange, byte[] body) {
    Map<String, List<String>> headers = new LinkedHashMap<>();
    exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
    received.add(
        new ReceivedCallback(
            exchange.getRequestMethod(),
            exchange.getRequestHeaders().getFirst("Content-Type"),
            Map.copyOf(headers),
            new String(body, StandardCharsets.UTF_8)));
  }

  private synchronized Runnable takeTheActionOnce() {
    Runnable action = onceWhileHandling;
    onceWhileHandling = () -> {};
    return action;
  }

  private synchronized Answer nextAnswer() {
    return answers.isEmpty() ? standingAnswer : answers.removeFirst();
  }

  public record ReceivedCallback(
      String method, String contentType, Map<String, List<String>> headers, String body) {

    public List<String> header(String name) {
      return headers.entrySet().stream()
          .filter(entry -> entry.getKey().equalsIgnoreCase(name))
          .findFirst()
          .map(Map.Entry::getValue)
          .orElse(List.of());
    }

    public Map<String, String> fields() {
      Map<String, String> fields = new LinkedHashMap<>();
      for (String pair : body.split("&", -1)) {
        if (pair.isEmpty()) {
          continue;
        }
        int separator = pair.indexOf('=');
        String name = separator < 0 ? pair : pair.substring(0, separator);
        String value = separator < 0 ? "" : pair.substring(separator + 1);
        fields.put(decode(name), decode(value));
      }
      return fields;
    }

    public List<String> fieldOrder() {
      return List.copyOf(fields().keySet());
    }

    private static String decode(String value) {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
  }
}
