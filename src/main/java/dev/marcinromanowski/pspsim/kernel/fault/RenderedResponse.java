package dev.marcinromanowski.pspsim.kernel.fault;

import java.time.Duration;

public final class RenderedResponse {

  private final int httpStatus;
  private final String contentType;
  private final byte[] body;

  public RenderedResponse(int httpStatus, String contentType, byte[] body) {
    this.httpStatus = httpStatus;
    this.contentType = contentType;
    this.body = body.clone();
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

  public FaultedDelivery asDelivered() {
    return new FaultedDelivery(null, httpStatus, contentType, body, body.length, Duration.ZERO);
  }
}
