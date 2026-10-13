package dev.marcinromanowski.pspsim.kernel.smoke.internal;

import dev.marcinromanowski.pspsim.kernel.smoke.CheckRequest;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckResponse;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckTransport;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.IntSupplier;

public final class LocalHttpCheckTransport implements CheckTransport {

  static final String THE_ROUTE_DID_NOT_ANSWER = "the route a client dials did not answer";

  private final HttpClient client;
  private final IntSupplier port;
  private final Duration deadline;

  public LocalHttpCheckTransport(HttpClient client, IntSupplier port, Duration deadline) {
    this.client = client;
    this.port = port;
    this.deadline = deadline;
  }

  @Override
  public CheckResponse send(CheckRequest request) {
    HttpRequest.Builder built =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port.getAsInt() + request.path()))
            .timeout(deadline)
            .method(request.method(), bodyOf(request));
    request.headers().forEach(built::header);
    try {
      HttpResponse<byte[]> answer =
          client.send(built.build(), HttpResponse.BodyHandlers.ofByteArray());
      return new CheckResponse(
          answer.statusCode(),
          answer.headers().map(),
          new String(answer.body(), StandardCharsets.UTF_8));
    } catch (IOException unreachable) {
      throw new IllegalStateException(THE_ROUTE_DID_NOT_ANSWER);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(THE_ROUTE_DID_NOT_ANSWER);
    }
  }

  private static HttpRequest.BodyPublisher bodyOf(CheckRequest request) {
    String body = request.body();
    return body == null
        ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
  }
}
