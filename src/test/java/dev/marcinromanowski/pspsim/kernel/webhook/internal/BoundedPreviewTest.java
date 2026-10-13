package dev.marcinromanowski.pspsim.kernel.webhook.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.webhook.internal.HttpMerchantEndpoint.BoundedPreview;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class BoundedPreviewTest {

  private static final String NOT_YET_COMPLETE = "<not yet complete>";

  @Test
  void keeps_only_the_configured_amount_when_one_delivery_carries_several_buffers() {
    RecordingSubscription subscription = new RecordingSubscription();
    BoundedPreview preview = subscribed(new BoundedPreview(8), subscription);

    preview.onNext(List.of(buffer("abcde"), buffer("fghijklmno")));

    assertThat(textSoFar(preview)).isEqualTo("abcdefgh");
    assertThat(subscription.cancellations).isEqualTo(1);
  }

  @Test
  void keeps_exactly_the_first_bytes_of_a_body_split_across_deliveries() {
    RecordingSubscription subscription = new RecordingSubscription();
    BoundedPreview preview = subscribed(new BoundedPreview(8), subscription);

    preview.onNext(List.of(buffer("abc")));
    preview.onNext(List.of(buffer("defghijk")));

    assertThat(textSoFar(preview)).isEqualTo("abcdefgh");
    assertThat(subscription.cancellations).isEqualTo(1);
  }

  @Test
  void a_body_exactly_at_the_bound_completes_and_stops_reading_without_waiting_for_the_end() {
    RecordingSubscription subscription = new RecordingSubscription();
    BoundedPreview preview = subscribed(new BoundedPreview(8), subscription);

    preview.onNext(List.of(buffer("abcdefgh")));

    assertThat(textSoFar(preview)).isEqualTo("abcdefgh");
    assertThat(subscription.cancellations).isEqualTo(1);
  }

  @Test
  void a_body_reaching_the_bound_exactly_across_buffers_completes_and_stops_reading() {
    RecordingSubscription subscription = new RecordingSubscription();
    BoundedPreview preview = subscribed(new BoundedPreview(8), subscription);

    preview.onNext(List.of(buffer("abcd"), buffer("efgh")));

    assertThat(textSoFar(preview)).isEqualTo("abcdefgh");
    assertThat(subscription.cancellations).isEqualTo(1);
  }

  @Test
  void a_body_below_the_bound_keeps_reading_until_the_merchant_finishes() {
    RecordingSubscription subscription = new RecordingSubscription();
    BoundedPreview preview = subscribed(new BoundedPreview(8), subscription);

    preview.onNext(List.of(buffer("abcdefg")));

    assertThat(textSoFar(preview)).isEqualTo(NOT_YET_COMPLETE);
    assertThat(subscription.cancellations).isZero();

    preview.onComplete();

    assertThat(textSoFar(preview)).isEqualTo("abcdefg");
    assertThat(subscription.cancellations).isZero();
  }

  @Test
  void keeps_every_buffer_of_a_response_that_stays_within_the_bound() {
    RecordingSubscription subscription = new RecordingSubscription();
    BoundedPreview preview = subscribed(new BoundedPreview(64), subscription);

    preview.onNext(List.of(buffer("refused: "), buffer("no such merchant")));
    preview.onComplete();

    assertThat(textSoFar(preview)).isEqualTo("refused: no such merchant");
    assertThat(subscription.cancellations).isZero();
  }

  @Test
  void asks_the_client_for_the_whole_body_as_soon_as_it_subscribes() {
    RecordingSubscription subscription = new RecordingSubscription();

    subscribed(new BoundedPreview(8), subscription);

    assertThat(subscription.requested).containsExactly(Long.MAX_VALUE);
  }

  private static BoundedPreview subscribed(
      BoundedPreview preview, RecordingSubscription subscription) {
    preview.onSubscribe(subscription);
    return preview;
  }

  private static String textSoFar(BoundedPreview preview) {
    CompletableFuture<String> body = preview.getBody().toCompletableFuture();
    return body.getNow(NOT_YET_COMPLETE);
  }

  private static ByteBuffer buffer(String bytes) {
    return ByteBuffer.wrap(bytes.getBytes(UTF_8));
  }

  private static final class RecordingSubscription implements Flow.Subscription {

    private final List<Long> requested = new ArrayList<>();
    private int cancellations;

    @Override
    public void request(long items) {
      requested.add(items);
    }

    @Override
    public void cancel() {
      cancellations++;
    }
  }
}
