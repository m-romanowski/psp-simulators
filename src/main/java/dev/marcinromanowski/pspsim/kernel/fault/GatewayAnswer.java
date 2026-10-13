package dev.marcinromanowski.pspsim.kernel.fault;

import java.nio.charset.StandardCharsets;

public final class GatewayAnswer {

  public static final String CONTENT_TYPE = "text/plain; charset=utf-8";

  private GatewayAnswer() {}

  public static String textFor(int httpStatus) {
    return httpStatus
        + " was answered in front of the provider. These bytes are the gateway's and not the"
        + " provider's, and whether the provider processed the request cannot be read from this"
        + " answer.";
  }

  public static byte[] bytesFor(int httpStatus) {
    return textFor(httpStatus).getBytes(StandardCharsets.UTF_8);
  }
}
