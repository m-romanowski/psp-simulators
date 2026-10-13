package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

record AutraPaymentEvent(
    TestRunId testRunId,
    String paymentId,
    String type,
    String status,
    Amount amount,
    @Nullable String errorCode,
    @Nullable String msg,
    Instant at) {

  static final String A_REFUSAL_CARRIES_BOTH_HALVES_OR_NEITHER =
      "provider autra publishes an event of a payment's timeline with errorCode and msg together,"
          + " and an event carrying one of the two would be read as a refusal whose reason is the"
          + " text \"null\" or as a success a reader cannot see the code of";

  static final String THE_EVENT_REPORTS_NO_REFUSAL =
      "this event of the timeline reports no refusal, so it carries neither the code nor the"
          + " message of one";

  AutraPaymentEvent {
    if (errorCode == null != (msg == null)) {
      throw new IllegalArgumentException(A_REFUSAL_CARRIES_BOTH_HALVES_OR_NEITHER);
    }
  }

  static AutraPaymentEvent of(
      TestRunId testRunId,
      String paymentId,
      String type,
      String status,
      Amount amount,
      Instant at) {
    return new AutraPaymentEvent(testRunId, paymentId, type, status, amount, null, null, at);
  }

  static AutraPaymentEvent refused(
      TestRunId testRunId,
      String paymentId,
      String type,
      String status,
      Amount amount,
      String errorCode,
      String msg,
      Instant at) {
    return new AutraPaymentEvent(testRunId, paymentId, type, status, amount, errorCode, msg, at);
  }

  boolean reportsARefusal() {
    return errorCode != null;
  }

  String theCodeItWasRefusedWith() {
    return Objects.requireNonNull(errorCode, THE_EVENT_REPORTS_NO_REFUSAL);
  }

  String theMessageItWasRefusedWith() {
    return Objects.requireNonNull(msg, THE_EVENT_REPORTS_NO_REFUSAL);
  }
}
