package dev.marcinromanowski.pspsim.kernel.fault;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

public final class FaultedDelivery {

  private final @Nullable AppliedFault fault;
  private final int httpStatus;
  private final String contentType;
  private final byte[] body;
  private final long declaredContentLength;
  private final Duration delay;

  public FaultedDelivery(
      @Nullable AppliedFault fault,
      int httpStatus,
      String contentType,
      byte[] body,
      long declaredContentLength,
      Duration delay) {
    this.fault = fault;
    this.httpStatus = httpStatus;
    this.contentType = contentType;
    this.body = body.clone();
    this.declaredContentLength = declaredContentLength;
    this.delay = delay;
  }

  public @Nullable AppliedFault fault() {
    return fault;
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String contentType() {
    return contentType;
  }

  public byte[] body() {
    return body.clone();
  }

  public long writtenBodyBytes() {
    return body.length;
  }

  public long declaredContentLength() {
    return declaredContentLength;
  }

  public Duration delay() {
    return delay;
  }

  public boolean completesTheBody() {
    return declaredContentLength == body.length;
  }
}
