package dev.marcinromanowski.pspsim.kernel.fault;

import java.time.Duration;
import java.util.Arrays;

public sealed interface TransportFault {

  int LOWEST_SERVER_ERROR = 500;

  int HIGHEST_SERVER_ERROR = 599;

  Duration LONGEST_DELAY = Duration.ofMinutes(5);

  TransportFaultKind kind();

  FaultedDelivery applyTo(RenderedResponse rendered);

  WithoutTheOperation answeredWithoutTheOperation();

  sealed interface WithoutTheOperation {

    record Answered(FaultedDelivery delivery) implements WithoutTheOperation {}

    record NeedsTheRenderedBytes(String whatItDoesWithThem) implements WithoutTheOperation {}
  }

  record ServerError(int httpStatus) implements TransportFault {

    public ServerError {
      if (httpStatus < LOWEST_SERVER_ERROR || httpStatus > HIGHEST_SERVER_ERROR) {
        throw new IllegalArgumentException(
            "action.httpStatus must be between "
                + LOWEST_SERVER_ERROR
                + " and "
                + HIGHEST_SERVER_ERROR
                + ", because a status a transport fault puts in place of a rendered response is a"
                + " server error and never a status the provider chose");
      }
    }

    @Override
    public TransportFaultKind kind() {
      return TransportFaultKind.SERVER_ERROR;
    }

    @Override
    public FaultedDelivery applyTo(RenderedResponse rendered) {
      return gatewayAnswer(new AppliedFault(kind(), FaultPlacement.AFTER_THE_OPERATION));
    }

    @Override
    public WithoutTheOperation answeredWithoutTheOperation() {
      return new WithoutTheOperation.Answered(
          gatewayAnswer(new AppliedFault(kind(), FaultPlacement.INSTEAD_OF_THE_OPERATION)));
    }

    private FaultedDelivery gatewayAnswer(AppliedFault applied) {
      byte[] body = GatewayAnswer.bytesFor(httpStatus);
      return new FaultedDelivery(
          applied, httpStatus, GatewayAnswer.CONTENT_TYPE, body, body.length, Duration.ZERO);
    }
  }

  record TruncatedBody() implements TransportFault {

    @Override
    public TransportFaultKind kind() {
      return TransportFaultKind.TRUNCATED_BODY;
    }

    @Override
    public FaultedDelivery applyTo(RenderedResponse rendered) {
      byte[] prefix = halfOf(bytesToDamage(rendered));
      return new FaultedDelivery(
          new AppliedFault(kind(), FaultPlacement.AFTER_THE_OPERATION),
          rendered.httpStatus(),
          rendered.contentType(),
          prefix,
          prefix.length,
          Duration.ZERO);
    }

    @Override
    public WithoutTheOperation answeredWithoutTheOperation() {
      return new WithoutTheOperation.NeedsTheRenderedBytes(
          "sends a genuine prefix of the bytes an operation rendered");
    }
  }

  record CorruptedBody() implements TransportFault {

    @Override
    public TransportFaultKind kind() {
      return TransportFaultKind.CORRUPTED_BODY;
    }

    @Override
    public FaultedDelivery applyTo(RenderedResponse rendered) {
      byte[] corrupted = bytesToDamage(rendered);
      int last = corrupted.length - 1;
      corrupted[last] = (byte) (corrupted[last] ^ 0xFF);
      return new FaultedDelivery(
          new AppliedFault(kind(), FaultPlacement.AFTER_THE_OPERATION),
          rendered.httpStatus(),
          rendered.contentType(),
          corrupted,
          corrupted.length,
          Duration.ZERO);
    }

    @Override
    public WithoutTheOperation answeredWithoutTheOperation() {
      return new WithoutTheOperation.NeedsTheRenderedBytes(
          "inverts one of the bytes an operation rendered");
    }
  }

  record DroppedConnection() implements TransportFault {

    @Override
    public TransportFaultKind kind() {
      return TransportFaultKind.DROPPED_CONNECTION;
    }

    @Override
    public FaultedDelivery applyTo(RenderedResponse rendered) {
      byte[] whole = bytesToDamage(rendered);
      return new FaultedDelivery(
          new AppliedFault(kind(), FaultPlacement.AFTER_THE_OPERATION),
          rendered.httpStatus(),
          rendered.contentType(),
          halfOf(whole),
          whole.length,
          Duration.ZERO);
    }

    @Override
    public WithoutTheOperation answeredWithoutTheOperation() {
      return new WithoutTheOperation.NeedsTheRenderedBytes(
          "writes part of the bytes an operation rendered under their full declared length");
    }
  }

  record DelayedResponse(Duration delay) implements TransportFault {

    public DelayedResponse {
      if (delay.isZero() || delay.isNegative()) {
        throw new IllegalArgumentException(
            "action.delayMs must be at least one millisecond, or the response is not delayed");
      }
      if (delay.compareTo(LONGEST_DELAY) > 0) {
        throw new IllegalArgumentException(
            "action.delayMs must not exceed "
                + LONGEST_DELAY
                + ", the longest a transport fault may hold a client's connection open");
      }
    }

    @Override
    public TransportFaultKind kind() {
      return TransportFaultKind.DELAYED_RESPONSE;
    }

    @Override
    public FaultedDelivery applyTo(RenderedResponse rendered) {
      byte[] body = rendered.body();
      return new FaultedDelivery(
          new AppliedFault(kind(), FaultPlacement.AFTER_THE_OPERATION),
          rendered.httpStatus(),
          rendered.contentType(),
          body,
          body.length,
          delay);
    }

    @Override
    public WithoutTheOperation answeredWithoutTheOperation() {
      return new WithoutTheOperation.NeedsTheRenderedBytes(
          "delivers the bytes an operation rendered late");
    }
  }

  private static byte[] bytesToDamage(RenderedResponse rendered) {
    byte[] body = rendered.body();
    if (body.length == 0) {
      throw new IllegalArgumentException(
          "a fault that damages a body needs bytes to damage, and this response carries none");
    }
    return body;
  }

  private static byte[] halfOf(byte[] body) {
    return Arrays.copyOf(body, body.length / 2);
  }
}
