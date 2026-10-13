package dev.marcinromanowski.pspsim.kernel.webhook.internal;

import dev.marcinromanowski.pspsim.kernel.webhook.MerchantEndpoint;
import dev.marcinromanowski.pspsim.kernel.webhook.MerchantResponse;
import dev.marcinromanowski.pspsim.kernel.webhook.TransportFailure;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookPayload;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow.Subscription;
import org.jspecify.annotations.Nullable;

public final class HttpMerchantEndpoint implements MerchantEndpoint, AutoCloseable {

  private final HttpClient client;
  private final Duration readTimeout;
  private final int maxResponsePreviewBytes;

  public HttpMerchantEndpoint(
      Duration connectTimeout, Duration readTimeout, int maxResponsePreviewBytes) {
    requirePositive(connectTimeout, "simulator.webhook.connect-timeout");
    requirePositive(readTimeout, "simulator.webhook.read-timeout");
    if (maxResponsePreviewBytes < 1) {
      throw new IllegalArgumentException(
          "simulator.webhook.max-response-preview-bytes must be positive");
    }
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    this.readTimeout = readTimeout;
    this.maxResponsePreviewBytes = maxResponsePreviewBytes;
  }

  @Override
  public MerchantResponse post(String url, WebhookPayload payload) {
    HttpRequest request;
    try {
      HttpRequest.Builder building =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(readTimeout)
              .POST(HttpRequest.BodyPublishers.ofByteArray(payload.body()));
      for (Map.Entry<String, List<String>> header : payload.wireHeaders().entrySet()) {
        for (String value : header.getValue()) {
          building.header(header.getKey(), value);
        }
      }
      request = building.build();
    } catch (IllegalArgumentException notARequestableCall) {
      return new MerchantResponse.NotAnswered(TransportFailure.CONNECTION_ERROR);
    }
    try {
      HttpResponse<String> response =
          client.send(request, info -> new BoundedPreview(maxResponsePreviewBytes));
      return new MerchantResponse.Answered(response.statusCode(), response.body());
    } catch (HttpConnectTimeoutException connectTimedOut) {
      return new MerchantResponse.NotAnswered(TransportFailure.CONNECT_TIMEOUT);
    } catch (HttpTimeoutException readTimedOut) {
      return new MerchantResponse.NotAnswered(TransportFailure.READ_TIMEOUT);
    } catch (IOException refusedOrReset) {
      return new MerchantResponse.NotAnswered(TransportFailure.CONNECTION_ERROR);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return new MerchantResponse.NotAnswered(TransportFailure.CONNECTION_ERROR);
    }
  }

  @Override
  public void close() {
    client.close();
  }

  static final class BoundedPreview implements HttpResponse.BodySubscriber<String> {

    private final int maxPreviewBytes;
    private final CompletableFuture<String> preview = new CompletableFuture<>();
    private final ByteArrayOutputStream kept = new ByteArrayOutputStream();
    private @Nullable Subscription subscription;

    BoundedPreview(int maxPreviewBytes) {
      this.maxPreviewBytes = maxPreviewBytes;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
      this.subscription = subscription;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      for (ByteBuffer buffer : buffers) {
        int room = maxPreviewBytes - kept.size();
        byte[] wanted = new byte[Math.min(room, buffer.remaining())];
        buffer.get(wanted);
        kept.writeBytes(wanted);
      }
      if (kept.size() >= maxPreviewBytes) {
        preview.complete(text());
        Objects.requireNonNull(subscription).cancel();
      }
    }

    @Override
    public void onError(Throwable dropped) {
      preview.completeExceptionally(dropped);
    }

    @Override
    public void onComplete() {
      preview.complete(text());
    }

    @Override
    public CompletionStage<String> getBody() {
      return preview;
    }

    private String text() {
      return kept.toString(StandardCharsets.UTF_8);
    }
  }

  private static void requirePositive(Duration timeout, String propertyName) {
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException(propertyName + " must be positive");
    }
  }
}
